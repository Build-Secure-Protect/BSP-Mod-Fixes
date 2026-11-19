package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.voltaic.CurioShieldingLookup;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import voltaic.api.radiation.CapabilityRadiationRecipient;

/**
 * F-40: the dose Voltaic's {@code recieveRadiation} hands to its Protection-enchantment step, what is left after the four armour
 * slots, is scaled by the equipped curios' Mekanism radiation shielding ({@link CurioShieldingLookup}). One call site, its second
 * argument; the first argument, the entity, is taken along to read its curios. {@code allow = 1} makes a second such call in a
 * changed Voltaic fail the mixin instead of being changed unseen. Targets a Voltaic class, whose names are never remapped
 * ({@code remap = false}); applied only beside exactly the Voltaic build it was written for, with Mekanism and Curios loaded
 * ({@code fix/voltaic/VoltaicGate}); switched off by {@code [voltaic] curioRadiationShielding = false}.
 */
@Mixin(value = CapabilityRadiationRecipient.class, remap = false)
public abstract class VoltaicCurioShieldingMixin {
    @ModifyArg(method = "recieveRadiation",
               at = @At(value = "INVOKE", target = "Lvoltaic/api/radiation/CapabilityRadiationRecipient;applyProtectionEnchantment(Lnet/minecraft/world/entity/LivingEntity;D)D"),
               index = 1, allow = 1)
    private double bsp_mod_fixes$shieldByCurios(LivingEntity entity, double unshielded) {
        return CurioShieldingLookup.apply(entity, unshielded);
    }
}
