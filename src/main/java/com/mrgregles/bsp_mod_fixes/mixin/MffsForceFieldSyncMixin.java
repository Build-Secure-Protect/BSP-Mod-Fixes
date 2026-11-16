package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.mffs.FieldSync;
import dev.su5ed.mffs.blockentity.ForceFieldBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-36: a force field block's data travels in the chunk packet ({@link FieldSync}). The block entity gains the two vanilla
 * methods it lacks: {@code getUpdateTag} (what the server writes into the chunk packet for each block entity; vanilla's is
 * empty) returns MFFS's own {@code getCustomUpdateTag}, and {@code handleUpdateTag} (what the client calls with that tag as
 * the chunk arrives) hands it to MFFS's own {@code handleCustomUpdateTag}, exactly what MFFS's reply handler did. Forge calls
 * {@code onLoad} as the chunk creates the block entity, before that tag reaches it, so on the client MFFS's {@code onLoad} (the
 * request) is held until the end of the tick and then run only for a block entity whose chunk brought nothing (one placed into
 * a chunk the client already has); one whose tag has arrived is left alone. Targets an MFFS class ({@code remap = false}; its three
 * patched methods carry MFFS's and Forge's own names); {@code getUpdateTag} is Minecraft's and is renamed with the rest of
 * this jar. Applied only beside exactly the MFFS build it was written for ({@code fix/mffs/MffsGate}).
 */
@Mixin(value = ForceFieldBlockEntity.class, remap = false)
public abstract class MffsForceFieldSyncMixin extends BlockEntity implements FieldSync.Synced {
    @Shadow
    public abstract CompoundTag getCustomUpdateTag();

    @Shadow
    public abstract void handleCustomUpdateTag(CompoundTag tag);

    /** Client: set once the chunk packet's tag has been applied, so the held {@code onLoad} is not run. */
    @Unique
    private boolean bsp_mod_fixes$syncedFromChunk;

    @Override
    public boolean bsp_mod_fixes$isSyncedFromChunk() {
        return bsp_mod_fixes$syncedFromChunk;
    }

    private MffsForceFieldSyncMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Server: what the chunk packet carries for this block (the projector, the camouflage, the light). */
    @Override
    public CompoundTag getUpdateTag() {
        if (!FieldSync.enabled()) {
            return super.getUpdateTag();
        }
        FieldSync.chunkTagWritten();
        return getCustomUpdateTag();
    }

    /** Client: the chunk packet's tag, applied as MFFS applied its reply. */
    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (!FieldSync.enabled()) {
            super.handleUpdateTag(tag);
            return;
        }
        bsp_mod_fixes$syncedFromChunk = true;
        FieldSync.chunkTagApplied();
        handleCustomUpdateTag(tag);
    }

    /**
     * Client: MFFS's {@code onLoad} asks the server for the block's data. Forge calls it as the chunk creates the block entity,
     * before the chunk's tag is applied, so the call is held and replayed at the end of the tick by {@link FieldSync}: a block
     * entity whose tag arrived meanwhile is never asked for and has its light checked once the chunk's light has landed (Forge's own {@code onLoad} only asks for a model-data refresh, which
     * {@code handleCustomUpdateTag} has done, and the camouflage's render delegate is registered there too); any other runs this
     * method as MFFS wrote it. The server side is untouched.
     */
    @Inject(method = "onLoad", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$askOnlyIfTheChunkBringsNothing(CallbackInfo ci) {
        if (FieldSync.holdsOnLoad(this.level != null && this.level.isClientSide, FieldSync.enabled(), FieldSync.replaying())) {
            FieldSync.defer(this);
            ci.cancel();
        }
    }
}
