package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.LivingEventFilter;
import com.mrgregles.bsp_mod_fixes.fix.TickPhaseFilter;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * MF-2, the two tick fixes. The events are posted on the Forge bus by hand, so the real Numina and Powersuits handlers (with
 * this mod's mixins applied) receive them, and each handler's visible side effect says whether it ran.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class TickGameTests {
    /** Numina's handler clears the fire of a player wearing a modular item whose heat is under 95 % of its maximum. */
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void numina_ticks_the_suit_on_the_start_phase_only(GameTestHelper h) {
        h.assertTrue(TickPhaseFilter.runs(TickEvent.Phase.START) && !TickPhaseFilter.runs(TickEvent.Phase.END), "START runs, END does not");
        ServerPlayer player = SecurityGameTests.wearingChestplate(h, GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(12, 2, 12)));
        player.setRemainingFireTicks(200);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        h.assertTrue(player.getRemainingFireTicks() == 200, "the END phase left the fire alone, ticks " + player.getRemainingFireTicks());
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
        h.assertTrue(player.getRemainingFireTicks() <= 0, "the START phase ran Numina's handler, which cleared the fire, ticks " + player.getRemainingFireTicks());
        h.succeed();
    }

    /** Powersuits' handler lowers a player's fall distance to the one their downward speed accounts for (zero when still). */
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void powersuits_updates_the_player_on_the_living_tick_only(GameTestHelper h) {
        ServerPlayer player = SecurityGameTests.wearingChestplate(h, GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(new Vec3(12, 2, 12)));
        player.setDeltaMovement(Vec3.ZERO);
        LivingEvent other = new LivingEvent.LivingVisibilityEvent(player, null, 1.0);
        LivingEvent tick = new LivingEvent.LivingTickEvent(player);
        h.assertTrue(LivingEventFilter.runs(tick) && !LivingEventFilter.runs(other), "the tick runs, another living event does not");
        player.fallDistance = 50.0f;
        MinecraftForge.EVENT_BUS.post(other);
        h.assertTrue(player.fallDistance == 50.0f, "a visibility event left the fall distance alone, found " + player.fallDistance);
        MinecraftForge.EVENT_BUS.post(tick);
        h.assertTrue(player.fallDistance == 0.0f, "the living tick ran Powersuits' handler, which clamped the fall distance of a still player to 0, found " + player.fallDistance);
        h.succeed();
    }
}
