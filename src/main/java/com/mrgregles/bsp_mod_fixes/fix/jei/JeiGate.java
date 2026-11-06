package com.mrgregles.bsp_mod_fixes.fix.jei;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The gate for the JEI fix (F-19): it patches JEI's internal start observer, so the mixin config plugin applies it only
 * when a JEI build of the series it was written for is loaded (any {@code 15.74.x}, D33) whose start observer still has the
 * two methods the mixin patches. JEI is an optional client-side mod of the pack; without it, with another series, or with
 * a changed observer, the mixin is skipped before Mixin looks its target up, and the startup
 * report lists the fix as skipped with the reason. On a dedicated server JEI's client start code never runs, so the verdict
 * there is {@link Verdict#SERVER}. Pure {@link #verdict(Optional)} for the tests; the lookup uses the loading mod list, which
 * exists while classes are still being transformed. Same shape as {@code fix/emi/EmiGate}.
 */
public final class JeiGate {
    public static final String MOD_ID = "jei";
    /** The JEI series the mixin targets: every build whose version starts with this ({@code jei-238222:9394360} is 15.74.0.234). */
    public static final String VERSION_PREFIX = "15.74.";
    public static final String TARGET = "JEI 15.74";
    /** The mixins of bsp_mod_fixes.mixins.json this gate decides for (simple names). */
    public static final Set<String> MIXINS = Set.of("JeiStartObserverMixin");

    /** What the gate decided for the JEI fix, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("JEI is not loaded"),
        OTHER_VERSION("JEI is not a " + VERSION_PREFIX + "x build"),
        CHANGED_TARGET("this JEI build's start observer lacks the methods the fix patches"),
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

    private JeiGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.contains(mixinSimpleName);
    }

    /** The class the mixin patches and the two methods it needs there, name and descriptor. */
    public static final String TARGET_CLASS = "mezz.jei.forge.startup.StartEventObserver";
    public static final Map<String, String> TARGET_METHODS = Map.of(
            "startIfReady", "()V",
            "register", "(Lmezz/jei/forge/events/PermanentEventSubscriptions;)V");

    /** The verdict for an installed JEI version ({@code empty} when JEI is absent). */
    public static Verdict verdict(Optional<String> installedVersion) {
        return verdict(installedVersion, true);
    }

    /**
     * The verdict for an installed JEI version and whether its start observer still has the methods the mixin patches: any
     * 15.74 build applies (D33), but a build of that series that renamed them costs the fix instead of stopping the client
     * (the mixin config is {@code required}, so an injection that finds no target would).
     */
    public static Verdict verdict(Optional<String> installedVersion, boolean targetHasMethods) {
        if (installedVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        if (!installedVersion.get().startsWith(VERSION_PREFIX)) {
            return Verdict.OTHER_VERSION;
        }
        return targetHasMethods ? Verdict.APPLY : Verdict.CHANGED_TARGET;
    }

    /** Whether a class (as Mixin reads it) has every method of {@code required}, name to descriptor. Pure. */
    public static boolean hasMethods(ClassNode node, Map<String, String> required) {
        if (node == null || node.methods == null) {
            return false;
        }
        for (Map.Entry<String, String> m : required.entrySet()) {
            boolean found = false;
            for (MethodNode method : node.methods) {
                if (method.name.equals(m.getKey()) && method.desc.equals(m.getValue())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads the target class through Mixin's own bytecode provider (the same bytes Mixin is about to patch) and checks the
     * methods. When the class cannot be read here, the answer is yes: Mixin then decides, as it would without this gate.
     */
    static boolean targetHasMethods() {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(TARGET_CLASS);
            return hasMethods(node, TARGET_METHODS);
        } catch (Throwable t) {
            Fixes.LOGGER.debug("BSP-Mod Fixes: could not read {} ahead of Mixin ({}); leaving the decision to Mixin", TARGET_CLASS, t.toString());
            return true;
        }
    }

    /** The JEI version the loader found, from mods.toml, or empty. */
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
            synchronized (JeiGate.class) {
                v = decided;
                if (v == null) {
                    Optional<String> installed = installedVersion();
                    v = FMLEnvironment.dist.isDedicatedServer() ? Verdict.SERVER
                            : verdict(installed, installed.map(i -> i.startsWith(VERSION_PREFIX)).orElse(false) && targetHasMethods());
                    switch (v) {
                        case SERVER -> Fixes.LOGGER.info("BSP-Mod Fixes: dedicated server, the JEI fix is client-side and skipped");
                        case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: JEI {} is loaded, the JEI fix applies", installed.orElse("?"));
                        case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: JEI is not loaded, the JEI fix is skipped");
                        case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: JEI {} is loaded but the JEI fix was built for {}x; it is skipped", installed.orElse("?"), VERSION_PREFIX);
                        case CHANGED_TARGET -> Fixes.LOGGER.warn("BSP-Mod Fixes: JEI {} is loaded but its start observer no longer has the methods the JEI fix patches; it is skipped", installed.orElse("?"));
                    }
                    decided = v;
                }
            }
        }
        return v;
    }

    /** Called by the mixin config plugin for the JEI mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Verdict v = decide();
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
