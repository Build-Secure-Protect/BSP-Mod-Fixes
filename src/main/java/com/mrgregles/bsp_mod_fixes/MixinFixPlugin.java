package com.mrgregles.bsp_mod_fixes;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.emi.EmiGate;
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
 * skips them unless EMI is loaded at the exact build they were written for; the mixins into client-only Minecraft classes go
 * through {@link ClientGate}, which skips them on a dedicated server, where their target does not exist.
 */
public final class MixinFixPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (Fixes.isEmiMixin(simpleName)) {
            return EmiGate.allows(simpleName);
        }
        if (ClientGate.gates(simpleName)) {
            return ClientGate.allows(simpleName);
        }
        return true;
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
