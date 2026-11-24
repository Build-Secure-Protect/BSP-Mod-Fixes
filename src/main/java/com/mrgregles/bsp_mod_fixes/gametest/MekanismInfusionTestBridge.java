package com.mrgregles.bsp_mod_fixes.gametest;

import mekanism.api.Action;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.IInfusionTank;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.frequency.Frequency;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismInfuseTypes;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mekanism's own classes for the MF-40 measurement, named here only, so the test class loads without the mod; a test touches
 * this class only after it has seen that Mekanism is loaded. Places machines, tubes and tanks, sets a machine's infusion side
 * configuration, and reports what a tube would see of the machine: the capability per side, a simulated insert, the tube's own
 * connection and the amount that actually arrived.
 */
final class MekanismInfusionTestBridge {
    private MekanismInfusionTestBridge() {}

    static Block block(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("no block " + id);
        }
        return block;
    }

    /** Places a block by setting its state, facing as asked when the block has a horizontal facing; the tile entity it makes. */
    static BlockEntity place(ServerLevel level, BlockPos pos, String id, Direction facing) {
        BlockState state = block(id).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        level.setBlock(pos, state, 3);
        return level.getBlockEntity(pos);
    }

    /**
     * The GameTest server runs with no profile cache, and Mekanism's username lookup for a security owner or a frequency owner
     * dereferences it, so a mock player cannot place a Mekanism machine there as shipped. Gives the server an empty cache (dev
     * only: the field is reached by its name in the mapped environment). Returns what it did, for the log.
     */
    static String ensureProfileCache(MinecraftServer server) {
        if (server.getProfileCache() != null) {
            return "the server had a profile cache";
        }
        try {
            Field field = MinecraftServer.class.getDeclaredField("services");
            field.setAccessible(true);
            File file = File.createTempFile("bsp_mod_fixes_usercache", ".json");
            file.deleteOnExit();
            field.set(server, new Services(server.getSessionService(), null, server.getProfileRepository(), new GameProfileCache(server.getProfileRepository(), file)));
            return "an empty profile cache was given to the server";
        } catch (ReflectiveOperationException | java.io.IOException e) {
            return "no profile cache could be given to the server: " + e;
        }
    }

    /** Places a block the way a player does: the block item's place call on the block below, so Mekanism's setPlacedBy runs (owner, item data, facing from the player). */
    static BlockEntity placeByPlayer(ServerLevel level, ServerPlayer player, BlockPos pos, String id) {
        BlockPos below = pos.below();
        level.setBlock(below, Blocks.STONE.defaultBlockState(), 3);
        BlockItem item = (BlockItem) block(id).asItem();
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        return level.getBlockEntity(pos);
    }

    /**
     * A Quantum Entangloporter made into a source: a public inventory frequency owned by the player, its infusion tank full of
     * redstone, the entangloporter's Infuse Types side toward the machine set to Output and ejecting (what a player sets in its
     * side configuration).
     */
    static void makeEntangloporterSource(BlockEntity qeTile, ServerPlayer owner, Direction toMachine, long amount) {
        TileEntityQuantumEntangloporter qe = (TileEntityQuantumEntangloporter) qeTile;
        qe.setFrequency(FrequencyType.INVENTORY, new Frequency.FrequencyIdentity("bsp_mod_fixes_mf40", true), owner.getUUID());
        List<IInfusionTank> tanks = qe.getFreq().getInfusionTanks(null);
        tanks.get(0).setStack(new InfusionStack(MekanismInfuseTypes.REDSTONE, amount));
        TileComponentConfig config = qe.getConfig();
        ConfigInfo info = config.getConfig(TransmissionType.INFUSION);
        RelativeSide side = RelativeSide.fromDirections(qe.getDirection(), toMachine);
        info.setDataType(DataType.OUTPUT, side);
        info.setEjecting(true);
        config.sideChanged(TransmissionType.INFUSION, side);
    }

    static String entangloporterReport(BlockEntity qeTile) {
        TileEntityQuantumEntangloporter qe = (TileEntityQuantumEntangloporter) qeTile;
        return "frequency " + (qe.getFreq() == null ? "none" : qe.getFreq().getName() + (qe.getFreq().isValid() ? " valid" : " invalid") + ", tank " + qe.getFreq().getInfusionTanks(null).get(0).getStored() + " mB")
                + "; " + configReport(qeTile);
    }

    /** Every side of the machine's infusion configuration set to Input, as the player did, through Mekanism's own config component. */
    static void setAllInfusionInput(BlockEntity tile) {
        TileComponentConfig config = ((ISideConfiguration) tile).getConfig();
        ConfigInfo info = config.getConfig(TransmissionType.INFUSION);
        for (RelativeSide side : RelativeSide.values()) {
            info.setDataType(DataType.INPUT, side);
            config.sideChanged(TransmissionType.INFUSION, side);
        }
    }

    /** The infusion configuration per relative side: data type, whether its slot info exists, is enabled and takes input. */
    static String configReport(BlockEntity tile) {
        TileComponentConfig config = ((ISideConfiguration) tile).getConfig();
        ConfigInfo info = config.getConfig(TransmissionType.INFUSION);
        if (info == null) {
            return "no infusion config";
        }
        List<String> parts = new ArrayList<>();
        for (RelativeSide side : RelativeSide.values()) {
            ISlotInfo slot = info.getSlotInfo(side);
            parts.add(side + "=" + info.getDataType(side) + (slot == null ? "(no slot info)" : "(enabled " + slot.isEnabled() + ", input " + slot.canInput() + ")"));
        }
        return "facing " + ((TileEntityMekanism) tile).getDirection() + ", canEject " + info.canEject() + ", ejecting " + info.isEjecting() + ": " + String.join(", ", parts);
    }

    /** What a neighbour asking from each absolute direction sees: the infusion capability, and a simulated insert of 100 mB of redstone. */
    static String capabilityReport(BlockEntity tile) {
        List<String> parts = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            IInfusionHandler handler = CapabilityUtils.getCapability(tile, Capabilities.INFUSION_HANDLER, dir).resolve().orElse(null);
            if (handler == null) {
                parts.add(dir + "=absent");
            } else {
                InfusionStack left = handler.insertChemical(new InfusionStack(MekanismInfuseTypes.REDSTONE, 100), Action.SIMULATE);
                parts.add(dir + "=present(" + handler.getTanks() + " tanks, simulated insert of 100 leaves " + left.getAmount() + ")");
            }
        }
        return String.join(", ", parts);
    }

    /** The machine's infusion tank contents. */
    static String stored(BlockEntity tile) {
        List<IInfusionTank> tanks = ((TileEntityMekanism) tile).getInfusionTanks(null);
        if (tanks.isEmpty()) {
            return "no infusion tank";
        }
        IInfusionTank tank = tanks.get(0);
        return tank.getStored() + " mB of " + (tank.isEmpty() ? "nothing" : tank.getType().getRegistryName());
    }

    /** A tube's view of its neighbour in one direction: the connection type Mekanism decided, and whether an acceptor is cached. */
    static String tubeReport(BlockEntity tube, Direction toMachine) {
        TileEntityTransmitter t = (TileEntityTransmitter) tube;
        return "connection " + t.getTransmitter().getConnectionType(toMachine) + ", raw " + t.getTransmitter().getConnectionTypeRaw(toMachine)
                + ", cached acceptor " + t.getTransmitter().getAcceptorCache().getCachedAcceptor(toMachine).isPresent();
    }

    /** A chemical tank made into a source: full of redstone infusion, every side Output, ejecting. */
    static void makeSource(BlockEntity tankTile, long amount) {
        TileEntityChemicalTank tank = (TileEntityChemicalTank) tankTile;
        IChemicalTank<?, ?> infusion = tank.getChemicalTank().getInfusionTank();
        ((IInfusionTank) infusion).setStack(new InfusionStack(MekanismInfuseTypes.REDSTONE, amount));
        TileComponentConfig config = tank.getConfig();
        ConfigInfo info = config.getConfig(TransmissionType.INFUSION);
        info.fill(DataType.OUTPUT);
        info.setEjecting(true);
        for (RelativeSide side : RelativeSide.values()) {
            config.sideChanged(TransmissionType.INFUSION, side);
        }
    }

    static String sourceStored(BlockEntity tankTile) {
        return ((TileEntityChemicalTank) tankTile).getChemicalTank().getInfusionTank().getStored() + " mB left in the source";
    }
}
