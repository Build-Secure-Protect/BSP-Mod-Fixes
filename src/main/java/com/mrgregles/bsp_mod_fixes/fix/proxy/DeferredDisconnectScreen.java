package com.mrgregles.bsp_mod_fixes.fix.proxy;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-17 (Minecraft 1.20.1, on a client with Client Reset Packet 0.3.2): the disconnected screen is only ever opened on the
 * render thread. Client Reset Packet overwrites {@code Connection.disconnect} on the client and, when the connection dies
 * during a server switch, calls {@code Minecraft.setScreen(new DisconnectedScreen(...))} from the thread the disconnect
 * happens on, the netty thread ("Netty Client IO"). {@code setScreen} assigns the screen before it initialises it, so the
 * render thread can draw a screen whose {@code init} has not run; with the Fabric Screen API (Forgified Fabric API) that
 * is a crash, "The current screen (DisconnectedScreen) has not been correctly initialised", because that API creates its
 * per-screen event lists in {@code init}. The mixin asks {@link #mustDefer} at the head of {@code setScreen} and, when the
 * answer is yes, hands the same call to the render thread through {@code Minecraft.execute} and cancels the one in hand.
 * Only a {@code DisconnectedScreen} is treated; every other screen is left to vanilla.
 */
public final class DeferredDisconnectScreen {
    public static final String ID = "F-17";
    public static final String WHAT = "a disconnected screen set from the network thread is opened on the render thread instead";
    public static final String TARGET = "Minecraft 1.20.1";

    private static final AtomicInteger DEFERRED = new AtomicInteger();

    private DeferredDisconnectScreen() {}

    /** Whether a {@code setScreen} call must be re-submitted to the render thread: it opens a disconnected screen and runs elsewhere. */
    public static boolean mustDefer(boolean disconnectedScreen, boolean onRenderThread) {
        return disconnectedScreen && !onRenderThread;
    }

    /** Records a deferred call. */
    public static void deferred(String thread) {
        DEFERRED.incrementAndGet();
        Fixes.LOGGER.info("F-17: the disconnected screen was set from thread '{}'; opening it on the render thread instead", thread);
    }

    /** How many disconnected screens have been deferred in this run. */
    public static int deferredCount() {
        return DEFERRED.get();
    }
}
