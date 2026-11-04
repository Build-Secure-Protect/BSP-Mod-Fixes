package com.mrgregles.bsp_mod_fixes.fix.emi;

/**
 * F-15 (EMI 1.1.24+1.20.1+forge): {@code JemiRecipeExtrasBuilder.addText} returns a {@code JemiTextWidget} that EMI never
 * adds to its widget list, so JEI text widgets (titles, temperatures, cooling times in Tinkers' Construct's categories) are
 * never drawn under EMI; and {@code JemiTextWidget} lacks the four {@code setTooltip} methods JEI 15.56 made abstract on
 * {@code ITextWidget}. The mixins register the text for drawing (wrapped to its width, aligned as JEI aligns it, with JEI's
 * default line spacing) and add the tooltip methods. The layout arithmetic is here, pure, as JEI computes it.
 */
public final class TextWidgets {
    public static final String ID = "F-15";
    public static final String WHAT = "JEI text widgets are drawn in EMI (EMI returned them without ever drawing them) and take tooltips";
    public static final String TARGET = EmiGate.TARGET;
    /** JEI's default spacing between text lines; EMI's widget starts at 0. */
    public static final int DEFAULT_LINE_SPACING = 2;
    /** JEI draws text without a shadow unless the plugin asks for one; EMI's widget starts with one. */
    public static final boolean DEFAULT_SHADOW = false;

    private TextWidgets() {}

    /** The height one line takes: the font's line height plus the spacing. */
    public static int lineHeight(int fontLineHeight, int spacing) {
        return fontLineHeight + spacing;
    }

    /** How many lines fit the area; 0 or less means no limit. Never under one, so a title in a short area still shows. */
    public static int maxLines(int areaHeight, int fontLineHeight, int spacing) {
        if (areaHeight <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (areaHeight + spacing) / lineHeight(fontLineHeight, spacing));
    }

    /** The height of a block of lines as JEI measures it for vertical alignment. */
    public static int blockHeight(int lines, int fontLineHeight, int spacing) {
        return lineHeight(fontLineHeight, spacing) * lines - spacing - 1;
    }
}
