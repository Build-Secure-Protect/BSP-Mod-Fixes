package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.emi.SlotViews;
import dev.emi.emi.jemi.impl.JemiRecipeSlotDrawable;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * F-41: the same members on EMI's slot drawable, JEI's drawable view of a slot, which hands everything to the slot it wraps
 * (whose own members {@code EmiJemiSlotViewMixin} adds, so this mixin is applied only together with that one, under the same
 * gate); and {@code IRecipeSlotDrawable.drawTooltip}, which draws the drawable's own tooltip lines. EMI 1.1.24 never constructs
 * this class (its drawables view answers an empty list), so this mixin is defensive, for a JEI plugin that would build one
 * itself, and {@code drawTooltip} is untested. Applied only when EMI is at the pinned version, on a client
 * ({@code fix/emi/EmiGate}).
 */
@Mixin(value = JemiRecipeSlotDrawable.class, remap = false)
public abstract class EmiJemiSlotDrawableMixin implements IRecipeSlotDrawable {
    @Shadow public JemiSlotWidget widget;

    @Shadow public abstract List<Component> getTooltip();

    @Override
    public List<ITypedIngredient<?>> getAllIngredientsList() {
        return SlotViews.list(widget.slot.getAllIngredients());
    }

    @Override
    public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
        return widget.slot.getAllIngredients();
    }

    @Override
    public Optional<TagKey<?>> getTagKey() {
        return widget.slot.getTagKey();
    }

    @Override
    public void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.renderComponentTooltip(Minecraft.getInstance().font, getTooltip(), mouseX, mouseY);
    }
}
