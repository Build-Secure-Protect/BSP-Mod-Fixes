package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.aae.QuantumAbsorptionHandler;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.pedroksl.advanced_ae.events.AAELivingEntityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-25: AdvancedAE's damage handler is replaced by {@link QuantumAbsorptionHandler#handle}, the same arithmetic plus the
 * action-bar line. Targets an AdvancedAE class (no remapping); the mixin config plugin applies it only when AdvancedAE
 * is loaded at the build it was written for ({@code fix/aae/AaeGate}). The handler is a static method the mod registers
 * on the Forge bus by reference, so the injection reaches it through the bus's wrapper.
 */
@Mixin(value = AAELivingEntityEvents.class, remap = false)
public abstract class AaeDamageAbsorptionMixin {
    @Inject(method = "incomingDamage", at = @At("HEAD"), cancellable = true)
    private static void bsp_mod_fixes$absorbWithALine(LivingDamageEvent event, CallbackInfo ci) {
        QuantumAbsorptionHandler.handle(event);
        ci.cancel();
    }
}
