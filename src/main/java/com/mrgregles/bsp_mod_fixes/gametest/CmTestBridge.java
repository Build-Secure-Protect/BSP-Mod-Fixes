package com.mrgregles.bsp_mod_fixes.gametest;

import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.api.room.RoomSize;
import dev.compactmods.machines.location.LevelBlockPosition;
import dev.compactmods.machines.room.Rooms;
import dev.compactmods.machines.room.data.CompactRoomData;
import dev.compactmods.machines.machine.CompactMachineBlockEntity;
import dev.compactmods.machines.tunnel.TunnelHelper;
import dev.compactmods.machines.tunnel.Tunnels;
import dev.compactmods.machines.tunnel.graph.TunnelConnectionGraph;
import dev.compactmods.machines.upgrade.MachineRoomUpgrades;
import dev.compactmods.machines.upgrade.RoomUpgradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Compact Machines' own classes for the room tests, named here only, so the test classes load without the mod; a test
 * touches this class only after it has seen that Compact Machines is loaded ({@code -PwithCm}).
 */
final class CmTestBridge {
    private CmTestBridge() {}

    /** A wall position of the room, where a tunnel would sit. */
    static BlockPos tunnelPos(ChunkPos room) {
        return room.getBlockAt(8, 5, 1);
    }

    /** Registers an item tunnel of the room for the machine, as placing one does. */
    static void addTunnel(ServerLevel rooms, ChunkPos room, ResourceKey<Level> machineLevel, BlockPos machinePos) {
        TunnelConnectionGraph.forRoom(rooms, room).registerTunnel(tunnelPos(room), Tunnels.ITEM_TUNNEL_DEF.get(), new LevelBlockPosition(machineLevel, machinePos), Direction.NORTH);
    }

    static void removeTunnel(ServerLevel rooms, ChunkPos room) {
        TunnelConnectionGraph.forRoom(rooms, room).unregister(tunnelPos(room));
    }

    /** Binds a machine block to a room, as entering a new machine does. */
    static void bind(BlockEntity machine, ChunkPos room) {
        ((CompactMachineBlockEntity) machine).setConnectedRoom(room);
    }

    static void addChunkloadUpgrade(ServerLevel rooms, ChunkPos room) {
        RoomUpgradeManager.get(rooms).addUpgrade(MachineRoomUpgrades.CHUNKLOAD.get(), room);
        MachineRoomUpgrades.CHUNKLOAD.get().onAdded(rooms, room); // as the admin command does
    }

    static void removeChunkloadUpgrade(ServerLevel rooms, ChunkPos room) {
        RoomUpgradeManager.get(rooms).removeUpgrade(MachineRoomUpgrades.CHUNKLOAD.get(), room);
        MachineRoomUpgrades.CHUNKLOAD.get().onRemoved(rooms, room);
    }

    /** A new room for the machine at {@code pos}, owned by the player, with a chest holding diamonds and a sign reading "BSP" at its centre. */
    static ChunkPos newFurnishedRoom(ServerLevel level, BlockPos pos, ServerPlayer player) {
        try {
            MinecraftServer server = level.getServer();
            ChunkPos room = Rooms.createNew(server, RoomSize.NORMAL, player.getUUID());
            bind(level.getBlockEntity(pos), room);
            ServerLevel rooms = CompactDimension.forServer(server);
            BlockPos centre = centre(server, room);
            rooms.setBlockAndUpdate(centre, Blocks.CHEST.defaultBlockState());
            ((ChestBlockEntity) rooms.getBlockEntity(centre)).setItem(0, new ItemStack(Items.DIAMOND, 64));
            rooms.setBlockAndUpdate(centre.north(), Blocks.OAK_SIGN.defaultBlockState());
            ((SignBlockEntity) rooms.getBlockEntity(centre.north())).updateText(text -> text.setMessage(0, Component.literal("BSP")), true);
            return room;
        } catch (Throwable t) {
            throw new AssertionError("could not make a room: " + t, t);
        }
    }

    private static BlockPos centre(MinecraftServer server, ChunkPos room) throws Throwable {
        return CompactRoomData.get(CompactDimension.forServer(server)).getData(room).getCenter();
    }

    /** Fills the room's chest with written books of a few hundred bytes each. */
    static void fillChest(MinecraftServer server, ChunkPos room, int slots) {
        try {
            ChestBlockEntity chest = (ChestBlockEntity) CompactDimension.forServer(server).getBlockEntity(centre(server, room));
            for (int i = 0; i < slots; i++) {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                book.getOrCreateTag().putString("title", "book " + i);
                book.getOrCreateTag().putString("filler", "x".repeat(800));
                chest.setItem(i, book);
            }
        } catch (Throwable t) {
            throw new AssertionError("could not fill the chest: " + t, t);
        }
    }

    /** Compact Machines' own release, for cleaning up after a run with the fix switched off. */
    static void cmRelease(ServerLevel rooms, ChunkPos room) {
        TunnelHelper.setChunkMode(rooms, room, false);
    }
}
