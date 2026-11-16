package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.mffs.FieldSync;
import dev.su5ed.mffs.api.ForceFieldBlock;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dev-only check of F-36 on a real client: {@code ./gradlew runClient -PwithMffs -PfieldProbe -PquickPlay=emitest}. Once the
 * world is open the integrated server places a powered projector 24 blocks north of and above the player with the cube mode, six Scale
 * modules and 64 Glow modules (a shell of {@value #SHELL} field blocks lit at 15), waits for MFFS to build it, camouflages every
 * field block as stone, then teleports the player far away until the field's chunks have left the client and brings them back.
 * While the chunks arrive again it counts the packets the client sends on MFFS's channel (the data request MFFS sends per field
 * block, by its message index) and the ticks until every field block on the client has its light and camouflage, then checks
 * the client's light engine and model data at every shell block and logs the figures with the fix's own counters; then it does
 * the same again walking in, one chunk every 20 ticks from just outside render distance, so the field's chunks arrive one or two
 * at a time with their light close behind (the case a burst hides). With
 * {@code -PskipFixes=MffsForceFieldSyncMixin} the same run shows MFFS's own behaviour. Lines start "BSP-Mod Fixes field probe";
 * never in the jar.
 */
public final class FieldSyncProbe {
    private static final int RADIUS = 6;
    /** (2r+1)^3 - (2r-1)^3 for r = 6. */
    private static final int SHELL = 866;
    private static final ResourceLocation MFFS_CHANNEL = ResourceLocation.fromNamespaceAndPath("mffs", "main");
    /** InitialDataRequestPacket is the fifth message MFFS registers on its channel. */
    private static final int REQUEST_INDEX = 4;
    private static final int AWAY = 3000;

    private static int stage;
    private static int wait;
    private static int ticks;
    private static int loadTicks;
    private static int completeAt = -1;
    private static volatile BlockPos projectorPos;
    private static volatile Vec3 home;
    private static volatile int built = -1;
    private static volatile boolean serverStepDone;
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final AtomicInteger MFFS_PACKETS = new AtomicInteger();

    private FieldSyncProbe() {}

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(FieldSyncProbe::tick);
        Fixes.LOGGER.info("BSP-Mod Fixes field probe: installed, waiting for a world");
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
        if (mc.screen != null) {
            mc.setScreen(null); // a pause or death screen would stop the integrated server
            return;
        }
        ticks++;
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            step(mc, server);
        } catch (Throwable t) {
            Fixes.LOGGER.error("BSP-Mod Fixes field probe: FAILED at stage {}", stage, t);
            stage = -1;
            cleanUp(server);
            mc.stop();
        }
    }

    private static void step(Minecraft mc, MinecraftServer server) {
        switch (stage) {
            case 0 -> {
                wait = 100; // the world settles
                stage = 1;
            }
            case 1 -> { // count what the client sends on MFFS's channel from now on
                mc.getConnection().getConnection().channel().pipeline().addFirst("bsp_mod_fixes_field_probe", new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof ServerboundCustomPayloadPacket packet && MFFS_CHANNEL.equals(packet.getIdentifier())) {
                            MFFS_PACKETS.incrementAndGet();
                            FriendlyByteBuf data = packet.getData();
                            if (data.readableBytes() > 0 && data.getByte(data.readerIndex()) == REQUEST_INDEX) {
                                REQUESTS.incrementAndGet();
                            }
                        }
                        super.write(ctx, msg, promise);
                    }
                });
                server.execute(() -> {
                    try {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        ServerLevel level = player.serverLevel();
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        home = player.position();
                        cleanYard(level, player.blockPosition()); // what an earlier run may have left in this save
                        BlockPos pos = player.blockPosition().offset(0, 24, 24); // in the air: terrain would block part of the shell
                        level.setBlockAndUpdate(pos, MffsTestBridge.projector());
                        BlockEntity projector = level.getBlockEntity(pos);
                        MffsTestBridge.setCubeOfRadius(projector, RADIUS);
                        MffsTestBridge.setGlowModules(projector, 64);
                        MffsTestBridge.power(projector);
                        projectorPos = pos;
                        Fixes.LOGGER.info("BSP-Mod Fixes field probe: projector at {}, cube of radius {} ({} field blocks), 64 Glow modules; building", pos, RADIUS, SHELL);
                    } catch (Throwable t) {
                        Fixes.LOGGER.error("BSP-Mod Fixes field probe: FAILED to place the projector", t);
                    }
                });
                wait = 100;
                stage = 2;
            }
            case 2 -> { // MFFS builds the field at its own pace
                if (projectorPos == null) {
                    throw new IllegalStateException("no projector was placed");
                }
                server.execute(() -> built = countShell(server.getPlayerList().getPlayers().get(0).serverLevel(), projectorPos));
                if (built == SHELL) {
                    Fixes.LOGGER.info("BSP-Mod Fixes field probe: the field is built ({} blocks) after {} ticks", built, ticks);
                    stage = 3;
                } else if (ticks > 6000) {
                    throw new IllegalStateException("the field did not finish: " + built + " of " + SHELL);
                } else {
                    wait = 20;
                }
            }
            case 3 -> { // every field block camouflaged on the server, so the chunk (or MFFS's reply) has something to carry
                serverStepDone = false;
                server.execute(() -> {
                    ServerLevel level = server.getPlayerList().getPlayers().get(0).serverLevel();
                    int done = 0;
                    for (BlockPos p : shell(projectorPos)) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be != null && MffsTestBridge.isForceFieldEntity(be)) {
                            MffsTestBridge.setCamouflage(be, Blocks.STONE.defaultBlockState());
                            done++;
                        }
                    }
                    Fixes.LOGGER.info("BSP-Mod Fixes field probe: {} field blocks camouflaged as stone on the server", done);
                    serverStepDone = true;
                });
                wait = 20;
                stage = 4;
            }
            case 4 -> { // away, until the field's chunks have left the client
                if (!serverStepDone) {
                    wait = 5;
                    return;
                }
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), home.x + AWAY, home.y + 20, home.z, Set.of(), player.getYRot(), player.getXRot());
                });
                wait = 100;
                stage = 5;
            }
            case 5 -> {
                ChunkPos fieldChunk = new ChunkPos(projectorPos);
                if (mc.level.getChunkSource().hasChunk(fieldChunk.x, fieldChunk.z)) {
                    if (ticks > 12000) {
                        throw new IllegalStateException("the field's chunk never left the client");
                    }
                    wait = 20;
                    return;
                }
                // The chunks are out of the client's range but their objects linger in its chunk array (the view centre moved before
                // the forget packets came, so the client never dropped them); rebuilding the array at the same radius keeps only the
                // chunks in range, so the field's blocks come back through chunk packets and not from stale objects.
                mc.level.getChunkSource().updateViewRadius(mc.options.getEffectiveRenderDistance());
                Fixes.LOGGER.info("BSP-Mod Fixes field probe: the field's chunks have left the client and the stale chunk objects are dropped; {} packets on MFFS's channel so far ({} data requests, the blocks placed in view), counters reset", MFFS_PACKETS.get(), REQUESTS.get());
                MFFS_PACKETS.set(0);
                REQUESTS.set(0);
                loadTicks = 0;
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot());
                });
                stage = 6;
            }
            case 6 -> { // back: the chunks arrive again; wait until every shell block on the client has its light and camouflage
                loadTicks++;
                int ready = clientReady(mc);
                if (loadTicks == 1) {
                    Fixes.LOGGER.info("BSP-Mod Fixes field probe: one tick after the teleport back the client has {} of {} field blocks", ready, SHELL);
                }
                if (ready == SHELL && completeAt < 0) {
                    completeAt = loadTicks;
                    Fixes.LOGGER.info("BSP-Mod Fixes field probe: every field block on the client has its light and camouflage {} ticks after the teleport back", completeAt);
                    wait = 100; // late requests and replies, if any
                    stage = 7;
                } else if (loadTicks > 1200) {
                    Fixes.LOGGER.warn("BSP-Mod Fixes field probe: after {} ticks only {} of {} field blocks on the client have their light and camouflage", loadTicks, ready, SHELL);
                    stage = 7;
                }
            }
            case 7 -> { // the teleport reload: everything arrives in one burst
                report(mc, "TELEPORT (all chunks in one burst)");
                // The walk-in: away again, just past render distance, then one chunk a step towards the field, so its chunks
                // arrive one or two at a time, as they do for a player walking up to a field.
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), home.x + AWAY, home.y + 20, home.z, Set.of(), player.getYRot(), player.getXRot());
                });
                wait = 100;
                stage = 8;
            }
            case 8 -> {
                ChunkPos fieldChunk = new ChunkPos(projectorPos);
                if (mc.level.getChunkSource().hasChunk(fieldChunk.x, fieldChunk.z)) {
                    wait = 20;
                    return;
                }
                mc.level.getChunkSource().updateViewRadius(mc.options.getEffectiveRenderDistance());
                walkStep = mc.options.getEffectiveRenderDistance() + 4; // chunks from home, outside the view
                MFFS_PACKETS.set(0);
                REQUESTS.set(0);
                loadTicks = 0;
                completeAt = -1;
                Fixes.LOGGER.info("BSP-Mod Fixes field probe: walk-in from {} chunks out, one chunk every 20 ticks", walkStep);
                stage = 9;
            }
            case 9 -> { // one chunk closer every 20 ticks until home
                loadTicks += 20;
                if (completeAt < 0 && clientReady(mc) == SHELL) {
                    completeAt = loadTicks; // every block lit and camouflaged this far into the walk
                }
                int step = walkStep;
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), home.x + step * 16.0, home.y + 20, home.z, Set.of(), player.getYRot(), player.getXRot());
                });
                if (walkStep == 0) {
                    stage = 10;
                    wait = 200; // the last chunks, their light, the queued checks
                    if (completeAt < 0 && clientReady(mc) == SHELL) {
                        completeAt = loadTicks;
                    }
                    return;
                }
                walkStep--;
                wait = 20;
            }
            default -> {
                report(mc, "WALK-IN (one chunk a step)");
                cleanUp(server);
                Fixes.LOGGER.info("BSP-Mod Fixes field probe: done, closing the client");
                stage = -1;
                wait = 40;
                mc.stop();
            }
        }
    }

    /** The projector and its field removed from the save, on the server thread, whatever stage the probe reached. */
    private static void cleanUp(MinecraftServer server) {
        BlockPos at = projectorPos;
        if (at == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.getPlayerList().getPlayers().get(0).serverLevel();
            BlockEntity projector = level.getBlockEntity(at);
            if (projector != null && !MffsTestBridge.isForceFieldEntity(projector)) {
                MffsTestBridge.unpower(projector);
                MffsTestBridge.destroyField(projector);
            }
            level.removeBlock(at, false);
            for (BlockPos p : shell(at)) {
                if (MffsTestBridge.isForceField(level.getBlockState(p))) {
                    level.removeBlock(p, false);
                }
            }
        });
    }

    /** Any MFFS projector or force field block within 40 blocks of the player goes: a run that failed may have left its field. */
    private static void cleanYard(ServerLevel level, BlockPos around) {
        int removed = 0;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-40, -40, -40), around.offset(40, 40, 40))) {
            if (MffsTestBridge.isForceField(level.getBlockState(p)) || level.getBlockState(p).is(MffsTestBridge.projector().getBlock())) {
                level.removeBlock(p, false);
                removed++;
            }
        }
        if (removed > 0) {
            Fixes.LOGGER.info("BSP-Mod Fixes field probe: {} projector or field blocks of an earlier run removed first", removed);
        }
    }

    private static int walkStep;

    /** What the client holds for the field after a reload, and what it sent on the way. */
    private static void report(Minecraft mc, String phase) {
        int lit = 0;
        int modelData = 0;
        int camouflaged = 0;
        int entities = 0;
        for (BlockPos p : shell(projectorPos)) {
            BlockEntity be = mc.level.getBlockEntity(p);
            if (be == null || !MffsTestBridge.isForceFieldEntity(be)) {
                continue;
            }
            entities++;
            if (MffsTestBridge.camouflage(be) != null) {
                camouflaged++;
            }
            if (mc.level.getBrightness(LightLayer.BLOCK, p) == 15) {
                lit++;
            }
            if (mc.level.getModelDataManager().getAt(p) != null && mc.level.getModelDataManager().getAt(p).get(ForceFieldBlock.CAMOUFLAGE_BLOCK) != null) {
                modelData++;
            }
        }
        Fixes.LOGGER.info("BSP-Mod Fixes field probe: RESULT {}: {} data requests sent by the client ({} packets on MFFS's channel) while {} field blocks loaded; complete after {} ticks; on the client {} block entities, {} camouflaged, {} lit at 15 by the light engine, {} with camouflage in their model data",
                phase, REQUESTS.get(), MFFS_PACKETS.get(), SHELL, completeAt < 0 ? "never (" + loadTicks + " waited)" : String.valueOf(completeAt), entities, camouflaged, lit, modelData);
        Fixes.LOGGER.info("BSP-Mod Fixes field probe: the fix's counters so far: {} tags written into chunk packets (server), {} applied from chunk packets (client), {} requests skipped (client), {} light checks made after the chunks' light landed (client, {} still waiting); fixes active: {}",
                FieldSync.chunkTagsWritten(), FieldSync.chunkTagsApplied(), FieldSync.requestsSkipped(), FieldSync.lightChecks(), FieldSync.landed(), Fixes.active().stream().map(Fixes.Fix::id).toList());
    }

    /** Shell blocks on the client whose block entity has both the Glow modules' light and the camouflage. */
    private static int clientReady(Minecraft mc) {
        int n = 0;
        for (BlockPos p : shell(projectorPos)) {
            BlockEntity be = mc.level.getBlockEntity(p);
            if (be != null && MffsTestBridge.isForceFieldEntity(be) && MffsTestBridge.clientBlockLight(be) == 15 && MffsTestBridge.camouflage(be) != null) {
                n++;
            }
        }
        return n;
    }

    private static int countShell(ServerLevel level, BlockPos center) {
        int n = 0;
        for (BlockPos p : shell(center)) {
            if (MffsTestBridge.isForceField(level.getBlockState(p))) {
                n++;
            }
        }
        return n;
    }

    private static List<BlockPos> shell(BlockPos center) {
        List<BlockPos> shell = new ArrayList<>(SHELL);
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == RADIUS) {
                        shell.add(center.offset(x, y, z));
                    }
                }
            }
        }
        return shell;
    }
}
