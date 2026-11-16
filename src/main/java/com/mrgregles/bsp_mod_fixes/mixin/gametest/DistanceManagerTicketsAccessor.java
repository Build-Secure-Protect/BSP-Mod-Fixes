package com.mrgregles.bsp_mod_fixes.mixin.gametest;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * GameTest server only: every chunk ticket of a level, by chunk. A ticket names its type (vanilla's player, forced, portal and
 * so on, Forge's block and entity tickets, a mod's own) and its level; the blast tests count them by type before and after a
 * blast. Never shipped.
 */
@Mixin(DistanceManager.class)
public interface DistanceManagerTicketsAccessor {
    @Accessor("tickets")
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> bsp_mod_fixes$tickets();
}
