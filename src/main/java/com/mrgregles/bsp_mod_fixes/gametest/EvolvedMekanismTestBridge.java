package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.fix.evolvedmekanism.ElytraSlotLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * Curios' and Elytra Slot's own classes for the F-42 tests, named here only, so the test class loads without the mods; a test
 * touches this class only after it has seen that all of them are loaded ({@code -PwithElytra}). The HDPE elytra and the canteen
 * are reached through the registry.
 */
final class EvolvedMekanismTestBridge {
    /** Elytra Slot's curio, what every elytra gets; Curios' wrapper of a registered {@code ICurioItem}, what Evolved Mekanism's registration gives. */
    static final String ELYTRA_SLOT_CURIO = "com.illusivesoulworks.elytraslot.common.CurioElytra";
    static final String REGISTERED_CURIO = "top.theillusivec4.curios.common.capability.ItemizedCurioCapability";

    /** A stack's {@code curios:item} lookup: the curio's class, or the exception the lookup threw (Forge's duplicate-key refusal as shipped). */
    record Lookup(String curioClass, String error) {}

    private EvolvedMekanismTestBridge() {}

    static Lookup curioOf(ItemStack stack) {
        try {
            ICurio curio = stack.getCapability(CuriosCapability.ITEM).resolve().orElse(null);
            return new Lookup(curio == null ? null : curio.getClass().getName(), null);
        } catch (Throwable t) {
            return new Lookup(null, t.getClass().getName() + ": " + t.getMessage());
        }
    }

    static boolean isElytra(Item item) {
        return ElytraSlotLookup.isElytra(item);
    }

    static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) {
            throw new IllegalStateException("no item " + id);
        }
        return item;
    }
}
