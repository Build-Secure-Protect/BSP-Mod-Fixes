package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.FlyingSpeedKeeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F-24: the Jump Assist's tick leaves the creative flying speed as it found it ({@link FlyingSpeedKeeper}). */
@Mixin(targets = "lehjr.powersuits.common.item.module.movement.JumpAssistModule$CapProvider$Ticker", remap = false)
public abstract class JumpAssistFlyingSpeedMixin {
    @Unique
    private float bsp_mod_fixes$flyingSpeedBefore;

    @Inject(method = "onPlayerTickActive", at = @At("HEAD"))
    private void bsp_mod_fixes$remember(Player player, ItemStack leggings, CallbackInfo ci) {
        bsp_mod_fixes$flyingSpeedBefore = FlyingSpeedKeeper.before(player);
    }

    @Inject(method = "onPlayerTickActive", at = @At("RETURN"))
    private void bsp_mod_fixes$restore(Player player, ItemStack leggings, CallbackInfo ci) {
        FlyingSpeedKeeper.restore(player, bsp_mod_fixes$flyingSpeedBefore);
    }
}
