package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.mffs.SpongeSampling;
import dev.su5ed.mffs.api.Projector;
import dev.su5ed.mffs.api.TargetPosPair;
import dev.su5ed.mffs.api.module.ProjectorMode;
import dev.su5ed.mffs.setup.ModModules;
import dev.su5ed.mffs.util.projector.CylinderProjectorMode;
import dev.su5ed.mffs.util.projector.SphereProjectorMode;
import dev.su5ed.mffs.util.ModUtil;
import dev.su5ed.mffs.util.module.SpongeModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * F-34: the sponge module drains by sampling ({@link SpongeSampling}). MFFS's {@code beforeSelect} (the interior copy, shuffle
 * and walk, run on the selection task's thread) is cancelled while the rule is on; the work moves to {@code beforeProject},
 * which MFFS calls from the projector's server tick every ten ticks, so the sampling reads loaded chunks on the server thread
 * ({@code getChunkNow} answers only there) and drains at once. Targets an MFFS class, whose names are never remapped
 * ({@code remap = false}); applied only beside exactly the MFFS build it was written for ({@code fix/mffs/MffsGate}).
 */
@Mixin(value = SpongeModule.class, remap = false)
public abstract class MffsSpongeMixin {
    @Inject(method = "beforeSelect", at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$skipTheWalk(Projector projector, Collection<? extends TargetPosPair> field, CallbackInfo ci) {
        if (SpongeSampling.enabled()) {
            ci.cancel(); // the only other thing the method does is an empty call into BaseModule
        }
    }

    @Inject(method = "beforeProject", at = @At("TAIL"))
    private void bsp_mod_fixes$drainBySampling(Projector projector, CallbackInfo ci) {
        if (!SpongeSampling.enabled() || !(projector.be().getLevel() instanceof ServerLevel level)) {
            return;
        }
        Optional<ProjectorMode> mode = projector.getMode();
        if (mode.isEmpty() || !SpongeSampling.passDue(projector.be(), projector.getTicks())) {
            return;
        }
        ProjectorMode shape = mode.get();
        BlockPos neg = projector.getNegativeScale();
        BlockPos pos = projector.getPositiveScale();
        // Each MFFS shape measures itself differently: the box follows the shape, the shape's own inside test decides each draw.
        SpongeSampling.Box box;
        if (shape instanceof SphereProjectorMode) {
            box = SpongeSampling.Box.sphere(projector.getModuleCount(ModModules.SCALE));
        } else if (shape instanceof CylinderProjectorMode) {
            box = SpongeSampling.Box.cylinder(neg, pos);
        } else {
            box = SpongeSampling.Box.cube(neg, pos);
        }
        long limit = SpongeSampling.interiorLimit();
        if (SpongeSampling.tooLarge(box.volume(), limit)) {
            SpongeSampling.skippedTooLarge(projector.be().getBlockPos(), box.volume(), limit);
            return;
        }
        // The same path MFFS's interior points take: rotate in the projector's axes, then move to the projector and its translation.
        BlockPos origin = projector.be().getBlockPos().offset(projector.getTranslation());
        int yaw = projector.getRotationYaw();
        int pitch = projector.getRotationPitch();
        int roll = projector.getRotationRoll();
        boolean rotated = yaw != 0 || pitch != 0 || roll != 0;
        UnaryOperator<Vec3> toWorld = local -> {
            Vec3 turned = rotated ? ModUtil.rotateByAngleExact(local, yaw, pitch, roll) : local;
            return Vec3.atLowerCornerOf(BlockPos.containing(turned).offset(origin));
        };
        SpongeSampling.StateAt states = p -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            return chunk == null ? null : chunk.getBlockState(p);
        };
        SpongeSampling.Pass pass = SpongeSampling.sample(box, toWorld, world -> shape.isInField(projector, world), states, level.random, SpongeSampling.perPass(projector.getProjectionSpeed()));
        for (BlockPos p : pass.fluid()) {
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()); // as MFFS's beforeProject sets its own list
        }
        for (BlockPos p : pass.waterlogged()) {
            BlockState state = level.getBlockState(p);
            if (SpongeSampling.isWaterlogged(state)) {
                level.setBlockAndUpdate(p, state.setValue(BlockStateProperties.WATERLOGGED, false));
            }
        }
        SpongeSampling.passed(pass);
    }
}
