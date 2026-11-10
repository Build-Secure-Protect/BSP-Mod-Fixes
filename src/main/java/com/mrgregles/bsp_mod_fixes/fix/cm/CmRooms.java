package com.mrgregles.bsp_mod_fixes.fix.cm;

import dev.compactmods.machines.tunnel.TunnelHelper;
import dev.compactmods.machines.tunnel.graph.TunnelConnectionGraph;
import dev.compactmods.machines.upgrade.MachineRoomUpgrades;
import dev.compactmods.machines.upgrade.RoomUpgradeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * F-29: the questions asked of Compact Machines' own data. Kept apart from {@link RoomLoader} so that class loads without
 * Compact Machines; this one is only touched once a Compact Machines block entity has registered itself.
 */
final class CmRooms {
    private CmRooms() {}

    /**
     * Whether a room wants loading: it has a tunnel (Compact Machines' own test, {@code tunnels().findAny()}), or the
     * Chunkloader upgrade (D61). A tunnel graph that cannot answer counts as wanting, as a room Compact Machines would
     * have forced.
     */
    static boolean wantsLoading(ServerLevel rooms, long room) {
        ChunkPos pos = new ChunkPos(room);
        try {
            if (TunnelConnectionGraph.forRoom(rooms, pos).tunnels().findAny().isPresent()) {
                return true;
            }
        } catch (RuntimeException e) {
            return true;
        }
        return RoomUpgradeManager.get(rooms).hasUpgrade(pos, MachineRoomUpgrades.CHUNKLOAD.get());
    }

    /** Compact Machines' own forcing of a room that wants loading, for handing held rooms back when the rule is switched off. */
    static void forceAsShipped(ServerLevel rooms, long room) {
        if (wantsLoading(rooms, room)) {
            TunnelHelper.setChunkMode(rooms, new ChunkPos(room), true);
        }
    }
}
