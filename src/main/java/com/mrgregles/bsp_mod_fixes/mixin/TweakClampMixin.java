package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.TweakClamp;
import lehjr.numina.common.network.packets.serverbound.TweakRequestDoublePacketServerBound;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * F-2: a tweak request outside 0..1 is replaced by the same request with the clamped value, handed back to the handler, and
 * the original call is cancelled. The second call sees a value in range and runs untouched. Logic in {@link TweakClamp}.
 */
@Mixin(value = TweakRequestDoublePacketServerBound.Handler.class, remap = false)
public abstract class TweakClampMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void bsp_mod_fixes$clampTweak(TweakRequestDoublePacketServerBound message, Supplier<NetworkEvent.Context> ctx, CallbackInfo ci) {
        double raw = message.tweakValue();
        if (TweakClamp.inRange(raw)) {
            return;
        }
        double clamped = TweakClamp.clamp(raw);
        TweakClamp.warn(ctx.get().getSender(), message.moduleRegName(), message.tweakName(), raw, clamped);
        TweakRequestDoublePacketServerBound.Handler.handle(
                new TweakRequestDoublePacketServerBound(message.type(), message.moduleRegName(), message.tweakName(), clamped), ctx);
        ci.cancel();
    }
}
