package com.mrgregles.bsp_mod_fixes.fix.cm;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.machine.CompactMachineBlockEntity;
import dev.compactmods.machines.room.Rooms;
import dev.compactmods.machines.room.data.CompactRoomData;
import dev.compactmods.machines.room.network.OpenMachinePreviewScreenPacket;
import dev.compactmods.machines.room.network.RoomNetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * F-31, the work: answers a room preview request by {@link PreviewRules}, with Compact Machines' own capture and its own
 * packet. What changes on the way is the block entity data: Compact Machines sends every block entity's full save; here
 * each is replaced by its update tag, which is what the server sends a client that can see the block, and left out when
 * that tag is empty (a chest, a furnace: the client builds the block entity fresh) or cannot be read back. The capture is
 * measured as it will be written and refused over the limit. Names Compact Machines' classes; touched from
 * {@code CmPreviewRequestMixin} alone, so only beside the build it was written for.
 */
public final class PreviewRequests {
    public static final String MSG_OFF = "message.bsp_mod_fixes.preview_off";
    public static final String MSG_TOO_LARGE = "message.bsp_mod_fixes.preview_too_large";
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private PreviewRequests() {}

    private static boolean loaded() {
        return FixesConfig.SPEC.isLoaded();
    }

    /**
     * A player right-clicked a machine with an empty hand, on the server. Returns false when Compact Machines is to answer
     * (the rule off); true when the request has been dealt with here.
     */
    public static synchronized boolean handle(ServerLevel level, BlockPos pos, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();
        long last = LAST.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        PreviewRules.Decision decision = PreviewRules.decide(loaded() && FixesConfig.CM_HARDEN_PREVIEW.get(), !loaded() || FixesConfig.CM_PREVIEWS.get(),
                now, last, loaded() ? FixesConfig.CM_PREVIEW_INTERVAL_SECONDS.get() * 20L : 20L);
        PreviewRules.count(decision);
        switch (decision) {
            case LEAVE -> {
                return false;
            }
            case OFF -> player.displayClientMessage(Component.translatable(MSG_OFF), true);
            case TOO_SOON -> { }
            case CAPTURE -> {
                if (level.getBlockEntity(pos) instanceof CompactMachineBlockEntity machine) {
                    machine.getConnectedRoom().ifPresent(room -> {
                        if (LAST.size() > 512) {
                            LAST.clear(); // a throttle, not a record: forgetting everyone costs each one early preview
                        }
                        LAST.put(player.getUUID(), now);
                        send(server, level, pos, player, room);
                    });
                }
            }
        }
        return true;
    }

    private static void send(MinecraftServer server, ServerLevel level, BlockPos pos, ServerPlayer player, ChunkPos room) {
        try {
            ServerLevel rooms = CompactDimension.forServer(server);
            StructureTemplate captured = Rooms.getInternalBlocks(server, room);
            CompoundTag tag = captured.save(new CompoundTag());
            if (FixesConfig.CM_PREVIEW_HIDES_CONTENTS.get()) {
                AABB bounds = CompactRoomData.get(rooms).getData(room).getRoomBounds();
                BlockPos origin = new BlockPos((int) bounds.minX, (int) bounds.minY - 1, (int) bounds.minZ); // where Compact Machines captures from
                PreviewRules.countStripped(asAClientWouldSeeThem(tag, rooms, origin));
            }
            DataOutputStream counter = PreviewRules.counter();
            NbtIo.write(tag, counter);
            long max = FixesConfig.CM_PREVIEW_MAX_KILOBYTES.get() * 1000L;
            if (!PreviewRules.fits(counter.size(), max)) {
                PreviewRules.countTooLarge();
                player.displayClientMessage(Component.translatable(MSG_TOO_LARGE), true);
                Fixes.warnThrottled(PreviewRules.ID + ":" + player.getGameProfile().getName(), "F-31: the preview of room {} asked for by {} is {} bytes, over the limit of {}; not sent",
                        room, player.getGameProfile().getName(), counter.size(), max);
                return;
            }
            RoomNetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new OpenMachinePreviewScreenPacket(GlobalPos.of(level.dimension(), pos), room, new Prepared(tag)));
            PreviewRules.countSent();
        } catch (Throwable e) { // Compact Machines' own two exceptions extend Throwable directly
            if (e instanceof Error error) {
                throw error;
            }
            Fixes.warnThrottled(PreviewRules.ID + ":error", "F-31: could not prepare the preview of room {} for {}: {}", room, player.getGameProfile().getName(), e.toString());
        }
    }

    /**
     * Rewrites the block entity data of a saved template: each entry's full save becomes the block entity's update tag, or
     * goes when there is none worth sending. Returns how many entries lost their full save.
     */
    static int asAClientWouldSeeThem(CompoundTag template, ServerLevel rooms, BlockPos origin) {
        int changed = 0;
        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            if (!entry.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            changed++;
            entry.remove("nbt");
            ListTag at = entry.getList("pos", Tag.TAG_INT);
            if (at.size() != 3) {
                continue;
            }
            BlockPos where = origin.offset(at.getInt(0), at.getInt(1), at.getInt(2));
            BlockEntity blockEntity = rooms.getBlockEntity(where);
            if (blockEntity == null) {
                continue;
            }
            try {
                CompoundTag shown = blockEntity.getUpdateTag();
                if (shown != null && !shown.isEmpty() && readsBack(where, blockEntity.getBlockState(), shown)) {
                    entry.put("nbt", shown.copy());
                }
            } catch (RuntimeException e) {
                // a block entity that cannot say what a client would see is sent bare
            }
        }
        return changed;
    }

    /** The client reads this data with the block entity's {@code load}; one that cannot take its own update tag here is sent bare. */
    private static boolean readsBack(BlockPos pos, BlockState state, CompoundTag shown) {
        try {
            CompoundTag withId = shown.copy();
            BlockEntity fresh = state.hasBlockEntity() ? ((net.minecraft.world.level.block.EntityBlock) state.getBlock()).newBlockEntity(pos, state) : null;
            if (fresh == null) {
                return false;
            }
            fresh.load(withId);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A template that writes the prepared data when Compact Machines' packet asks it to save itself. */
    private static final class Prepared extends StructureTemplate {
        private final CompoundTag prepared;

        private Prepared(CompoundTag prepared) {
            this.prepared = prepared;
        }

        @Override
        public CompoundTag save(CompoundTag into) {
            return prepared;
        }
    }

    /** For the tests: forgets when a player last previewed. */
    public static synchronized void forget(UUID player) {
        LAST.remove(player);
    }
}
