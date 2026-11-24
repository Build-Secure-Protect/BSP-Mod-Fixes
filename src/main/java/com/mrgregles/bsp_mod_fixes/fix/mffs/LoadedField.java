package com.mrgregles.bsp_mod_fixes.fix.mffs;

import com.mojang.datafixers.util.Pair;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.LongAdder;

/**
 * F-44 (MFFS 5.1.29): a force field stays within loaded chunks; no field work loads a chunk. MFFS's selection asks its position
 * cache before it asks whether the chunk is loaded, and the cache's loader reads the block state: a block-state read of an
 * unloaded chunk loads it (from a worker thread through the main thread, with a one-tick ticket), so every field position
 * outside the loaded area pulled its chunk in, the chunk's saved field blocks got block entities and tickers, and a tick later
 * the chunk unloaded and every ticker died; the cache entry lives one minute, so each chunk the field touched was loaded and
 * dropped at least once a minute for as long as the projector ticked (AllTheLeaks counted 1.17 million dead tickers in a
 * hundred minutes around one projector). {@code destroyField} reads every calculated position the same way, Fusion's
 * {@code beforeProject} its collected positions, and a field block's {@code onLoad} looks its projector up, loading the
 * projector's chunk whenever a field chunk loads.
 *
 * <p>With the fix, {@code MffsLoadedFieldMixin} has the selection skip a position whose chunk does not tick blocks (the range a
 * player or a chunk loader keeps; a merely present chunk's edge blocks would pull the next chunk in through their neighbour
 * updates) before any module or the cache sees it, and reads the block state in {@code destroyField} only where the chunk is loaded; {@code MffsFusionProjectMixin}
 * does the same for Fusion's removals; {@code MffsFieldBlockLoadMixin} keeps a field block whose projector's chunk is not
 * loaded instead of loading it to ask. Field blocks in unloaded chunks stay as saved and are completed where missing only when
 * their chunk is loaded for another reason; a stale block (its projector gone) is still removed when its chunk loads while the
 * projector's chunk is loaded, as MFFS does. Not covered: a teardown or a Fusion removal in a present chunk that does not tick
 * removes blocks whose neighbour updates can pull the next chunk in once (no repetition: nothing is placed there again); the
 * projector lookups of the chunk packet (F-36's tag), of collision and of light emission still load the projector's chunk when
 * it is absent; with F-43 off the selection runs on the pool and judges by presence, not ticking. Switch: {@code [mffs]
 * fieldStaysInLoadedChunks} in {@code bsp_mod_fixes-server.toml}, read at each use. One DEBUG line a minute at most. Pure {@link #keep} and
 * {@link #notProjectable()} for the tests.
 */
public final class LoadedField {
    public static final String ID = "F-44";
    public static final String WHAT = "a force field stays within loaded chunks: selection, teardown, Fusion's removals and a field block's projector lookup never load a chunk (MFFS loaded and dropped every chunk its field touched at least once a minute, each time killing the chunk's block entities)";
    public static final String TARGET = MffsGate.TARGET;
    /** The least time between two DEBUG lines. */
    public static final long DEBUG_MILLIS = 60_000L;

    private static final Pair<BlockState, Boolean> NOT_PROJECTABLE = Pair.of(Blocks.AIR.defaultBlockState(), false);
    private static final LongAdder SKIPPED_POSITIONS = new LongAdder();
    private static final LongAdder KEPT_BLOCKS = new LongAdder();
    private static volatile long lastDebug = Long.MIN_VALUE;

    private LoadedField() {}

