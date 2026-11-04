package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.CreativeInstallGuard;
import lehjr.powersuits.common.network.packets.serverbound.CreativeInstallPacketServerBound;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** F-1: refuses the creative-install packet for a sender who is not in creative mode. Logic in {@link CreativeInstallGuard}. */
@Mixin(value = CreativeInstallPacketServerBound.Handler.class, remap = false)
public abstract class CreativeInstallGuardMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void bsp_mod_fixes$requireCreative(CreativeInstallPacketServerBound message, Supplier<NetworkEvent.Context> ctx, CallbackInfo ci) {
        if (!CreativeInstallGuard.allows(ctx.get().getSender(), message.slotType(), message.regName())) {
            ctx.get().setPacketHandled(true);
            ci.cancel();
        }
    }
}
