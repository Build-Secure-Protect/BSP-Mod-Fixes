package com.mrgregles.bsp_mod_fixes.fix.evolvedmekanism;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;

import java.util.concurrent.atomic.LongAdder;

/**
 * F-42 (Evolved Mekanism 1.3.0-fix3): Evolved Mekanism's Curios support registers fourteen items as curios at common setup
 * ({@code CuriosCompat.onCommonSetup}, through {@code CuriosApi.registerCurio}): thirteen of Mekanism's, its HDPE Reinforced Elytra
 * among them, and its own Portable Hazmat Suit; checking only that Curios is loaded. Elytra Slot gives every elytra it knows, that one included, a {@code curios:item}
 * capability of its own at the capability-attach event; Curios attaches another for the registration; Forge refuses a second
 * key on one object with {@code IllegalStateException: Duplicate Capability Key: curios:item}. The first capability lookup on
 * any HDPE elytra stack crashes the side that asks: the creative search (every item's tooltip, Ender IO's capacitor handler
 * does the lookup), a JEI or EMI tooltip, an inventory lookup on a server holding one.
 *
 * <p>With the fix, {@code EvolvedMekanismElytraCurioMixin} redirects that one registration call ({@link ElytraSlotLookup}):
 * an item whose stack Elytra Slot's own predicate matches is not registered, so the HDPE elytra has Elytra Slot's curio, like
 * the pack's other elytras, and still fits and flies from Evolved Mekanism's own slot (Elytra Slot's flight looks through every
 * Curios slot). The other thirteen items register as shipped. One INFO line per skipped item. No switch: the registration runs
 * at common setup, before the server config loads; the gate ({@link EvolvedMekanismGate}) and {@code -PskipFixes} are the
 * switch, and without Elytra Slot the mixin is skipped and Evolved Mekanism's registration stands. Pure {@link #skip} for the
 * tests.
 */
public final class ElytraCurioRegistration {
    public static final String ID = "F-42";
    public static final String WHAT = "Evolved Mekanism does not register an elytra Elytra Slot already gives a curio capability (Mekanism's HDPE Reinforced Elytra), whose two curios:item keys crashed every capability lookup on the stack (the creative search, tooltips)";
    public static final String TARGET = EvolvedMekanismGate.TARGET;

    private static final LongAdder SKIPPED = new LongAdder();

    private ElytraCurioRegistration() {}

    /** Whether a registration is skipped: only while Elytra Slot is loaded, and only for a stack its elytra predicate matches. */
    public static boolean skip(boolean elytraSlotLoaded, boolean isElytra) {
        return elytraSlotLoaded && isElytra;
    }

    /** Records a registration the fix skipped, with the one line that says so. */
    public static void skipped(String itemId) {
        SKIPPED.increment();
        Fixes.LOGGER.info("BSP-Mod Fixes {}: Evolved Mekanism's curio registration of {} is skipped: Elytra Slot already gives every elytra a Curios capability, and Forge refuses a second one", ID, itemId);
    }

    /** How many registrations the fix skipped this session (one per setup: the HDPE Reinforced Elytra). */
    public static long skippedItems() {
        return SKIPPED.sum();
    }
}
