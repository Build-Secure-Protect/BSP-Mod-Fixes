package com.mrgregles.bsp_mod_fixes.mixin;

import lehjr.powersuits.common.event.MovementManager;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F-7: Powersuits' fall handler is cancelled; {@link com.mrgregles.bsp_mod_fixes.fix.ShockAbsorber#onFall} replaces it. */
@Mixin(value = MovementManager.class, remap = false)
public abstract class MovementManagerFallMixin {
    @Inject(method = "handleFallEvent", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$replaced(LivingFallEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
