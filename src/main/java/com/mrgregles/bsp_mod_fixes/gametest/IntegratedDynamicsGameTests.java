package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.integrateddynamics.DanglingParts;
import com.mrgregles.bsp_mod_fixes.fix.integrateddynamics.IdGate;
import com.mrgregles.bsp_mod_fixes.fix.integrateddynamics.OrphanRemoval;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * MF-45: Integrated Dynamics' dangling part records (F-49, F-50). The pure rules and the gate always; with {@code -PwithId}
 * a real cable network (a cable with a Block Reader), the handoff's forced desync, a carrier-less removal, a normal removal
 * and the switch, through {@link IdTestBridge}. No Integrated Dynamics class is named here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class IntegratedDynamicsGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 400)
    public void a_dangling_part_record_is_skipped_on_kill_and_a_carrierless_removal_leaves_none(GameTestHelper h) {
        // the rules
        int[] calls = {0};
        java.util.function.Predicate<RuntimeException> partState = e -> e instanceof IllegalStateException; // stands in for PartStateException here
        h.assertTrue(DanglingParts.decide(true, true, () -> calls[0]++, partState) == DanglingParts.Outcome.CALLED && calls[0] == 1, "a present part: the call runs");
        h.assertTrue(DanglingParts.decide(true, false, () -> calls[0]++, partState) == DanglingParts.Outcome.SKIPPED_MISSING && calls[0] == 1, "a missing part: skipped, no call");
        h.assertTrue(DanglingParts.decide(true, true, () -> { throw new IllegalStateException("no part state"); }, partState) == DanglingParts.Outcome.SKIPPED_EXCEPTION, "a call that throws the part state exception: skipped");
        boolean otherEscapes = false;
        try {
            DanglingParts.decide(true, true, () -> { throw new IllegalArgumentException("something else"); }, partState);
        } catch (IllegalArgumentException e) {
            otherEscapes = true;
        }
        h.assertTrue(otherEscapes, "any other exception goes on as before");
        h.assertTrue(DanglingParts.decide(false, false, () -> calls[0]++, partState) == DanglingParts.Outcome.CALLED && calls[0] == 2, "the switch off: the call runs whatever the part");
        h.assertTrue(DanglingParts.key("minecraft:overworld", new BlockPos(4, 1, -1013), Direction.SOUTH).equals("minecraft:overworld4, 1, -1013/south") && DanglingParts.key(null, null, null).equals("?/?"), "the once-per-run key");
        h.assertTrue(OrphanRemoval.shouldSearch(true, true, false) && !OrphanRemoval.shouldSearch(true, true, true) && !OrphanRemoval.shouldSearch(true, false, false) && !OrphanRemoval.shouldSearch(false, true, false),
                "the saved networks are searched only with the switch on, a part on the side and no network on the carrier");
        h.assertTrue(IdGate.verdict(Optional.empty(), true, true) == IdGate.Verdict.ABSENT && IdGate.verdict(Optional.of("1.31.4"), true, true) == IdGate.Verdict.OTHER_VERSION
                && IdGate.verdict(Optional.of(IdGate.VERSION), false, true) == IdGate.Verdict.CHANGED_TARGET && IdGate.verdict(Optional.of(IdGate.VERSION), true, false) == IdGate.Verdict.NO_LIBRARIES
                && IdGate.verdict(Optional.of(IdGate.VERSION), true, true) == IdGate.Verdict.APPLY, "the gate's five verdicts");
        h.assertTrue(IdGate.gates("IdNetworkKillMixin") && IdGate.gates("IdPartRemovalMixin") && !IdGate.gates("MffsSpongeMixin"), "the gate decides for the two mixins");

        Map<String, String> skipped = Fixes.skipped();
        if (!net.minecraftforge.fml.ModList.get().isLoaded(IdGate.MOD_ID)) {
            h.assertTrue(IdGate.Verdict.ABSENT.reason().equals(skipped.get(DanglingParts.ID)) && IdGate.Verdict.ABSENT.reason().equals(skipped.get(OrphanRemoval.ID)),
                    "without Integrated Dynamics both fixes are skipped as absent, got " + skipped);
            Fixes.LOGGER.info("BSP-Mod Fixes test: Integrated Dynamics is not loaded, no network is built (add -PwithId)");
            h.succeed();
            return;
        }
        boolean killFixOn = !skipped.containsKey(DanglingParts.ID);
        boolean removalFixOn = !skipped.containsKey(OrphanRemoval.ID);
        h.assertTrue(killFixOn || MixinFixPlugin.SKIP_REASON.equals(skipped.get(DanglingParts.ID)), "F-49 is applied or switched off for this run: " + skipped);
        h.assertTrue(removalFixOn || MixinFixPlugin.SKIP_REASON.equals(skipped.get(OrphanRemoval.ID)), "F-50 is applied or switched off for this run: " + skipped);
        h.assertTrue(FixesConfig.SPEC.isLoaded(), "the server config is loaded on the test server");

        ServerLevel level = h.getLevel();
        Direction side = Direction.SOUTH;
        h.assertTrue(IdTestBridge.decideAgainstARealPartStateException(level, h.absolutePos(BlockPos.ZERO), side) == DanglingParts.Outcome.SKIPPED_EXCEPTION, "Integrated Dynamics' own part state exception is the one caught");
        BlockPos desync = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos orphan = h.absolutePos(new BlockPos(6, 2, 2));
        BlockPos live = h.absolutePos(new BlockPos(10, 2, 2));
        IdTestBridge.placeCableWithReader(level, desync, side);
        IdTestBridge.placeCableWithReader(level, orphan, side);
        IdTestBridge.placeCableWithReader(level, live, side);

        h.runAfterDelay(10, () -> {
            // F-49: the handoff's forced desync, then the kill that crashed Frostwarf
            Object network = IdTestBridge.network(level, desync, side);
            h.assertTrue(IdTestBridge.elementsAt(network, level, desync, side) == 1, "the network holds the reader's element");
            IdTestBridge.removePartBehindTheNetworksBack(level, desync, side);
            h.assertTrue(IdTestBridge.elementsAt(network, level, desync, side) == 1, "the record is now stale: the element stays while the container has no part");
            long skippedBefore = DanglingParts.skipped();
            boolean threw = IdTestBridge.killThrowsPartState(network);
            String key = IdTestBridge.key(level, desync, side);
            if (killFixOn) {
                h.assertFalse(threw, "with F-49 the kill finishes");
                h.assertTrue(DanglingParts.skipped() == skippedBefore + 1, "one call skipped, got " + (DanglingParts.skipped() - skippedBefore));
                h.assertTrue(DanglingParts.warned().contains(key), "the WARN names the position and side: " + DanglingParts.warned());
            } else {
                h.assertTrue(threw, "as shipped the kill throws PartStateException");
            }

            // F-50: a part removed while its cable's carrier has no network
            Object orphanNetwork = IdTestBridge.network(level, orphan, side);
            h.assertTrue(IdTestBridge.elementsAt(orphanNetwork, level, orphan, side) == 1, "the second network holds its reader's element");
            IdTestBridge.clearCarrier(level, orphan);
            h.assertFalse(IdTestBridge.carrierHasNetwork(level, orphan), "the carrier has no network now");
            int itemsBefore = IdTestBridge.itemEntitiesNear(level, orphan);
            long removedBefore = OrphanRemoval.removed();
            h.assertTrue(IdTestBridge.removePartNormally(level, orphan, side), "the normal removal succeeds");
            h.assertFalse(IdTestBridge.hasPart(level, orphan, side), "the part has left the container");
            h.assertTrue(IdTestBridge.itemEntitiesNear(level, orphan) == itemsBefore + 1, "the part's item dropped, as before");
            if (removalFixOn) {
                h.assertTrue(IdTestBridge.elementsAt(orphanNetwork, level, orphan, side) == 0, "with F-50 the saved network's record went with the part");
                h.assertTrue(OrphanRemoval.removed() == removedBefore + 1, "one record removed, got " + (OrphanRemoval.removed() - removedBefore));
            } else {
                h.assertTrue(IdTestBridge.elementsAt(orphanNetwork, level, orphan, side) == 1, "as shipped the carrier-less removal leaves the record dangling");
            }
            boolean orphanThrew = IdTestBridge.killThrowsPartState(orphanNetwork);
            h.assertTrue(orphanThrew == (!removalFixOn && !killFixOn), "the kill of the second network: throws only with both fixes off, got " + orphanThrew);

            // a live part: the kill runs the part's own hook, nothing skipped, no line
            Object liveNetwork = IdTestBridge.network(level, live, side);
            long skippedBeforeLive = DanglingParts.skipped();
            h.assertFalse(IdTestBridge.killThrowsPartState(liveNetwork), "a network with a live part kills cleanly");
            h.assertTrue(DanglingParts.skipped() == skippedBeforeLive, "nothing skipped for a live part");
            h.assertFalse(DanglingParts.warned().contains(IdTestBridge.key(level, live, side)), "no line for a live part");

            // the switch off: Integrated Dynamics as shipped. The kill throws before the network is marked killed, so that network keeps
            // ticking and Integrated Dynamics' own update guard prints its "Attempted to tick a part that was not properly unloaded"
            // line with a stack trace in the log: expected noise of this test, not a failure.
            boolean saved = FixesConfig.ID_SKIP_DANGLING_PARTS.get();
            try {
                FixesConfig.ID_SKIP_DANGLING_PARTS.set(false);
                BlockPos off = h.absolutePos(new BlockPos(2, 2, 6));
                IdTestBridge.placeCableWithReader(level, off, side);
                Object offNetwork = IdTestBridge.network(level, off, side);
                IdTestBridge.removePartBehindTheNetworksBack(level, off, side);
                h.assertTrue(IdTestBridge.killThrowsPartState(offNetwork), "with the switch off the kill throws as shipped");
            } finally {
                FixesConfig.ID_SKIP_DANGLING_PARTS.set(saved);
            }
            h.succeed();
        });
    }
}
