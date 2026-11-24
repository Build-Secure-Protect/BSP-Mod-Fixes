package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.mffs.LoadedField;
import com.mrgregles.bsp_mod_fixes.fix.mffs.MffsGate;
import com.mrgregles.bsp_mod_fixes.fix.mffs.SelectionThread;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * MF-41 to MF-43: a projector selects its field on the server thread (F-43) and a force field stays within loaded chunks (F-44).
 * The rules and the gate are pure; with {@code -PwithMffs} a real projector stands far from the test, in a chunk this test keeps
 * loaded by itself, with a cube field reaching the unloaded chunks around it: the chunk loads and unloads around it and the field
 * block entities those unloads kill are counted for twenty seconds, as shipped ({@code -PskipFixes}) and with the fixes; and two
 * projectors on one frequency with Fusion modules still merge their fields. No class of MFFS is named here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class MffsLoadedFieldGameTests {
    private static final String[] MIXINS = {"MffsSelectionThreadMixin", "MffsLoadedFieldMixin", "MffsFusionProjectMixin", "MffsFieldBlockLoadMixin"};
    /** The two projector tests run in a batch of their own: the F-35 test lowers the block limit for a moment, and batches run one after another. */
    private static final String BATCH = "bsp_mod_fixes_mffs_field";
    /** Waits, twenty ticks at a time up to {@code maxTicks}, until {@code ready} holds, then runs {@code then}; the server ticks hundreds of times a second here. */
    private static void waitUntil(GameTestHelper h, java.util.function.BooleanSupplier ready, int waited, int maxTicks, Runnable then) {
        if (!ready.getAsBoolean() && waited < maxTicks) {
            h.runAfterDelay(20, () -> waitUntil(h, ready, waited + 20, maxTicks, then));
            return;
        }
        then.run();
    }

    /** Holds the far projector's chunk loaded and block-ticking, and its eight neighbours loaded but not ticking, nothing beyond (a forced chunk would load two rings). */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("bsp_mod_fixes_mffs_test", Comparator.comparingLong(ChunkPos::toLong));

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void the_loaded_field_and_selection_thread_rules_and_the_mffs_gate_hold(GameTestHelper h) {
        h.assertTrue(LoadedField.keep(true, false) && !LoadedField.keep(true, true) && !LoadedField.keep(false, false), "a position is left alone only with the switch on and its chunk not loaded");
        Pair<?, ?> answer = LoadedField.notProjectable();
        h.assertTrue(answer.getFirst() == Blocks.AIR.defaultBlockState() && Boolean.FALSE.equals(answer.getSecond()), "the cache answer for an untouched position is air, not projectable");
        h.assertTrue(SelectionThread.onServer(true, true) && !SelectionThread.onServer(true, false) && !SelectionThread.onServer(false, true), "selection runs on the server only with the switch on in a server level");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_SELECT_ON_SERVER_THREAD.get() && FixesConfig.MFFS_FIELD_STAYS_IN_LOADED_CHUNKS.get() && SelectionThread.enabled() && LoadedField.enabled(), "both switches are on by default");
        for (String mixin : MIXINS) {
            h.assertTrue(MffsGate.gates(mixin), mixin + " is gated by the MFFS gate");
        }
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(SelectionThread.ID) && f.targetMod().equals(MffsGate.TARGET)), "the registry knows F-43 as an MFFS fix");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(LoadedField.ID) && f.targetMod().equals(MffsGate.TARGET)), "the registry knows F-44 as an MFFS fix");
        h.assertTrue(MffsGate.verdict(Optional.of(MffsGate.VERSION), true).applies() && MffsGate.verdict(Optional.empty(), true) == MffsGate.Verdict.ABSENT, "the gate applies to exactly " + MffsGate.VERSION);
        if (!ModList.get().isLoaded(MffsGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.get(SelectionThread.ID) != null && skipped.get(SelectionThread.ID).equals(MffsGate.Verdict.ABSENT.reason()), "without MFFS the plugin skipped F-43 as absent: " + skipped);
            h.assertTrue(skipped.get(LoadedField.ID) != null && skipped.get(LoadedField.ID).equals(MffsGate.Verdict.ABSENT.reason()), "without MFFS the plugin skipped F-44 as absent: " + skipped);
        }
        h.succeed();
    }

    /** The chunk loads and unloads around a far projector, and the field block entities each unload kills. */
    private static final class ChunkWatch {
        final ServerLevel level;
        final ChunkPos own;
        final int reach;
        final Map<Long, int[]> perChunk = new TreeMap<>(); // loads, unloads, block entities killed
        final Consumer<ChunkEvent.Load> onLoad = this::load;
        final Consumer<ChunkEvent.Unload> onUnload = this::unload;

        ChunkWatch(ServerLevel level, ChunkPos own, int reach) {
            this.level = level;
            this.own = own;
            this.reach = reach;
        }

        /** The chunks beyond the ticket's ring: distance two to {@code reach} from the projector's chunk. */
        private boolean watched(ChunkEvent event) {
            if (event.getLevel() != level) {
                return false;
            }
            ChunkPos pos = event.getChunk().getPos();
            int distance = Math.max(Math.abs(pos.x - own.x), Math.abs(pos.z - own.z));
            return distance >= 2 && distance <= reach;
        }

        private void load(ChunkEvent.Load event) {
            if (watched(event) && event.getChunk() instanceof LevelChunk) {
                perChunk.computeIfAbsent(event.getChunk().getPos().toLong(), k -> new int[3])[0]++;
            }
        }

        private void unload(ChunkEvent.Unload event) {
            if (watched(event) && event.getChunk() instanceof LevelChunk chunk) {
                int[] counts = perChunk.computeIfAbsent(chunk.getPos().toLong(), k -> new int[3]);
                counts[1]++;
                counts[2] += MffsTestBridge.forceFieldEntities(chunk);
            }
        }

        int loads() { return perChunk.values().stream().mapToInt(c -> c[0]).sum(); }
        int unloads() { return perChunk.values().stream().mapToInt(c -> c[1]).sum(); }
        int killed() { return perChunk.values().stream().mapToInt(c -> c[2]).sum(); }

        String report() {
            StringBuilder sb = new StringBuilder();
            perChunk.forEach((key, c) -> sb.append(new ChunkPos(key)).append(" loads ").append(c[0]).append(" unloads ").append(c[1]).append(" killed ").append(c[2]).append("; "));
            return perChunk.size() + " chunks touched: " + sb;
        }
    }

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 9000, batch = BATCH)
    public void a_far_projectors_field_loads_no_chunk_beyond_its_own_and_its_selection_runs_on_the_server(GameTestHelper h) {
        if (!ModList.get().isLoaded(MffsGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: MFFS is not loaded, no projector is placed (add -PwithMffs)");
            h.succeed();
            return;
        }
        boolean threadOn = Fixes.active().stream().anyMatch(f -> f.id().equals(SelectionThread.ID));
        boolean loadedOn = Fixes.active().stream().anyMatch(f -> f.id().equals(LoadedField.ID));
        h.assertTrue(threadOn || MixinFixPlugin.SKIP_REASON.equals(Fixes.skipped().get(SelectionThread.ID)), "F-43 is applied or switched off for this run");
        h.assertTrue(loadedOn || MixinFixPlugin.SKIP_REASON.equals(Fixes.skipped().get(LoadedField.ID)), "F-44 is applied or switched off for this run");
        ServerLevel level = h.getLevel();
        // Forty-eight chunks east of the test: beyond the spawn chunks, so nothing but this test keeps anything loaded there.
        BlockPos at = h.absolutePos(new BlockPos(12, 4, 12)).offset(768, 40, 0);
        ChunkPos own = new ChunkPos(at);
        boolean loadedBefore = level.getChunkSource().hasChunk(own.x, own.z);
        level.getChunkSource().addRegionTicket(TICKET, own, 1, own); // level 32 here, 33 on the ring: loaded, nothing beyond
        level.setBlockAndUpdate(at, MffsTestBridge.projector());
        BlockEntity projector = level.getBlockEntity(at);
        h.assertTrue(projector != null, "the far projector's block entity exists");
        MffsTestBridge.setCubeOfRadius(projector, 40); // a shell of about forty thousand positions, reaching two to three chunks beyond its own
        ChunkWatch watch = new ChunkWatch(level, own, 5);
        MinecraftForge.EVENT_BUS.addListener(watch.onLoad);
        MinecraftForge.EVENT_BUS.addListener(watch.onUnload);
        long serverBefore = SelectionThread.onServerThread();
        long poolBefore = SelectionThread.onPool();
        long skippedBefore = LoadedField.skippedPositions();
        MffsTestBridge.power(projector);
        Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-41): far projector at {} in chunk {} (loaded before: {}), F-43 {}, F-44 {}", at, own, loadedBefore, threadOn ? "on" : "off", loadedOn ? "on" : "off");
        // The field's forty thousand positions are calculated on the pool and the first blocks placed before the clock starts;
        // then two thousand ticks of projection are watched.
        waitUntil(h, () -> MffsTestBridge.calculatedPositions(projector) > 0 && fieldBlocksAround(level, own) > 0, 0, 4000, () -> {
        Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-41): calculated {} positions and placed the first blocks after {} ticks; beyond the ring so far {} loads", MffsTestBridge.calculatedPositions(projector), h.getTick(), watch.loads());
        h.runAfterDelay(2000, () -> {
            MinecraftForge.EVENT_BUS.unregister(watch.onLoad);
            MinecraftForge.EVENT_BUS.unregister(watch.onUnload);
            int inOwn = fieldBlocksAround(level, own);
            long onServer = SelectionThread.onServerThread() - serverBefore;
            long onPool = SelectionThread.onPool() - poolBefore;
            long skipped = LoadedField.skippedPositions() - skippedBefore;
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-41): after 2000 more ticks: {} calculated positions; {} field blocks in the ticket's three-by-three; beyond it {} loads, {} unloads, {} field block entities killed by unloads; selections on the server {}, on the pool {}; positions skipped by F-44 {}",
                    MffsTestBridge.calculatedPositions(projector), inOwn, watch.loads(), watch.unloads(), watch.killed(), onServer, onPool, skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-41): {}", watch.report());
            try {
            h.assertTrue(MffsTestBridge.calculatedPositions(projector) > 20000, "the field was calculated: " + MffsTestBridge.calculatedPositions(projector));
            h.assertTrue(inOwn > 0, "the field was built within the loaded chunks: " + inOwn); // at this spot of the test world most of the two face planes in the ticking chunk are solid terrain
            if (loadedOn) {
                h.assertTrue(watch.loads() == 0 && watch.unloads() == 0, "with F-44 the projector loads no chunk beyond the ticket's ring: " + watch.report());
                h.assertTrue(skipped > 0, "F-44 skipped the positions in unloaded chunks: " + skipped);
            }
            if (threadOn) {
                h.assertTrue(onServer > 0 && onPool == 0, "with F-43 every selection ran on the server thread: " + onServer + " on the server, " + onPool + " on the pool");
            }
            } finally {
                // Cleanup, whatever the assertions said: the projector off and gone, the chunk released.
                MffsTestBridge.unpower(projector);
                MffsTestBridge.destroyField(projector);
                level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                level.getChunkSource().removeRegionTicket(TICKET, own, 1, own);
            }
            h.succeed();
        });
        });
    }

    /** The force field block entities in the ticket's three-by-three around the projector's chunk. */
    private static int fieldBlocksAround(ServerLevel level, ChunkPos own) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(own.x + dx, own.z + dz);
                n += chunk == null ? 0 : MffsTestBridge.forceFieldEntities(chunk);
            }
        }
        return n;
    }

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 6000, batch = BATCH)
    public void two_projectors_on_one_frequency_with_fusion_modules_still_merge_their_fields(GameTestHelper h) {
        if (!ModList.get().isLoaded(MffsGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: MFFS is not loaded, no projector is placed (add -PwithMffs)");
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(6, 4, 12));
        BlockPos b = h.absolutePos(new BlockPos(14, 4, 12));
        level.setBlockAndUpdate(a, MffsTestBridge.projector());
        level.setBlockAndUpdate(b, MffsTestBridge.projector());
        BlockEntity pa = level.getBlockEntity(a);
        BlockEntity pb = level.getBlockEntity(b);
        h.assertTrue(pa != null && pb != null, "both projectors exist");
        for (BlockEntity p : new BlockEntity[]{pa, pb}) {
            MffsTestBridge.setCubeOfRadius(p, 5);
            MffsTestBridge.setFusionModule(p);
            h.assertTrue(MffsTestBridge.hasFusionModule(p), "the projector has its Fusion module");
            MffsTestBridge.setFrequency(p, 7);
            MffsTestBridge.power(p);
        }
        // Both shells of about six hundred positions at twenty-eight per ten ticks, then Fusion's removals: wait until both outer
        // walls stand, then a thousand more ticks for the removals.
        BlockPos aOuter = a.offset(-5, 0, 0);
        BlockPos bOuter = b.offset(5, 0, 0);
        waitUntil(h, () -> MffsTestBridge.isForceField(level.getBlockState(aOuter)) && MffsTestBridge.isForceField(level.getBlockState(bOuter)), 0, 4000, () -> h.runAfterDelay(1000, () -> {
            BlockPos aShellOutside = a.offset(-5, 0, 0); // x = 1: A's shell, outside B's cube
            BlockPos aShellInsideB = a.offset(5, 0, 0);  // x = 11: A's shell, inside B's cube (9 to 19)
            BlockPos bShellInsideA = b.offset(-5, 0, 0); // x = 9: B's shell, inside A's cube (1 to 11)
            BlockPos bShellOutside = b.offset(5, 0, 0);  // x = 19: B's shell, outside A's cube
            h.assertTrue(MffsTestBridge.isForceField(level.getBlockState(aShellOutside)), "A's shell stands where B's field does not reach");
            h.assertTrue(MffsTestBridge.isForceField(level.getBlockState(bShellOutside)), "B's shell stands where A's field does not reach");
            h.assertTrue(!MffsTestBridge.isForceField(level.getBlockState(aShellInsideB)), "Fusion left A's shell out inside B's field");
            h.assertTrue(!MffsTestBridge.isForceField(level.getBlockState(bShellInsideA)), "Fusion left B's shell out inside A's field");
            for (BlockEntity p : new BlockEntity[]{pa, pb}) {
                MffsTestBridge.unpower(p);
                MffsTestBridge.destroyField(p);
            }
            h.succeed();
        }));
    }
}
