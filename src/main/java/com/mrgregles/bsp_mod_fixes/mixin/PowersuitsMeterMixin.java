package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.MeterGuard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-26: Powersuits' meter overlay is drawn only while a modular item is worn or held ({@link MeterGuard}). Targets a
 * Powersuits class (no remapping) that exists on a client only, so the mixin config plugin skips it on a dedicated server
 * ({@code fix/proxy/ClientGate}).
 */
@Mixin(targets = "lehjr.powersuits.client.gui.overlay.MPSMeterOverlay", remap = false)
public abstract class PowersuitsMeterMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void bsp_mod_fixes$onlyWithASuit(Player player, GuiGraphics graphics, float partialTick, float scaledWidth, float scaledHeight, CallbackInfo ci) {
        if (!MeterGuard.shouldDraw(MeterGuard.modularEquipped(player))) {
            MeterGuard.skipped();
            ci.cancel();
        }
    }
}
