package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.voltaic.CurioShieldingLookup;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import voltaic.api.radiation.CapabilityRadiationRecipient;
import voltaic.registers.VoltaicEffects;

/**
 * Voltaic's, Curios' and Mekanism's own classes for the F-40 tests, named here only, so the test class loads without the mods;
 * a test touches this class only after it has seen that all of them are loaded ({@code -PwithHazmat}). Evolved Mekanism's suit
 * is reached through the registry; its switch is Mekanism's item data boolean {@code curio_active}.
 */
final class VoltaicTestBridge {
    private VoltaicTestBridge() {}

    /** A fresh recipient, the class the mixin patches (Voltaic attaches one to every living entity). */
    static Object recipient() {
        return new CapabilityRadiationRecipient();
    }

    /** Voltaic's real dose, through the real method (patched or not). */
    static void dose(Object recipient, LivingEntity entity, double rads, double strength) {
        ((CapabilityRadiationRecipient) recipient).recieveRadiation(entity, rads, strength);
    }

    /** The dose Voltaic recorded since its last tick, as its readouts show it: the recipient's tick moves the running total into view. */
    static double recorded(Object recipient, LivingEntity entity) {
        CapabilityRadiationRecipient r = (CapabilityRadiationRecipient) recipient;
        r.tick(entity);
        return r.getRecievedRadiation(entity).amount();
    }

    static boolean irradiated(LivingEntity entity) {
        return entity.hasEffect(VoltaicEffects.RADIATION.get());
    }

    /** Evolved Mekanism's Portable Hazmat Suit, switched on or off. */
    static ItemStack suit(boolean active) {
        ItemStack stack = new ItemStack(item("evolvedmekanism:portable_hazmat_suit"));
        ItemDataUtils.setBoolean(stack, "curio_active", active);
        return stack;
    }

    static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) {
            throw new IllegalStateException("no item " + id);
        }
        return item;
    }

    /** Puts a stack into the first slot of a curio type; false when the entity has no such slot. */
    static boolean equipCurio(LivingEntity entity, String slotType, ItemStack stack) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(entity).resolve().orElseThrow();
        ICurioStacksHandler handler = inventory.getStacksHandler(slotType).orElse(null);
        if (handler == null) {
            return false;
        }
        handler.getStacks().setStackInSlot(0, stack);
        return true;
    }

    static double curioShielding(LivingEntity entity) {
        return CurioShieldingLookup.shielding(entity);
    }
}
