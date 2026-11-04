package com.mrgregles.bsp_mod_fixes.gametest;

import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Forge GameTests: checks that run on a headless server with {@code ./gradlew runGameTestServer}. Every test builds what it
 * needs inside the empty template {@code bsp_mod_fixes:empty} (24 x 10 x 24 of air, from tools/gen_gametest_structures.py).
 * The harness itself: the two patched mods are loaded at the pinned versions and a mock player can be made.
 */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class ModFixesGameTests {
    static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public void numina_and_powersuits_are_loaded_at_the_pinned_versions_and_a_mock_player_can_be_made(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("numina"), "Numina is loaded");
        h.assertTrue(ModList.get().isLoaded("powersuits"), "Modular Powersuits is loaded");
        h.assertTrue(BSPModFixes.version("numina").equals("2.3.13"), "Numina 2.3.13, found " + BSPModFixes.version("numina"));
        h.assertTrue(BSPModFixes.version("powersuits").equals("2.3.13"), "Modular Powersuits 2.3.13, found " + BSPModFixes.version("powersuits"));
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        h.assertTrue(player.getGameProfile().getName().startsWith("bsp_fixes_test_"), "a mock player with a test name");
        h.assertFalse(player.isCreative(), "the mock player is in survival");
        h.succeed();
    }
}
