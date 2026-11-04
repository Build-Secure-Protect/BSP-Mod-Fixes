package com.mrgregles.bsp_mod_fixes.fix;

import net.minecraftforge.event.TickEvent;

/**
 * F-3 (Numina 2.3.13): Numina's player tick handler never looks at the tick event's phase, and Forge fires the event twice per
 * tick (START before the player moves, END after), so every module ticked twice: double jet thrust and drain, double cooling.
 * The handler is now let through on START only, the phase that matches the single living-update event it replaced.
 */
public final class TickPhaseFilter {
    public static final String ID = "F-3";
    public static final String WHAT = "Numina's player tick handler runs once per tick (START phase), not on START and END";
    public static final String TARGET = "Numina 2.3.13";

    private TickPhaseFilter() {}

    /** Whether Numina's handler runs for a tick event of this phase. */
    public static boolean runs(TickEvent.Phase phase) {
        return phase == TickEvent.Phase.START;
    }
}
