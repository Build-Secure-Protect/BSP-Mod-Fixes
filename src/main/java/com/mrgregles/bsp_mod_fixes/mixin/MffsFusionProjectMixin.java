package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.mffs.LoadedField;
import dev.su5ed.mffs.util.module.FusionModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-44, the Fusion module's part: {@code beforeProject} removes the field blocks it collected inside another projector's field,
 * reading each position's block state first, which loads the chunk; the read goes through {@link LoadedField}, which answers air
 * (nothing to remove) where the chunk is not loaded. The block-state call is Minecraft's, remapped through the refmap. Targets an
 * MFFS class ({@code remap = false} on the class); applied only beside exactly the MFFS build it was written for; switched off by
 * {@code [mffs] fieldStaysInLoadedChunks = false}.
 */
@Mixin(value = FusionModule.class, remap = false)
public abstract class MffsFusionProjectMixin {
    @Redirect(method = "beforeProject",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", remap = true),
              allow = 1)
    private BlockState bsp_mod_fixes$removeOnlyWhereLoaded(Level level, BlockPos pos) {
        return LoadedField.blockStateIfLoaded(level, pos);
    }
}
