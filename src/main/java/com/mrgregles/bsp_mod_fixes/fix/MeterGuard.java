package com.mrgregles.bsp_mod_fixes.fix;

import lehjr.numina.common.capabilities.inventory.modularitem.IModularItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-26: Modular Powersuits' HUD energy meter shows only while a Powersuits modular item is worn or held. Powersuits draws
 * its meters ({@code MPSMeterOverlay.render}, called on every HUD frame from {@code MPSOverlay}) whenever Numina's sum of
 * the player's energy is above zero, and that sum takes the Forge energy capability of every equipped item of any mod; so
 * anyone wearing energy-storing armor (AdvancedAE's quantum set exposes its AE as FE) gets Powersuits' meter with no
 * Powersuits item on. The mixin ({@code PowersuitsMeterMixin}, client only) cancels the draw unless {@link #modularEquipped}
 * says a modular item is in an equipment slot, the same rule Powersuits uses for the rest of its HUD (D52: worn or held).
 */
public final class MeterGuard {
    public static final String ID = "F-26";
    public static final String WHAT = "Powersuits' HUD energy meter shows only while a Powersuits modular item is worn or held, not for any mod's energy-storing armor";
    public static final String TARGET = "Modular Powersuits 2.3.13";

    private static final AtomicInteger SKIPPED = new AtomicInteger();

    private MeterGuard() {}

    /** Whether the meter is drawn: only with a modular item equipped. */
    public static boolean shouldDraw(boolean modularEquipped) {
        return modularEquipped;
    }

    /** Whether any equipment slot (armor or hands) holds a Numina modular item (Powersuits' armor and the Power Fist). */
    public static boolean modularEquipped(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (player.getItemBySlot(slot).getCapability(ForgeCapabilities.ITEM_HANDLER).filter(IModularItem.class::isInstance).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Counts a frame the meter was kept off (for the tests and the log). */
    public static void skipped() {
        SKIPPED.incrementAndGet();
    }

    public static int skippedCount() {
        return SKIPPED.get();
    }
}
