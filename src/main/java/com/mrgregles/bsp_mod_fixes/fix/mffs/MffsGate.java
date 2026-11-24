package com.mrgregles.bsp_mod_fixes.fix.mffs;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiGate;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The gate for the MFFS (Modular Force Field System) fixes (F-34 onward): the mixin config plugin applies each of them only
 * when exactly the MFFS build they were written for is loaded and that build's class still has the methods the mixin
 * patches. MFFS is an optional mod of the pack, on both sides; without it, with another version, or with a changed class,
 * the mixin is skipped before Mixin looks its target up, and the startup report lists the fix as skipped with the reason.
 * Pure {@link #verdict(Optional, boolean)} for the tests. Same shape as {@code fix/gobber/GobberGate} and {@code fix/cm/CmGate}.
 */
public final class MffsGate {
    public static final String MOD_ID = "mffs";
    /** The MFFS build the mixins target: {@code mffs_version} in gradle.properties, {@code mffs-238546:8649940} on CurseMaven. */
    public static final String VERSION = "5.1.29";
    public static final String TARGET = "MFFS " + VERSION;
    /** The class each mixin patches and the methods it needs there, name and descriptor (MFFS's own names, never remapped). */
    public static final Map<String, Target> MIXINS = Map.of(
            "MffsSpongeMixin", new Target("dev.su5ed.mffs.util.module.SpongeModule", Map.of(
                    "beforeSelect", "(Ldev/su5ed/mffs/api/Projector;Ljava/util/Collection;)V",
                    "beforeProject", "(Ldev/su5ed/mffs/api/Projector;)V")),
            "MffsFieldLimitMixin", new Target("dev.su5ed.mffs.blockentity.ProjectorBlockEntity", Map.of(
                    "selectProjectablePositions", "()Ljava/util/List;",
                    "projectField", "()V",
                    "destroyField", "()V",
                    "tickServer", "()V")),
            "MffsForceFieldSyncMixin", new Target("dev.su5ed.mffs.blockentity.ForceFieldBlockEntity", Map.of(
                    "getCustomUpdateTag", "()Lnet/minecraft/nbt/CompoundTag;",
                    "handleCustomUpdateTag", "(Lnet/minecraft/nbt/CompoundTag;)V",
                    "onLoad", "()V")),
            "MffsSelectionThreadMixin", new Target("dev.su5ed.mffs.blockentity.ProjectorBlockEntity", Map.of(
                    "runSelectionTask", "()Ljava/util/concurrent/CompletableFuture;")),
            "MffsLoadedFieldMixin", new Target("dev.su5ed.mffs.blockentity.ProjectorBlockEntity", Map.of(
                    "selectProjectablePositions", "()Ljava/util/List;",
                    "destroyField", "()V",
                    "lambda$destroyField$19", "(Lnet/minecraft/core/BlockPos;)Z")),
            "MffsFusionProjectMixin", new Target("dev.su5ed.mffs.util.module.FusionModule", Map.of(
                    "beforeProject", "(Ldev/su5ed/mffs/api/Projector;)V")),
            "MffsFieldBlockLoadMixin", new Target("dev.su5ed.mffs.blockentity.ForceFieldBlockEntity", Map.of(
                    "onLoad", "()V",
                    "getProjector", "()Ljava/util/Optional;")));

    /** A mixin's target class and the methods of it the mixin relies on. */
    public record Target(String className, Map<String, String> methods) {}

    /** What the gate decided for a mixin, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("MFFS is not loaded"),
        OTHER_VERSION("MFFS is not " + VERSION),
        CHANGED_TARGET("this MFFS build's class lacks a method the fix patches");

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

    private MffsGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.containsKey(mixinSimpleName);
    }

    /** The verdict for an installed MFFS version ({@code empty} when it is absent) whose class has the patched methods. */
    public static Verdict verdict(Optional<String> installedVersion) {
        return verdict(installedVersion, true);
    }

    /** The verdict for an installed MFFS version and whether the mixin's target class still has the methods it patches. */
    public static Verdict verdict(Optional<String> installedVersion, boolean targetHasMethods) {
        if (installedVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        if (!VERSION.equals(installedVersion.get())) {
            return Verdict.OTHER_VERSION;
        }
        return targetHasMethods ? Verdict.APPLY : Verdict.CHANGED_TARGET;
    }

    /** Reads the target class through Mixin's own bytecode provider and checks the methods; unreadable here, Mixin decides. */
    static boolean targetHasMethods(Target target) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target.className());
            return JeiGate.hasMethods(node, target.methods());
        } catch (Throwable t) {
            Fixes.LOGGER.debug("BSP-Mod Fixes: could not read {} ahead of Mixin ({}); leaving the decision to Mixin", target.className(), t.toString());
            return true;
        }
    }

    /** The MFFS version the loader found, from mods.toml, or empty. Read once. */
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

    /** Called by the mixin config plugin for an MFFS mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Target target = MIXINS.get(mixinSimpleName);
        Optional<String> version = installedVersion();
        Verdict v = verdict(version, version.map(VERSION::equals).orElse(false) && targetHasMethods(target));
        switch (v) {
            case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: MFFS {} is loaded, {} applies", VERSION, mixinSimpleName);
            case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: MFFS is not loaded, {} is skipped", mixinSimpleName);
            case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: MFFS {} is loaded but {} was built for {}; it is skipped", version.orElse("?"), mixinSimpleName, VERSION);
            case CHANGED_TARGET -> Fixes.LOGGER.warn("BSP-Mod Fixes: MFFS {} is loaded but {} no longer has a method {} patches; it is skipped", version.orElse("?"), target.className(), mixinSimpleName);
        }
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
