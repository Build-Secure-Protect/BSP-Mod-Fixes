package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * MF-30, a measurement and not a fix: what keeps chunks loaded after a Ballistix blast far from any player. With
 * {@code -PwithBallistix} (the pack's exact Voltaic, Electrodynamics, Nuclear Science, Ballistix and MFFS) a real blast entity
 * is spawned the way Ballistix's own explosives and missiles spawn one, in chunks a stand-in ticket loads for the moment a
 * player would be there; the ticket goes a few seconds later (the player walks away) and the level's tickets by type, its loaded
 * chunk count and Forge's saved store are read before the blast, as it starts, when its entity is gone and a minute after that.
 * Without the mods the tests say so and pass. Each test has a batch of its own: they share the level's chunks.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class BallistixBlastGameTests {
    static final String MOD_ID = "ballistix";
    /** Stands in for the player who set the explosive off: an ordinary untimed ticket, removed when they "walk away". */
    static final TicketType<ChunkPos> STAND_IN = TicketType.create("bsp_mod_fixes:test_stand_in", Comparator.comparingLong(ChunkPos::toLong));
    static final String TAG = "BSP-Mod Fixes probe (blast):";

    // The GameTest server ticks as fast as it can (about six hundred ticks a wall second here), while Ballistix works a blast
    // out on a thread of its own that needs real seconds: the waits below are long in ticks so they are long enough in wall time.
    static final int LONGEST = 60_000;

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 130_000, batch = "ballistix_nuclear")
    public void a_nuclear_blast_far_from_players_leaves_no_chunk_held_a_minute_after_it_ends(GameTestHelper h) {
        blast(h, "nuclear", new BlockPos(6000, 0, 6000), LONGEST);
    }

    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 130_000, batch = "ballistix_darkmatter")
    public void a_darkmatter_blast_far_from_players_leaves_no_chunk_held_a_minute_after_it_ends(GameTestHelper h) {
        blast(h, "darkmatter", new BlockPos(-6000, 0, -6000), LONGEST);
    }

    /** What the code says: a blast entity removed by anything but its own end keeps its Forge ticket, saved with the world. */
    @GameTest(template = ModFixesGameTests.EMPTY, timeoutTicks = 600, batch = "ballistix_kill")
    public void a_blast_entity_killed_before_it_ends_leaves_its_forge_ticket_in_the_saved_store(GameTestHelper h) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            Fixes.LOGGER.info("{} Ballistix is not loaded (-PwithBallistix): the kill case is not measured", TAG);
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        BlockPos site = surface(level, new BlockPos(6000, 0, -6000));
        ChunkPos chunk = new ChunkPos(site);
        ChunkHolds.Snapshot before = ChunkHolds.of(level);
        hold(level, chunk, true);
        h.runAfterDelay(40, () -> {
            UUID id = spawn(level, "nuclear", site);
            h.runAfterDelay(20, () -> {
                ChunkHolds.Snapshot live = ChunkHolds.of(level);
                Fixes.LOGGER.info("{} nuclear blast live at {}: {}", TAG, site, live.describe());
                int ticking = live.forgeSavedByMod().getOrDefault(MOD_ID + " block ticking", 0);
                h.assertTrue(ticking >= 1, "a live blast holds a ticking Forge block ticket under 'ballistix' (found " + ticking + ")");
                Entity blast = level.getEntity(id);
                h.assertTrue(blast != null, "the blast entity exists");
                blast.kill(); // what /kill does: Entity.remove(KILLED)
                h.runAfterDelay(5, () -> {
                    ChunkHolds.Snapshot after = ChunkHolds.of(level);
                    Fixes.LOGGER.info("{} nuclear blast killed: {}", TAG, after.describe());
                    h.assertTrue(level.getEntity(id) == null, "the blast entity is gone");
                    int left = after.forgeSavedByMod().getOrDefault(MOD_ID + " block ticking", 0);
                    h.assertTrue(left == ticking, "Ballistix released nothing on a kill: " + ticking + " saved ticking tickets before, " + left + " after");
                    // Put the dev world back as it was: release what Ballistix left, as an admin would have to.
                    boolean released = ForgeChunkManager.forceChunk(level, MOD_ID, site, chunk.x, chunk.z, false, true);
                    hold(level, chunk, false);
                    ChunkHolds.Snapshot cleaned = ChunkHolds.of(level);
                    Fixes.LOGGER.info("{} after releasing Ballistix's ticket by hand (released={}): {}", TAG, released, cleaned.describe());
                    h.assertTrue(cleaned.forgeSavedByMod().getOrDefault(MOD_ID + " block ticking", 0) == before.forgeSavedByMod().getOrDefault(MOD_ID + " block ticking", 0), "the store is back to its baseline");
                    h.succeed();
                });
            });
        });
    }

    private static void blast(GameTestHelper h, String kind, BlockPos where, int longest) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            Fixes.LOGGER.info("{} Ballistix is not loaded (-PwithBallistix): the {} blast is not measured", TAG, kind);
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        settle(h, level, 0, () -> begin(h, level, kind, where, longest));
    }

    /** Waits until no blast of an earlier test is still running (its Forge ticket is in the store), so the baseline is clean. */
    private static void settle(GameTestHelper h, ServerLevel level, int waited, Runnable then) {
        boolean busy = ChunkHolds.of(level).forgeSavedByMod().keySet().stream().anyMatch(k -> k.startsWith(MOD_ID + " "));
        if (busy && waited < LONGEST) {
            h.runAfterDelay(100, () -> settle(h, level, waited + 100, then));
            return;
        }
        then.run();
    }

    private static void begin(GameTestHelper h, ServerLevel level, String kind, BlockPos where, int longest) {
        ChunkHolds.Snapshot baseline = ChunkHolds.of(level);
        Fixes.LOGGER.info("{} {} blast, baseline: {}", TAG, kind, baseline.describe());
        BlockPos site = surface(level, where);
        ChunkPos chunk = new ChunkPos(site);
        hold(level, chunk, true);
        h.runAfterDelay(40, () -> {
            UUID id = spawn(level, kind, site);
            Fixes.LOGGER.info("{} {} blast spawned at {} (chunk {}), entity {}", TAG, kind, site, chunk, id);
            h.runAfterDelay(60, () -> {
                hold(level, chunk, false); // the player walks away
                Fixes.LOGGER.info("{} {} blast, three seconds in, the stand-in ticket gone: {}", TAG, kind, ChunkHolds.of(level).describe());
                waitForEnd(h, level, id, kind, 0, longest, baseline);
            });
        });
    }

    private static void waitForEnd(GameTestHelper h, ServerLevel level, UUID id, String kind, int waited, int longest, ChunkHolds.Snapshot baseline) {
        Entity blast = level.getEntity(id);
        if (blast != null && waited < longest) {
            if (waited % 3000 == 0 && waited > 0) {
                Fixes.LOGGER.info("{} {} blast, {} s in, still running at {}: {}", TAG, kind, waited / 20, blast.blockPosition(), ChunkHolds.of(level).describe());
            }
            h.runAfterDelay(20, () -> waitForEnd(h, level, id, kind, waited + 20, longest, baseline));
            return;
        }
        ChunkHolds.Snapshot over = ChunkHolds.of(level);
        Fixes.LOGGER.info("{} {} blast {} after {} s: {}", TAG, kind, blast == null ? "ended" : "STILL RUNNING (gave up waiting)", waited / 20, over.describe());
        h.runAfterDelay(1200, () -> {
            ChunkHolds.Snapshot later = ChunkHolds.of(level);
            Fixes.LOGGER.info("{} {} blast, a minute later: {}", TAG, kind, later.describe());
            Map<String, Integer> left = later.newTypesAgainst(baseline);
            h.assertTrue(left.isEmpty(), "a minute after the " + kind + " blast no ticket type is held that was not held before it: left " + left);
            h.assertTrue(later.forgeSavedByMod().equals(baseline.forgeSavedByMod()), "Forge's saved store is as before the blast: " + later.forgeSavedByMod() + " against " + baseline.forgeSavedByMod());
            h.assertTrue(later.loadedChunks() <= baseline.loadedChunks() + 9, "the blast's chunks have unloaded: " + later.loadedChunks() + " loaded against " + baseline.loadedChunks() + " before");
            h.succeed();
        });
    }

    /** Loads the chunk and finds the ground under the sky at that column. */
    private static BlockPos surface(ServerLevel level, BlockPos column) {
        level.getChunk(column.getX() >> 4, column.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        return new BlockPos(column.getX(), y + 1, column.getZ());
    }

    /** A radius-3 stand-in for a player: the blast's chunk and the ring around it tick while it is held. */
    private static void hold(ServerLevel level, ChunkPos chunk, boolean on) {
        if (on) {
            level.getChunkSource().addRegionTicket(STAND_IN, chunk, 3, chunk);
        } else {
            level.getChunkSource().removeRegionTicket(STAND_IN, chunk, 3, chunk);
        }
    }

    /**
     * Spawns Ballistix's blast entity as its explosives and missiles do ({@code Blast.performExplosion}: position, blast type,
     * nothing else, since no caller in 1.2.3 ever turns a blast persistent), through the entity's own saved-data reader so no
     * Ballistix class is named here.
     */
    private static UUID spawn(ServerLevel level, String kind, BlockPos site) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(MOD_ID, "blast"));
        if (type == null) {
            throw new IllegalStateException("no ballistix:blast entity type");
        }
        Entity blast = type.create(level);
        if (blast == null) {
            throw new IllegalStateException("ballistix:blast could not be created");
        }
        // Not saveWithoutId: Ballistix's own save encodes the blast id with a codec and throws on a fresh entity's null id.
        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(site.getX() + 0.5));
        pos.add(DoubleTag.valueOf(site.getY() + 0.5));
        pos.add(DoubleTag.valueOf(site.getZ() + 0.5));
        tag.put("Pos", pos);
        tag.putString("type", MOD_ID + ":" + kind);
        try {
            blast.load(tag);
            if (!level.addFreshEntity(blast)) {
                throw new IllegalStateException("the blast entity was not added");
            }
        } catch (RuntimeException e) {
            Fixes.LOGGER.error("{} spawning the {} blast failed", TAG, kind, e); // the GameTest reporter prints the message alone
            throw e;
        }
        return blast.getUUID();
    }
}
