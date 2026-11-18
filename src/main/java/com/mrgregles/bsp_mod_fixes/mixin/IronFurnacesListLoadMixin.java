package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.ironfurnaces.FurnaceListRepair;
import ironfurnaces.capability.PlayerFurnacesListProvider;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-39: Iron Furnaces' {@code PlayerFurnacesListProvider.deserializeNBT} replaces the player's furnace list instead of appending
 * to it ({@link FurnaceListRepair}). The head injection reads the tag, keeps the first entry of each position, sets the list's
 * contents and returns, so Iron Furnaces' own loop (append without a check) does not run; with the switch off it runs as
 * shipped. The list is reached through the provider's own public fields, no shadow. Targets an Iron Furnaces class; the method
 * is Forge's {@code INBTSerializable} one and the tag class keeps its name in production, so nothing is remapped. Applied on both
 * sides, only beside exactly the Iron Furnaces build it was written for ({@code fix/ironfurnaces/IronFurnacesGate}).
 */
@Mixin(value = PlayerFurnacesListProvider.class, remap = false)
public abstract class IronFurnacesListLoadMixin {
    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$replaceInsteadOfAppend(CompoundTag tag, CallbackInfo ci) {
        if (!FurnaceListRepair.enabled()) {
            return;
        }
        PlayerFurnacesListProvider self = (PlayerFurnacesListProvider) (Object) this;
        FurnaceListRepair.load(tag, self.furnacesList.listFurances);
        ci.cancel();
    }
}
