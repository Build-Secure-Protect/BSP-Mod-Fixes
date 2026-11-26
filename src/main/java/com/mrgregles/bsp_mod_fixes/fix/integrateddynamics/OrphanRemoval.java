package com.mrgregles.bsp_mod_fixes.fix.integrateddynamics;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-50 (Integrated Dynamics 1.31.5): a part removed from a cable that has no network at that moment still leaves the network
 * it was in. {@code PartContainerDefault.removePart} has two branches: with the cable's carrier holding a network it removes
 * the part's element from that network (Pre, drops, Post) before dropping the part; with the carrier's network null it drops
 * the part and the item and tells no network at all. The carrier is transient: after a chunk (re)load it is null until the
 * cable's delayed ticker re-attaches it, and the saved networks (world data, outliving their chunks) keep their elements
 * meanwhile. A part removed in that window leaves a dangling element, the record F-49 then meets on the kill path
 * ([[Mod Fixes - Assessment Integrated Dynamics Part State Crash]] section 2, candidate A). Mod at fault: Integrated Dynamics.
 *
 * <p>The fix ({@code IdPartRemovalMixin} through {@code OrphanRemovalHooks}, which names Integrated Dynamics' types; this class
 * names none) runs at the head of {@code removePart}: when the container has a part on that side and no network, the saved
 * networks are searched for a part element at that position and side, and each found is removed from its network through the
 * network's own removal (Pre with its cancellable event, then {@code beforeNetworkKill}, {@code onNetworkRemoval} and the Post
 * event; the part still exists, so those run as on a normal removal, F-49 guarding them besides). The element's
 * {@code onPreRemoved} and {@code onPostRemoved}, empty in this build and in Integrated Tunnels, Terminals and Crafting, are
 * not run; the drop is the carrier-less branch's own. A network listener that refuses the Pre event leaves the record, which
 * is then logged once (F-49 covers its kill). The part's own removal then proceeds as Integrated Dynamics wrote it. One WARN
 * per position and side per server run names what was found. Same switch as F-49, on before the server config has loaded.
 * Pure {@link #shouldSearch} for the tests.
 */
public final class OrphanRemoval {
    public static final String ID = "F-50";
    public static final String WHAT = "a part removed from an Integrated Dynamics cable whose carrier has no network at that moment is also removed from the saved network that still lists it, so no dangling element is left for the kill path (the carrier-less branch of removePart told no network)";
    public static final String TARGET = IdGate.TARGET;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final LongAdder REMOVED = new LongAdder();
    private static final LongAdder REFUSED = new LongAdder();

    private OrphanRemoval() {}

    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.ID_SKIP_DANGLING_PARTS.get();
    }

    /** Pure: whether the saved networks are searched: the switch on, a part on the side, and no network on the cable's carrier. */
    public static boolean shouldSearch(boolean enabled, boolean hasPart, boolean carrierHasNetwork) {
        return enabled && hasPart && !carrierHasNetwork;
    }

    /** Records what a search found and removed: the counters, and the WARN lines once per position and side per run (a refusal has its own). */
    public static void record(String level, BlockPos pos, Direction side, int found, int removed) {
        if (found <= 0) {
            return;
        }
        REMOVED.add(removed);
        REFUSED.add(found - removed);
        String key = DanglingParts.key(level, pos, side);
        if (removed > 0 && WARNED.add(key)) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-50: a part on {} of {} in {} was removed while its cable had no network; its record was found in {} saved network(s) and removed with it, so nothing dangles",
                    side.getSerializedName(), pos.toShortString(), level, removed);
        }
        if (found > removed && WARNED.add("refused:" + key)) {
            Fixes.LOGGER.warn("BSP-Mod Fixes F-50: a part on {} of {} in {} was removed while its cable had no network, and a network listener refused the removal of {} of its {} record(s); they stay (F-49 skips them on the kill path)",
                    side.getSerializedName(), pos.toShortString(), level, found - removed, found);
        }
    }

    /** How many records a listener refused to let go so far. */
    public static long refused() {
        return REFUSED.sum();
    }

    public static long removed() {
        return REMOVED.sum();
    }

    public static Set<String> warned() {
        return Set.copyOf(WARNED);
    }
}
