package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * Dev-client probe runs only ({@code bsp_mod_fixes.probe.mixins.json}, never in the jar): Forge's {@code ConfigValue.get()}
 * answers the default before its config has loaded, as it does in production. In a development run Forge throws instead, and
 * Modular Powersuits' key handler asks every item for its energy capability while key mappings are registered, before any config
 * has loaded, which makes Ender IO's capacitor bank read its config and crash the dev client at startup. The pack never sees this.
 */
@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
public abstract class ProbeConfigValueMixin<T> {
    @Shadow private ForgeConfigSpec spec;
    @Shadow @Final private Supplier<T> defaultSupplier;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$defaultBeforeLoad(CallbackInfoReturnable<T> cir) {
        if (spec != null && !spec.isLoaded()) {
            cir.setReturnValue(defaultSupplier.get());
        }
    }
}
