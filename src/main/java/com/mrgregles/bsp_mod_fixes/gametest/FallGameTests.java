package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.ShockAbsorber;
import lehjr.powersuits.common.constants.MPSConstants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** MF-4: the Shock Absorber (F-7). Fall events are posted on the Forge bus, so Powersuits' cancelled handler and ours both see them. */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class FallGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void shock_absorber_plan_shares_the_fall_by_slider_and_battery(GameTestHelper h) {
        ShockAbsorber.Plan full = ShockAbsorber.plan(30, 10, 100, 1_000_000);
        h.assertTrue(full.absorbed() == 30 && full.drain() == 3000, "slider 1, full battery: all 30 blocks for 3000 FE, found " + full);
        ShockAbsorber.Plan poor = ShockAbsorber.plan(30, 10, 100, 1000);
        h.assertTrue(poor.absorbed() == 10 && poor.drain() == 1000, "slider 1, 1000 FE: 10 blocks for 1000 FE, found " + poor);
        ShockAbsorber.Plan half = ShockAbsorber.plan(30, 5, 50, 1_000_000);
        h.assertTrue(half.absorbed() == 15 && half.drain() == 750, "slider 0.5: half the fall at 50 FE/block, found " + half);
        h.assertTrue(ShockAbsorber.plan(30, 0, 100, 1_000_000).equals(ShockAbsorber.Plan.NOTHING), "slider 0 absorbs nothing");
        h.assertTrue(ShockAbsorber.plan(30, 10, 100, 0).equals(ShockAbsorber.Plan.NOTHING), "an empty battery absorbs nothing");
        h.assertTrue(ShockAbsorber.plan(3, 10, 100, 1_000_000).equals(ShockAbsorber.Plan.NOTHING), "a 3-block fall is left alone");
        h.assertTrue(ShockAbsorber.plan(30, 100, 100, 1_000_000).absorbed() == 30, "a multiplier over 10 still absorbs the fall once");
        ShockAbsorber.Plan free = ShockAbsorber.plan(30, 10, 0, 0);
        h.assertTrue(free.absorbed() == 30 && free.drain() == 0, "a zero cost absorbs for free, found " + free);
        h.assertTrue(ShockAbsorber.plan(30, Double.NaN, 100, 1000).equals(ShockAbsorber.Plan.NOTHING), "NaN multiplier absorbs nothing");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void shock_absorber_at_slider_1_absorbs_a_30_block_fall_with_a_full_battery_and_a_third_with_1000_fe(GameTestHelper h) {
        ServerPlayer player = Suits.wearing(h, EquipmentSlot.FEET, Suits.BOOTS, Suits.BATTERY, Suits.SHOCK_ABSORBER);
        Suits.modular(player, EquipmentSlot.FEET).setModuleTweakDouble(Suits.SHOCK_ABSORBER, MPSConstants.POWER, 1.0);
        double multiplier = Suits.module(player, EquipmentSlot.FEET, Suits.SHOCK_ABSORBER).applyPropertyModifiers(MPSConstants.MULTIPLIER);
        double costPerBlock = Suits.module(player, EquipmentSlot.FEET, Suits.SHOCK_ABSORBER).applyPropertyModifiers(MPSConstants.ENERGY_CONSUMPTION);
        h.assertTrue(multiplier == 10 && costPerBlock == 100, "stock properties at slider 1: multiplier 10, 100 FE/block, found " + multiplier + ", " + costPerBlock);

        Suits.charge(player, 100_000);
        double before = Suits.energy(player);
        LivingFallEvent fall = new LivingFallEvent(player, 30f, 1f);
        MinecraftForge.EVENT_BUS.post(fall);
        h.assertTrue(fall.getDistance() == 0f, "the whole fall is absorbed, distance " + fall.getDistance());
        h.assertTrue(before - Suits.energy(player) == 3000, "30 blocks cost 3000 FE (Powersuits would have taken 30,000 and gone negative), drained " + (before - Suits.energy(player)));

        ServerPlayer poor = Suits.wearing(h, EquipmentSlot.FEET, Suits.BOOTS, Suits.BATTERY, Suits.SHOCK_ABSORBER);
        Suits.modular(poor, EquipmentSlot.FEET).setModuleTweakDouble(Suits.SHOCK_ABSORBER, MPSConstants.POWER, 1.0);
        Suits.charge(poor, 1000);
        LivingFallEvent partial = new LivingFallEvent(poor, 30f, 1f);
        MinecraftForge.EVENT_BUS.post(partial);
        h.assertTrue(partial.getDistance() == 20f, "1000 FE pays for 10 of 30 blocks (Powersuits absorbed nothing), distance " + partial.getDistance());
        h.assertTrue(Suits.energy(poor) == 0, "the battery is empty, energy " + Suits.energy(poor));

        LivingFallEvent small = new LivingFallEvent(player, 3f, 1f);
        MinecraftForge.EVENT_BUS.post(small);
        h.assertTrue(small.getDistance() == 3f && before - Suits.energy(player) == 3000, "a 3-block fall is left alone and costs nothing");
        h.succeed();
    }
}
