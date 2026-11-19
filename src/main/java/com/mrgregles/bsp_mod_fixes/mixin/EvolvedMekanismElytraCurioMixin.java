package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.evolvedmekanism.ElytraSlotLookup;
import fr.iglee42.evolvedmekanism.curios.CuriosCompat;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * F-42: the one {@code CuriosApi.registerCurio} call in Evolved Mekanism's Curios setup (the body of its {@code enqueueWork}
 * lambda, a synthetic method of this build) goes through {@link ElytraSlotLookup}, which skips an item Elytra Slot already
 * gives a curio capability and registers every other one as before. {@code allow = 1} makes a second such call in a changed
 * build fail the mixin instead of being changed unseen. Targets an Evolved Mekanism class, whose names are never remapped
 * ({@code remap = false}; class names in the descriptor are not renamed in 1.20.1 Forge); applied only beside exactly the
 * Evolved Mekanism build it was written for, with Elytra Slot and Curios loaded ({@code fix/evolvedmekanism/EvolvedMekanismGate}).
 */
@Mixin(value = CuriosCompat.class, remap = false)
public abstract class EvolvedMekanismElytraCurioMixin {
    @Redirect(method = "lambda$onCommonSetup$0",
              at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/CuriosApi;registerCurio(Lnet/minecraft/world/item/Item;Ltop/theillusivec4/curios/api/type/capability/ICurioItem;)V"),
              allow = 1)
    private static void bsp_mod_fixes$registerUnlessElytraSlotHasIt(Item item, ICurioItem curio) {
        ElytraSlotLookup.register(item, curio);
    }
}
