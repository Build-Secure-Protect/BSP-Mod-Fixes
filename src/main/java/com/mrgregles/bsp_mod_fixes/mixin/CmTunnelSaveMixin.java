package com.mrgregles.bsp_mod_fixes.mixin;

import com.mrgregles.bsp_mod_fixes.fix.cm.LoadSaves;
import dev.compactmods.machines.tunnel.TunnelHelper;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * F-28: the save at the end of Compact Machines' {@code TunnelHelper.setChunkMode}, reached from every machine block
 * entity's load, goes through {@link LoadSaves#save}. Targets a Compact Machines class (no remapping); the redirected call
 * is into Minecraft's {@code ServerChunkCache}, so that injection point alone is remapped. Applied only when Compact
 * Machines is loaded at the build it was written for ({@code fix/cm/CmGate}).
 */
@Mixin(value = TunnelHelper.class, remap = false)
public abstract class CmTunnelSaveMixin {
    @Redirect(method = "setChunkMode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;save(Z)V", remap = true))
    private static void bsp_mod_fixes$saveByRule(ServerChunkCache chunks, boolean flush) {
        LoadSaves.save(chunks, flush);
    }
}
