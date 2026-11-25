package com.mrgregles.bsp_mod_fixes.fix.powersuits;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-46 (Modular Powersuits 2.3.13): the Rail Gun fires without starting an item use. The module's {@code Ticker.use} is a
 * hitscan: when the cooldown is over and the energy is there it drains, heats, ray-traces and damages at once, then, shot or
 * not, calls {@code player.startUsingItem(hand)} and answers success. The Power Fist's {@code getUseAnimation} is
 * {@code UseAnim.BOW} for every mode and its use duration is the active module's, 25 ticks for the Rail Gun, so every right
 * click drew the bow pose for 25 ticks (the Plasma Cannon's charge look) and a click during the 15-tick cooldown restarted it
 * with nothing fired: the owner's "charging animation, then it glitches". Nothing of the Rail Gun needs the use: its
 * {@code releaseUsing} is the interface's empty default and no charge is read on release. Mod at fault: Modular Powersuits.
 *
 * <p>The fix ({@code PowersuitsRailgunUseMixin}) redirects that one {@code startUsingItem} call to {@link #skipItemUse}, which
 * does nothing but count: the shot, the cooldown, the sound, the particles and the success result are as before, the player
 * is never "using" the fist, so no pose and no 25-tick use. On both sides (the client runs the same {@code use} for its own
 * pose). No switch (D151). The Plasma Cannon and the Blade Launcher, which start a use the same way and read the charge on
 * release, are not touched. Pure {@link #startsItemUse} for the tests.
 */
public final class RailgunShot {
    public static final String ID = "F-46";
    public static final String WHAT = "the Rail Gun fires its hitscan shot without starting a 25-tick item use, so the Power Fist no longer draws the bow pose on every click (Powersuits started the use on every right click, shot or not, and nothing of the Rail Gun read it)";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    private static final LongAdder SKIPPED = new LongAdder();
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private RailgunShot() {}

    /** Pure: whether the Rail Gun's use starts an item use. Never: a hitscan shot has nothing to charge or release. */
    public static boolean startsItemUse() {
        return false;
    }

    /** The redirect's handler, in place of {@code player.startUsingItem(hand)}: counts the skipped use; the first is logged at DEBUG. */
    public static void skipItemUse(Player player, InteractionHand hand) {
        SKIPPED.increment();
        if (LOGGED.compareAndSet(false, true)) {
            Fixes.LOGGER.debug("BSP-Mod Fixes F-46: the Rail Gun's use no longer starts an item use ({} hand, {})", hand, player.getScoreboardName());
        }
    }

    /** How many item uses were skipped so far (one per Rail Gun click, both sides). */
    public static long skipped() {
        return SKIPPED.sum();
    }
}
