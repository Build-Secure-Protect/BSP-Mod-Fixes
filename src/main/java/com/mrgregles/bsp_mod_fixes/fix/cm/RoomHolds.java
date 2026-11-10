package com.mrgregles.bsp_mod_fixes.fix.cm;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Collection;
import java.util.function.LongPredicate;

/**
 * F-29, the rule: a Compact Machines room is kept loaded only while a machine block bound to it is itself loaded.
 * Compact Machines marks the chunk of every room that has a tunnel as forced (saved with the world, restored at every
 * server start) and never releases it: not when the machine's own chunk unloads, not when its owner leaves, not when the
 * machine is broken. Everything built inside such rooms ticks around the clock, and the list only grows.
 *
 * <p>Here a room is <em>held</em> when it wants loading (it has a tunnel, or the Chunkloader upgrade) and a loaded machine
 * block bound to it can be traced to something outside: the machine stands outside the room dimension, or it stands inside
 * a room that is itself held, or inside a room a player is standing in. Rooms that only hold each other (a machine inside
 * its own room, two rooms holding one another) are not held. A room that stops being held is released after a short
 * delay, so a chunk flapping at the edge of a player's view does not load and unload it. Pure: positions are chunk longs,
 * the caller says what a room wants and where the players are ({@code RoomLoader}).
 */
public final class RoomHolds {
    public static final String ID = "F-29";
    public static final String WHAT = "a room is kept loaded only while a machine block bound to it is loaded, and is released when the last one unloads or is broken";
    public static final String TARGET = CmGate.TARGET;

    /** A loaded machine block: whether it stands in the room dimension, the chunk it stands in, and the room it is bound to. */
    public record Machine(boolean inRoomDimension, long ownChunk, long room) {}

    private RoomHolds() {}

    /** Whether one machine holds its room right now, given the rooms already held and the rooms players stand in. */
    public static boolean holds(Machine machine, boolean roomWantsLoading, LongSet held, LongSet playerRooms) {
        if (!roomWantsLoading) {
            return false;
        }
        return !machine.inRoomDimension() || held.contains(machine.ownChunk()) || playerRooms.contains(machine.ownChunk());
    }

    /** Every room held by these loaded machines: traced outward from machines outside the room dimension and rooms with players. */
    public static LongSet held(Collection<Machine> machines, LongPredicate wantsLoading, LongSet playerRooms) {
        Long2BooleanOpenHashMap wants = new Long2BooleanOpenHashMap();
        LongOpenHashSet held = new LongOpenHashSet();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Machine m : machines) {
                if (held.contains(m.room())) {
                    continue;
                }
                boolean w = wants.containsKey(m.room()) ? wants.get(m.room()) : cache(wants, m.room(), wantsLoading.test(m.room()));
                if (holds(m, w, held, playerRooms)) {
                    held.add(m.room());
                    changed = true;
                }
            }
        }
        return held;
    }

    private static boolean cache(Long2BooleanOpenHashMap wants, long room, boolean value) {
        wants.put(room, value);
        return value;
    }

    /**
     * The rooms to release now: loaded by us, no longer held, and not held for at least {@code delayTicks}. Remembers in
     * {@code unheldSince} when each loaded room was first seen not held, and forgets a room that is held again.
     */
    public static LongList due(LongSet loaded, LongSet held, Long2LongOpenHashMap unheldSince, long now, long delayTicks) {
        LongArrayList due = new LongArrayList();
        for (long room : loaded) {
            if (held.contains(room)) {
                unheldSince.remove(room);
            } else {
                long since = unheldSince.containsKey(room) ? unheldSince.get(room) : now;
                unheldSince.put(room, since);
                if (now - since >= delayTicks) {
                    due.add(room);
                }
            }
        }
        for (long room : due) {
            unheldSince.remove(room);
        }
        return due;
    }

    /**
     * Whether a machine's capability lookup is answered "nothing here" without asking Compact Machines: only for a room
     * that is neither held nor loaded, where the lookup would otherwise load the room's chunk on the server thread for
     * every pipe that asks. A held room is never refused, loaded yet or not, so a pipe's first question as a base loads
     * is answered as Compact Machines answers it.
     */
    public static boolean refusesLookup(boolean ruleOn, boolean roomHeld, boolean roomLoaded) {
        return ruleOn && !roomHeld && !roomLoaded;
    }
}
