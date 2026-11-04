package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.emi.EmiExtras;
import dev.emi.emi.jemi.impl.extras.JemiRecipeExtrasBuilder;
import dev.emi.emi.jemi.impl.extras.JemiWidgetBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IButtonWidget;
import mezz.jei.api.gui.widgets.ICycleButtonWidget;
import mezz.jei.api.gui.widgets.IDrawableWidget;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ITextWidget;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;

/**
 * F-14: the JEI 15.56 widget methods EMI's extras builder lacks; F-15: the text widget EMI makes is registered for drawing.
 * Applied only when EMI is at the pinned version (EmiGate).
 */
@Mixin(value = JemiRecipeExtrasBuilder.class, remap = false)
public abstract class EmiRecipeExtrasMixin implements IRecipeExtrasBuilder {
    @Shadow public List<JemiWidgetBuilder> widgets;

    @Override
    public IDrawableWidget addDrawableWidget(IDrawable drawable) {
        return EmiExtras.drawable(widgets, drawable);
    }

    @Override
    public IDrawableWidget addTooltipArea(int x, int y, int width, int height) {
        return EmiExtras.tooltipArea(widgets, x, y, width, height);
    }

    @Override
    public IDrawableWidget addRecipeArrowWidget() {
        return EmiExtras.arrow(widgets);
    }

    @Override
    public IDrawableWidget addRecipePlusSignWidget() {
        return EmiExtras.plusSign(widgets);
    }

    @Override
    public IDrawableWidget addAnimatedRecipeArrowWidget(int ticksPerCycle) {
        return EmiExtras.animatedArrow(widgets, ticksPerCycle);
    }

    @Override
    public IDrawableWidget addAnimatedRecipeFlameWidget(int cookTime) {
        return EmiExtras.animatedFlame(widgets, cookTime);
    }

    @Override
    public IButtonWidget<?> addButton(int width, int height, Runnable action) {
        return EmiExtras.button(widgets, width, height, action);
    }

    @Override
    public <T> ICycleButtonWidget<T> addCycleButton(List<T> values, T initial, int width, int height, Consumer<T> onChange) {
        return EmiExtras.cycleButton(widgets, values, initial, width, height, onChange);
    }

    @Inject(method = "addText", at = @At("RETURN"))
    private void bsp_mod_fixes$drawText(List<FormattedText> text, int maxWidth, int maxHeight, CallbackInfoReturnable<ITextWidget> cir) {
        EmiExtras.text(widgets, cir.getReturnValue());
    }
}
