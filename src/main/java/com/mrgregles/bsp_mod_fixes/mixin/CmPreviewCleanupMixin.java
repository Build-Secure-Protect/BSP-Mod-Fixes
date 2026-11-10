package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.client.PreviewCleanup;
import dev.compactmods.machines.room.client.MachineRoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-30: Compact Machines' room preview screen releases what it allocated, when it closes and before it rebuilds its
 * widgets on a window resize ({@link PreviewCleanup}). Targets a Compact Machines class; the two methods are Minecraft's
 * {@code Screen.init} and {@code Screen.removed} as that class overrides them, so those two names are remapped. Client
 * only, and only beside the Compact Machines build it was written for ({@code fix/cm/CmGate}).
 */
@Mixin(value = MachineRoomScreen.class, remap = false)
public abstract class CmPreviewCleanupMixin {
    @Inject(method = "init()V", at = @At("HEAD"), remap = true)
    private void bsp_mod_fixes$releaseTheOldRenderer(CallbackInfo ci) {
        PreviewCleanup.beforeInit(this);
    }

    @Inject(method = "removed()V", at = @At("TAIL"), remap = true)
    private void bsp_mod_fixes$releaseThePreview(CallbackInfo ci) {
        PreviewCleanup.afterRemoved(this);
    }
}
