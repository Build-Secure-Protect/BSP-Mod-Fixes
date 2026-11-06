package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.proxy.ClientGate;
import com.mrgregles.bsp_mod_fixes.fix.proxy.DeferredDisconnectScreen;
import com.mrgregles.bsp_mod_fixes.fix.proxy.StalePacketDrop;
import com.mrgregles.bsp_mod_fixes.mixin.gametest.ConnectionInvoker;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

import static net.minecraft.network.ConnectionProtocol.HANDSHAKING;
import static net.minecraft.network.ConnectionProtocol.LOGIN;
import static net.minecraft.network.ConnectionProtocol.PLAY;
import static net.minecraft.network.protocol.PacketFlow.CLIENTBOUND;
import static net.minecraft.network.protocol.PacketFlow.SERVERBOUND;

/**
 * MF-13 and MF-14, the server-switch fixes. F-16 is checked as the pure decision and then on a real {@link Connection} built
 * on an in-memory netty channel, the way {@link MockPlayers} builds one, with the mixin applied: the channel is moved to LOGIN
 * and {@code doSendPacket} is called with the PLAY-on-PLAY protocols vanilla records for a packet sent before the move. F-17
 * patches a client-only class, so here only its decision and its skip on the dedicated server can be checked.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class ProxySwitchGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void stale_packet_decision_drops_only_a_client_packet_queued_before_the_protocol_moved(GameTestHelper h) {
        h.assertTrue(StalePacketDrop.shouldDrop(CLIENTBOUND, PLAY, PLAY, LOGIN), "a client's PLAY packet after the move to LOGIN is dropped");
        h.assertFalse(StalePacketDrop.shouldDrop(CLIENTBOUND, PLAY, PLAY, PLAY), "the channel still in PLAY: sent");
        h.assertFalse(StalePacketDrop.shouldDrop(CLIENTBOUND, PLAY, LOGIN, LOGIN), "a deliberate change LOGIN to PLAY: sent (vanilla switches the protocol)");
        h.assertFalse(StalePacketDrop.shouldDrop(CLIENTBOUND, LOGIN, HANDSHAKING, HANDSHAKING), "the intention packet's change: sent");
        h.assertFalse(StalePacketDrop.shouldDrop(CLIENTBOUND, PLAY, PLAY, null), "no protocol on the channel yet: sent");
        h.assertFalse(StalePacketDrop.shouldDrop(SERVERBOUND, PLAY, PLAY, LOGIN), "a server's connection is never touched");
        h.assertFalse(StalePacketDrop.shouldDrop(null, PLAY, PLAY, LOGIN), "an unknown flow is never touched");
        Packet<?> payload = new ServerboundCustomPayloadPacket(ResourceLocation.fromNamespaceAndPath("bsp_mod_fixes", "stale"), new FriendlyByteBuf(Unpooled.buffer()));
        String what = StalePacketDrop.describe(payload);
        h.assertTrue(what.equals(ServerboundCustomPayloadPacket.class.getName() + " on channel bsp_mod_fixes:stale"), "a custom payload names its channel, got " + what);
        Packet<?> plain = new ClientIntentionPacket("localhost", 25565, ConnectionProtocol.LOGIN);
        h.assertTrue(StalePacketDrop.describe(plain).equals(ClientIntentionPacket.class.getName()), "any other packet is named by its class, got " + StalePacketDrop.describe(plain));
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void connection_mixin_drops_a_stale_play_packet_on_a_client_channel_moved_to_login_and_sends_the_rest(GameTestHelper h) {
        Packet<?> stale = new ServerboundCustomPayloadPacket(ResourceLocation.fromNamespaceAndPath("bsp_mod_fixes", "stale"), new FriendlyByteBuf(Unpooled.buffer()));
        int before = StalePacketDrop.droppedCount();

        // the client's connection, moved to LOGIN by the reset while a PLAY packet waits on the event loop
        Connection client = new Connection(CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(client);
        channel.runPendingTasks();
        client.setProtocol(LOGIN);
        channel.runPendingTasks();
        h.assertTrue(channel.attr(Connection.ATTRIBUTE_PROTOCOL).get() == LOGIN, "the channel is in LOGIN");
        ((ConnectionInvoker) client).bsp_mod_fixes$doSendPacket(stale, null, PLAY, PLAY);
        channel.runPendingTasks();
        h.assertTrue(channel.outboundMessages().isEmpty(), "the stale PLAY packet was not written, found " + channel.outboundMessages());
        h.assertTrue(StalePacketDrop.droppedCount() == before + 1, "one drop was counted and logged, count " + StalePacketDrop.droppedCount());
        h.assertTrue(channel.attr(Connection.ATTRIBUTE_PROTOCOL).get() == LOGIN, "the channel stays in LOGIN");

        // a send that carries its own protocol change is vanilla's business: the protocol switches and the packet goes out
        ((ConnectionInvoker) client).bsp_mod_fixes$doSendPacket(stale, null, PLAY, LOGIN);
        channel.runPendingTasks();
        h.assertTrue(channel.attr(Connection.ATTRIBUTE_PROTOCOL).get() == PLAY, "vanilla switched the channel to PLAY");
        h.assertTrue(channel.outboundMessages().size() == 1 && channel.outboundMessages().peek() == stale, "the packet was written, found " + channel.outboundMessages());
        h.assertTrue(StalePacketDrop.droppedCount() == before + 1, "nothing more was dropped");

        // once in PLAY, a PLAY-on-PLAY send is normal and goes out
        ((ConnectionInvoker) client).bsp_mod_fixes$doSendPacket(stale, null, PLAY, PLAY);
        channel.runPendingTasks();
        h.assertTrue(channel.outboundMessages().size() == 2, "a packet in the channel's own protocol goes out, found " + channel.outboundMessages().size());
        h.assertTrue(StalePacketDrop.droppedCount() == before + 1, "still nothing more dropped");
        channel.finishAndReleaseAll();

        // a server's connection in the same state is left alone (the mixin is inert on a dedicated server)
        Connection server = new Connection(SERVERBOUND);
        EmbeddedChannel serverChannel = new EmbeddedChannel(server);
        serverChannel.runPendingTasks();
        server.setProtocol(LOGIN);
        serverChannel.runPendingTasks();
        ((ConnectionInvoker) server).bsp_mod_fixes$doSendPacket(stale, null, PLAY, PLAY);
        serverChannel.runPendingTasks();
        h.assertTrue(serverChannel.outboundMessages().size() == 1, "the server's packet was written, found " + serverChannel.outboundMessages().size());
        h.assertTrue(StalePacketDrop.droppedCount() == before + 1, "the server's packet was not counted as dropped");
        serverChannel.finishAndReleaseAll();
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void disconnected_screen_is_deferred_only_off_the_render_thread_and_the_fix_is_skipped_on_a_dedicated_server(GameTestHelper h) {
        h.assertTrue(DeferredDisconnectScreen.mustDefer(true, false), "a disconnected screen from the netty thread is deferred");
        h.assertFalse(DeferredDisconnectScreen.mustDefer(true, true), "a disconnected screen from the render thread is opened at once");
        h.assertFalse(DeferredDisconnectScreen.mustDefer(false, false), "any other screen from another thread is left to vanilla");
        h.assertFalse(DeferredDisconnectScreen.mustDefer(false, true), "any other screen from the render thread is left to vanilla");
        h.assertTrue(ClientGate.gates("DisconnectedScreenThreadMixin"), "the Minecraft mixin is gated");
        h.assertFalse(ClientGate.gates("ConnectionStalePacketMixin"), "the Connection mixin applies on both sides");
        h.assertTrue(ClientGate.applies(false) && !ClientGate.applies(true), "client-only mixins apply on a client and never on a dedicated server");

        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(StalePacketDrop.ID)), "F-16 is known to the build");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(DeferredDisconnectScreen.ID)), "F-17 is known to the build");
        // the GameTest server opens no listener, so Connection loads the first time a test builds one; make sure one exists
        new EmbeddedChannel(new Connection(SERVERBOUND)).finishAndReleaseAll();
        h.assertTrue(Fixes.active().stream().anyMatch(f -> f.id().equals(StalePacketDrop.ID)), "F-16 is applied once Connection has loaded, active: " + Fixes.active());
        h.assertTrue(Fixes.active().stream().noneMatch(f -> f.id().equals(DeferredDisconnectScreen.ID)), "F-17 is not applied on a dedicated server");
        Map<String, String> skipped = Fixes.skipped();
        h.assertTrue(skipped.containsKey(DeferredDisconnectScreen.ID) && skipped.get(DeferredDisconnectScreen.ID).equals(ClientGate.SERVER_REASON), "F-17 was skipped by the plugin with the server reason, got " + skipped);
        h.assertFalse(skipped.containsKey(StalePacketDrop.ID), "F-16 was not skipped");
        h.succeed();
    }
}
