package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewLeak;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewRequests;
import com.mrgregles.bsp_mod_fixes.fix.cm.PreviewRules;
import com.mrgregles.bsp_mod_fixes.mixin.BufferBuilderAccessor;
import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.api.room.RoomSize;
import dev.compactmods.machines.machine.CompactMachineBlockEntity;
import dev.compactmods.machines.room.Rooms;
import dev.compactmods.machines.room.data.CompactRoomData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Dev-only check of the room preview's memory (F-30) on a real client: {@code ./gradlew runClient -PwithCm -PpreviewProbe
 * -PquickPlay=cmtest}. Once the world is open the integrated server places a machine, makes it a room with a chest, a
 * furnace, glass, a full chest and a written sign in it, and has the player right-click the machine with an empty hand
 * {@value #PREVIEWS} times (so each preview comes through F-31 when that fix is on); each preview is
 * left open for a few frames and closed, one of them through a window resize. Before the first and after the last the
 * probe counts the OpenGL objects alive (framebuffers, textures, buffers, vertex arrays), and it counts them again around
 * one preview late in the run (before, open, closed); it logs those with what the fix says it released. With {@code -PskipFixes=CmPreviewCleanupMixin} the same run
 * shows Compact Machines' own behaviour. Lines start "BSP-Mod Fixes preview probe"; never in the jar.
 */
public final class PreviewProbe {
    private static final int PREVIEWS = 30;
    private static final int RESIZE_AT = 15;
    /** The preview whose OpenGL objects are counted just before it opens, while it is open and just after it closes. */
    private static final int SAMPLE = 25;
    private static int[] sample;
    private static int stage;
    private static int wait;
    private static int done;
    private static int openTicks;
    private static volatile BlockPos machinePos;
    private static volatile ChunkPos room;
    private static int[] glBefore;
    private static long bytesPerPreview = -1;

    private PreviewProbe() {}

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(PreviewProbe::tick);
        Fixes.LOGGER.info("BSP-Mod Fixes preview probe: installed, waiting for a world");
    }

    private static boolean isPreview(Minecraft mc) {
        return mc.screen != null && mc.screen.getClass().getName().equals(PreviewLeak.SCREEN);
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.screen != null && !isPreview(mc)) {
            mc.setScreen(null); // a pause or death screen would stop the integrated server
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            step(mc, server);
        } catch (Throwable t) {
            Fixes.LOGGER.error("BSP-Mod Fixes preview probe: FAILED at stage {}", stage, t);
            stage = -1;
            mc.stop();
        }
    }

    private static void step(Minecraft mc, MinecraftServer server) throws Exception {
        switch (stage) {
            case 0 -> {
                wait = 300; // the world's own chunks finish meshing and drawing first, so their buffers are not counted as growth
                stage = 1;
            }
            case 1 -> {
                server.execute(() -> {
                    try {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        ServerLevel level = player.serverLevel();
                        BlockPos pos = player.blockPosition().above(3).north(2);
                        level.setBlockAndUpdate(pos, ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("compactmachines", "machine_normal")).defaultBlockState());
                        ChunkPos made = Rooms.createNew(server, RoomSize.NORMAL, player.getUUID());
                        ((CompactMachineBlockEntity) level.getBlockEntity(pos)).setConnectedRoom(made);
                        ServerLevel rooms = CompactDimension.forServer(server);
                        BlockPos centre = new BlockPos(CompactRoomData.get(rooms).getData(made).getCenter());
                        rooms.setBlockAndUpdate(centre, Blocks.CHEST.defaultBlockState());
                        rooms.setBlockAndUpdate(centre.east(), Blocks.RED_STAINED_GLASS.defaultBlockState());
                        rooms.setBlockAndUpdate(centre.west(), Blocks.FURNACE.defaultBlockState());
                        rooms.setBlockAndUpdate(centre.south(), Blocks.GLASS.defaultBlockState());
                        if (rooms.getBlockEntity(centre) instanceof ChestBlockEntity chest) {
                            chest.setItem(0, new ItemStack(Items.DIAMOND, 64)); // what F-31 keeps on the server
                        }
                        rooms.setBlockAndUpdate(centre.north(), Blocks.OAK_SIGN.defaultBlockState());
                        if (rooms.getBlockEntity(centre.north()) instanceof SignBlockEntity sign) {
                            sign.updateText(text -> text.setMessage(0, Component.literal("BSP")), true); // what a client in the room is sent
                        }
                        machinePos = pos;
                        room = made;
                        Fixes.LOGGER.info("BSP-Mod Fixes preview probe: machine at {} bound to room {}, furnished around {}", pos, made, centre);
                    } catch (Throwable t) {
                        Fixes.LOGGER.error("BSP-Mod Fixes preview probe: FAILED to set the room up", t);
                    }
                });
                wait = 40;
                stage = 2;
            }
            case 2 -> {
                if (room == null) {
                    throw new IllegalStateException("no room was made");
                }
                System.gc();
                glBefore = glCounts();
                Fixes.LOGGER.info("BSP-Mod Fixes preview probe: before: {}", describe(glBefore));
                stage = 3;
            }
            case 3 -> { // ask the server for one preview, as a right click does
                if (done == SAMPLE) {
                    sample = glCounts();
                }
                server.execute(() -> {
                    try {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        ServerLevel level = player.serverLevel();
                        player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
                        PreviewRequests.forget(player.getUUID()); // the probe clicks faster than the interval allows a player
                        level.getBlockState(machinePos).use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(machinePos), Direction.UP, machinePos, false));
                    } catch (Throwable t) {
                        Fixes.LOGGER.error("BSP-Mod Fixes preview probe: FAILED to send a preview", t);
                    }
                });
                openTicks = 0;
                wait = 2;
                stage = 4;
            }
            case 4 -> {
                if (!isPreview(mc)) {
                    if (++openTicks > 200) {
                        throw new IllegalStateException("the preview screen did not open");
                    }
                    return;
                }
                wait = 8; // a few frames drawn
                stage = 5;
            }
            case 5 -> {
                if (done == 0) {
                    bytesPerPreview = sceneBytes(mc.screen);
                    Screenshot.grab(mc.gameDirectory, "preview_open.png", mc.getMainRenderTarget(), c -> {});
                    Fixes.LOGGER.info("BSP-Mod Fixes preview probe: screenshot preview_open.png; one preview's buffer packs hold {} bytes of native memory", bytesPerPreview);
                }
                if (done == SAMPLE) {
                    Screenshot.grab(mc.gameDirectory, "preview_late.png", mc.getMainRenderTarget(), c -> {});
                    int[] open = glCounts();
                    Fixes.LOGGER.info("BSP-Mod Fixes preview probe: one preview, open: +{} framebuffers, +{} textures, +{} buffers, +{} vertex arrays", open[0] - sample[0], open[1] - sample[1], open[2] - sample[2], open[3] - sample[3]);
                }
                if (done == RESIZE_AT) {
                    mc.resizeDisplay(); // the screen rebuilds its widgets, as on a window resize
                    Fixes.LOGGER.info("BSP-Mod Fixes preview probe: preview {} went through a resize", done + 1);
                }
                wait = 3;
                stage = 6;
            }
            case 6 -> {
                mc.setScreen(null);
                if (done == SAMPLE) {
                    int[] closed = glCounts();
                    Fixes.LOGGER.info("BSP-Mod Fixes preview probe: one preview, closed: +{} framebuffers, +{} textures, +{} buffers, +{} vertex arrays left behind", closed[0] - sample[0], closed[1] - sample[1], closed[2] - sample[2], closed[3] - sample[3]);
                }
                done++;
                wait = 3;
                stage = done < PREVIEWS ? 3 : 7;
            }
            case 7 -> {
                wait = 20;
                stage = 8;
            }
            default -> {
                System.gc();
                int[] after = glCounts();
                Fixes.LOGGER.info("BSP-Mod Fixes preview probe: after {} previews: {}", done, describe(after));
                Fixes.LOGGER.info("BSP-Mod Fixes preview probe: grew by {} framebuffers, {} textures, {} buffers, {} vertex arrays; native memory taken by the previews' buffer packs {} MB",
                        after[0] - glBefore[0], after[1] - glBefore[1], after[2] - glBefore[2], after[3] - glBefore[3], bytesPerPreview * done / (1024 * 1024));
                Fixes.LOGGER.info("BSP-Mod Fixes preview probe: the fix released {} scenes, {} framebuffer chains, {} vertex buffers, {} MB of native memory",
                        PreviewLeak.previews(), PreviewLeak.chains(), PreviewLeak.vertexBuffers(), PreviewLeak.nativeBytes() / (1024 * 1024));
                server.execute(() -> server.getPlayerList().getPlayers().get(0).serverLevel().removeBlock(machinePos, false));
                Fixes.LOGGER.info("BSP-Mod Fixes preview probe: the server sent {} previews by its rule, leaving out the saved data of {} block entities", PreviewRules.sent(), PreviewRules.stripped());
                Fixes.LOGGER.info("BSP-Mod Fixes preview probe: done, closing the client");
                stage = -1;
                mc.stop();
            }
        }
    }

    /**
     * Live OpenGL objects by kind: framebuffers, textures, buffers, vertex arrays. Framebuffer and texture names stay small;
     * the world's own chunk sections reserve a buffer and vertex array name per section and layer at world load (most never
     * used), so a preview's are far up the range and the scan goes to 600,000.
     */
    private static int[] glCounts() {
        int[] n = new int[4];
        for (int id = 1; id <= 20_000; id++) {
            if (GL30.glIsFramebuffer(id)) n[0]++;
            if (GL11.glIsTexture(id)) n[1]++;
        }
        for (int id = 1; id <= 600_000; id++) {
            if (GL15.glIsBuffer(id)) n[2]++;
            if (GL30.glIsVertexArray(id)) n[3]++;
        }
        return n;
    }

    private static String describe(int[] n) {
        return n[0] + " framebuffers, " + n[1] + " textures, " + n[2] + " buffers, " + n[3] + " vertex arrays";
    }

    /** The native bytes held by the two buffer packs of the open preview's baked scene. */
    private static long sceneBytes(Object screen) throws Exception {
        Field sceneField = screen.getClass().getDeclaredField("scene");
        sceneField.setAccessible(true);
        Object scene = sceneField.get(screen);
        long bytes = 0;
        for (String name : new String[]{"blockBuilders", "fluidBuilders"}) {
            Field f = scene.getClass().getDeclaredField(name);
            f.setAccessible(true);
            ChunkBufferBuilderPack pack = (ChunkBufferBuilderPack) f.get(scene);
            for (RenderType layer : RenderType.chunkBufferLayers()) {
                ByteBuffer memory = ((BufferBuilderAccessor) pack.builder(layer)).bsp_mod_fixes$buffer();
                bytes += memory == null ? 0 : memory.capacity();
            }
        }
        return bytes;
    }
}
