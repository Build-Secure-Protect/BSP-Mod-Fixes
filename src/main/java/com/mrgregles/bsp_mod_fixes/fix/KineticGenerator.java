package com.mrgregles.bsp_mod_fixes.fix;

import lehjr.numina.common.capabilities.module.tickable.IPlayerTickModule;
import lehjr.numina.common.energy.ElectricItemUtils;
import lehjr.powersuits.common.constants.MPSConstants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * F-6 (Modular Powersuits 2.3.13): the Kinetic Generator samples the player's walked distance on one tick in twenty, only when
 * the player happens to be on the ground at that tick, and its "not generating" branch (flying, riding, airborne) falls through
 * and generates anyway. This replacement measures the horizontal distance the player moved since the previous tick, every tick,
 * on the server, and pays {@code energyPerBlock} per block of it. Nothing is paid while flying, riding, gliding or airborne,
 * and a step of more than {@link #MAX_STEP} blocks (a teleport) is ignored. The position map is only ever touched on the
 * server thread; the step and the on-ground state come from the client's movement packets, so a modified client can earn at
 * most {@code MAX_STEP × energyPerBlock} per tick (the original paid an unbounded sample).
 */
public final class KineticGenerator {
    public static final String ID = "F-6";
    public static final String WHAT = "Kinetic Generator pays energyPerBlock per block walked, every tick, nothing while airborne";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    /** A longer horizontal step in one tick is a teleport, not walking. */
    public static final double MAX_STEP = 1.0;

    private static final Map<Player, Vec3> LAST = Collections.synchronizedMap(new WeakHashMap<>());

    private KineticGenerator() {}

    /** Replaces the module's tick. {@code module} is the generator (its properties and inactive branch), {@code suit} the worn item. */
    public static void tick(Player player, IPlayerTickModule module, ItemStack suit) {
        boolean airborne = player.getAbilities().flying || player.isPassenger() || player.isFallFlying() || !player.onGround();
        if (player.level().isClientSide) {
            // Only the server thread touches the position map: in single player the client's player has the same entity id
            // (and so the same map key) as the server's, and must not evict its entry.
            if (airborne) {
                module.onPlayerTickInactive(player, suit);
            }
            return;
        }
        if (airborne) {
            module.onPlayerTickInactive(player, suit);
            LAST.remove(player);
            return;
        }
        Vec3 now = player.position();
        Vec3 last = LAST.put(player, now);
        if (last == null) {
            return;
        }
        int energy = energyFor(horizontalDistance(last, now), module.applyPropertyModifiers(MPSConstants.ENERGY_GENERATION));
        if (energy > 0 && ElectricItemUtils.getPlayerEnergy(player) < ElectricItemUtils.getMaxPlayerEnergy(player)) {
            ElectricItemUtils.givePlayerEnergy(player, energy, false);
        }
    }

    public static double horizontalDistance(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The energy for one tick's step: 0 for no movement or a teleport, else the step times the rate, in whole FE. */
    public static int energyFor(double distance, double energyPerBlock) {
        if (!(distance > 0.0) || distance > MAX_STEP || !(energyPerBlock > 0.0)) {
            return 0;
        }
        return (int) (distance * energyPerBlock);
    }

    /** For tests: forgets a player's last position. */
    public static void forget(Player player) {
        LAST.remove(player);
    }
}
