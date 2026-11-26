package com.mrgregles.bsp_mod_fixes.fix.integrateddynamics;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * F-49 (Integrated Dynamics 1.31.5): a network element whose part no longer exists is skipped on the network's kill and
 * removal paths instead of crashing the server. {@code Network.kill()} (from {@code killIfEmpty()} in the server tick) calls
 * every element's {@code beforeNetworkKill}, and {@code Network.removeNetworkElementPost} calls {@code beforeNetworkKill} and
 * {@code onNetworkRemoval}; for a part element each reads the part's state through its container, which throws
 * {@code PartStateException} when the container has no part on that side, and nothing on those paths catches it, while the
 * network's update path does (it logs "Attempted to tick a part that was not properly unloaded" and invalidates the element).
 * One stale record, a part gone from its cable while the saved network kept its element (how it goes stale is F-50's, and
 * [[Mod Fixes - Assessment Integrated Dynamics Part State Crash]] section 2), then took Frostwarf down with "Exception in
 * server tick loop". Mod at fault: Integrated Dynamics.
 *
 * <p>The fix ({@code IdNetworkKillMixin} through {@code DanglingPartsHooks}, which names Integrated Dynamics' types; this class
 * names none, so it loads without the mod) redirects those three calls: for a part element whose container is not there or
 * has no part on its side (asked through the element's own container lookup, the same one Integrated Dynamics' call makes an
 * instant later, so it loads the chunk if that is needed, and the one exception it throws, {@code PartStateException}, is
 * caught) the call is skipped; otherwise the call runs inside the smallest catch of {@code PartStateException}, which skips as
 * well. Either way the position and side are logged at WARN once per server run, in the handoff's words. In {@code kill()} the
 * network is dying, so skipping is the removal; in {@code removeNetworkElementPost} the method goes on to drop the element from
 * the network. A skipped {@code onNetworkRemoval} leaves the part's id in the part network, since the id lives in the lost
 * state: the same residue Integrated Dynamics' own update-path invalidation leaves, harmless. Nothing that exists is changed,
 * no drop is touched, the update path keeps Integrated Dynamics' own guard. The switch is read at each call and is on before
 * the server config has loaded (a crash guard, on by doubt). Switch:
 * {@code [integratedDynamics] skipDanglingParts}, read at each call. Pure {@link #decide} and {@link #key} for the tests.
 */
public final class DanglingParts {
    public static final String ID = "F-49";
    public static final String WHAT = "an Integrated Dynamics network element whose part no longer exists is skipped and dropped on the network's kill and removal paths, with one WARN per position, instead of PartStateException taking the server down (the update path guarded it, the kill path did not)";
    public static final String TARGET = IdGate.TARGET;

    /** What a call on a part element did. */
    public enum Outcome { CALLED, SKIPPED_MISSING, SKIPPED_EXCEPTION }

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final LongAdder SKIPPED = new LongAdder();

    private DanglingParts() {}

    /** Whether the switch is on: the server config's value, and on before that config has loaded (a crash guard, so on by doubt). */
    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.ID_SKIP_DANGLING_PARTS.get();
    }

    /**
     * Pure: runs {@code call} unless {@code partPresent} is false; a runtime exception the call throws that {@code isPartState}
     * accepts (Integrated Dynamics' {@code PartStateException}) is swallowed and reported, any other goes on; {@code enabled}
     * false runs the call as Integrated Dynamics does, letting everything out.
     */
    public static Outcome decide(boolean enabled, boolean partPresent, Runnable call, Predicate<RuntimeException> isPartState) {
        if (!enabled) {
            call.run();
            return Outcome.CALLED;
        }
        if (!partPresent) {
            return Outcome.SKIPPED_MISSING;
        }
        try {
            call.run();
            return Outcome.CALLED;
        } catch (RuntimeException e) {
            if (isPartState.test(e)) {
                return Outcome.SKIPPED_EXCEPTION;
            }
            throw e;
        }
    }

    /** Pure: the once-per-run key of a position and side. */
    public static String key(@Nullable String level, @Nullable BlockPos pos, @Nullable Direction side) {
        return (level == null || pos == null ? "?" : level + pos.toShortString()) + "/" + (side == null ? "?" : side.getSerializedName());
    }

    /** Records a skip: the counter, and the WARN line once per position and side per run. */
    public static void record(@Nullable String level, @Nullable BlockPos pos, @Nullable Direction side, String path, Outcome outcome) {
        SKIPPED.increment();
        if (WARNED.add(key(level, pos, side))) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-49: Integrated Dynamics network had a part on {} of {} in {} that no longer exists; removed it from the network instead of crashing ({}, {})",
                    side == null ? "a side" : side.getSerializedName(), pos == null ? "an unknown position" : pos.toShortString(),
                    level == null ? "an unknown level" : level, path, outcome == Outcome.SKIPPED_MISSING ? "the container has no such part" : "the part's state threw");
        }
    }

    /** The positions and sides warned about so far in this run (the keys of {@link #key}). */
    public static Set<String> warned() {
        return Set.copyOf(WARNED);
    }

    /** How many calls were skipped so far (a position's second skip counts too). */
    public static long skipped() {
        return SKIPPED.sum();
    }
}
