package com.mrgregles.bsp_mod_fixes.fix;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-24: Modular Powersuits leaves the player's creative flying speed alone. Three of its modules write vanilla's
 * {@code Abilities.flyingSpeed} every tick they run and never put it back: the Glider adds 0.03 to it while the player
 * glides, the Sprint Assist and Jump Assist set it to a fifth of the walking speed. Powersuits' own flight never reads the
 * field (its thrust has its own maths), so the writes do nothing for a suit wearer; but vanilla saves the value in the
 * player's data ({@code abilities.flySpeed}) and sends it to the client with every abilities update, so it outlives the suit,
 * follows the player across the network, and the moment anything grants creative flight (AdvancedAE's Flight card) the
 * player flies at whatever was left behind: 0.65 after one second of gliding is 144 blocks a second.
 *
 * <p>Two parts. The three mixins ({@code GliderFlyingSpeedMixin}, {@code SprintAssistFlyingSpeedMixin},
 * {@code JumpAssistFlyingSpeedMixin}) remember the speed at the head of each module's tick and put it back at its return
 * ({@link #before}, {@link #restore}). And a guard on the server tick ({@link #onPlayerTick}) puts a survival or adventure
 * player's stored speed back to vanilla's {@link #VANILLA} once a second when it is anything else, with one log line, so
 * every player already affected heals; creative and spectator players are left alone (an op may have set their own), and
 * the guard can be turned off in {@code bsp_mod_fixes-server.toml}. A tick guard rather than a login heal: BSP-InventorySync
 * applies the player's carried abilities on login too, and whichever ran last would win.
 */
public final class FlyingSpeedKeeper {
    public static final String ID = "F-24";
    public static final String WHAT = "Powersuits' Glider, Sprint Assist and Jump Assist leave the creative flying speed alone, and a survival or adventure player's stored speed is kept at vanilla's";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    /** Vanilla's creative flying speed ({@code Abilities.flyingSpeed} of a new player). */
    public static final float VANILLA = 0.05f;
    private static final float EPS = 1e-6f;
    /** How often the guard looks, in ticks. */
    public static final int GUARD_EVERY = 20;

    private static final AtomicInteger RESTORED = new AtomicInteger();
    private static final AtomicInteger HEALED = new AtomicInteger();

    private FlyingSpeedKeeper() {}

    public static boolean isVanilla(float speed) {
        return Math.abs(speed - VANILLA) <= EPS;
    }

    /** Whether a stored speed is put back: never for a creative or spectator player, otherwise whenever it is not vanilla's. */
    public static boolean shouldReset(boolean creativeOrSpectator, float speed) {
        return !creativeOrSpectator && !isVanilla(speed);
    }

    /** At the head of a module's tick: the speed to put back afterwards. */
    public static float before(Player player) {
        return player.getAbilities().getFlyingSpeed();
    }

    /** At the return of a module's tick: the speed is what it was before. Counts the ticks that had changed it. */
    public static void restore(Player player, float saved) {
        if (player.getAbilities().getFlyingSpeed() != saved) {
            player.getAbilities().setFlyingSpeed(saved);
            RESTORED.incrementAndGet();
        }
    }

    /** The guard: once a second, on the server, for every player. */
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % GUARD_EVERY == 0) {
            guard(player, FixesConfig.SPEC.isLoaded() && FixesConfig.KEEP_FLYING_SPEED.get());
        }
    }

    /** Puts the player's stored speed back to vanilla's when the rule says so. Returns whether it did. */
    public static boolean guard(ServerPlayer player, boolean enabled) {
        float speed = player.getAbilities().getFlyingSpeed();
        if (!enabled || !shouldReset(player.isCreative() || player.isSpectator(), speed)) {
            return false;
        }
        player.getAbilities().setFlyingSpeed(VANILLA);
        player.onUpdateAbilities();
        HEALED.incrementAndGet();
        Fixes.LOGGER.info("F-24: {}'s creative flying speed was {}, put back to {} (a Modular Powersuits module leaves it changed)",
                player.getGameProfile().getName(), speed, VANILLA);
        return true;
    }

    /** Module ticks that had changed the speed and were undone. */
    public static int restored() {
        return RESTORED.get();
    }

    /** Players whose stored speed the guard put back. */
    public static int healed() {
        return HEALED.get();
    }
}
