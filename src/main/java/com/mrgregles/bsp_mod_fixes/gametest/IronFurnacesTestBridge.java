package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.ironfurnaces.FurnaceListNames;
import ironfurnaces.capability.CapabilityPlayerFurnacesList;
import ironfurnaces.capability.PlayerFurnacesListProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Iron Furnaces' own classes for the F-39 tests, named here only, so the test class loads without the mod; a test touches this
 * class only after it has seen that Iron Furnaces is loaded ({@code -PwithIf}).
 */
final class IronFurnacesTestBridge {
    private IronFurnacesTestBridge() {}

    /** A fresh provider, as Iron Furnaces attaches one to every player. */
    static Object provider() {
        return new PlayerFurnacesListProvider();
    }

    /** The provider's live list. */
    static List<BlockPos> list(Object provider) {
        return ((PlayerFurnacesListProvider) provider).furnacesList.listFurances;
    }

    /** Iron Furnaces' own load, through the real method (patched or not). */
    static void deserialize(Object provider, CompoundTag tag) {
        ((PlayerFurnacesListProvider) provider).deserializeNBT(tag);
    }

    /** Iron Furnaces' own save. */
    static CompoundTag serialize(Object provider) {
        return ((PlayerFurnacesListProvider) provider).serializeNBT();
    }

    /** The live list of a player's attached capability. */
    static List<BlockPos> playerList(ServerPlayer player) {
        return player.getCapability(CapabilityPlayerFurnacesList.FURNACES_LIST).map(h -> h.get()).orElseThrow();
    }

    static String nameAmong(List<ServerPlayer> players, List<BlockPos> list) {
        return FurnaceListNames.nameAmong(players, list);
    }
}
