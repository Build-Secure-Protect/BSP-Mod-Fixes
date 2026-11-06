package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.proxy.StalePacketDrop;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * F-16: a send that reaches {@code doSendPacket} after the channel's protocol moved on is dropped and logged. Targets a
 * Minecraft class, so it is remapped: the refmap carries {@code doSendPacket}, and the shadowed fields are renamed in this
 * class at reobfuscation. Applied on both sides; inert on a server, whose connections receive SERVERBOUND packets. The
 * dropped packet's listener, if any, is not told: the packet is treated as never sent (vanilla would have failed the write
 * and closed the connection).
 */
@Mixin(Connection.class)
public abstract class ConnectionStalePacketMixin {
    @Shadow
    private Channel channel;
    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(method = "doSendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/ConnectionProtocol;)V",
            at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$dropStalePacket(Packet<?> packet, @Nullable PacketSendListener listener, ConnectionProtocol newProtocol,
                                               ConnectionProtocol currentProtocol, CallbackInfo ci) {
        if (channel == null) {
            return;
        }
        ConnectionProtocol actual = channel.attr(Connection.ATTRIBUTE_PROTOCOL).get();
        if (StalePacketDrop.shouldDrop(receiving, newProtocol, currentProtocol, actual)) {
            StalePacketDrop.dropped(packet, currentProtocol, actual);
            ci.cancel();
        }
    }
}
