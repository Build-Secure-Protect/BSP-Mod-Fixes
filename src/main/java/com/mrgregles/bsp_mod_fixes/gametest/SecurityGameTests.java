package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.TweakClamp;
import lehjr.numina.common.capabilities.inventory.modularitem.IModularItem;
import lehjr.numina.common.network.packets.serverbound.TweakRequestDoublePacketServerBound;
import lehjr.numina.common.tags.TagUtils;
import lehjr.powersuits.common.network.packets.serverbound.CreativeInstallPacketServerBound;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * MF-1, the two security fixes. The packets go through the real Powersuits and Numina handlers (with this mod's mixins applied)
 * from a mock player's connection; the handlers queue their work on the server thread, so the checks run two ticks later.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class SecurityGameTests {
    static final ResourceLocation CHESTPLATE = Suits.CHESTPLATE;
    static final ResourceLocation ENERGY_SHIELD = Suits.ENERGY_SHIELD;
    static final String FIELD_STRENGTH = "fieldStrength";

    // F-1: creative install -------------------------------------------------------------------------------------------

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void creative_install_is_refused_for_a_survival_player(GameTestHelper h) {
        ServerPlayer player = wearingChestplate(h, GameType.SURVIVAL);
        NetworkEvent.Context ctx = Packets.from(player);
        CreativeInstallPacketServerBound.Handler.handle(new CreativeInstallPacketServerBound(EquipmentSlot.CHEST, ENERGY_SHIELD), () -> ctx);
        h.assertTrue(ctx.getPacketHandled(), "the packet is marked handled");
        h.runAfterDelay(2, () -> {
            h.assertTrue(installed(player).isEmpty(), "no module was installed, found " + installed(player));
            h.succeed();
        });
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void creative_install_still_works_for_a_creative_player(GameTestHelper h) {
        ServerPlayer player = wearingChestplate(h, GameType.CREATIVE);
        NetworkEvent.Context ctx = Packets.from(player);
        CreativeInstallPacketServerBound.Handler.handle(new CreativeInstallPacketServerBound(EquipmentSlot.CHEST, ENERGY_SHIELD), () -> ctx);
        h.assertTrue(ctx.getPacketHandled(), "the packet is marked handled");
        h.runAfterDelay(2, () -> {
            h.assertTrue(installed(player).equals(List.of(ENERGY_SHIELD)), "the energy shield was installed, found " + installed(player));
            h.succeed();
        });
    }

    // F-2: tweak clamp ----------------------------------------------------------------------------------------------------

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void tweak_clamp_maps_out_of_range_and_broken_values_into_0_to_1(GameTestHelper h) {
        h.assertTrue(TweakClamp.clamp(5.0) == 1.0, "5.0 becomes 1.0");
        h.assertTrue(TweakClamp.clamp(-1.0) == 0.0, "-1.0 becomes 0.0");
        h.assertTrue(TweakClamp.clamp(Double.NaN) == 0.0, "NaN becomes 0.0");
        h.assertTrue(TweakClamp.clamp(Double.POSITIVE_INFINITY) == 0.0, "+infinity becomes 0.0");
        h.assertTrue(TweakClamp.clamp(Double.NEGATIVE_INFINITY) == 0.0, "-infinity becomes 0.0");
        h.assertTrue(TweakClamp.clamp(0.0) == 0.0 && TweakClamp.clamp(0.5) == 0.5 && TweakClamp.clamp(1.0) == 1.0, "0, 0.5 and 1 are kept");
        h.assertTrue(TweakClamp.inRange(0.0) && TweakClamp.inRange(1.0) && TweakClamp.inRange(0.25), "0, 1 and 0.25 are in range");
        h.assertFalse(TweakClamp.inRange(1.0000001) || TweakClamp.inRange(-0.0000001) || TweakClamp.inRange(Double.NaN), "just outside and NaN are not");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void tweak_request_of_5_is_stored_as_1_and_a_request_of_a_half_is_kept(GameTestHelper h) {
        ServerPlayer player = wearingChestplate(h, GameType.SURVIVAL);
        IModularItem chest = modular(player);
        h.assertTrue(chest.insertItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(ENERGY_SHIELD)), false).isEmpty(), "the energy shield fits slot 0");
        sendTweak(player, 5.0);
        h.runAfterDelay(2, () -> {
            h.assertTrue(tweak(player) == 1.0, "a request of 5.0 is stored as 1.0, found " + tweak(player));
            sendTweak(player, 0.5);
            h.runAfterDelay(2, () -> {
                h.assertTrue(tweak(player) == 0.5, "a request of 0.5 is stored as 0.5, found " + tweak(player));
                sendTweak(player, Double.NaN);
                h.runAfterDelay(2, () -> {
                    h.assertTrue(tweak(player) == 0.0, "a request of NaN is stored as 0 (the tag is removed), found " + tweak(player));
                    h.succeed();
                });
            });
        });
    }

    // helpers ------------------------------------------------------------------------------------------------------------

    static ServerPlayer wearingChestplate(GameTestHelper h, GameType mode) {
        ServerPlayer player = MockPlayers.serverPlayer(h, mode);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ForgeRegistries.ITEMS.getValue(CHESTPLATE)));
        return player;
    }

    static IModularItem modular(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .filter(IModularItem.class::isInstance).map(IModularItem.class::cast)
                .orElseThrow(() -> new AssertionError("the chestplate has no modular item capability"));
    }

    static List<ResourceLocation> installed(ServerPlayer player) {
        return modular(player).getInstalledModuleNames();
    }

    static void sendTweak(ServerPlayer player, double value) {
        NetworkEvent.Context ctx = Packets.from(player);
        TweakRequestDoublePacketServerBound.Handler.handle(
                new TweakRequestDoublePacketServerBound(EquipmentSlot.CHEST, ENERGY_SHIELD, FIELD_STRENGTH, value), () -> ctx);
    }

    static double tweak(ServerPlayer player) {
        IModularItem chest = modular(player);
        int slot = chest.findInstalledModule(ENERGY_SHIELD);
        if (slot < 0) {
            throw new AssertionError("the energy shield is not installed");
        }
        return TagUtils.getModuleDoubleOrZero(chest.getStackInSlot(slot), FIELD_STRENGTH);
    }
}
