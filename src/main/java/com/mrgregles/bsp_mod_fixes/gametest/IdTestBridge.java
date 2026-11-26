package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.integrateddynamics.DanglingParts;
import com.mrgregles.bsp_mod_fixes.fix.integrateddynamics.DanglingPartsHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.PartStateException;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.api.network.IPartNetworkElement;
import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.capability.partcontainer.PartContainerDefault;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;
import org.cyclops.integrateddynamics.core.network.Network;
import org.cyclops.integrateddynamics.core.part.PartTypes;

import java.util.Optional;

/**
 * Names Integrated Dynamics' classes for {@code IntegratedDynamicsGameTests}, so the test class loads without the mod. Only
 * called, and so only loaded, when the mod is loaded (a {@code -PwithId} run); networks cross as plain objects.
 */
final class IdTestBridge {
    private IdTestBridge() {}

    /** A cable block at {@code pos} with a Block Reader on {@code side}, as a player would place them. */
    static void placeCableWithReader(ServerLevel level, BlockPos pos, Direction side) {
        Block cable = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("integrateddynamics", "cable"));
        level.setBlock(pos, cable.defaultBlockState(), 3);
        if (!PartHelpers.addPart(level, pos, side, PartTypes.BLOCK_READER, new ItemStack(PartTypes.BLOCK_READER.getItem()))) {
            throw new AssertionError("the Block Reader was not added to the cable at " + pos);
        }
    }

    /** The cable's network, initialised if the cable has none yet; handed to the test as a plain object so the test class names no Integrated Dynamics type. */
    static Object network(ServerLevel level, BlockPos pos, Direction side) {
        Optional<INetwork> network = Optional.ofNullable(NetworkHelpers.getNetwork(level, pos, side).orElse(null));
        if (network.isEmpty()) {
            network = NetworkHelpers.initNetwork(level, pos, side);
        }
        return network.orElseThrow(() -> new AssertionError("the cable at " + pos + " has no network"));
    }

    /** How many part elements the network holds for this position and side. */
    static int elementsAt(Object network, ServerLevel level, BlockPos pos, Direction side) {
        int n = 0;
        for (INetworkElement element : ((INetwork) network).getElements()) {
            if (element instanceof IPartNetworkElement<?, ?> part && part.getPosition().getBlockPos().equals(pos)
                    && part.getPosition().getLevel().equals(level.dimension().location().toString()) && part.getSide() == side) {
                n++;
            }
        }
        return n;
    }

    /** The forced desync of the handoff: the part leaves the container's live map, no network is told. */
    static void removePartBehindTheNetworksBack(ServerLevel level, BlockPos pos, Direction side) {
        IPartContainer container = PartHelpers.getPartContainer(level, pos, side).orElseThrow(() -> new AssertionError("no part container at " + pos));
        ((PartContainerDefault) container).getPartData().remove(side);
        if (container.hasPart(side)) {
            throw new AssertionError("the part is still in the container at " + pos);
        }
    }

    /** The cable's carrier forgets its network, as it stands between a chunk load and the cable's ticker re-attaching it. */
    static void clearCarrier(ServerLevel level, BlockPos pos) {
        NetworkHelpers.getNetworkCarrier(level, pos, null).orElseThrow(() -> new AssertionError("no network carrier at " + pos)).setNetwork(null);
    }

    static boolean carrierHasNetwork(ServerLevel level, BlockPos pos) {
        return NetworkHelpers.getNetworkCarrier(level, pos, null).map(c -> c.getNetwork() != null).orElse(false);
    }

    /** Integrated Dynamics' own part removal (a wrench), dropping the part's item. */
    static boolean removePartNormally(ServerLevel level, BlockPos pos, Direction side) {
        return PartHelpers.removePart(level, pos, side, null, true, true, true);
    }

    static boolean hasPart(ServerLevel level, BlockPos pos, Direction side) {
        return PartHelpers.getPartContainer(level, pos, side).map(c -> c.hasPart(side)).orElse(false);
    }

    /** Kills the network as {@code killIfEmpty} would; whether a {@code PartStateException} came out of it. */
    static boolean killThrowsPartState(Object network) {
        try {
            ((Network) network).kill();
            return false;
        } catch (PartStateException e) {
            return true;
        }
    }

    /** The rule run against a call that throws Integrated Dynamics' own part state exception. */
    static DanglingParts.Outcome decideAgainstARealPartStateException(ServerLevel level, BlockPos pos, Direction side) {
        return DanglingParts.decide(true, true, () -> { throw new PartStateException(DimPos.of(level, pos), side); }, DanglingPartsHooks::isPartState);
    }

    /** The once-per-run key as the fix builds it from Integrated Dynamics' DimPos. */
    static String key(ServerLevel level, BlockPos pos, Direction side) {
        return DanglingParts.key(DimPos.of(level, pos).getLevel(), pos, side);
    }

    static int itemEntitiesNear(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).size();
    }
}
