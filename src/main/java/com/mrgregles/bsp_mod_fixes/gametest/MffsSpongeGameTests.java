package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.mffs.MffsGate;
import com.mrgregles.bsp_mod_fixes.fix.mffs.SpongeSampling;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * MF-31: MFFS's sponge module drains by sampling (F-34). The rule and the gate are pure; with {@code -PwithMffs} a real
 * projector with the cube mode and three Scale modules stands in the test area, water, MFFS's own Fortron fluid and a
 * waterlogged slab are placed inside its field, and the real sponge module is run against it: with the fix the fluids go in
 * one pass and the projector's cache never holds the interior point set; with {@code -PskipFixes=MffsSpongeMixin} MFFS's own
 * code drains them and the set is built.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class MffsSpongeGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void the_sponge_sampler_finds_only_loaded_fluid_inside_the_field_and_the_mffs_gate_holds(GameTestHelper h) {
        BlockPos origin = new BlockPos(100, 64, -200);
        SpongeSampling.Box box = new SpongeSampling.Box(-3, -3, -3, 3, 3, 3);
        h.assertTrue(box.volume() == 343, "a cube of radius 3 has 343 interior positions");
        Map<BlockPos, BlockState> world = new HashMap<>();
        Set<BlockPos> fluid = Set.of(origin.offset(1, 1, 1), origin.offset(-2, 0, 2), origin.offset(0, -2, 0), origin.offset(2, 2, -2));
        for (BlockPos p : fluid) {
            world.put(p, p.equals(origin.offset(0, -2, 0)) ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState());
        }
        BlockPos slab = origin.offset(-1, 1, 2);
        world.put(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        world.put(origin.offset(2, -1, 2), Blocks.OAK_SLAB.defaultBlockState()); // dry slab: not drained
        BlockPos unloadedWater = origin.offset(-2, -2, -2);
        world.put(unloadedWater, Blocks.WATER.defaultBlockState());
        world.put(origin.offset(3, 0, 0), Blocks.WATER.defaultBlockState()); // on the wall: the cube mode counts it outside
        SpongeSampling.StateAt states = p -> p.equals(unloadedWater) ? null : world.getOrDefault(p, Blocks.AIR.defaultBlockState());
        // the cube mode's rule: strictly inside the shell
        java.util.function.Predicate<Vec3> inField = w -> Math.abs(w.x - origin.getX()) <= 2 && Math.abs(w.y - origin.getY()) <= 2 && Math.abs(w.z - origin.getZ()) <= 2;
        java.util.function.UnaryOperator<Vec3> toWorld = local -> Vec3.atLowerCornerOf(BlockPos.containing(local).offset(origin));

        SpongeSampling.Pass pass = SpongeSampling.sample(box, toWorld, inField, states, RandomSource.create(42), SpongeSampling.perPass(28));
        h.assertTrue(new HashSet<>(pass.fluid()).equals(fluid), "every loaded fluid block strictly inside is found, and nothing else: " + pass.fluid());
        h.assertTrue(pass.waterlogged().equals(List.of(slab)), "the waterlogged slab is listed apart: " + pass.waterlogged());
        h.assertTrue(pass.fluid().size() == fluid.size(), "no position twice");
        h.assertTrue(pass.attempts() <= SpongeSampling.perPass(28) * SpongeSampling.ATTEMPTS_PER_WANTED, "the draws are bounded: " + pass.attempts());
        SpongeSampling.Pass one = SpongeSampling.sample(box, toWorld, inField, p -> Blocks.WATER.defaultBlockState(), RandomSource.create(7), 1);
        h.assertTrue(one.found() == 1, "the cap holds in a box full of water: " + one.found());
        SpongeSampling.Pass none = SpongeSampling.sample(box, toWorld, inField, p -> Blocks.AIR.defaultBlockState(), RandomSource.create(7), 610);
        h.assertTrue(none.found() == 0 && none.attempts() == 610 * SpongeSampling.ATTEMPTS_PER_WANTED, "a dry box spends its draws and finds nothing: " + none.attempts());
        h.assertTrue(SpongeSampling.perPass(28) == 610 && SpongeSampling.perPass(56) == 1170, "MFFS's own cap: 50 + 20 x projection speed");
        h.assertTrue(!SpongeSampling.tooLarge(343, 0) && !SpongeSampling.tooLarge(343, 343) && SpongeSampling.tooLarge(344, 343), "the interior limit: 0 is none, over it is too large");
        h.assertTrue(SpongeSampling.due(5, Long.MIN_VALUE) && !SpongeSampling.due(100, 50) && SpongeSampling.due(110, 50), "a pass is due at once, then every 60 ticks");
        BlockPos three = new BlockPos(3, 3, 3);
        h.assertTrue(SpongeSampling.Box.cube(three, three).equals(box), "the cube's box is the two scales");
        h.assertTrue(SpongeSampling.Box.sphere(3).equals(box), "the sphere's box is its Scale count each way");
        SpongeSampling.Box cylinder = SpongeSampling.Box.cylinder(three, three);
        h.assertTrue(cylinder.equals(new SpongeSampling.Box(-6, -3, -6, 6, 2, 6)) && cylinder.volume() == 13 * 6 * 13, "the cylinder's box is MFFS's radius (half the four horizontal scales) and the shell's floor to the layer under its cap: " + cylinder);
        h.assertTrue(SpongeSampling.isFluidBlock(Blocks.WATER.defaultBlockState()) && SpongeSampling.isFluidBlock(Blocks.LAVA.defaultBlockState()) && !SpongeSampling.isFluidBlock(Blocks.STONE.defaultBlockState()), "MFFS's fluid test");
        h.assertTrue(SpongeSampling.isWaterlogged(world.get(slab)) && !SpongeSampling.isWaterlogged(Blocks.OAK_SLAB.defaultBlockState()) && !SpongeSampling.isWaterlogged(Blocks.WATER.defaultBlockState()), "the waterlogged test");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_FIX_SPONGE.get() && FixesConfig.MFFS_SPONGE_MAX_INTERIOR_BLOCKS.get() == 0 && SpongeSampling.enabled() && SpongeSampling.interiorLimit() == 0, "on by default, no interior limit by default");

        h.assertTrue(MffsGate.verdict(Optional.of(MffsGate.VERSION)).applies(), "exactly " + MffsGate.VERSION + " applies");
        h.assertTrue(MffsGate.verdict(Optional.of("5.1.28")) == MffsGate.Verdict.OTHER_VERSION && MffsGate.verdict(Optional.of("5.2.0")) == MffsGate.Verdict.OTHER_VERSION, "other builds are skipped");
        h.assertTrue(MffsGate.verdict(Optional.empty()) == MffsGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(MffsGate.verdict(Optional.of(MffsGate.VERSION), false) == MffsGate.Verdict.CHANGED_TARGET, "the build without the methods is skipped");
        h.assertTrue(MffsGate.gates("MffsSpongeMixin") && !MffsGate.gates("GobberDragonFlightMixin"), "the gate decides for its own mixin only");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 900, batch = "mffs_sponge")
    public void the_real_sponge_module_drains_water_fortron_and_a_waterlogged_slab_and_the_fix_never_builds_the_interior(GameTestHelper h) {
        if (!ModList.get().isLoaded(MffsGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: MFFS is not loaded, no projector is placed (add -PwithMffs)");
            h.succeed();
            return;
        }
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(SpongeSampling.ID));
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(12, 4, 12));
        level.setBlockAndUpdate(center, MffsTestBridge.projector());
        BlockEntity projector = level.getBlockEntity(center);
        h.assertTrue(projector != null, "the projector's block entity exists");
        MffsTestBridge.setCubeOfRadius(projector, 3);
        h.assertTrue(MffsTestBridge.positiveScaleX(projector) == 3, "three Scale modules give a radius of 3, found " + MffsTestBridge.positiveScaleX(projector));
        // Inside the cube's shell (within 2 of the centre): two water sources, a Fortron source, a waterlogged slab.
        List<BlockPos> sources = List.of(center.offset(1, 1, 1), center.offset(-2, 2, -2));
        BlockPos fortron = center.offset(2, 0, -1);
        BlockPos slab = center.offset(-1, 1, 2);
        for (BlockPos p : sources) {
            level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
        }
        level.setBlock(fortron, MffsTestBridge.fortronSource(), 3);
        level.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 3);
        Object module = MffsTestBridge.spongeModule();
        long passesBefore = SpongeSampling.passes();
        h.runAfterDelay(20, () -> { // the water has spread a little by now
            h.assertTrue(level.getBlockState(sources.get(0)).getFluidState().isSource(), "the water sources stand before the sponge runs");
            if (fixOn) {
                // At a multiple-of-60 projector tick MFFS's own beforeSelect would build and cache the interior set: the cancel is shown there.
                atSixty(h, projector, 0, () -> {
                    MffsTestBridge.beforeSelect(module, projector);
                    h.assertTrue(!MffsTestBridge.cacheHasInteriorPoints(projector), "MFFS's walk was cancelled: no interior point set at a multiple-of-60 tick");
                    MffsTestBridge.beforeProject(module, projector); // the pass, on the server thread
                    h.assertTrue(SpongeSampling.passes() == passesBefore + 1, "one sampling pass ran");
                    check(h, level, sources, fortron, slab, "with the fix, after one pass");
                    h.assertTrue(!MffsTestBridge.cacheHasInteriorPoints(projector), "the fix never built the interior point set");
                    h.runAfterDelay(100, () -> {
                        h.assertTrue(noFluidWithin(level, center, 2), "the flowing water that was left has dried up with its sources gone");
                        // The sphere: MFFS's radius is the count of all Scale modules (3), inside is closer than 2.5 to the centre.
                        MffsTestBridge.setMode(projector, "sphere");
                        List<BlockPos> inBall = List.of(center.offset(2, 1, 0), center.offset(0, -2, 0));
                        for (BlockPos p : inBall) {
                            level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
                        }
                        h.runAfterDelay(100, () -> drainedWithinPasses(h, level, module, projector, inBall, "sphere", 3, () -> {
                            // The cylinder: MFFS's radius is half the four horizontal scales (6), its inside test has no vertical bound.
                            MffsTestBridge.setMode(projector, "cylinder");
                            List<BlockPos> inCylinder = List.of(center.offset(5, 1, 0), center.offset(0, 2, 4));
                            BlockPos outside = center.offset(7, 0, 0);
                            for (BlockPos p : inCylinder) {
                                level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
                            }
                            level.setBlock(outside, Blocks.WATER.defaultBlockState(), 3);
                            h.runAfterDelay(100, () -> drainedWithinPasses(h, level, module, projector, inCylinder, "cylinder", 3, () -> {
                                h.assertTrue(level.getBlockState(outside).getFluidState().isSource(), "cylinder: the source outside the radius stands");
                                h.assertTrue(!MffsTestBridge.cacheHasInteriorPoints(projector), "still no interior point set");
                                h.succeed();
                            }));
                        }));
                    });
                });
            } else {
                // MFFS's own: beforeSelect acts only on a tick that is a multiple of 60, so it is called once a tick until one is.
                // (One delayed step at a time: registering a tick hook from inside a callback breaks the GameTest's own tick map.)
                mffsOwnPass(h, level, module, projector, sources, fortron, slab, 0);
            }
        });
    }

    /**
     * Runs sampling passes (MFFS's cadence, 60 ticks apart) until every source is drained, up to {@code passes}: a pass draws at
     * random, so one pass misses a given position about one time in a hundred in the cylinder's box, and the test is about the
     * box, not that luck.
     */
    private static void drainedWithinPasses(GameTestHelper h, ServerLevel level, Object module, BlockEntity projector, List<BlockPos> sources, String shape, int passes, Runnable then) {
        MffsTestBridge.beforeSelect(module, projector);
        MffsTestBridge.beforeProject(module, projector);
        boolean drained = sources.stream().noneMatch(p -> level.getBlockState(p).getFluidState().isSource());
        if (!drained && passes > 1) {
            h.runAfterDelay(SpongeSampling.PASS_TICKS, () -> drainedWithinPasses(h, level, module, projector, sources, shape, passes - 1, then));
            return;
        }
        for (BlockPos p : sources) {
            h.assertTrue(!level.getBlockState(p).getFluidState().isSource(), shape + ": the water source at " + p + " is gone");
        }
        then.run();
    }

    /** Runs {@code then} on a projector tick that is a multiple of 60, one delayed step at a time. */
    private static void atSixty(GameTestHelper h, BlockEntity projector, int waited, Runnable then) {
        if (MffsTestBridge.ticks(projector) % 60 != 0 && waited < 130) {
            h.runAfterDelay(1, () -> atSixty(h, projector, waited + 1, then));
            return;
        }
        h.assertTrue(MffsTestBridge.ticks(projector) % 60 == 0, "a multiple-of-60 projector tick was reached");
        then.run();
    }

    private static void mffsOwnPass(GameTestHelper h, ServerLevel level, Object module, BlockEntity projector, List<BlockPos> sources, BlockPos fortron, BlockPos slab, int waited) {
        long ticks = MffsTestBridge.ticks(projector);
        MffsTestBridge.beforeSelect(module, projector);
        if (ticks % 60 != 0 && waited < 130) {
            h.runAfterDelay(1, () -> mffsOwnPass(h, level, module, projector, sources, fortron, slab, waited + 1));
            return;
        }
        h.assertTrue(ticks % 60 == 0, "a multiple-of-60 projector tick was reached (ticks " + ticks + ")");
        h.runAfterDelay(1, () -> {
            MffsTestBridge.beforeProject(module, projector);
            check(h, level, sources, fortron, slab, "as shipped, after MFFS's own pass");
            h.assertTrue(MffsTestBridge.cacheHasInteriorPoints(projector), "as shipped MFFS built and cached the interior point set");
            h.succeed();
        });
    }

    private static void check(GameTestHelper h, ServerLevel level, List<BlockPos> sources, BlockPos fortron, BlockPos slab, String when) {
        for (BlockPos p : sources) {
            h.assertTrue(!level.getBlockState(p).getFluidState().isSource(), when + ": the water source at " + p + " is gone");
        }
        h.assertTrue(level.getBlockState(fortron).getFluidState().isEmpty(), when + ": the Fortron source is gone");
        boolean slabDrained = !level.getBlockState(slab).getValue(BlockStateProperties.WATERLOGGED);
        if (when.startsWith("with the fix")) {
            h.assertTrue(slabDrained, when + ": the slab is no longer waterlogged (D84)");
        } else {
            h.assertTrue(!slabDrained, when + ": MFFS leaves the waterlogged slab as it is");
        }
    }

    private static boolean noFluidWithin(ServerLevel level, BlockPos center, int r) {
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (!level.getBlockState(p).getFluidState().isEmpty() && SpongeSampling.isFluidBlock(level.getBlockState(p))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
