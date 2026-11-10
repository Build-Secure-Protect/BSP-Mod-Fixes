package com.mrgregles.bsp_mod_fixes.fix.cm;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F-29, the work: keeps the list of loaded Compact Machines blocks and the rooms they are bound to, and holds a chunk
 * ticket of this mod's own on every room {@link RoomHolds} says is held. The mixin {@code CmRoomHoldMixin} reports a
 * machine when Compact Machines would have forced or released its room ({@link #machineSynced}) and when its block entity
 * goes ({@link #machineGone}: the chunk unloading or the block breaking); a pass once a second ({@link #reconcile}) adds
 * and releases tickets, so a first tunnel, a last tunnel, a player walking into a room and a machine unloading all take
 * effect within a second, and a release after the configured delay.
 *
 * <p>The ticket is a plain region ticket (radius 2, as Compact Machines' own), never the saved "forced" flag, so nothing
 * outlives a restart. A level with no players and no saved forced chunk stops ticking its entities and block entities after
 * 300 ticks ({@code ServerLevel.tick}); Compact Machines' flag kept the room dimension going, so while any room is held
 * each pass resets that count ({@code resetEmptyTime}). At server start the forced flags Compact Machines saved in the room dimension before this fix are
 * cleared once ({@link #clearStaleForced}): rooms then load again as their machines do. With
 * {@code [compactMachines] roomsFollowMachines} off every call goes back to Compact Machines and this class releases what
 * it holds. All on the server thread. No Compact Machines class is named here ({@code CmRooms} asks its data).
 */
public final class RoomLoader {
    /** Compact Machines' room dimension. */
    public static final ResourceKey<Level> ROOMS = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("compactmachines", "compact_world"));
    private static final TicketType<ChunkPos> TICKET = TicketType.create("bsp_mod_fixes:cm_room", Comparator.comparingLong(ChunkPos::toLong));
    /** As Compact Machines' own ticket: the room's chunk ticks its block entities and entities, two rings stay loaded around it. */
    public static final int RADIUS = 2;
    /** How often the pass runs, in ticks. */
    public static final int EVERY = 20;

    private static final Map<GlobalPos, Long> MACHINES = new HashMap<>();
    private static final LongOpenHashSet LOADED = new LongOpenHashSet();
    private static final Long2LongOpenHashMap UNHELD_SINCE = new Long2LongOpenHashMap();
    private static long held;
    private static long released;

    private RoomLoader() {}

    /** Whether the rule is on: the server config's switch, once it is loaded. */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.CM_ROOMS_FOLLOW_MACHINES.get();
    }

    /** The release delay in ticks. */
    public static long delayTicks() {
        return FixesConfig.SPEC.isLoaded() ? FixesConfig.CM_ROOM_RELEASE_SECONDS.get() * 20L : 100L;
    }

    /**
     * A machine block entity has a room and Compact Machines is about to force or release it. Returns false when the rule
     * is off, and the caller then lets Compact Machines act; otherwise the machine is remembered and, when it holds the
     * room at once, the room's ticket is added now (so a pipe's first question finds the room loading, as it does with
     * Compact Machines' own forcing).
     */
    public static synchronized boolean machineSynced(BlockEntity machine, ServerLevel rooms, ChunkPos room) {
        if (!enabled() || !(machine.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        if (machine.isRemoved()) {
            return true; // its load ran after its chunk had gone again: nothing to remember, nothing to force
        }
        long roomKey = room.toLong();
        MACHINES.put(GlobalPos.of(level.dimension(), machine.getBlockPos().immutable()), roomKey);
        if (!LOADED.contains(roomKey)) {
            RoomHolds.Machine m = new RoomHolds.Machine(level.dimension().equals(ROOMS), new ChunkPos(machine.getBlockPos()).toLong(), roomKey);
            if (RoomHolds.holds(m, CmRooms.wantsLoading(rooms, roomKey), LOADED, playerRooms(rooms))) {
                hold(rooms, roomKey);
            }
        }
        UNHELD_SINCE.remove(roomKey);
        return true;
    }

    /** A machine block entity is gone from its level: its chunk unloaded, or the block was broken or replaced. */
    public static synchronized void machineGone(BlockEntity machine) {
        if (!MACHINES.isEmpty() && machine.getLevel() instanceof ServerLevel level) {
            MACHINES.remove(GlobalPos.of(level.dimension(), machine.getBlockPos()));
        }
    }

    /** The listener: once a second at the end of the server tick. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % EVERY == 0) {
            reconcile(event.getServer(), event.getServer().getTickCount());
        }
    }

    /** The pass: adds a ticket for every room newly held and releases the rooms not held for the delay. */
    public static synchronized void reconcile(MinecraftServer server, long now) {
        if (MACHINES.isEmpty() && LOADED.isEmpty()) {
            return;
        }
        ServerLevel rooms = server.getLevel(ROOMS);
        if (rooms == null) {
            return;
        }
        if (!enabled()) {
            for (long room : LOADED.toLongArray()) {
                CmRooms.forceAsShipped(rooms, room); // Compact Machines only forces when a machine loads; these machines are loaded already
                release(rooms, room);
            }
            MACHINES.clear();
            UNHELD_SINCE.clear();
            return;
        }
        List<RoomHolds.Machine> machines = new ArrayList<>(MACHINES.size());
        for (Map.Entry<GlobalPos, Long> e : MACHINES.entrySet()) {
            // a block entity outlives its chunk's loaded state by a wide ring (it is only removed when the chunk is dropped
            // altogether), so the chunk is asked each pass: the machine counts while its chunk is a loaded, full chunk
            ChunkPos own = new ChunkPos(e.getKey().pos());
            ServerLevel level = server.getLevel(e.getKey().dimension());
            if (level != null && level.getChunkSource().getChunkNow(own.x, own.z) != null) {
                machines.add(new RoomHolds.Machine(e.getKey().dimension().equals(ROOMS), own.toLong(), e.getValue()));
            }
        }
        LongSet nowHeld = RoomHolds.held(machines, room -> CmRooms.wantsLoading(rooms, room), playerRooms(rooms));
        for (long room : nowHeld) {
            if (!LOADED.contains(room)) {
                hold(rooms, room);
            }
        }
        for (long room : RoomHolds.due(LOADED, nowHeld, UNHELD_SINCE, now, delayTicks())) {
            release(rooms, room);
        }
        if (!LOADED.isEmpty()) {
            rooms.resetEmptyTime();
        }
    }

    private static LongSet playerRooms(ServerLevel rooms) {
        LongOpenHashSet set = new LongOpenHashSet();
        for (ServerPlayer player : rooms.players()) {
            set.add(player.chunkPosition().toLong());
        }
        return set;
    }

    private static void hold(ServerLevel rooms, long room) {
        ChunkPos pos = new ChunkPos(room);
        rooms.getChunkSource().addRegionTicket(TICKET, pos, RADIUS, pos);
        rooms.resetEmptyTime();
        LOADED.add(room);
        held++;
    }

    private static void release(ServerLevel rooms, long room) {
        ChunkPos pos = new ChunkPos(room);
        rooms.getChunkSource().removeRegionTicket(TICKET, pos, RADIUS, pos);
        LOADED.remove(room);
        released++;
    }

    /** Whether this fix holds a ticket on the room now. */
    public static synchronized boolean isHeld(long room) {
        return LOADED.contains(room);
    }

    /** For a machine's capability lookup: whether to answer without asking Compact Machines ({@link RoomHolds#refusesLookup}). */
    public static boolean refusesLookup(Level machineLevel, ChunkPos room) {
        if (!(machineLevel instanceof ServerLevel level) || !enabled() || isHeld(room.toLong())) {
            return false;
        }
        ServerLevel rooms = level.getServer().getLevel(ROOMS);
        return rooms != null && RoomHolds.refusesLookup(true, false, rooms.getChunkSource().getChunkNow(room.x, room.z) != null);
    }

    /** At server start, the rule on: the forced flags saved in the room dimension are cleared; rooms load again with their machines. */
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel rooms = event.getServer().getLevel(ROOMS);
        if (rooms != null && enabled()) {
            int cleared = clearStaleForced(rooms);
            if (cleared > 0) {
                Fixes.LOGGER.info("F-29: cleared {} force-loaded chunk(s) saved in {}; a room loads again while a machine bound to it is loaded", cleared, ROOMS.location());
            }
        }
    }

    /** Clears every saved forced flag of the room dimension. Returns how many there were. */
    public static int clearStaleForced(ServerLevel rooms) {
        long[] forced = rooms.getForcedChunks().toLongArray();
        for (long chunk : forced) {
            rooms.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
        }
        return forced.length;
    }

    /** The server stopped (a single-player world closed): nothing is carried into the next one. */
    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        MACHINES.clear();
        LOADED.clear();
        UNHELD_SINCE.clear();
    }

    /** Loaded machine blocks known now. */
    public static synchronized int machines() {
        return MACHINES.size();
    }

    /** Tickets added so far. */
    public static synchronized long heldCount() {
        return held;
    }

    /** Tickets released so far. */
    public static synchronized long releasedCount() {
        return released;
    }
}
