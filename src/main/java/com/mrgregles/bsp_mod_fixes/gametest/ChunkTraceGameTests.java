package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.chunks.ChunkWatch;
import com.mrgregles.bsp_mod_fixes.fix.chunks.OffThreadTracer;
import com.mrgregles.bsp_mod_fixes.fix.chunks.UnloadIteration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MF-48: F-48's snapshots, retries and deferral under a writer on another thread; F-51's off-thread tracer; F-52's watch-area
 * tracer. Detached {@code LevelChunk}s (never in the chunk map, nothing in the world touched) as in {@code ChunkUnloadGameTests};
 * the F-52 part forces and releases a far chunk of the test level. With {@code -PskipFixes=LevelChunkUnloadMixin,
 * LevelChunkOffThreadMixin,DistanceManagerTicketMixin} each part asserts the as-shipped behaviour instead.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class ChunkTraceGameTests {
    private static int chunkCounter;
    private static final TicketType<ChunkPos> TEST_TICKET = TicketType.create("bsp_mod_fixes_test", Comparator.comparingLong(ChunkPos::toLong));

    /** A collection whose iteration throws {@code ConcurrentModificationException} the first {@code failures} times, as a racing map does. */
    private static final class FlakyValues extends AbstractCollection<String> {
        private final List<String> values;
        private final AtomicInteger failures;

        FlakyValues(List<String> values, int failures) {
            this.values = values;
            this.failures = new AtomicInteger(failures);
        }

        @Override
        public Iterator<String> iterator() {
            Iterator<String> it = values.iterator();
            if (failures.getAndDecrement() > 0) {
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public String next() {
                        throw new ConcurrentModificationException("the test's racing thread");
                    }
                };
            }
            return it;
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    /** A map whose {@code values()} always throws on iteration: a map no snapshot can copy. */
    private static final class UncopiableMap extends HashMap<String, String> {
        @Override
        public Collection<String> values() {
            return new FlakyValues(new ArrayList<>(super.values()), Integer.MAX_VALUE);
        }
    }

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 400)
    public void f48_a_copy_of_the_block_entity_map_is_retaken_when_another_thread_changes_it_and_the_passes_are_deferred_when_it_cannot_be(GameTestHelper h) {
        // the pure snapshot: two failures then a copy; a collection that never copies; the pass over such a map is aborted untouched
        UnloadIteration.Snapshot<String> two = UnloadIteration.snapshot(new FlakyValues(List.of("a", "b"), 2), UnloadIteration.SNAPSHOT_ATTEMPTS);
        h.assertTrue(two != null && two.values().equals(List.of("a", "b")) && two.retries() == 2, "two failed attempts, then the copy: " + two);
        h.assertTrue(UnloadIteration.snapshot(new FlakyValues(List.of("a"), 99), UnloadIteration.SNAPSHOT_ATTEMPTS) == null, "a collection that always throws is given up after the attempts");
        h.assertTrue(UnloadIteration.snapshot(new FlakyValues(List.of("a"), UnloadIteration.SNAPSHOT_ATTEMPTS - 1), UnloadIteration.SNAPSHOT_ATTEMPTS).retries() == UnloadIteration.SNAPSHOT_ATTEMPTS - 1, "the last attempt may still succeed");
        UncopiableMap uncopiable = new UncopiableMap();
        uncopiable.put("k", "K");
        List<String> ran = new ArrayList<>();
        UnloadIteration.PassReport aborted = UnloadIteration.runPass(uncopiable, ran::add, v -> "k");
        h.assertTrue(aborted.aborted() && ran.isEmpty() && aborted.retries() == UnloadIteration.SNAPSHOT_ATTEMPTS, "a pass whose first copy fails every attempt is aborted before running anything: " + aborted);
        UnloadIteration.SweepReport sweepAborted = UnloadIteration.sweep(uncopiable, List.of(), v -> {}, UnloadIteration.MAX_SWEEP_ROUNDS);
        h.assertTrue(sweepAborted.aborted() && sweepAborted.removed().isEmpty(), "a sweep whose copy fails is aborted: " + sweepAborted);
        Map<String, String> plain = new LinkedHashMap<>(Map.of("k", "K"));
        UnloadIteration.PassReport quiet = UnloadIteration.runPass(plain, v -> {}, v -> "k");
        h.assertTrue(!quiet.aborted() && quiet.retries() == 0 && quiet.lostDiffs() == 0 && quiet.visited().equals(List.of("K")), "a quiet pass: no retries, the visited values reported: " + quiet);

        boolean fixOn = !Fixes.skipped().containsKey(UnloadIteration.ID);
        if (!fixOn) {
            // as shipped: the live walk throws when another thread changes the map under it (ChunkUnloadGameTests covers the hook case)
            h.succeed();
            return;
        }

        // the race: a thread putting and removing chest block entities in the live map while the unload runs, fifty rounds,
        // never an exception; the chunks whose passes were deferred run from the next server task once the writer has stopped
        long retriesBefore = UnloadIteration.retries();
        long skippedBefore = UnloadIteration.skippedPasses();
        List<Raced> raced = new ArrayList<>();
        for (int round = 0; round < 50; round++) {
            raced.add(race(h));
        }
        h.assertTrue(UnloadIteration.skippedPasses() == skippedBefore, "no pass was skipped for good while the writer ran only during the unload call");
        long retriesSeen = UnloadIteration.retries() - retriesBefore;
        Fixes.LOGGER.info("MF-48: {} snapshot retries over 50 raced unloads, {} deferred so far, {} pending", retriesSeen, UnloadIteration.deferredUnloads(), UnloadIteration.pendingDeferrals());

        // the deferral, made certain: the next five copies of this chunk's map fail, as five racing writes would make them; the
        // unload returns with the map untouched and the passes queued; the server task runs them, sweeps and clears
        Raced deferred = detached(h, 3);
        UnloadIteration.failSnapshotsForTest(deferred.chunk.getBlockEntities().values(), UnloadIteration.SNAPSHOT_ATTEMPTS);
        long deferredBefore = UnloadIteration.deferredUnloads();
        deferred.chunk.clearAllBlockEntities();
        UnloadIteration.failSnapshotsForTest(null, 0);
        h.assertTrue(deferred.chunk.getBlockEntities().size() == 3, "the map is left for the deferred task, got " + deferred.chunk.getBlockEntities().size());
        h.assertTrue(UnloadIteration.deferredUnloads() == deferredBefore + 1 && UnloadIteration.pendingDeferrals() >= 1, "the unload is deferred: " + UnloadIteration.pendingDeferrals() + " pending");
        h.assertTrue(deferred.unloaded.isEmpty() && deferred.chests.stream().noneMatch(BlockEntity::isRemoved), "nothing ran yet");

        // the deferral that fails again: ten failures cover the unload's five and the task's five; the passes are skipped, the map cleared
        Raced given = detached(h, 2);
        UnloadIteration.failSnapshotsForTest(given.chunk.getBlockEntities().values(), 2 * UnloadIteration.SNAPSHOT_ATTEMPTS);
        given.chunk.clearAllBlockEntities();
        h.assertTrue(given.chunk.getBlockEntities().size() == 2 && UnloadIteration.pendingDeferrals() >= 2, "the second unload is deferred too");

        h.runAfterDelay(10, () -> {
            UnloadIteration.failSnapshotsForTest(null, 0);
            for (Raced r : raced) {
                h.assertTrue(r.unloaded.size() == r.chests.size() && r.chests.stream().allMatch(BlockEntity::isRemoved), "every raced chunk's block entities got onChunkUnloaded and setRemoved: " + r.unloaded.size() + " of " + r.chests.size());
            }
            h.assertTrue(deferred.chunk.getBlockEntities().isEmpty(), "the deferred task cleared the map");
            h.assertTrue(deferred.unloaded.size() == 3 && deferred.chests.stream().allMatch(BlockEntity::isRemoved), "the deferred task ran onChunkUnloaded and setRemoved over all three: " + deferred.unloaded.size());
            String key = "race:" + h.getLevel().dimension().location() + deferred.chunk.getPos();
            h.assertTrue(UnloadIteration.warned().contains(key), "the race is warned once for the chunk: " + key + " in " + UnloadIteration.warned());
            h.assertTrue(given.chunk.getBlockEntities().isEmpty(), "the given-up unload still clears the map");
            h.assertTrue(given.unloaded.isEmpty() && given.chests.stream().noneMatch(BlockEntity::isRemoved), "the given-up unload ran no pass: its block entities are dropped as the crash left them");
            h.assertTrue(UnloadIteration.skippedPasses() == skippedBefore + 1, "one unload skipped for good, got " + (UnloadIteration.skippedPasses() - skippedBefore));
            h.assertTrue(UnloadIteration.warned().contains("skipped:" + h.getLevel().dimension().location() + given.chunk.getPos()), "the skip is always warned, under its own key: " + UnloadIteration.warned());
            h.assertTrue(UnloadIteration.pendingDeferrals() == 0, "no deferral pending, got " + UnloadIteration.pendingDeferrals());
            h.assertTrue(OffThreadTracer.describeWriter(deferred.chunk).equals("no off-thread writer seen by F-51") || Fixes.skipped().containsKey(OffThreadTracer.ID) || !OffThreadTracer.enabled(),
                    "the test's writers bypass the traced methods, so F-48's line says F-51 saw none: " + OffThreadTracer.describeWriter(deferred.chunk));
            h.succeed();
        });
    }

    private record Raced(LevelChunk chunk, List<ChestBlockEntity> chests, List<BlockEntity> unloaded) {}

    /** A detached chunk far from anything holding {@code n} chests that record their onChunkUnloaded. */
    private static Raced detached(GameTestHelper h, int n) {
        ChunkPos pos = new ChunkPos(61_000 + chunkCounter++, 61_000);
        LevelChunk chunk = new LevelChunk(h.getLevel(), pos);
        Map<BlockPos, BlockEntity> map = chunk.getBlockEntities();
        BlockState chest = Blocks.CHEST.defaultBlockState();
        List<ChestBlockEntity> chests = new ArrayList<>();
        List<BlockEntity> unloaded = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BlockPos p = pos.getWorldPosition().offset(i & 15, 64 + (i >> 4), (i >> 8) & 15);
            ChestBlockEntity be = new ChestBlockEntity(p, chest) {
                @Override
                public void onChunkUnloaded() {
                    super.onChunkUnloaded();
                    unloaded.add(this);
                }
            };
            chests.add(be);
            map.put(p, be);
        }
        return new Raced(chunk, chests, unloaded);
    }

    /** One raced unload: a writer thread adds and removes chests at other positions of the live map while clearAllBlockEntities runs. */
    private static Raced race(GameTestHelper h) {
        Raced r = detached(h, 200);
        Map<BlockPos, BlockEntity> map = r.chunk.getBlockEntities();
        BlockState chest = Blocks.CHEST.defaultBlockState();
        BlockPos base = r.chunk.getPos().getWorldPosition();
        AtomicBoolean stop = new AtomicBoolean();
        Thread writer = new Thread(() -> {
            int i = 0;
            while (!stop.get()) {
                BlockPos p = base.offset(i & 15, 200 + ((i >> 4) & 15), 8);
                if ((i & 1) == 0) {
                    map.put(p, new ChestBlockEntity(p, chest));
                } else {
                    map.remove(p);
                }
                i++;
            }
        }, "bsp-mod-fixes-test-racer");
        writer.setDaemon(true);
        writer.start();
        try {
            r.chunk.clearAllBlockEntities();
        } catch (RuntimeException e) {
            stop.set(true);
            h.fail("the unload threw under a racing writer: " + e);
        } finally {
            stop.set(true);
        }
        try {
            writer.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // whatever the writer left at its own positions is not a chest of the unload: the chests are asserted on, not the map,
        // whose size field a clear racing a put can leave wrong (a deferred chunk's map is cleared by its task, once the writer has stopped)
        return r;
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void f51_a_thread_other_than_the_servers_changing_a_chunks_block_entities_is_named_once_with_its_stack(GameTestHelper h) {
        h.assertTrue(FixesConfig.SPEC.isLoaded(), "the server config is loaded on the test server");
        boolean fixOn = !Fixes.skipped().containsKey(OffThreadTracer.ID);
        ServerLevel level = h.getLevel();
        ChunkPos pos = new ChunkPos(62_000 + chunkCounter++, 62_000);
        LevelChunk chunk = new LevelChunk(level, pos);
        BlockState chest = Blocks.CHEST.defaultBlockState();
        BlockPos p = pos.getWorldPosition().offset(1, 64, 1);
        ChestBlockEntity removed = new ChestBlockEntity(p, chest);
        removed.setRemoved();
        String dimension = level.dimension().location().toString();
        String threadName = "bsp-mod-fixes-test-writer";
        String key = threadName + "|" + ChunkTraceGameTests.class.getName();
        long seen = OffThreadTracer.seen();

        // the server thread: nothing
        chunk.setBlockEntity(new ChestBlockEntity(p, chest));
        chunk.removeBlockEntity(p);
        h.assertTrue(OffThreadTracer.seen() == seen, "the server thread's own writes are not traced");

        // another thread: setBlockEntity at HEAD (the detached chunk has no chest block there, so the method returns early, after the tracer)
        onThread(threadName, () -> chunk.setBlockEntity(new ChestBlockEntity(p, chest)));
        if (fixOn) {
            h.assertTrue(OffThreadTracer.seen() == seen + 1, "one off-thread write seen, got " + (OffThreadTracer.seen() - seen));
            h.assertTrue(OffThreadTracer.warned().contains(key), "the WARN key is the thread's name and the calling class: " + OffThreadTracer.warned());
            String writer = OffThreadTracer.lastWriter(dimension, pos);
            h.assertTrue(writer != null && writer.contains(threadName) && writer.contains(ChunkTraceGameTests.class.getName()), "the chunk's last writer is remembered: " + writer);
            h.assertTrue(OffThreadTracer.describeWriter(chunk).startsWith("F-51 last saw thread '" + threadName + "'"), "F-48's clause names it: " + OffThreadTracer.describeWriter(chunk));
        } else {
            h.assertTrue(OffThreadTracer.seen() == seen, "with the mixin off nothing is traced");
            h.assertTrue(OffThreadTracer.describeWriter(chunk).equals("F-51 (the off-thread tracer) is not applied"), "F-48's clause says the tracer is not applied: " + OffThreadTracer.describeWriter(chunk));
        }

        // the reader's remove: getBlockEntity drops a removed block entity's entry; off the server thread that is traced at the remove
        chunk.getBlockEntities().put(p, removed);
        onThread(threadName, () -> chunk.getBlockEntity(p, LevelChunk.EntityCreationType.CHECK));
        h.assertTrue(!chunk.getBlockEntities().containsKey(p), "the lookup dropped the removed entry");
        if (fixOn) {
            h.assertTrue(OffThreadTracer.seen() == seen + 2, "the drop is seen, got " + (OffThreadTracer.seen() - seen));
            h.assertTrue(OffThreadTracer.warned().stream().filter(k -> k.equals(key)).count() == 1, "still one WARN key for the thread and class");
            // another thread name: a second WARN
            onThread(threadName + "-2", () -> chunk.removeBlockEntity(p));
            h.assertTrue(OffThreadTracer.warned().contains(threadName + "-2|" + ChunkTraceGameTests.class.getName()), "a second thread gets its own WARN: " + OffThreadTracer.warned());
            // the switch off: nothing
            boolean saved = FixesConfig.DIAG_OFF_THREAD_TRACER.get();
            try {
                FixesConfig.DIAG_OFF_THREAD_TRACER.set(false);
                h.assertFalse(OffThreadTracer.enabled(), "the switch reads false");
                long before = OffThreadTracer.seen();
                onThread(threadName, () -> chunk.removeBlockEntity(p));
                h.assertTrue(OffThreadTracer.seen() == before, "with the switch off nothing is traced");
                h.assertTrue(OffThreadTracer.describeWriter(chunk).equals("F-51 (the off-thread tracer) is switched off"), "F-48's clause says so: " + OffThreadTracer.describeWriter(chunk));
            } finally {
                FixesConfig.DIAG_OFF_THREAD_TRACER.set(saved);
            }
        } else {
            h.assertTrue(OffThreadTracer.seen() == seen, "with the mixin off the drop is not traced either");
        }
        h.succeed();
    }

    private static void onThread(String name, Runnable body) {
        Throwable[] failure = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure[0] = e;
            }
        }, name);
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (failure[0] != null) {
            throw new IllegalStateException("the test thread failed", failure[0]);
        }
    }

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 400)
    public void f52_tickets_and_loads_of_a_watched_chunk_are_logged_with_one_stack_per_ticket_type_and_caller_a_minute(GameTestHelper h) {
        // the pure parts: the area line, the frame filter, the trace throttle
        Optional<ChunkWatch.Area> area = ChunkWatch.parse("allthemodium:the_other 464 464 468 468");
        h.assertTrue(area.isPresent() && area.get().equals(new ChunkWatch.Area("allthemodium:the_other", 464, 464, 468, 468)), "the documented example parses: " + area);
        h.assertTrue(ChunkWatch.parse(" compactmachines:compact_world   128 -64 128 -64 ").map(a -> a.contains("compactmachines:compact_world", 128, -64)).orElse(false), "spaces and negatives are fine");
        h.assertTrue(ChunkWatch.parse("minecraft:overworld 5 5 1 1").get().equals(new ChunkWatch.Area("minecraft:overworld", 1, 1, 5, 5)), "min and max may come in either order");
        h.assertTrue(ChunkWatch.parse("overworld 1 1").isEmpty() && ChunkWatch.parse("Bad:Dim 1 1 1 1").isEmpty() && ChunkWatch.parse("minecraft:overworld 1 1 1 x").isEmpty(), "a short, a mis-cased and a non-numeric line are refused");
        h.assertTrue(ChunkWatch.isValidLine("minecraft:overworld 0 0 0 0") && !ChunkWatch.isValidLine(3) && !ChunkWatch.isValidLine("nope"), "the validator");
        h.assertTrue(!area.get().contains(463, 464) && area.get().contains(468, 468) && !area.get().contains("minecraft:overworld", 465, 465), "inclusive bounds, the dimension checked");
        String throttleKey = "test|" + System.nanoTime();
        h.assertTrue(ChunkWatch.mayTrace(throttleKey, 1_000) && !ChunkWatch.mayTrace(throttleKey, 1_000 + ChunkWatch.TRACE_INTERVAL_MS - 1) && ChunkWatch.mayTrace(throttleKey, 1_000 + ChunkWatch.TRACE_INTERVAL_MS), "one stack per key a minute");

        h.assertTrue(FixesConfig.SPEC.isLoaded(), "the server config is loaded on the test server");
        boolean fixOn = !Fixes.skipped().containsKey(ChunkWatch.ID);
        ServerLevel level = h.getLevel();
        String dimension = level.dimension().location().toString();
        ChunkPos watched = new ChunkPos(50_000, 50_000 + chunkCounter++);
        ChunkPos outside = new ChunkPos(watched.x + 100, watched.z);
        List<? extends String> savedAreas = FixesConfig.DIAG_CHUNK_WATCH_AREAS.get();
        boolean savedSwitch = FixesConfig.DIAG_CHUNK_WATCH_TRACER.get();
        try {
            FixesConfig.DIAG_CHUNK_WATCH_AREAS.set(List.of(dimension + " " + watched.x + " " + watched.z + " " + watched.x + " " + watched.z));
            h.assertTrue(ChunkWatch.areas().size() == 1 && ChunkWatch.areas().get(0).contains(dimension, watched.x, watched.z), "the area is read from the config: " + ChunkWatch.areas());
            long events = ChunkWatch.events();
            long traces = ChunkWatch.traces();
            int recentBefore = ChunkWatch.recent().size();

            // a forced chunk: the FORCED ticket's add, the chunk's load (setChunkForced loads it at once), the ticket's removal
            level.setChunkForced(watched.x, watched.z, true);
            level.setChunkForced(watched.x, watched.z, false);
            // a region ticket of a type of the test's own, added and removed twice: four lines, one stack
            level.getChunkSource().addRegionTicket(TEST_TICKET, watched, 1, watched);
            level.getChunkSource().removeRegionTicket(TEST_TICKET, watched, 1, watched);
            level.getChunkSource().addRegionTicket(TEST_TICKET, watched, 1, watched);
            level.getChunkSource().removeRegionTicket(TEST_TICKET, watched, 1, watched);
            // a chunk outside the area: nothing
            level.getChunkSource().addRegionTicket(TEST_TICKET, outside, 1, outside);
            level.getChunkSource().removeRegionTicket(TEST_TICKET, outside, 1, outside);

            List<String> lines = ChunkWatch.recent();
            List<String> mine = lines.subList(Math.min(recentBefore, lines.size()), lines.size());
            String me = ChunkTraceGameTests.class.getName();
            if (fixOn) {
                h.assertTrue(count(mine, "ticket added to chunk [" + watched.x + ", " + watched.z + "] in " + dimension + ": forced level 31") == 1, "the forced ticket's add, once: " + mine);
                h.assertTrue(count(mine, "ticket removed from chunk [" + watched.x + ", " + watched.z + "] in " + dimension + ": forced level 31") == 1, "the forced ticket's removal, once: " + mine);
                h.assertTrue(count(mine, "chunk [" + watched.x + ", " + watched.z + "] in " + dimension + " loaded") == 1, "the chunk's load, once: " + mine);
                h.assertTrue(count(mine, ": bsp_mod_fixes_test level") == 4, "the test ticket's two adds and two removals: " + mine);
                // a frame prints with its module in front ("TRANSFORMER/bsp_mod_fixes@<version>/<class>.<method>(<file>:<line>)")
                h.assertTrue(mine.stream().filter(l -> l.startsWith("ticket ")).allMatch(l -> l.contains(" by ") && l.contains(me + ".f52_")), "every ticket line names this class and method as the caller: " + mine);
                h.assertTrue(mine.stream().noneMatch(l -> l.contains("[" + outside.x + ", ")), "the chunk outside the area is not logged: " + mine);
                // loading the forced chunk adds Minecraft's own "unknown" and "light" tickets as well, each a type of its own: one stack per type
                Set<String> types = new java.util.TreeSet<>();
                for (String l : mine) {
                    if (l.startsWith("ticket ")) {
                        types.add(l.substring(l.indexOf(": ", l.indexOf(" in ")) + 2, l.indexOf(" level ")));
                    }
                }
                h.assertTrue(types.contains("forced") && types.contains("bsp_mod_fixes_test"), "the forced and the test ticket types are among the lines: " + types);
                h.assertTrue(ChunkWatch.traces() == traces + types.size(), "one stack per ticket type (the caller is this class for all): " + types.size() + " types, got " + (ChunkWatch.traces() - traces) + " stacks");
                h.assertTrue(ChunkWatch.events() >= events + 7, "at least seven lines (the unload's may follow), got " + (ChunkWatch.events() - events));

                // the switch off: nothing
                FixesConfig.DIAG_CHUNK_WATCH_TRACER.set(false);
                h.assertFalse(ChunkWatch.enabled(), "the switch reads false");
                long before = ChunkWatch.events();
                level.getChunkSource().addRegionTicket(TEST_TICKET, watched, 1, watched);
                level.getChunkSource().removeRegionTicket(TEST_TICKET, watched, 1, watched);
                h.assertTrue(ChunkWatch.events() == before, "with the switch off nothing is logged");
                FixesConfig.DIAG_CHUNK_WATCH_TRACER.set(savedSwitch);
            } else {
                h.assertTrue(mine.isEmpty() && ChunkWatch.events() == events, "with the mixin and its handlers off nothing is logged: " + mine);
            }
        } finally {
            FixesConfig.DIAG_CHUNK_WATCH_AREAS.set(savedAreas);
            FixesConfig.DIAG_CHUNK_WATCH_TRACER.set(savedSwitch);
        }
        h.assertTrue(ChunkWatch.areas().isEmpty() == savedAreas.isEmpty(), "the config is put back");
        h.succeed();
    }

    private static int count(List<String> lines, String part) {
        int n = 0;
        for (String line : lines) {
            if (line.contains(part)) {
                n++;
            }
        }
        return n;
    }
}
