package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.chunks.OffThreadTracer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F-51: the four places where {@code LevelChunk} changes its block entity map tell {@link OffThreadTracer} who is calling: the
 * head of {@code setBlockEntity}, {@code removeBlockEntity} and {@code clearAllBlockEntities}, and the {@code Map.remove}
 * inside {@code getBlockEntity(BlockPos, EntityCreationType)} that drops an entry whose block entity is removed (the other
 * {@code remove} in that method is on the pending map; {@code ordinal = 0} is the first). Nothing is cancelled or changed.
 * Targets a Minecraft class, so the names are remapped through the refmap. Applied on both sides; the tracer returns at once
 * for a client level. Not gated. {@code LevelChunkUnloadMixin} (F-48) redirects calls inside {@code clearAllBlockEntities};
 * the two are independent.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkOffThreadMixin {
    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void bsp_mod_fixes$traceSet(BlockEntity blockEntity, CallbackInfo ci) {
        OffThreadTracer.onWrite((LevelChunk) (Object) this, "setBlockEntity", blockEntity.getBlockPos());
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void bsp_mod_fixes$traceRemove(BlockPos pos, CallbackInfo ci) {
        OffThreadTracer.onWrite((LevelChunk) (Object) this, "removeBlockEntity", pos);
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void bsp_mod_fixes$traceClear(CallbackInfo ci) {
        OffThreadTracer.onWrite((LevelChunk) (Object) this, "clearAllBlockEntities", null);
    }

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void bsp_mod_fixes$traceDropOfRemoved(BlockPos pos, LevelChunk.EntityCreationType type, CallbackInfoReturnable<BlockEntity> cir) {
        OffThreadTracer.onWrite((LevelChunk) (Object) this, "getBlockEntity (dropping a removed block entity's entry)", pos);
    }
}
