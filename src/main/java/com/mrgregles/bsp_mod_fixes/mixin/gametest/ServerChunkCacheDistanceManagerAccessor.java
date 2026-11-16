package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GameTest server only: a level's distance manager, which holds its chunk tickets; the blast tests read them. Never shipped. */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheDistanceManagerAccessor {
    @Accessor("distanceManager")
    DistanceManager bsp_mod_fixes$distanceManager();
}
