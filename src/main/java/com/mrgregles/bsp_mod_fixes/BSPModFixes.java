package com.mrgregles.bsp_mod_fixes;

import com.mojang.logging.LogUtils;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.net.FixesNetwork;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
        context.registerConfig(ModConfig.Type.CLIENT, FixesClientConfig.SPEC);
        FixesNetwork.register();
        Fixes.registerEvents();
        if (FMLEnvironment.dist.isClient()) {
            com.mrgregles.bsp_mod_fixes.client.ClientSetup.init(context.getModEventBus()); // named here only, so a server never loads it
        }
        context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> Fixes.report());
        if (Boolean.getBoolean("bsp_mod_fixes.emiProbe")) {
            context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> runProbe("EmiClientProbe", "run"));
        }
        if (Boolean.getBoolean("bsp_mod_fixes.recipeProbe")) {
            context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> runProbe("EmiRecipeProbe", "install"));
        }
        if (Boolean.getBoolean("bsp_mod_fixes.titleProbe")) {
            context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> runProbe("TitleProbe", "install"));
        }
        if (Boolean.getBoolean("bsp_mod_fixes.readoutProbe")) {
            context.getModEventBus().addListener((FMLLoadCompleteEvent e) -> runProbe("ReadoutProbe", "install"));
        }
        LOGGER.info("BSP-Mod Fixes {} loaded (Numina {}, Modular Powersuits {})", version(MODID), version("numina"), version("powersuits"));
    }

    /**
     * Dev only ({@code -PemiProbe}, {@code -PrecipeProbe}, {@code -PtitleProbe} or {@code -PreadoutProbe} on a client run): a probe class of the gametest package, which
     * is not in the jar, is loaded by name and its static method called, so EMI's real classes are exercised headlessly.
     */
    private static void runProbe(String probe, String method) {
        try {
            Class.forName("com.mrgregles.bsp_mod_fixes.gametest." + probe).getMethod(method).invoke(null);
        } catch (Throwable t) {
            LOGGER.error("BSP-Mod Fixes probe {} FAILED to run", probe, t);
        }
    }

    /** The version of a loaded mod as mods.toml gives it, or "absent". */
    public static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("absent");
    }
}
