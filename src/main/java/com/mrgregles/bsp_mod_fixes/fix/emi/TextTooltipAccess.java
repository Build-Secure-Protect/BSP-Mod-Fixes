package com.mrgregles.bsp_mod_fixes.fix.emi;

/** Added to EMI's {@code JemiTextWidget} by the F-15 mixin: the tooltip set through JEI's {@code ITextWidget}. Client only. */
public interface TextTooltipAccess {
    WidgetTooltip bsp_mod_fixes$tooltip();
}
