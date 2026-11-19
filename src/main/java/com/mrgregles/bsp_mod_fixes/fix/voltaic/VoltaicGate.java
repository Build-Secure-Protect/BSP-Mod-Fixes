package com.mrgregles.bsp_mod_fixes.fix.voltaic;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiGate;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.util.Map;
import java.util.Optional;

/**
 * The gate for the Voltaic fix (F-40): the mixin config plugin applies it only when exactly the Voltaic build it was written for
 * is loaded with the patched methods in place, and when the two mods whose API the fix calls are loaded with the members it
 * calls: Mekanism (the radiation-shielding capability interface) and Curios (the equipped-curios lookup). Those two are not
 * pinned to a version: the fix only reads two stable API members, and the members are checked in bytecode instead. Without any
 * of the three, or with a changed class, the mixin is skipped before Mixin looks its target up, and the startup report names
 * the reason. Pure {@link #verdict(Optional, boolean, boolean, boolean)} for the tests.
 */
public final class VoltaicGate {
    public static final String MOD_ID = "voltaic";
    public static final String MEKANISM_ID = "mekanism";
    public static final String CURIOS_ID = "curios";
    /** The Voltaic build the mixin targets: {@code voltaic_version} in gradle.properties, {@code voltaic-1237904:9437780} on CurseMaven. */
    public static final String VERSION = "1.20.1-1.2.2";
    public static final String TARGET = "Voltaic " + VERSION;
    /** The class the mixin patches and the methods it needs there (Voltaic's own names, never remapped). */
    public static final Map<String, Target> MIXINS = Map.of(
            "VoltaicCurioShieldingMixin", new Target("voltaic.api.radiation.CapabilityRadiationRecipient", Map.of(
                    "recieveRadiation", "(Lnet/minecraft/world/entity/LivingEntity;DD)V",
                    "applyProtectionEnchantment", "(Lnet/minecraft/world/entity/LivingEntity;D)D")));
    /** Mekanism's API member the fix reads. */
    public static final Target MEKANISM_API = new Target("mekanism.api.radiation.capability.IRadiationShielding", Map.of(
            "getRadiationShielding", "()D"));
    /** Curios' API member the fix calls. */
    public static final Target CURIOS_API = new Target("top.theillusivec4.curios.api.CuriosApi", Map.of(
            "getCuriosInventory", "(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;"));

    /** A class and the methods of it the fix relies on. */
    public record Target(String className, Map<String, String> methods) {}

    /** What the gate decided for a mixin, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("Voltaic is not loaded"),
        OTHER_VERSION("Voltaic is not " + VERSION),
        CHANGED_TARGET("this Voltaic build's class lacks a method the fix patches"),
        NO_MEKANISM("Mekanism is not loaded, or its radiation-shielding API lacks the member the fix reads"),
        NO_CURIOS("Curios is not loaded, or its API lacks the lookup the fix calls");

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

    private static volatile Optional<String> installed;

    private VoltaicGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.containsKey(mixinSimpleName);
    }

    /**
     * The verdict for an installed Voltaic version ({@code empty} when absent), whether its class has the patched methods, and
     * whether Mekanism's and Curios' API members are there (each false when the mod is absent).
     */
    public static Verdict verdict(Optional<String> voltaicVersion, boolean targetHasMethods, boolean mekanismApi, boolean curiosApi) {
        if (voltaicVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        if (!VERSION.equals(voltaicVersion.get())) {
            return Verdict.OTHER_VERSION;
        }
        if (!targetHasMethods) {
            return Verdict.CHANGED_TARGET;
        }
        if (!mekanismApi) {
            return Verdict.NO_MEKANISM;
        }
        return curiosApi ? Verdict.APPLY : Verdict.NO_CURIOS;
    }

    /** Reads a class through Mixin's own bytecode provider and checks the methods; unreadable here, Mixin decides (true). */
    static boolean hasMethods(Target target) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target.className());
            return JeiGate.hasMethods(node, target.methods());
        } catch (Throwable t) {
            Fixes.LOGGER.debug("BSP-Mod Fixes: could not read {} ahead of Mixin ({}); leaving the decision to Mixin", target.className(), t.toString());
            return true;
        }
    }

    /** Whether the loader found a mod. */
    public static boolean loaded(String modId) {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
    }

    /** The Voltaic version the loader found, from its manifest, or empty. Read once. */
    public static Optional<String> installedVersion() {
        Optional<String> v = installed;
        if (v == null) {
            LoadingModList list = LoadingModList.get();
            ModFileInfo file = list == null ? null : list.getModFileById(MOD_ID);
            v = file == null || file.getMods().isEmpty() ? Optional.empty() : Optional.of(file.getMods().get(0).getVersion().toString());
            installed = v;
        }
        return v;
    }

    /** Called by the mixin config plugin for the Voltaic mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Target target = MIXINS.get(mixinSimpleName);
        Optional<String> version = installedVersion();
        boolean pinned = version.map(VERSION::equals).orElse(false);
        Verdict v = verdict(version,
                pinned && hasMethods(target),
                pinned && loaded(MEKANISM_ID) && hasMethods(MEKANISM_API),
                pinned && loaded(CURIOS_ID) && hasMethods(CURIOS_API));
        switch (v) {
            case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: Voltaic {} is loaded with Mekanism and Curios, {} applies", VERSION, mixinSimpleName);
            case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: Voltaic is not loaded, {} is skipped", mixinSimpleName);
            case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: Voltaic {} is loaded but {} was built for {}; it is skipped", version.orElse("?"), mixinSimpleName, VERSION);
            case CHANGED_TARGET -> Fixes.LOGGER.warn("BSP-Mod Fixes: Voltaic {} is loaded but {} no longer has a method {} patches; it is skipped", version.orElse("?"), target.className(), mixinSimpleName);
            case NO_MEKANISM, NO_CURIOS -> Fixes.LOGGER.info("BSP-Mod Fixes: {} is skipped: {}", mixinSimpleName, v.reason());
        }
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
