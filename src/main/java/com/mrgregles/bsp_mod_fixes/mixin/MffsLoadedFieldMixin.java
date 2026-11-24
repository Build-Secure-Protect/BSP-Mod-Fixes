package com.mrgregles.bsp_mod_fixes.mixin;

import com.google.common.cache.LoadingCache;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import com.mrgregles.bsp_mod_fixes.fix.mffs.LoadedField;
import dev.su5ed.mffs.api.Projector;
import dev.su5ed.mffs.api.module.Module;
import dev.su5ed.mffs.blockentity.ProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-44, the projector's part: in {@code selectProjectablePositions} a position whose chunk does not tick blocks is skipped before
 * any module's {@code onSelect} (the Disintegration module reads the block state there) and before the position cache (whose
 * loader reads the block state, loading the chunk), the chunk's answer memoised for the pass (a pass visits every calculated
 * position; the modules' hook fires once per module per position); in {@code destroyField}'s filter the block state is read only where the chunk is
 * loaded, so a teardown removes what stands in loaded chunks and loads nothing ({@link LoadedField}). The filter is the
 * synthetic lambda of this build, hence the exact version pin; the block-state call inside it is Minecraft's, remapped through
 * the refmap. Targets an MFFS class ({@code remap = false} on the class); applied only beside exactly the MFFS build it was
 * written for; switched off by {@code [mffs] fieldStaysInLoadedChunks = false}.
 */
@Mixin(value = ProjectorBlockEntity.class, remap = false)
public abstract class MffsLoadedFieldMixin {
    /** The ticking answer per chunk for the pass under way: cleared at the head of each selection, filled as positions come. */
    @Unique
    private final Long2BooleanMap bsp_mod_fixes$tickingByChunk = new Long2BooleanOpenHashMap();

    @Inject(method = "selectProjectablePositions", at = @At("HEAD"))
    private void bsp_mod_fixes$startPass(CallbackInfoReturnable<java.util.List<?>> cir) {
        bsp_mod_fixes$tickingByChunk.clear();
    }

    @Redirect(method = "selectProjectablePositions",
              at = @At(value = "INVOKE", target = "Ldev/su5ed/mffs/api/module/Module;onSelect(Ldev/su5ed/mffs/api/Projector;Lnet/minecraft/core/BlockPos;)Ldev/su5ed/mffs/api/module/Module$ProjectAction;"),
              allow = 1)
    private Module.ProjectAction bsp_mod_fixes$skipUnloadedBeforeModules(Module module, Projector projector, BlockPos pos) {
        if (LoadedField.skip(((BlockEntity) (Object) this).getLevel(), pos, bsp_mod_fixes$tickingByChunk)) {
            return Module.ProjectAction.SKIP;
        }
        return module.onSelect(projector, pos);
    }

    @Redirect(method = "selectProjectablePositions",
              at = @At(value = "INVOKE", target = "Lcom/google/common/cache/LoadingCache;getUnchecked(Ljava/lang/Object;)Ljava/lang/Object;"),
              allow = 1)
    private Object bsp_mod_fixes$skipUnloadedBeforeTheCache(LoadingCache<Object, Object> cache, Object key) {
        if (key instanceof BlockPos pos && LoadedField.skip(((BlockEntity) (Object) this).getLevel(), pos, bsp_mod_fixes$tickingByChunk)) {
            return LoadedField.notProjectable();
        }
        return cache.getUnchecked(key);
    }

    @Redirect(method = "lambda$destroyField$19",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", remap = true),
              allow = 1)
    private BlockState bsp_mod_fixes$tearDownOnlyWhereLoaded(Level level, BlockPos pos) {
        return LoadedField.blockStateIfLoaded(level, pos);
    }
}
