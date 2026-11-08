package com.mrgregles.bsp_mod_fixes.fix.gobber;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.LongAdder;

/**
 * F-27: Gobber 2's dragon armour sends the player's abilities only when they changed. Gobber's {@code onArmorTick} sets
 * {@code mayfly} and calls {@code Player.onUpdateAbilities()} every tick for every dragon piece worn, with no side check.
 * Forge calls the armour tick from the inventory tick on the client and on the server, so with the full set on the server
 * sends four {@code ClientboundPlayerAbilitiesPacket}s a tick and the client four {@code ServerboundPlayerAbilitiesPacket}s;
 * vanilla's two handlers overwrite the {@code flying} flag with whatever the other side last sent, which only ever happens
 * on a change in vanilla (Gobber's sends also resynced the other abilities fields by accident; every writer of those
 * sends on its own). With any latency the flag becomes a loop with no damping: the double tap turns flight on for one
 * tick in every round trip, the player "vibrates" and flight never holds (Gobber's issues #14 and #25, fine in single player).
 *
 * <p>The mixin ({@code GobberDragonFlightMixin}) remembers the two flags at the head of the method ({@link #begin}) and
 * redirects its {@code onUpdateAbilities()} calls to {@link #update}: on the server the update is sent when
 * {@code mayfly} or {@code flying} changed in this call, on the client never (the client has nothing to tell the server here;
 * the double tap sends its own packet; D54). The "armour taken off" branch still sends, since it changes {@code mayfly}.
 * {@code [gobber] fixDragonFlight} in {@code bsp_mod_fixes-server.toml} (sent to clients at login) turns the rule off, and every
 * call then passes through as Gobber makes it (D58). Pure {@link #shouldSend} for the tests.
 */
public final class DragonFlightSends {
    public static final String ID = "F-27";
    public static final String WHAT = "the dragon armour sends the player's abilities only when they changed, and never from the client, so creative flight holds on a server";
    public static final String TARGET = GobberGate.TARGET;

    /** No head recorded: the call is passed through. */
    public static final int NONE = -1;
    /** The two flags of a call's head, per thread: the client and the server tick the same item instance in single player. */
    private static final ThreadLocal<int[]> BEFORE = ThreadLocal.withInitial(() -> new int[]{NONE});

    /** Counters for the tests and the log; 80 calls a tick for one player would wrap an int in about 15 days. */
    private static final LongAdder SENT = new LongAdder();
    private static final LongAdder HELD = new LongAdder();

    private DragonFlightSends() {}

    /** The two flags as one value. */
    public static int flags(boolean mayfly, boolean flying) {
        return (mayfly ? 1 : 0) | (flying ? 2 : 0);
    }

    public static int flags(Abilities abilities) {
        return flags(abilities.mayfly, abilities.flying);
    }

    /**
     * Whether an {@code onUpdateAbilities()} call of the dragon armour's tick goes out: always with the rule off or no head
     * recorded; never on the client; on the server only when the flags changed since the head of the call.
     */
    public static boolean shouldSend(boolean enabled, boolean clientSide, int before, int now) {
        if (!enabled || before == NONE) {
            return true;
        }
        if (clientSide) {
            return false;
        }
        return before != now;
    }

    /** Whether the rule is on: the server config's switch, read once it is loaded (a client gets the server's copy at login). */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.FIX_DRAGON_FLIGHT.get();
    }

    /** At the head of the armour tick: remembers the flags. */
    public static void begin(Player player) {
        BEFORE.get()[0] = flags(player.getAbilities());
    }

    /** In place of each {@code onUpdateAbilities()} of the armour tick: sends when the rule says so, counts either way. */
    public static void update(Player player) {
        int before = BEFORE.get()[0];
        BEFORE.get()[0] = NONE;
        if (shouldSend(enabled(), player.level().isClientSide, before, flags(player.getAbilities()))) {
            player.onUpdateAbilities();
            SENT.increment();
        } else {
            HELD.increment();
        }
    }

    /** Updates the dragon armour's tick sent through the mixin. */
    public static long sent() {
        return SENT.sum();
    }

    /** Updates the dragon armour's tick asked for and not sent. */
    public static long held() {
        return HELD.sum();
    }
}
