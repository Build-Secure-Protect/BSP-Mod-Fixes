package com.mrgregles.bsp_mod_fixes.fix.emi;

/**
 * F-14 (EMI 1.1.24+1.20.1+forge): EMI's {@code JemiRecipeExtrasBuilder} implements JEI's {@code IRecipeExtrasBuilder} as it
 * was in JEI 15.20. JEI 15.56 added the widget-returning methods ({@code addDrawableWidget}, {@code addTooltipArea},
 * {@code addRecipeArrowWidget}, {@code addRecipePlusSignWidget}, {@code addAnimatedRecipeArrowWidget}, {@code
 * addAnimatedRecipeFlameWidget}, {@code addButton}, {@code addCycleButton}) and the aligned {@code setPosition} overload on
 * every placeable, all abstract; Tinkers' Construct draws its arrows, icons and tooltip areas through them, so under EMI
 * each category's extras threw {@code AbstractMethodError} (logged by EMI as "Exception adding JEMI extras") and the
 * recipe drew without them. The mixins add the methods, backed by EMI's own widgets the way EMI's older {@code addDrawable}
 * and {@code addRecipeArrow} are, with the widget's tooltip (text, lines, a rich component or a callback) on the same area.
 * Buttons are minimal working widgets (D17).
 */
public final class ExtrasFix {
    public static final String ID = "F-14";
    public static final String WHAT = "EMI's JEI recipe extras builder gains the JEI 15.56 widget methods (drawables, arrows, tooltip areas, buttons) with their tooltips";
    public static final String TARGET = EmiGate.TARGET;

    private ExtrasFix() {}
}
