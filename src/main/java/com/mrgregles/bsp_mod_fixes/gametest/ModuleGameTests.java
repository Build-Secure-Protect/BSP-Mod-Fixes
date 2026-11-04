package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.KineticGenerator;
import com.mrgregles.bsp_mod_fixes.fix.NightVisionTiming;
import lehjr.numina.common.capabilities.module.tickable.IPlayerTickModule;
import lehjr.powersuits.common.constants.MPSConstants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** MF-3: Night Vision timing (F-5) and the Kinetic Generator (F-6), driven by calling the real modules' tick methods. */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class ModuleGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void night_vision_charges_once_and_refreshes_under_220_ticks_with_a_400_tick_effect(GameTestHelper h) {
        ServerPlayer player = Suits.wearing(h, EquipmentSlot.HEAD, Suits.HELMET, Suits.BATTERY, Suits.NIGHT_VISION);
        Suits.charge(player, 10_000);
        IPlayerTickModule nightVision = (IPlayerTickModule) Suits.module(player, EquipmentSlot.HEAD, Suits.NIGHT_VISION);
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        double cost = nightVision.applyPropertyModifiers(MPSConstants.ENERGY_CONSUMPTION);
        double start = Suits.energy(player);
        h.assertTrue(start > cost * 3, "the battery took the charge, energy " + start);

        nightVision.onPlayerTickActive(player, helmet);
        h.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "the first tick applied night vision");
        h.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == NightVisionTiming.DURATION,
                "a 400-tick effect, found " + player.getEffect(MobEffects.NIGHT_VISION).getDuration());
        h.assertTrue(Suits.energy(player) == start - cost, "one charge of " + cost + " FE, energy " + Suits.energy(player));

        nightVision.onPlayerTickActive(player, helmet);
        h.assertTrue(Suits.energy(player) == start - cost, "a second tick with 400 ticks left charged nothing, energy " + Suits.energy(player));

        withEffectOf(player, 300);
        nightVision.onPlayerTickActive(player, helmet);
        h.assertTrue(Suits.energy(player) == start - cost, "300 ticks left is not refreshed (Powersuits would have), energy " + Suits.energy(player));
        h.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == 300, "the 300-tick effect is left alone");

        withEffectOf(player, NightVisionTiming.REFRESH_UNDER - 1);
        nightVision.onPlayerTickActive(player, helmet);
        h.assertTrue(Suits.energy(player) == start - 2 * cost, "219 ticks left is refreshed for one more charge, energy " + Suits.energy(player));
        h.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == NightVisionTiming.DURATION, "back to 400 ticks");
        h.succeed();
    }

    private static void withEffectOf(ServerPlayer player, int ticks) {
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0, false, false));
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void kinetic_generator_pays_per_block_walked_and_nothing_airborne_or_teleporting(GameTestHelper h) {
        double rate = 2000;
        h.assertTrue(KineticGenerator.energyFor(0.5, rate) == 1000, "half a block at 2000 is 1000 FE");
        h.assertTrue(KineticGenerator.energyFor(0.0, rate) == 0 && KineticGenerator.energyFor(-1, rate) == 0, "no step, no energy");
        h.assertTrue(KineticGenerator.energyFor(1.5, rate) == 0, "a 1.5-block step is a teleport");
        h.assertTrue(KineticGenerator.energyFor(Double.NaN, rate) == 0 && KineticGenerator.energyFor(0.5, 0) == 0, "NaN and a zero rate pay nothing");

        ServerPlayer player = Suits.wearing(h, EquipmentSlot.LEGS, Suits.LEGGINGS, Suits.BATTERY, Suits.KINETIC_GENERATOR);
        IPlayerTickModule generator = (IPlayerTickModule) Suits.module(player, EquipmentSlot.LEGS, Suits.KINETIC_GENERATOR);
        ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
        double perBlock = generator.applyPropertyModifiers(MPSConstants.ENERGY_GENERATION);
        h.assertTrue(perBlock > 0, "the generator has a rate, found " + perBlock);
        Vec3 start = player.position();
        player.setOnGround(true);
        KineticGenerator.forget(player);

        generator.onPlayerTickActive(player, leggings);
        h.assertTrue(Suits.energy(player) == 0, "the first tick only records the position, energy " + Suits.energy(player));

        player.moveTo(start.add(0.9, 0, 0));
        generator.onPlayerTickActive(player, leggings);
        double gained = Suits.energy(player);
        h.assertTrue(Math.abs(gained - 0.9 * perBlock) <= 1, "0.9 block on the ground paid 0.9 x " + perBlock + ", found " + gained);

        generator.onPlayerTickActive(player, leggings);
        h.assertTrue(Suits.energy(player) == gained, "standing still pays nothing");

        player.setOnGround(false);
        player.moveTo(start.add(1.8, 0, 0));
        generator.onPlayerTickActive(player, leggings);
        h.assertTrue(Suits.energy(player) == gained, "an airborne step pays nothing");
        player.setOnGround(true);
        generator.onPlayerTickActive(player, leggings);
        h.assertTrue(Suits.energy(player) == gained, "landing only records the position again");

        player.moveTo(start.add(6.8, 0, 0));
        generator.onPlayerTickActive(player, leggings);
        h.assertTrue(Suits.energy(player) == gained, "a 5-block jump in one tick is a teleport and pays nothing");

        player.moveTo(start.add(7.3, 0, 0));
        generator.onPlayerTickActive(player, leggings);
        h.assertTrue(Math.abs(Suits.energy(player) - gained - 0.5 * perBlock) <= 1, "half a block pays half the rate, found " + (Suits.energy(player) - gained));
        h.succeed();
    }
}
