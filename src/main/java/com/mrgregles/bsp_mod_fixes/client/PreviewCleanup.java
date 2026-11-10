package com.mrgregles.bsp_mod_fixes.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mrgregles.bsp_mod_fixes.FixesClientConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewLeak;
import com.mrgregles.bsp_mod_fixes.mixin.BufferBuilderAccessor;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * F-30, the work, on the client's render thread: releases what Compact Machines' room preview allocates ({@link PreviewLeak}
 * says what and why). {@link #beforeInit} runs before the screen (re)builds its widgets: a renderer left from an earlier
 * build, which Compact Machines drops on a window resize without disposing, is disposed in full. {@link #afterRemoved} runs
 * after the screen's own closing code: the renderer is disposed in full and the baked scene's GPU and native buffers are
 * released, then the screen's reference to the scene is cleared, so nothing can draw or free it a second time.
 *
 * <p>Gander's and the screen's private members are read by reflection, resolved once; if any is missing the fix switches
 * itself off with one WARN and the preview behaves as Compact Machines ships it. Client only: named from the mixin alone.
 */
public final class PreviewCleanup {
    private static final Set<Object> CHAIN_RELEASED = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile Handles handles;
    private static volatile boolean broken;

    private record Handles(Field screenScene, Field screenRenderer, Field rendererChain, Field chainCopyTarget, Method rendererDispose,
                           Field sceneBlockBuilders, Field sceneFluidBuilders, Field sceneBlockBuffers, Field sceneFluidBuffers) {}

    private PreviewCleanup() {}

    /** Whether the cleanup runs: the client config's switch (on by default), and the members found. */
    public static boolean enabled() {
        return !broken && FixesClientConfig.freePreviewMemory();
    }

    private static Handles handles() throws ReflectiveOperationException {
        Handles h = handles;
        if (h == null) {
            ClassLoader loader = PreviewCleanup.class.getClassLoader();
            Class<?> screen = Class.forName(PreviewLeak.SCREEN, false, loader);
            Class<?> renderer = Class.forName(PreviewLeak.RENDERER, false, loader);
            Class<?> chain = Class.forName(PreviewLeak.CHAIN, false, loader);
            Class<?> scene = Class.forName(PreviewLeak.SCENE, false, loader);
            Method dispose = renderer.getMethod("dispose");
            h = new Handles(field(screen, "scene"), field(screen, "renderer"), field(renderer, "translucencyChain"), field(chain, "intermediaryCopyTarget"), dispose,
                    field(scene, "blockBuilders"), field(scene, "fluidBuilders"), field(scene, "blockBuffers"), field(scene, "fluidBuffers"));
            handles = h;
        }
        return h;
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Before the preview screen builds its widgets: the renderer of an earlier build (a window resize) is released. */
    public static void beforeInit(Object screen) {
        run(() -> {
            Object renderer = handles().screenRenderer().get(screen);
            if (renderer != null) {
                releaseRenderer(renderer);
            }
        });
    }

    /** After the preview screen's own closing code: its renderer and its baked scene are released. */
    public static void afterRemoved(Object screen) {
        run(() -> {
            Handles h = handles();
            Object renderer = h.screenRenderer().get(screen);
            if (renderer != null) {
                releaseRenderer(renderer);
            }
            Object scene = h.screenScene().get(screen);
            if (scene != null) {
                h.screenScene().set(screen, null); // first, so a failure below cannot lead to a second release
                releaseScene(scene);
            }
        });
    }

    private interface Work {
        void run() throws Exception;
    }

    private static void run(Work work) {
        if (!enabled()) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> run(work));
            return;
        }
        try {
            work.run();
        } catch (Throwable t) {
            broken = true;
            Fixes.LOGGER.warn("F-30: could not release the room preview's memory ({}); the fix is off for this session and the preview behaves as Compact Machines ships it", t.toString());
        }
    }

    /** Gander's own dispose (one framebuffer, idempotent), then what it leaves: the framebuffer chain, its copy target, its shader. */
    private static void releaseRenderer(Object renderer) throws Exception {
        Handles h = handles();
        h.rendererDispose().invoke(renderer);
        if (CHAIN_RELEASED.add(renderer)) {
            Object chain = h.rendererChain().get(renderer);
            if (chain != null) {
                ((AutoCloseable) chain).close();
                if (h.chainCopyTarget().get(chain) instanceof RenderTarget copy) {
                    copy.destroyBuffers();
                }
                PreviewLeak.countChain();
            }
        }
    }

    /** The baked scene: its GPU vertex buffers and the native memory of its two buffer packs. */
    private static void releaseScene(Object scene) throws Exception {
        Handles h = handles();
        closeAll(h.sceneBlockBuffers().get(scene));
        closeAll(h.sceneFluidBuffers().get(scene));
        free(h.sceneBlockBuilders().get(scene));
        free(h.sceneFluidBuilders().get(scene));
        PreviewLeak.countPreview();
    }

    private static void closeAll(Object buffers) {
        if (buffers instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value instanceof VertexBuffer vb && !vb.isInvalid()) {
                    vb.close();
                    PreviewLeak.countVertexBuffer();
                }
            }
        }
    }

    /** Hands a pack's builders' native memory back to the allocator vanilla took it from, and leaves each builder without a buffer. */
    private static void free(Object pack) {
        if (pack instanceof ChunkBufferBuilderPack builders) {
            for (RenderType layer : RenderType.chunkBufferLayers()) {
                BufferBuilder builder = builders.builder(layer);
                BufferBuilderAccessor access = (BufferBuilderAccessor) builder;
                ByteBuffer memory = access.bsp_mod_fixes$buffer();
                if (memory != null) {
                    access.bsp_mod_fixes$setBuffer(null);
                    PreviewLeak.countNativeBytes(memory.capacity());
                    MemoryUtil.getAllocator(false).free(MemoryUtil.memAddress0(memory));
                }
            }
        }
    }
}
