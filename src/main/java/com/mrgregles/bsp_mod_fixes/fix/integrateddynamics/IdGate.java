package com.mrgregles.bsp_mod_fixes.fix.integrateddynamics;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiGate;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.util.Map;
import java.util.Optional;

/**
 * The gate for the Integrated Dynamics fixes (F-49 onward): the mixin config plugin applies each of them only when exactly the
 * Integrated Dynamics build they were written for is loaded, with the methods the mixin patches still in its class, and with
 * the two libraries it requires, Cyclops Core and Common Capabilities (any version: the fixes call two stable Cyclops Core
 * members, checked in bytecode). Integrated Dynamics is an optional mod of the pack, on both sides; without it, with another
 * version, or with a changed class, the mixin is skipped before Mixin looks its target up, and the startup report names the
 * reason. The two libraries share one verdict ({@code NO_LIBRARIES}): Forge refuses to load Integrated Dynamics without
 * either, so the check is a belt, and Common Capabilities is checked by its mod id only since the fixes call nothing of it.
 * Pure {@link #verdict(Optional, boolean, boolean)} for the tests. Same shape as {@code fix/voltaic/VoltaicGate}.
 */
public final class IdGate {
    public static final String MOD_ID = "integrateddynamics";
    public static final String CYCLOPS_ID = "cyclopscore";
    public static final String COMMON_CAPABILITIES_ID = "commoncapabilities";
    /** The build the mixins target: {@code id_version} in gradle.properties, {@code integrated-dynamics-236307:9626465} on CurseMaven. */
    public static final String VERSION = "1.31.5";
    public static final String TARGET = "Integrated Dynamics " + VERSION;
    /** The class each mixin patches and the methods it needs there, name and descriptor (Integrated Dynamics' own names, never remapped). */
    public static final Map<String, Target> MIXINS = Map.of(
            "IdNetworkKillMixin", new Target("org.cyclops.integrateddynamics.core.network.Network", Map.of(
                    "kill", "()V",
                    "removeNetworkElementPost", "(Lorg/cyclops/integrateddynamics/api/network/INetworkElement;)V")),
            "IdPartRemovalMixin", new Target("org.cyclops.integrateddynamics.capability.partcontainer.PartContainerDefault", Map.of(
                    "removePart", "(Lnet/minecraft/core/Direction;Lnet/minecraft/world/entity/player/Player;ZZ)Lorg/cyclops/integrateddynamics/api/part/IPartType;",
                    "hasPart", "(Lnet/minecraft/core/Direction;)Z",
                    "getNetwork", "()Lorg/cyclops/integrateddynamics/api/network/INetwork;")));
    /** Cyclops Core's members the fixes read. */
    public static final Target CYCLOPS_API = new Target("org.cyclops.cyclopscore.datastructure.DimPos", Map.of(
            "getBlockPos", "()Lnet/minecraft/core/BlockPos;",
            "getLevel", "()Ljava/lang/String;"));

    /** A class and the methods of it the fixes rely on. */
    public record Target(String className, Map<String, String> methods) {}

    /** What the gate decided for a mixin, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("Integrated Dynamics is not loaded"),
        OTHER_VERSION("Integrated Dynamics is not " + VERSION),
        CHANGED_TARGET("this Integrated Dynamics build's class lacks a method the fix patches"),
        NO_LIBRARIES("Cyclops Core or Common Capabilities is not loaded, or Cyclops Core's DimPos lacks a member the fix reads");

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

    private IdGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.containsKey(mixinSimpleName);
    }

    /** The verdict for an installed version ({@code empty} when absent), whether the target class has the patched methods, and whether the libraries are there. */
    public static Verdict verdict(Optional<String> idVersion, boolean targetHasMethods, boolean libraries) {
        if (idVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        if (!VERSION.equals(idVersion.get())) {
            return Verdict.OTHER_VERSION;
        }
        if (!targetHasMethods) {
            return Verdict.CHANGED_TARGET;
        }
        return libraries ? Verdict.APPLY : Verdict.NO_LIBRARIES;
    }

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

    /** The Integrated Dynamics version the loader found, from its mods.toml, or empty. Read once. */
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

    /** Called by the mixin config plugin for an Integrated Dynamics mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Target target = MIXINS.get(mixinSimpleName);
        Optional<String> version = installedVersion();
        boolean pinned = version.map(VERSION::equals).orElse(false);
        Verdict v = verdict(version, pinned && hasMethods(target),
                pinned && loaded(CYCLOPS_ID) && loaded(COMMON_CAPABILITIES_ID) && hasMethods(CYCLOPS_API));
        switch (v) {
            case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: Integrated Dynamics {} is loaded with Cyclops Core and Common Capabilities, {} applies", VERSION, mixinSimpleName);
            case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: Integrated Dynamics is not loaded, {} is skipped", mixinSimpleName);
            case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: Integrated Dynamics {} is loaded but {} was built for {}; it is skipped", version.orElse("?"), mixinSimpleName, VERSION);
            case CHANGED_TARGET -> Fixes.LOGGER.warn("BSP-Mod Fixes: Integrated Dynamics {} is loaded but {} no longer has a method {} patches; it is skipped", version.orElse("?"), target.className(), mixinSimpleName);
            case NO_LIBRARIES -> Fixes.LOGGER.info("BSP-Mod Fixes: {} is skipped: {}", mixinSimpleName, v.reason());
        }
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
