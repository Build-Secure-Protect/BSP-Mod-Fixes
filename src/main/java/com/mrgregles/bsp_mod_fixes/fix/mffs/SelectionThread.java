package com.mrgregles.bsp_mod_fixes.fix.mffs;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * F-43 (MFFS 5.1.29): a projector selects its field's positions on the server thread. MFFS runs {@code selectProjectablePositions}
 * through {@code CompletableFuture.completeAsync(supplier)}, the common ForkJoinPool, and that method reads the world and shared
 * state while the server thread changes them: {@code FusionModule.beforeSelect} streams {@code FrequencyGrid}'s plain
 * {@code HashSet}, which every fortron block entity's load and unload edits ({@code ConcurrentModificationException}, "Error
 * selecting force field blocks", the selection answered empty); {@code canProjectPos} and the Disintegration module read block
 * states, which from a worker thread hop to the main thread and load chunks; {@code isLoaded}, the other projectors' modules
 * and the projector's own slots are read with no synchronisation.
 *
 * <p>With the fix, {@code MffsSelectionThreadMixin} hands the same supplier to {@code completeAsync(supplier, executor)} with the
 * server as executor ({@link MinecraftServer#submitAsync}), so the selection runs between ticks on the server thread, as the
 * projection already does; the calculation of the field's geometry stays on the pool, where it reads only module counts and the
 * level's height. Nothing of MFFS then touches the world or the grid off the server thread. Switch: {@code [mffs]
 * selectOnServerThread} in {@code bsp_mod_fixes-server.toml}, read at each selection. Pure {@link #onServer} for the tests.
 */
public final class SelectionThread {
    public static final String ID = "F-43";
    public static final String WHAT = "a projector selects its field's positions on the server thread, not on the common ForkJoinPool, where MFFS's selection streamed the frequency grid while the server thread changed it (ConcurrentModificationException) and read the world";
    public static final String TARGET = MffsGate.TARGET;

    private static final LongAdder ON_SERVER = new LongAdder();
    private static final LongAdder ON_POOL = new LongAdder();

    private SelectionThread() {}

    /** Whether the switch is on: the server config's value once it is loaded. */
    public static boolean enabled() {
        return FixesConfig.SPEC.isLoaded() && FixesConfig.MFFS_SELECT_ON_SERVER_THREAD.get();
    }

    /** Whether a selection runs on the server thread: the switch on and the projector in a server level. */
    public static boolean onServer(boolean enabled, boolean serverLevel) {
        return enabled && serverLevel;
    }

    /** What the mixin does in place of {@code future.completeAsync(supplier)}: the same supplier, on the server when the rule says so. */
    public static <T> CompletableFuture<T> start(CompletableFuture<T> future, Supplier<? extends T> supplier, Level level) {
        if (onServer(enabled(), level instanceof ServerLevel)) {
            MinecraftServer server = ((ServerLevel) level).getServer();
            Executor executor = task -> server.submitAsync(task);
            return future.completeAsync(() -> {
                ON_SERVER.increment();
                return supplier.get();
            }, executor);
        }
        return future.completeAsync(() -> {
            ON_POOL.increment();
            return supplier.get();
        });
    }

    /** How many selections ran on the server thread this session. */
    public static long onServerThread() {
        return ON_SERVER.sum();
    }

    /** How many selections ran on the pool this session (the switch off). */
    public static long onPool() {
        return ON_POOL.sum();
    }
}
