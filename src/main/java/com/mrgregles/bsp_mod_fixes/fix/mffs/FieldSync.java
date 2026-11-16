package com.mrgregles.bsp_mod_fixes.fix.mffs;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-36: a force field block's data travels in the chunk packet; the client no longer asks the server for it block by block.
 * Every MFFS force field block is a block entity holding its projector's position, its camouflage and the light its projector's
 * Glow modules give it. MFFS's block entity overrides neither {@code getUpdateTag} nor {@code handleUpdateTag}, so the chunk
 * packet carries nothing for it; instead, on the client, each block entity's {@code onLoad} sends the server a request for its
 * data as its chunk arrives, the server answers each with the block's tag, and the client then re-renders and relights the block:
 * about 4,100 requests, replies and section rebuilds for one wall chunk of a field at eight chunks' radius, 6,100 at twenty, for
 * every chunk a player loads near the field. On 12 November a field of about twenty chunks froze a player's client and dropped
 * them from the network three times in an evening (the proxy log; the assessment's section 9).
 *
 * <p>With the fix, {@code MffsForceFieldSyncMixin} gives the block entity the two vanilla methods: {@code getUpdateTag} returns
 * MFFS's own {@code getCustomUpdateTag} (the projector, the camouflage, the light), so the chunk packet carries it, about 80 bytes
 * a block before compression and alike from block to block; {@code handleUpdateTag} hands the tag to MFFS's own
 * {@code handleCustomUpdateTag}, exactly what its reply handler did; and the request goes only from a block whose chunk brought
 * nothing. Forge calls a block entity's {@code onLoad} the moment the chunk creates it, before the chunk's tag reaches it, so on
 * the client MFFS's {@code onLoad} is held back ({@link #defer}) and replayed at the end of the tick ({@link #replayDeferred}):
 * by then a block entity made by a chunk packet has had its tag and is left alone, and one placed into a chunk the client already
 * has (a field being built in view) runs MFFS's {@code onLoad} as written and asks as before, a tick later at most. The light
 * check MFFS's handler makes for a block's glow is lost when the tag comes with the chunk: the client applies a chunk's light data
 * later, from a queue drained at render time, and the light engine's pass installs that data after it has checked and propagated,
 * so for every block entity the chunk brought, a marker is queued behind that data ({@code client/FieldSyncLight}) and the check
 * is made at the tick's end after the marker has run ({@link #checkLanded}). {@code [mffs] syncFieldBlocksInChunks} turns the
 * rule off on both sides (D88). No MFFS class is named here; the mixin hands the pieces over.
 */
public final class FieldSync {
    public static final String ID = "F-36";
    public static final String WHAT = "force field blocks' camouflage, projector and light travel in the chunk packet, so the client no longer asks the server for each block one by one as its chunk arrives";
    public static final String TARGET = MffsGate.TARGET;

    private static final LongAdder CHUNK_TAGS = new LongAdder();
    private static final LongAdder APPLIED = new LongAdder();
    private static final LongAdder REQUESTS_SKIPPED = new LongAdder();
    private static final LongAdder LIGHT_CHECKS = new LongAdder();

    private FieldSync() {}

    /** Whether the rule is on: the server config's switch, which Forge sends to each client at login. */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_SYNC_FIELD_BLOCKS.get();
    }

    /** What the mixin gives a field block entity, so the replay can ask whether its chunk packet's tag has been applied. */
    public interface Synced {
        boolean bsp_mod_fixes$isSyncedFromChunk();
    }

    /**
     * Whether a client block entity's {@code onLoad} is held for the tick's end (the replay then decides); MFFS's own code runs at
     * once on the server, with the rule off, and in the replay itself. Pure.
     */
    public static boolean holdsOnLoad(boolean clientSide, boolean enabled, boolean replaying) {
        return clientSide && enabled && !replaying;
    }

    private static final Queue<BlockEntity> DEFERRED = new ConcurrentLinkedQueue<>();
    private static volatile boolean replaying;

    /** Positions whose chunk's light data has landed on the client, waiting for their check at the tick's end. */
    private record Landed(Level level, List<BlockPos> positions) {}

    private static final Queue<Landed> LANDED = new ConcurrentLinkedQueue<>();

    /** A client block entity whose {@code onLoad} is held until the end of the tick. */
    public static void defer(BlockEntity be) {
        DEFERRED.add(be);
    }

    /** Whether {@link #replayDeferred} is running MFFS's {@code onLoad} right now (the mixin then lets it through). */
    public static boolean replaying() {
        return replaying;
    }

    public static int deferred() {
        return DEFERRED.size();
    }

    /** Whether a level's block entities may still be touched: on a client, only the level the client is in now. */
    private static boolean live(Level level) {
        return !level.isClientSide() || com.mrgregles.bsp_mod_fixes.client.FieldSyncLight.isCurrentLevel(level); // reached on a client only
    }

    /**
     * Runs MFFS's {@code onLoad} for every held block entity that is still in its level and whose chunk packet brought no tag; one
     * whose tag has been applied meanwhile is left alone, counted as a request skipped, and on a client has a marker queued behind
     * its chunk's light data for the check that follows. A block entity of a level the client has left (a disconnect in the same
     * tick) is dropped: MFFS's {@code onLoad} would send on a connection that is gone. Returns how many ran MFFS's {@code onLoad}.
     */
    public static int replayDeferred() {
        int ran = 0;
        Map<Level, List<BlockPos>> landedNext = new LinkedHashMap<>();
        replaying = true;
        try {
            BlockEntity be;
            while ((be = DEFERRED.poll()) != null) {
                Level level = be.getLevel();
                if (be.isRemoved() || level == null || !live(level)) {
                    continue;
                }
                if (be instanceof Synced synced && synced.bsp_mod_fixes$isSyncedFromChunk()) {
                    REQUESTS_SKIPPED.increment();
                    if (level.isClientSide()) {
                        landedNext.computeIfAbsent(level, l -> new ArrayList<>()).add(be.getBlockPos());
                    }
                    continue;
                }
                try {
                    be.onLoad();
                    ran++;
                } catch (RuntimeException e) {
                    Fixes.warnThrottled("mffs-field-sync-onload", "BSP-Mod Fixes: MFFS's onLoad for the force field block at {} failed when replayed: {}", be.getBlockPos(), e.toString());
                }
            }
        } finally {
            replaying = false;
        }
        for (Map.Entry<Level, List<BlockPos>> e : landedNext.entrySet()) {
            com.mrgregles.bsp_mod_fixes.client.FieldSyncLight.afterChunkLight(e.getKey(), e.getValue()); // reached on a client only
        }
        return ran;
    }

    /** The marker behind a chunk's light data has run: these positions are checked at the next tick's end. */
    public static void lightLanded(Level level, List<BlockPos> positions) {
        LANDED.add(new Landed(level, positions));
    }

    public static int landed() {
        return LANDED.size();
    }

    /**
     * The light checks for every position whose chunk's light data has landed, one tick after the light engine's pass installed
     * it, so the Glow modules' light is computed on top of the server's data and not under it. Returns how many were checked.
     */
    public static int checkLanded() {
        int checked = 0;
        Landed landed;
        while ((landed = LANDED.poll()) != null) {
            if (!live(landed.level())) {
                continue;
            }
            for (BlockPos pos : landed.positions()) {
                landed.level().getLightEngine().checkBlock(pos);
                checked++;
            }
        }
        LIGHT_CHECKS.add(checked);
        return checked;
    }

    /** The end of each client tick (registered by {@code Fixes.registerEvents} beside the MFFS build this was written for). */
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!LANDED.isEmpty()) {
            checkLanded();
        }
        if (!DEFERRED.isEmpty()) {
            replayDeferred();
        }
    }

    /** A block's tag written into a chunk packet (server side; for the counters). */
    public static void chunkTagWritten() {
        CHUNK_TAGS.increment();
    }

    /** A chunk packet's tag applied to a block entity (client side; for the counters). */
    public static void chunkTagApplied() {
        APPLIED.increment();
    }

    public static long chunkTagsWritten() {
        return CHUNK_TAGS.sum();
    }

    public static long chunkTagsApplied() {
        return APPLIED.sum();
    }

    public static long requestsSkipped() {
        return REQUESTS_SKIPPED.sum();
    }

    public static long lightChecks() {
        return LIGHT_CHECKS.sum();
    }
}
