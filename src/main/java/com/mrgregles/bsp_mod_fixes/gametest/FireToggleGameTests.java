package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.FireImmunityToggle;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** MF-7: the fire-immunity toggle (F-10), through Numina's real tick handler. */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class FireToggleGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void suit_clears_fire_only_while_the_config_says_so(GameTestHelper h) {
        h.assertTrue(FixesConfig.SPEC.isLoaded(), "the server config is loaded on the test server");
        boolean saved = FixesConfig.SUIT_CLEARS_FIRE.get();
        try {
            ServerPlayer player = Suits.wearing(h, EquipmentSlot.CHEST, Suits.CHESTPLATE);
            FixesConfig.SUIT_CLEARS_FIRE.set(false);
            h.assertFalse(FireImmunityToggle.clearsFire(), "the toggle reads false");
            player.setRemainingFireTicks(200);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
            h.assertTrue(player.getRemainingFireTicks() == 200, "with the toggle off the suit tick left the fire burning, ticks " + player.getRemainingFireTicks());

            FixesConfig.SUIT_CLEARS_FIRE.set(true);
            h.assertTrue(FireImmunityToggle.clearsFire(), "the toggle reads true");
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
            h.assertTrue(player.getRemainingFireTicks() <= 0, "with the toggle on the suit tick put the fire out (Powersuits' behaviour), ticks " + player.getRemainingFireTicks());
        } finally {
            FixesConfig.SUIT_CLEARS_FIRE.set(saved);
        }
        h.succeed();
    }
}
