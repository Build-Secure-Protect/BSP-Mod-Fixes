package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.chunks.UnloadIteration;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * F-48: the two {@code Collection.forEach} calls of {@code LevelChunk.clearAllBlockEntities} that walk the block entity map
 * (the {@code onChunkUnloaded} pass, then the {@code setRemoved} pass) go through {@link UnloadIteration}, which runs the same
 * action over a copy, names a block entity that changed the map meanwhile and removes block entities added during the
 * unload. The third {@code forEach}, the ticker rebind, is untouched (Canary and AllTheLeaks work there). Targets a Minecraft
 * class, so the method name is remapped ({@code m_187957_} in the built jar's refmap); the redirected calls are the JDK's.
 * {@code ordinal} picks the pass, {@code allow = 1} on each makes a method with fewer such calls fail the mixin instead of being
 * changed unseen (a build that added a {@code forEach} before these two would shift the ordinals unseen; Forge is pinned by
 * this mod's build, so the guard is one-directional by design). Applied on both sides (the client's {@code ClientLevel.unload} runs the same method); not gated.
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
}
