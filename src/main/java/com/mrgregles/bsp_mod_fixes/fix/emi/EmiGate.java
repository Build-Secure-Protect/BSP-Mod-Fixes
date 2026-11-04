package com.mrgregles.bsp_mod_fixes.fix.emi;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

import java.util.Optional;

/**
 * The gate for the EMI fixes (F-11 onward): they patch EMI's JEI compatibility layer and are compiled against one exact EMI
 * build, so the mixin config plugin applies them only when that build is loaded. EMI is an optional client-side mod of the
 * pack; without it, or with another version, the mixins are skipped before Mixin looks their targets up, and the startup
 * report lists the fixes as skipped with the reason. On a dedicated server EMI's JEI layer never loads, so the verdict there
 * is {@link Verdict#SERVER} whatever is installed. Pure {@link #verdict(Optional)} for the tests; the lookup uses the loading
 * mod list, which exists while classes are still being transformed.
 */
public final class EmiGate {
    public static final String MOD_ID = "emi";
    /** The EMI build the mixins target: {@code emi_version} in gradle.properties, {@code emi-580555:8081375} on CurseMaven. */
    public static final String VERSION = "1.1.24+1.20.1+forge";
    public static final String TARGET = "EMI " + VERSION;

    /** What the gate decided for the EMI fixes, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("EMI is not loaded"),
        OTHER_VERSION("EMI is not " + VERSION),
        SERVER("client-side only, nothing to patch on a dedicated server");

        private final String reason;

        Verdict(String reason) {
            this.reason = reason;
        }

        public boolean applies() {
            return this == APPLY;
        }

        public String reason() {
            return reason;
        }
    }

    private static volatile Verdict decided;

    private EmiGate() {}

    /** The verdict for an installed EMI version ({@code empty} when EMI is absent). */
    public static Verdict verdict(Optional<String> installedVersion) {
        if (installedVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        return VERSION.equals(installedVersion.get()) ? Verdict.APPLY : Verdict.OTHER_VERSION;
    }

    /** The EMI version the loader found, from mods.toml, or empty. */
    public static Optional<String> installedVersion() {
        LoadingModList list = LoadingModList.get();
        if (list == null) {
            return Optional.empty();
        }
        ModFileInfo file = list.getModFileById(MOD_ID);
        if (file == null || file.getMods().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(file.getMods().get(0).getVersion().toString());
    }

    /** The verdict for this run, decided once and logged once. */
    public static Verdict decide() {
        Verdict v = decided;
        if (v == null) {
            synchronized (EmiGate.class) {
                v = decided;
                if (v == null) {
                    Optional<String> installed = installedVersion();
                    v = FMLEnvironment.dist.isDedicatedServer() ? Verdict.SERVER : verdict(installed);
                    switch (v) {
                        case SERVER -> Fixes.LOGGER.info("BSP-Mod Fixes: dedicated server, the EMI fixes are client-side and skipped");
                        case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: EMI {} is loaded, the EMI fixes apply", VERSION);
                        case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: EMI is not loaded, the EMI fixes are skipped");
                        case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: EMI {} is loaded but the EMI fixes were built for {}; they are skipped", installed.orElse("?"), VERSION);
                    }
                    decided = v;
                }
            }
        }
        return v;
    }

    /** Called by the mixin config plugin for each EMI mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Verdict v = decide();
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
