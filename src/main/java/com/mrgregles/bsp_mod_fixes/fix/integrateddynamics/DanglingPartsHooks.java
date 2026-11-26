package com.mrgregles.bsp_mod_fixes.fix.integrateddynamics;

import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.PartStateException;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.api.network.IPartNetworkElement;
import org.cyclops.integrateddynamics.api.part.IPartContainer;

import java.util.function.Consumer;

/**
 * F-49's handlers, the part that names Integrated Dynamics' types: called by {@code IdNetworkKillMixin} in place of the three
 * calls, so this class loads only where the mixin applies (exactly the pinned build). The rules are {@link DanglingParts}'.
 */
public final class DanglingPartsHooks {
    private DanglingPartsHooks() {}

    /** Whether a runtime exception is Integrated Dynamics' part state exception, the one the fix swallows. */
    public static boolean isPartState(RuntimeException e) {
        return e instanceof PartStateException;
    }

    /** Whether the element's part is still in its container: the element's own container lookup (Integrated Dynamics' call makes the same one next, loading the chunk if needed) has a part on its side. */
    public static boolean partPresent(INetworkElement element) {
        if (!(element instanceof IPartNetworkElement<?, ?> part)) {
            return true; // not a part: nothing of ours to check
        }
        try {
            IPartContainer container = part.getPartContainer();
            return container != null && container.hasPart(part.getSide());
        } catch (PartStateException e) {
            return false; // the container is not there (the block entity or its capability gone)
        }
    }

    /** In place of {@code element.beforeNetworkKill(network)}. */
    public static void beforeNetworkKill(INetworkElement element, INetwork network, String path) {
        guarded(element, path, e -> e.beforeNetworkKill(network));
    }

    /** In place of {@code element.onNetworkRemoval(network)}. */
    public static void onNetworkRemoval(INetworkElement element, INetwork network, String path) {
        guarded(element, path, e -> e.onNetworkRemoval(network));
    }

    private static void guarded(INetworkElement element, String path, Consumer<INetworkElement> call) {
        boolean enabled = DanglingParts.enabled();
        DanglingParts.Outcome outcome = DanglingParts.decide(enabled, !enabled || partPresent(element), () -> call.accept(element), DanglingPartsHooks::isPartState);
        if (outcome != DanglingParts.Outcome.CALLED) {
            DimPos pos = element instanceof IPartNetworkElement<?, ?> p ? p.getPosition() : null;
            DanglingParts.record(pos == null ? null : pos.getLevel(), pos == null ? null : pos.getBlockPos(),
                    element instanceof IPartNetworkElement<?, ?> p ? p.getSide() : null, path, outcome);
        }
    }
}
