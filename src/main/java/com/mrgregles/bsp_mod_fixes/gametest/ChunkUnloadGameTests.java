package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.chunks.UnloadIteration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MF-44: a chunk's unload over copies (F-48). The pure pass over a plain map, then a detached {@code LevelChunk} (never in the
 * chunk map, nothing in the world touched) whose block entity map holds chest block entities that add and re-create entries
 * during the unload, as a hook adding a block entity and a neighbour lookup of a removed block entity do; with
 * {@code -PskipFixes=LevelChunkUnloadMixin} the test asserts vanilla's exception instead.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class ChunkUnloadGameTests {
    private static int chunkCounter;

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_chunk_unload_runs_over_copies_names_the_culprit_and_removes_what_was_added(GameTestHelper h) {
        // the pure pass: an action that adds, one that replaces its own entry, one that removes another
        Map<String, String> map = new LinkedHashMap<>();
        map.put("a", "A");
        map.put("b", "B");
        map.put("c", "C");
        List<String> seen = new ArrayList<>();
        UnloadIteration.PassReport report = UnloadIteration.runPass(map, v -> {
            seen.add(v);
            switch (v) {
                case "A" -> map.put("d", "D");
                case "B" -> { map.remove("b"); map.put("b", "B2"); }
                case "C" -> map.remove("a");
                default -> {}
            }
        }, v -> v.substring(0, 1).toLowerCase());
        h.assertTrue(seen.equals(List.of("A", "B", "C")), "every value of the start is visited once, in order, whatever the actions do: " + seen);
        h.assertTrue(report.changes().size() == 3, "three changes seen: " + report.changes());
        h.assertTrue(report.changes().get(0).actor().equals("A") && report.changes().get(0).added() == 1 && report.changes().get(0).removed() == 0, "A added one: " + report.changes().get(0));
        h.assertTrue(report.changes().get(1).actor().equals("B") && report.changes().get(1).added() == 1 && report.changes().get(1).removed() == 1, "B replaced its own entry (+1/-1): " + report.changes().get(1));
        h.assertTrue(report.changes().get(2).actor().equals("C") && report.changes().get(2).added() == 0 && report.changes().get(2).removed() == 1, "C removed one: " + report.changes().get(2));
        h.assertTrue(report.leftovers().size() == 2 && report.leftovers().contains("B2") && report.leftovers().contains("D"), "the entries the pass never saw are left over: " + report.leftovers());
        Map<String, String> quiet = new LinkedHashMap<>(Map.of("x", "X"));
        h.assertFalse(UnloadIteration.runPass(quiet, v -> {}, v -> "x").changedAnything(), "a pass that changes nothing reports nothing");

        // the sweep after the pass: a removal that re-creates the entry once more takes two rounds; one that always re-creates is given up after the bound
        Map<String, String> once = new LinkedHashMap<>(Map.of("k", "K1"));
        UnloadIteration.SweepReport sweep = UnloadIteration.sweep(once, List.of(), v -> { if (v.equals("K1")) { once.put("k", "K2"); } else { once.remove("k"); } }, UnloadIteration.MAX_SWEEP_ROUNDS);
        h.assertTrue(sweep.removed().equals(List.of("K1", "K2")) && sweep.rounds() == 2 && !sweep.exhausted(), "a re-created entry is swept in a second round: " + sweep);
        Map<String, String> forever = new LinkedHashMap<>(Map.of("k", "K0"));
        int[] n = {0};
        UnloadIteration.SweepReport endless = UnloadIteration.sweep(forever, List.of(), v -> forever.put("k", "K" + (++n[0])), UnloadIteration.MAX_SWEEP_ROUNDS);
        h.assertTrue(endless.rounds() == UnloadIteration.MAX_SWEEP_ROUNDS && endless.removed().size() == UnloadIteration.MAX_SWEEP_ROUNDS && endless.exhausted() && endless.stillThere().equals(List.of("K" + UnloadIteration.MAX_SWEEP_ROUNDS)),
                "an entry re-created on every removal is given up after the bound, the last copy reported: " + endless);
        h.assertFalse(UnloadIteration.sweep(new LinkedHashMap<>(Map.of("k", "K")), List.of("K"), v -> {}, 8).exhausted() || UnloadIteration.sweep(new LinkedHashMap<>(Map.of("k", "K")), List.of("K"), v -> {}, 8).rounds() != 0, "nothing new: no round");

        h.assertTrue(FixesConfig.SPEC.isLoaded(), "the server config is loaded on the test server");
        boolean fixOn = !Fixes.skipped().containsKey(UnloadIteration.ID);
        h.assertTrue(fixOn || Fixes.skipped().get(UnloadIteration.ID).equals(MixinFixPlugin.SKIP_REASON), "F-48 is applied or switched off for this run: " + Fixes.skipped());

        // a detached chunk with two culprits: one adds a block entity in its onChunkUnloaded, one re-creates its own entry
        // in its setRemoved, as Level.getBlockEntity does for a removed block entity (drop, then create and put)
        Culprits culprits = culprits(h);
        if (fixOn) {
            long leftovers = UnloadIteration.leftoversRemoved();
            long changed = UnloadIteration.changedUnloads();
            culprits.chunk.clearAllBlockEntities();
            h.assertTrue(culprits.chunk.getBlockEntities().isEmpty(), "the map is cleared after both passes, got " + culprits.chunk.getBlockEntities());
            h.assertTrue(culprits.added[0] != null && culprits.added[0].isRemoved(), "the block entity added during the first pass was removed by the second");
            h.assertTrue(culprits.recreated[0] != null && culprits.recreated[0].isRemoved(), "the block entity re-created during the second pass was removed by the sweep");
            h.assertTrue(culprits.adder.isRemoved() && culprits.replacer.isRemoved(), "the original block entities were removed");
            h.assertTrue(UnloadIteration.leftoversRemoved() == leftovers + 1, "one leftover swept, got " + (UnloadIteration.leftoversRemoved() - leftovers));
            h.assertTrue(UnloadIteration.changedUnloads() >= changed + 1, "the unload is counted as one that changed its block entities");
            h.assertTrue(UnloadIteration.warned().contains("minecraft:chest") && UnloadIteration.warned().contains("leftover:minecraft:chest"),
                    "the WARN lines name minecraft:chest as the culprit and as the leftover: " + UnloadIteration.warned());
        } else {
            h.assertTrue(throwsConcurrentModification(culprits.chunk), "as shipped the unload throws ConcurrentModificationException");
        }

        // the switch off: vanilla's walk over the live map, the exception
        boolean saved = FixesConfig.CHUNKS_UNLOAD_OVER_COPIES.get();
        try {
            FixesConfig.CHUNKS_UNLOAD_OVER_COPIES.set(false);
            h.assertFalse(UnloadIteration.enabled(), "the switch reads false");
            h.assertTrue(throwsConcurrentModification(culprits(h).chunk), "with the switch off the unload throws as vanilla does");
        } finally {
            FixesConfig.CHUNKS_UNLOAD_OVER_COPIES.set(saved);
        }
        h.succeed();
    }

    private static boolean throwsConcurrentModification(LevelChunk chunk) {
        try {
            chunk.clearAllBlockEntities();
            return false;
        } catch (ConcurrentModificationException e) {
            return true;
        }
    }

    private record Culprits(LevelChunk chunk, ChestBlockEntity adder, ChestBlockEntity replacer, BlockEntity[] added, BlockEntity[] recreated) {}

    /** A detached chunk far from anything, holding a chest that adds a block entity on unload and a chest that re-creates itself on removal. */
    private static Culprits culprits(GameTestHelper h) {
        ChunkPos pos = new ChunkPos(60_000 + chunkCounter++, 60_000);
        // the constructor fires Forge's AttachCapabilitiesEvent for a chunk that is never put in the chunk map nor invalidated;
        // only this mod listens on the GameTest server, so nothing is attached to the ghost chunk
        LevelChunk chunk = new LevelChunk(h.getLevel(), pos);
        Map<BlockPos, BlockEntity> map = chunk.getBlockEntities();
        BlockState chest = Blocks.CHEST.defaultBlockState();
        BlockPos p1 = pos.getWorldPosition().offset(1, 64, 1);
        BlockPos p2 = pos.getWorldPosition().offset(2, 64, 2);
        BlockPos p3 = pos.getWorldPosition().offset(3, 64, 3);
        BlockEntity[] added = new BlockEntity[1];
        BlockEntity[] recreated = new BlockEntity[1];
        ChestBlockEntity adder = new ChestBlockEntity(p1, chest) {
            @Override
            public void onChunkUnloaded() {
                super.onChunkUnloaded();
                added[0] = new ChestBlockEntity(p3, chest);
                map.put(p3, added[0]);
            }
        };
        ChestBlockEntity replacer = new ChestBlockEntity(p2, chest) {
            @Override
            public void setRemoved() {
                super.setRemoved();
                // what LevelChunk.getBlockEntity(pos, IMMEDIATE) does when a neighbour looks a removed block entity up
                if (map.get(p2) == this) {
                    map.remove(p2);
                    recreated[0] = new ChestBlockEntity(p2, chest);
                    map.put(p2, recreated[0]);
                }
            }
        };
        map.put(p1, adder);
        map.put(p2, replacer);
        return new Culprits(chunk, adder, replacer, added, recreated);
    }
}
