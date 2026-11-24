package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.mffs.LoadedField;
import dev.su5ed.mffs.blockentity.ForceFieldBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-44, the field block's part: MFFS's {@code onLoad} looks the block's projector up and removes the block when it finds none,
 * and the lookup loads the projector's chunk whenever a field chunk loads first. On the server, a field block whose projector's
 * chunk is not loaded is kept as saved and the method returns ({@link LoadedField}); with the projector's chunk loaded, MFFS's
 * check runs as before, so a stale block is still removed. The client's part of {@code onLoad} is untouched (it is F-36's).
 * Targets an MFFS class ({@code remap = false}); applied only beside exactly the MFFS build it was written for; switched off by
 * {@code [mffs] fieldStaysInLoadedChunks = false}.
 */
@Mixin(value = ForceFieldBlockEntity.class, remap = false)
public abstract class MffsFieldBlockLoadMixin {
    @Shadow private BlockPos projector;

    @Inject(method = "onLoad", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$keepWithoutLoadingTheProjector(CallbackInfo ci) {
        if (LoadedField.holdsFieldBlock(((BlockEntity) (Object) this).getLevel(), projector)) {
            ci.cancel();
        }
    }
}
