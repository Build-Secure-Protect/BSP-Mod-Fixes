package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.NightVisionTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** F-5: the Night Vision module's refresh threshold and effect duration. Values in {@link NightVisionTiming}. */
@Mixin(targets = "lehjr.powersuits.common.item.module.vision.NightVisionModule$CapProvider$Ticker", remap = false)
public abstract class NightVisionTimingMixin {
    @ModifyConstant(method = "onPlayerTickActive", constant = @Constant(intValue = NightVisionTiming.ORIGINAL_REFRESH_UNDER))
    private int bsp_mod_fixes$refreshUnder(int original) {
        return NightVisionTiming.REFRESH_UNDER;
    }

    @ModifyConstant(method = "onPlayerTickActive", constant = @Constant(intValue = NightVisionTiming.ORIGINAL_DURATION))
    private int bsp_mod_fixes$duration(int original) {
        return NightVisionTiming.DURATION;
    }
}
