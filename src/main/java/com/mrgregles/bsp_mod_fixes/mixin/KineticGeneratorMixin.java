package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.KineticGenerator;
import lehjr.numina.common.capabilities.module.tickable.IPlayerTickModule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F-6: the Kinetic Generator's tick is replaced by {@link KineticGenerator#tick}. */
@Mixin(targets = "lehjr.powersuits.common.item.module.energy_generation.KineticGeneratorModule$CapProvider$Ticker", remap = false)
public abstract class KineticGeneratorMixin {
    @Inject(method = "onPlayerTickActive", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$perBlock(Player player, ItemStack suit, CallbackInfo ci) {
        KineticGenerator.tick(player, (IPlayerTickModule) (Object) this, suit);
        ci.cancel();
    }
}
