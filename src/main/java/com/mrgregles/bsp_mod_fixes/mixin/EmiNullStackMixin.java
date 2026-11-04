package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.emi.NullStack;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.JemiUtil;
import mezz.jei.api.ingredients.IIngredientType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** F-11: a null JEI ingredient converts to the empty EMI stack. Applied only when EMI is at the pinned version (EmiGate). */
@Mixin(value = JemiUtil.class, remap = false)
public abstract class EmiNullStackMixin {
    @Inject(method = "getStack(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;)Ldev/emi/emi/api/stack/EmiStack;",
            at = @At("HEAD"), cancellable = true)
    private static void bsp_mod_fixes$emptyForNull(IIngredientType<?> type, Object ingredient, CallbackInfoReturnable<EmiStack> cir) {
        if (NullStack.isMissing(ingredient)) {
            cir.setReturnValue(EmiStack.EMPTY);
        }
    }
}
