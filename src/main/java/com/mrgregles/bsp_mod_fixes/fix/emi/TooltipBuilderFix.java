package com.mrgregles.bsp_mod_fixes.fix.emi;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * F-12 (EMI 1.1.24+1.20.1+forge): EMI's {@code JemiTooltipBuilder} implements JEI's {@code ITooltipBuilder} as it was in
 * JEI 15.20, which EMI compiled against. JEI 15.39 to 15.52 added four abstract methods ({@code clear}, {@code
 * clearIngredient}, {@code getLines}, {@code addKeyUsageComponent}) that EMI never implemented, so a JEI plugin calling one
 * of them under EMI throws {@code AbstractMethodError}; Tinkers' Construct calls {@code getLines()} for every fluid slot and
 * EMI then shows the whole recipe as "Error Rendering". The mixin adds the four: {@code getLines()} is a {@link LiveLines}
 * view over EMI's own lists, so removing or adding through it changes what EMI renders.
 */
public final class TooltipBuilderFix {
    public static final String ID = "F-12";
    public static final String WHAT = "EMI's JEI tooltip builder gains getLines (live), clear, clearIngredient and addKeyUsageComponent";
    public static final String TARGET = EmiGate.TARGET;

    private TooltipBuilderFix() {}

    /** JEI's key-usage line: the translation key formatted with the key's name in bold, the whole line italic and gray. */
    public static MutableComponent keyUsage(String translationKey, Component keyName) {
        return Component.translatable(translationKey, keyName.copy().withStyle(ChatFormatting.BOLD))
                .withStyle(ChatFormatting.ITALIC)
                .withStyle(ChatFormatting.GRAY);
    }
}
