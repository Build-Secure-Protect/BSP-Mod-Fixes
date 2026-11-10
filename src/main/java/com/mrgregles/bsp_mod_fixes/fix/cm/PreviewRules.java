package com.mrgregles.bsp_mod_fixes.fix.cm;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-31, the rules for the server half of Compact Machines' room preview. A right click on a machine with an empty hand
 * makes the server capture the whole room ({@code StructureTemplate.fillFromWorld}: up to 13 x 15 x 13 blocks read on the
 * server thread, the room's chunk loaded if it is not, and every block entity written with its full save, inventories
 * included) and send it to the clicker as one packet. Compact Machines asks nothing first: not who owns the room, not how
 * long ago the same player asked, not how large the result is. So any player can make the server do that work several
 * times a second by holding the button, receives the complete contents of every container in anyone's room, and a room
 * of full containers can pass the 1,048,576 bytes a custom payload may carry.
 *
 * <p>With the rule on, a preview request is answered by this mod ({@code PreviewRequests}): refused when previews are
 * switched off, ignored when the same player's last one was under the interval ago, captured with Compact Machines' own
 * method, stripped of what a client standing in the room would not be sent, measured, and sent with Compact Machines' own
 * packet, or refused with a message when it is over the limit. Pure decisions here; counters for the tests.
 */
public final class PreviewRules {
    public static final String ID = "F-31";
    public static final String WHAT = "a room preview is sent at most once per player per interval, without the contents of containers, within a size limit, and can be switched off";
    public static final String TARGET = CmGate.TARGET;

    /** What a preview request comes to. */
    public enum Decision {
        /** The rule is off: Compact Machines answers as it ships. */
        LEAVE,
        /** Previews are switched off: nothing is captured, the player is told. */
        OFF,
        /** The same player's last preview was under the interval ago: nothing happens. */
        TOO_SOON,
        /** Capture and send. */
        CAPTURE
    }

    private static final LongAdder SENT = new LongAdder();
    private static final LongAdder TOO_SOON = new LongAdder();
    private static final LongAdder OFF = new LongAdder();
    private static final LongAdder TOO_LARGE = new LongAdder();
    private static final LongAdder STRIPPED = new LongAdder();

    private PreviewRules() {}

    /** {@code lastTick} is the tick of the player's last captured preview, or {@link Long#MIN_VALUE} for none. */
    public static Decision decide(boolean ruleOn, boolean previewsOn, long nowTick, long lastTick, long intervalTicks) {
        if (!ruleOn) {
            return Decision.LEAVE;
        }
        if (!previewsOn) {
            return Decision.OFF;
        }
        if (lastTick != Long.MIN_VALUE && nowTick - lastTick < intervalTicks && nowTick >= lastTick) {
            return Decision.TOO_SOON;
        }
        return Decision.CAPTURE;
    }

    /** Whether a capture of {@code bytes} on the wire is sent: within the limit. */
    public static boolean fits(long bytes, long maxBytes) {
        return bytes <= maxBytes;
    }

    /** A stream that only counts, for measuring what a tag would take on the wire. */
    public static DataOutputStream counter() {
        return new DataOutputStream(OutputStream.nullOutputStream());
    }

    public static void count(Decision decision) {
        switch (decision) {
            case OFF -> OFF.increment();
            case TOO_SOON -> TOO_SOON.increment();
            default -> { }
        }
    }

    public static void countSent() {
        SENT.increment();
    }

    public static void countTooLarge() {
        TOO_LARGE.increment();
    }

    public static void countStripped(long blockEntities) {
        STRIPPED.add(blockEntities);
    }

    /** Previews sent through the rule. */
    public static long sent() {
        return SENT.sum();
    }

    /** Requests ignored as too soon. */
    public static long tooSoon() {
        return TOO_SOON.sum();
    }

    /** Requests refused because previews are off. */
    public static long off() {
        return OFF.sum();
    }

    /** Captures refused as too large. */
    public static long tooLarge() {
        return TOO_LARGE.sum();
    }

    /** Block entities whose saved data was not sent. */
    public static long stripped() {
        return STRIPPED.sum();
    }
}
