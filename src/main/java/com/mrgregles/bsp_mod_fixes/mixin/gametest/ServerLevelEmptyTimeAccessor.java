package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * GameTest server only: a level's count of ticks without players. Past 300, a level with no players and no saved forced
 * chunk stops ticking its entities and block entities; the F-29 test sets it there to stand in for a room dimension
 * nobody has been in for a while. Never shipped.
 */
@Mixin(ServerLevel.class)
public interface ServerLevelEmptyTimeAccessor {
    @Accessor("emptyTime")
    void bsp_mod_fixes$setEmptyTime(int ticks);
}
