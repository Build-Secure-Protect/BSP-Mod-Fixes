package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.emi.CycleValues;
import com.mrgregles.bsp_mod_fixes.fix.emi.ExtrasFix;
import com.mrgregles.bsp_mod_fixes.fix.emi.TextWidgets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** F-14 and F-15 without client classes: the cycle button's values, JEI's text layout arithmetic, and the registry. */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class EmiExtrasGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void cycle_button_values_step_round_and_start_at_the_initial_value(GameTestHelper h) {
        CycleValues<String> c = new CycleValues<>(List.of("a", "b", "c"), "b");
        h.assertTrue(c.value().equals("b"), "starts at the initial value");
        h.assertTrue(c.next().equals("c") && c.next().equals("a") && c.next().equals("b"), "steps round the list");
        c.set("c");
        h.assertTrue(c.value().equals("c"), "set selects a value");
        c.set("zzz");
        h.assertTrue(c.value().equals("c"), "a value not in the list is ignored");
        CycleValues<String> unknownStart = new CycleValues<>(List.of("a", "b"), "zzz");
        h.assertTrue(unknownStart.value().equals("a"), "an initial value not in the list starts at the first");
        CycleValues<String> empty = new CycleValues<>(List.of(), "a");
        h.assertTrue(empty.value() == null && empty.next() == null && empty.size() == 0, "an empty list never throws");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void text_layout_measures_lines_as_jei_does(GameTestHelper h) {
        h.assertTrue(TextWidgets.lineHeight(9, 2) == 11, "a line is the font height plus the spacing");
        h.assertTrue(TextWidgets.blockHeight(1, 9, 2) == 8, "one line measures 8 for centring, as JEI");
        h.assertTrue(TextWidgets.blockHeight(3, 9, 2) == 30, "three lines: 33 - 2 - 1");
        h.assertTrue(TextWidgets.maxLines(0, 9, 2) == Integer.MAX_VALUE, "no height: no limit");
        h.assertTrue(TextWidgets.maxLines(20, 9, 2) == 2, "20 high: two lines of 11 (the last needs no spacing)");
        h.assertTrue(TextWidgets.maxLines(5, 9, 2) == 1, "too short for one line: still one, so a title shows");
        h.assertTrue(TextWidgets.DEFAULT_LINE_SPACING == 2, "JEI's default spacing");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void extras_and_text_fixes_are_known_and_skipped_on_a_dedicated_server(GameTestHelper h) {
        h.assertTrue(Fixes.isEmiMixin("EmiRecipeExtrasMixin") && Fixes.isEmiMixin("EmiPlaceableMixin") && Fixes.isEmiMixin("EmiTextWidgetMixin"), "all three are EMI mixins");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(ExtrasFix.ID)) && Fixes.known().stream().anyMatch(f -> f.id().equals(TextWidgets.ID)), "F-14 and F-15 are known");
        h.assertTrue(Fixes.active().stream().noneMatch(f -> f.id().equals(ExtrasFix.ID) || f.id().equals(TextWidgets.ID)), "neither is active on a server");
        h.assertTrue(Fixes.skipped().containsKey(ExtrasFix.ID) && Fixes.skipped().containsKey(TextWidgets.ID), "both skipped on a dedicated server: " + Fixes.skipped());
        h.succeed();
    }
}
