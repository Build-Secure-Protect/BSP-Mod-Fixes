package com.mrgregles.bsp_mod_fixes.fix;

import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * F-4 (Modular Powersuits 2.3.13): Powersuits' player update handler subscribes to the base {@code LivingEvent}, so it runs for
 * every living sub-event of every entity (hurt, jump, fall, drops, ...) and rewrites the player's fall distance at each. It
 * is meant to run once per tick; it is now let through for {@code LivingTickEvent} only.
 */
public final class LivingEventFilter {
    public static final String ID = "F-4";
    public static final String WHAT = "Powersuits' player update handler runs on LivingTickEvent only, not every LivingEvent";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    private LivingEventFilter() {}

    /** Whether Powersuits' handler runs for this event. */
    public static boolean runs(LivingEvent event) {
        return event instanceof LivingEvent.LivingTickEvent;
    }
}
