package com.mrgregles.bsp_mod_fixes.fix.proxy;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-16 (Minecraft 1.20.1): a packet queued for the server before a server switch changed the connection's protocol is dropped
 * instead of killing the connection. Vanilla's {@code Connection.sendPacket} reads the channel's protocol at the moment a
 * packet is sent and, off the netty thread, schedules {@code doSendPacket(packet, listener, newProtocol, currentProtocol)} on
 * the event loop; {@code doSendPacket} switches the channel's protocol only when those two differ. During a Velocity switch
 * made with Client Reset Packet the netty thread moves the channel to LOGIN while the world unloads, so a PLAY packet a client
 * mod sent from the render thread in that window reaches the event loop labelled PLAY on PLAY, nothing switches the protocol
 * back, and the encoder, which reads the channel's live protocol, throws "Can't serialize unregistered packet" and the
 * connection closes. The mixin asks {@link #shouldDrop} at the head of {@code doSendPacket} and cancels the send when the
 * packet is stale, with one WARN line naming the packet ({@link #describe}). A deliberate protocol change (the two recorded
 * protocols differ, as for the handshake intention packet) is never touched, and neither is a server's connection.
 * <p>No serverbound packet class is registered under two protocols in 1.20.1, so a packet this drops is always one the
 * encoder would have refused: nothing vanilla could have sent is lost. Left alone on purpose: a PLAY packet a mod sends
 * after the channel is already in LOGIN records PLAY on LOGIN, which vanilla treats as a protocol change and sends as PLAY;
 * the proxy, not the encoder, then ends that switch, with no drop line in the log.
 */
public final class StalePacketDrop {
    public static final String ID = "F-16";
    public static final String WHAT = "a packet queued before a server switch moved the connection to another protocol is dropped instead of closing the connection";
    public static final String TARGET = "Minecraft 1.20.1";

    private static final AtomicInteger DROPPED = new AtomicInteger();

    private StalePacketDrop() {}

    /**
     * Whether a send reaching {@code doSendPacket} must be dropped: the connection is a client's ({@code receiving} is
     * CLIENTBOUND), the send carries no protocol change of its own ({@code newProtocol == currentProtocol}), and the channel
     * has meanwhile moved to another protocol ({@code actual}, the channel attribute; null while unset).
     */
    public static boolean shouldDrop(PacketFlow receiving, ConnectionProtocol newProtocol, ConnectionProtocol currentProtocol, ConnectionProtocol actual) {
        return receiving == PacketFlow.CLIENTBOUND
                && newProtocol == currentProtocol
                && actual != null
                && actual != currentProtocol;
    }

    /** The packet for the log line: its class, and for a custom payload the channel it was for, so the sending mod can be named. */
    public static String describe(Packet<?> packet) {
        String what = packet.getClass().getName();
        if (packet instanceof ServerboundCustomPayloadPacket payload) {
            what += " on channel " + payload.getIdentifier();
        }
        return what;
    }

    /** Records a drop and logs it: once per dropped packet (a switch produces at most a few). */
    public static void dropped(Packet<?> packet, ConnectionProtocol currentProtocol, ConnectionProtocol actual) {
        DROPPED.incrementAndGet();
        Fixes.LOGGER.warn("[BSP Mod Fixes] Dropped a stale {} packet ({}) after a server switch; the connection is now in {}",
                currentProtocol, describe(packet), actual);
    }

    /** How many packets have been dropped in this run. */
    public static int droppedCount() {
        return DROPPED.get();
    }
}
