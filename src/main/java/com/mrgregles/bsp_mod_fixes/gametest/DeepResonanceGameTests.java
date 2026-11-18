package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.deepresonance.DeepResonanceGate;
import com.mrgregles.bsp_mod_fixes.fix.deepresonance.PlaceEventLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * MF-35: Deep Resonance's place handler survives an event with no entity (F-38). The rule and the gate are pure; with
 * {@code -PwithDr} (the pack's exact Deep Resonance, McJtyLib and RFTools Base) a real {@code BlockEvent.EntityPlaceEvent}
 * with no entity is posted on the Forge bus for an obsidian block, as Ballistix's blast posts one for its debris, and Deep
 * Resonance's real handler answers it: with the fix nothing is thrown, with {@code -PskipFixes=DeepResonancePlaceEventMixin}
 * or the switch off the {@code NullPointerException} of the owner's report. No Deep Resonance class is named here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class DeepResonanceGameTests {
    private static final String HANDLER = "mcjty.deepresonance.ForgeEventHandlers";

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_place_event_without_a_placer_keeps_its_level_and_the_deep_resonance_gate_holds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Entity stand = EntityType.ARMOR_STAND.create(level);
        h.assertTrue(stand != null, "an armor stand can be made");
        // the rule
        h.assertTrue(!PlaceEventLevel.skip(stand, level, true) && !PlaceEventLevel.skip(stand, null, true), "an event with an entity is never skipped");
        h.assertTrue(!PlaceEventLevel.skip(null, level, true), "no entity but a real level: the handler runs on the event's level");
        h.assertTrue(PlaceEventLevel.skip(null, null, true), "no entity and no level: skipped");
        h.assertTrue(!PlaceEventLevel.skip(null, null, false), "switch off: nothing is skipped, Deep Resonance's own code runs");
        h.assertTrue(PlaceEventLevel.levelOf(stand, level, true) == stand.level() && PlaceEventLevel.levelOf(stand, level, false) == stand.level(), "with an entity, its level, either way");
        h.assertTrue(PlaceEventLevel.levelOf(null, level, true) == level, "without one and the fix on, the event's level");
        boolean threw = false;
        try {
            PlaceEventLevel.levelOf(null, level, false);
        } catch (NullPointerException e) {
            threw = true;
        }
        h.assertTrue(threw, "without one and the fix off, Deep Resonance's own call and its exception");
        threw = false;
        try {
            PlaceEventLevel.levelOf(null, null, true);
        } catch (NullPointerException e) {
            threw = true;
        }
        h.assertTrue(threw, "without one and without a level, the same call even with the fix on (the head guard returns first in the handler)");
        // the event path, on a real Forge event with no entity
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
        BlockEvent.EntityPlaceEvent event = placeEvent(level, pos, null);
        h.assertTrue(event.getEntity() == null && event.getLevel() == level && event.getPlacedBlock().is(Blocks.OBSIDIAN), "the event carries no entity, the level and the placed block");
        long before = PlaceEventLevel.fallbacks();
        h.assertTrue(PlaceEventLevel.levelOf(null, event) == level, "the event's level is answered");
        h.assertTrue(PlaceEventLevel.levelOf(stand, placeEvent(level, pos, stand)) == level, "an entity's level is answered");
        h.assertTrue(PlaceEventLevel.fallbacks() == before + 1, "one fallback counted, for the event without an entity");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.DR_PLACE_EVENTS_WITHOUT_PLACER.get() && PlaceEventLevel.enabled(), "the switch is on by default");
        // the gate
        h.assertTrue(DeepResonanceGate.verdict(Optional.of(DeepResonanceGate.VERSION)).applies(), "exactly " + DeepResonanceGate.VERSION + " applies");
        h.assertTrue(DeepResonanceGate.verdict(Optional.of("1.20-5.0.5")) == DeepResonanceGate.Verdict.OTHER_VERSION && DeepResonanceGate.verdict(Optional.of("1.20-5.0.7")) == DeepResonanceGate.Verdict.OTHER_VERSION, "other builds are skipped");
        h.assertTrue(DeepResonanceGate.verdict(Optional.empty()) == DeepResonanceGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(DeepResonanceGate.verdict(Optional.of(DeepResonanceGate.VERSION), false) == DeepResonanceGate.Verdict.CHANGED_TARGET, "the build without the method is skipped");
        h.assertTrue(DeepResonanceGate.gates("DeepResonancePlaceEventMixin") && !DeepResonanceGate.gates("MffsSpongeMixin"), "the gate decides for its own mixin only");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(PlaceEventLevel.ID) && f.targetMod().equals(DeepResonanceGate.TARGET)), "the registry knows F-38 as a Deep Resonance fix");
        if (!ModList.get().isLoaded(DeepResonanceGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(PlaceEventLevel.ID) && skipped.get(PlaceEventLevel.ID).equals(DeepResonanceGate.Verdict.ABSENT.reason()),
                    "without Deep Resonance the plugin skipped F-38 as absent: " + skipped);
        }
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void deep_resonances_real_handler_survives_a_place_event_without_a_placer_and_the_switch_restores_its_own(GameTestHelper h) {
        if (!ModList.get().isLoaded(DeepResonanceGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: Deep Resonance is not loaded, no event is posted (add -PwithDr)");
            h.succeed();
            return;
        }
        h.assertTrue(BSPModFixes.version(DeepResonanceGate.MOD_ID).equals(DeepResonanceGate.VERSION), "Deep Resonance " + DeepResonanceGate.VERSION + ", found " + BSPModFixes.version(DeepResonanceGate.MOD_ID));
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(PlaceEventLevel.ID));
        h.assertTrue(fixOn || Fixes.skipped().getOrDefault(PlaceEventLevel.ID, "").equals(MixinFixPlugin.SKIP_REASON),
                "F-38 is applied (Deep Resonance registers its handler at setup, so the class is loaded) or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        ServerLevel level = h.getLevel();
        BlockPos obsidian = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos stone = h.absolutePos(new BlockPos(4, 2, 2));
        level.setBlock(obsidian, Blocks.OBSIDIAN.defaultBlockState(), 3);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        Entity stand = EntityType.ARMOR_STAND.create(level);
        h.assertTrue(stand != null, "an armor stand can be made");
        stand.setPos(obsidian.getX(), obsidian.getY() + 1, obsidian.getZ());
        level.addFreshEntity(stand);

        long before = PlaceEventLevel.fallbacks();
        // 1. Obsidian, a shielding block, placed by no entity: the owner's report.
        Throwable shipped = post(level, obsidian, null);
        if (fixOn) {
            h.assertTrue(shipped == null, "with F-38 the handler survives the event without an entity: " + shipped);
            h.assertTrue(PlaceEventLevel.fallbacks() == before + 1, "the fallback was taken once");
        } else {
            h.assertTrue(isDeepResonancesNpe(shipped), "as shipped Deep Resonance's handler throws NullPointerException on the null entity: " + shipped);
            h.assertTrue(PlaceEventLevel.fallbacks() == before, "no fallback without the mixin");
        }
        // 2. The same block placed by an entity: unchanged either way.
        h.assertTrue(post(level, obsidian, stand) == null, "an event with an entity passes either way");
        // 3. A block that shields nothing, placed by no entity: Deep Resonance returns before the entity is read, either way.
        h.assertTrue(post(level, stone, null) == null, "a non-shielding block never reaches the entity");
        h.assertTrue(PlaceEventLevel.fallbacks() == (fixOn ? before + 1 : before), "neither of those took the fallback");
        // 4. The switch off: Deep Resonance's own behaviour, exception included, even with the mixin applied.
        if (fixOn) {
            FixesConfig.DR_PLACE_EVENTS_WITHOUT_PLACER.set(false);
            try {
                h.assertTrue(!PlaceEventLevel.enabled(), "the switch reads off");
                Throwable off = post(level, obsidian, null);
                h.assertTrue(isDeepResonancesNpe(off), "placeEventsWithoutPlacer = false dereferences the null entity as Deep Resonance did, and the same exception leaves the handler: " + off);
                h.assertTrue(post(level, obsidian, stand) == null, "and an event with an entity still passes");
            } finally {
                FixesConfig.DR_PLACE_EVENTS_WITHOUT_PLACER.set(true);
            }
            h.assertTrue(post(level, obsidian, null) == null, "back on, the event without an entity passes again");
            h.assertTrue(PlaceEventLevel.fallbacks() == before + 2, "two fallbacks in all");
        }
        h.succeed();
    }

    /** The event Ballistix's blast posts for a debris block: Forge's snapshot of the position, the placed state, the actor (or none). */
    private static BlockEvent.EntityPlaceEvent placeEvent(ServerLevel level, BlockPos pos, @Nullable Entity entity) {
        return new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos), level.getBlockState(pos.below()), entity);
    }

    /** Posts the event on the Forge bus and returns what a listener threw, or null (the bus rethrows a listener's exception). */
    @Nullable
    private static Throwable post(ServerLevel level, BlockPos pos, @Nullable Entity entity) {
        try {
            MinecraftForge.EVENT_BUS.post(placeEvent(level, pos, entity));
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    /** Whether this is the exception of the owner's report: a NullPointerException thrown from Deep Resonance's own handler. */
    private static boolean isDeepResonancesNpe(@Nullable Throwable t) {
        if (!(t instanceof NullPointerException)) {
            return false;
        }
        for (StackTraceElement frame : t.getStackTrace()) {
            if (frame.getClassName().equals(HANDLER) && frame.getMethodName().equals("onBlockPlaceEvent")) {
                return true;
            }
        }
        return false;
    }
}
