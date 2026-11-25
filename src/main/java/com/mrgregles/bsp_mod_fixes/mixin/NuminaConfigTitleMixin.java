package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.powersuits.SilkTouchConfigKey;
import lehjr.numina.common.capabilities.module.powermodule.ModuleCategory;
import lehjr.numina.common.capabilities.module.powermodule.PowerModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-45: the one {@code ModuleCategory.getConfigTitle()} call in Numina's {@code PowerModule} constructor, which names the
 * config section a module's keys are built under, goes through {@link SilkTouchConfigKey#titleFor}: "Mining_Enchantment"
 * (the three Mining Enchantment modules' category) becomes "Mining_Enhancement", the section Powersuits' spec writes them
 * under; every other title is itself. {@code allow = 1} makes a second such call in a changed build fail the mixin instead of
 * being changed unseen. Targets a Numina class, whose names are never remapped ({@code remap = false}); Numina is mandatory
 * at exactly this build, so the mixin is not gated (as the other Numina fixes).
 */
@Mixin(value = PowerModule.class, remap = false)
public abstract class NuminaConfigTitleMixin {
    @Redirect(method = "<init>",
              at = @At(value = "INVOKE", target = "Llehjr/numina/common/capabilities/module/powermodule/ModuleCategory;getConfigTitle()Ljava/lang/String;"),
              allow = 1)
    private String bsp_mod_fixes$titleTheSpecWrites(ModuleCategory category) {
        return SilkTouchConfigKey.titleFor(category.getConfigTitle());
    }
}
