package com.mrgregles.bsp_mod_fixes.fix.powersuits;

import com.mrgregles.bsp_mod_fixes.fix.Fixes;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * F-45 (Numina 2.3.13, for Modular Powersuits 2.3.13's config spec): Powersuits' three Mining Enchantment modules (Fortune,
 * Silk Touch, Aqua Affinity) read their settings where Powersuits writes them. Numina's {@code PowerModule} builds a module's
 * config keys as {@code Modules / <category title> / <module name> / <setting>}, the title from
 * {@code ModuleCategory.getConfigTitle()}: the three modules are built with {@code ModuleCategory.MINING_ENCHANTMENT}, titled
 * "Mining_Enchantment". Powersuits' own {@code ServerConfig} spec, though, writes those three modules under
 * {@code MINING_ENHANCEMENT}'s title "Mining_Enhancement", beside the AOE pick, Vein Miner and the rest, and writes no
 * "Mining_Enchantment" section at all; Forge's spec correction removes one added by hand. So the modules looked their settings
 * up at keys the file never has, {@code ModuleConfig.getBasePropertyDoubleOrDefault} answered the code's default, and the
 * owner's {@code base_silkTouchEnCon = 2500.0} was never read: Silk Touch drained the code's 50,000 FE every tick it was on
 * (the enchantment modules drain per tick by design), Fortune and Aqua Affinity their own defaults, and their
 * {@code isAllowed} keys were ignored the same way. Mod at fault: Modular Powersuits, whose spec and module classes disagree.
 *
 * <p>The fix ({@code NuminaConfigTitleMixin}) redirects the one {@code getConfigTitle()} call in {@code PowerModule}'s
 * constructor through {@link #titleFor}: "Mining_Enchantment" becomes "Mining_Enhancement", every other title is itself, so
 * the three modules' keys land in the section the spec writes and the file holds. Read once at construction, like the keys;
 * no switch (D151): the pack's two mandatory mods are pinned to these exact builds, and nothing else of either mod reads a
 * module's category title (Powersuits' spec uses the enum directly). Pure {@link #configTitle} for the tests.
 */
public final class SilkTouchConfigKey {
    public static final String ID = "F-45";
    public static final String WHAT = "Powersuits' Fortune, Silk Touch and Aqua Affinity modules read their energy cost and isAllowed from the Mining_Enhancement config section, where Powersuits' spec writes them (they asked under Mining_Enchantment, a section the spec never writes, so the server's values were ignored and Silk Touch drained the code's 50,000 FE a tick)";
    public static final String TARGET = "Numina 2.3.13 (for Modular Powersuits 2.3.13's config spec)";

    /** The title {@code ModuleCategory.MINING_ENCHANTMENT} answers, the one the three modules are built with. */
    public static final String MODULE_TITLE = "Mining_Enchantment";
    /** The title Powersuits' {@code ServerConfig} writes those modules under ({@code MINING_ENHANCEMENT}'s). */
    public static final String SPEC_TITLE = "Mining_Enhancement";

    private static final LongAdder REDIRECTED = new LongAdder();
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private SilkTouchConfigKey() {}

    /** Pure: the title a module's config keys are built from. "Mining_Enchantment" becomes "Mining_Enhancement"; any other title is itself. */
    public static String configTitle(String title) {
        return MODULE_TITLE.equals(title) ? SPEC_TITLE : title;
    }

    /** The redirect's handler: {@link #configTitle}, counting each module moved and logging the first at INFO. */
    public static String titleFor(String title) {
        String result = configTitle(title);
        if (!result.equals(title)) {
            REDIRECTED.increment();
            if (LOGGED.compareAndSet(false, true)) {
                Fixes.LOGGER.info("BSP-Mod Fixes F-45: a Mining Enchantment module reads its config under Modules.{} (Powersuits writes it there, the module asked under Modules.{})", result, title);
            }
        }
        return result;
    }

    /** How many modules had their title moved so far (every Fortune, Silk Touch and Aqua Affinity capability built). */
    public static long redirected() {
        return REDIRECTED.sum();
    }
}
