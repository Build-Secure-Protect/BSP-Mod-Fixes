package com.mrgregles.bsp_mod_fixes.fix;

/**
 * F-9 (Numina 2.3.13 and Modular Powersuits 2.3.13): Numina ships its English as {@code us_en.json}, a file name Minecraft never
 * loads, so every Numina name shows as its raw key; Powersuits lacks the keybinding names of its modules and a few GUI keys. This
 * mod ships {@code assets/numina/lang/en_us.json} (all of Numina's plus the keys its code uses) and
 * {@code assets/powersuits/lang/en_us.json} (only what Powersuits lacks). Forge merges lang files per key across mods, so nothing
 * of theirs is overridden. Both files come from {@code tools/gen_lang.py}; {@code tools/check_lang.py} runs in the Gradle check.
 */
public final class Lang {
    public static final String ID = "F-9";
    public static final String WHAT = "English names for Numina (its lang file is misnamed) and Powersuits' missing keybinding and GUI keys";
    public static final String TARGET = "Numina 2.3.13, Modular Powersuits 2.3.13";

    public static final String NUMINA_FILE = "/assets/numina/lang/en_us.json";
    public static final String POWERSUITS_FILE = "/assets/powersuits/lang/en_us.json";
    /** The module categories the Tinker Table names; all must be in the Numina file. */
    public static final String[] CATEGORIES = {"none", "debug", "armor", "energystorage", "energygeneration", "tool", "weapon",
            "movement", "cosmetic", "vision", "environment", "special", "miningenhancement", "miningenchantment"};

    private Lang() {}
}
