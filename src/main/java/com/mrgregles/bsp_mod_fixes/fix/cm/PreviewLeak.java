package com.mrgregles.bsp_mod_fixes.fix.cm;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-30, what is known without a client: the room preview of Compact Machines (right click on a machine with an empty hand)
 * never gives back most of what it allocates. Its screen, {@code MachineRoomScreen}, draws the room through the Gander
 * library nested in Compact Machines' jar; on closing, Gander's {@code SpatialRenderer.dispose()} destroys one framebuffer
 * and clears two maps, and that is all. Never released: the renderer's {@code TranslucencyChain} (eight window-sized
 * colour and depth layers, a window-sized copy target and a shader; its {@code close()} has no caller and skips the copy
 * target), the baked scene's {@code VertexBuffer}s (GPU buffers), and its two {@code ChunkBufferBuilderPack}s, whose
 * {@code BufferBuilder}s hold memory taken with {@code malloc} that the Java heap never sees (27 MB a pack: a builder takes six
 * times its nominal size). The
 * screen also builds a new renderer every time the window is resized without disposing the old one. Every preview
 * therefore costs video and native memory for the rest of the session.
 *
 * <p>The work is in {@code client/PreviewCleanup}, reached from {@code CmPreviewCleanupMixin}; this class holds what the
 * startup report, the gate's test and the counters need, and names no client class. The members below are reached by
 * reflection (Gander is not on the compile classpath), so a build that renamed one costs the fix, with one WARN, and
 * nothing else.
 */
public final class PreviewLeak {
    public static final String ID = "F-30";
    public static final String WHAT = "the room preview releases its framebuffers, vertex buffers and native buffer memory when it closes or is resized";
    public static final String TARGET = CmGate.TARGET;

    public static final String SCREEN = "dev.compactmods.machines.room.client.MachineRoomScreen";
    public static final String RENDERER = "dev.compactmods.gander.ui.widget.SpatialRenderer";
    public static final String CHAIN = "dev.compactmods.gander.render.translucency.TranslucencyChain";
    public static final String SCENE = "dev.compactmods.gander.render.baked.BakedLevel";
    /** The fields read by name, per class. */
    public static final Map<String, Set<String>> FIELDS = Map.of(
            SCREEN, Set.of("scene", "renderer"),
            RENDERER, Set.of("translucencyChain"),
            CHAIN, Set.of("intermediaryCopyTarget"),
            SCENE, Set.of("blockBuilders", "fluidBuilders", "blockBuffers", "fluidBuffers"));
    /** The methods called by name (no arguments), per class. */
    public static final Map<String, Set<String>> METHODS = Map.of(
            RENDERER, Set.of("dispose"),
            CHAIN, Set.of("close"));

    private static final LongAdder PREVIEWS = new LongAdder();
    private static final LongAdder CHAINS = new LongAdder();
    private static final LongAdder VERTEX_BUFFERS = new LongAdder();
    private static final LongAdder NATIVE_BYTES = new LongAdder();

    private PreviewLeak() {}

    public static void countPreview() {
        PREVIEWS.increment();
    }

    public static void countChain() {
        CHAINS.increment();
    }

    public static void countVertexBuffer() {
        VERTEX_BUFFERS.increment();
    }

    public static void countNativeBytes(long bytes) {
        NATIVE_BYTES.add(bytes);
    }

    /** Preview scenes released so far. */
    public static long previews() {
        return PREVIEWS.sum();
    }

    /** Framebuffer chains released so far (one per renderer: one per preview, one more per window resize while open). */
    public static long chains() {
        return CHAINS.sum();
    }

    /** GPU vertex buffers closed so far. */
    public static long vertexBuffers() {
        return VERTEX_BUFFERS.sum();
    }

    /** Native bytes handed back so far. */
    public static long nativeBytes() {
        return NATIVE_BYTES.sum();
    }
}
