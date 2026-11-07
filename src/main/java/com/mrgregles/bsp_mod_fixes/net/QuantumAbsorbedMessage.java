package com.mrgregles.bsp_mod_fixes.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * F-25, server to the wearer: the quantum armor absorbed part of a hit. {@code hit} is the damage after the armor (what
 * AdvancedAE's handler received), {@code absorbed} the share taken off it, {@code paid} the AE the pieces paid together.
 * The client hands it to the suit readout. Forge does not enforce a message's direction on receipt, so the handler checks
 * the reception side before naming the client class: a server that is sent this message by a client ignores it.
 */
public record QuantumAbsorbedMessage(float hit, float absorbed, double paid) {
    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(hit);
        buf.writeFloat(absorbed);
        buf.writeDouble(paid);
    }

    public static QuantumAbsorbedMessage read(FriendlyByteBuf buf) {
        return new QuantumAbsorbedMessage(buf.readFloat(), buf.readFloat(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.setPacketHandled(true);
        if (!ctx.getDirection().getReceptionSide().isClient()) {
            return;
        }
        com.mrgregles.bsp_mod_fixes.client.QuantumReadout.onAbsorbed(hit, absorbed, paid);
    }
}
