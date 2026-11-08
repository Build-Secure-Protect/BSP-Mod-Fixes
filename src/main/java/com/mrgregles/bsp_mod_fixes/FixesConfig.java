package com.mrgregles.bsp_mod_fixes;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config, {@code bsp_mod_fixes-server.toml} in the world's serverconfig folder. Documented in docs/CONFIG.md. */
public final class FixesConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SUIT_CLEARS_FIRE;
    public static final ForgeConfigSpec.BooleanValue KEEP_FLYING_SPEED;
    public static final ForgeConfigSpec.BooleanValue FIX_DRAGON_FLIGHT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("fire");
        SUIT_CLEARS_FIRE = b.comment(
                "Numina puts out a burning player who wears or holds a modular item while the suit's heat is under 95% of its",
                "maximum, which makes suit wearers all but immune to fire aspect and flame.",
                "true keeps that Powersuits behaviour; false lets the fire burn as on anyone else.")
                .define("suitClearsFire", true);
        b.pop();
        b.push("flight");
        KEEP_FLYING_SPEED = b.comment(
                "Modular Powersuits' Glider, Sprint Assist and Jump Assist modules changed the player's creative flying speed and",
                "never put it back; vanilla saves that speed with the player, and creative flight (AdvancedAE's Flight card) then",
                "uses it. The modules no longer change it. true also puts a survival or adventure player's stored speed back to",
                "vanilla's 0.05 once a second when it is anything else, with one log line (creative and spectator players are left",
                "alone); false leaves every stored speed as it is.")
                .define("keepFlyingSpeed", true);
        b.pop();
        b.push("gobber");
        FIX_DRAGON_FLIGHT = b.comment(
                "Gobber 2's dragon armour sends the player's abilities to the other side every tick, for every piece worn, on the",
                "client and on the server; each side then overwrites its flying flag with the other's stale copy, so on a server",
                "creative flight flickers on and off and the double tap will not hold. true sends the update only when the",
                "flags changed, and never from the client; false leaves Gobber's behaviour as it is (for when Gobber fixes it",
                "itself). Only read while exactly Gobber 2.8.9 is loaded; any other Gobber skips the fix whatever this says.",
                "A change on a running server reaches each client at its next login (Forge sends this file to clients at login only).")
                .define("fixDragonFlight", true);
        b.pop();
        SPEC = b.build();
    }

    private FixesConfig() {}
}
