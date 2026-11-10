package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.cm.CmGate;
import com.mrgregles.bsp_mod_fixes.fix.cm.RoomHolds;
import com.mrgregles.bsp_mod_fixes.fix.cm.RoomLoader;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * MF-27: a Compact Machines room is loaded only while a machine block bound to it is (F-29). The rule is pure; with
 * {@code -PwithCm} a real machine block is placed, bound to a room of the real room dimension with a real tunnel record,
 * and broken again, and the room's ticket and the saved forced flags are read back.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class CompactRoomGameTests {
    private static final long A = new ChunkPos(0, 0).toLong(), B = new ChunkPos(64, 0).toLong(), C = new ChunkPos(128, 0).toLong(), OUT = new ChunkPos(7, 7).toLong();

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_room_is_held_only_through_a_loaded_machine_that_leads_outside_and_released_after_the_delay(GameTestHelper h) {
        LongSet none = new LongOpenHashSet();
        RoomHolds.Machine outsideToA = new RoomHolds.Machine(false, OUT, A);
        RoomHolds.Machine inAToB = new RoomHolds.Machine(true, A, B);
        RoomHolds.Machine inBToC = new RoomHolds.Machine(true, B, C);
        h.assertTrue(RoomHolds.held(List.of(outsideToA), r -> true, none).equals(LongSet.of(A)), "a loaded machine outside holds its room");
        h.assertTrue(RoomHolds.held(List.of(outsideToA), r -> false, none).isEmpty(), "but not a room with no tunnel and no upgrade");
        h.assertTrue(RoomHolds.held(List.of(), r -> true, none).isEmpty(), "no loaded machine, nothing held");
        h.assertTrue(RoomHolds.held(List.of(inBToC, inAToB, outsideToA), r -> true, none).equals(LongSet.of(A, B, C)), "machines inside held rooms pass the hold on, in whatever order they are listed");
        h.assertTrue(RoomHolds.held(List.of(inBToC, inAToB), r -> true, none).isEmpty(), "the chain without its outside machine holds nothing");
        h.assertTrue(RoomHolds.held(List.of(outsideToA, inAToB, inBToC), r -> r != B, none).equals(LongSet.of(A)), "a room that wants no loading breaks the chain");

        RoomHolds.Machine inAToA = new RoomHolds.Machine(true, A, A);
        RoomHolds.Machine inBToA = new RoomHolds.Machine(true, B, A);
        h.assertTrue(RoomHolds.held(List.of(inAToA), r -> true, none).isEmpty(), "a machine inside its own room does not hold it");
        h.assertTrue(RoomHolds.held(List.of(inAToB, inBToA), r -> true, none).isEmpty(), "two rooms holding each other are not held");
        h.assertTrue(RoomHolds.held(List.of(inAToB, inBToA), r -> true, LongSet.of(A)).equals(LongSet.of(A, B)), "a player standing in one of them holds what its machines lead to");
        h.assertTrue(RoomHolds.held(List.of(inAToB), r -> true, LongSet.of(A)).equals(LongSet.of(B)), "a player inside a room holds the rooms of the machines in it");

        // the release delay
        Long2LongOpenHashMap since = new Long2LongOpenHashMap();
        LongSet loaded = LongSet.of(A, B);
        h.assertTrue(RoomHolds.due(loaded, LongSet.of(A, B), since, 1000, 100).isEmpty() && since.isEmpty(), "held rooms are never due");
        h.assertTrue(RoomHolds.due(loaded, LongSet.of(A), since, 1000, 100).isEmpty() && since.get(B) == 1000, "a room first seen unheld waits");
        h.assertTrue(RoomHolds.due(loaded, LongSet.of(A), since, 1099, 100).isEmpty(), "still inside the delay");
        h.assertTrue(RoomHolds.due(loaded, LongSet.of(A, B), since, 1050, 100).isEmpty() && since.isEmpty(), "held again: the wait is forgotten");
        RoomHolds.due(loaded, LongSet.of(A), since, 2000, 100);
        h.assertTrue(RoomHolds.due(loaded, LongSet.of(A), since, 2100, 100).equals(List.of(B)) && since.isEmpty(), "unheld for the whole delay: released");
        h.assertTrue(RoomHolds.due(loaded, none, since, 3000, 0).size() == 2, "a delay of nothing releases at once");

        // the capability lookup
        h.assertTrue(RoomHolds.refusesLookup(true, false, false), "a room neither held nor loaded is not looked into");
        h.assertTrue(!RoomHolds.refusesLookup(true, true, false) && !RoomHolds.refusesLookup(true, false, true) && !RoomHolds.refusesLookup(false, false, false), "a held room, a loaded room, or the rule off: Compact Machines answers");

        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.CM_ROOMS_FOLLOW_MACHINES.get() && FixesConfig.CM_ROOM_RELEASE_SECONDS.get() == 5 && RoomLoader.delayTicks() == 100, "the rule is on by default with a five second release");
        h.assertTrue(CmGate.gates("CmRoomHoldMixin") && CmGate.gates("CmChunkloadRuleMixin"), "the gate decides for the two room mixins");
        h.succeed();
    }

    // The two tests below share the room loader's state and one of them switches the rule off for a moment, which hands every
    // held room back to Compact Machines: each has a batch of its own, so they never run in the same ticks.
    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 400, batch = "compact_rooms_switches")
    public void a_real_machine_holds_its_room_while_it_is_loaded_and_the_room_is_released_when_it_is_broken(GameTestHelper h) {
        if (!ModList.get().isLoaded(CmGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(RoomHolds.ID) && skipped.get(RoomHolds.ID).equals(CmGate.Verdict.ABSENT.reason()),
                    "without Compact Machines the plugin skipped F-29 with the absent reason, got " + skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes test: Compact Machines is not loaded, no machine is placed (add -PwithCm)");
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        ServerLevel rooms = server.getLevel(RoomLoader.ROOMS);
        h.assertTrue(rooms != null, "the room dimension exists in this run's world");
        Block machineBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(CmGate.MOD_ID, "machine_normal"));
        BlockPos pos = h.absolutePos(new BlockPos(5, 2, 5));
        ChunkPos room = new ChunkPos(64 * 40, 64 * 41);        // a room slot far out on Compact Machines' own grid
        long key = room.toLong();
        long delay = RoomLoader.delayTicks();

        // the saved forced flags of before the fix are cleared at server start: here, directly
        rooms.setChunkForced(room.x + 64, room.z, true);
        h.assertTrue(RoomLoader.clearStaleForced(rooms) >= 1 && rooms.getForcedChunks().isEmpty(), "the room dimension's saved forced flags are cleared");

        level.setBlockAndUpdate(pos, machineBlock.defaultBlockState());
        BlockEntity machine = level.getBlockEntity(pos);
        h.assertTrue(machine != null, "a machine block entity");
        CmTestBridge.addTunnel(rooms, room, level.dimension(), pos);
        CmTestBridge.bind(machine, room);                      // Compact Machines syncs: here it would force the room for good

        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(RoomHolds.ID));
        h.assertTrue(fixOn || Fixes.skipped().containsKey(RoomHolds.ID), "F-29 is applied or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        Fixes.LOGGER.info("BSP-Mod Fixes test: a Compact Machines block is bound to a room {}", fixOn ? "with F-29" : "as shipped (F-29 switched off)");
        if (!fixOn) {
            // as shipped: forced and saved, and still forced after the machine is broken
            h.assertTrue(rooms.getForcedChunks().contains(key) && !RoomLoader.isHeld(key), "Compact Machines forces the room itself");
            level.removeBlock(pos, false);
            RoomLoader.reconcile(server, server.getTickCount() + 10 * delay + 1);
            h.assertTrue(rooms.getForcedChunks().contains(key), "and the room stays force-loaded after its machine is broken: nothing ever releases it");
            CmTestBridge.removeTunnel(rooms, room);
            CmTestBridge.cmRelease(rooms, room);
            h.assertTrue(rooms.getForcedChunks().isEmpty(), "cleaned up");
            h.succeed();
            return;
        }

        h.assertTrue(RoomLoader.isHeld(key), "the room is held the moment its loaded machine has a tunnel");
        h.assertTrue(!rooms.getForcedChunks().contains(key), "by a ticket, not by a saved forced flag");
        h.runAfterDelay(60, () -> {
            h.assertTrue(RoomLoader.isHeld(key) && rooms.getChunkSource().getChunkNow(room.x, room.z) != null, "three seconds on the room's chunk is loaded and still held");
            long now = server.getTickCount();

            // the last tunnel goes: the room wants no loading, and is released after the delay
            CmTestBridge.removeTunnel(rooms, room);
            RoomLoader.reconcile(server, now);
            h.assertTrue(RoomLoader.isHeld(key), "inside the delay the room is still loaded");
            RoomLoader.reconcile(server, now + delay);
            h.assertTrue(!RoomLoader.isHeld(key), "no tunnel for the whole delay: released");

            // the Chunkloader upgrade is a reason to load under the same rule, and forces nothing for good
            CmTestBridge.addChunkloadUpgrade(rooms, room);
            h.assertTrue(!rooms.getForcedChunks().contains(key), "the upgrade no longer sets the saved forced flag");
            RoomLoader.reconcile(server, now + delay + 20);
            h.assertTrue(RoomLoader.isHeld(key), "a room with the upgrade is held while its machine is loaded");
            CmTestBridge.removeChunkloadUpgrade(rooms, room);
            RoomLoader.reconcile(server, now + delay + 40);
            RoomLoader.reconcile(server, now + 2 * delay + 40);
            h.assertTrue(!RoomLoader.isHeld(key), "and released when the upgrade goes");

            // a tunnel again: held within the next pass, without the machine having to reload
            CmTestBridge.addTunnel(rooms, room, level.dimension(), pos);
            RoomLoader.reconcile(server, now + 2 * delay + 60);
            h.assertTrue(RoomLoader.isHeld(key), "a first tunnel holds the room at the next pass");

            // the switch: off, this fix lets go and Compact Machines' own forcing is back
            boolean saved = FixesConfig.CM_ROOMS_FOLLOW_MACHINES.get();
            try {
                FixesConfig.CM_ROOMS_FOLLOW_MACHINES.set(false);
                RoomLoader.reconcile(server, now + 2 * delay + 80);
                h.assertTrue(!RoomLoader.isHeld(key) && RoomLoader.machines() == 0, "switched off, the fix releases what it holds");
                h.assertTrue(rooms.getForcedChunks().contains(key), "and hands the room to Compact Machines' own forcing at once, without waiting for the machine to reload");
                CmTestBridge.bind(machine, room);
                h.assertTrue(rooms.getForcedChunks().contains(key) && !RoomLoader.isHeld(key), "a machine loading with the rule off is forced by Compact Machines as before");
                CmTestBridge.cmRelease(rooms, room);
            } finally {
                FixesConfig.CM_ROOMS_FOLLOW_MACHINES.set(saved);
            }
            CmTestBridge.bind(machine, room);
            h.assertTrue(RoomLoader.isHeld(key) && rooms.getForcedChunks().isEmpty(), "switched on again, the room is held by the rule");

            // the machine is broken (its chunk unloading takes the same path): released after the delay, not before
            int machines = RoomLoader.machines();
            level.removeBlock(pos, false);
            h.assertTrue(RoomLoader.machines() == machines - 1, "the broken machine is forgotten at once");
            long later = now + 3 * delay + 100;
            RoomLoader.reconcile(server, later);
            h.assertTrue(RoomLoader.isHeld(key), "the room stays for the delay");
            RoomLoader.reconcile(server, later + delay);
            h.assertTrue(!RoomLoader.isHeld(key), "then it is released: nothing bound to it is loaded");
            CmTestBridge.removeTunnel(rooms, room);
            h.succeed();
        });
    }

    /**
     * A level nobody is in, with no saved forced chunk, stops ticking its block entities after 300 ticks; Compact Machines'
     * saved flag kept the room dimension going, and this fix's ticket alone would not. The room dimension is put past that
     * point and a furnace in a held room must still smelt.
     */
    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 500, batch = "compact_rooms_ticking")
    public void a_held_room_keeps_running_with_nobody_in_the_room_dimension(GameTestHelper h) {
        if (!ModList.get().isLoaded(CmGate.MOD_ID) || Fixes.active().stream().noneMatch(f -> f.id().equals(RoomHolds.ID))) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: no held room to run (Compact Machines absent, or F-29 switched off for this run)");
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        ServerLevel rooms = level.getServer().getLevel(RoomLoader.ROOMS);
        BlockPos pos = h.absolutePos(new BlockPos(13, 2, 13));
        ChunkPos room = new ChunkPos(64 * 43, 64 * 41);
        level.setBlockAndUpdate(pos, ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(CmGate.MOD_ID, "machine_normal")).defaultBlockState());
        CmTestBridge.addTunnel(rooms, room, level.dimension(), pos);
        CmTestBridge.bind(level.getBlockEntity(pos), room);
        h.assertTrue(RoomLoader.isHeld(room.toLong()) && rooms.players().isEmpty() && rooms.getForcedChunks().isEmpty(), "a held room, nobody in the room dimension, no saved forced chunk");
        h.runAfterDelay(40, () -> {
            BlockPos at = room.getBlockAt(8, 5, 8);
            rooms.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
            net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) rooms.getBlockEntity(at);
            furnace.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON));
            furnace.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));
            ((com.mrgregles.bsp_mod_fixes.mixin.gametest.ServerLevelEmptyTimeAccessor) rooms).bsp_mod_fixes$setEmptyTime(100_000);
            h.runAfterDelay(260, () -> {
                boolean smelted = furnace.getItem(2).is(net.minecraft.world.item.Items.IRON_INGOT);
                rooms.removeBlock(at, false);
                level.removeBlock(pos, false);
                CmTestBridge.removeTunnel(rooms, room);
                h.assertTrue(smelted, "thirteen seconds on the furnace in the held room has smelted its iron: the room dimension is ticking");
                h.succeed();
            });
        });
    }
}
