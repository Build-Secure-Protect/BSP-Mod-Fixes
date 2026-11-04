package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.emi.TextTooltipAccess;
import com.mrgregles.bsp_mod_fixes.fix.emi.TextWidgets;
import com.mrgregles.bsp_mod_fixes.fix.emi.WidgetTooltip;
import dev.emi.emi.jemi.impl.extras.JemiPlaceable;
import dev.emi.emi.jemi.impl.extras.JemiTextWidget;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import mezz.jei.api.gui.widgets.ITextWidget;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

/**
 * F-15: EMI's text widget takes the four tooltip forms, starts with JEI's line spacing and shadow defaults, and answers JEI 15.56's
 * {@code ITextWidget.setPosition} overloads. JEI redeclares both with the return type {@code ITextWidget}; EMI's widget was
 * compiled before that and only has the {@code IPlaceable}-returning forms from {@code JemiPlaceable}, so a plugin calling
 * {@code addText(...).setPosition(x, y)} under EMI threw {@code AbstractMethodError} (Tinkers' Construct does, in seven
 * categories). The positions are written straight to {@code JemiPlaceable}'s public fields: calling its own
 * {@code setPosition} from here would come back through the bridge this method gets. Applied only when EMI is at the
 * pinned version.
 */
@Mixin(value = JemiTextWidget.class, remap = false)
public abstract class EmiTextWidgetMixin implements ITextWidget, TextTooltipAccess {
    @Shadow public int spacing;
    @Shadow public boolean shadow;

    @Unique
    private WidgetTooltip bsp_mod_fixes$tooltip;

    @Override
    public WidgetTooltip bsp_mod_fixes$tooltip() {
        if (bsp_mod_fixes$tooltip == null) {
            bsp_mod_fixes$tooltip = new WidgetTooltip();
        }
        return bsp_mod_fixes$tooltip;
    }

    /** JEI's text defaults: a line spacing of 2 and no shadow (EMI starts at 0 and with a shadow); a plugin's own setters still win. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bsp_mod_fixes$jeiTextDefaults(List<FormattedText> text, int width, int height, CallbackInfo ci) {
        spacing = TextWidgets.DEFAULT_LINE_SPACING;
        shadow = TextWidgets.DEFAULT_SHADOW;
    }

    @Override
    public ITextWidget setPosition(int xPos, int yPos) {
        JemiPlaceable<?> placeable = (JemiPlaceable<?>) (Object) this;
        placeable.x = xPos;
        placeable.y = yPos;
        return this;
    }

    @Override
    public ITextWidget setPosition(int areaX, int areaY, int areaWidth, int areaHeight, HorizontalAlignment horizontal, VerticalAlignment vertical) {
        return setPosition(areaX + horizontal.getXPos(areaWidth, getWidth()), areaY + vertical.getYPos(areaHeight, getHeight()));
    }

    @Override
    public ITextWidget setTooltip(FormattedText text) {
        bsp_mod_fixes$tooltip().set(text);
        return this;
    }

    @Override
    public ITextWidget setTooltip(Collection<? extends FormattedText> lines) {
        bsp_mod_fixes$tooltip().set(lines);
        return this;
    }

    @Override
    public ITextWidget setTooltip(TooltipComponent component) {
        bsp_mod_fixes$tooltip().set(component);
        return this;
    }

    @Override
    public ITextWidget setTooltip(IRecipeWidgetTooltipCallback callback) {
        bsp_mod_fixes$tooltip().set(callback);
        return this;
    }
}
