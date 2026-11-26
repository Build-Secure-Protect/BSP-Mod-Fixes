package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.integrateddynamics.DanglingPartsHooks;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.core.network.Network;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-49: the one {@code INetworkElement.beforeNetworkKill} call in {@code Network.kill()}, and the one {@code beforeNetworkKill}
 * and one {@code onNetworkRemoval} call in {@code Network.removeNetworkElementPost}, go through {@link DanglingPartsHooks}, which
 * skips a part element whose part is gone (and a {@code PartStateException} the call throws) with one WARN per position
 * instead of letting the exception out of the server tick. {@code allow = 1} on each makes a changed build fail the mixin
 * instead of being changed unseen. Targets an Integrated Dynamics class, whose names are never remapped ({@code remap = false});
 * applied only beside exactly the build it was written for, with its libraries ({@code fix/integrateddynamics/IdGate}).
 */
@Mixin(value = Network.class, remap = false)
public abstract class IdNetworkKillMixin {
    @Redirect(method = "kill",
              at = @At(value = "INVOKE", target = "Lorg/cyclops/integrateddynamics/api/network/INetworkElement;beforeNetworkKill(Lorg/cyclops/integrateddynamics/api/network/INetwork;)V"),
              allow = 1)
    private void bsp_mod_fixes$killSkipsAMissingPart(INetworkElement element, INetwork network) {
        DanglingPartsHooks.beforeNetworkKill(element, network, "kill");
    }

    @Redirect(method = "removeNetworkElementPost",
              at = @At(value = "INVOKE", target = "Lorg/cyclops/integrateddynamics/api/network/INetworkElement;beforeNetworkKill(Lorg/cyclops/integrateddynamics/api/network/INetwork;)V"),
              allow = 1)
    private void bsp_mod_fixes$removalSkipsAMissingPartBefore(INetworkElement element, INetwork network) {
        DanglingPartsHooks.beforeNetworkKill(element, network, "removal");
    }

    @Redirect(method = "removeNetworkElementPost",
              at = @At(value = "INVOKE", target = "Lorg/cyclops/integrateddynamics/api/network/INetworkElement;onNetworkRemoval(Lorg/cyclops/integrateddynamics/api/network/INetwork;)V"),
              allow = 1)
    private void bsp_mod_fixes$removalSkipsAMissingPartAfter(INetworkElement element, INetwork network) {
        DanglingPartsHooks.onNetworkRemoval(element, network, "removal");
    }
}