    /** Whether the switch is on: the server config's value once it is loaded. */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_FIELD_STAYS_IN_LOADED_CHUNKS.get();
    }

    /** The rule: a position is left alone when the switch is on and its chunk is not loaded. */
    public static boolean keep(boolean enabled, boolean chunkLoaded) {
        return enabled && !chunkLoaded;
    }

    /**
     * Whether the chunk holding {@code pos} is loaded, asked without loading it: on the server thread the chunk source's loaded
     * chunk, from any other thread its holder (what MFFS's own {@code isLoaded} reads); on a client level the level's own answer.
     */
    public static boolean loaded(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            ServerChunkCache chunks = server.getChunkSource();
            int x = pos.getX() >> 4;
            int z = pos.getZ() >> 4;
            if (Thread.currentThread() == server.getServer().getRunningThread()) {
                return chunks.getChunkNow(x, z) != null;
            }
            return chunks.hasChunk(x, z);
        }
        return level.isLoaded(pos);
    }

    /**
     * Whether the chunk holding {@code pos} ticks blocks: within the range a player or a chunk loader keeps, whose neighbours are
     * loaded too. A field block is placed with neighbour-shape updates that read the block states around it, and a read of an
     * unloaded chunk loads it, so a block at the edge of a merely present chunk pulls the next chunk in and the field creeps on
     * through chunks loaded for a tick; a ticking chunk's neighbours are already present, so nothing is pulled in. Read without
     * loading, from any thread.
     */
    public static boolean ticking(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            if (Thread.currentThread() != server.getServer().getRunningThread()) {
                // F-43 off: the selection is on the pool, and the ticking tracker's map is the server thread's to change; the
                // chunk holder map is swapped by reference, so presence is the safe answer there (a present chunk's edge can
                // still pull the next chunk in once through a placed block's neighbour updates).
                return loaded(level, pos);
            }
            return server.shouldTickBlocksAt(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        return level.isLoaded(pos);
    }

    /**
     * {@link #skip(Level, BlockPos)} with a per-pass memo of the answer by chunk: a pass visits every calculated position, tens
     * of thousands in a few dozen chunks, so the chunk's answer is asked once per pass instead of once per position.
     */
    public static boolean skip(Level level, BlockPos pos, Long2BooleanMap tickingByChunk) {
        if (!enabled()) {
            return false;
        }
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        boolean ticking;
        if (tickingByChunk.containsKey(chunk)) {
            ticking = tickingByChunk.get(chunk);
        } else {
            ticking = ticking(level, pos);
            tickingByChunk.put(chunk, ticking);
        }
        if (!keep(true, ticking)) {
            return false;
        }
        SKIPPED_POSITIONS.increment();
        debug("a field position in a chunk that does not tick was skipped at " + pos + " in " + level.dimension().location());
        return true;
    }

    /** Whether the selection skips {@code pos}: the switch on and its chunk not block-ticking; counted and logged at DEBUG at most once a minute. */
    public static boolean skip(Level level, BlockPos pos) {
        if (!keep(enabled(), ticking(level, pos))) {
            return false;
        }
        SKIPPED_POSITIONS.increment();
        debug("a field position in a chunk that does not tick was skipped at " + pos + " in " + level.dimension().location());
        return true;
    }

    /** The cache answer for a position the selection must not touch: air, not projectable. */
    @SuppressWarnings("unchecked")
    public static <T> T notProjectable() {
        return (T) NOT_PROJECTABLE;
    }

    /** The block state for a teardown or a Fusion removal: the real one where the chunk is loaded, air (nothing to remove) elsewhere. */
    public static BlockState blockStateIfLoaded(Level level, BlockPos pos) {
        if (keep(enabled(), loaded(level, pos))) {
            SKIPPED_POSITIONS.increment();
            debug("a field block in an unloaded chunk was left as saved at " + pos + " in " + level.dimension().location());
            return Blocks.AIR.defaultBlockState();
        }
        return level.getBlockState(pos);
    }

    /** Whether a field block that has just loaded keeps standing without asking: on the server, with a projector whose chunk is not loaded. */
    public static boolean holdsFieldBlock(Level level, BlockPos projector) {
        if (level == null || level.isClientSide || projector == null || !keep(enabled(), loaded(level, projector))) {
            return false;
        }
        KEPT_BLOCKS.increment();
        debug("a field block kept without loading its projector's chunk, projector at " + projector + " in " + level.dimension().location());
        return true;
    }

    private static void debug(String what) {
        long now = System.currentTimeMillis();
        if (lastDebug == Long.MIN_VALUE || now - lastDebug >= DEBUG_MILLIS) { // the first line at once, then one a minute
            lastDebug = now;
            Fixes.LOGGER.debug("BSP-Mod Fixes {}: {} ({} positions left alone by the selection, the teardown or Fusion, {} field blocks kept so far)", ID, what, SKIPPED_POSITIONS.sum(), KEPT_BLOCKS.sum());
        }
    }

    /** How many positions the selection, the teardown or Fusion left alone this session (every pass counts its skips again). */
    public static long skippedPositions() {
        return SKIPPED_POSITIONS.sum();
    }

    /** How many field blocks kept standing without loading their projector's chunk this session. */
    public static long keptBlocks() {
        return KEPT_BLOCKS.sum();
    }
}
