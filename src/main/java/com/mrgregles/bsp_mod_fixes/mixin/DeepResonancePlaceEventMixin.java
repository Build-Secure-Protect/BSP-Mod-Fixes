package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.deepresonance.PlaceEventLevel;
import mcjty.deepresonance.ForgeEventHandlers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-38: Deep Resonance's {@code ForgeEventHandlers.onBlockPlaceEvent} survives a place event with no entity
 * ({@link PlaceEventLevel}). The head guard returns from the handler (never cancelling the event, so the block is placed) when
 * there is no entity and the event's level is not a {@code Level}; the one {@code Entity.level()} call in the method, which
 * threw on the null entity, is answered with the event's own level instead. Targets a Deep Resonance class, whose names are
 * never remapped ({@code remap = false}); the redirected call is Minecraft's, so that one target is remapped ({@code m_9236_}
 * in the built jar's refmap). {@code allow = 1} makes a second such call in a changed build fail the mixin instead of being
 * redirected unseen. Applied on both sides, only beside exactly the Deep Resonance build it was written for
 * ({@code fix/deepresonance/DeepResonanceGate}); switched off by {@code [deepResonance] placeEventsWithoutPlacer = false}.
 */
@Mixin(value = ForgeEventHandlers.class, remap = false)
public abstract class DeepResonancePlaceEventMixin {
    @Inject(method = "onBlockPlaceEvent", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$skipWithoutALevel(BlockEvent.EntityPlaceEvent event, CallbackInfo ci) {
        if (PlaceEventLevel.skip(event.getEntity(), event.getLevel())) {
            ci.cancel(); // returns from the handler only; the event stays as it was
        }
    }

    @Redirect(method = "onBlockPlaceEvent",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;level()Lnet/minecraft/world/level/Level;", remap = true),
              allow = 1)
    private Level bsp_mod_fixes$levelFromTheEvent(Entity entity, BlockEvent.EntityPlaceEvent event) {
        return PlaceEventLevel.levelOf(entity, event);
    }
}
