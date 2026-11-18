package com.mrgregles.bsp_mod_fixes.fix.deepresonance;

import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-38 (Deep Resonance 1.20-5.0.6): a block placed with no placer no longer crashes Deep Resonance's radiation shielding
 * handler. Deep Resonance listens to Forge's {@code BlockEvent.EntityPlaceEvent} to notice a shielding block (obsidian, its
 * dense obsidian and dense glass, a lead storage block) going up near a radiation source, and reads the level it needs as
 * {@code event.getEntity().level()}. Forge declares that entity {@code @Nullable}: a block placed by no entity (Ballistix's
 * blast debris landing, which posts the event with the blast's owner, and a blast lit by redstone, a timer or a chain has none)
 * carries null, and the handler throws {@code NullPointerException} on the server thread. Neruina catches it, suspends the
 * debris entity and announces an error to every player; without Neruina the server would crash. Every other listener on that
 * event in the pack tests the entity before using it.
 *
 * <p>With the fix, {@code DeepResonancePlaceEventMixin} skips the handler at its head when the event has no entity and its own
 * level is not a {@link Level} (nothing Deep Resonance could use), and redirects the one {@code Entity.level()} call to
 * {@link #levelOf}, which answers with the event's own level when the entity is null; for an event with an entity nothing
 * changes. The event itself is never cancelled, so the block is placed as before, and a blast-placed shielding block near a
 * source is still recorded. The first fallback of a session is logged once at INFO (debris posts many events a tick). Switch:
 * {@code [deepResonance] placeEventsWithoutPlacer} in {@code bsp_mod_fixes-server.toml}, read at each event; while the switch
 * is off the null entity is dereferenced as Deep Resonance's own code did, so the same {@code NullPointerException} leaves
 * the handler (thrown from this class's redirect handler inside Deep Resonance's method). Pure {@link #skip} and {@link #levelOf} for the tests.
 */
public final class PlaceEventLevel {
    public static final String ID = "F-38";
    public static final String WHAT = "a block placed with no placer (Ballistix blast debris) no longer crashes the radiation shielding handler, which now reads the level from the event";
    public static final String TARGET = DeepResonanceGate.TARGET;

    private static final AtomicBoolean LOGGED = new AtomicBoolean();
    private static final LongAdder FALLBACKS = new LongAdder();

    private PlaceEventLevel() {}

    /** Whether the switch is on: the server config's value, and on before that config has loaded (a crash guard, so on by doubt). */
    public static boolean enabled() {
        return !FixesConfig.SPEC.isLoaded() || FixesConfig.DR_PLACE_EVENTS_WITHOUT_PLACER.get();
    }

    /**
     * Whether Deep Resonance's handler is skipped for this event: the fix is on, the event has no entity, and its level is not
     * a {@link Level} (null, or another {@code LevelAccessor}), so there is nothing to read the radiation manager from. With an
     * entity, or with a real level to fall back on, the handler runs.
     */
    public static boolean skip(@Nullable Entity entity, @Nullable LevelAccessor eventLevel, boolean enabled) {
        return enabled && entity == null && !(eventLevel instanceof Level);
    }

    /** {@link #skip(Entity, LevelAccessor, boolean)} with the switch read. */
    public static boolean skip(@Nullable Entity entity, @Nullable LevelAccessor eventLevel) {
        return skip(entity, eventLevel, enabled());
    }

    /**
     * The level Deep Resonance's handler goes on with, in place of {@code entity.level()}: the entity's level when there is
     * one; the event's own level when there is none, the fix is on and that level is a {@link Level} (the head guard has
     * already returned otherwise, but the switch is read again here, so the type is checked again rather than trusted); and
     * the same call on the null entity when the fix is off, so the handler throws the {@code NullPointerException} it threw
     * as shipped, from this method inside Deep Resonance's.
     */
    public static Level levelOf(@Nullable Entity entity, @Nullable LevelAccessor eventLevel, boolean enabled) {
        if (entity == null && enabled && eventLevel instanceof Level level) {
            return level;
        }
        return entity.level();
    }

    /** {@link #levelOf(Entity, LevelAccessor, boolean)} for the event, with the switch read and the first fallback of the session logged. */
    public static Level levelOf(@Nullable Entity entity, BlockEvent.EntityPlaceEvent event) {
        boolean enabled = enabled();
        if (entity == null && enabled && event.getLevel() instanceof Level) {
            fellBack(event.getPlacedBlock(), event.getPos());
        }
        return levelOf(entity, event.getLevel(), enabled);
    }

    /** Records a fallback: counted, and the first of the session logged at INFO with the block and its position. */
    public static void fellBack(BlockState placed, BlockPos pos) {
        FALLBACKS.increment();
        if (LOGGED.compareAndSet(false, true)) {
            Fixes.LOGGER.info("BSP-Mod Fixes {}: {} was placed at {} by no entity (blast debris, most likely); Deep Resonance's shielding handler read the level from the event instead of crashing. Logged once a session; later ones are only counted",
                    ID, ForgeRegistries.BLOCKS.getKey(placed.getBlock()), pos.toShortString());
        }
    }

    /** How many events without an entity the fix has answered this session. */
    public static long fallbacks() {
        return FALLBACKS.sum();
    }
}
