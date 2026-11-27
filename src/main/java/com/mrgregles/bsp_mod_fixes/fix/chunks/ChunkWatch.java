package com.mrgregles.bsp_mod_fixes.fix.chunks;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-52 (Minecraft 1.20.1, Forge 47.4.23): a diagnostic. For the chunks inside the configured watch areas, every chunk ticket
 * added or removed and every load and unload is one INFO line, so what keeps a chunk reloading (a 5×5 area of
 * {@code allthemodium:the_other} once a minute with no player near, D182) can be read from the log. Every ticket of a
 * {@code ServerLevel} passes through two package-private methods of {@code DistanceManager}, {@code addTicket(long, Ticket)}
 * and {@code removeTicket(long, Ticket)}: the public {@code addTicket}/{@code removeTicket(TicketType, ChunkPos, int, T)},
 * {@code addRegionTicket}/{@code removeRegionTicket} (both overloads), {@code updateChunkForced} (vanilla {@code /forceload}
 * and {@code ServerLevel.setChunkForced}), the player ticket tracker, and Forge's {@code ForgeChunkManager.forceChunk} (through
 * {@code addRegionTicket}) all end there (javap, Forge 47.4.23; only the expiry of timed tickets in {@code purgeStaleTickets}
 * and the shutdown's {@code removeTicketsOnClosing} drop tickets without them). {@code DistanceManagerTicketMixin} calls
 * {@link #onTicket} at the head of both; Forge's {@code ChunkEvent.Load} and {@code ChunkEvent.Unload} (full chunks of a
 * server level) go to {@link #onChunkLoad} and {@link #onChunkUnload} from the Forge bus. A ticket line has the type, level and
 * key of the ticket and the innermost calling class outside Minecraft, Forge and the JDK; once per ticket type and caller a
 * minute it also carries up to {@link #MAX_FRAMES} such frames (D186). The level of a distance manager is found by matching
 * it against each level's chunk map once and remembered. Cheap when no area matches: the coordinates are compared before the
 * level is looked up, and nothing at all is done while the list is empty. Switches: {@code [diagnostics] chunkWatchTracer} and
 * {@code [diagnostics] chunkWatchAreas} (D188). Nothing is changed.
 */
public final class ChunkWatch {
    public static final String ID = "F-52";
    public static final String WHAT = "logs, for the chunks inside the configured watch areas, every chunk ticket added or removed (type, level, key, the caller, and once per ticket type and caller a minute a stack of up to twelve frames outside Minecraft, Forge and Java) and every load and unload, one INFO line each, so what keeps reloading a chunk can be read from the log; a diagnostic, nothing is changed";
    public static final String TARGET = "Minecraft 1.20.1 (Forge 47.4.23)";

    /** The form of one watch area in the config. */
    public static final String FORMAT = "<dimension> <minChunkX> <minChunkZ> <maxChunkX> <maxChunkZ>";
    /** How many frames outside Minecraft, Forge and the JDK a ticket's stack carries. */
    public static final int MAX_FRAMES = 12;
    /** The least time between two stacks for one ticket type and caller. */
    public static final long TRACE_INTERVAL_MS = 60_000;
    /** How many of the last lines are kept for the tests. */
    public static final int RECENT_LINES = 200;

    /** A watched area: a dimension and an inclusive chunk rectangle. */
    public record Area(String dimension, int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(int chunkX, int chunkZ) {
            return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
        }

        public boolean contains(String dim, int chunkX, int chunkZ) {
            return dimension.equals(dim) && contains(chunkX, chunkZ);
        }
    }

    private static final Map<DistanceManager, ServerLevel> LEVELS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_TRACE = new ConcurrentHashMap<>();
    private static final Deque<String> RECENT = new ArrayDeque<>();
    private static final LongAdder EVENTS = new LongAdder();
    private static final LongAdder TRACES = new LongAdder();
    private static volatile List<? extends String> cachedLines = null;
    private static volatile List<Area> cachedAreas = List.of();

    private ChunkWatch() {}

    /** Whether the switch is on: the server config's value, and on before that config has loaded. */
    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.DIAG_CHUNK_WATCH_TRACER.get();
    }

    /**
     * Pure: one config line, {@value #FORMAT}, whitespace-separated, the dimension a resource location and the four numbers
     * chunk coordinates; min and max may come in either order. Empty when the line is not in that form.
     */
    public static Optional<Area> parse(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 5 || ResourceLocation.tryParse(parts[0]) == null) {
            return Optional.empty();
        }
        try {
            int x1 = Integer.parseInt(parts[1]);
            int z1 = Integer.parseInt(parts[2]);
            int x2 = Integer.parseInt(parts[3]);
            int z2 = Integer.parseInt(parts[4]);
            return Optional.of(new Area(parts[0], Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The config's validator: a string in the form of {@link #FORMAT}. */
    public static boolean isValidLine(Object line) {
        return line instanceof String s && parse(s).isPresent();
    }

    /** The areas the config names, parsed once per change of the list; empty before the server config has loaded. */
    public static List<Area> areas() {
        if (!FixesConfig.SPEC.isLoaded()) {
            return List.of();
        }
        List<? extends String> lines = FixesConfig.DIAG_CHUNK_WATCH_AREAS.get();
        if (!lines.equals(cachedLines)) {
            List<Area> areas = new ArrayList<>();
            for (String line : lines) {
                parse(line).ifPresent(areas::add);
            }
            cachedAreas = List.copyOf(areas);
            cachedLines = List.copyOf(lines);
        }
        return cachedAreas;
    }

    /** Pure: whether any area covers the chunk coordinates, in any dimension. */
    public static boolean inAnyArea(List<Area> areas, int chunkX, int chunkZ) {
        for (Area area : areas) {
            if (area.contains(chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }

    /** Pure: whether an area covers the chunk in that dimension. */
    public static boolean inArea(List<Area> areas, String dimension, int chunkX, int chunkZ) {
        for (Area area : areas) {
            if (area.contains(dimension, chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }

    /** Pure: whether a stack may be printed for this key now (the first time, then once per {@link #TRACE_INTERVAL_MS}). */
    public static boolean mayTrace(String key, long nowMs) {
        if (LAST_TRACE.size() > 1_000) {
            LAST_TRACE.clear();
        }
        Long last = LAST_TRACE.get(key);
        if (last != null && nowMs - last < TRACE_INTERVAL_MS) {
            return false;
        }
        LAST_TRACE.put(key, nowMs);
        return true;
    }

    /** The mixin's entry: a ticket is being added to or removed from the chunk with this key in the level this distance manager serves. */
    public static void onTicket(DistanceManager manager, long chunkKey, Ticket<?> ticket, boolean added) {
        if (!enabled()) {
            return;
        }
        List<Area> areas = areas();
        if (areas.isEmpty()) {
            return;
        }
        int chunkX = ChunkPos.getX(chunkKey);
        int chunkZ = ChunkPos.getZ(chunkKey);
        if (!inAnyArea(areas, chunkX, chunkZ)) {
            return;
        }
        ServerLevel level = levelOf(manager);
        String dimension = level == null ? "an unknown level" : level.dimension().location().toString();
        if (level != null && !inArea(areas, dimension, chunkX, chunkZ)) {
            return;
        }
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        List<String> frames = ForeignFrames.of(stack, MAX_FRAMES);
        String caller = frames.isEmpty() ? "Minecraft or Forge itself (no mod frame on the stack)" : frames.get(0);
        String line = "ticket " + (added ? "added to" : "removed from") + " chunk [" + chunkX + ", " + chunkZ + "] in " + dimension
                + ": " + ticket.getType() + " level " + ticket.getTicketLevel() + " (" + ticket + ") by " + caller;
        // the stack once a minute per ticket type and calling class (the class, not the frame: one mod's several call sites are one caller)
        boolean trace = !frames.isEmpty() && mayTrace(ticket.getType() + "|" + ForeignFrames.firstClass(stack, ""), System.currentTimeMillis());
        log(level, line, trace ? frames : null);
    }

    /** Forge's chunk load event: a full chunk of a server level came in. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        onChunkEvent(event, event.isNewChunk() ? "loaded (newly generated)" : "loaded");
    }

    /** Forge's chunk unload event: a full chunk of a server level is going. */
    public static void onChunkUnload(ChunkEvent.Unload event) {
        onChunkEvent(event, "unloaded");
    }

    private static void onChunkEvent(ChunkEvent event, String what) {
        if (!(event.getLevel() instanceof ServerLevel level) || !enabled()) {
            return;
        }
        List<Area> areas = areas();
        if (areas.isEmpty()) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        String dimension = level.dimension().location().toString();
        if (!inArea(areas, dimension, pos.x, pos.z)) {
            return;
        }
        log(level, "chunk " + pos + " in " + dimension + " " + what, null);
    }

    /** The level whose chunk map owns this distance manager, matched once against every level of the running server. */
    @Nullable
    static ServerLevel levelOf(DistanceManager manager) {
        ServerLevel cached = LEVELS.get(manager);
        if (cached != null) {
            return cached;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().chunkMap.getDistanceManager() == manager) {
                if (LEVELS.size() > 64) {
                    LEVELS.clear();
                }
                LEVELS.put(manager, level);
                return level;
            }
        }
        return null;
    }

    private static void log(@Nullable ServerLevel level, String line, @Nullable List<String> frames) {
        String tick = level == null ? "" : " [tick " + level.getServer().getTickCount() + "]";
        EVENTS.increment();
        synchronized (RECENT) {
            if (RECENT.size() >= RECENT_LINES) {
                RECENT.removeFirst();
            }
            RECENT.addLast(line);
        }
        if (frames == null) {
            Fixes.LOGGER.info("BSP-Mod Fixes F-52{}: {}", tick, line);
        } else {
            TRACES.increment();
            Fixes.LOGGER.info("BSP-Mod Fixes F-52{}: {}; the stack outside Minecraft, Forge and Java (one per ticket type and caller a minute):\n    at {}",
                    tick, line, String.join("\n    at ", frames));
        }
    }

    /** The last lines logged (without the tick), oldest first. */
    public static List<String> recent() {
        synchronized (RECENT) {
            return List.copyOf(RECENT);
        }
    }

    /** How many lines were logged so far in this run. */
    public static long events() {
        return EVENTS.sum();
    }

    /** How many of them carried a stack. */
    public static long traces() {
        return TRACES.sum();
    }
}
