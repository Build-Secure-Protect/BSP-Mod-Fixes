package com.mrgregles.bsp_mod_fixes.fix.mffs;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.IFluidBlock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * F-34: MFFS's sponge module drains fluids by sampling the field's interior. MFFS's {@code SpongeModule.beforeSelect} copies the
 * projector's whole interior point set every 60 ticks ({@code (2r+1)^3} positions for a cube of scale radius r: seventeen
 * million at eight chunks), shuffles the copy and walks it through a block-state cache whose misses load chunks, on whatever
 * thread the selection runs; the set itself is built once and kept, about a gigabyte at that size. The server stalls on every
 * pass and may run out of memory, which is what players saw. The fluid test was never the problem: MFFS already drains any
 * {@code LiquidBlock} (every Forge fluid block) or {@code IFluidBlock}.
 *
 * <p>With the fix, {@code MffsSpongeMixin} cancels MFFS's walk and, on the server thread in {@code beforeProject}, every 60
 * ticks draws up to MFFS's own cap of positions ({@code 50 + 20 x projection speed}) at random inside the field's box, keeps
 * those the mode calls inside and whose chunk is loaded, drains the fluid blocks among them (source and flowing alike, as
 * MFFS does) and empties waterlogged blocks (D84). Nothing is built, cached or loaded. {@code [mffs] fixSponge} turns the
 * rule off; {@code [mffs] spongeMaxInteriorBlocks} (0 = none) makes the sponge do nothing in a field whose box is larger (D83).
 * Pure {@link #sample} for the tests; no MFFS class is named here, the mixin hands the pieces over.
 *
 * <p>What differs from MFFS, on purpose or inherited: the cap counts fluid and waterlogged positions alike, where MFFS counted
 * sources only and collected flowing blocks for free; the pyramid's inside test is MFFS's frustum, which never counts the apex
 * half as inside (MFFS's own interior there was a single layer); the custom mode's inside test is always false, so a custom
 * field's sponge drains nothing (MFFS's did so only where field blocks were about to stand); a sphere's inside is MFFS's ball
 * of radius minus a half, so the outermost layer MFFS's interior set reached is left to the shell.
 */
public final class SpongeSampling {
    public static final String ID = "F-34";
    public static final String WHAT = "the sponge module drains fluids by sampling the field's interior in loaded chunks instead of copying, shuffling and walking every interior position every three seconds";
    public static final String TARGET = MffsGate.TARGET;

    /** MFFS's cadence: a pass every 60 ticks. */
    public static final int PASS_TICKS = 60;
    /** How many random draws a pass may spend per position it wants: a field with little fluid in it costs little. */
    public static final int ATTEMPTS_PER_WANTED = 8;

    private static final LongAdder PASSES = new LongAdder();
    private static final LongAdder DRAINED = new LongAdder();
    private static final LongAdder WATERLOGGED = new LongAdder();
    private static final LongAdder SKIPPED_TOO_LARGE = new LongAdder();

    private SpongeSampling() {}

    /**
     * The box the pass draws from, in the projector's own axes before rotation and translation, both ends inclusive. Each
     * MFFS shape defines its own extent, so the box follows the shape ({@link #cube}, {@link #sphere}, {@link #cylinder}); the
     * mode's own inside test then decides each draw. Its volume is what {@code spongeMaxInteriorBlocks} is measured against.
     */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        /** The cube, tube and pyramid modes: the negative and positive scales, as MFFS's cube interior loops them. */
        public static Box cube(BlockPos negativeScale, BlockPos positiveScale) {
            return new Box(-negativeScale.getX(), -negativeScale.getY(), -negativeScale.getZ(), positiveScale.getX(), positiveScale.getY(), positiveScale.getZ());
        }

        /** The sphere mode: MFFS's radius there is the count of every Scale module, whichever slot it sits in. */
        public static Box sphere(int scaleModules) {
            return new Box(-scaleModules, -scaleModules, -scaleModules, scaleModules, scaleModules, scaleModules);
        }

        /**
         * The cylinder mode: MFFS's radius there is half the sum of the four horizontal scales, and its inside test has no
         * vertical bound, so the box's floor and cap are the shell's: from the bottom cap to the layer under the top cap.
         */
        public static Box cylinder(BlockPos negativeScale, BlockPos positiveScale) {
            int radius = (positiveScale.getX() + negativeScale.getX() + positiveScale.getZ() + negativeScale.getZ()) / 2;
            return new Box(-radius, -negativeScale.getY(), -radius, radius, Math.max(-negativeScale.getY(), positiveScale.getY() - 1), radius);
        }
    }

    /** The tick of each projector's last pass, by its block entity: MFFS makes a fresh module instance whenever its cache clears. */
    private static final Map<BlockEntity, Long> LAST_PASS = new WeakHashMap<>();

    /** Whether a pass is due for this projector now; if so, records it. Server thread only. */
    public static boolean passDue(BlockEntity projector, long ticks) {
        synchronized (LAST_PASS) {
            Long last = LAST_PASS.get(projector);
            if (!due(ticks, last == null ? Long.MIN_VALUE : last)) {
                return false;
            }
            LAST_PASS.put(projector, ticks);
            return true;
        }
    }

    /** The block state at a position, or null when its chunk is not loaded (then the position is left alone). */
    @FunctionalInterface
    public interface StateAt {
        @Nullable
        BlockState at(BlockPos pos);
    }

    /** One pass's findings: fluid blocks to set to air, waterlogged blocks to empty, and the draws it took. */
    public record Pass(List<BlockPos> fluid, List<BlockPos> waterlogged, int attempts) {
        public int found() {
            return fluid.size() + waterlogged.size();
        }
    }

    /** Whether the rule is on: the server config's switch, read once it is loaded. */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_FIX_SPONGE.get();
    }

    /** The interior limit from the config, 0 for none. */
    public static long interiorLimit() {
        return FixesConfig.SPEC.isLoaded() ? FixesConfig.MFFS_SPONGE_MAX_INTERIOR_BLOCKS.get() : 0;
    }

    /** Whether a box of this volume is over the limit (0: never). */
    public static boolean tooLarge(long volume, long limit) {
        return limit > 0 && volume > limit;
    }

    /** MFFS's own cap on what one pass drains: 50 plus 20 per unit of projection speed (which is 28 per speed module plus 28). */
    public static int perPass(int projectionSpeed) {
        return 50 + 20 * Math.max(0, projectionSpeed);
    }

    /** Whether a pass is due: none yet, or 60 ticks since the last. */
    public static boolean due(long ticks, long lastPassTicks) {
        return lastPassTicks == Long.MIN_VALUE || ticks - lastPassTicks >= PASS_TICKS;
    }

    /** MFFS's test for a fluid block it drains: a Forge or vanilla fluid block whose fluid state is not empty. */
    public static boolean isFluidBlock(BlockState state) {
        return (state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof IFluidBlock) && !state.getFluidState().isEmpty();
    }

    /** A block holding water in its waterlogged property (a slab, stairs, a fence under water). */
    public static boolean isWaterlogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    /**
     * One pass: up to {@code wanted} fluid or waterlogged positions found in at most {@code wanted x ATTEMPTS_PER_WANTED} random
     * draws inside {@code box}. Each draw is turned into a world position by {@code toWorld} (the mode's rotation and the
     * projector's position and translation), kept when {@code inField} says the mode counts it as inside and {@code states}
     * has its chunk loaded. No position is listed twice.
     */
    public static Pass sample(Box box, UnaryOperator<Vec3> toWorld, Predicate<Vec3> inField, StateAt states, RandomSource random, int wanted) {
        Set<BlockPos> fluid = new LinkedHashSet<>();
        Set<BlockPos> waterlogged = new LinkedHashSet<>();
        int maxAttempts = Math.max(1, wanted) * ATTEMPTS_PER_WANTED;
        int attempts = 0;
        while (attempts < maxAttempts && fluid.size() + waterlogged.size() < wanted) {
            attempts++;
            Vec3 local = new Vec3(
                    box.minX() + random.nextInt(box.maxX() - box.minX() + 1),
                    box.minY() + random.nextInt(box.maxY() - box.minY() + 1),
                    box.minZ() + random.nextInt(box.maxZ() - box.minZ() + 1));
            Vec3 world = toWorld.apply(local);
            if (!inField.test(world)) {
                continue;
            }
            BlockPos pos = BlockPos.containing(world);
            BlockState state = states.at(pos);
            if (state == null) {
                continue;
            }
            if (isWaterlogged(state)) {
                waterlogged.add(pos);
            } else if (isFluidBlock(state)) {
                fluid.add(pos);
            }
        }
        return new Pass(new ArrayList<>(fluid), new ArrayList<>(waterlogged), attempts);
    }

    /** Records a pass for the counters. */
    public static void passed(Pass pass) {
        PASSES.increment();
        DRAINED.add(pass.fluid().size());
        WATERLOGGED.add(pass.waterlogged().size());
    }

    /** A sponge in a field over the limit: nothing drained, one WARN a while. */
    public static void skippedTooLarge(BlockPos projector, long volume, long limit) {
        SKIPPED_TOO_LARGE.increment();
        Fixes.warnThrottled("mffs-sponge-" + projector.asLong(), "BSP-Mod Fixes: the sponge module of the MFFS projector at {} works in a field of {} interior blocks, over spongeMaxInteriorBlocks ({}); it drains nothing", projector, volume, limit);
    }

    public static long passes() {
        return PASSES.sum();
    }

    public static long drained() {
        return DRAINED.sum();
    }

    public static long waterloggedDrained() {
        return WATERLOGGED.sum();
    }

    public static long skippedTooLarge() {
        return SKIPPED_TOO_LARGE.sum();
    }
}
