package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.jei.JeiStartGate;
import mezz.jei.forge.events.PermanentEventSubscriptions;
import mezz.jei.forge.startup.StartEventObserver;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-19: JEI's start observer starts JEI only after the login's tag packet has been handled. {@code startIfReady} is
 * cancelled while {@link JeiStartGate#holdStart} says so; a {@code TagsUpdatedEvent} from a client packet marks the
 * login synced and queues {@code startIfReady} for the next client tick. Targets a JEI class (no remapping) that exists
 * on a client only, so the mixin config plugin applies it only on a client with a JEI 15.74 build whose observer still has
 * the two methods patched here ({@code fix/jei/JeiGate}).
 */
@Mixin(value = StartEventObserver.class, remap = false)
public abstract class JeiStartObserverMixin {
    @Shadow
    protected abstract void startIfReady();

    @Inject(method = "startIfReady", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$holdUntilTagsArrive(CallbackInfo ci) {
        if (JeiStartGate.holdStart(bsp_mod_fixes$currentConnection())) {
            ci.cancel();
        }
    }

    /**
     * At HEAD, not TAIL: JEI's jar is R8-optimised and {@code register} reuses the {@code this} and parameter slots for
     * other values, so a callback at the tail fails verification ("Bad type on operand stack"); at the head the slots hold
     * what their descriptors say. The listener's registration order does not matter.
     */
    @Inject(method = "register", at = @At("HEAD"))
    private void bsp_mod_fixes$startAfterTags(PermanentEventSubscriptions subscriptions, CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
            if (JeiStartGate.countsAsSync(event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED)) {
                JeiStartGate.tagsSynced(bsp_mod_fixes$currentConnection());
                // Next client tick, not inside the event: every other listener of this tag event has run by then (Thermal
                // rebuilds the recipe lists its JEI plugin reads there). The hold check runs again in startIfReady, so a
                // login that ended in between is held, not started.
                Minecraft.getInstance().tell(() -> startIfReady());
            }
        });
    }

    /**
     * The current login: the client's packet listener, which is new for every login, or null before one. Not the
     * {@code Connection} JEI keys its own observations on: a Velocity switch through Client Reset Packet keeps the same
     * {@code Connection} object (moved to LOGIN and back) and creates a new listener, and the new backend's tags have to be
     * waited for again.
     */
    @Unique
    private static Object bsp_mod_fixes$currentConnection() {
        return Minecraft.getInstance().getConnection();
    }
}
