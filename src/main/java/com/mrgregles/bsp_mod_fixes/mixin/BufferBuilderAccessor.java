package com.mrgregles.bsp_mod_fixes.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

/**
 * F-30: the native memory a {@code BufferBuilder} holds. Vanilla never frees one (its own are kept for the whole session);
 * the room preview of Compact Machines makes ten per preview and drops them, so {@code client/PreviewCleanup} frees their
 * memory through this. A client-only Minecraft class: the mixin config plugin skips it on a dedicated server, and applies
 * it only beside the Compact Machines build F-30 was written for.
 */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("buffer")
    ByteBuffer bsp_mod_fixes$buffer();

    @Accessor("buffer")
    void bsp_mod_fixes$setBuffer(ByteBuffer buffer);
}
