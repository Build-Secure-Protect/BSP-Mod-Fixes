package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.mixin.gametest.DistanceManagerTicketsAccessor;
import com.mrgregles.bsp_mod_fixes.mixin.gametest.ServerChunkCacheDistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What keeps a level's chunks loaded, read at one moment: the loaded chunk count, the chunk tickets by type (how many chunks
 * each type holds and the lowest level it holds one at: 31 ticks entities, 32 ticks blocks, 33 is a loaded border chunk), and
 * Forge's saved ticket store by mod id (the tickets that come back at every start). Vanilla and Forge only; GameTest server only.
 */
final class ChunkHolds {
    private ChunkHolds() {}

    record Snapshot(int loadedChunks, Map<String, Integer> chunksByType, Map<String, Integer> lowestLevelByType, Map<String, Integer> forgeSavedByMod) {
        /** The ticket types present that {@code baseline} did not have, with the chunks they hold. */
        Map<String, Integer> newTypesAgainst(Snapshot baseline) {
            Map<String, Integer> out = new TreeMap<>(chunksByType);
            baseline.chunksByType.keySet().forEach(out::remove);
            return out;
        }

        String describe() {
            StringBuilder b = new StringBuilder(loadedChunks + " loaded chunks; tickets:");
            if (chunksByType.isEmpty()) {
                b.append(" none");
            }
            chunksByType.forEach((type, n) -> b.append(' ').append(type).append('=').append(n).append(" chunks (level ").append(lowestLevelByType.get(type)).append(')'));
            b.append("; Forge's saved store:");
            if (forgeSavedByMod.isEmpty()) {
                b.append(" empty");
            }
            forgeSavedByMod.forEach((mod, n) -> b.append(' ').append(mod).append('=').append(n));
            return b.toString();
        }
    }

    static Snapshot of(ServerLevel level) {
        ServerChunkCache cache = level.getChunkSource();
        DistanceManager manager = ((ServerChunkCacheDistanceManagerAccessor) cache).bsp_mod_fixes$distanceManager();
        Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = ((DistanceManagerTicketsAccessor) manager).bsp_mod_fixes$tickets();
        Map<String, Integer> byType = new TreeMap<>();
        Map<String, Integer> lowest = new TreeMap<>();
        for (SortedArraySet<Ticket<?>> set : tickets.values()) {
            Set<String> seen = new HashSet<>();
            for (Ticket<?> ticket : set) {
                String type = ticket.getType().toString();
                if (seen.add(type)) {
                    byType.merge(type, 1, Integer::sum);
                }
                lowest.merge(type, ticket.getTicketLevel(), Math::min);
            }
        }
        Map<String, Integer> forge = new TreeMap<>();
        ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
        if (data != null) {
            count(data.getBlockForcedChunks(), "block", forge);
            count(data.getEntityForcedChunks(), "entity", forge);
        }
        return new Snapshot(cache.getLoadedChunksCount(), byType, lowest, forge);
    }

    private static <T extends Comparable<? super T>> void count(ForgeChunkManager.TicketTracker<T> tracker, String kind, Map<String, Integer> out) {
        for (Map.Entry<ForgeChunkManager.TicketOwner<T>, LongSet> e : tracker.getChunks().entrySet()) {
            out.merge(modId(e.getKey()) + " " + kind, e.getValue().size(), Integer::sum);
        }
        for (Map.Entry<ForgeChunkManager.TicketOwner<T>, LongSet> e : tracker.getTickingChunks().entrySet()) {
            out.merge(modId(e.getKey()) + " " + kind + " ticking", e.getValue().size(), Integer::sum);
        }
    }

    /** Forge keeps a ticket owner's mod id in a private field with no getter (BSP-Server Utilities reads it the same way). */
    private static String modId(ForgeChunkManager.TicketOwner<?> owner) {
        try {
            Field f = ForgeChunkManager.TicketOwner.class.getDeclaredField("modId");
            f.setAccessible(true);
            return String.valueOf(f.get(owner));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }
}
