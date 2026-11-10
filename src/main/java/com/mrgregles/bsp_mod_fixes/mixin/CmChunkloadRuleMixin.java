package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.cm.RoomLoader;
import dev.compactmods.machines.upgrade.ChunkloadUpgrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F-29: the Chunkloader upgrade no longer forces its room for good or releases it by itself; a room with the upgrade is
 * one that wants loading, held under the same rule as a room with a tunnel ({@link RoomLoader}; D61).
 */
@Mixin(value = ChunkloadUpgrade.class, remap = false)
public abstract class CmChunkloadRuleMixin {
    @Inject(method = {"forceLoad", "normalLoad"}, at = @At("HEAD"), cancellable = true)
    private void bsp_mod_fixes$underTheRoomRule(ServerLevel level, ChunkPos room, CallbackInfo ci) {
        if (RoomLoader.enabled()) {
            ci.cancel();
        }
    }
}
