package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import com.mrgregles.bsp_mod_fixes.gametest.SaveCounter;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GameTest server only: counts every {@code ServerChunkCache.save(boolean)}, so the F-28 test sees for itself whether
 * Compact Machines' chunk-mode code saved the dimension, with the fix on, switched off, or not applied. Never shipped.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheSaveCounterMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void bsp_mod_fixes$count(boolean flush, CallbackInfo ci) {
        SaveCounter.count();
    }
}
