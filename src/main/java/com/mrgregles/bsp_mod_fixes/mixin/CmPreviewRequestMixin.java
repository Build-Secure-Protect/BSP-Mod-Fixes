package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewRequests;
import dev.compactmods.machines.machine.CompactMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F-31: a right click on a Compact Machine with an empty hand, which in Compact Machines does one thing, the room preview,
 * is answered by {@link PreviewRequests} on the server. Every other use of the block (the shrinking device, a name tag, an
 * upgrade item, all of which need an item in the hand) and the client's side of the click go on to Compact Machines
 * untouched. Targets a Compact Machines class; {@code use} is Minecraft's method as that class overrides it, so that name
 * is remapped. Applied only beside the Compact Machines build it was written for ({@code fix/cm/CmGate}).
 */
@Mixin(value = CompactMachineBlock.class, remap = false)
public abstract class CmPreviewRequestMixin {
    @Inject(method = "use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true, remap = true)
    private void bsp_mod_fixes$previewByRule(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer && player.getMainHandItem().isEmpty()
                && PreviewRequests.handle(serverLevel, pos, serverPlayer)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
