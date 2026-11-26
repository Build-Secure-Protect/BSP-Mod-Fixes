package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.integrateddynamics.OrphanRemovalHooks;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.capability.partcontainer.PartContainerDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F-50: at the head of {@code PartContainerDefault.removePart}, when the container has a part on that side and its cable's
 * carrier has no network, {@link OrphanRemovalHooks#beforeRemoval} removes the part's element from every saved network that still
 * lists it, so the carrier-less branch that follows (the drop and the part's removal, telling no network) leaves nothing
 * dangling. Nothing is cancelled or changed in the method itself. Targets an Integrated Dynamics class, whose names are never
 * remapped ({@code remap = false}); applied only beside exactly the build it was written for ({@code fix/integrateddynamics/IdGate}).
 */
@Mixin(value = PartContainerDefault.class, remap = false)
public abstract class IdPartRemovalMixin {
    @Shadow protected abstract INetwork getNetwork();

    @Inject(method = "removePart", at = @At("HEAD"))
    private void bsp_mod_fixes$removeTheRecordACarrierlessCableForgot(Direction side, Player player, boolean dropMainElement, boolean saveState, CallbackInfoReturnable<IPartType> cir) {
        PartContainerDefault self = (PartContainerDefault) (Object) this;
        OrphanRemovalHooks.beforeRemoval(self.getPosition(), side, self.hasPart(side), getNetwork());
    }
}
