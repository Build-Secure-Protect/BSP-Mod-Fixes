package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * MF-40, a measurement, no fix: a player's report that an Ultimate Infusing Factory (and a Metallurgic Infuser) placed since
 * Evolved Mekanism came into the pack take no infuse type from a tube though every side is set to Input, while infuse type
 * put in by hand works. With {@code -PwithMekanism} (Mekanism alone) or {@code -PwithHazmat} (Mekanism with Evolved Mekanism)
 * the test places both machines by block state (a mock player's block item trips Mekanism's security owner lookup on the
 * GameTest server, which has no profile cache), and with Evolved Mekanism also its Overclocked tube beside Mekanism's factory and
 * its Overclocked factory beside Mekanism's tube; sets every infusion side to Input, puts a tube and a source chemical tank full
 * of redstone infusion beside each, and logs what Mekanism
 * does: the configuration, the capability a neighbour sees from each direction with a simulated insert, the tube's connection,
 * and what arrived after three seconds. It passes unless something throws; the log is the result. No class of Mekanism is named here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class MekanismInfusionGameTests {
    private static final String FACTORY = "mekanism:ultimate_infusing_factory";
    private static final String INFUSER = "mekanism:metallurgic_infuser";
    private static final String TUBE = "mekanism:ultimate_pressurized_tube";
    private static final String TANK = "mekanism:ultimate_chemical_tank";
    private static final String QE = "mekanism:quantum_entangloporter";

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 200)
    public void measure_whether_an_infusing_machine_with_every_side_set_to_input_takes_infusion_from_a_tube(GameTestHelper h) {
        if (!ModList.get().isLoaded("mekanism")) {
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): Mekanism is not loaded, nothing is placed (add -PwithMekanism or -PwithHazmat)");
            h.succeed();
            return;
        }
        boolean evolved = ModList.get().isLoaded("evolvedmekanism");
        Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): Mekanism {}{}", BSPModFixes.version("mekanism"), evolved ? " with Evolved Mekanism " + BSPModFixes.version("evolvedmekanism") : " alone");
        ServerLevel level = h.getLevel();
        Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {}", MekanismInfusionTestBridge.ensureProfileCache(level.getServer()));
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        net.minecraft.world.phys.Vec3 at = h.absoluteVec(new net.minecraft.world.phys.Vec3(12, 2, 22));
        player.moveTo(at.x, at.y, at.z, 0f, 0f); // facing south, so a block it places faces north
        // Lines, each: machine at x=3, tube at x=4, source tank at x=5; the tube's machine side is WEST. The Evolved Mekanism lines
        // run only with that mod: its Overclocked tube beside Mekanism's factory, and its Overclocked factory beside Mekanism's tube.
        List<String> names = new ArrayList<>(List.of("Ultimate Infusing Factory + Ultimate tube", "Metallurgic Infuser + Ultimate tube"));
        List<String> machineIds = new ArrayList<>(List.of(FACTORY, INFUSER));
        List<String> tubeIds = new ArrayList<>(List.of(TUBE, TUBE));
        if (evolved) {
            names.add("Ultimate Infusing Factory + Overclocked tube (Evolved Mekanism)");
            machineIds.add(FACTORY);
            tubeIds.add("evolvedmekanism:overclocked_pressurized_tube");
            names.add("Overclocked Infusing Factory (Evolved Mekanism) + Ultimate tube");
            machineIds.add("evolvedmekanism:overclocked_infusing_factory");
            tubeIds.add(TUBE);
        }
        int n = names.size();
        BlockEntity[] machines = new BlockEntity[n];
        BlockEntity[] tubes = new BlockEntity[n];
        BlockEntity[] sources = new BlockEntity[n];
        for (int i = 0; i < n; i++) {
            int z = 3 + 4 * i;
            BlockPos machinePos = h.absolutePos(new BlockPos(3, 2, z));
            machines[i] = MekanismInfusionTestBridge.place(level, machinePos, machineIds.get(i), Direction.NORTH);
            h.assertTrue(machines[i] != null, names.get(i) + ": a tile entity was made");
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} as placed: {}", names.get(i), MekanismInfusionTestBridge.configReport(machines[i]));
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} as placed, a neighbour sees: {}", names.get(i), MekanismInfusionTestBridge.capabilityReport(machines[i]));
            MekanismInfusionTestBridge.setAllInfusionInput(machines[i]);
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} all Input: {}", names.get(i), MekanismInfusionTestBridge.configReport(machines[i]));
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} all Input, a neighbour sees: {}", names.get(i), MekanismInfusionTestBridge.capabilityReport(machines[i]));
            tubes[i] = MekanismInfusionTestBridge.place(level, h.absolutePos(new BlockPos(4, 2, z)), tubeIds.get(i), Direction.NORTH);
            sources[i] = MekanismInfusionTestBridge.place(level, h.absolutePos(new BlockPos(5, 2, z)), TANK, Direction.NORTH);
            MekanismInfusionTestBridge.makeSource(sources[i], 10_000);
        }
        // Two entangloporter lines, as the player's screenshots suggest: a machine placed by the player with the entangloporter put
        // beside it afterwards, and an entangloporter already there, configured, before the machine is placed beside it.
        String[] qeNames = {"Ultimate Infusing Factory placed by a player, then an entangloporter", "an entangloporter first, then an Ultimate Infusing Factory placed by a player"};
        BlockEntity[] qeMachines = new BlockEntity[2];
        BlockEntity[] qes = new BlockEntity[2];
        for (int i = 0; i < 2; i++) {
            int z = 3 + 4 * (n + i);
            BlockPos machinePos = h.absolutePos(new BlockPos(3, 2, z));
            BlockPos qePos = h.absolutePos(new BlockPos(4, 2, z));
            if (i == 1) {
                qes[i] = MekanismInfusionTestBridge.place(level, qePos, QE, Direction.NORTH);
                MekanismInfusionTestBridge.makeEntangloporterSource(qes[i], player, Direction.WEST, 10_000);
            }
            qeMachines[i] = MekanismInfusionTestBridge.placeByPlayer(level, player, machinePos, FACTORY);
            h.assertTrue(qeMachines[i] != null, qeNames[i] + ": a tile entity was made by the player's placement");
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} as placed: {}", qeNames[i], MekanismInfusionTestBridge.configReport(qeMachines[i]));
            MekanismInfusionTestBridge.setAllInfusionInput(qeMachines[i]);
            if (i == 0) {
                qes[i] = MekanismInfusionTestBridge.place(level, qePos, QE, Direction.NORTH);
                MekanismInfusionTestBridge.makeEntangloporterSource(qes[i], player, Direction.WEST, 10_000);
            }
            Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {}: entangloporter {}", qeNames[i], MekanismInfusionTestBridge.entangloporterReport(qes[i]));
        }
        h.runAfterDelay(60, () -> {
            for (int i = 0; i < 2; i++) {
                Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} after 60 ticks: machine holds {}; entangloporter {}", qeNames[i],
                        MekanismInfusionTestBridge.stored(qeMachines[i]), MekanismInfusionTestBridge.entangloporterReport(qes[i]));
            }
            for (int i = 0; i < n; i++) {
                Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} after 60 ticks: tube {}; machine holds {}; {}", names.get(i),
                        MekanismInfusionTestBridge.tubeReport(tubes[i], Direction.WEST), MekanismInfusionTestBridge.stored(machines[i]), MekanismInfusionTestBridge.sourceStored(sources[i]));
                Fixes.LOGGER.info("BSP-Mod Fixes measure (MF-40): {} after 60 ticks, a neighbour sees: {}", names.get(i), MekanismInfusionTestBridge.capabilityReport(machines[i]));
            }
            h.succeed();
        });
    }
}
