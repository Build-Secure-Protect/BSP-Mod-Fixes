package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.chunks.ChunkWatch;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-52: the two package-private methods every chunk ticket of a level passes through, {@code DistanceManager.addTicket(long,
 * Ticket)} and {@code removeTicket(long, Ticket)} (the public ticket methods, the region tickets, {@code updateChunkForced},
 * the player tracker and Forge's chunk manager all call them), tell {@link ChunkWatch} at their head; nothing is cancelled or
 * changed. The descriptors pick the overloads (the public {@code addTicket}/{@code removeTicket} take a type, a position, a
 * level and a key). Targets a Minecraft class, so the names are remapped through the refmap. {@code ChunkMap.DistanceManager}
 * is the instance in use and inherits both. Server side (a client never constructs one). Not gated.
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerTicketMixin {
    @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("HEAD"))
    private void bsp_mod_fixes$traceAdd(long chunkKey, Ticket<?> ticket, CallbackInfo ci) {
        ChunkWatch.onTicket((DistanceManager) (Object) this, chunkKey, ticket, true);
    }

    @Inject(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("HEAD"))
    private void bsp_mod_fixes$traceRemove(long chunkKey, Ticket<?> ticket, CallbackInfo ci) {
        ChunkWatch.onTicket((DistanceManager) (Object) this, chunkKey, ticket, false);
    }
}
