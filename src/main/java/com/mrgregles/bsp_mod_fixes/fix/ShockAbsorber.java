package com.mrgregles.bsp_mod_fixes.fix;

import lehjr.numina.common.capabilities.NuminaCapabilities;
import lehjr.numina.common.capabilities.inventory.modularitem.IModularItem;
import lehjr.numina.common.capabilities.module.powermodule.IPowerModule;
import lehjr.numina.common.energy.ElectricItemUtils;
import lehjr.powersuits.common.constants.MPSConstants;
import lehjr.powersuits.common.constants.MPSRegistryNames;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/**
 * F-7 (Modular Powersuits 2.3.13): the Shock Absorber was all or nothing: if the battery could not pay for the whole fall,
 * nothing was absorbed, and its multiplier ran 0..10 so a fall could be "absorbed" ten times over. Powersuits' fall handler is
 * cancelled by a mixin and this handler takes its place: the slider sets the share of the fall absorbed (0..100 %), the module's
 * energy cost per block is paid for what is absorbed, and when the battery holds less, the part it can pay for is absorbed.
 * Falls of three blocks or less are left alone, as before.
 */
public final class ShockAbsorber {
    public static final String ID = "F-7";
    public static final String WHAT = "Shock Absorber absorbs the share of a fall the slider sets and the battery can pay for";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    /** Powersuits' multiplier at slider 1; the slider's share of the fall is multiplier / FULL_MULTIPLIER. */
    public static final double FULL_MULTIPLIER = 10.0;
    public static final double MIN_DISTANCE = 3.0;

    /** What one fall costs: blocks absorbed and FE drained. */
    public record Plan(double absorbed, int drain) {
        public static final Plan NOTHING = new Plan(0, 0);
    }

    private ShockAbsorber() {}

    /**
     * @param distance   the fall in blocks
     * @param multiplier the module's "multiplier" property (0 at slider 0, 10 at slider 1)
     * @param energyCon  the module's "energyCon" property, FE per block absorbed
     * @param energy     the FE the player has
     */
    public static Plan plan(double distance, double multiplier, double energyCon, double energy) {
        if (!(distance > MIN_DISTANCE)) {
            return Plan.NOTHING;
        }
        double share = clamp(multiplier / FULL_MULTIPLIER, 0.0, 1.0);
        double costPerBlock = Math.max(0.0, finite(energyCon));
        double absorbed = distance * share;
        if (costPerBlock > 0.0) {
            absorbed = Math.min(absorbed, Math.max(0.0, finite(energy)) / costPerBlock);
        }
        if (!(absorbed > 0.0)) {
            return Plan.NOTHING;
        }
        return new Plan(absorbed, (int) Math.ceil(absorbed * costPerBlock));
    }

    /** The fall handler, on both sides as Powersuits' was; only the server drains (Numina's drain is a no-op elsewhere). */
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getDistance() > MIN_DISTANCE)) {
            return;
        }
        IPowerModule absorber = player.getItemBySlot(EquipmentSlot.FEET).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .filter(IModularItem.class::isInstance).map(IModularItem.class::cast)
                .map(boots -> boots.getOnlineModuleOrEmpty(MPSRegistryNames.SHOCK_ABSORBER_MODULE))
                .flatMap(module -> module.getCapability(NuminaCapabilities.POWER_MODULE).resolve())
                .orElse(null);
        if (absorber == null) {
            return;
        }
        Plan plan = plan(event.getDistance(), absorber.applyPropertyModifiers(MPSConstants.MULTIPLIER),
                absorber.applyPropertyModifiers(MPSConstants.ENERGY_CONSUMPTION), ElectricItemUtils.getPlayerEnergy(player));
        if (plan.absorbed() <= 0) {
            return;
        }
        if (plan.drain() > 0) {
            ElectricItemUtils.drainPlayerEnergy(player, plan.drain(), false);
        }
        event.setDistance((float) Math.max(0.0, event.getDistance() - plan.absorbed()));
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo || Double.isNaN(v) ? lo : Math.min(v, hi);
    }

    static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
