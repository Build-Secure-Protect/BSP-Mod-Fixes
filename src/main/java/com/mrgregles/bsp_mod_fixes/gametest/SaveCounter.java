package com.mrgregles.bsp_mod_fixes.gametest;

import java.util.concurrent.atomic.AtomicInteger;

/** GameTest server only: how many times any {@code ServerChunkCache.save(boolean)} ran, counted by a test-only mixin. */
public final class SaveCounter {
    private static final AtomicInteger SAVES = new AtomicInteger();

    private SaveCounter() {}

    public static void count() {
        SAVES.incrementAndGet();
    }

    public static int saves() {
        return SAVES.get();
    }
}
