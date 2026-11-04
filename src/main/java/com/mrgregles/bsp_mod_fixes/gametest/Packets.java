package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.mixin.gametest.NetworkContextInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** Packet contexts for the tests: what a server handler receives when {@code player} sends a packet. */
final class Packets {
    private Packets() {}

    static NetworkEvent.Context from(ServerPlayer player) {
        return NetworkContextInvoker.bsp_mod_fixes$newContext(player.connection.connection, NetworkDirection.PLAY_TO_SERVER, 0);
    }
}
