package com.mrgregles.bsp_mod_fixes.fix.integrateddynamics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.api.network.IPartNetworkElement;
import org.cyclops.integrateddynamics.core.network.Network;
import org.cyclops.integrateddynamics.core.persist.world.NetworkWorldStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * F-50's handler, the part that names Integrated Dynamics' types: called by {@code IdPartRemovalMixin} at the head of
 * {@code removePart}, so this class loads only where the mixin applies. The rule is {@link OrphanRemoval}'s.
 */
public final class OrphanRemovalHooks {
    private OrphanRemovalHooks() {}

    /** Whether a network element is the part element at this position and side. */
    public static boolean matches(INetworkElement element, String level, BlockPos pos, Direction side) {
        if (!(element instanceof IPartNetworkElement<?, ?> part)) {
            return false;
        }
        DimPos at = part.getPosition();
        return at != null && at.getLevel().equals(level) && at.getBlockPos().equals(pos) && part.getSide() == side;
    }

    /**
     * The head of {@code removePart}: with a part on the side, no network on the carrier and the switch on, every saved network's
     * element for this position and side is removed from its network. Returns how many were removed.
     */
    public static int beforeRemoval(@Nullable DimPos position, Direction side, boolean hasPart, @Nullable INetwork carrierNetwork) {
        if (position == null || !OrphanRemoval.shouldSearch(OrphanRemoval.enabled(), hasPart, carrierNetwork != null)) {
            return 0;
        }
        int removed = 0;
        int found = 0;
        // getNetworks() answers an unmodifiable copy; the Pre and Post removal are Network's own, not on the INetwork interface
        List<INetwork> networks = new ArrayList<>(NetworkWorldStorage.getInstance(IntegratedDynamics._instance).getNetworks());
        for (INetwork candidate : networks) {
            if (!(candidate instanceof Network network)) {
                continue;
            }
            List<INetworkElement> matching = new ArrayList<>();
            for (INetworkElement element : network.getElements()) {
                if (matches(element, position.getLevel(), position.getBlockPos(), side)) {
                    matching.add(element);
                }
            }
            found += matching.size();
            for (INetworkElement element : matching) {
                if (network.removeNetworkElementPre(element)) { // a listener may refuse through the cancellable Pre event
                    network.removeNetworkElementPost(element);
                    removed++;
                }
            }
        }
        OrphanRemoval.record(position.getLevel(), position.getBlockPos(), side, found, removed);
        return removed;
    }
}
