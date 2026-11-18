package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesClientConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.emi.TransferSlots;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F-37 without client classes: the clamp arithmetic over the five shapes that crash the pack's EMI (Mekanism's Laser
 * Tractor Beam and Quark's dirty glass with six entries, EnderStorage's three recolour recipes with one), a nine-entry
 * recipe left alone, the switch's default, the once-per-recipe log, and the registry's view of the mixin. The redirect
 * itself is exercised on a dev client by {@code JemiSlotsProbe} and in game (docs/TESTING.md MF-34).
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class EmiTransferGameTests {
    /** What EMI asks for: {@code subList(0, max(9, size) - blankedSlots)}. */
    private static int emiEnd(int size, int blankedSlots) {
        return Math.max(9, size) - blankedSlots;
    }

    private static List<String> inputs(int size) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add("in" + i);
        }
        return list;
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void transfer_slots_clamp_emis_bounds_to_the_inputs_there_are(GameTestHelper h) {
        TransferSlots.forgetWarned();
        // mekanism:laser_tractor_beam: six entries, one blanked slot, EMI asks for 0..8
        h.assertTrue(emiEnd(6, 1) == 8, "the Laser Tractor Beam asks for toIndex 8");
        h.assertFalse(TransferSlots.fits(0, 8, 6), "8 does not fit a six-entry list");
        h.assertTrue(TransferSlots.clampedTo(0, 8, 6) == 6 && TransferSlots.clampedFrom(0, 6) == 0, "clamped to 0..6");
        h.assertTrue(TransferSlots.subList(inputs(6), 0, 8, "mekanism:laser_tractor_beam").equals(inputs(6)), "all six entries are laid out");
        // enderstorage:recolour_ender_{tank,chest,pouch}: one entry, one blanked slot, EMI asks for 0..8
        h.assertTrue(emiEnd(1, 1) == 8, "a recolour recipe asks for toIndex 8");
        h.assertTrue(TransferSlots.subList(inputs(1), 0, 8, "enderstorage:recolour_ender_tank").equals(inputs(1)), "the one entry is laid out");
        // quark:tweaks/crafting/dirty_glass: six entries, no blanked slot, EMI asks for 0..9
        h.assertTrue(emiEnd(6, 0) == 9, "dirty glass asks for toIndex 9");
        h.assertTrue(TransferSlots.subList(inputs(6), 0, 9, "quark:tweaks/crafting/dirty_glass").equals(inputs(6)), "all six entries are laid out");
        // EMI's own shaped recipes carry nine entries: the bounds fit and nothing changes
        for (int blanked : new int[]{0, 1, 3}) {
            int end = emiEnd(9, blanked);
            h.assertTrue(TransferSlots.fits(0, end, 9), "0.." + end + " fits a nine-entry list");
            h.assertTrue(TransferSlots.subList(inputs(9), 0, end, "minecraft:nine").equals(inputs(9).subList(0, end)), "a nine-entry list is cut as EMI asked, " + end);
        }
        // a longer list (a mod's 4x4 or 5x5 recipe shown as crafting) is cut as EMI asked too
        h.assertTrue(TransferSlots.fits(0, 15, 16) && TransferSlots.subList(inputs(16), 0, 15, "x:big").size() == 15, "a sixteen-entry list is cut at 15 as asked");
        // degenerate bounds never throw
        h.assertTrue(TransferSlots.clampedFrom(-2, 4) == 0 && TransferSlots.clampedFrom(7, 4) == 4, "from is held to 0..size");
        h.assertTrue(TransferSlots.clampedTo(3, 1, 4) == 3, "to is never before from");
        h.assertTrue(TransferSlots.clampedTo(0, 20, 0) == 0 && TransferSlots.subList(inputs(0), 0, 9, "x:empty").isEmpty(), "an empty list gives an empty view");
        h.assertTrue(TransferSlots.subList(inputs(4), 7, 1, "x:odd").isEmpty(), "crossed bounds give an empty view, not an exception");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void transfer_slots_log_each_recipe_once_and_are_switched_by_the_client_config(GameTestHelper h) {
        TransferSlots.forgetWarned();
        h.assertTrue(TransferSlots.firstClampOf("a:one"), "the first clamp of a recipe is logged");
        h.assertFalse(TransferSlots.firstClampOf("a:one"), "the second is not");
        h.assertTrue(TransferSlots.firstClampOf("a:two"), "another recipe is logged once too");
        TransferSlots.forgetWarned();
        h.assertTrue(TransferSlots.firstClampOf("a:one"), "forgotten, it is logged again");
        h.assertTrue(TransferSlots.enabled() && FixesClientConfig.clampJeiTransferSlots(), "the switch reads on (its default, and before a client config exists)");
        h.assertTrue(FixesClientConfig.CLAMP_JEI_TRANSFER_SLOTS.getDefault(), "the default is on");
        h.assertTrue(FixesClientConfig.CLAMP_JEI_TRANSFER_SLOTS.getPath().equals(List.of("emi", "clampJeiTransferSlots")), "the key is [emi] clampJeiTransferSlots, got " + FixesClientConfig.CLAMP_JEI_TRANSFER_SLOTS.getPath());
        h.assertTrue(Fixes.isEmiMixin("EmiJemiSlotsViewMixin"), "the F-37 mixin is an EMI mixin, gated by EmiGate");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(TransferSlots.ID) && f.targetMod().equals("EMI 1.1.24+1.20.1+forge")), "F-37 is known to the build and targets the pinned EMI");
        h.assertTrue(Fixes.active().stream().noneMatch(f -> f.id().equals(TransferSlots.ID)), "F-37 is not active on a dedicated server");
        Map<String, String> skipped = Fixes.skipped();
        String reason = skipped.get(TransferSlots.ID);
        h.assertTrue(reason != null && (reason.contains("dedicated server") || reason.contains("-PskipFixes")), "F-37 was skipped by the plugin with the server reason (or switched off for the run), got " + reason);
        h.succeed();
    }
}
