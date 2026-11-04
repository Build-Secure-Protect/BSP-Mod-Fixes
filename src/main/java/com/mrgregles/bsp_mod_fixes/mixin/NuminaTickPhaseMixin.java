package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.TickPhaseFilter;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F-3: Numina's player tick handler is skipped on every phase but START. Logic in {@link TickPhaseFilter}. */
@Mixin(value = lehjr.numina.common.event.PlayerUpdateHandler.class, remap = false)
public abstract class NuminaTickPhaseMixin {
    @Inject(method = "onPlayerUpdate", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$oncePerTick(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (!TickPhaseFilter.runs(event.phase)) {
            ci.cancel();
        }
    }
}
