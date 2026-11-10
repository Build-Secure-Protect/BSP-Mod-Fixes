package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.cm.CmGate;
import com.mrgregles.bsp_mod_fixes.fix.cm.LoadSaves;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * MF-26: Compact Machines no longer saves its room dimension when a machine block loads (F-28). The rule and the gate are
 * pure; with {@code -PwithCm} the mod's real chunk-mode code is called (by reflection, so this class loads without the
 * mod) on the test level and a test-only mixin counts the saves it makes.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class CompactMachinesGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void compact_machines_save_rule_follows_the_switch_and_the_gate_holds(GameTestHelper h) {
        h.assertTrue(LoadSaves.shouldSkip(true) && !LoadSaves.shouldSkip(false), "the save is skipped only while the switch is on");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.CM_SKIP_SAVE_ON_LOAD.get() && LoadSaves.enabled(), "the switch is on by default");

        h.assertTrue(CmGate.verdict(Optional.of(CmGate.VERSION)).applies(), "exactly " + CmGate.VERSION + " applies");
        h.assertTrue(CmGate.verdict(Optional.of("6.0.2")) == CmGate.Verdict.OTHER_VERSION && CmGate.verdict(Optional.of("7.0.81")) == CmGate.Verdict.OTHER_VERSION, "an older and a newer build are skipped");
        h.assertTrue(CmGate.verdict(Optional.empty()) == CmGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(CmGate.verdict(Optional.of(CmGate.VERSION), false) == CmGate.Verdict.CHANGED_TARGET, "6.0.3 without the patched methods is skipped");
        h.assertTrue(CmGate.gates("CmTunnelSaveMixin") && CmGate.gates("CmChunkloadSaveMixin") && !CmGate.gates("GobberDragonFlightMixin"), "the gate decides for its own mixins only");
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void compact_machines_forces_and_releases_a_room_without_saving_the_dimension_and_the_switch_restores_its_own(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded(CmGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(LoadSaves.ID) && skipped.get(LoadSaves.ID).equals(CmGate.Verdict.ABSENT.reason()),
                    "without Compact Machines the plugin skipped F-28 with the absent reason, got " + skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes test: Compact Machines is not loaded, its chunk-mode code is not run (add -PwithCm)");
            h.succeed();
            return;
        }
        h.assertTrue(BSPModFixes.version(CmGate.MOD_ID).equals(CmGate.VERSION), "Compact Machines " + CmGate.VERSION + ", found " + BSPModFixes.version(CmGate.MOD_ID));
        // Compact Machines' own classes, by name: loading them is what applies the two mixins
        Method setChunkMode = Class.forName("dev.compactmods.machines.tunnel.TunnelHelper").getMethod("setChunkMode", ServerLevel.class, ChunkPos.class, boolean.class);
        Class<?> upgradeClass = Class.forName("dev.compactmods.machines.upgrade.ChunkloadUpgrade");
        Object upgrade = upgradeClass.getConstructor().newInstance();
        Method onAdded = upgradeClass.getMethod("onAdded", ServerLevel.class, ChunkPos.class);
        Method onRemoved = upgradeClass.getMethod("onRemoved", ServerLevel.class, ChunkPos.class);
        // With -PskipFixes=CmTunnelSaveMixin,CmChunkloadSaveMixin the code runs as Compact Machines ships it: one save a call.
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(LoadSaves.ID));
        h.assertTrue(fixOn || Fixes.skipped().containsKey(LoadSaves.ID), "F-28 is applied or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        Fixes.LOGGER.info("BSP-Mod Fixes test: Compact Machines' chunk-mode code runs {}", fixOn ? "with F-28" : "as shipped (F-28 switched off)");
        int each = fixOn ? 0 : 1;

        // the method takes any level and chunk: a far chunk of the test level stands in for a room (the room dimension is not in a GameTest world)
        ServerLevel level = h.getLevel();
        ChunkPos here = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        ChunkPos room = new ChunkPos(here.x + 64, here.z + 64);
        ChunkPos upgraded = new ChunkPos(here.x + 64, here.z + 66);
        h.assertTrue(!level.getForcedChunks().contains(room.toLong()) && !level.getForcedChunks().contains(upgraded.toLong()), "nothing forced yet");
        long skipped = LoadSaves.skipped(), passed = LoadSaves.passed();
        try {
            // a machine with tunnels loading: the room is forced; Compact Machines then saved the whole dimension
            int saves = SaveCounter.saves();
            setChunkMode.invoke(null, level, room, true);
            h.assertTrue(level.getForcedChunks().contains(room.toLong()), "the room's chunk is force-loaded, save or no save");
            h.assertTrue(SaveCounter.saves() == saves + each, "saves of the dimension for one machine load: " + each + " expected, found " + (SaveCounter.saves() - saves));

            // ten machines of one base loading in a tick
            saves = SaveCounter.saves();
            for (int i = 0; i < 10; i++) {
                setChunkMode.invoke(null, level, room, true);
            }
            h.assertTrue(SaveCounter.saves() == saves + 10 * each, "ten machine loads: " + 10 * each + " saves expected, found " + (SaveCounter.saves() - saves));

            // the Chunkloader upgrade applied and removed, as Compact Machines forces it: the room rule of F-29, which takes
            // the upgrade's forcing over when it is on, is switched off for these two calls so the save is what is seen
            boolean roomRule = FixesConfig.CM_ROOMS_FOLLOW_MACHINES.get();
            try {
                FixesConfig.CM_ROOMS_FOLLOW_MACHINES.set(false);
                saves = SaveCounter.saves();
                onAdded.invoke(upgrade, level, upgraded);
                h.assertTrue(level.getForcedChunks().contains(upgraded.toLong()), "the upgrade force-loads its room");
                onRemoved.invoke(upgrade, level, upgraded);
                h.assertTrue(!level.getForcedChunks().contains(upgraded.toLong()), "and releases it when removed");
                h.assertTrue(SaveCounter.saves() == saves + 2 * each, "the upgrade's two calls: " + 2 * each + " saves expected, found " + (SaveCounter.saves() - saves));
            } finally {
                FixesConfig.CM_ROOMS_FOLLOW_MACHINES.set(roomRule);
            }

            if (fixOn) {
                h.assertTrue(LoadSaves.skipped() == skipped + 13 && LoadSaves.passed() == passed, "the fix skipped all 13 saves, found " + (LoadSaves.skipped() - skipped));
                // the switch: off, Compact Machines' save is back
                boolean saved = FixesConfig.CM_SKIP_SAVE_ON_LOAD.get();
                try {
                    FixesConfig.CM_SKIP_SAVE_ON_LOAD.set(false);
                    saves = SaveCounter.saves();
                    setChunkMode.invoke(null, level, room, true);
                    h.assertTrue(SaveCounter.saves() == saves + 1 && LoadSaves.passed() == passed + 1, "switched off, the save runs as Compact Machines makes it");
                } finally {
                    FixesConfig.CM_SKIP_SAVE_ON_LOAD.set(saved);
                }
            }
        } finally {
            // a machine without tunnels loading: the room is released
            setChunkMode.invoke(null, level, room, false);
        }
        h.assertTrue(!level.getForcedChunks().contains(room.toLong()), "the room's chunk is released again");
        h.succeed();
    }
}
