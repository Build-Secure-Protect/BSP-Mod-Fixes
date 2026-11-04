package com.mrgregles.bsp_mod_fixes.fix.emi;

import java.util.List;

/**
 * F-13 (EMI 1.1.24+1.20.1+forge): the amount line of a fluid under a JEI rich tooltip callback. JEI's fluid tooltip carries
 * its amount as a line with the translation key {@code jei.tooltip.liquid.amount}; Tinkers' Construct's callback removes that
 * line and adds Mantle's unit lines ("1 Ingot", "9 Nuggets"). EMI gives the callback an empty builder and keeps its own
 * amount line ("1,000mB") in the slot tooltip, so under EMI both showed, Tinkers' after the mod name. The mixin offers EMI's
 * own amount line to the callback under JEI's key: removed, it is gone; left alone, it goes back where it was; and the
 * callback's lines take the amount line's place, before the mod name, as in JEI. Pure arithmetic here, the rest in the mixin.
 */
public final class AmountLine {
    public static final String ID = "F-13";
    public static final String WHAT = "a fluid's amount line is offered to a JEI rich tooltip callback under JEI's key, so Tinkers' unit lines replace it";
    public static final String TARGET = EmiGate.TARGET;
    /** JEI's translation key for the amount line, the one Tinkers' Construct looks for. */
    public static final String JEI_KEY = "jei.tooltip.liquid.amount";

    private AmountLine() {}

    /**
     * Where EMI's amount line sits in a fluid stack's tooltip: after the fluid's own text lines, only when the amount is
     * above one (EMI adds no line otherwise) and the tooltip is long enough. -1 when there is none.
     */
    public static int index(int fluidTextLines, long amount, int tooltipSize) {
        if (amount <= 1 || fluidTextLines < 0 || fluidTextLines >= tooltipSize) {
            return -1;
        }
        return fluidTextLines;
    }

    /** Puts the callback's surviving lines into the tooltip at the amount line's old index (appends when there was none). */
    public static <C> void splice(List<C> tooltip, int index, List<C> survivors) {
        List<C> copy = List.copyOf(survivors);
        if (index < 0 || index > tooltip.size()) {
            tooltip.addAll(copy);
        } else {
            tooltip.addAll(index, copy);
        }
    }
}
