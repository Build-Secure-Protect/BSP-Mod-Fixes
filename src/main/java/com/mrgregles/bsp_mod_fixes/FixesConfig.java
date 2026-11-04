package com.mrgregles.bsp_mod_fixes;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config, {@code bsp_mod_fixes-server.toml} in the world's serverconfig folder. Documented in docs/CONFIG.md. */
public final class FixesConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SUIT_CLEARS_FIRE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("fire");
        SUIT_CLEARS_FIRE = b.comment(
                "Numina puts out a burning player who wears or holds a modular item while the suit's heat is under 95% of its",
                "maximum, which makes suit wearers all but immune to fire aspect and flame.",
                "true keeps that Powersuits behaviour; false lets the fire burn as on anyone else.")
                .define("suitClearsFire", true);
        b.pop();
        SPEC = b.build();
    }

    private FixesConfig() {}
}
