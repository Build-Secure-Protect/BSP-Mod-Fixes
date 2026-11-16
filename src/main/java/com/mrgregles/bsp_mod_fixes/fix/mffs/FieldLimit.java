package com.mrgregles.bsp_mod_fixes.fix.mffs;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-35: a force field over the configured size is not projected. MFFS puts no bound on a field: every Scale module adds a
 * block of radius, and a cube of eight chunks' radius is about 396,000 field blocks, each a block entity that every nearby
 * client asks the server about one by one as its chunk arrives; a player's client at twenty chunks froze and died (the proxy
 * log of 12 November). Two limits, each off at 0 ({@code [mffs] maxFieldBlocks}, default 100,000, the number of positions the
 * field would have; {@code [mffs] maxFieldRadius}, default 0, the largest Scale count on any side; D86): a projector whose
 * calculated field breaks either projects nothing, removes what it had built where the chunks are loaded, tells the players
 * within {@value #RANGE} blocks on their action bar once a minute (D87) and logs one throttled line. The field is judged when
 * MFFS selects positions to project, after its modules have had their say on the calculated list (the Dome module drops the
 * lower half), and the refusal is forgotten whenever MFFS tears the field down (a power loss, a module change), so an unpowered
 * projector is quiet and a changed one is judged afresh. The positions are still calculated and kept, so MFFS's own teardown
 * works as before. Pure
 * {@link #judge} and {@link #within} for the tests; the mixin ({@code MffsFieldLimitMixin}) hands the pieces over.
 */
public final class FieldLimit {
    public static final String ID = "F-35";
    public static final String WHAT = "a force field over the configured size is not projected: the projector tears down what it built, tells players nearby and logs once";
    public static final String TARGET = MffsGate.TARGET;

    /** How often a refused projector tells players and the log: once a minute. */
    public static final int NOTICE_TICKS = 1200;
    /** How far from the projector block a player is told (D87). */
    public static final double RANGE = 15.0;
    /** The action-bar line, in this jar's lang file (tools/gen_lang.py). */
    public static final String MSG = "message.bsp_mod_fixes.field_too_large";

    private static final LongAdder REFUSALS = new LongAdder();
    private static final LongAdder NOTICES = new LongAdder();
    private static final LongAdder TORN_DOWN = new LongAdder();
    private static final LongAdder WARNINGS = new LongAdder();

    private FieldLimit() {}

    /** Whether a field is refused, and by which limit ("blocks", "radius", or "" when it passes). */
    public record Verdict(boolean refused, String why) {
        public static final Verdict PASSES = new Verdict(false, "");
    }

    /** One judgement: the verdict with the figures it was made on, written whole from the selection thread. */
    public record Judged(Verdict verdict, int fieldBlocks, int radius) {
        public static final Judged PASSES = new Judged(Verdict.PASSES, 0, 0);
    }

    /** Dev runs only: the GameTests change the block limit here instead of writing the dev world's config file. */
    private static volatile Integer maxBlocksOverride;

    public static int maxFieldBlocks() {
        Integer override = maxBlocksOverride;
        if (override != null) {
            return override;
        }
        return FixesConfig.SPEC.isLoaded() ? FixesConfig.MFFS_MAX_FIELD_BLOCKS.get() : 0;
    }

    /** Test hook: a block limit that stands in for the config until cleared with null. */
    public static void overrideMaxFieldBlocks(Integer limit) {
        maxBlocksOverride = limit;
    }

    public static int maxFieldRadius() {
        return FixesConfig.SPEC.isLoaded() ? FixesConfig.MFFS_MAX_FIELD_RADIUS.get() : 0;
    }

    /** The field's radius for the limit: the largest Scale count on any of the six sides. */
    public static int radius(BlockPos negativeScale, BlockPos positiveScale) {
        return Math.max(
                Math.max(Math.max(negativeScale.getX(), negativeScale.getY()), negativeScale.getZ()),
                Math.max(Math.max(positiveScale.getX(), positiveScale.getY()), positiveScale.getZ()));
    }

    /** The decision: the block count first, then the radius; a limit of 0 is no limit. */
    public static Verdict judge(int fieldBlocks, int radius, int maxBlocks, int maxRadius) {
        if (maxBlocks > 0 && fieldBlocks > maxBlocks) {
            return new Verdict(true, "blocks");
        }
        if (maxRadius > 0 && radius > maxRadius) {
            return new Verdict(true, "radius");
        }
        return Verdict.PASSES;
    }

    /** Whether the players and the log are told now: at once when the refusal begins, then every {@link #NOTICE_TICKS}. */
    public static boolean noticeDue(long ticks, long lastNoticeTicks) {
        return lastNoticeTicks == Long.MIN_VALUE || ticks - lastNoticeTicks >= NOTICE_TICKS;
    }

    /** The players of the list within {@code range} blocks of the projector's block, centre to centre. */
    public static List<ServerPlayer> within(Collection<? extends ServerPlayer> players, BlockPos projector, double range) {
        List<ServerPlayer> near = new ArrayList<>();
        double rangeSq = range * range;
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(projector.getX() + 0.5, projector.getY() + 0.5, projector.getZ() + 0.5) <= rangeSq) {
                near.add(player);
            }
        }
        return near;
    }

    /** The action-bar line for one player. */
    public static void notify(ServerPlayer player) {
        NOTICES.increment();
        player.displayClientMessage(Component.translatable(MSG), true);
    }

    /** A refusal began (for the counters). */
    public static void refused() {
        REFUSALS.increment();
    }

    /** One throttled WARN per projector while it is refused. */
    public static void warn(BlockPos projector, Verdict verdict, int fieldBlocks, int radius) {
        WARNINGS.increment();
        Fixes.warnThrottled("mffs-field-" + projector.asLong(),
                "BSP-Mod Fixes: the MFFS projector at {} would project a field of {} blocks with a radius of {}, over the server's limit ({}: maxFieldBlocks {}, maxFieldRadius {}); nothing is projected",
                projector, fieldBlocks, radius, verdict.why(), maxFieldBlocks(), maxFieldRadius());
    }

    /** Field blocks removed by a refused projector (for the counters). */
    public static void tornDown(int blocks) {
        TORN_DOWN.add(blocks);
    }

    public static long refusals() {
        return REFUSALS.sum();
    }

    public static long notices() {
        return NOTICES.sum();
    }

    public static long tornDownBlocks() {
        return TORN_DOWN.sum();
    }

    public static long warnings() {
        return WARNINGS.sum();
    }
}
