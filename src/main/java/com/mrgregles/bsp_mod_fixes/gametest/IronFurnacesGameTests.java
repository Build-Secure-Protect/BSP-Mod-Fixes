package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.FixesConfig;
import com.mrgregles.bsp_mod_fixes.MixinFixPlugin;
import com.mrgregles.bsp_mod_fixes.fix.Fixes;
import com.mrgregles.bsp_mod_fixes.fix.ironfurnaces.FurnaceListRepair;
import com.mrgregles.bsp_mod_fixes.fix.ironfurnaces.IronFurnacesGate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MF-36: a player's Iron Furnaces list is replaced when it loads, duplicates dropped (F-39). The repair, the cap and the gate are
 * pure; with {@code -PwithIf} (the pack's Iron Furnaces 4.1.8) a real provider is loaded twice from a duplicated tag through the
 * real method, and a mock player's attached capability is loaded through Forge's {@code deserializeCaps}, the path vanilla and
 * BSP-InventorySync take: two entries with the fix, the sum of both loads as shipped. No Iron Furnaces class is named here.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class IronFurnacesGameTests {
    static final BlockPos A = new BlockPos(6204, 150, 7089);
    static final BlockPos B = new BlockPos(6207, 149, 7082);

    /** A saved list in Iron Furnaces' own layout: {@code count} entries, the positions repeated in turn. */
    static CompoundTag savedList(int count, BlockPos... positions) {
        CompoundTag tag = new CompoundTag();
        CompoundTag furnaces = new CompoundTag();
        for (int i = 0; i < count; i++) {
            BlockPos p = positions[i % positions.length];
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", p.getX());
            entry.putInt("Y", p.getY());
            entry.putInt("Z", p.getZ());
            furnaces.put("furnace" + i, entry);
        }
        tag.put("furnaces", furnaces);
        tag.putInt("count", count);
        return tag;
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void a_loaded_furnace_list_keeps_one_entry_per_position_replaces_the_old_list_and_the_iron_furnaces_gate_holds(GameTestHelper h) {
        // the repair over a bloated list like siber18's, two positions in turn
        long started = System.nanoTime();
        FurnaceListRepair.Result big = FurnaceListRepair.read(savedList(300_000, A, B));
        long ms = (System.nanoTime() - started) / 1_000_000;
        Fixes.LOGGER.info("BSP-Mod Fixes test: 300,000 saved entries read and deduplicated in {} ms", ms);
        h.assertTrue(big.kept().equals(List.of(A, B)) && big.read() == 300_000 && big.removed() == 299_998, "two kept in their first order, 299,998 dropped: " + big.kept() + " " + big.removed());
        h.assertTrue(ms < 10_000, "the repair of 300,000 entries takes well under ten seconds: " + ms + " ms");
        // the layout read as Iron Furnaces reads it
        FurnaceListRepair.Result plain = FurnaceListRepair.read(savedList(2, A, B));
        h.assertTrue(plain.kept().equals(List.of(A, B)) && plain.removed() == 0, "a clean list is kept as it is");
        CompoundTag holes = savedList(3, A, B, A);
        holes.getCompound("furnaces").remove("furnace1");
        FurnaceListRepair.Result withHole = FurnaceListRepair.read(holes);
        h.assertTrue(withHole.kept().equals(List.of(A, BlockPos.ZERO)) && withHole.removed() == 1, "a missing entry reads as 0, 0, 0, as Iron Furnaces' own loop reads it: " + withHole.kept());
        h.assertTrue(FurnaceListRepair.read(new CompoundTag()).kept().isEmpty(), "an empty tag is an empty list");
        CompoundTag negative = new CompoundTag();
        negative.putInt("count", -5);
        h.assertTrue(FurnaceListRepair.read(negative).kept().isEmpty(), "a negative count reads nothing");
        // a load replaces
        List<BlockPos> list = new ArrayList<>(List.of(A, B));
        FurnaceListRepair.replace(list, List.of(B, new BlockPos(1, 2, 3)));
        h.assertTrue(list.equals(List.of(B, new BlockPos(1, 2, 3))), "the list holds exactly what was loaded: " + list);
        // the cap
        h.assertTrue(!FurnaceListRepair.overCap(4096, 4096) && FurnaceListRepair.overCap(4097, 4096) && !FurnaceListRepair.overCap(1_000_000, 0), "over the cap only above it, never at 0");
        h.assertTrue(FixesConfig.SPEC.isLoaded() && FixesConfig.IF_REPAIR_FURNACE_LIST.get() && FixesConfig.IF_MAX_FURNACES_PER_PLAYER.get() == 4096 && FurnaceListRepair.enabled() && FurnaceListRepair.cap() == 4096, "on by default, 4096 by default");
        // the queue: a load that dropped entries waits for its player's name, then is logged once; a clean load is not queued
        FurnaceListRepair.resolve(l -> null, Long.MAX_VALUE); // another test's leftovers, whatever the order the tests ran in
        int pendingBefore = FurnaceListRepair.pending();
        h.assertTrue(pendingBefore == 0, "the queue starts empty");
        List<BlockPos> live = new ArrayList<>();
        FurnaceListRepair.Result loaded = FurnaceListRepair.load(savedList(1_000, A, B), live, 100);
        h.assertTrue(live.equals(List.of(A, B)) && loaded.removed() == 998, "the live list holds the two: " + live);
        h.assertTrue(FurnaceListRepair.pending() == pendingBefore + 1, "the load is queued for a name");
        List<BlockPos> clean = new ArrayList<>();
        FurnaceListRepair.load(savedList(2, A, B), clean, 100);
        h.assertTrue(FurnaceListRepair.pending() == pendingBefore + 1, "a clean load is not queued");
        List<String> unnamed = FurnaceListRepair.resolve(l -> null, 150);
        h.assertTrue(unnamed.isEmpty() && FurnaceListRepair.pending() == pendingBefore + 1, "no player holds the list yet: it waits");
        List<String> named = FurnaceListRepair.resolve(l -> l == live ? "siber18" : null, 160);
        h.assertTrue(named.size() == 1 && named.get(0).contains("removed 998 duplicate Iron Furnaces list entries for siber18 (2 kept)"), "logged once, with the name: " + named);
        h.assertTrue(FurnaceListRepair.pending() == pendingBefore, "and dropped from the queue");
        List<BlockPos> orphan = new ArrayList<>();
        FurnaceListRepair.load(savedList(4, A, B), orphan, 200);
        h.assertTrue(FurnaceListRepair.resolve(l -> null, 200 + FurnaceListRepair.NAME_WAIT_TICKS - 1).isEmpty(), "still waiting just before the limit");
        List<String> gaveUp = FurnaceListRepair.resolve(l -> null, 200 + FurnaceListRepair.NAME_WAIT_TICKS);
        h.assertTrue(gaveUp.size() == 1 && gaveUp.get(0).contains("removed 2 duplicate Iron Furnaces list entries for a player no longer online (2 kept)"), "logged without a name after the wait: " + gaveUp);
        // over the cap: a WARN naming the player, nothing removed
        BlockPos[] many = new BlockPos[4097];
        for (int i = 0; i < many.length; i++) {
            many[i] = new BlockPos(i, 64, -i);
        }
        List<BlockPos> crowded = new ArrayList<>();
        FurnaceListRepair.load(savedList(4097, many), crowded, 300);
        h.assertTrue(crowded.size() == 4097, "nothing but duplicates is ever removed: " + crowded.size());
        List<String> warned = FurnaceListRepair.resolve(l -> l == crowded ? "hoarder" : null, 300);
        h.assertTrue(warned.size() == 1 && warned.get(0).contains("hoarder holds 4097 furnaces, over maxFurnacesPerPlayer (4096)"), "the cap warning names the player: " + warned);
        h.assertTrue(FurnaceListRepair.pending() == 0, "nothing left in the queue for another test");
        // the gate
        h.assertTrue(IronFurnacesGate.verdict(Optional.of(IronFurnacesGate.VERSION)).applies(), "exactly " + IronFurnacesGate.VERSION + " applies");
        h.assertTrue(IronFurnacesGate.verdict(Optional.of("4.1.6")) == IronFurnacesGate.Verdict.OTHER_VERSION && IronFurnacesGate.verdict(Optional.of("4.1.9")) == IronFurnacesGate.Verdict.OTHER_VERSION, "other builds are skipped");
        h.assertTrue(IronFurnacesGate.verdict(Optional.empty()) == IronFurnacesGate.Verdict.ABSENT, "absent is skipped");
        h.assertTrue(IronFurnacesGate.verdict(Optional.of(IronFurnacesGate.VERSION), false) == IronFurnacesGate.Verdict.CHANGED_TARGET, "the build without the method is skipped");
        h.assertTrue(IronFurnacesGate.gates("IronFurnacesListLoadMixin") && !IronFurnacesGate.gates("DeepResonancePlaceEventMixin"), "the gate decides for its own mixin only");
        h.assertTrue(Fixes.known().stream().anyMatch(f -> f.id().equals(FurnaceListRepair.ID) && f.targetMod().equals(IronFurnacesGate.TARGET)), "the registry knows F-39 as an Iron Furnaces fix");
        if (!ModList.get().isLoaded(IronFurnacesGate.MOD_ID)) {
            Map<String, String> skipped = Fixes.skipped();
            h.assertTrue(skipped.containsKey(FurnaceListRepair.ID) && skipped.get(FurnaceListRepair.ID).equals(IronFurnacesGate.Verdict.ABSENT.reason()),
                    "without Iron Furnaces the plugin skipped F-39 as absent: " + skipped);
        }
        h.succeed();
    }

    @GameTest(template = ModFixesGameTests.EMPTY)
    public void iron_furnaces_real_provider_loads_a_list_once_and_a_players_capability_is_named_and_the_switch_restores_its_own(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded(IronFurnacesGate.MOD_ID)) {
            Fixes.LOGGER.info("BSP-Mod Fixes test: Iron Furnaces is not loaded, no provider is made (add -PwithIf)");
            h.succeed();
            return;
        }
        h.assertTrue(BSPModFixes.version(IronFurnacesGate.MOD_ID).equals(IronFurnacesGate.VERSION), "Iron Furnaces " + IronFurnacesGate.VERSION + ", found " + BSPModFixes.version(IronFurnacesGate.MOD_ID));
        // The provider class loads at the first player (its capability is attached then), so by the first test it is in place.
        Object provider = IronFurnacesTestBridge.provider();
        boolean fixOn = Fixes.active().stream().anyMatch(f -> f.id().equals(FurnaceListRepair.ID));
        h.assertTrue(fixOn || Fixes.skipped().getOrDefault(FurnaceListRepair.ID, "").equals(MixinFixPlugin.SKIP_REASON),
                "F-39 is applied or switched off for this run: " + Fixes.active() + " " + Fixes.skipped());
        List<BlockPos> list = IronFurnacesTestBridge.list(provider);
        long loadsBefore = FurnaceListRepair.loads();
        // 1. The real load, twice from a duplicated tag: what vanilla's load and InventorySync's restore do to one player.
        CompoundTag saved = savedList(1_000, A, B);
        IronFurnacesTestBridge.deserialize(provider, saved);
        IronFurnacesTestBridge.deserialize(provider, saved);
        CompoundTag written = IronFurnacesTestBridge.serialize(provider);
        if (fixOn) {
            h.assertTrue(list.equals(List.of(A, B)), "with F-39 two loads leave the two furnaces: " + list.size());
            h.assertTrue(written.getInt("count") == 2 && written.getCompound("furnaces").getAllKeys().size() == 2 && written.getCompound("furnaces").contains("furnace1"), "the save is count 2, furnace0 and furnace1");
            h.assertTrue(FurnaceListRepair.loads() == loadsBefore + 2, "two loads went through the fix");
        } else {
            h.assertTrue(list.size() == 2_000, "as shipped two loads of 1,000 entries append to 2,000: " + list.size());
            h.assertTrue(written.getInt("count") == 2_000, "and the save carries all 2,000");
            h.assertTrue(FurnaceListRepair.loads() == loadsBefore, "nothing went through the fix");
        }
        // 2. A player's attached capability, loaded through Forge's deserializeCaps (vanilla's and InventorySync's path), then named.
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        List<BlockPos> playerList = IronFurnacesTestBridge.playerList(player);
        CompoundTag caps = new CompoundTag();
        caps.put("ironfurnaces:furnaces_list", savedList(500, A, B));
        Method deserializeCaps = findDeserializeCaps(player.getClass());
        deserializeCaps.invoke(player, caps);
        deserializeCaps.invoke(player, caps);
        if (fixOn) {
            h.assertTrue(playerList.equals(List.of(A, B)), "the player's list holds the two: " + playerList.size());
            h.assertTrue(FurnaceListRepair.pending() >= 2, "both loads wait for the player's name");
            List<String> lines = FurnaceListRepair.resolve(l -> IronFurnacesTestBridge.nameAmong(List.of(player), l), 0);
            String expected = "removed 498 duplicate Iron Furnaces list entries for " + player.getGameProfile().getName() + " (2 kept)";
            h.assertTrue(lines.stream().filter(line -> line.contains(expected)).count() == 2, "both loads named by the player's capability: " + lines);
        } else {
            h.assertTrue(playerList.size() == 1_000, "as shipped the player's list doubles: " + playerList.size());
        }
        // 3. The switch off: Iron Furnaces' own append, even with the mixin applied.
        if (fixOn) {
            FixesConfig.IF_REPAIR_FURNACE_LIST.set(false);
            try {
                h.assertTrue(!FurnaceListRepair.enabled(), "the switch reads off");
                Object shipped = IronFurnacesTestBridge.provider();
                IronFurnacesTestBridge.deserialize(shipped, savedList(10, A, B));
                IronFurnacesTestBridge.deserialize(shipped, savedList(10, A, B));
                h.assertTrue(IronFurnacesTestBridge.list(shipped).size() == 20, "repairFurnaceList = false hands the load to Iron Furnaces' own loop, which appends: " + IronFurnacesTestBridge.list(shipped).size());
            } finally {
                FixesConfig.IF_REPAIR_FURNACE_LIST.set(true);
            }
            Object again = IronFurnacesTestBridge.provider();
            IronFurnacesTestBridge.deserialize(again, savedList(10, A, B));
            h.assertTrue(IronFurnacesTestBridge.list(again).equals(List.of(A, B)), "back on, a load gives the two");
        }
        FurnaceListRepair.resolve(l -> null, Long.MAX_VALUE); // the free providers' loads have no player: drained here, not left for the other test
        h.succeed();
    }

    /** Forge's {@code CapabilityProvider.deserializeCaps} is protected: reached as BSP-InventorySync reaches it. */
    private static Method findDeserializeCaps(Class<?> c) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod("deserializeCaps", CompoundTag.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // up the hierarchy
            }
        }
        throw new NoSuchMethodException("deserializeCaps");
    }
}
