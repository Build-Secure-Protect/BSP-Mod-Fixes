package com.mrgregles.bsp_mod_fixes.fix;

/**
 * F-5 (Modular Powersuits 2.3.13): the Night Vision module refreshes the effect whenever its remaining duration is under 500
 * ticks, but applies a 300-tick effect, so it refreshes, and charges its energy cost, every single tick. With a 400-tick effect
 * refreshed under 220 ticks it charges once about every 9 seconds and the effect never drops under 200 ticks, where vanilla
 * starts to flicker. The mixin swaps the two constants in the module's tick method.
 */
public final class NightVisionTiming {
    public static final String ID = "F-5";
    public static final String WHAT = "Night Vision refreshes (and charges) about every 9 s instead of every tick";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    /** Powersuits' constants. */
    public static final int ORIGINAL_REFRESH_UNDER = 500;
    public static final int ORIGINAL_DURATION = 300;
    /** Ours: refresh when the remaining duration is under this many ticks ... */
    public static final int REFRESH_UNDER = 220;
    /** ... with an effect of this many ticks. */
    public static final int DURATION = 400;

    private NightVisionTiming() {}
}
