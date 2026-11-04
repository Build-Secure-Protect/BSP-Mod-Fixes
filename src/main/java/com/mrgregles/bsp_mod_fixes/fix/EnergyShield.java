package com.mrgregles.bsp_mod_fixes.fix;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import lehjr.numina.common.capabilities.NuminaCapabilities;
import lehjr.numina.common.capabilities.inventory.modularitem.IModularItem;
import lehjr.numina.common.capabilities.module.powermodule.IPowerModule;
import lehjr.numina.common.energy.ElectricItemUtils;
import lehjr.powersuits.common.constants.MPSConstants;
import lehjr.powersuits.common.constants.MPSRegistryNames;
import lehjr.powersuits.common.item.armor.AbstractElectricItemArmor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F-8 (Modular Powersuits 2.3.13): the Energy Shield was plain armor that never used energy. Powersuits added its "armorEnergy"
 * to the armor attribute, its energy-draining {@code damageItem} was dead code (the armor cannot be depleted, so Forge never calls
 * it), and its ticker switched the module off for good whenever energy dipped under one hit's cost. Three parts:
 * (a) {@link #withoutShieldArmor} takes the shield's points back out of the armor attribute (mixin at the method's return);
 * (b) the ticker is cancelled (mixin), so the module stays on;
 * (c) {@link #onHurt} absorbs a share of each hit, 4 % per shield point as vanilla armor does, capped at 80 %, and drains
 * {@code armorEnergyPerDamage} FE per point absorbed; with less energy, the share it can pay for. Sources that bypass armor or
 * invulnerability are left alone. It runs before vanilla armor, so plating still reduces what gets through.
 */
public final class EnergyShield {
    public static final String ID = "F-8";
    public static final String WHAT = "Energy Shield absorbs hits for energy instead of adding armor points, and stays on when low";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    /** Vanilla's damage reduction per armor point. */
    public static final double SHARE_PER_POINT = 0.04;
    /** The most of a hit all shields together may take, as vanilla armor caps at 20 points. */
    public static final double MAX_SHARE = 0.8;

    /** One worn shield: its armor points and FE per damage point absorbed. */
    public record Piece(double armorPoints, double costPerPoint) {}

    /** What one hit costs: damage absorbed and FE drained. */
    public record Plan(double absorbed, int drain) {
        public static final Plan NOTHING = new Plan(0, 0);
    }

    private EnergyShield() {}

    // (a) armor attribute --------------------------------------------------------------------------------------------------

    /** The attribute map for {@code stack} in {@code slot} with the online shield's points taken out of the armor modifier. */
    public static Multimap<Attribute, AttributeModifier> withoutShieldArmor(EquipmentSlot slot, ItemStack stack, Multimap<Attribute, AttributeModifier> in) {
        if (slot != Mob.getEquipmentSlotForItem(stack)) {
            return in;
        }
        IPowerModule shield = onlineShield(stack);
        if (shield == null) {
            return in;
        }
        double points = shield.applyPropertyModifiers(MPSConstants.ARMOR_VALUE_ENERGY);
        if (!(points > 0)) {
            return in;
        }
        UUID id = AbstractElectricItemArmor.ARMOR_MODIFIERS[slot.getIndex()];
        ImmutableMultimap.Builder<Attribute, AttributeModifier> out = ImmutableMultimap.builder();
        boolean changed = false;
        for (Map.Entry<Attribute, AttributeModifier> e : in.entries()) {
            AttributeModifier m = e.getValue();
            if (e.getKey() == Attributes.ARMOR && m.getId().equals(id)) {
                changed = true;
                double left = m.getAmount() - points;
                if (left > 0) {
                    out.put(Attributes.ARMOR, new AttributeModifier(id, m.getName(), left, m.getOperation()));
                }
            } else {
                out.put(e.getKey(), m);
            }
        }
        return changed ? out.build() : in;
    }

    /** The online, allowed energy shield of a worn modular item, or null. */
    public static IPowerModule onlineShield(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .filter(IModularItem.class::isInstance).map(IModularItem.class::cast)
                .map(item -> item.getOnlineModuleOrEmpty(MPSRegistryNames.ENERGY_SHIELD_MODULE))
                .flatMap(module -> module.getCapability(NuminaCapabilities.POWER_MODULE).resolve())
                .orElse(null);
    }

    // (c) hurt handler ----------------------------------------------------------------------------------------------------------

    public static Plan plan(double amount, List<Piece> pieces, double energy) {
        if (!(amount > 0) || pieces.isEmpty()) {
            return Plan.NOTHING;
        }
        double[] shares = new double[pieces.size()];
        double total = 0;
        for (int i = 0; i < shares.length; i++) {
            shares[i] = Math.max(0, finite(pieces.get(i).armorPoints())) * SHARE_PER_POINT;
            total += shares[i];
        }
        if (total > MAX_SHARE) {
            for (int i = 0; i < shares.length; i++) {
                shares[i] *= MAX_SHARE / total;
            }
        }
        double absorbed = 0;
        double cost = 0;
        for (int i = 0; i < shares.length; i++) {
            double part = amount * shares[i];
            absorbed += part;
            cost += part * Math.max(0, finite(pieces.get(i).costPerPoint()));
        }
        if (cost > 0) {
            double available = Math.max(0, finite(energy));
            if (available < cost) {
                double fraction = available / cost;
                absorbed *= fraction;
                cost = available;
            }
        }
        if (!(absorbed > 0)) {
            return Plan.NOTHING;
        }
        return new Plan(absorbed, (int) Math.ceil(cost));
    }

    /** Server only (the hurt event fires there): players wearing online shields pay energy to absorb part of the hit. */
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        List<Piece> pieces = new ArrayList<>(4);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            IPowerModule shield = onlineShield(player.getItemBySlot(slot));
            if (shield != null) {
                pieces.add(new Piece(shield.applyPropertyModifiers(MPSConstants.ARMOR_VALUE_ENERGY),
                        shield.applyPropertyModifiers(MPSConstants.ARMOR_ENERGY_CONSUMPTION)));
            }
        }
        Plan plan = plan(event.getAmount(), pieces, ElectricItemUtils.getPlayerEnergy(player));
        if (plan.absorbed() <= 0) {
            return;
        }
        if (plan.drain() > 0) {
            ElectricItemUtils.drainPlayerEnergy(player, plan.drain(), false);
        }
        event.setAmount((float) Math.max(0, event.getAmount() - plan.absorbed()));
    }

    static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
