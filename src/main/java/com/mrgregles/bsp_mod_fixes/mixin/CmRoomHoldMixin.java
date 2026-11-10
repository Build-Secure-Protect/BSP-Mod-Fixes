package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.cm.RoomLoader;
import dev.compactmods.machines.machine.CompactMachineBlockEntity;
import dev.compactmods.machines.tunnel.TunnelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F-29: a Compact Machines block reports its room to {@link RoomLoader} where Compact Machines would have forced or
 * released it for good, and reports itself gone when its block entity is removed (its chunk unloading, or the block
 * breaking; Compact Machines has no such step). A capability lookup into a room that is neither held nor loaded is answered
 * as "nothing here" instead of loading the room's chunk on the server thread. Targets a Compact Machines class (no
 * remapping); {@code setRemoved} is Minecraft's and is renamed with the rest of this jar. Applied only when Compact
 * Machines is loaded at the build it was written for ({@code fix/cm/CmGate}).
 */
@Mixin(value = CompactMachineBlockEntity.class, remap = false)
public abstract class CmRoomHoldMixin extends BlockEntity {
    @Shadow
    private ChunkPos roomChunk;

    private CmRoomHoldMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Redirect(method = "forceLoadIfHasTunnels", at = @At(value = "INVOKE", target = "Ldev/compactmods/machines/tunnel/TunnelHelper;setChunkMode(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ChunkPos;Z)V"))
    private void bsp_mod_fixes$holdByRule(ServerLevel rooms, ChunkPos room, boolean hasTunnels) {
        if (!RoomLoader.machineSynced(this, rooms, room)) {
            TunnelHelper.setChunkMode(rooms, room, hasTunnels);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$noLookupIntoAnUnloadedRoom(Capability cap, Direction side, CallbackInfoReturnable cir) {
        if (this.roomChunk != null && RoomLoader.refusesLookup(this.level, this.roomChunk)) {
            cir.setReturnValue(super.getCapability(cap, side));
        }
    }

    /** The admin command that unbinds a machine clears its room without any other step this mixin sees. */
    @Inject(method = "disconnect", at = @At("TAIL"))
    private void bsp_mod_fixes$forgetWhenUnbound(CallbackInfo ci) {
        RoomLoader.machineGone(this);
    }

    @Override
    public void setRemoved() {
        RoomLoader.machineGone(this);
        super.setRemoved();
    }
}
