package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.powersuits.RailgunShot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-46: the one {@code Player.startUsingItem(hand)} call in the Rail Gun module's {@code Ticker.use} goes to
 * {@link RailgunShot#skipItemUse}, which only counts, so a Rail Gun click fires its hitscan shot (or runs into its cooldown)
 * without the fist becoming a used item: no bow pose, no 25-tick use. {@code allow = 1} makes a second such call in a changed
 * build fail the mixin instead of being redirected unseen. Targets a Powersuits class, whose names are never remapped
 * ({@code remap = false}); the redirected call is Minecraft's, so that one target is remapped ({@code m_6672_} in the built
 * jar's refmap). Powersuits is mandatory at exactly this build, so the mixin is not gated; applied on both sides.
 */
@Mixin(targets = "lehjr.powersuits.common.item.module.weapon.RailgunModule$CapProvider$Ticker", remap = false)
public abstract class PowersuitsRailgunUseMixin {
    @Redirect(method = "use",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;startUsingItem(Lnet/minecraft/world/InteractionHand;)V", remap = true),
              allow = 1)
    private void bsp_mod_fixes$noItemUse(Player player, InteractionHand hand) {
        RailgunShot.skipItemUse(player, hand);
    }
}
