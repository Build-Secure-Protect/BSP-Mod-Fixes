package com.mrgregles.bsp_mod_fixes.fix.cm;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import net.minecraft.server.level.ServerChunkCache;

import java.util.concurrent.atomic.LongAdder;

/**
 * F-28: Compact Machines no longer saves its whole room dimension each time a machine block loads. Every machine block
 * entity's {@code onLoad} (and a machine being placed) ends in {@code TunnelHelper.setChunkMode}, which marks the room's
 * chunk forced or not, adds or removes a ticket, and then calls {@code ServerChunkCache.save(true)}: every loaded chunk of
 * the room dimension is written, pending unloads are processed and the IO worker is flushed, on the server thread, the work
 * of {@code /save-all flush} for that dimension, once per machine, whenever a chunk holding machines loads. The Chunkloader
 * upgrade does the same when it is applied or removed. Nothing depends on the save: the forced flag and the ticket take
 * effect through the dimension's own tick, and the chunks are written by the world's normal saves.
 *
 * <p>The two mixins ({@code CmTunnelSaveMixin}, {@code CmChunkloadSaveMixin}) redirect those {@code save} calls here.
 * {@link #save} skips the call while {@code [compactMachines] skipSaveOnLoad} is on (the default) and makes it as Compact
 * Machines would otherwise. The whole call is skipped, its leading ticket update included, so a room's ticket takes effect
 * on the room dimension's next tick rather than inside the machine's load (D66). Pure {@link #shouldSkip} for the tests.
 */
public final class LoadSaves {
    public static final String ID = "F-28";
    public static final String WHAT = "a machine block loading, or a Chunkloader upgrade changing, no longer saves and flushes the whole room dimension on the server thread";
    public static final String TARGET = CmGate.TARGET;

    private static final LongAdder SKIPPED = new LongAdder();
    private static final LongAdder PASSED = new LongAdder();

    private LoadSaves() {}

    /** Whether a save asked for by Compact Machines' chunk-mode code is skipped: only while the switch is on. */
    public static boolean shouldSkip(boolean enabled) {
        return enabled;
    }

    /** Whether the rule is on: the server config's switch, once it is loaded (off before, which is Compact Machines as shipped). */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.CM_SKIP_SAVE_ON_LOAD.get();
    }

    /** In place of each {@code ServerChunkCache.save(flush)} of Compact Machines' chunk-mode code. */
    public static void save(ServerChunkCache chunks, boolean flush) {
        if (shouldSkip(enabled())) {
            SKIPPED.increment();
            return;
        }
        PASSED.increment();
        chunks.save(flush);
    }

    /** Saves skipped so far. */
    public static long skipped() {
        return SKIPPED.sum();
    }

    /** Saves let through so far (the switch off). */
    public static long passed() {
        return PASSED.sum();
    }
}
