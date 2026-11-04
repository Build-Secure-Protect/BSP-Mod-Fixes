package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.LivingEventFilter;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F-4: Powersuits' player update handler is skipped for every living event but the tick. Logic in {@link LivingEventFilter}. */
@Mixin(value = lehjr.powersuits.common.event.PlayerUpdateHandler.class, remap = false)
public abstract class PowersuitsLivingEventMixin {
    @Inject(method = "onPlayerUpdate", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$tickOnly(LivingEvent event, CallbackInfo ci) {
        if (!LivingEventFilter.runs(event)) {
            ci.cancel();
        }
    }
}
