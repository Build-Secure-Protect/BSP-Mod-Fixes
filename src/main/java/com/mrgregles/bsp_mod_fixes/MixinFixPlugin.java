package com.mrgregles.bsp_mod_fixes;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.aae.AaeGate;
import com.mrgregles.bsp_mod_fixes.fix.emi.EmiGate;
import com.mrgregles.bsp_mod_fixes.fix.gobber.GobberGate;
import com.mrgregles.bsp_mod_fixes.fix.jei.JeiGate;
import com.mrgregles.bsp_mod_fixes.fix.proxy.ClientGate;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Plugin of bsp_mod_fixes.mixins.json: tells {@link Fixes} when a mixin has been applied to its target, so the startup report
 * lists only fixes that are really in place. The Numina and Powersuits mixins are never filtered (both mods are mandatory and
 * the config is {@code required}, so a mixin that fails stops the game); the EMI mixins go through {@link EmiGate}, which
 * skips them unless EMI is loaded at the exact build they were written for; the JEI mixin goes through {@link JeiGate}, which
 * skips it unless a JEI 15.74 build is loaded on a client; the AdvancedAE mixin goes through {@link AaeGate}, which skips it
 * unless AdvancedAE is loaded at the exact build it was written for; the Gobber mixin goes through {@link GobberGate}, which skips
 * it unless Gobber 2 is loaded at the exact build it was written for with the patched method in place; the mixins into client-only Minecraft classes go through
 * {@link ClientGate}, which skips them on a dedicated server, where their target does not exist.
 */
public final class MixinFixPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /** Dev runs only: {@code -PskipFixes=<mixin simple names, comma-separated>} switches those mixins off for the run. */
    public static final String SKIP_PROPERTY = "bsp_mod_fixes.skipMixins";
    public static final String SKIP_REASON = "switched off for this run (-PskipFixes)";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (switchedOff(simpleName, System.getProperty(SKIP_PROPERTY))) {
            Fixes.mixinSkipped(simpleName, SKIP_REASON);
            Fixes.LOGGER.warn("BSP-Mod Fixes: {} is switched off for this run ({})", simpleName, SKIP_PROPERTY);
            return false;
        }
        if (Fixes.isEmiMixin(simpleName)) {
            return EmiGate.allows(simpleName);
        }
        if (JeiGate.gates(simpleName)) {
            return JeiGate.allows(simpleName);
        }
        if (AaeGate.gates(simpleName)) {
            return AaeGate.allows(simpleName);
        }
        if (GobberGate.gates(simpleName)) {
            return GobberGate.allows(simpleName);
        }
        if (ClientGate.gates(simpleName)) {
            return ClientGate.allows(simpleName);
        }
        return true;
    }

    /** Whether a mixin (simple name) is in the comma-separated skip list (null or blank: nothing is). Pure. */
    public static boolean switchedOff(String mixinSimpleName, String skipList) {
        if (skipList == null || skipList.isBlank()) {
            return false;
        }
        for (String name : skipList.split(",")) {
            if (name.trim().equals(mixinSimpleName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        Fixes.mixinApplied(mixinClassName, targetClassName);
    }
}
