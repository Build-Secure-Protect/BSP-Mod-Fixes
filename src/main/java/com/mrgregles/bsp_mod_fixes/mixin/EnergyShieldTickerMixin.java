package com.mrgregles.bsp_mod_fixes.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F-8 (b): the Energy Shield's tick, which switched the module off for good when energy was low, does nothing. */
@Mixin(targets = "lehjr.powersuits.common.item.module.armor.EnergyShieldModule$CapProvider$Ticker", remap = false)
public abstract class EnergyShieldTickerMixin {
    @Inject(method = "onPlayerTickActive", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$staysOn(Player player, ItemStack item, CallbackInfo ci) {
        ci.cancel();
    }
}
