package com.mrgregles.bsp_mod_fixes.gametest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mrgregles.bsp_mod_fixes.BSPModFixes;
import com.mrgregles.bsp_mod_fixes.fix.Lang;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** MF-6: the shipped lang files (F-9) are on the classpath, parse, and carry the keys the brief named. */
@GameTestHolder(BSPModFixes.MODID)
@PrefixGameTestTemplate(false)
public class LangGameTests {
    @GameTest(template = ModFixesGameTests.EMPTY)
    public void shipped_lang_files_parse_and_carry_the_categories_and_keybinding_names(GameTestHelper h) {
        Map<String, String> numina = read(Lang.NUMINA_FILE);
        Map<String, String> powersuits = read(Lang.POWERSUITS_FILE);
        for (String category : Lang.CATEGORIES) {
            h.assertTrue(numina.containsKey("numina.module.category." + category), "numina.module.category." + category);
        }
        h.assertTrue("Numina".equals(numina.get("key.categories.numina")), "the Numina key category");
        h.assertTrue(numina.containsKey("key..numina.fovfixtoggle"), "the FOV key as the code names it, with two dots");
        h.assertTrue("Servo Motor".equals(numina.get("item.numina.component_servo")), "a Numina item name copied from us_en.json");
        h.assertTrue("Toggle Jetpack".equals(powersuits.get("keybinding.powersuits.jetpack")), "a module toggle key name");
        h.assertTrue(!powersuits.containsKey("keybinding.powersuits.powerfist") && !powersuits.containsKey("keybinding.powersuits.powerarmor_torso"),
                "no toggle key for the non-module items");
        h.assertTrue(!powersuits.containsKey("item.powersuits.jetpack") && !powersuits.containsKey("keybinding.powersuits.toggleModule"),
                "nothing Powersuits already has is overridden");
        h.assertTrue("Tinker Table".equals(powersuits.get("gui.tinkertable")) && "Crafting".equals(powersuits.get("container.crafting")), "the GUI keys");
        h.assertTrue(numina.size() >= 77 + 10 && powersuits.size() >= 51 + 20, "sizes: numina " + numina.size() + ", powersuits " + powersuits.size());
        h.succeed();
    }

    private static Map<String, String> read(String resource) {
        try (Reader in = new InputStreamReader(BSPModFixes.class.getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(in, new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception e) {
            throw new AssertionError("cannot read " + resource + ": " + e, e);
        }
    }
}
