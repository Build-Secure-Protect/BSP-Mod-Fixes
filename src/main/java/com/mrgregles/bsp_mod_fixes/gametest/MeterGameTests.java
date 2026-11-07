package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.MeterGuard;
import com.mrgregles.bsp_mod_fixes.fix.proxy.ClientGate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * MF-24: Powersuits' HUD energy meter only with a Powersuits item (F-26). The overlay class is client-only, so the mixin is
 * skipped here; the rule and the equipped check run on dressed mock players.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class MeterGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void powersuits_meter_shows_only_with_a_modular_item_worn_or_held_and_the_fix_is_skipped_on_a_dedicated_server(GameTestHelper h) {
        h.assertTrue(MeterGuard.shouldDraw(true) && !MeterGuard.shouldDraw(false), "the rule: drawn with a modular item, not without");

        ServerPlayer bare = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        h.assertTrue(!MeterGuard.modularEquipped(bare), "nothing worn: no modular item");
        bare.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        h.assertTrue(!MeterGuard.modularEquipped(bare), "vanilla armor is not a modular item");

        ServerPlayer suited = Suits.wearing(h, EquipmentSlot.CHEST, Suits.CHESTPLATE, Suits.BATTERY);
        h.assertTrue(MeterGuard.modularEquipped(suited), "a Powersuits chestplate is");
        ServerPlayer fist = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        fist.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("powersuits", "powerfist"))));
        h.assertTrue(MeterGuard.modularEquipped(fist), "a held Power Fist is too (D52: worn or held)");

        Map<String, String> skipped = Fixes.skipped();
        h.assertTrue(skipped.containsKey(MeterGuard.ID) && skipped.get(MeterGuard.ID).equals(ClientGate.SERVER_REASON),
                "F-26 was skipped by the plugin with the server reason, got " + skipped);
        h.assertTrue(ClientGate.gates("PowersuitsMeterMixin"), "the client gate decides for the meter mixin");
        h.succeed();
    }
}
