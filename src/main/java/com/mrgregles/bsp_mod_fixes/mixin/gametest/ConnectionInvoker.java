package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * GameTest server only: lets the tests call {@code Connection.doSendPacket} with the protocols vanilla records at send time,
 * so the F-16 mixin on that method is exercised on a real connection (an in-memory netty channel) in the state the race
 * leaves it in. In a dev run the method has its official name; this config never ships, so no production mapping is needed.
 */
@Mixin(Connection.class)
public interface ConnectionInvoker {
    @Invoker("doSendPacket")
    void bsp_mod_fixes$doSendPacket(Packet<?> packet, PacketSendListener listener, ConnectionProtocol newProtocol, ConnectionProtocol currentProtocol);
}
