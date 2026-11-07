package com.mrgregles.bsp_mod_fixes.net;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * This mod's own network channel, {@code bsp_mod_fixes:main}: one message so far, the quantum armor's absorption to the
 * wearer (F-25). The mod is required on both sides at the same version, so the channel requires its version on both.
 */
public final class FixesNetwork {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BSPModFixes.MODID, "main");
    public static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ID, () -> VERSION, VERSION::equals, VERSION::equals);

    private FixesNetwork() {}

    /** Registers the messages. Called once from the mod constructor, on both sides. */
    public static void register() {
        CHANNEL.messageBuilder(QuantumAbsorbedMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuantumAbsorbedMessage::write)
                .decoder(QuantumAbsorbedMessage::read)
                .consumerMainThread(QuantumAbsorbedMessage::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
