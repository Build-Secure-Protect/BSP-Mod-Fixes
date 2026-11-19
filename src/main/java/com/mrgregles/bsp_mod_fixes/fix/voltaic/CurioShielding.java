package com.mrgregles.bsp_mod_fixes.fix.voltaic;

import com.mrgregles.bsp_mod_fixes.FixesConfig;

import java.util.concurrent.atomic.LongAdder;

/**
 * F-40 (Voltaic 1.20.1-1.2.2): a curio with Mekanism radiation shielding shields against Voltaic's radiation too. Voltaic runs
 * Nuclear Science's radiation, and its {@code CapabilityRadiationRecipient.recieveRadiation} shields a dose by the four armour
 * slots only: a Voltaic hazmat piece by its slot's share, anything else by Mekanism's radiation-shielding capability on the
 * stack. Nothing reads a Curios slot. Evolved Mekanism's Portable Hazmat Suit is a curio whose capability answers full
 * shielding while it is switched on, so it shields against Mekanism's radiation (Mekanism's own manager reads armour, then
 * every equipped curio) and not at all against Nuclear Science's.
 *
 * <p>With the fix, {@code VoltaicCurioShieldingMixin} scales the one value Voltaic hands to its Protection-enchantment step,
 * the dose left after the armour loop, by one minus the equipped curios' shielding, read the way Mekanism reads it
 * ({@link CurioShieldingLookup}, one Curios lookup per dose, every curio's capability summed and stopped at 1): full shielding
 * leaves nothing, partial shielding a part. Everything before that point is Voltaic's own: the 0.1 floor, creative players, the
 * iodine rule, armour shares and armour damage, and the recorded dose (after iodine) that the Geiger counter and dosimeter read; everything
 * after it too, the enchantment and the effect. Switch: {@code [voltaic] curioRadiationShielding} in
 * {@code bsp_mod_fixes-server.toml}, read at each dose. Pure {@link #scale}, {@link #sum} and {@link #clamp} for the tests.
 */
public final class CurioShielding {
    public static final String ID = "F-40";
    public static final String WHAT = "a curio with Mekanism radiation shielding (Evolved Mekanism's Portable Hazmat Suit) shields against Voltaic's radiation too (Nuclear Science); Voltaic read the four armour slots only";
    public static final String TARGET = VoltaicGate.TARGET;

    /** What counts as full shielding: Voltaic's own tolerance for its armour sum, so two partial curios summing to 1 leave no residue. */
    public static final double FULL = 0.999999999999;

    private static final LongAdder SHIELDED = new LongAdder();

    private CurioShielding() {}

    /** Whether the switch is on: the server config's value once it is loaded. */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.VOLTAIC_CURIO_RADIATION_SHIELDING.get();
    }

    /** A capability's shielding made safe: 0 when it is negative or not a number, as Voltaic treats a non-finite one. */
    public static double clamp(double shielding) {
        return Double.isFinite(shielding) && shielding > 0 ? shielding : 0;
    }

    /** The running total with one more curio's shielding added, never over 1 (Mekanism's own sum). */
    public static double sum(double total, double shielding) {
        return Math.min(1.0, total + clamp(shielding));
    }

    /**
     * The dose that goes on to Voltaic's enchantment step: {@code unshielded} scaled by one minus the curios' shielding, nothing
     * at full shielding (within {@link #FULL}, as Voltaic judges its armour sum), {@code unshielded} itself when there is none or
     * nothing left.
     */
    public static double scale(double unshielded, double curioShielding) {
        double s = clamp(curioShielding);
        if (unshielded <= 0 || s <= 0) {
            return unshielded;
        }
        return s >= FULL ? 0.0 : unshielded * (1.0 - s);
    }

    /** Records a dose a curio shielded, for the counters. */
    public static void shielded() {
        SHIELDED.increment();
    }

    /** How many doses a curio has shielded, wholly or in part, this session. */
    public static long shieldedDoses() {
        return SHIELDED.sum();
    }
}
