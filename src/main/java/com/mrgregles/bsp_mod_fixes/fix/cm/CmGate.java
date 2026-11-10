package com.mrgregles.bsp_mod_fixes.fix.cm;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiGate;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

import java.util.Map;
import java.util.Optional;

/**
 * The gate for the Compact Machines fixes (F-28 onward): they patch that mod's classes, so the mixin config plugin applies
 * each mixin only when exactly the Compact Machines build they were written for is loaded and the mixin's target class
 * still has the methods it patches. Compact Machines is an optional mod of the pack, on both sides; without it, with
 * another version, or with a changed class, a mixin is skipped before Mixin looks its target up, and the startup report
 * lists its fix as skipped with the reason. Pure {@link #verdict(Optional, boolean)} for the tests. Same shape as
 * {@code fix/gobber/GobberGate}, with one target per mixin.
 */
public final class CmGate {
    public static final String MOD_ID = "compactmachines";
    /** The Compact Machines build the mixins target: {@code cm_version} in gradle.properties, {@code compact-machines-224218:6000230} on CurseMaven. */
    public static final String VERSION = "6.0.3";
    public static final String TARGET = "Compact Machines " + VERSION;

    /** A mixin's target: the class and the methods it patches there, name to descriptor. */
    public record Target(String className, Map<String, String> methods, boolean clientOnly) {
        public Target(String className, Map<String, String> methods) {
            this(className, methods, false);
        }
    }

    private static final String LEVEL_AND_ROOM = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ChunkPos;)V";
    /** The mixins of bsp_mod_fixes.mixins.json this gate decides for (simple names), each with its target. */
    private static final String MACHINE = "dev.compactmods.machines.machine.CompactMachineBlockEntity";
    public static final Map<String, Target> MIXINS = Map.ofEntries(
            // F-28
            Map.entry("CmTunnelSaveMixin", new Target("dev.compactmods.machines.tunnel.TunnelHelper",
                    Map.of("setChunkMode", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ChunkPos;Z)V"))),
            Map.entry("CmChunkloadSaveMixin", new Target("dev.compactmods.machines.upgrade.ChunkloadUpgrade",
                    Map.of("forceLoad", LEVEL_AND_ROOM, "normalLoad", LEVEL_AND_ROOM))),
            // F-29
            Map.entry("CmRoomHoldMixin", new Target(MACHINE, Map.of("forceLoadIfHasTunnels", "()V", "disconnect", "()V",
                    "getCapability", "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))),
            Map.entry("CmChunkloadRuleMixin", new Target("dev.compactmods.machines.upgrade.ChunkloadUpgrade",
                    Map.of("forceLoad", LEVEL_AND_ROOM, "normalLoad", LEVEL_AND_ROOM))),
            // F-30, client only: the preview screen (its Minecraft-named methods differ between a dev run and production, so
            // a method of its own stands for its shape) and vanilla's BufferBuilder, patched only beside Compact Machines
            Map.entry("CmPreviewCleanupMixin", new Target(PreviewLeak.SCREEN, Map.of("setScene", "(Ldev/compactmods/gander/render/baked/BakedLevel;)V"), true)),
            Map.entry("BufferBuilderAccessor", new Target("com.mojang.blaze3d.vertex.BufferBuilder", Map.of(), true)),
            // F-31: the machine block's use is Minecraft-named too, so a method of the block's own stands for its shape
            Map.entry("CmPreviewRequestMixin", new Target("dev.compactmods.machines.machine.CompactMachineBlock",
                    Map.of("getSize", "()Ldev/compactmods/machines/api/room/RoomSize;"))));

    /** What the gate decided for a Compact Machines mixin, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("Compact Machines is not loaded"),
        OTHER_VERSION("Compact Machines is not " + VERSION),
        CHANGED_TARGET("this Compact Machines build lacks the methods the fix patches"),
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

    private static volatile Optional<String> installed;
    private static volatile boolean logged;

    private CmGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.containsKey(mixinSimpleName);
    }

    /** The verdict for an installed Compact Machines version ({@code empty} when absent) whose class has the patched methods. */
    public static Verdict verdict(Optional<String> installedVersion) {
        return verdict(installedVersion, true);
    }

    /** The verdict for an installed Compact Machines version and whether the mixin's target still has the methods it patches. */
    public static Verdict verdict(Optional<String> installedVersion, boolean targetHasMethods) {
        if (installedVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        if (!VERSION.equals(installedVersion.get())) {
            return Verdict.OTHER_VERSION;
        }
        return targetHasMethods ? Verdict.APPLY : Verdict.CHANGED_TARGET;
    }

    /** As {@link #verdict(Optional, boolean)} for a mixin whose target exists on a client only: skipped on a dedicated server. */
    public static Verdict verdict(Optional<String> installedVersion, boolean targetHasMethods, boolean clientOnly, boolean dedicatedServer) {
        Verdict v = verdict(installedVersion, true);
        if (v != Verdict.APPLY) {
            return v;
        }
        if (clientOnly && dedicatedServer) {
            return Verdict.SERVER;
        }
        return targetHasMethods ? Verdict.APPLY : Verdict.CHANGED_TARGET;
    }

    /** Reads a target class through Mixin's own bytecode provider and checks its methods; unreadable here, Mixin decides. */
    static boolean targetHasMethods(Target target) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target.className());
            return JeiGate.hasMethods(node, target.methods());
        } catch (Throwable t) {
            Fixes.LOGGER.debug("BSP-Mod Fixes: could not read {} ahead of Mixin ({}); leaving the decision to Mixin", target.className(), t.toString());
            return true;
        }
    }

    /** The Compact Machines version the loader found, from mods.toml (the jar's version), or empty. Looked up once. */
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

    /** Called by the mixin config plugin for each Compact Machines mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Optional<String> found = installedVersion();
        Target target = MIXINS.get(mixinSimpleName);
        boolean dedicated = FMLEnvironment.dist.isDedicatedServer();
        boolean readable = found.map(VERSION::equals).orElse(false) && !(target.clientOnly() && dedicated);
        Verdict v = verdict(found, !readable || targetHasMethods(target), target.clientOnly(), dedicated);
        synchronized (CmGate.class) {
            if (!logged || v == Verdict.CHANGED_TARGET) { // one line for the mod; a changed target is always said
                logged = true;
                switch (v) {
                    case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: Compact Machines {} is loaded, the Compact Machines fixes apply", VERSION);
                    case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: Compact Machines is not loaded, the Compact Machines fixes are skipped");
                    case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: Compact Machines {} is loaded but the Compact Machines fixes were built for {}; they are skipped", found.orElse("?"), VERSION);
                    case CHANGED_TARGET -> Fixes.LOGGER.warn("BSP-Mod Fixes: Compact Machines {} is loaded but {} no longer has the methods {} patches; it is skipped", found.orElse("?"), MIXINS.get(mixinSimpleName).className(), mixinSimpleName);
                    case SERVER -> Fixes.LOGGER.info("BSP-Mod Fixes: Compact Machines {} is loaded, the Compact Machines fixes apply (the preview's is client-side and skipped here)", VERSION);
                }
            }
        }
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
