package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.emi.EmiTooltips;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** F-13: a slot's rich tooltip callback runs with EMI's fluid amount line on offer. Applied only when EMI is at the pinned version. */
@Mixin(value = JemiSlotWidget.class, remap = false)
public abstract class EmiSlotTooltipMixin {
    @Redirect(method = "addTooltip", at = @At(value = "INVOKE",
            target = "Lmezz/jei/api/gui/ingredient/IRecipeSlotRichTooltipCallback;onRichTooltip(Lmezz/jei/api/gui/ingredient/IRecipeSlotView;Lmezz/jei/api/gui/builder/ITooltipBuilder;)V"))
    private static void bsp_mod_fixes$richTooltipWithAmount(IRecipeSlotRichTooltipCallback callback, IRecipeSlotView slotView, ITooltipBuilder builder,
                                                           List<ClientTooltipComponent> tooltip, JemiRecipeSlot slot, EmiIngredient stack, IIngredientRenderer<?> renderer) {
        EmiTooltips.richTooltip(callback, slotView, builder, tooltip, stack);
    }
}
