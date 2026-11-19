package com.mrgregles.bsp_mod_fixes.fix.evolvedmekanism;

import com.illusivesoulworks.elytraslot.ElytraSlotCommonMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * F-42's end of the redirected call: Elytra Slot's own elytra predicate, the test by which it will attach its curio capability
 * to the item's stacks, decides whether Evolved Mekanism's registration goes through to Curios. Names Elytra Slot's and Curios'
 * classes, so it is reached only from the mixin, which the gate applies only when both are loaded. Runs once per item at common
 * setup, on the main thread inside Evolved Mekanism's {@code enqueueWork}, with registries frozen; the predicate reads item ids,
 * no tags.
 */
public final class ElytraSlotLookup {
    private ElytraSlotLookup() {}

    /** What the mixin does in place of {@code CuriosApi.registerCurio(item, curio)}. */
    public static void register(Item item, ICurioItem curio) {
        if (ElytraCurioRegistration.skip(EvolvedMekanismGate.loaded(EvolvedMekanismGate.ELYTRA_SLOT_ID), isElytra(item))) {
            ElytraCurioRegistration.skipped(String.valueOf(ForgeRegistries.ITEMS.getKey(item)));
            return;
        }
        CuriosApi.registerCurio(item, curio);
    }

    /** Whether Elytra Slot treats the item's stacks as an elytra, by its own predicate. */
    public static boolean isElytra(Item item) {
        return ElytraSlotCommonMod.IS_ELYTRA.test(new ItemStack(item));
    }
}
