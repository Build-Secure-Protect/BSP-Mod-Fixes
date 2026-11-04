package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.FireImmunityToggle;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** F-10: Numina's one call to {@code Player.clearFire()} in its tick handler goes through the config toggle. */
@Mixin(value = lehjr.numina.common.event.PlayerUpdateHandler.class, remap = false)
public abstract class NuminaFireClearMixin {
    @Redirect(method = "onPlayerUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;clearFire()V", remap = true))
    private void bsp_mod_fixes$clearFireIfAllowed(Player player) {
        if (FireImmunityToggle.clearsFire()) {
            player.clearFire();
        }
    }
}
