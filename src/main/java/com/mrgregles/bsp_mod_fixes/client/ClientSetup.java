package com.mrgregles.bsp_mod_fixes.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.aae.AaeGate;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-side wiring, touched only on a client ({@code BSPModFixes} checks the dist before naming this class): the quantum
 * suit readout's overlay, key binding and screen events (F-25). The readout reads the worn pieces through AE2's API, so it
 * is installed only when AdvancedAE is loaded at the build the fix was written for ({@link AaeGate}); without it only the
 * key binding exists, registered on every client so Controls looks the same everywhere.
 */
public final class ClientSetup {
    public static final String KEY_CATEGORY = "key.categories.bsp_mod_fixes";
    public static final String KEY_TOGGLE_READOUT = "key.bsp_mod_fixes.quantum_readout";
    /** Unbound by default (D49): the pack has many mods and their keys. */
    public static final KeyMapping TOGGLE_READOUT = new KeyMapping(KEY_TOGGLE_READOUT, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);

    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(TOGGLE_READOUT));
        if (!AaeGate.decide().applies()) {
            Fixes.LOGGER.info("BSP-Mod Fixes: the quantum suit readout is not installed ({})", AaeGate.decide().reason());
            return;
        }
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("quantum_readout", QuantumReadout::render));
        MinecraftForge.EVENT_BUS.addListener(QuantumReadout::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(QuantumReadout::onScreenRender);
        MinecraftForge.EVENT_BUS.addListener(QuantumReadout::onScreenClick);
        Fixes.LOGGER.info("BSP-Mod Fixes: the quantum suit readout is installed (F-25)");
    }
}
