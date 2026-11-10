package com.mrgregles.bsp_mod_fixes;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config, {@code bsp_mod_fixes-server.toml} in the world's serverconfig folder. Documented in docs/CONFIG.md. */
public final class FixesConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SUIT_CLEARS_FIRE;
    public static final ForgeConfigSpec.BooleanValue KEEP_FLYING_SPEED;
    public static final ForgeConfigSpec.BooleanValue FIX_DRAGON_FLIGHT;
    public static final ForgeConfigSpec.BooleanValue CM_SKIP_SAVE_ON_LOAD;
    public static final ForgeConfigSpec.BooleanValue CM_ROOMS_FOLLOW_MACHINES;
    public static final ForgeConfigSpec.IntValue CM_ROOM_RELEASE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue CM_HARDEN_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue CM_PREVIEWS;
    public static final ForgeConfigSpec.IntValue CM_PREVIEW_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue CM_PREVIEW_HIDES_CONTENTS;
    public static final ForgeConfigSpec.IntValue CM_PREVIEW_MAX_KILOBYTES;

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
        b.push("compactMachines");
        CM_SKIP_SAVE_ON_LOAD = b.comment(
                "Compact Machines saves and flushes every loaded chunk of its room dimension to disk, on the server thread, each",
                "time a machine block loads or is placed and each time a Chunkloader upgrade is applied or removed: the work of",
                "/save-all flush, once per machine. true skips that save (rooms are still force-loaded and released exactly as",
                "before, and are saved with the world's normal saves); false leaves Compact Machines' behaviour as it is.",
                "Only read while exactly Compact Machines 6.0.3 is loaded; any other build skips the fix whatever this says.")
                .define("skipSaveOnLoad", true);
        CM_ROOMS_FOLLOW_MACHINES = b.comment(
                "Compact Machines force-loads the room of every machine that has a tunnel, saves that with the world, and never",
                "releases it except when a machine bound to it loads again with no tunnel left: not when the machine's own chunk",
                "unloads, not when its owner leaves, not when the machine is broken.",
                "true keeps a room loaded only while a machine block bound to it is itself loaded (by a player nearby, a chunk",
                "loader, a BSP-Core Anchor), or while a player is inside it; the room is released a few seconds after the last",
                "such machine unloads or is broken, and the forced flags saved in the room dimension are cleared at each server",
                "start. The Chunkloader upgrade follows the same rule. false leaves Compact Machines' behaviour as it is; rooms",
                "this fix holds are released within a second and Compact Machines forces rooms again as machines load.",
                "Only read while exactly Compact Machines 6.0.3 is loaded.")
                .define("roomsFollowMachines", true);
        CM_ROOM_RELEASE_SECONDS = b.comment(
                "How long a room stays loaded after the last loaded machine bound to it has gone, in seconds, so a machine at the",
                "edge of a player's view does not load and unload its room over and over.")
                .defineInRange("roomReleaseSeconds", 5, 0, 600);
        CM_HARDEN_PREVIEW = b.comment(
                "A right click on a Compact Machine with an empty hand makes the server capture the whole room, every container's",
                "contents included, and send it to that player, for any player, any machine, as often as they click.",
                "true answers such a click by the four settings below; false leaves Compact Machines' behaviour as it is and the",
                "four settings are not read. Only read while exactly Compact Machines 6.0.3 is loaded.")
                .define("hardenPreview", true);
        CM_PREVIEWS = b.comment(
                "Whether the room preview exists at all. false: a right click with an empty hand captures nothing and the player",
                "is told previews are off; rooms are entered with the Personal Shrinking Device as before.")
                .define("previews", true);
        CM_PREVIEW_INTERVAL_SECONDS = b.comment(
                "The least time between two previews for one player, in seconds; a click sooner than that does nothing.")
                .defineInRange("previewIntervalSeconds", 1, 0, 60);
        CM_PREVIEW_HIDES_CONTENTS = b.comment(
                "true sends each block entity of the room as a player standing in it would receive it (its update data: a sign's",
                "text, a machine's visible state), not its full save, so the contents of chests, drives and machines stay on the",
                "server. false sends the full save, as Compact Machines does.")
                .define("previewHidesContents", true);
        CM_PREVIEW_MAX_KILOBYTES = b.comment(
                "The largest preview that is sent, in kilobytes of room data. A larger one is not sent and the player is told the",
                "room is too large to preview. The game cannot carry more than 1,048 kilobytes in one such packet.")
                .defineInRange("previewMaxKilobytes", 1000, 10, 1040);
        b.pop();
        SPEC = b.build();
    }

    private FixesConfig() {}
}
