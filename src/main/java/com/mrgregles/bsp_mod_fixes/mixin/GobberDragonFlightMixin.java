package com.mrgregles.bsp_mod_fixes.mixin;

import com.kwpugh.gobber2.items.armor.ItemCustomArmorDragon;
import com.mrgregles.bsp_mod_fixes.fix.gobber.DragonFlightSends;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-27: the dragon armour's tick sends the player's abilities only when they changed ({@link DragonFlightSends}). Targets a
 * Gobber class, whose names are never remapped, so the mixin is {@code remap = false}; the one call it redirects is into
 * Minecraft's {@code Player}, so that injection point alone is remapped (the refmap carries it). The mixin config plugin
 * applies the mixin only when Gobber 2 is loaded at the build it was written for ({@code fix/gobber/GobberGate}).
 */
@Mixin(value = ItemCustomArmorDragon.class, remap = false)
public abstract class GobberDragonFlightMixin {
    @Inject(method = "onArmorTick", at = @At("HEAD"))
    private void bsp_mod_fixes$remember(ItemStack stack, Level level, Player player, CallbackInfo ci) {
        DragonFlightSends.begin(player);
    }

    @Redirect(method = "onArmorTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onUpdateAbilities()V", remap = true))
    private void bsp_mod_fixes$sendOnChange(Player player) {
        DragonFlightSends.update(player);
    }
}
