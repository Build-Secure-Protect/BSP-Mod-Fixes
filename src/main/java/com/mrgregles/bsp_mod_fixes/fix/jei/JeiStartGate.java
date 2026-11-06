package com.mrgregles.bsp_mod_fixes.fix.jei;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-19 (JEI 15.74, client only): JEI starts only after the server's tags have arrived. At login Minecraft 1.20.1 sends the
 * recipe packet before the tag packet, and JEI's start observer starts JEI inside the recipe packet's handler without
 * waiting for the tag sync. Every JEI plugin that resolves a tag ingredient while it registers its recipes then sees an
 * empty tag: Mekanism drops such recipes as "incomplete" (every metallurgic infusing recipe, most of its ore processing),
 * Voltaic's categories throw on them (Electrodynamics, Nuclear Science, Blastcraft), and other plugins show Forge's "Empty
 * Tag" barrier. In single player the integrated server binds the tags before either packet, which is why it only shows on
 * a dedicated server. The mixin asks {@link #holdStart} at the head of JEI's {@code startIfReady} and cancels it while this
 * login's tags have not been received; when the client's tag packet has been handled ({@code TagsUpdatedEvent} from a
 * client packet) it marks the login synced and has {@code startIfReady} run again on the next client tick, after every
 * other listener of that tag event (Thermal rebuilds its recipe lists there), so JEI starts a moment later than it did,
 * with the tags bound. A tag sync is remembered per login (the client's packet listener, new for every login and for
 * every Velocity switch through Client Reset Packet, which keeps the netty connection but makes a new listener), so each
 * login holds again until its own tags arrive, and the order of the login, recipe and tag events does not matter. JEI's
 * own fallback (start late when a screen opens) is untouched.
 */
public final class JeiStartGate {
    public static final String ID = "F-19";
    public static final String WHAT = "JEI starts only after the server's tags have arrived, so plugins do not drop tag-based recipes as incomplete";
    public static final String TARGET = JeiGate.TARGET;

    /** Which login's tags have arrived (the login is identified by any object, compared by identity); pure, so the GameTest server can drive it. */
    public static final class Session {
        private WeakReference<Object> synced = new WeakReference<>(null);

        /** Records that the tag packet of this login has been handled. */
        public synchronized void tagsSynced(Object login) {
            synced = new WeakReference<>(login);
        }

        /** Whether JEI's start must wait: no login yet, or this login's tags have not arrived. */
        public synchronized boolean holdsStart(Object login) {
            return login == null || synced.get() != login;
        }
    }

    private static final Session SESSION = new Session();
    private static final AtomicInteger HELD = new AtomicInteger();
    private static final AtomicInteger RELEASED = new AtomicInteger();
    /** The login whose hold is pending (logged, not yet released), weakly, so a finished login's listener is not kept alive. */
    private static volatile WeakReference<Object> pendingHold = new WeakReference<>(null);

    private JeiStartGate() {}

    /** Whether a tag update counts as this login's sync: only the client packet does, never the integrated server's own data load. */
    public static boolean countsAsSync(boolean fromClientPacket) {
        return fromClientPacket;
    }

    /** Asked at the head of JEI's {@code startIfReady}: whether to cancel it for now. One INFO line per held login. */
    public static boolean holdStart(Object login) {
        boolean hold = SESSION.holdsStart(login);
        if (hold && login != null && pendingHold.get() != login) {
            pendingHold = new WeakReference<>(login);
            HELD.incrementAndGet();
            Fixes.LOGGER.info("F-19: holding JEI's start until the server's tags arrive");
        }
        return hold;
    }

    /**
     * Called when the client has handled this login's tag packet; the mixin then has {@code startIfReady} run on the next
     * client tick. The release is logged and counted only while a hold is pending for this login: a later tag packet of the
     * same login (a server {@code /reload} sends tags, then recipes) marks nothing new and stays quiet.
     */
    public static void tagsSynced(Object login) {
        SESSION.tagsSynced(login);
        if (login != null && pendingHold.get() == login) {
            pendingHold = new WeakReference<>(null);
            RELEASED.incrementAndGet();
            Fixes.LOGGER.info("F-19: the server's tags arrived, JEI may start");
        }
    }

    /** How many logins JEI's start was held for in this run. */
    public static int heldCount() {
        return HELD.get();
    }

    /** How many of those were released by their tag packet. */
    public static int releasedCount() {
        return RELEASED.get();
    }
}
