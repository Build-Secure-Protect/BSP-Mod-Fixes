package com.mrgregles.bsp_mod_fixes.fix.gobber;

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
 * The gate for the Gobber 2 fix (F-27): it patches the dragon armour's tick, so the mixin config plugin applies it only when
 * exactly the Gobber build it was written for is loaded and that build's class still has the patched method. Gobber is an
 * optional mod of the pack, on both sides; without it, with another version, or with a changed class, the mixin is skipped
 * before Mixin looks its target up, and the startup report lists the fix as skipped with the reason. Pure
 * {@link #verdict(Optional, boolean)} for the tests; the lookup uses the loading mod list, which exists while classes are
 * still being transformed. Same shape as {@code fix/aae/AaeGate} and {@code fix/jei/JeiGate}.
 */
public final class GobberGate {
    public static final String MOD_ID = "gobber2";
    /** The Gobber build the mixin targets: {@code gobber_version} in gradle.properties, {@code gobber-301700:5329818} on CurseMaven. */
    public static final String VERSION = "2.8.9";
    public static final String TARGET = "Gobber 2 " + VERSION;
    /** The mixins of bsp_mod_fixes.mixins.json this gate decides for (simple names). */
    public static final Set<String> MIXINS = Set.of("GobberDragonFlightMixin");
    /** The class the mixin patches and the method it redirects inside, name and descriptor (Forge's armour tick, never remapped). */
    public static final String TARGET_CLASS = "com.kwpugh.gobber2.items.armor.ItemCustomArmorDragon";
    public static final Map<String, String> TARGET_METHODS = Map.of(
            "onArmorTick", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)V");

    /** What the gate decided for the Gobber fix, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("Gobber 2 is not loaded"),
        OTHER_VERSION("Gobber 2 is not " + VERSION),
        CHANGED_TARGET("this Gobber build's dragon armour lacks the method the fix patches");

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

    private GobberGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.contains(mixinSimpleName);
    }

    /** The verdict for an installed Gobber version ({@code empty} when it is absent) whose class has the patched method. */
    public static Verdict verdict(Optional<String> installedVersion) {
        return verdict(installedVersion, true);
    }

    /** The verdict for an installed Gobber version and whether its dragon armour class still has the method the mixin patches. */
    public static Verdict verdict(Optional<String> installedVersion, boolean targetHasMethod) {
        if (installedVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        if (!VERSION.equals(installedVersion.get())) {
            return Verdict.OTHER_VERSION;
        }
        return targetHasMethod ? Verdict.APPLY : Verdict.CHANGED_TARGET;
    }

    /** Reads the target class through Mixin's own bytecode provider and checks the method; unreadable here, Mixin decides. */
    static boolean targetHasMethod() {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(TARGET_CLASS);
            return JeiGate.hasMethods(node, TARGET_METHODS);
        } catch (Throwable t) {
            Fixes.LOGGER.debug("BSP-Mod Fixes: could not read {} ahead of Mixin ({}); leaving the decision to Mixin", TARGET_CLASS, t.toString());
            return true;
        }
    }

    /** The Gobber version the loader found, from mods.toml, or empty. */
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
            synchronized (GobberGate.class) {
                v = decided;
                if (v == null) {
                    Optional<String> installed = installedVersion();
                    v = verdict(installed, installed.map(VERSION::equals).orElse(false) && targetHasMethod());
                    switch (v) {
                        case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: Gobber 2 {} is loaded, the Gobber fix applies", VERSION);
                        case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: Gobber 2 is not loaded, the Gobber fix is skipped");
                        case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: Gobber 2 {} is loaded but the Gobber fix was built for {}; it is skipped", installed.orElse("?"), VERSION);
                        case CHANGED_TARGET -> Fixes.LOGGER.warn("BSP-Mod Fixes: Gobber 2 {} is loaded but its dragon armour no longer has the method the Gobber fix patches; it is skipped", installed.orElse("?"));
                    }
                    decided = v;
                }
            }
        }
        return v;
    }

    /** Called by the mixin config plugin for the Gobber mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Verdict v = decide();
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
