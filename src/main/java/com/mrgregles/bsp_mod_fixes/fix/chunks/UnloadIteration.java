package com.mrgregles.bsp_mod_fixes.fix.chunks;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * F-48 (Minecraft 1.20.1, Forge 47.4.23): a chunk's unload runs its block entities' two passes over copies of the block
 * entity map. {@code LevelChunk.clearAllBlockEntities} (from {@code ServerLevel.unload} and {@code ClientLevel.unload}) runs
 * {@code blockEntities.values().forEach(onChunkUnloaded)}, then {@code forEach(setRemoved)}, then clears the map and rebinds
 * the tickers, over a plain {@code HashMap} that is live the whole time. Any change to the map during a pass makes
 * {@code HashMap.Values.forEach} throw {@code ConcurrentModificationException} at the end of that pass: the rest of the method
 * never runs (the second pass, the clears, the ticker rebinds), the chunk's block entities are dropped without
 * {@code setRemoved}, its tickers stay bound (AllTheLeaks' dead tickers), and from {@code ServerLevel.tick} it is "Exception
 * ticking world", the crash. Two changers. One is a hook on the server thread: {@code LevelChunk.getBlockEntity} drops an
 * entry whose block entity {@code isRemoved()} and, for {@code Level.getBlockEntity}'s immediate creation, puts a new block
 * entity in its place; every block entity invalidates its attached capabilities in both passes (Forge's
 * {@code BlockEntity.onChunkUnloaded} and {@code setRemoved} both call {@code invalidateCaps}; MFFS's electric block entities
 * invalidate their own energy capabilities besides), which wakes every cache listener another mod attached, and a listener
 * that re-reads its neighbour at once changes the map twice. The other is <b>another thread</b> (0.20.0, D182): a mod that
 * reads a chunk's block entities off the server thread (Distant Horizons' chunk workers, MFFS's force field block from the
 * light thread) drops every removed entry it meets through the same {@code getBlockEntity}, and during an unload every entry is
 * removed; such a write lands in the middle of any walk or copy of the map, which 0.17.0 still made over the live map (its
 * copy, its diffs and its sweep), so the crash moved into this fix's own lines. Mod at fault: Minecraft's loop, exposed by
 * whichever listener or thread touches the map during the unload (the WARN lines, and F-51's, name them).
 *
 * <p>The fix ({@code LevelChunkUnloadMixin}) redirects the two {@code forEach} calls over the block entity map (the ticker
 * rebind is left alone) to {@link #unloadPass} and {@link #removePass}, and the map's {@code clear} to {@link #clearOrDefer}.
 * Each pass runs the same action over a {@link #snapshot} of the values in vanilla's order, and after each call checks cheaply
 * whether the map moved (its size, or the entry at that block entity's own position); when it did, an identity diff against
 * the last snapshot gives the +N/−M and the block entity is named at WARN, once per block entity type per server run (D161,
 * D164); one diff at the end of each pass catches an equal-size change elsewhere, named as the pass's (once per chunk per run,
 * the actor unknown). After the second pass every block entity in the map the pass never saw (added during the unload) gets
 * {@code setRemoved()} too, in rounds up to {@link #MAX_SWEEP_ROUNDS} since a removal can wake the same listener and
 * re-create the entry again; a type still coming back after the last round is named at WARN, and the clear then drops it (the
 * one case left half-alive). Every read of the live map is a snapshot ({@link #snapshot}: a plain copy by iteration, retaken
 * up to {@link #SNAPSHOT_ATTEMPTS} times when another thread's change makes the iteration throw; {@code size()} and
 * {@code get()} do not iterate and stay). When a pass's first snapshot fails every attempt (D187) the pass is not skipped: on
 * a server level both passes and the clear are <b>deferred</b> to the next server task ({@code server.tell(new TickTask)};
 * {@code execute} would run at once on the server thread and only be a sixth attempt), which takes a fresh copy there, runs
 * {@code onChunkUnloaded} and {@code setRemoved} as the unload would have, sweeps and clears; only when that attempt fails
 * too are the passes skipped and the map cleared, the block entities dropped without their calls as the crash left them, but
 * without the crash. On a client chunk ({@code ClientLevel.unload}, no server task) a failed pass is skipped at once. A
 * snapshot that failed mid-pass (a diff, a sweep round) is given up and counted; the pass itself goes on. Whenever a retry
 * was needed, one WARN at the end of the unload (from the clear, or from the deferred task), once per chunk per run, and always
 * when the passes were skipped for good: "block entity map of chunk [x, z] in &lt;dim&gt; changed on another thread during
 * unload (retried N times)", with what became of the passes and whether F-51 ({@link OffThreadTracer}) saw an off-thread
 * writer of that chunk (D184: "no off-thread writer seen by F-51" means the writer reaches the map another way, and
 * wrapping the map itself is the next step). No lock: the writers are not synchronised, so a lock here would guard nothing.
 * Switch: {@code [chunks] unloadOverCopies} in {@code bsp_mod_fixes-server.toml}, read at each unload; off, the passes run
 * {@code values.forEach(action)} and the clear runs as vanilla does. Both sides (on a client the chunk is still {@code loaded}
 * during its unload, so a block entity created meanwhile gets {@code onLoad} and a ticker there, then {@code setRemoved} from
 * the pass or the sweep and vanilla's rebind of its ticker; on the server the chunk is unloaded first, so none of that). Pure
 * {@link #snapshot}, {@link #runPass} and {@link #sweep} for the tests.
 */
public final class UnloadIteration {
    public static final String ID = "F-48";
    public static final String WHAT = "a chunk's unload runs its block entities' onChunkUnloaded and setRemoved passes over copies of the block entity map, retaking a copy another thread changed meanwhile and deferring the passes to the next server task when five copies fail, names a block entity that changed the map and removes any added during the unload (Minecraft iterated the live map, so a lookup or block change from a hook, or a write from another thread, threw ConcurrentModificationException, crashing the server and leaving the chunk's block entities and tickers half-alive)";
    public static final String TARGET = "Minecraft 1.20.1 (Forge 47.4.23)";

    /** One change of the map seen during a pass: after whose call (null: found at the pass's end, actor unknown), how many entries came and went. */
    public record Change(@Nullable Object actor, int added, int removed) {}

    /** A copy of a collection another thread may be changing: the values, and how many attempts failed before one went through. */
    public record Snapshot<V>(List<V> values, int retries) {}

    /**
     * What a pass found: the changes, the entries in the map at the end that the pass never visited, the values it did visit,
     * how many snapshot attempts failed, how many diffs were given up, and whether the pass was aborted before running anything
     * (its first snapshot failed every attempt).
     */
    public record PassReport(List<Change> changes, List<Object> leftovers, List<Object> visited, int retries, int lostDiffs, boolean aborted) {
        public boolean changedAnything() {
            return !changes.isEmpty() || !leftovers.isEmpty();
        }

        static PassReport abortedAfter(int retries) {
            return new PassReport(List.of(), List.of(), List.of(), retries, 0, true);
        }
    }

    /** How many rounds the sweep after the second pass makes before giving a re-created block entity up. */
    public static final int MAX_SWEEP_ROUNDS = 8;

    /** How many times a snapshot of the map is attempted before it is given up (D187). */
    public static final int SNAPSHOT_ATTEMPTS = 5;

    /**
     * What the sweep did: the entries removed over all rounds, the rounds made, the entries still coming back after the last,
     * how many snapshot attempts failed, and whether a round's snapshot failed every attempt (the sweep then ends there).
     */
    public record SweepReport(List<Object> removed, int rounds, List<Object> stillThere, int retries, boolean aborted) {
        public boolean exhausted() {
            return !stillThere.isEmpty();
        }
    }

    /** The passes of one chunk's unload waiting for the next server task: the actions still to run, and the retries so far. */
    private static final class Deferred {
        @Nullable Consumer<BlockEntity> unloadAction;
        @Nullable Consumer<BlockEntity> removeAction;
        int retries;
    }

    /** The race facts of one unload: failed snapshot attempts, diffs given up, a sweep given up, a pass skipped on a client chunk. */
    private static final class Race {
        int retries;
        int lostDiffs;
        boolean sweepAborted;
        @Nullable String skipped;

        String details() {
            return (lostDiffs > 0 ? ", " + lostDiffs + " diff(s) of its changes given up" : "")
                    + (sweepAborted ? ", the sweep of block entities added meanwhile given up" : "");
        }
    }

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final LongAdder CHANGED_UNLOADS = new LongAdder();
    private static final LongAdder LEFTOVERS_REMOVED = new LongAdder();
    private static final LongAdder RETRIES = new LongAdder();
    private static final LongAdder DEFERRED_UNLOADS = new LongAdder();
    private static final LongAdder SKIPPED_PASSES = new LongAdder();
    /** The chunks whose passes are deferred, by identity, with what the deferred task must run. */
    private static final Map<LevelChunk, Deferred> DEFERRED = Collections.synchronizedMap(new IdentityHashMap<>());
    /** What the passes of one chunk's unload met of the other thread so far, by identity, for the one WARN at the unload's end. */
    private static final Map<LevelChunk, Race> RACED = Collections.synchronizedMap(new IdentityHashMap<>());
    /** GameTests only: the collection whose next snapshots are made to fail, and how many of them; null in production. */
    private static final AtomicReference<Collection<?>> FAULT_TARGET = new AtomicReference<>();
    private static final AtomicInteger FAULTS_LEFT = new AtomicInteger();

    private UnloadIteration() {}

    /** Whether the switch is on: the server config's value, and on before that config has loaded (a crash guard, so on by doubt). */
    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.CHUNKS_UNLOAD_OVER_COPIES.get();
    }

    /**
     * Pure: a copy of {@code values} in iteration order, attempted up to {@code attempts} times while the iteration throws
     * because another thread changed the collection meanwhile ({@code ConcurrentModificationException} from a {@code HashMap}
     * iterator; an index or null failure from a torn read of its table). Null when every attempt failed. A null value, which a
     * torn read can hand out and the map never holds, is dropped. The loop body is the iterator and {@code add}, so nothing but
     * the race can throw here. One race is silent: an iterator crossing a resize that a concurrent {@code put} triggers can end
     * early without an exception, so a copy taken under a <i>putter</i> may be short; a block entity so missed by the first pass
     * gets no {@code onChunkUnloaded}, but still {@code setRemoved} from the second pass or the sweep. The writer seen so far is
     * a remover ({@code getBlockEntity}'s drop), whose race always throws.
     */
    @Nullable
    public static <V> Snapshot<V> snapshot(Collection<V> values, int attempts) {
        int retries = 0;
        while (retries < attempts) {
            if (consumeFault(values)) {
                retries++;
                continue;
            }
            try {
                List<V> copy = new ArrayList<>(Math.max(values.size(), 0) + 4);
                for (V value : values) {
                    if (value != null) {
                        copy.add(value);
                    }
                }
                return new Snapshot<>(copy, retries);
            } catch (ConcurrentModificationException | IndexOutOfBoundsException | NullPointerException e) {
                retries++;
            }
        }
        return null;
    }

    /**
     * Pure: runs {@code action} once over each value the map holds at the start, in the map's order, from a snapshot, so the
     * action may change the map. After each call the map's size and the entry at that value's key are checked; when either
     * moved, an identity diff against the last snapshot records what came and went, attributed to that value; a diff at the
     * end records anything else, unattributed. The report's leftovers are the values in the map at the end that the pass
     * never visited. When the first snapshot fails every attempt the report is aborted and nothing was run; a later snapshot
     * failing is a lost diff, and the pass goes on.
     */
    public static <K, V> PassReport runPass(Map<K, V> map, Consumer<V> action, Function<V, K> keyOf) {
        Snapshot<V> start = snapshot(map.values(), SNAPSHOT_ATTEMPTS);
        if (start == null) {
            return PassReport.abortedAfter(SNAPSHOT_ATTEMPTS);
        }
        int retries = start.retries();
        int lostDiffs = 0;
        List<V> copy = start.values();
        Set<V> visited = identitySet(copy);
        Set<V> last = visited;
        int expectedSize = map.size();
        List<Change> changes = new ArrayList<>();
        for (V value : copy) {
            action.accept(value);
            if (map.size() != expectedSize || map.get(keyOf.apply(value)) != value) {
                Snapshot<V> now = snapshot(map.values(), SNAPSHOT_ATTEMPTS);
                expectedSize = map.size();
                if (now == null) {
                    lostDiffs++;
                    retries += SNAPSHOT_ATTEMPTS;
                    continue;
                }
                retries += now.retries();
                Set<V> nowSet = identitySet(now.values());
                int added = countMissing(nowSet, last);
                int removed = countMissing(last, nowSet);
                if (added > 0 || removed > 0) {
                    changes.add(new Change(value, added, removed));
                }
                last = nowSet;
            }
        }
        List<Object> leftovers = new ArrayList<>();
        Snapshot<V> end = snapshot(map.values(), SNAPSHOT_ATTEMPTS);
        if (end == null) {
            lostDiffs++;
            retries += SNAPSHOT_ATTEMPTS;
        } else {
            retries += end.retries();
            Set<V> nowSet = identitySet(end.values());
            int added = countMissing(nowSet, last);
            int removed = countMissing(last, nowSet);
            if (added > 0 || removed > 0) {
                changes.add(new Change(null, added, removed));
            }
            for (V value : end.values()) {
                if (!visited.contains(value)) {
                    leftovers.add(value);
                }
            }
        }
        return new PassReport(List.copyOf(changes), List.copyOf(leftovers), List.copyOf(copy), retries, lostDiffs, false);
    }

    /**
     * Pure: removes, with {@code remove}, every value in the map that is not in {@code visited}, again and again while removals
     * bring new values in (a listener re-creating the entry it just lost), at most {@code maxRounds} times, each round over a
     * snapshot. The report lists what was removed, the rounds made, and what was still new after the last round; a round whose
     * snapshot fails every attempt ends the sweep, aborted, with what was removed so far.
     */
    public static <K, V> SweepReport sweep(Map<K, V> map, Collection<V> visited, Consumer<V> remove, int maxRounds) {
        Set<V> seen = identitySet(visited);
        List<Object> removed = new ArrayList<>();
        int rounds = 0;
        int retries = 0;
        while (rounds < maxRounds) {
            Snapshot<V> now = snapshot(map.values(), SNAPSHOT_ATTEMPTS);
            if (now == null) {
                return new SweepReport(List.copyOf(removed), rounds, List.of(), retries + SNAPSHOT_ATTEMPTS, true);
            }
            retries += now.retries();
            List<V> fresh = new ArrayList<>();
            for (V value : now.values()) {
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
        Snapshot<V> end = snapshot(map.values(), SNAPSHOT_ATTEMPTS);
        if (end == null) {
            return new SweepReport(List.copyOf(removed), rounds, List.of(), retries + SNAPSHOT_ATTEMPTS, true);
        }
        retries += end.retries();
        List<Object> stillThere = new ArrayList<>();
        for (V value : end.values()) {
            if (!seen.contains(value)) {
                stillThere.add(value);
            }
        }
        return new SweepReport(List.copyOf(removed), rounds, List.copyOf(stillThere), retries, false);
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

    /**
     * In place of the map's {@code clear} at the end of the unload: the clear, unless the chunk's passes were deferred, in
     * which case the deferred task clears after running them and is scheduled here, both passes' actions being known by now.
     * The unload's one race WARN, if any, is printed here, so that it tells the whole unload's outcome.
     */
    public static void clearOrDefer(LevelChunk chunk, Map<BlockPos, BlockEntity> map) {
        Deferred deferred = DEFERRED.get(chunk);
        if (deferred == null) {
            map.clear();
            Race race = RACED.remove(chunk);
            if (race != null) {
                warnRace(chunk, race.retries, (race.skipped == null ? "the passes ran" : race.skipped) + race.details(), race.skipped != null);
            }
            return;
        }
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            DEFERRED.remove(chunk);
            RACED.remove(chunk);
            map.clear();
            return;
        }
        MinecraftServer server = level.getServer();
        DEFERRED_UNLOADS.increment();
        server.tell(new TickTask(server.getTickCount() + 1, () -> runDeferred(chunk, deferred)));
    }

    private static void pass(LevelChunk chunk, Collection<BlockEntity> values, Consumer<BlockEntity> action, boolean second) {
        if (!enabled()) {
            values.forEach(action);
            return;
        }
        Deferred waiting = DEFERRED.get(chunk);
        if (waiting != null) {
            // the first pass was deferred: this one waits with it and runs from the same task
            if (second) {
                waiting.removeAction = action;
            } else {
                waiting.unloadAction = action;
            }
            return;
        }
        Map<BlockPos, BlockEntity> map = chunk.getBlockEntities();
        PassReport report = runPass(map, action, BlockEntity::getBlockPos);
        if (report.aborted()) {
            RETRIES.add(report.retries());
            if (chunk.getLevel() instanceof ServerLevel) {
                Deferred deferred = new Deferred();
                if (second) {
                    deferred.removeAction = action;
                } else {
                    deferred.unloadAction = action;
                }
                deferred.retries = report.retries();
                DEFERRED.put(chunk, deferred);
                return;
            }
            // a client chunk: no server task to defer to; the pass is skipped and vanilla's clear follows
            SKIPPED_PASSES.increment();
            Race race = raceOf(chunk);
            race.retries += report.retries();
            race.skipped = "the " + passName(second) + " pass is skipped (a client chunk: no server task to defer it to) and the map cleared";
            return;
        }
        finishPass(chunk, map, report, second);
    }

    /** The part of a pass after its run: the WARN lines for the changes, the sweep after the second pass, the counters, the race facts for the unload's WARN. */
    private static void finishPass(LevelChunk chunk, Map<BlockPos, BlockEntity> map, PassReport report, boolean second) {
        boolean raced = report.retries() > 0 || report.lostDiffs() > 0;
        for (Change change : report.changes()) {
            warnChange(chunk, change, raced);
        }
        int ownRetries = report.retries();
        boolean sweepAborted = false;
        int swept = 0;
        if (second) {
            @SuppressWarnings("unchecked")
            List<BlockEntity> visited = (List<BlockEntity>) (List<?>) report.visited();
            SweepReport sweep = sweep(map, visited, be -> {
                if (!be.isRemoved()) {
                    be.setRemoved();
                }
                warnLeftover(chunk, be);
            }, MAX_SWEEP_ROUNDS);
            swept = sweep.removed().size();
            LEFTOVERS_REMOVED.add(swept);
            ownRetries += sweep.retries();
            sweepAborted = sweep.aborted();
            if (sweep.exhausted()) {
                for (Object o : sweep.stillThere()) {
                    warnStillThere(chunk, (BlockEntity) o, sweep.rounds(), raced || sweep.retries() > 0);
                }
            }
        }
        if (!report.changes().isEmpty() || swept > 0) {
            CHANGED_UNLOADS.increment();
            Fixes.LOGGER.debug("BSP-Mod Fixes F-48: chunk {} in {}: {} change(s) to its block entities during the {} pass of its unload, {} block entities added meanwhile removed",
                    chunk.getPos(), dimension(chunk), report.changes().size(), passName(second), swept);
        }
        RETRIES.add(ownRetries);
        if (ownRetries > 0 || report.lostDiffs() > 0 || sweepAborted) {
            Race race = raceOf(chunk);
            race.retries += ownRetries;
            race.lostDiffs += report.lostDiffs();
            race.sweepAborted |= sweepAborted;
        }
    }

    /** The next server task after an unload whose pass could not copy the map: the passes, the sweep and the clear, from a fresh copy. */
    private static void runDeferred(LevelChunk chunk, Deferred deferred) {
        DEFERRED.remove(chunk);
        Map<BlockPos, BlockEntity> map = chunk.getBlockEntities();
        int retries = deferred.retries; // counted in RETRIES when the pass was aborted
        boolean ran = true;
        if (deferred.unloadAction != null) {
            PassReport first = runPass(map, deferred.unloadAction, BlockEntity::getBlockPos);
            if (first.aborted()) {
                RETRIES.add(first.retries());
                retries += first.retries();
                ran = false;
            } else {
                finishPass(chunk, map, first, false);
            }
        }
        if (ran && deferred.removeAction != null) {
            PassReport second = runPass(map, deferred.removeAction, BlockEntity::getBlockPos);
            if (second.aborted()) {
                RETRIES.add(second.retries());
                retries += second.retries();
                ran = false;
            } else {
                finishPass(chunk, map, second, true);
            }
        }
        map.clear();
        Race race = RACED.remove(chunk);
        String details = race == null ? "" : race.details();
        if (race != null) {
            retries += race.retries;
        }
        if (ran) {
            warnRace(chunk, retries, "the passes ran from the next server task" + details, false);
        } else {
            SKIPPED_PASSES.increment();
            warnRace(chunk, retries, "the passes were skipped after a second failure from the next server task: the chunk's block entities are dropped without onChunkUnloaded and setRemoved, as the crash left them" + details, true);
        }
    }

    /** Forge's server-stopped event: tasks queued in the last tick never run, so nothing waits any longer. */
    public static void onServerStopped(ServerStoppedEvent event) {
        int pending = DEFERRED.size();
        DEFERRED.clear();
        RACED.clear();
        if (pending > 0) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-48: the server stopped with the deferred passes of {} chunk unload(s) still queued; their block entities are dropped with the server", pending);
        }
    }

    private static Race raceOf(LevelChunk chunk) {
        return RACED.computeIfAbsent(chunk, c -> new Race());
    }

    private static String passName(boolean second) {
        return second ? "setRemoved" : "onChunkUnloaded";
    }

    /**
     * The unload's one WARN about another thread, once per chunk per run; {@code always} (the passes skipped for good, the one
     * outcome that must never go unlogged) prints under its own key whatever was printed for the chunk before.
     */
    private static void warnRace(LevelChunk chunk, int retries, String outcome, boolean always) {
        if (WARNED.add((always ? "skipped:" : "race:") + dimension(chunk) + chunk.getPos())) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-48: block entity map of chunk {} in {} changed on another thread during unload (retried {} times); {}; {}",
                    chunk.getPos(), dimension(chunk), retries, outcome, OffThreadTracer.describeWriter(chunk));
        }
    }


    private static void warnStillThere(LevelChunk chunk, BlockEntity be, int rounds, boolean raced) {
        String type = typeId(be);
        if (WARNED.add("recreated:" + type)) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-48: {} at {} in {} was re-created on every removal during its chunk's unload, {} rounds; the last copy is dropped with the chunk without setRemoved{}",
                    type, be.getBlockPos().toShortString(), dimension(chunk), rounds,
                    raced ? "; another thread changed the map during this unload, so the additions may be its and not a listener's" : "");
        }
    }

    /** {@code raced}: a snapshot of this pass had to be retaken, so the change may be the other thread's and not the named block entity's. */
    private static void warnChange(LevelChunk chunk, Change change, boolean raced) {
        BlockEntity actor = change.actor() instanceof BlockEntity be ? be : null;
        String type = actor == null ? "a block entity of the pass" : typeId(actor);
        String doubt = raced ? "; another thread changed the map during this unload, so the change may be its doing and not this block entity's" : "";
        if (WARNED.add(actor == null ? "unknown:" + dimension(chunk) + chunk.getPos() : type)) {
            if (actor == null) {
                Fixes.LOGGER.warn("BSP-Mod Fixes F-48: a block entity of chunk {} in {} changed its chunk's block entities while the chunk unloaded (+{}/-{}); which one is not known (a change the per-call checks did not catch: equal-size, or after the last call){}",
                        chunk.getPos(), dimension(chunk), change.added(), change.removed(), doubt);
            } else {
                Fixes.LOGGER.warn("BSP-Mod Fixes F-48: {} at {} in {} changed its chunk's block entities while the chunk unloaded (+{}/-{}){}",
                        type, actor.getBlockPos().toShortString(), dimension(chunk), change.added(), change.removed(), doubt);
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

    private static boolean consumeFault(Collection<?> values) {
        if (FAULT_TARGET.get() != values) {
            return false;
        }
        int left = FAULTS_LEFT.get();
        if (left <= 0) {
            return false;
        }
        FAULTS_LEFT.compareAndSet(left, left - 1);
        return true;
    }

    /** GameTests only: the next {@code failures} snapshots of exactly this collection fail as a racing thread would make them; null clears it. */
    public static void failSnapshotsForTest(@Nullable Collection<?> target, int failures) {
        FAULT_TARGET.set(target);
        FAULTS_LEFT.set(target == null ? 0 : failures);
    }

    /** The block entity types (and "unknown:", "leftover:", "recreated:", "race:", "skipped:" marks) warned about so far in this run. */
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

    /** How many snapshot attempts failed because the map changed under them, so far. */
    public static long retries() {
        return RETRIES.sum();
    }

    /** How many unloads had their passes deferred to the next server task, so far. */
    public static long deferredUnloads() {
        return DEFERRED_UNLOADS.sum();
    }

    /** How many passes were skipped for good (a second failure from the deferred task, or a client chunk), so far. */
    public static long skippedPasses() {
        return SKIPPED_PASSES.sum();
    }

    /** How many chunks' passes are waiting for their deferred task right now. */
    public static int pendingDeferrals() {
        return DEFERRED.size();
    }
}
