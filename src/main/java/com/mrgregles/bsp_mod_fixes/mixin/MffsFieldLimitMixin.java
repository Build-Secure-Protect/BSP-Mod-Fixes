package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.mffs.FieldLimit;
import dev.su5ed.mffs.api.Projector;
import dev.su5ed.mffs.api.TargetPosPair;
import dev.su5ed.mffs.blockentity.ProjectorBlockEntity;
import dev.su5ed.mffs.setup.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * F-35: a force field over the configured size is not projected ({@link FieldLimit}). The field's positions are calculated
 * by MFFS as before (on its calculation task's thread) and judged at the head of each selection, after the modules have
 * trimmed the list; while the projector is refused, its selection returns nothing and {@code projectField} is cancelled, so no
 * block is placed and no module acts; from the projector's server tick, once a minute, the players within fifteen blocks are
 * told, one throttled line is logged, and the field blocks the projector had built are removed where their chunks are loaded.
 * MFFS's own {@code destroyField} (a power loss, a module change, the block broken) forgets the refusal, and the calculated
 * positions are kept, so that teardown still clears the field as before.
 * Targets an MFFS class ({@code remap = false}); applied only beside exactly the MFFS build it was written for.
 */
@Mixin(value = ProjectorBlockEntity.class, remap = false)
public abstract class MffsFieldLimitMixin {
    @Unique
    private volatile FieldLimit.Judged bsp_mod_fixes$judged = FieldLimit.Judged.PASSES;
    @Unique
    private long bsp_mod_fixes$lastNotice = Long.MIN_VALUE;

    /**
     * Judged at the head of every selection (MFFS's selection thread): by then the calculated list has had the modules'
     * {@code onCalculate} applied (the Dome module removes the lower half), so the count is what would be projected.
     */
    @Inject(method = "selectProjectablePositions", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$judgeAndSelectNothing(CallbackInfoReturnable<List<TargetPosPair>> cir) {
        Projector self = (Projector) this;
        int blocks = self.getCalculatedFieldPositions().size();
        int radius = FieldLimit.radius(self.getNegativeScale(), self.getPositiveScale());
        FieldLimit.Verdict verdict = FieldLimit.judge(blocks, radius, FieldLimit.maxFieldBlocks(), FieldLimit.maxFieldRadius());
        boolean was = bsp_mod_fixes$judged.verdict().refused();
        bsp_mod_fixes$judged = new FieldLimit.Judged(verdict, blocks, radius);
        if (verdict.refused()) {
            if (!was) {
                bsp_mod_fixes$lastNotice = Long.MIN_VALUE; // tell them at once
                FieldLimit.refused();
            }
            cir.setReturnValue(new ArrayList<>());
        }
    }

    @Inject(method = "projectField", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$projectNothing(CallbackInfo ci) {
        if (bsp_mod_fixes$judged.verdict().refused()) {
            ci.cancel(); // no block placed, no module's beforeProject or onProject; the next selection returns nothing above
        }
    }

    /** MFFS tears the field down when the projector loses power or a module changes: the refusal goes with it, to be judged afresh. */
    @Inject(method = "destroyField", at = @At("HEAD"))
    private void bsp_mod_fixes$forget(CallbackInfo ci) {
        bsp_mod_fixes$judged = FieldLimit.Judged.PASSES;
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void bsp_mod_fixes$notice(CallbackInfo ci) {
        FieldLimit.Judged judged = bsp_mod_fixes$judged;
        Projector self = (Projector) this;
        if (!judged.verdict().refused() || !(self.be().getLevel() instanceof ServerLevel level)) {
            return;
        }
        long ticks = self.getTicks();
        if (!FieldLimit.noticeDue(ticks, bsp_mod_fixes$lastNotice)) {
            return;
        }
        bsp_mod_fixes$lastNotice = ticks;
        BlockPos at = self.be().getBlockPos();
        FieldLimit.warn(at, judged.verdict(), judged.fieldBlocks(), judged.radius());
        for (ServerPlayer player : FieldLimit.within(level.players(), at, FieldLimit.RANGE)) {
            FieldLimit.notify(player);
        }
        // What this projector had built, where its chunks are loaded: the calculated positions MFFS kept for us.
        Block forceField = ModBlocks.FORCE_FIELD.get();
        int removed = 0;
        for (TargetPosPair pair : self.getCalculatedFieldPositions()) {
            BlockPos p = pair.pos();
            LevelChunk chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            if (chunk != null && chunk.getBlockState(p).is(forceField)) {
                level.removeBlock(p, false);
                removed++;
            }
        }
        FieldLimit.tornDown(removed);
    }
}
