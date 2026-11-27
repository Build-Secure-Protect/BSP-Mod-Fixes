package com.mrgregles.bsp_mod_fixes.fix.chunks;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-51 (Minecraft 1.20.1, Forge 47.4.23): a diagnostic. A chunk's block entity map is a plain {@code HashMap} that the server
 * thread walks when the chunk unloads (F-48) and whenever it reads it; a mod that changes the map from another thread races
 * every such walk, and the walk throws {@code ConcurrentModificationException}, the crash F-48 now survives. {@code LevelChunk}
 * changes the map in four methods only: {@code setBlockEntity} ({@code put}), {@code removeBlockEntity} ({@code remove}),
 * {@code getBlockEntity(BlockPos, EntityCreationType)} (a {@code remove} of an entry whose block entity {@code isRemoved()}:
 * a <b>reader</b> changes the map whenever it meets one, and during an unload every entry is one) and
 * {@code clearAllBlockEntities} ({@code clear}). {@code LevelChunkOffThreadMixin} calls {@link #onWrite} at the head of the
 * first three and before the {@code remove} inside the fourth (D184: not at the head of {@code getBlockEntity}, so the read
 * path pays nothing). On the server thread the call is an {@code instanceof} and a reference compare and returns; off it, the
 * thread's name and the first calling class outside Minecraft, Forge, the JDK and this mod's fixes are the key of one WARN per
 * run with the dimension, chunk, position and the full stack (D185), and the writer is remembered per chunk so F-48's own
 * warning can say whether this tracer saw one ({@link #describeWriter}). Mod at fault: the owner of the calling class the WARN
 * names (Distant Horizons' chunk workers and MFFS's force field block from the light thread are the candidates on Moongrove).
 * Switch: {@code [diagnostics] offThreadBlockEntityTracer} (D188). Server levels only. Nothing is changed.
 */
public final class OffThreadTracer {
    public static final String ID = "F-51";
    public static final String WHAT = "names, once per thread and calling class per run with the full stack, a thread other than the server's that changes a chunk's block entity map (setBlockEntity, removeBlockEntity, clearAllBlockEntities, or getBlockEntity dropping a removed entry), the race behind the chunk unload crashes; a diagnostic, nothing is changed";
    public static final String TARGET = "Minecraft 1.20.1 (Forge 47.4.23)";

    /** How many chunks' last writers are remembered before the memory is emptied and starts over. */
    public static final int MAX_REMEMBERED_CHUNKS = 10_000;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> LAST_WRITER = new ConcurrentHashMap<>();
    private static final LongAdder SEEN = new LongAdder();

    private OffThreadTracer() {}

    /** Whether the switch is on: the server config's value, and on before that config has loaded. */
    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.DIAG_OFF_THREAD_TRACER.get();
    }

    /** The mixin's entry: {@code what} is being done to {@code chunk}'s block entity map ({@code pos}: the entry's position, when there is one) by the current thread. */
    public static void onWrite(LevelChunk chunk, String what, @Nullable BlockPos pos) {
        if (!(chunk.getLevel() instanceof ServerLevel level) || level.getServer().isSameThread() || !enabled()) {
            return;
        }
        Thread thread = Thread.currentThread();
        Throwable stack = new Throwable("the call off the server thread");
        String caller = ForeignFrames.firstClass(stack.getStackTrace(), "a class of Minecraft, Forge or the JDK only (no mod frame on the stack)");
        String writer = "thread '" + thread.getName() + "' from " + caller;
        String dimension = level.dimension().location().toString();
        if (LAST_WRITER.size() >= MAX_REMEMBERED_CHUNKS) {
            LAST_WRITER.clear();
        }
        LAST_WRITER.put(chunkKey(dimension, chunk.getPos()), writer);
        SEEN.increment();
        if (WARNED.add(thread.getName() + "|" + caller)) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-51: {} ran {} on chunk {} in {}{} off the server thread; the chunk's block entity map is a plain HashMap the server thread walks when the chunk unloads, so this is the race behind the unload crashes. Mod at fault: the owner of that class. Stack:",
                    writer, what, chunk.getPos(), dimension, pos == null ? "" : " at " + pos.toShortString(), stack);
        }
    }

    /** For F-48's warning: what this tracer knows about off-thread writers of the chunk. */
    public static String describeWriter(LevelChunk chunk) {
        if (Fixes.skipped().containsKey(ID)) {
            return "F-51 (the off-thread tracer) is not applied";
        }
        if (!enabled()) {
            return "F-51 (the off-thread tracer) is switched off";
        }
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return "F-51 (the off-thread tracer) watches server levels only";
        }
        String writer = LAST_WRITER.get(chunkKey(level.dimension().location().toString(), chunk.getPos()));
        return writer == null ? "no off-thread writer seen by F-51" : "F-51 last saw " + writer + " change it";
    }

    /** The last off-thread writer remembered for a chunk, or null. */
    @Nullable
    public static String lastWriter(String dimension, ChunkPos pos) {
        return LAST_WRITER.get(chunkKey(dimension, pos));
    }

    private static String chunkKey(String dimension, ChunkPos pos) {
        return dimension + " " + pos.x + " " + pos.z;
    }

    /** The "thread name|calling class" keys warned about so far in this run. */
    public static Set<String> warned() {
        return Set.copyOf(WARNED);
    }

    /** How many off-thread changes were seen so far in this run. */
    public static long seen() {
        return SEEN.sum();
    }
}
