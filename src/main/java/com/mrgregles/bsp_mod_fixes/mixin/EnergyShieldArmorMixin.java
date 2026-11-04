package com.mrgregles.bsp_mod_fixes.mixin;

import com.google.common.collect.Multimap;
import com.mrgregles.bsp_mod_fixes.fix.EnergyShield;
import lehjr.powersuits.common.item.armor.AbstractElectricItemArmor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** F-8 (a): the Energy Shield's points leave the armor attribute. Logic in {@link EnergyShield#withoutShieldArmor}. */
@Mixin(value = AbstractElectricItemArmor.class, remap = false)
public abstract class EnergyShieldArmorMixin {
    @Inject(method = "getAttributeModifiers(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)Lcom/google/common/collect/Multimap;",
            at = @At("RETURN"), cancellable = true)
    private void bsp_mod_fixes$withoutShield(EquipmentSlot slot, ItemStack stack, CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> cir) {
        Multimap<Attribute, AttributeModifier> in = cir.getReturnValue();
        Multimap<Attribute, AttributeModifier> out = EnergyShield.withoutShieldArmor(slot, stack, in);
        if (out != in) {
            cir.setReturnValue(out);
        }
    }
}
