package com.mrgregles.bsp_mod_fixes.fix.aae;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

import java.util.Optional;
import java.util.Set;

/**
 * The gate for the AdvancedAE fix (F-25): it patches AdvancedAE's damage handler and is compiled against one exact build, so
 * the mixin config plugin applies it only when that build is loaded. AdvancedAE is an optional mod of the pack, on both
 * sides; without it, or with another version, the mixin is skipped before Mixin looks its target up, and the startup report
 * lists the fix as skipped with the reason. Pure {@link #verdict(Optional)} for the tests; the lookup uses the loading mod
 * list, which exists while classes are still being transformed. Same shape as {@code fix/emi/EmiGate}.
 */
public final class AaeGate {
    public static final String MOD_ID = "advanced_ae";
    /** The AdvancedAE build the mixin targets: {@code aae_version} in gradle.properties, {@code advanced-ae-1084104:8564205} on CurseMaven. */
    public static final String VERSION = "1.3.6-1.20.1";
    public static final String TARGET = "AdvancedAE " + VERSION;
    /** The mixins of bsp_mod_fixes.mixins.json this gate decides for (simple names). */
    public static final Set<String> MIXINS = Set.of("AaeDamageAbsorptionMixin");

    /** What the gate decided for the AdvancedAE fix, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("AdvancedAE is not loaded"),
        OTHER_VERSION("AdvancedAE is not " + VERSION);

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

    private AaeGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.contains(mixinSimpleName);
    }

    /** The verdict for an installed AdvancedAE version ({@code empty} when it is absent). */
    public static Verdict verdict(Optional<String> installedVersion) {
        if (installedVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        return VERSION.equals(installedVersion.get()) ? Verdict.APPLY : Verdict.OTHER_VERSION;
    }

    /** The AdvancedAE version the loader found, from mods.toml, or empty. */
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
            synchronized (AaeGate.class) {
                v = decided;
                if (v == null) {
                    Optional<String> installed = installedVersion();
                    v = verdict(installed);
                    switch (v) {
                        case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: AdvancedAE {} is loaded, the AdvancedAE fix applies", VERSION);
                        case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: AdvancedAE is not loaded, the AdvancedAE fix is skipped");
                        case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: AdvancedAE {} is loaded but the AdvancedAE fix was built for {}; it is skipped", installed.orElse("?"), VERSION);
                    }
                    decided = v;
                }
            }
        }
        return v;
    }

    /** Called by the mixin config plugin for the AdvancedAE mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Verdict v = decide();
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
