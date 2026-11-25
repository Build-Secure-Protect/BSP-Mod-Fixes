package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.powersuits.RailgunCooldown;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-47: the one cooldown read ({@code TagUtils.getModularItemDoubleOrZero(fist, "cooldown")}) and the one cooldown write
 * ({@code TagUtils.setModularItemDoubleOrRemove(fist, "cooldown", 15.0)}) in the Rail Gun module's {@code Ticker.use} go
 * through {@link RailgunCooldown}: the write also stamps the game time the cooldown ends at, the read answers the ticks left
 * by that stamp, so the cooldown runs by the clock whether or not the module ticks. Both handlers take the target method's
 * own arguments after the call's (the level's game time is read from them). {@code allow = 1} on each makes a second such
 * call in a changed build fail the mixin instead of being redirected unseen. Targets a Powersuits class and Numina's
 * {@code TagUtils}, neither remapped ({@code remap = false}); not gated, the mod is pinned; applied on both sides (the
 * client's {@code use} runs with the client's game time, for its own effects only).
 */
@Mixin(targets = "lehjr.powersuits.common.item.module.weapon.RailgunModule$CapProvider$Ticker", remap = false)
public abstract class PowersuitsRailgunCooldownMixin {
    @Redirect(method = "use",
              at = @At(value = "INVOKE", target = "Llehjr/numina/common/tags/TagUtils;getModularItemDoubleOrZero(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)D"),
              allow = 1)
    private double bsp_mod_fixes$cooldownByGameTime(ItemStack fist, String key, ItemStack itemStack, Level level, Player player, InteractionHand hand) {
        return RailgunCooldown.cooldown(fist, key, level);
    }

    @Redirect(method = "use",
              at = @At(value = "INVOKE", target = "Llehjr/numina/common/tags/TagUtils;setModularItemDoubleOrRemove(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;D)V"),
              allow = 1)
    private void bsp_mod_fixes$stampTheShot(ItemStack fist, String key, double value, ItemStack itemStack, Level level, Player player, InteractionHand hand) {
        RailgunCooldown.shot(fist, key, value, level);
    }
}
