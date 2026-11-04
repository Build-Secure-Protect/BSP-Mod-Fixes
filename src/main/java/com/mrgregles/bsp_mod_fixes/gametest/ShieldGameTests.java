package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.EnergyShield;
import lehjr.numina.common.capabilities.module.powermodule.IPowerModule;
import lehjr.numina.common.capabilities.module.tickable.IPlayerTickModule;
import lehjr.numina.common.energy.ElectricItemUtils;
import lehjr.powersuits.common.constants.MPSConstants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;

/** MF-5: the Energy Shield (F-8), a, b and c. */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class ShieldGameTests {
    static final double EPS = 1e-4;

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void energy_shield_plan_takes_4_percent_per_point_capped_at_80_and_what_the_battery_pays_for(GameTestHelper h) {
        EnergyShield.Plan one = EnergyShield.plan(10, List.of(new EnergyShield.Piece(6, 5000)), 1_000_000);
        h.assertTrue(near(one.absorbed(), 2.4) && one.drain() == 12000, "6 points take 24 % of 10 for 2.4 x 5000 FE, found " + one);
        EnergyShield.Plan four = EnergyShield.plan(10, List.of(new EnergyShield.Piece(6, 5000), new EnergyShield.Piece(6, 5000),
                new EnergyShield.Piece(6, 5000), new EnergyShield.Piece(6, 5000)), 1_000_000);
        h.assertTrue(near(four.absorbed(), 8.0) && four.drain() == 40000, "four shields are capped at 80 %, found " + four);
        EnergyShield.Plan poor = EnergyShield.plan(10, List.of(new EnergyShield.Piece(6, 5000)), 6000);
        h.assertTrue(near(poor.absorbed(), 1.2) && poor.drain() == 6000, "6000 FE pays for half the share, found " + poor);
        h.assertTrue(EnergyShield.plan(10, List.of(new EnergyShield.Piece(6, 5000)), 0).equals(EnergyShield.Plan.NOTHING), "no energy, nothing absorbed");
        h.assertTrue(EnergyShield.plan(10, List.of(), 1_000_000).equals(EnergyShield.Plan.NOTHING), "no shield, nothing absorbed");
        h.assertTrue(EnergyShield.plan(10, List.of(new EnergyShield.Piece(0, 5000)), 1_000_000).equals(EnergyShield.Plan.NOTHING), "slider 0, nothing absorbed");
        EnergyShield.Plan free = EnergyShield.plan(10, List.of(new EnergyShield.Piece(6, 0)), 0);
        h.assertTrue(near(free.absorbed(), 2.4) && free.drain() == 0, "a zero cost absorbs for free, found " + free);
        h.assertTrue(EnergyShield.plan(10, List.of(new EnergyShield.Piece(Double.NaN, Double.NaN)), 1000).equals(EnergyShield.Plan.NOTHING), "NaN properties absorb nothing");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void energy_shield_adds_no_armor_points_but_keeps_its_knockback_resistance(GameTestHelper h) {
        ServerPlayer bare = Suits.wearing(h, EquipmentSlot.CHEST, Suits.CHESTPLATE, Suits.BATTERY);
        ServerPlayer shielded = Suits.wearing(h, EquipmentSlot.CHEST, Suits.CHESTPLATE, Suits.BATTERY, Suits.ENERGY_SHIELD);
        Suits.modular(shielded, EquipmentSlot.CHEST).setModuleTweakDouble(Suits.ENERGY_SHIELD, MPSConstants.MODULE_FIELD_STRENGTH, 1.0);
        IPowerModule shield = Suits.module(shielded, EquipmentSlot.CHEST, Suits.ENERGY_SHIELD);
        h.assertTrue(shield.applyPropertyModifiers(MPSConstants.ARMOR_VALUE_ENERGY) == 6, "stock shield at slider 1 is worth 6 armor points");
        h.assertTrue(shield.isModuleOnline(), "the shield is online");

        ItemStack bareChest = bare.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack shieldedChest = shielded.getItemBySlot(EquipmentSlot.CHEST);
        double armorBare = sum(bareChest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR));
        double armorShielded = sum(shieldedChest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR));
        h.assertTrue(near(armorBare, armorShielded), "the shield adds no armor points: " + armorBare + " without, " + armorShielded + " with");
        double knockback = sum(shieldedChest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.KNOCKBACK_RESISTANCE));
        h.assertTrue(near(knockback, 0.25), "the shield's knockback resistance of 0.25 stays, found " + knockback);
        h.assertTrue(sum(shieldedChest.getAttributeModifiers(EquipmentSlot.LEGS).get(Attributes.ARMOR)) == 0, "another slot's map is untouched");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void energy_shield_stays_online_with_an_empty_battery(GameTestHelper h) {
        ServerPlayer player = Suits.wearing(h, EquipmentSlot.CHEST, Suits.CHESTPLATE, Suits.BATTERY, Suits.ENERGY_SHIELD);
        Suits.modular(player, EquipmentSlot.CHEST).setModuleTweakDouble(Suits.ENERGY_SHIELD, MPSConstants.MODULE_FIELD_STRENGTH, 1.0);
        IPowerModule shield = Suits.module(player, EquipmentSlot.CHEST, Suits.ENERGY_SHIELD);
        h.assertTrue(Suits.energy(player) == 0 && shield.applyPropertyModifiers(MPSConstants.ARMOR_ENERGY_CONSUMPTION) > 0, "empty battery, a per-hit cost");
        ((IPlayerTickModule) shield).onPlayerTickActive(player, player.getItemBySlot(EquipmentSlot.CHEST));
        h.assertTrue(shield.isModuleOnline(), "the shield is still online after a tick with no energy (Powersuits switched it off)");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void energy_shield_absorbs_part_of_a_hit_for_energy_and_ignores_armor_bypassing_damage(GameTestHelper h) {
        ServerPlayer player = Suits.wearing(h, EquipmentSlot.CHEST, Suits.CHESTPLATE, Suits.BATTERY, Suits.ENERGY_SHIELD);
        Suits.modular(player, EquipmentSlot.CHEST).setModuleTweakDouble(Suits.ENERGY_SHIELD, MPSConstants.MODULE_FIELD_STRENGTH, 1.0);
        IPowerModule shield = Suits.module(player, EquipmentSlot.CHEST, Suits.ENERGY_SHIELD);
        h.assertTrue(shield.applyPropertyModifiers(MPSConstants.ARMOR_ENERGY_CONSUMPTION) == 5000, "stock cost at slider 1 is 5000 FE per point");
        DamageSource cactus = h.getLevel().damageSources().cactus();
        DamageSource magic = h.getLevel().damageSources().magic();
        h.assertTrue(!cactus.is(DamageTypeTags.BYPASSES_ARMOR) && magic.is(DamageTypeTags.BYPASSES_ARMOR), "cactus respects armor, magic bypasses it");

        Suits.charge(player, 100_000);
        double before = Suits.energy(player);
        LivingHurtEvent hit = new LivingHurtEvent(player, cactus, 10f);
        MinecraftForge.EVENT_BUS.post(hit);
        h.assertTrue(near(hit.getAmount(), 7.6), "24 % of 10 is absorbed, " + hit.getAmount() + " gets through");
        h.assertTrue(before - Suits.energy(player) == 12000, "2.4 points cost 12,000 FE, drained " + (before - Suits.energy(player)));

        LivingHurtEvent spell = new LivingHurtEvent(player, magic, 10f);
        MinecraftForge.EVENT_BUS.post(spell);
        h.assertTrue(spell.getAmount() == 10f && before - Suits.energy(player) == 12000, "magic is untouched and costs nothing");

        ElectricItemUtils.drainPlayerEnergy(player, Suits.energy(player) - 6000, false);
        h.assertTrue(Suits.energy(player) == 6000, "6000 FE left, found " + Suits.energy(player));
        LivingHurtEvent lowHit = new LivingHurtEvent(player, cactus, 10f);
        MinecraftForge.EVENT_BUS.post(lowHit);
        h.assertTrue(near(lowHit.getAmount(), 8.8), "6000 FE pays for 1.2 of the 2.4 points, " + lowHit.getAmount() + " gets through");
        h.assertTrue(Suits.energy(player) == 0, "the battery is empty");
        h.assertTrue(shield.isModuleOnline(), "and the shield is still online");

        LivingHurtEvent broke = new LivingHurtEvent(player, cactus, 10f);
        MinecraftForge.EVENT_BUS.post(broke);
        h.assertTrue(broke.getAmount() == 10f, "with no energy nothing is absorbed");
        h.succeed();
    }

    static double sum(Collection<AttributeModifier> modifiers) {
        return modifiers.stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < EPS;
    }
}
