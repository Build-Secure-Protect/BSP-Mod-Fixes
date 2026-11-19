package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.voltaic.CurioShielding;
import com.mrgregles.bsp_mod_fixes.fix.voltaic.VoltaicGate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * MF-37: a curio with Mekanism radiation shielding shields against Voltaic's radiation (F-40). The arithmetic and the gate are
 * pure; with {@code -PwithHazmat} (the pack's Voltaic, Electrodynamics, Nuclear Science, Mekanism, Curios, Evolved Mekanism and
 * IgleeLib) a mock player wears the real Portable Hazmat Suit in its curio slot and is dosed through Voltaic's real recipient:
 * no radiation effect with the fix, the effect as shipped, the effect with the suit off or gone, Nuclear Science's hazmat armour
 * unchanged, the recorded dose unchanged. No class of those mods is named here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class VoltaicGameTests {
    private static final String SLOT = "portable_hazmat_suit";

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_curios_shielding_scales_the_dose_left_after_armour_and_the_voltaic_gate_holds(GameTestHelper h) {
        h.assertTrue(CurioShielding.clamp(0.25) == 0.25 && CurioShielding.clamp(-1) == 0 && CurioShielding.clamp(Double.NaN) == 0 && CurioShielding.clamp(Double.POSITIVE_INFINITY) == 0, "a capability's shielding is used as it is, a negative or non-finite one as 0");
        h.assertTrue(CurioShielding.sum(0, 0.25) == 0.25 && CurioShielding.sum(0.9, 0.25) == 1.0 && CurioShielding.sum(0.5, Double.NaN) == 0.5, "curios are summed and capped at 1, as Mekanism sums them");
        h.assertTrue(CurioShielding.scale(100, 1.0) == 0 && CurioShielding.scale(100, 1.5) == 0, "full shielding leaves nothing");
        double threePartials = CurioShielding.sum(CurioShielding.sum(CurioShielding.sum(0, 0.3), 0.6), 0.1); // 0.9999999999999999 in doubles
        h.assertTrue(threePartials < 1.0 && CurioShielding.scale(100, threePartials) == 0, "partial curios summing to 1 within floating point leave nothing either, as Voltaic judges its armour sum: " + threePartials);
        h.assertTrue(CurioShielding.scale(100, 0.9999999999999999) == 0 && CurioShielding.scale(100, 0.9999) > 0, "the tolerance is Voltaic's: a hair under 1 is full, 0.9999 is not");
        h.assertTrue(Math.abs(CurioShielding.scale(100, 0.25) - 75) < 1e-9, "a quarter of shielding leaves three quarters: " + CurioShielding.scale(100, 0.25));
        h.assertTrue(CurioShielding.scale(100, 0) == 100 && CurioShielding.scale(0, 1.0) == 0 && CurioShielding.scale(-5, 1.0) == -5, "no shielding, or nothing left, changes nothing");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.VOLTAIC_CURIO_RADIATION_SHIELDING.get() && CurioShielding.enabled(), "the switch is on by default");
        // the gate
        h.assertTrue(VoltaicGate.verdict(Optional.of(VoltaicGate.VERSION), true, true, true).applies(), "exactly " + VoltaicGate.VERSION + " with both APIs applies");
        h.assertTrue(VoltaicGate.verdict(Optional.empty(), true, true, true) == VoltaicGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(VoltaicGate.verdict(Optional.of("1.20.1-1.0.13"), true, true, true) == VoltaicGate.Verdict.OTHER_VERSION && VoltaicGate.verdict(Optional.of("1.20.1-1.2.3"), true, true, true) == VoltaicGate.Verdict.OTHER_VERSION, "other builds are skipped");
        h.assertTrue(VoltaicGate.verdict(Optional.of(VoltaicGate.VERSION), false, true, true) == VoltaicGate.Verdict.CHANGED_TARGET, "the build without the methods is skipped");
        h.assertTrue(VoltaicGate.verdict(Optional.of(VoltaicGate.VERSION), true, false, true) == VoltaicGate.Verdict.NO_MEKANISM, "without Mekanism's API: skipped, named");
        h.assertTrue(VoltaicGate.verdict(Optional.of(VoltaicGate.VERSION), true, true, false) == VoltaicGate.Verdict.NO_CURIOS, "without Curios' API: skipped, named");
        h.assertTrue(VoltaicGate.gates("VoltaicCurioShieldingMixin") && !VoltaicGate.gates("IronFurnacesListLoadMixin"), "the gate decides for its own mixin only");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(CurioShielding.ID) && f.targetMod().equals(VoltaicGate.TARGET)), "the registry knows F-40 as a Voltaic fix");
        if (!ModList.get().isLoaded(VoltaicGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(CurioShielding.ID) && skipped.get(CurioShielding.ID).equals(VoltaicGate.Verdict.ABSENT.reason()),
                    "without Voltaic the plugin skipped F-40 as absent: " + skipped);
        }
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void the_real_portable_hazmat_suit_shields_a_voltaic_dose_and_the_switch_restores_voltaics_own(GameTestHelper h) {
        ModList mods = ModList.get();
        if (!mods.isLoaded(VoltaicGate.MOD_ID) || !mods.isLoaded(VoltaicGate.MEKANISM_ID) || !mods.isLoaded(VoltaicGate.CURIOS_ID) || !mods.isLoaded("evolvedmekanism") || !mods.isLoaded("nuclearscience")) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: Voltaic, Mekanism, Curios, Evolved Mekanism and Nuclear Science are not all loaded, no dose is given (add -PwithHazmat)");
            h.succeed();
            return;
        }
        h.assertTrue(BSPModFixes.version(VoltaicGate.MOD_ID).equals(VoltaicGate.VERSION), "Voltaic " + VoltaicGate.VERSION + ", found " + BSPModFixes.version(VoltaicGate.MOD_ID));
        Object recipient = VoltaicTestBridge.recipient(); // loads the mixin's target
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(CurioShielding.ID));
        h.assertTrue(fixOn || Fixes.skipped().getOrDefault(CurioShielding.ID, "").equals(MixinFixPlugin.SKIP_REASON),
                "F-40 is applied or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        long shieldedBefore = CurioShielding.shieldedDoses();
        // 1. Nothing worn: the dose lands, either way.
        VoltaicTestBridge.dose(recipient, player, 100, 1);
        h.assertTrue(VoltaicTestBridge.irradiated(player), "a bare player is irradiated by 100 rads");
        h.assertTrue(Math.abs(VoltaicTestBridge.recorded(recipient, player) - 100) < 1e-9, "the recorded dose is the incoming dose: " + VoltaicTestBridge.recorded(recipient, player));
        player.removeAllEffects();
        // 2. The suit, switched on, in its curio slot: the owner's report.
        h.assertTrue(VoltaicTestBridge.equipCurio(player, SLOT, VoltaicTestBridge.suit(true)), "the player has the " + SLOT + " curio slot");
        h.assertTrue(VoltaicTestBridge.curioShielding(player) == 1.0, "the switched-on suit reads as full shielding: " + VoltaicTestBridge.curioShielding(player));
        VoltaicTestBridge.dose(recipient, player, 100, 1);
        if (fixOn) {
            h.assertTrue(!VoltaicTestBridge.irradiated(player), "with F-40 the suit shields a Voltaic dose");
            h.assertTrue(CurioShielding.shieldedDoses() == shieldedBefore + 1, "one shielded dose counted");
        } else {
            h.assertTrue(VoltaicTestBridge.irradiated(player), "as shipped Voltaic reads the armour slots only: the suit does nothing");
        }
        h.assertTrue(Math.abs(VoltaicTestBridge.recorded(recipient, player) - 100) < 1e-9, "the recorded dose is still the incoming dose, shielded or not: " + VoltaicTestBridge.recorded(recipient, player));
        player.removeAllEffects();
        // 3. The suit switched off: no shielding, either way.
        VoltaicTestBridge.equipCurio(player, SLOT, VoltaicTestBridge.suit(false));
        h.assertTrue(VoltaicTestBridge.curioShielding(player) == 0, "the switched-off suit reads as no shielding");
        VoltaicTestBridge.dose(recipient, player, 100, 1);
        h.assertTrue(VoltaicTestBridge.irradiated(player), "a switched-off suit does not shield");
        player.removeAllEffects();
        // 4. The suit removed.
        VoltaicTestBridge.equipCurio(player, SLOT, ItemStack.EMPTY);
        VoltaicTestBridge.dose(recipient, player, 100, 1);
        h.assertTrue(VoltaicTestBridge.irradiated(player), "no suit, no shielding");
        player.removeAllEffects();
        // 5. Nuclear Science's hazmat armour, no curio: Voltaic's own shielding, unchanged.
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(VoltaicTestBridge.item("nuclearscience:hazmathelmet")));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(VoltaicTestBridge.item("nuclearscience:hazmatplate")));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(VoltaicTestBridge.item("nuclearscience:hazmatlegs")));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(VoltaicTestBridge.item("nuclearscience:hazmatboots")));
        VoltaicTestBridge.dose(recipient, player, 100, 1);
        h.assertTrue(!VoltaicTestBridge.irradiated(player), "a full hazmat set shields as before, with or without the fix");
        h.assertTrue(CurioShielding.shieldedDoses() == (fixOn ? shieldedBefore + 1 : shieldedBefore), "no curio counted while armour did the shielding (nothing was left for the lookup)");
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.removeAllEffects();
        // 6. The switch off: Voltaic's own behaviour even with the mixin applied.
        if (fixOn) {
            VoltaicTestBridge.equipCurio(player, SLOT, VoltaicTestBridge.suit(true));
            FixesConfig.VOLTAIC_CURIO_RADIATION_SHIELDING.set(false);
            try {
                h.assertTrue(!CurioShielding.enabled(), "the switch reads off");
                VoltaicTestBridge.dose(recipient, player, 100, 1);
                h.assertTrue(VoltaicTestBridge.irradiated(player), "curioRadiationShielding = false hands the dose to Voltaic as shipped: the suit does nothing");
            } finally {
                FixesConfig.VOLTAIC_CURIO_RADIATION_SHIELDING.set(true);
            }
            player.removeAllEffects();
            VoltaicTestBridge.dose(recipient, player, 100, 1);
            h.assertTrue(!VoltaicTestBridge.irradiated(player), "back on, the suit shields again");
            h.assertTrue(CurioShielding.shieldedDoses() == shieldedBefore + 2, "two shielded doses in all");
        }
        h.succeed();
    }
}
