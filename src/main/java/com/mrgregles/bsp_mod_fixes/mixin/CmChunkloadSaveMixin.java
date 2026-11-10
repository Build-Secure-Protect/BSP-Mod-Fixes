package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.cm.LoadSaves;
import dev.compactmods.machines.upgrade.ChunkloadUpgrade;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-28: the saves at the end of the Chunkloader upgrade's {@code forceLoad} and {@code normalLoad} (the upgrade applied,
 * removed, or the room dimension loading with it) go through {@link LoadSaves#save}, as {@link CmTunnelSaveMixin}'s does.
 */
@Mixin(value = ChunkloadUpgrade.class, remap = false)
public abstract class CmChunkloadSaveMixin {
    @Redirect(method = {"forceLoad", "normalLoad"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;save(Z)V", remap = true))
    private void bsp_mod_fixes$saveByRule(ServerChunkCache chunks, boolean flush) {
        LoadSaves.save(chunks, flush);
    }
}
