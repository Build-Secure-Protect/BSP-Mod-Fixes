package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.FlyingSpeedKeeper;
import lehjr.numina.common.capabilities.NuminaCapabilities;
import lehjr.numina.common.capabilities.module.tickable.IPlayerTickModule;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * MF-21: the creative flying speed Powersuits' modules leave on the player (F-24): the real Glider, Sprint Assist and Jump
 * Assist tickers on a dressed mock player, and the guard that puts a stored speed back.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class FlyingSpeedGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void flying_speed_guard_puts_a_survival_players_stored_speed_back_and_leaves_creative_and_spectator_alone(GameTestHelper h) {
        h.assertTrue(!FlyingSpeedKeeper.shouldReset(false, FlyingSpeedKeeper.VANILLA), "vanilla's 0.05 is left alone");
        h.assertTrue(FlyingSpeedKeeper.shouldReset(false, 0.65f), "a glider's 0.65 is put back");
        h.assertTrue(FlyingSpeedKeeper.shouldReset(false, 0.02f), "a sprint assist's 0.02 is put back, slower than vanilla or not");
        h.assertTrue(!FlyingSpeedKeeper.shouldReset(true, 0.65f), "a creative or spectator player keeps whatever they have");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.KEEP_FLYING_SPEED.get(), "the guard is on by default");

        ServerPlayer survival = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        survival.getAbilities().setFlyingSpeed(0.65f);
        int healed = FlyingSpeedKeeper.healed();
        h.assertTrue(!FlyingSpeedKeeper.guard(survival, false) && survival.getAbilities().getFlyingSpeed() == 0.65f, "switched off, the guard changes nothing");
        h.assertTrue(FlyingSpeedKeeper.guard(survival, true), "the guard acts on 0.65");
        h.assertTrue(FlyingSpeedKeeper.isVanilla(survival.getAbilities().getFlyingSpeed()), "put back to 0.05, found " + survival.getAbilities().getFlyingSpeed());
        h.assertTrue(FlyingSpeedKeeper.healed() == healed + 1, "one heal counted");
        h.assertTrue(!FlyingSpeedKeeper.guard(survival, true) && FlyingSpeedKeeper.healed() == healed + 1, "a second look changes nothing");

        ServerPlayer creative = MockPlayers.serverPlayer(h, GameType.CREATIVE);
        creative.getAbilities().setFlyingSpeed(0.65f);
        h.assertTrue(!FlyingSpeedKeeper.guard(creative, true) && creative.getAbilities().getFlyingSpeed() == 0.65f, "a creative player keeps 0.65");
        ServerPlayer spectator = MockPlayers.serverPlayer(h, GameType.SPECTATOR);
        spectator.getAbilities().setFlyingSpeed(0.65f);
        h.assertTrue(!FlyingSpeedKeeper.guard(spectator, true) && spectator.getAbilities().getFlyingSpeed() == 0.65f, "a spectator keeps 0.65");

        // the listener on the bus: a server player's tick, END phase, on a guard tick
        ServerPlayer adventure = MockPlayers.serverPlayer(h, GameType.ADVENTURE);
        adventure.getAbilities().setFlyingSpeed(0.3f);
        adventure.tickCount = FlyingSpeedKeeper.GUARD_EVERY * 3 + 1;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, adventure));
        h.assertTrue(adventure.getAbilities().getFlyingSpeed() == 0.3f, "between guard ticks nothing happens");
        adventure.tickCount = FlyingSpeedKeeper.GUARD_EVERY * 3;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, adventure));
        h.assertTrue(adventure.getAbilities().getFlyingSpeed() == 0.3f, "the START phase is not the guard's");
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, adventure));
        h.assertTrue(FlyingSpeedKeeper.isVanilla(adventure.getAbilities().getFlyingSpeed()), "the guard tick put an adventure player back, found " + adventure.getAbilities().getFlyingSpeed());
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void powersuits_glider_sprint_assist_and_jump_assist_leave_the_creative_flying_speed_as_they_found_it(GameTestHelper h) {
        // Sprint Assist: sprinting a block on the ground with a charged suit is the branch that writes the speed
        ServerPlayer sprinter = Suits.wearing(h, EquipmentSlot.LEGS, Suits.LEGGINGS, Suits.BATTERY, Suits.SPRINT_ASSIST);
        Suits.charge(sprinter, 1_000_000);
        IPlayerTickModule sprintAssist = (IPlayerTickModule) Suits.module(sprinter, EquipmentSlot.LEGS, Suits.SPRINT_ASSIST);
        ItemStack leggings = sprinter.getItemBySlot(EquipmentSlot.LEGS);
        sprinter.setOnGround(true);
        sprinter.setSprinting(true);
        sprinter.walkDistO = 0;
        sprinter.walkDist = 1;
        int restored = FlyingSpeedKeeper.restored();
        sprintAssist.onPlayerTickActive(sprinter, leggings);
        h.assertTrue(FlyingSpeedKeeper.isVanilla(sprinter.getAbilities().getFlyingSpeed()), "after a sprint assist tick the flying speed is still 0.05, found " + sprinter.getAbilities().getFlyingSpeed());
        h.assertTrue(FlyingSpeedKeeper.restored() == restored + 1, "the module had changed it and the fix put it back, so the writing branch really ran: " + (FlyingSpeedKeeper.restored() - restored));
        sprinter.setSprinting(false);
        sprintAssist.onPlayerTickActive(sprinter, leggings);
        h.assertTrue(FlyingSpeedKeeper.isVanilla(sprinter.getAbilities().getFlyingSpeed()) && FlyingSpeedKeeper.restored() == restored + 2, "the walking branch too");

        // Glider: crouching while falling with the module is the branch that adds 0.03 a tick
        ServerPlayer glider = Suits.wearing(h, EquipmentSlot.CHEST, Suits.CHESTPLATE, Suits.BATTERY, Suits.GLIDER);
        Suits.charge(glider, 1_000_000);
        IPlayerTickModule gliderModule = (IPlayerTickModule) Suits.module(glider, EquipmentSlot.CHEST, Suits.GLIDER);
        ItemStack chestplate = glider.getItemBySlot(EquipmentSlot.CHEST);
        glider.setOnGround(false);
        glider.setPose(Pose.CROUCHING);
        glider.setDeltaMovement(new Vec3(0, -0.5, 0));
        for (int tick = 0; tick < 20; tick++) {
            glider.setDeltaMovement(new Vec3(0, -0.5, 0));
            gliderModule.onPlayerTickActive(glider, chestplate);
        }
        h.assertTrue(FlyingSpeedKeeper.isVanilla(glider.getAbilities().getFlyingSpeed()), "after a second of gliding the flying speed is still 0.05 (Powersuits would have 0.65), found " + glider.getAbilities().getFlyingSpeed());
        h.assertTrue(FlyingSpeedKeeper.restored() == restored + 2 + 20, "every gliding tick had changed it and was undone: " + (FlyingSpeedKeeper.restored() - restored - 2));

        // Jump Assist: the jump key held (Numina's key-state capability on the player) is the branch that writes the speed
        ServerPlayer jumper = Suits.wearing(h, EquipmentSlot.LEGS, Suits.LEGGINGS, Suits.BATTERY, Suits.JUMP_ASSIST);
        Suits.charge(jumper, 1_000_000);
        IPlayerTickModule jumpAssist = (IPlayerTickModule) Suits.module(jumper, EquipmentSlot.LEGS, Suits.JUMP_ASSIST);
        boolean keyHeld = jumper.getCapability(NuminaCapabilities.PLAYER_KEYSTATES).map(k -> {
            k.setJumpKeyState(true);
            return true;
        }).orElse(false);
        jumpAssist.onPlayerTickActive(jumper, jumper.getItemBySlot(EquipmentSlot.LEGS));
        h.assertTrue(FlyingSpeedKeeper.isVanilla(jumper.getAbilities().getFlyingSpeed()), "after a jump assist tick the flying speed is still 0.05, found " + jumper.getAbilities().getFlyingSpeed());
        if (keyHeld) {
            h.assertTrue(FlyingSpeedKeeper.restored() == restored + 23, "the jump branch had changed it and was undone: " + (FlyingSpeedKeeper.restored() - restored - 22));
        }

        h.assertTrue(Fixes.active().stream().anyMatch(f -> f.id().equals(FlyingSpeedKeeper.ID)), "F-24 is recorded as applied once the module classes loaded: " + Fixes.active());
        h.succeed();
    }
}
