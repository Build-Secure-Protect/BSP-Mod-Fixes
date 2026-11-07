package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Dev-only: {@code ./gradlew runClient -PtitleProbe} closes the client by itself a few seconds after the title screen is up,
 * so a headless run that only wants the startup log (the mixin applications, the fixes report) ends without a hand on the
 * window. Logs one line, "BSP-Mod Fixes title probe: title screen reached, closing the client", then stops the client the
 * way the quit button does. Stays out of the jar with the rest of the gametest package; loaded by name only when the
 * property is set.
 */
public final class TitleProbe {
    private static final int SETTLE_TICKS = 60;
    private static int seen;
    private static boolean done;

    private TitleProbe() {}

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(TitleProbe::tick);
        Fixes.LOGGER.info("BSP-Mod Fixes title probe: installed, waiting for the title screen");
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen)) {
            seen = 0;
            return;
        }
        if (++seen < SETTLE_TICKS) {
            return;
        }
        done = true;
        Fixes.LOGGER.info("BSP-Mod Fixes title probe: title screen reached, closing the client");
        mc.stop();
    }
}
