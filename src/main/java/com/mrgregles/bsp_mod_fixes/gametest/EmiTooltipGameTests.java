package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.emi.AmountLine;
import com.mrgregles.bsp_mod_fixes.fix.emi.LiveLines;
import com.mrgregles.bsp_mod_fixes.fix.emi.TooltipBuilderFix;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

/**
 * F-12 and F-13 without client classes: the live line list over strings (a line "t:..." has plain text and renders upper
 * case, "r:..." is a rich line with no text, "x:..." is one EMI's own add drops and the view renders blank), the amount-line arithmetic and splice, and the
 * key-usage line. The mixins and the EMI-touching helper are checked in game (docs/TESTING.md MF-10).
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class EmiTooltipGameTests {
    private static LiveLines<String, String, String> lines(List<String> rendered, List<String> legacy) {
        return new LiveLines<>(rendered, legacy,
                l -> l.startsWith("x:") ? "" : l.substring(2).toUpperCase(),
                l -> l.startsWith("t:") ? Optional.of(l.substring(2)) : Optional.empty(),
                r -> "r:?" + r);
    }

    /** What EMI's own add does: renders into the rendered list (dropping what it cannot), keeps plain text, then tells the live list. */
    private static void emiAdd(LiveLines<String, String, String> live, List<String> rendered, List<String> legacy, String line) {
        if (!line.startsWith("x:")) {
            rendered.add(line.substring(2).toUpperCase());
            if (line.startsWith("t:")) {
                legacy.add(line.substring(2));
            }
        }
        live.recordAppended(line);
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void live_lines_mirror_every_add_and_write_removals_and_inserts_through(GameTestHelper h) {
        List<String> rendered = new ArrayList<>();
        List<String> legacy = new ArrayList<>();
        LiveLines<String, String, String> live = lines(rendered, legacy);
        emiAdd(live, rendered, legacy, "t:name");
        emiAdd(live, rendered, legacy, "r:image");
        emiAdd(live, rendered, legacy, "x:dropped");
        emiAdd(live, rendered, legacy, "t:amount");
        h.assertTrue(live.equals(List.of("t:name", "r:image", "t:amount")), "three lines recorded, the dropped one not: " + live);
        h.assertTrue(rendered.equals(List.of("NAME", "IMAGE", "AMOUNT")), "rendered as EMI made them: " + rendered);
        h.assertTrue(legacy.equals(List.of("name", "amount")), "plain text of the text lines only: " + legacy);

        // Tinkers' pattern: a ListIterator removes the amount line, then more lines are added
        ListIterator<String> it = live.listIterator();
        while (it.hasNext()) {
            if (it.next().equals("t:amount")) {
                it.remove();
            }
        }
        h.assertTrue(rendered.equals(List.of("NAME", "IMAGE")) && legacy.equals(List.of("name")), "the removal reached both EMI lists: " + rendered + " " + legacy);
        live.add("t:1 Ingot");
        live.add(1, "t:inserted");
        h.assertTrue(rendered.equals(List.of("NAME", "INSERTED", "IMAGE", "1 INGOT")), "adds render into place: " + rendered);
        h.assertTrue(legacy.equals(List.of("name", "inserted", "1 Ingot")), "and keep the plain text in order: " + legacy);
        live.add("x:unrenderable");
        h.assertTrue(live.size() == 5 && rendered.size() == 5 && rendered.get(4).isEmpty(), "a line with no renderer is kept as a blank line, so iterators stay honest");
        live.remove(4);

        // set: text to rich, rich to text
        live.set(1, "r:pic");
        h.assertTrue(rendered.get(1).equals("PIC") && legacy.equals(List.of("name", "1 Ingot")), "set text->rich drops its plain text: " + legacy);
        live.set(2, "t:line");
        h.assertTrue(rendered.get(2).equals("LINE") && legacy.equals(List.of("name", "line", "1 Ingot")), "set rich->text inserts its plain text in order: " + legacy);

        // something else touched EMI's list directly: the view pads with stand-ins and never drifts
        rendered.add("STRAY");
        h.assertTrue(live.size() == 5 && live.get(4).startsWith("r:?"), "a stray rendered line shows as an opaque stand-in: " + live);
        live.remove(4);
        h.assertTrue(rendered.size() == 4 && legacy.size() == 3, "removing the stand-in removes the stray line only");
        rendered.remove(0);
        h.assertTrue(live.size() == 3 && live.get(0).equals("r:pic"), "a line removed behind the view's back is trimmed: " + live);

        live.clear();
        h.assertTrue(live.isEmpty() && rendered.isEmpty() && legacy.isEmpty(), "clear empties all three");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void amount_probe_is_replaced_by_the_callbacks_lines_or_put_back_where_it_was(GameTestHelper h) {
        h.assertTrue(AmountLine.index(1, 1000, 3) == 1, "one text line, an amount: the amount line is at 1");
        h.assertTrue(AmountLine.index(2, 1000, 4) == 2, "two text lines: at 2");
        h.assertTrue(AmountLine.index(1, 1, 3) == -1, "amount 1: EMI adds no amount line");
        h.assertTrue(AmountLine.index(1, 1000, 1) == -1, "a tooltip too short to hold one: none");
        h.assertTrue(AmountLine.index(-1, 1000, 3) == -1, "nonsense: none");

        // Tinkers removes the probe and adds its units: the units take the amount line's place, before the mod name
        List<String> slotTooltip = new ArrayList<>(List.of("Molten Iron", "1,000mB", "Tinkers' Construct"));
        List<String> builderRendered = new ArrayList<>();
        List<String> builderLegacy = new ArrayList<>();
        LiveLines<String, String, String> live = lines(builderRendered, builderLegacy);
        int index = AmountLine.index(1, 1000, slotTooltip.size());
        String emiAmount = slotTooltip.remove(index);
        live.seed("t:" + AmountLine.JEI_KEY, emiAmount);
        h.assertTrue(builderRendered.equals(List.of("1,000mB")) && builderLegacy.equals(List.of(AmountLine.JEI_KEY)), "the probe renders as EMI's own amount line");
        ListIterator<String> it = live.listIterator();
        while (it.hasNext()) {
            if (it.next().endsWith(AmountLine.JEI_KEY)) {
                it.remove();
            }
        }
        emiAdd(live, builderRendered, builderLegacy, "t:1 Ingot");
        AmountLine.splice(slotTooltip, index, builderRendered);
        live.clear();
        h.assertTrue(slotTooltip.equals(List.of("Molten Iron", "1 INGOT", "Tinkers' Construct")), "JEI's result: " + slotTooltip);
        h.assertTrue(builderRendered.isEmpty(), "the builder is emptied so EMI's own append adds nothing");

        // another mod's callback leaves the probe alone: EMI's line goes back where it was, the callback's line after it
        slotTooltip = new ArrayList<>(List.of("Water", "1,000mB", "Minecraft"));
        builderRendered.clear();
        builderLegacy.clear();
        live = lines(builderRendered, builderLegacy);
        emiAmount = slotTooltip.remove(1);
        live.seed("t:" + AmountLine.JEI_KEY, emiAmount);
        emiAdd(live, builderRendered, builderLegacy, "t:Wet");
        AmountLine.splice(slotTooltip, 1, builderRendered);
        h.assertTrue(slotTooltip.equals(List.of("Water", "1,000mB", "WET", "Minecraft")), "untouched probe: " + slotTooltip);

        // no amount line: the callback's lines are appended as EMI does
        slotTooltip = new ArrayList<>(List.of("Water", "Minecraft"));
        AmountLine.splice(slotTooltip, -1, List.of("WET"));
        h.assertTrue(slotTooltip.equals(List.of("Water", "Minecraft", "WET")), "no amount line: appended: " + slotTooltip);
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void key_usage_line_formats_the_key_name_in_bold_on_an_italic_gray_line(GameTestHelper h) {
        MutableComponent line = TooltipBuilderFix.keyUsage("example.key.usage", Component.literal("R"));
        h.assertTrue(line.getContents() instanceof TranslatableContents tc && tc.getKey().equals("example.key.usage"), "translatable with the given key");
        Object arg = ((TranslatableContents) line.getContents()).getArgs()[0];
        h.assertTrue(arg instanceof Component c && c.getStyle().isBold() && c.getString().equals("R"), "the key name in bold");
        h.assertTrue(line.getStyle().isItalic() && line.getStyle().getColor() != null
                && line.getStyle().getColor().getValue() == ChatFormatting.GRAY.getColor(), "italic and gray, as JEI draws it");
        h.succeed();
    }
}
