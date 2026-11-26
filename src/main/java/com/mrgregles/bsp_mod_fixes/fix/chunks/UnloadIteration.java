package com.mrgregles.bsp_mod_fixes.fix.chunks;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * F-48 (Minecraft 1.20.1, Forge 47.4.23): a chunk's unload runs its block entities' two passes over copies of the block
 * entity map. {@code LevelChunk.clearAllBlockEntities} (from {@code ServerLevel.unload} and {@code ClientLevel.unload}) runs
 * {@code blockEntities.values().forEach(onChunkUnloaded)}, then {@code forEach(setRemoved)}, then clears the map and rebinds
 * the tickers, over a plain {@code HashMap} that is live the whole time. Any hook that changes the map during a pass makes
 * {@code HashMap.Values.forEach} throw {@code ConcurrentModificationException} at the end of that pass: the rest of the method
 * never runs (the second pass, the clears, the ticker rebinds), the chunk's block entities are dropped without
 * {@code setRemoved}, its tickers stay bound (AllTheLeaks' dead tickers), and from {@code ServerLevel.tick} it is "Exception
 * ticking world", the crash. The usual change is not a block write but a lookup: {@code LevelChunk.getBlockEntity} drops an
 * entry whose block entity {@code isRemoved()} and, for {@code Level.getBlockEntity}'s immediate creation, puts a new block
 * entity in its place; and every block entity invalidates its attached capabilities in both passes (Forge's
 * {@code BlockEntity.onChunkUnloaded} and {@code setRemoved} both call {@code invalidateCaps}; MFFS's electric block entities
 * invalidate their own energy capabilities besides), which wakes every cache listener another mod attached, and a listener
 * that re-reads its neighbour at once changes the map twice, leaving a half-alive block entity in a chunk that is being thrown
 * away. The stack never names that listener. A block change from a hook does the same; MFFS 5.1.29 makes none on unload (D162). Mod at fault: Minecraft's
 * loop, exposed by whichever listener looks up during the unload (the WARN line names its block entity).
 *
 * <p>The fix ({@code LevelChunkUnloadMixin}) redirects the two {@code forEach} calls over the block entity map (the ticker
 * rebind is left alone) to {@link #unloadPass} and {@link #removePass}: each runs the same action over
 * {@code List.copyOf(values)} in vanilla's order, and after each call checks cheaply whether the map moved (its size, or the
 * entry at that block entity's own position); when it did, an identity diff against the last snapshot gives the +N/−M and the
 * block entity is named at WARN, once per block entity type per server run (D161, D164); one diff at the end of each pass
 * catches an equal-size change elsewhere, named as the pass's (once per chunk per run, the actor unknown). After the second
 * pass every block entity in the map the pass never saw (added during the unload) gets {@code setRemoved()} too, in rounds up
 * to {@link #MAX_SWEEP_ROUNDS} since a removal can wake the same listener and re-create the entry again; a type still coming
 * back after the last round is named at WARN, and vanilla's clear then drops it (the one case left half-alive). Switch:
 * {@code [chunks] unloadOverCopies} in {@code bsp_mod_fixes-server.toml}, read at each unload; off, the passes run
 * {@code values.forEach(action)} as vanilla does. Both sides (on a client the chunk is still {@code loaded} during its
 * unload, so a block entity created meanwhile gets {@code onLoad} and a ticker there, then {@code setRemoved} from the pass or
 * the sweep and vanilla's rebind of its ticker; on the server the chunk is unloaded first, so none of that). Pure
 * {@link #runPass} and {@link #sweep} for the tests. Not covered: a lookup from another thread (MFFS's force field block reads
 * its block entity for its light emission on the light thread), which can change the map outside any loop.
 */
public final class UnloadIteration {
    public static final String ID = "F-48";
    public static final String WHAT = "a chunk's unload runs its block entities' onChunkUnloaded and setRemoved passes over copies of the block entity map, names a block entity that changed the map meanwhile and removes any added during the unload (Minecraft iterated the live map, so a lookup or block change from a hook threw ConcurrentModificationException, crashing the server and leaving the chunk's block entities and tickers half-alive)";
    public static final String TARGET = "Minecraft 1.20.1 (Forge 47.4.23)";

    /** One change of the map seen during a pass: after whose call (null: found at the pass's end, actor unknown), how many entries came and went. */
    public record Change(@Nullable Object actor, int added, int removed) {}

    /** What a pass found: the changes, and the entries in the map at the end that the pass never visited. */
    public record PassReport(List<Change> changes, List<Object> leftovers) {
        public boolean changedAnything() {
            return !changes.isEmpty() || !leftovers.isEmpty();
        }
    }

    /** How many rounds the sweep after the second pass makes before giving a re-created block entity up. */
    public static final int MAX_SWEEP_ROUNDS = 8;

    /** What the sweep did: the entries removed over all rounds, the rounds made, and the entries still coming back after the last. */
    public record SweepReport(List<Object> removed, int rounds, List<Object> stillThere) {
        public boolean exhausted() {
            return !stillThere.isEmpty();
        }
    }

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final LongAdder CHANGED_UNLOADS = new LongAdder();
    private static final LongAdder LEFTOVERS_REMOVED = new LongAdder();

    private UnloadIteration() {}

    /** Whether the switch is on: the server config's value, and on before that config has loaded (a crash guard, so on by doubt). */
    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.CHUNKS_UNLOAD_OVER_COPIES.get();
    }

    /**
     * Pure: runs {@code action} once over each value the map holds at the start, in the map's order, from a copy, so the
     * action may change the map. After each call the map's size and the entry at that value's key are checked; when either
     * moved, an identity diff against the last snapshot records what came and went, attributed to that value; a diff at the
     * end records anything else, unattributed. The report's leftovers are the values in the map at the end that the pass
     * never visited.
     */
    public static <K, V> PassReport runPass(Map<K, V> map, Consumer<V> action, Function<V, K> keyOf) {
        List<V> copy = List.copyOf(map.values());
        Set<V> visited = identitySet(copy);
        Set<V> snapshot = visited;
        int expectedSize = map.size();
        List<Change> changes = new ArrayList<>();
        for (V value : copy) {
            action.accept(value);
            if (map.size() != expectedSize || map.get(keyOf.apply(value)) != value) {
                Set<V> now = identitySet(map.values());
                int added = countMissing(now, snapshot);
                int removed = countMissing(snapshot, now);
                if (added > 0 || removed > 0) {
                    changes.add(new Change(value, added, removed));
                }
                snapshot = now;
                expectedSize = map.size();
            }
        }
        Set<V> now = identitySet(map.values());
        int added = countMissing(now, snapshot);
        int removed = countMissing(snapshot, now);
        if (added > 0 || removed > 0) {
            changes.add(new Change(null, added, removed));
        }
        List<Object> leftovers = new ArrayList<>();
        for (V value : now) {
            if (!visited.contains(value)) {
                leftovers.add(value);
            }
        }
        return new PassReport(List.copyOf(changes), List.copyOf(leftovers));
    }

    /**
     * Pure: removes, with {@code remove}, every value in the map that is not in {@code visited}, again and again while removals
     * bring new values in (a listener re-creating the entry it just lost), at most {@code maxRounds} times. The report lists
     * what was removed, the rounds made, and what was still new after the last round.
     */
    public static <K, V> SweepReport sweep(Map<K, V> map, Collection<V> visited, Consumer<V> remove, int maxRounds) {
        Set<V> seen = identitySet(visited);
        List<Object> removed = new ArrayList<>();
        int rounds = 0;
        while (rounds < maxRounds) {
            List<V> fresh = new ArrayList<>();
            for (V value : map.values()) {
                if (!seen.contains(value)) {
                    fresh.add(value);
                }
            }
            if (fresh.isEmpty()) {
                break;
            }
            rounds++;
            for (V value : fresh) {
                seen.add(value);
                remove.accept(value);
                removed.add(value);
            }
        }
        List<Object> stillThere = new ArrayList<>();
        for (V value : map.values()) {
            if (!seen.contains(value)) {
                stillThere.add(value);
            }
        }
        return new SweepReport(List.copyOf(removed), rounds, List.copyOf(stillThere));
    }

    private static <V> Set<V> identitySet(Collection<V> values) {
        Set<V> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(values);
        return set;
    }

    /** How many of {@code of} are not in {@code in}. */
    private static <V> int countMissing(Set<V> of, Set<V> in) {
        int n = 0;
        for (V v : of) {
            if (!in.contains(v)) {
                n++;
            }
        }
        return n;
    }

    /** The first pass of a chunk's unload ({@code onChunkUnloaded}), in place of {@code values.forEach(action)}. */
    public static void unloadPass(LevelChunk chunk, Collection<BlockEntity> values, Consumer<BlockEntity> action) {
        pass(chunk, values, action, false);
    }

    /** The second pass ({@code setRemoved}); block entities added during the unload are removed afterwards. */
    public static void removePass(LevelChunk chunk, Collection<BlockEntity> values, Consumer<BlockEntity> action) {
        pass(chunk, values, action, true);
    }

    private static void pass(LevelChunk chunk, Collection<BlockEntity> values, Consumer<BlockEntity> action, boolean sweep) {
        if (!enabled()) {
            values.forEach(action);
            return;
        }
        PassReport report = runPass(chunk.getBlockEntities(), action, BlockEntity::getBlockPos);
        for (Change change : report.changes()) {
            warnChange(chunk, change);
        }
        int swept = 0;
        if (sweep) {
            Map<BlockPos, BlockEntity> map = chunk.getBlockEntities();
            SweepReport swept0 = sweep(map, passVisited(map, report), be -> {
                if (!be.isRemoved()) {
                    be.setRemoved();
                }
                warnLeftover(chunk, be);
            }, MAX_SWEEP_ROUNDS);
            swept = swept0.removed().size();
            LEFTOVERS_REMOVED.add(swept);
            if (swept0.exhausted()) {
                for (Object o : swept0.stillThere()) {
                    warnStillThere(chunk, (BlockEntity) o, swept0.rounds());
                }
            }
        }
        if (!report.changes().isEmpty() || swept > 0) {
            CHANGED_UNLOADS.increment();
            Fixes.LOGGER.debug("BSP-Mod Fixes F-48: chunk {} in {}: {} change(s) to its block entities during the {} pass of its unload, {} block entities added meanwhile removed",
                    chunk.getPos(), dimension(chunk), report.changes().size(), sweep ? "setRemoved" : "onChunkUnloaded", swept);
        }
    }

    /** The block entities the pass visited: what is in the map now, less the report's leftovers (the pass's copy is not kept). */
    private static Collection<BlockEntity> passVisited(Map<BlockPos, BlockEntity> map, PassReport report) {
        Set<BlockEntity> leftovers = identitySet(new ArrayList<>());
        for (Object o : report.leftovers()) {
            leftovers.add((BlockEntity) o);
        }
        List<BlockEntity> visited = new ArrayList<>();
        for (BlockEntity be : map.values()) {
            if (!leftovers.contains(be)) {
                visited.add(be);
            }
        }
        return visited;
    }

    private static void warnStillThere(LevelChunk chunk, BlockEntity be, int rounds) {
        String type = typeId(be);
        if (WARNED.add("recreated:" + type)) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-48: {} at {} in {} was re-created on every removal during its chunk's unload, {} rounds; the last copy is dropped with the chunk without setRemoved",
                    type, be.getBlockPos().toShortString(), dimension(chunk), rounds);
        }
    }

    private static void warnChange(LevelChunk chunk, Change change) {
        BlockEntity actor = change.actor() instanceof BlockEntity be ? be : null;
        String type = actor == null ? "a block entity of the pass" : typeId(actor);
        if (WARNED.add(actor == null ? "unknown:" + dimension(chunk) + chunk.getPos() : type)) {
            if (actor == null) {
                Fixes.LOGGER.warn("BSP-Mod Fixes F-48: a block entity of chunk {} in {} changed its chunk's block entities while the chunk unloaded (+{}/-{}); which one is not known (an equal-size change away from the block entity being called)",
                        chunk.getPos(), dimension(chunk), change.added(), change.removed());
            } else {
                Fixes.LOGGER.warn("BSP-Mod Fixes F-48: {} at {} in {} changed its chunk's block entities while the chunk unloaded (+{}/-{})",
                        type, actor.getBlockPos().toShortString(), dimension(chunk), change.added(), change.removed());
            }
        }
    }

    private static void warnLeftover(LevelChunk chunk, BlockEntity be) {
        String type = typeId(be);
        if (WARNED.add("leftover:" + type)) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-48: {} at {} in {} was added to its chunk while the chunk unloaded and has been removed with it",
                    type, be.getBlockPos().toShortString(), dimension(chunk));
        }
    }

    private static String typeId(BlockEntity be) {
        ResourceLocation key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        return key == null ? be.getClass().getName() : key.toString();
    }

    private static String dimension(LevelChunk chunk) {
        return chunk.getLevel() == null ? "an unknown level" : chunk.getLevel().dimension().location().toString();
    }

    /** The block entity types (and "unknown:", "leftover:", "recreated:" marks) warned about so far in this run. */
    public static Set<String> warned() {
        return Set.copyOf(WARNED);
    }

    /** How many unloads changed their chunk's block entities, or left some to remove, so far. */
    public static long changedUnloads() {
        return CHANGED_UNLOADS.sum();
    }

    /** How many block entities added during an unload were removed by the sweep so far. */
    public static long leftoversRemoved() {
        return LEFTOVERS_REMOVED.sum();
    }
}
