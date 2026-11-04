package com.mrgregles.bsp_mod_fixes;

import com.mojang.logging.LogUtils;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for BSP-Mod Fixes: fixes for other mods in the Build Secure Protect modpack, patched through mixins and event
 * handlers of this mod's own without changing the other mods' jars. Each fix lives in {@code fix/} (its logic, testable on the
 * GameTest server) with a thin mixin in {@code mixin/} where one is needed. The target mods and their exact versions are pinned
 * in mods.toml.
 */
@Mod(BSPModFixes.MODID)
public class BSPModFixes {
    /** Mod id. Must match {@code mod_id} in gradle.properties and {@code modId} in mods.toml. */
    public static final String MODID = "bsp_mod_fixes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BSPModFixes(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, FixesConfig.SPEC);
        Fixes.registerEvents();
        context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> Fixes.report());
        if (Boolean.getBoolean("bsp_mod_fixes.emiProbe")) {
            context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> runEmiProbe());
        }
        LOGGER.info("BSP-Mod Fixes {} loaded (Numina {}, Modular Powersuits {})", version(MODID), version("numina"), version("powersuits"));
    }

    /**
     * Dev only ({@code -PemiProbe} on a client run): the probe class of the gametest package, which is not in the jar, is
     * loaded by name and run, so the EMI fixes' methods are called headlessly on EMI's real classes.
     */
    private static void runEmiProbe() {
        try {
            Class.forName("com.mrgregles.bsp_mod_fixes.gametest.EmiClientProbe").getMethod("run").invoke(null);
        } catch (Throwable t) {
            LOGGER.error("BSP-Mod Fixes probe FAILED to run", t);
        }
    }

    /** The version of a loaded mod as mods.toml gives it, or "absent". */
    public static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("absent");
    }
}
