package com.mrgregles.bsp_mod_fixes.fix.ironfurnaces;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * F-39 (Iron Furnaces 4.1.8): a player's furnace list is replaced when it loads, not appended to. Iron Furnaces keeps, on every
 * player, the positions of the furnaces they placed ({@code ironfurnaces:furnaces_list}, read by the Million Furnace's generator
 * to find one furnace of each tier by its owner). Its capability provider's {@code deserializeNBT} adds every saved entry to the
 * list it already holds, with no clear and no check, where Forge's load is a restore and every other provider replaces its
 * state. Any second load on the same player doubles the list. In the pack BSP-InventorySync gives every player exactly that:
 * at login vanilla loads the local player file, then InventorySync restores the network snapshot through Forge's
 * {@code deserializeCaps}, so the list became local + snapshot on every login and was saved to both. One player's file reached
 * 2,138,192 entries for two furnaces (88.7 MB), each login froze the server for four minutes and left a {@code ServerPlayer}
 * behind. Iron Furnaces' own {@code add} does check the position first; the load is the one unchecked path.
 *
 * <p>With the fix, {@code IronFurnacesListLoadMixin} answers the provider's load here: the tag is read as Iron Furnaces reads it
 * ({@code count}, then {@code furnaces.furnace<i>} with {@code X}, {@code Y}, {@code Z}, a missing entry reading as 0, 0, 0),
 * the first entry of each position is kept through a hash set, and the list's contents are replaced. A load that dropped
 * entries, or left more than {@code maxFurnacesPerPlayer}, is queued; {@link FurnaceListNames} names the player at the end of
 * the tick (the provider does not know its player) and logs one line per such load (a first login after the fix sees two: the
 * local file's, then InventorySync's snapshot copy's). Nothing is ever removed but duplicates. Switch:
 * {@code [ironFurnaces] repairFurnaceList} in {@code bsp_mod_fixes-server.toml}, read at each load; off, Iron Furnaces' own
 * append runs. Pure {@link #read}, {@link #replace}, {@link #overCap} and the queue for the tests.
 */
public final class FurnaceListRepair {
    public static final String ID = "F-39";
    public static final String WHAT = "a player's furnace list is replaced when it loads instead of appended to, and duplicate entries are dropped (InventorySync's restore doubled it at every login)";
    public static final String TARGET = IronFurnacesGate.TARGET;
    /** How long a queued repair waits for its player to be found before it is logged without a name, in ticks. */
    public static final int NAME_WAIT_TICKS = 200;

    /** One load: the entries kept (in their first order), how many were read, how many were dropped as duplicates. */
    public record Result(List<BlockPos> kept, int read, int removed) {}

    /** A load waiting for its player's name: the list it filled (held weakly), its result, the tick it was queued. */
    public record Pending(WeakReference<List<BlockPos>> list, Result result, long queuedTick) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final LongAdder LOADS = new LongAdder();
    private static final LongAdder REMOVED = new LongAdder();

    private FurnaceListRepair() {}

    /** Whether the switch is on: the server config's value, and on before that config has loaded (a guard against growth, so on by doubt). */
    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.IF_REPAIR_FURNACE_LIST.get();
    }

    /** The count a player's list may reach before a warning, 0 for none. */
    public static int cap() {
        return FixesConfig.SPEC.isLoaded() ? FixesConfig.IF_MAX_FURNACES_PER_PLAYER.get() : 4096;
    }

    /** Whether a list of this size is over the cap (0: never). */
    public static boolean overCap(int size, int cap) {
        return cap > 0 && size > cap;
    }

    /**
     * Reads the saved list as Iron Furnaces does and keeps the first entry of each position: {@code count} entries named
     * {@code furnace<i>} under {@code furnaces}, each with {@code X}, {@code Y}, {@code Z}; a missing entry or field reads as 0,
     * as Iron Furnaces' own loop would read it. One hash insert per entry, nothing walked twice.
     */
    public static Result read(CompoundTag tag) {
        int count = Math.max(0, tag.getInt("count"));
        CompoundTag furnaces = tag.getCompound("furnaces");
        List<BlockPos> kept = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            CompoundTag entry = furnaces.getCompound("furnace" + i);
            BlockPos pos = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            if (seen.add(pos.asLong())) {
                kept.add(pos);
            }
        }
        return new Result(kept, count, count - kept.size());
    }

    /** Makes {@code list} hold exactly {@code kept}, in order: a load replaces, it does not append. */
    public static void replace(List<BlockPos> list, List<BlockPos> kept) {
        list.clear();
        list.addAll(kept);
    }

    /** {@link #load(CompoundTag, List, long)} at the running server's tick (0 where there is no server). */
    public static Result load(CompoundTag tag, List<BlockPos> list) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return load(tag, list, server == null ? 0 : server.getTickCount());
    }

    /**
     * What the mixin does in place of Iron Furnaces' load: read, replace, count, and queue the load for a named log line when
     * entries were dropped or the list is over the cap. {@code tick} is the server's tick for the wait.
     */
    public static Result load(CompoundTag tag, List<BlockPos> list, long tick) {
        Result result = read(tag);
        replace(list, result.kept());
        LOADS.increment();
        REMOVED.add(result.removed());
        if (result.removed() > 0 || overCap(result.kept().size(), cap())) {
            synchronized (PENDING) {
                PENDING.add(new Pending(new WeakReference<>(list), result, tick));
            }
        }
        return result;
    }

    /**
     * Resolves the queued loads: each whose list {@code nameOf} can name (the player whose capability holds that very list) is
     * logged with the name and dropped; one older than {@link #NAME_WAIT_TICKS} is logged without a name and dropped; the rest
     * wait. Returns the lines logged, for the tests. {@code nameOf} answers null for a list no online player holds.
     */
    public static List<String> resolve(Function<List<BlockPos>, @Nullable String> nameOf, long tick) {
        List<String> logged = new ArrayList<>();
        synchronized (PENDING) {
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                List<BlockPos> list = p.list().get();
                String name = list == null ? null : nameOf.apply(list);
                if (name == null && list != null && tick - p.queuedTick() < NAME_WAIT_TICKS) {
                    continue;
                }
                logged.addAll(log(name == null ? "a player no longer online" : name, p.result()));
                it.remove();
            }
        }
        return logged;
    }

    private static List<String> log(String who, Result r) {
        List<String> lines = new ArrayList<>();
        if (r.removed() > 0) {
            String line = String.format("BSP-Mod Fixes %s: removed %d duplicate Iron Furnaces list entries for %s (%d kept)", ID, r.removed(), who, r.kept().size());
            Fixes.LOGGER.info(line);
            lines.add(line);
        }
        if (overCap(r.kept().size(), cap())) {
            String line = String.format("BSP-Mod Fixes %s: the Iron Furnaces list of %s holds %d furnaces, over maxFurnacesPerPlayer (%d); nothing was removed, check that player's furnaces", ID, who, r.kept().size(), cap());
            Fixes.LOGGER.warn(line);
            lines.add(line);
        }
        return lines;
    }

    /** The loads waiting for a name (for the tests). */
    public static int pending() {
        synchronized (PENDING) {
            return PENDING.size();
        }
    }

    public static long loads() {
        return LOADS.sum();
    }

    public static long removed() {
        return REMOVED.sum();
    }
}
