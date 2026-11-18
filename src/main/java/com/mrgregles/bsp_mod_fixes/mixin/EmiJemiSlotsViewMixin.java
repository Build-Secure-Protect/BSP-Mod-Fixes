package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.emi.TransferSlots;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.jemi.JemiRecipeHandler;
import mezz.jei.api.recipe.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * F-37: the one {@code List.subList} call in EMI's {@code JemiRecipeHandler.createSlotsView} (the shaped crafting branch) is
 * answered by {@link TransferSlots#subList}, with both bounds held within the input list. The target method is static; its
 * own arguments are captured after the redirected call's, so the log line can name the recipe; {@code allow = 1} makes a
 * second {@code subList} call in a changed EMI fail the mixin instead of being redirected unseen. Applied only when EMI is at
 * the pinned version; switched off by the client config's {@code [emi] clampJeiTransferSlots = false}.
 */
@Mixin(value = JemiRecipeHandler.class, remap = false)
public abstract class EmiJemiSlotsViewMixin {
    @Redirect(method = "createSlotsView", at = @At(value = "INVOKE", target = "Ljava/util/List;subList(II)Ljava/util/List;"), allow = 1)
    private static List<EmiIngredient> bsp_mod_fixes$clampInputSubList(List<EmiIngredient> inputs, int from, int to,
                                                                        EmiRecipe recipe, Object rawRecipe, RecipeType<?> type, List<Widget> widgets) {
        return TransferSlots.subList(inputs, from, to, recipe == null ? null : recipe.getId());
    }
}
