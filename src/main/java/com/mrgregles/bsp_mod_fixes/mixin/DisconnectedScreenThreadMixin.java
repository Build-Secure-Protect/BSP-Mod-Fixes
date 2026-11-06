package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.proxy.DeferredDisconnectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-17: a {@code DisconnectedScreen} set from any thread but the render thread is re-submitted to the render thread and the
 * call in hand is cancelled. Targets a client-only class, so the mixin config plugin skips it on a dedicated server
 * ({@code fix/proxy/ClientGate}); remapped like the other Minecraft targets (the refmap carries {@code setScreen}).
 */
@Mixin(Minecraft.class)
public abstract class DisconnectedScreenThreadMixin {
    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$openDisconnectedScreenOnTheRenderThread(Screen screen, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (DeferredDisconnectScreen.mustDefer(screen instanceof DisconnectedScreen, minecraft.isSameThread())) {
            DeferredDisconnectScreen.deferred(Thread.currentThread().getName());
            minecraft.execute(() -> minecraft.setScreen(screen));
            ci.cancel();
        }
    }
}
