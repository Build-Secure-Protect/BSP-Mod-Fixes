package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * GameTest server only: lets a test end a mock player's spawn protection (vanilla gives every new server player 60 ticks
 * in which {@code ServerPlayer.hurt} refuses any hit), so a hit can be dealt through the real damage pipeline. In a dev run
 * the field has its official name; this config never ships, so no production mapping is needed.
 */
@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
    @Accessor("spawnInvulnerableTime")
    void bsp_mod_fixes$setSpawnInvulnerableTime(int ticks);
}
