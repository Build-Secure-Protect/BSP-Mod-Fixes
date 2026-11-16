package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.mffs.FieldSync;
import com.mrgregles.bsp_mod_fixes.fix.mffs.MffsGate;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * MF-33: a force field block's data travels in the chunk packet (F-36). The rule and the gate are pure; with {@code -PwithMffs}
 * a real powered projector with the cube mode, three Scale modules and 64 Glow modules builds its 218-block field, one block
 * is camouflaged by hand, and the chunk packet data of the field's chunks is built as the server builds it for a player: every
 * field block entity's entry carries its projector, its light of 15 and, where set, its camouflage; the block entity applies
 * such a tag through MFFS's own handler. With {@code -PskipFixes=MffsForceFieldSyncMixin} the same entries carry nothing, as
 * MFFS ships. The client's held {@code onLoad} and its replay are tested on counting block entities; the whole client half (no
 * request sent, the blocks lit and camouflaged from the chunk alone) is seen on a dev client with {@code -PfieldProbe}, since a
 * dedicated server has no client to show it.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class MffsFieldSyncGameTests {
    /** A cube of scale radius 3: 7^3 - 5^3 positions on its shell. */
    static final int SHELL = 218;
    /** 64 Glow modules: MFFS rounds min(count, 64) / 64 x 15. */
    static final int GLOW_LIGHT = 15;

    /** A block entity whose onLoad counts its calls; {@code synced} says whether a chunk packet's tag has reached it. */
    static final class Counting extends ChestBlockEntity implements FieldSync.Synced {
        int loads;
        boolean synced;

        Counting(BlockPos pos) {
            super(pos, Blocks.CHEST.defaultBlockState());
        }

        @Override
        public void onLoad() {
            loads++;
        }

        @Override
        public boolean bsp_mod_fixes$isSyncedFromChunk() {
            return synced;
        }
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_field_block_asks_only_when_its_chunk_brought_nothing_and_the_gate_holds(GameTestHelper h) {
        h.assertTrue(!FieldSync.holdsOnLoad(false, true, false), "the server's onLoad is MFFS's");
        h.assertTrue(!FieldSync.holdsOnLoad(true, false, false), "with the rule off the client's onLoad is MFFS's");
        h.assertTrue(!FieldSync.holdsOnLoad(true, true, true), "the replay runs MFFS's onLoad");
        h.assertTrue(FieldSync.holdsOnLoad(true, true, false), "a client block entity's onLoad is held until the tick's end");
        // The replay: a held block entity whose chunk brought nothing runs MFFS's onLoad once; one whose tag arrived is left alone
        // and counted; one removed meanwhile is dropped. (A server level counts as live; on a client only the current level does.)
        ServerLevel level = h.getLevel();
        Counting unsynced = new Counting(h.absolutePos(new BlockPos(1, 2, 1)));
        Counting synced = new Counting(h.absolutePos(new BlockPos(2, 2, 1)));
        Counting removed = new Counting(h.absolutePos(new BlockPos(3, 2, 1)));
        for (Counting c : List.of(unsynced, synced, removed)) {
            c.setLevel(level);
            FieldSync.defer(c);
        }
        synced.synced = true;
        removed.setRemoved();
        long skippedBefore = FieldSync.requestsSkipped();
        h.assertTrue(FieldSync.deferred() == 3 && !FieldSync.replaying(), "three held, nothing replaying");
        int ran = FieldSync.replayDeferred();
        h.assertTrue(ran == 1 && unsynced.loads == 1 && synced.loads == 0 && removed.loads == 0, "only the unsynced one ran MFFS's onLoad: " + ran + " ran, loads " + unsynced.loads + "/" + synced.loads + "/" + removed.loads);
        h.assertTrue(FieldSync.requestsSkipped() == skippedBefore + 1, "the synced one counts as a request skipped");
        h.assertTrue(FieldSync.deferred() == 0 && !FieldSync.replaying(), "the queue is empty and the replay flag is down");
        // The light checks: positions marked as landed are checked at the next tick's end, once each, and the queue empties.
        long checksBefore = FieldSync.lightChecks();
        FieldSync.lightLanded(level, List.of(unsynced.getBlockPos(), synced.getBlockPos()));
        h.assertTrue(FieldSync.landed() == 1, "one landed marker waits");
        h.assertTrue(FieldSync.checkLanded() == 2 && FieldSync.lightChecks() == checksBefore + 2 && FieldSync.landed() == 0, "both positions were checked and counted, and the queue emptied");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_SYNC_FIELD_BLOCKS.get(), "default: on");
        h.assertTrue(FieldSync.enabled(), "the rule reads the switch");
        h.assertTrue(MffsGate.gates("MffsForceFieldSyncMixin"), "the gate decides for the sync mixin");
        Map<String, String> methods = MffsGate.MIXINS.get("MffsForceFieldSyncMixin").methods();
        h.assertTrue(methods.containsKey("getCustomUpdateTag") && methods.containsKey("handleCustomUpdateTag") && methods.containsKey("onLoad"), "the gate checks the three patched methods");
        h.succeed();
    }

    // Builds a real field: a batch of its own.
    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 4000, batch = "mffs_field_sync")
    public void a_real_fields_chunk_packet_carries_every_field_blocks_projector_camouflage_and_light(GameTestHelper h) {
        if (!ModList.get().isLoaded(MffsGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: MFFS is not loaded, no projector is placed (add -PwithMffs)");
            h.succeed();
            return;
        }
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(FieldSync.ID));
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(12, 4, 12));
        level.setBlockAndUpdate(center, MffsTestBridge.projector());
        BlockEntity projector = level.getBlockEntity(center);
        h.assertTrue(projector != null, "the projector's block entity exists");
        MffsTestBridge.setCubeOfRadius(projector, 3);
        MffsTestBridge.setGlowModules(projector, 64);
        MffsTestBridge.power(projector);
        // The build runs on a background thread while this server ticks hundreds of times a second: ~1,200 ticks for 218 blocks.
        h.runAfterDelay(1200, () -> {
            List<BlockPos> shell = new ArrayList<>();
            for (BlockPos p : shellPositions(center)) {
                if (MffsTestBridge.isForceField(level.getBlockState(p))) {
                    shell.add(p);
                }
            }
            h.assertTrue(shell.size() == SHELL, "the powered projector built its whole shell: " + shell.size() + " of " + SHELL);
            BlockPos camouflaged = shell.get(0);
            MffsTestBridge.setCamouflage(Objects.requireNonNull(level.getBlockEntity(camouflaged)), Blocks.STONE.defaultBlockState());

            // The chunk packet data of every chunk the shell touches, built as ChunkMap builds it for a player, and read back
            // as the client reads it: one entry per block entity, with its update tag or nothing.
            Set<ChunkPos> chunks = new LinkedHashSet<>();
            for (BlockPos p : shell) {
                chunks.add(new ChunkPos(p));
            }
            long writtenBefore = FieldSync.chunkTagsWritten();
            Map<BlockPos, CompoundTag> entries = new HashMap<>();
            long nanos = 0;
            long bytes = 0;
            for (ChunkPos cp : chunks) {
                LevelChunk chunk = level.getChunk(cp.x, cp.z);
                long t0 = System.nanoTime();
                ClientboundLevelChunkPacketData data = new ClientboundLevelChunkPacketData(chunk);
                nanos += System.nanoTime() - t0;
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                data.write(buf);
                bytes += buf.readableBytes();
                buf.release();
                data.getBlockEntitiesTagsConsumer(cp.x, cp.z).accept((pos, type, tag) -> {
                    if (type == MffsTestBridge.forceFieldType()) {
                        entries.put(pos.immutable(), tag);
                    }
                });
            }
            Fixes.LOGGER.info("BSP-Mod Fixes test: the chunk packet data of {} chunk(s) holding {} field blocks: {} bytes, built in {} ms, fix {}",
                    chunks.size(), SHELL, bytes, String.format("%.2f", nanos / 1_000_000.0), fixOn ? "on" : "off");
            h.assertTrue(entries.size() == SHELL, "every field block entity has an entry in its chunk packet: " + entries.size());
            long carrying = entries.values().stream().filter(t -> t != null && t.contains("projector") && t.contains("clientBlockLight")).count();
            if (!fixOn) {
                h.assertTrue(carrying == 0 && entries.values().stream().allMatch(Objects::isNull), "as MFFS ships, the chunk packet carries nothing for a field block (" + carrying + " carried data)");
                h.assertTrue(FieldSync.chunkTagsWritten() == writtenBefore, "and the fix counted nothing");
            } else {
                h.assertTrue(carrying == SHELL, "every entry carries the projector and the light: " + carrying + " of " + SHELL);
                h.assertTrue(entries.values().stream().allMatch(t -> t.getInt("clientBlockLight") == GLOW_LIGHT), "the Glow modules' light rides along as " + GLOW_LIGHT);
                h.assertTrue(entries.values().stream().allMatch(t -> NbtUtils.readBlockPos(t.getCompound("projector")).equals(center)), "the projector's position rides along");
                h.assertTrue(entries.get(camouflaged).contains("camouflage"), "the camouflaged block's entry carries its camouflage");
                long others = entries.entrySet().stream().filter(e -> !e.getKey().equals(camouflaged) && e.getValue().contains("camouflage")).count();
                h.assertTrue(others == 0, "and no other does: " + others);
                h.assertTrue(FieldSync.chunkTagsWritten() >= writtenBefore + SHELL, "the fix counted a tag per field block");
            }

            // The tag applied as the client applies it: with the fix the block entity takes the light from the tag (MFFS's own
            // handler); as MFFS ships, handleUpdateTag is vanilla's load, which knows nothing of the light. The sample must be a
            // block without camouflage (shell.get(0) is the camouflaged one): with a camouflage MFFS's handler reaches its
            // client-only render delegate, which this dedicated server cannot load.
            BlockEntity sample = Objects.requireNonNull(level.getBlockEntity(shell.get(1)));
            CompoundTag tag = MffsTestBridge.customUpdateTag(sample);
            tag.putInt("clientBlockLight", 7);
            sample.handleUpdateTag(tag);
            h.assertTrue(MffsTestBridge.clientBlockLight(sample) == (fixOn ? 7 : 0), "the chunk's tag is applied through MFFS's own handler when the fix is on: light " + MffsTestBridge.clientBlockLight(sample));

            // Unpowered, MFFS tears the field down itself; then the projector goes.
            MffsTestBridge.unpower(projector);
            h.runAfterDelay(60, () -> {
                int left = 0;
                for (BlockPos p : shellPositions(center)) {
                    if (MffsTestBridge.isForceField(level.getBlockState(p))) {
                        left++;
                    }
                }
                h.assertTrue(left == 0, "MFFS's own teardown cleared the field: " + left + " left");
                level.removeBlock(center, false);
                h.succeed();
            });
        });
    }

    /** Every position on the shell of the radius-3 cube around the centre. */
    static List<BlockPos> shellPositions(BlockPos center) {
        List<BlockPos> shell = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == 3) {
                        shell.add(center.offset(x, y, z));
                    }
                }
            }
        }
        return shell;
    }
}
