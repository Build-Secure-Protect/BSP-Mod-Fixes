package com.mrgregles.bsp_mod_fixes.fix.evolvedmekanism;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiGate;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.service.MixinService;

import java.util.Map;
import java.util.Optional;

/**
 * The gate for the Evolved Mekanism fix (F-42): the mixin config plugin applies it only when exactly the Evolved Mekanism build
 * it was written for is loaded with the patched method in place, and when the two mods whose members the fix uses are loaded
 * with those members: Elytra Slot (its elytra predicate, the very test by which it attaches its own curio capability) and Curios
 * (the registration call the fix redirects). Those two are not pinned to a version: the members are checked in bytecode
 * instead. Without Elytra Slot there is no second capability and nothing to fix, so the mixin is skipped with that reason;
 * without any of the three, or with a changed class, it is skipped before Mixin looks its target up, and the startup report
 * names the reason. Pure {@link #verdict(Optional, boolean, boolean, boolean)} for the tests.
 */
public final class EvolvedMekanismGate {
    public static final String MOD_ID = "evolvedmekanism";
    public static final String ELYTRA_SLOT_ID = "elytraslot";
    public static final String CURIOS_ID = "curios";
    /** The Evolved Mekanism build the mixin targets: {@code evolvedmekanism_version} in gradle.properties, {@code evolved-mekanism-1230085:9685752} on CurseMaven. */
    public static final String VERSION = "1.3.0-fix3";
    public static final String TARGET = "Evolved Mekanism " + VERSION;
    /**
     * The class the mixin patches and the method it needs there: the body of the {@code enqueueWork} lambda of
     * {@code CuriosCompat.onCommonSetup}, a synthetic method whose name holds for this build only (hence the exact pin).
     */
    public static final Map<String, Target> MIXINS = Map.of(
            "EvolvedMekanismElytraCurioMixin", new Target("fr.iglee42.evolvedmekanism.curios.CuriosCompat", Map.of(
                    "lambda$onCommonSetup$0", "()V")));
    /** Elytra Slot's member the fix reads: the predicate by which Elytra Slot attaches its curio capability to a stack. */
    public static final String ELYTRA_SLOT_CLASS = "com.illusivesoulworks.elytraslot.ElytraSlotCommonMod";
    public static final String ELYTRA_SLOT_FIELD = "IS_ELYTRA";
    public static final String ELYTRA_SLOT_FIELD_DESC = "Ljava/util/function/Predicate;";
    /** Curios' API member the fix redirects. */
    public static final Target CURIOS_API = new Target("top.theillusivec4.curios.api.CuriosApi", Map.of(
            "registerCurio", "(Lnet/minecraft/world/item/Item;Ltop/theillusivec4/curios/api/type/capability/ICurioItem;)V"));

    /** A class and the methods of it the fix relies on. */
    public record Target(String className, Map<String, String> methods) {}

    /** What the gate decided for a mixin, with the reason the startup report shows. */
    public enum Verdict {
        APPLY(""),
        ABSENT("Evolved Mekanism is not loaded"),
        OTHER_VERSION("Evolved Mekanism is not " + VERSION),
        CHANGED_TARGET("this Evolved Mekanism build's class lacks the method the fix patches"),
        NO_ELYTRA_SLOT("Elytra Slot is not loaded (or lacks its elytra predicate), so nothing attaches a second curios:item to an elytra and Evolved Mekanism's registration stands"),
        NO_CURIOS("Curios is not loaded, or its API lacks the registration call the fix redirects");

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

    private EvolvedMekanismGate() {}

    /** Whether the gate decides for this mixin (simple name). */
    public static boolean gates(String mixinSimpleName) {
        return MIXINS.containsKey(mixinSimpleName);
    }

    /**
     * The verdict for an installed Evolved Mekanism version ({@code empty} when absent), whether its class has the patched
     * method, and whether Elytra Slot's predicate and Curios' registration call are there (each false when the mod is absent).
     */
    public static Verdict verdict(Optional<String> evolvedMekanismVersion, boolean targetHasMethods, boolean elytraSlot, boolean curiosApi) {
        if (evolvedMekanismVersion.isEmpty()) {
            return Verdict.ABSENT;
        }
        if (!VERSION.equals(evolvedMekanismVersion.get())) {
            return Verdict.OTHER_VERSION;
        }
        if (!targetHasMethods) {
            return Verdict.CHANGED_TARGET;
        }
        if (!elytraSlot) {
            return Verdict.NO_ELYTRA_SLOT;
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

    /** Reads a class the same way and checks one field by name and descriptor; unreadable here, true as above. */
    static boolean hasField(String className, String name, String desc) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(className);
            return hasField(node, name, desc);
        } catch (Throwable t) {
            Fixes.LOGGER.debug("BSP-Mod Fixes: could not read {} ahead of Mixin ({}); leaving the decision to Mixin", className, t.toString());
            return true;
        }
    }

    /** Whether a class (as Mixin reads it) has a field of this name and descriptor. Pure. */
    public static boolean hasField(ClassNode node, String name, String desc) {
        if (node == null || node.fields == null) {
            return false;
        }
        for (FieldNode field : node.fields) {
            if (field.name.equals(name) && field.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the loader found a mod. */
    public static boolean loaded(String modId) {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
    }

    /** The Evolved Mekanism version the loader found, from its manifest, or empty. Read once. */
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

    /** Called by the mixin config plugin for the Evolved Mekanism mixin: whether to apply it, recording a skip for the startup report. */
    public static boolean allows(String mixinSimpleName) {
        Target target = MIXINS.get(mixinSimpleName);
        Optional<String> version = installedVersion();
        boolean pinned = version.map(VERSION::equals).orElse(false);
        Verdict v = verdict(version,
                pinned && hasMethods(target),
                pinned && loaded(ELYTRA_SLOT_ID) && hasField(ELYTRA_SLOT_CLASS, ELYTRA_SLOT_FIELD, ELYTRA_SLOT_FIELD_DESC),
                pinned && loaded(CURIOS_ID) && hasMethods(CURIOS_API));
        switch (v) {
            case APPLY -> Fixes.LOGGER.info("BSP-Mod Fixes: Evolved Mekanism {} is loaded with Elytra Slot and Curios, {} applies", VERSION, mixinSimpleName);
            case ABSENT -> Fixes.LOGGER.info("BSP-Mod Fixes: Evolved Mekanism is not loaded, {} is skipped", mixinSimpleName);
            case OTHER_VERSION -> Fixes.LOGGER.warn("BSP-Mod Fixes: Evolved Mekanism {} is loaded but {} was built for {}; it is skipped", version.orElse("?"), mixinSimpleName, VERSION);
            case CHANGED_TARGET -> Fixes.LOGGER.warn("BSP-Mod Fixes: Evolved Mekanism {} is loaded but {} no longer has the method {} patches; it is skipped", version.orElse("?"), target.className(), mixinSimpleName);
            case NO_ELYTRA_SLOT, NO_CURIOS -> Fixes.LOGGER.info("BSP-Mod Fixes: {} is skipped: {}", mixinSimpleName, v.reason());
        }
        if (!v.applies()) {
            Fixes.mixinSkipped(mixinSimpleName, v.reason());
        }
        return v.applies();
    }
}
