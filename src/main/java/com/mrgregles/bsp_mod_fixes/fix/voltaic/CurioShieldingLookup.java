package com.mrgregles.bsp_mod_fixes.fix.voltaic;

import mekanism.api.radiation.capability.IRadiationShielding;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * F-40's reading of the equipped curios: Curios' inventory of the entity once, every slot of every curio type, each stack's
 * Mekanism radiation-shielding capability (a token of this mod's own on Mekanism's API interface, as Voltaic itself holds one),
 * summed and stopped at 1 as Mekanism's own radiation manager sums it. Names Curios' and Mekanism's classes, so it is reached
 * only from the mixin, which the gate applies only when both are loaded. No collection is built per call.
 */
public final class CurioShieldingLookup {
    private static final Capability<IRadiationShielding> SHIELDING = CapabilityManager.get(new CapabilityToken<>() {});

    private CurioShieldingLookup() {}

    /** What the mixin hands to Voltaic's enchantment step in place of the dose left after armour. */
    public static double apply(LivingEntity entity, double unshielded) {
        if (unshielded <= 0 || !CurioShielding.enabled()) {
            return unshielded;
        }
        double curio = shielding(entity);
        if (curio <= 0) {
            return unshielded;
        }
        CurioShielding.shielded();
        return CurioShielding.scale(unshielded, curio);
    }

    /** The equipped curios' shielding, 0 to 1. */
    public static double shielding(LivingEntity entity) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(entity).resolve().orElse(null);
        if (inventory == null) {
            return 0;
        }
        double total = 0;
        for (ICurioStacksHandler type : inventory.getCurios().values()) {
            IDynamicStackHandler stacks = type.getStacks();
            for (int i = 0, n = stacks.getSlots(); i < n && total < 1.0; i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                IRadiationShielding shielding = stack.getCapability(SHIELDING).resolve().orElse(null);
                if (shielding != null) {
                    total = CurioShielding.sum(total, shielding.getRadiationShielding());
                }
            }
            if (total >= 1.0) {
                break;
            }
        }
        return total;
    }
}
