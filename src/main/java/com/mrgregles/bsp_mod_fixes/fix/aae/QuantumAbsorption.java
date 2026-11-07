package com.mrgregles.bsp_mod_fixes.fix.aae;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-25: the quantum armor's damage absorption is shown to the wearer. AdvancedAE 1.3.6 takes a share of every hit a player
 * receives (after armor, enchantments and absorption hearts: Forge's {@code LivingDamageEvent}) out of the four pieces' AE
 * charge, a quarter each, at {@link #AE_PER_POINT} per damage point, and says nothing: against the armor's own 80 %
 * reduction and a buffer of hundreds of millions of AE, the player sees neither the damage taken off nor the energy paid.
 * The replacement handler ({@code QuantumAbsorptionHandler}, through {@code mixin/AaeDamageAbsorptionMixin}) keeps
 * AdvancedAE's arithmetic and rules exactly and sends the wearer one packet per absorption, which the client's suit readout
 * ({@code client/QuantumReadout}, drawn after {@link ReadoutState}) shows on its damage row. This class is the pure part:
 * the plan and the rule for a hit that ends under one point. No AdvancedAE class is referenced here, so the GameTest server
 * can call it without the mod.
 */
public final class QuantumAbsorption {
    public static final String ID = "F-25";
    public static final String WHAT = "the quantum armor's damage absorption is shown on a suit readout: the hit, the share taken off and the AE paid, with the suit's charge";
    public static final String TARGET = AaeGate.TARGET;

    /** AdvancedAE's price: AE per point of damage absorbed, a float as in its code, so the products match to the bit. */
    public static final float AE_PER_POINT = 1000f;
    /** AdvancedAE's share: one quarter of the absorbable amount per armor piece, four pieces. */
    public static final int PIECES = 4;

    private static final AtomicInteger REPORTED = new AtomicInteger();

    private QuantumAbsorption() {}

    /**
     * What each piece pays and what that takes off the hit. {@code paid[i]} is the AE taken from piece {@code i}, in the order
     * the pieces were given; {@code absorbed} is the damage taken off the hit, in points.
     */
    public record Plan(float absorbed, double[] paid) {
        public static final Plan NOTHING = new Plan(0, new double[0]);

        public double energy() {
            return Arrays.stream(paid).sum();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Plan p && p.absorbed == absorbed && Arrays.equals(p.paid, paid);
        }

        @Override
        public int hashCode() {
            return Float.hashCode(absorbed) * 31 + Arrays.hashCode(paid);
        }

        @Override
        public String toString() {
            return "Plan[absorbed=" + absorbed + ", paid=" + Arrays.toString(paid) + "]";
        }
    }

    /**
     * AdvancedAE's plan for a hit: {@code percent} of {@code amount} may be absorbed, a quarter of that per piece; each piece
     * pays its quarter at {@link #AE_PER_POINT} or whatever charge it has, and the damage taken off is what was paid, back in
     * points. {@code charges} is one entry per quantum piece worn (any number, each a quarter); a piece with no charge pays
     * nothing and absorbs nothing, nothing is redistributed.
     */
    public static Plan plan(float amount, int percent, double[] charges) {
        if (charges == null || charges.length == 0 || !(amount > 0) || percent <= 0) {
            return Plan.NOTHING;
        }
        float maxAbsorption = amount * percent / 100f;
        float amountPerPiece = maxAbsorption / PIECES;
        double[] paid = new double[charges.length];
        float absorbed = 0;
        for (int i = 0; i < charges.length; i++) {
            double charge = Double.isNaN(charges[i]) ? 0 : Math.max(0, charges[i]);
            paid[i] = Math.min(amountPerPiece * AE_PER_POINT, charge); // a float product widened, as AdvancedAE passes it
            absorbed += (float) paid[i] / AE_PER_POINT;
        }
        return new Plan(absorbed, paid);
    }

    /**
     * AdvancedAE's rule, kept as it is (D38 is still open): a hit that ends under one point is cancelled altogether, whoever
     * the player is and whether or not anything was absorbed.
     */
    public static boolean cancelsUnder(float amount) {
        return amount < 1.0f;
    }

    /** Whether a plan is worth telling the wearer: something was taken off the hit. */
    public static boolean worthReporting(Plan plan) {
        return plan.absorbed() > 0;
    }

    /** Counts an absorption sent to the wearer (for the tests and the log). */
    public static void reported() {
        REPORTED.incrementAndGet();
    }

    public static int reportedCount() {
        return REPORTED.get();
    }
}
