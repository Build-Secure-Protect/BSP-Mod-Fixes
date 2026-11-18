package com.mrgregles.bsp_mod_fixes.fix.ironfurnaces;

import ironfurnaces.capability.CapabilityPlayerFurnacesList;
import ironfurnaces.capability.IPlayerFurnacesList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * F-39's end of tick: the loads {@link FurnaceListRepair} queued are matched to the online player whose furnace-list capability
 * holds that very list, and logged with the player's name. Names Iron Furnaces' classes, so it is registered (in
 * {@code Fixes.registerEvents}) only beside the Iron Furnaces build the mixin was written for, and only when the mixin was not
 * skipped; the test classes reach it the same way.
 */
public final class FurnaceListNames {
    private FurnaceListNames() {}

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FurnaceListRepair.pending() == 0) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        FurnaceListRepair.resolve(list -> nameAmong(players, list), server.getTickCount());
    }

    /** The name of the player among {@code players} whose furnace list is {@code list} itself, or null. */
    @Nullable
    public static String nameAmong(Iterable<ServerPlayer> players, List<BlockPos> list) {
        for (ServerPlayer player : players) {
            IPlayerFurnacesList cap = player.getCapability(CapabilityPlayerFurnacesList.FURNACES_LIST).orElse(null);
            if (cap != null && cap.get() == list) {
                return player.getGameProfile().getName();
            }
        }
        return null;
    }
}
