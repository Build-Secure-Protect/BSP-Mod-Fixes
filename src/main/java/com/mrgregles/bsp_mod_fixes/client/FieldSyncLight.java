package com.mrgregles.bsp_mod_fixes.client;

import com.mrgregles.bsp_mod_fixes.fix.mffs.FieldSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * F-36, client only: the light checks for force field blocks whose data came with their chunk, timed after that chunk's light.
 * The client applies a chunk packet's light data from a queue it drains at render time, a part of it per frame, and the light
 * engine's one pass then checks the queued block nodes and propagates first and installs the queued section data last, so a
 * check made before that pass, or in it, is overwritten by the server's data (which knows no glow). A runnable put into the same
 * queue runs after every chunk's light data queued before it; it only records that the light has landed, and
 * {@link FieldSync#checkLanded} issues the checks at the next tick's end, after the pass that installed the data. Named only from a
 * client-side path of {@code fix/mffs/FieldSync}, so a server never loads it.
 */
public final class FieldSyncLight {
    private FieldSyncLight() {}

    /** Puts one runnable behind the light data of every chunk the client has received so far; it marks the positions as landed. */
    public static void afterChunkLight(Level level, List<BlockPos> positions) {
        if (level instanceof ClientLevel client) {
            client.queueLightUpdate(() -> FieldSync.lightLanded(level, positions));
        }
    }

    /** Whether this is the level the client is in now (a block entity of a level left behind must not be touched). */
    public static boolean isCurrentLevel(Level level) {
        return Minecraft.getInstance().level == level;
    }
}
