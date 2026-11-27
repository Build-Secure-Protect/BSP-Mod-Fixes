package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.chunks.UnloadIteration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * F-48: the two {@code Collection.forEach} calls of {@code LevelChunk.clearAllBlockEntities} that walk the block entity map
 * (the {@code onChunkUnloaded} pass, then the {@code setRemoved} pass) go through {@link UnloadIteration}, which runs the same
 * action over a copy retaken when another thread changes the map, names a block entity that changed the map meanwhile and
 * removes block entities added during the unload; the map's {@code clear} that follows goes through it too, so that passes
 * deferred to the next server task (five copies failed) still find their entries there and clear afterwards. The third
 * {@code forEach} and the second {@code clear}, the ticker rebind, are untouched (Canary and AllTheLeaks work there). Targets a
 * Minecraft class, so the method name is remapped ({@code m_187957_} in the built jar's refmap); the redirected calls are the
 * JDK's. {@code ordinal} picks the call; the config's {@code defaultRequire = 1} makes a method without that call fail the mixin
 * instead of being changed unseen, and {@code allow = 1} caps each redirect at one call (a build that added a {@code forEach}
 * or a {@code clear} before these would shift the ordinals unseen; Forge is pinned by this mod's build, so the guard is
 * one-directional by design). Applied on both sides (the
 * client's {@code ClientLevel.unload} runs the same method); not gated. {@code LevelChunkOffThreadMixin} (F-51) injects into
 * the same class at other points; the two are independent.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkUnloadMixin {
    @Redirect(method = "clearAllBlockEntities",
              at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V", ordinal = 0),
              allow = 1)
    private void bsp_mod_fixes$unloadOverACopy(Collection<BlockEntity> values, Consumer<BlockEntity> action) {
        UnloadIteration.unloadPass((LevelChunk) (Object) this, values, action);
    }

    @Redirect(method = "clearAllBlockEntities",
              at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V", ordinal = 1),
              allow = 1)
    private void bsp_mod_fixes$removeOverACopy(Collection<BlockEntity> values, Consumer<BlockEntity> action) {
        UnloadIteration.removePass((LevelChunk) (Object) this, values, action);
    }

    @Redirect(method = "clearAllBlockEntities",
              at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V", ordinal = 0),
              allow = 1)
    private void bsp_mod_fixes$clearOrDefer(Map<BlockPos, BlockEntity> map) {
        UnloadIteration.clearOrDefer((LevelChunk) (Object) this, map);
    }
}
