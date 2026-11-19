package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.evolvedmekanism.ElytraCurioRegistration;
import com.mrgregles.bsp_mod_fixes.fix.evolvedmekanism.EvolvedMekanismGate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Map;
import java.util.Optional;

/**
 * MF-39: Evolved Mekanism does not register an elytra Elytra Slot already gives a curio capability (F-42). The decision and the
 * gate are pure; with {@code -PwithElytra} (the pack's Mekanism, Curios, Evolved Mekanism, IgleeLib, Elytra Slot and Caelus,
 * with the Voltaic family the Hazmat set brings) the real HDPE Reinforced Elytra's {@code curios:item} lookup resolves to Elytra
 * Slot's curio with the fix and throws Forge's duplicate-key refusal as shipped; a vanilla elytra's and a canteen's lookups are
 * the same either way. No class of those mods is named here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class EvolvedMekanismGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void an_elytra_registration_is_skipped_only_beside_elytra_slot_and_the_evolved_mekanism_gate_holds(GameTestHelper h) {
        h.assertTrue(ElytraCurioRegistration.skip(true, true), "Elytra Slot loaded and an elytra: skipped");
        h.assertTrue(!ElytraCurioRegistration.skip(true, false), "Elytra Slot loaded, not an elytra: registered");
        h.assertTrue(!ElytraCurioRegistration.skip(false, true) && !ElytraCurioRegistration.skip(false, false), "without Elytra Slot everything registers");
        // the gate
        h.assertTrue(EvolvedMekanismGate.verdict(Optional.of(EvolvedMekanismGate.VERSION), true, true, true).applies(), "exactly " + EvolvedMekanismGate.VERSION + " with Elytra Slot and Curios applies");
        h.assertTrue(EvolvedMekanismGate.verdict(Optional.empty(), true, true, true) == EvolvedMekanismGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(EvolvedMekanismGate.verdict(Optional.of("1.3.0-fix2"), true, true, true) == EvolvedMekanismGate.Verdict.OTHER_VERSION && EvolvedMekanismGate.verdict(Optional.of("1.3.1"), true, true, true) == EvolvedMekanismGate.Verdict.OTHER_VERSION, "other builds are skipped");
        h.assertTrue(EvolvedMekanismGate.verdict(Optional.of(EvolvedMekanismGate.VERSION), false, true, true) == EvolvedMekanismGate.Verdict.CHANGED_TARGET, "the build without the lambda is skipped");
        h.assertTrue(EvolvedMekanismGate.verdict(Optional.of(EvolvedMekanismGate.VERSION), true, false, true) == EvolvedMekanismGate.Verdict.NO_ELYTRA_SLOT, "without Elytra Slot: skipped, named (nothing to fix)");
        h.assertTrue(EvolvedMekanismGate.verdict(Optional.of(EvolvedMekanismGate.VERSION), true, true, false) == EvolvedMekanismGate.Verdict.NO_CURIOS, "without Curios' call: skipped, named");
        h.assertTrue(EvolvedMekanismGate.gates("EvolvedMekanismElytraCurioMixin") && !EvolvedMekanismGate.gates("VoltaicCurioShieldingMixin"), "the gate decides for its own mixin only");
        ClassNode node = new ClassNode();
        node.fields.add(new FieldNode(0x19, EvolvedMekanismGate.ELYTRA_SLOT_FIELD, EvolvedMekanismGate.ELYTRA_SLOT_FIELD_DESC, null, null));
        h.assertTrue(EvolvedMekanismGate.hasField(node, EvolvedMekanismGate.ELYTRA_SLOT_FIELD, EvolvedMekanismGate.ELYTRA_SLOT_FIELD_DESC), "the field check finds the predicate");
        h.assertTrue(!EvolvedMekanismGate.hasField(node, "PROVIDERS", "Ljava/util/List;") && !EvolvedMekanismGate.hasField(node, EvolvedMekanismGate.ELYTRA_SLOT_FIELD, "Ljava/util/List;") && !EvolvedMekanismGate.hasField(null, "x", "I"), "another name or descriptor, or no class, is not found");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(ElytraCurioRegistration.ID) && f.targetMod().equals(EvolvedMekanismGate.TARGET)), "the registry knows F-42 as an Evolved Mekanism fix");
        if (!ModList.get().isLoaded(EvolvedMekanismGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(ElytraCurioRegistration.ID) && skipped.get(ElytraCurioRegistration.ID).equals(EvolvedMekanismGate.Verdict.ABSENT.reason()),
                    "without Evolved Mekanism the plugin skipped F-42 as absent: " + skipped);
            h.assertTrue(ElytraCurioRegistration.skippedItems() == 0, "nothing was skipped");
        }
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void the_real_hdpe_elytra_has_elytra_slots_curio_with_the_fix_and_two_keys_as_shipped(GameTestHelper h) {
        ModList mods = ModList.get();
        if (!mods.isLoaded(EvolvedMekanismGate.MOD_ID) || !mods.isLoaded(EvolvedMekanismGate.ELYTRA_SLOT_ID) || !mods.isLoaded(EvolvedMekanismGate.CURIOS_ID) || !mods.isLoaded("mekanism")) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: Evolved Mekanism, Elytra Slot, Curios and Mekanism are not all loaded, no capability is looked up (add -PwithElytra)");
            h.succeed();
            return;
        }
        h.assertTrue(BSPModFixes.version(EvolvedMekanismGate.MOD_ID).equals(EvolvedMekanismGate.VERSION), "Evolved Mekanism " + EvolvedMekanismGate.VERSION + ", found " + BSPModFixes.version(EvolvedMekanismGate.MOD_ID));
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(ElytraCurioRegistration.ID));
        h.assertTrue(fixOn || Fixes.skipped().getOrDefault(ElytraCurioRegistration.ID, "").equals(MixinFixPlugin.SKIP_REASON),
                "F-42 is applied or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        ItemStack hdpe = new ItemStack(EvolvedMekanismTestBridge.item("mekanism:hdpe_elytra"));
        ItemStack canteen = new ItemStack(EvolvedMekanismTestBridge.item("mekanism:canteen"));
        h.assertTrue(EvolvedMekanismTestBridge.isElytra(hdpe.getItem()) && EvolvedMekanismTestBridge.isElytra(Items.ELYTRA) && !EvolvedMekanismTestBridge.isElytra(canteen.getItem()),
                "Elytra Slot's predicate takes the HDPE elytra and vanilla's, not the canteen");
        // 1. The HDPE Reinforced Elytra: the owner's crash.
        EvolvedMekanismTestBridge.Lookup lookup = EvolvedMekanismTestBridge.curioOf(hdpe);
        if (fixOn) {
            h.assertTrue(lookup.error() == null, "with F-42 the HDPE elytra's capability lookup does not throw: " + lookup.error());
            h.assertTrue(EvolvedMekanismTestBridge.ELYTRA_SLOT_CURIO.equals(lookup.curioClass()), "with F-42 the HDPE elytra has Elytra Slot's curio, like every elytra: " + lookup.curioClass());
            h.assertTrue(ElytraCurioRegistration.skippedItems() == 1, "one registration skipped at setup, the HDPE elytra's: " + ElytraCurioRegistration.skippedItems());
        } else {
            h.assertTrue(lookup.error() != null && lookup.error().contains("Duplicate Capability Key: curios:item"), "as shipped the lookup throws Forge's duplicate-key refusal: " + lookup);
            h.assertTrue(ElytraCurioRegistration.skippedItems() == 0, "nothing skipped as shipped");
        }
        // 2. A vanilla elytra: Elytra Slot's curio, either way (Evolved Mekanism registers none).
        lookup = EvolvedMekanismTestBridge.curioOf(new ItemStack(Items.ELYTRA));
        h.assertTrue(lookup.error() == null && EvolvedMekanismTestBridge.ELYTRA_SLOT_CURIO.equals(lookup.curioClass()), "a vanilla elytra has Elytra Slot's curio: " + lookup);
        // 3. A canteen: Evolved Mekanism's registration, untouched, either way.
        lookup = EvolvedMekanismTestBridge.curioOf(canteen);
        h.assertTrue(lookup.error() == null && EvolvedMekanismTestBridge.REGISTERED_CURIO.equals(lookup.curioClass()), "the canteen keeps Evolved Mekanism's curio: " + lookup);
        h.succeed();
    }
}
