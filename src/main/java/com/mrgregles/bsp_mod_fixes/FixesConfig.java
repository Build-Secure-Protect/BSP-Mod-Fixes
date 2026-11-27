package com.mrgregles.bsp_mod_fixes;

import com.mrgregles.bsp_mod_fixes.fix.chunks.ChunkWatch;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Collections;
import java.util.List;

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
    public static final ForgeConfigSpec.BooleanValue MFFS_FIX_SPONGE;
    public static final ForgeConfigSpec.IntValue MFFS_SPONGE_MAX_INTERIOR_BLOCKS;
    public static final ForgeConfigSpec.IntValue MFFS_MAX_FIELD_BLOCKS;
    public static final ForgeConfigSpec.IntValue MFFS_MAX_FIELD_RADIUS;
    public static final ForgeConfigSpec.BooleanValue MFFS_SYNC_FIELD_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue MFFS_SELECT_ON_SERVER_THREAD;
    public static final ForgeConfigSpec.BooleanValue MFFS_FIELD_STAYS_IN_LOADED_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue CHUNKS_UNLOAD_OVER_COPIES;
    public static final ForgeConfigSpec.BooleanValue ID_SKIP_DANGLING_PARTS;
    public static final ForgeConfigSpec.BooleanValue DIAG_OFF_THREAD_TRACER;
    public static final ForgeConfigSpec.BooleanValue DIAG_CHUNK_WATCH_TRACER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIAG_CHUNK_WATCH_AREAS;
    public static final ForgeConfigSpec.BooleanValue DR_PLACE_EVENTS_WITHOUT_PLACER;
    public static final ForgeConfigSpec.BooleanValue IF_REPAIR_FURNACE_LIST;
    public static final ForgeConfigSpec.IntValue IF_MAX_FURNACES_PER_PLAYER;
    public static final ForgeConfigSpec.BooleanValue VOLTAIC_CURIO_RADIATION_SHIELDING;

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
        b.push("mffs");
        MFFS_FIX_SPONGE = b.comment(
                "MFFS's sponge module copies every position inside the force field, shuffles the copy on the server thread and walks",
                "it every three seconds, loading chunks as it goes: a field of eight chunks' radius is seventeen million positions and",
                "about a gigabyte of memory, and the server stalls on every pass. true has the sponge draw a few hundred random",
                "positions inside the field in loaded chunks instead and drain the fluid it finds there, source and flowing alike, and",
                "empty waterlogged blocks; false leaves MFFS's behaviour as it is. Only read while exactly MFFS 5.1.29 is loaded; any",
                "other MFFS skips the fix whatever this says.")
                .define("fixSponge", true);
        MFFS_SPONGE_MAX_INTERIOR_BLOCKS = b.comment(
                "The largest field interior, in blocks, the sponge module works in while fixSponge is on: a sponge in a larger field",
                "drains nothing and the server logs one line a while. 0 means no limit. A cube of radius r has (2r+1)^3 interior",
                "blocks: 35,937 at one chunk, 2,146,689 at four chunks, 16,974,593 at eight.")
                .defineInRange("spongeMaxInteriorBlocks", 0, 0, Integer.MAX_VALUE);
        MFFS_MAX_FIELD_BLOCKS = b.comment(
                "The largest force field a projector may project, in field blocks (the positions its shape would fill). MFFS puts",
                "no bound on a field, and every field block is a block entity that each nearby client asks the server about one by",
                "one as its chunk arrives: a cube of eight chunks' radius is about 396,000 of them and froze a player's client. A",
                "projector whose field is over this limit projects nothing, removes what it had built, tells the players within 15",
                "blocks on their action bar once a minute and logs one line. 0 means no limit. A cube of radius r has about 24 r^2",
                "field blocks: 6,000 at one chunk, 98,000 at four chunks, 393,000 at eight. Only read while exactly MFFS 5.1.29 is",
                "loaded; any other MFFS skips the fix whatever this says.")
                .defineInRange("maxFieldBlocks", 100000, 0, Integer.MAX_VALUE);
        MFFS_MAX_FIELD_RADIUS = b.comment(
                "The largest radius a projector may project, in blocks from the field's centre along any axis (the largest Scale",
                "count on any side), judged beside maxFieldBlocks with the same effect. 0 means no limit; 128 is eight chunks.")
                .defineInRange("maxFieldRadius", 0, 0, Integer.MAX_VALUE);
        MFFS_SYNC_FIELD_BLOCKS = b.comment(
                "Every MFFS force field block is a block entity holding its projector's position, its camouflage and its glow, and",
                "MFFS sends none of that in the chunk packet: as each chunk arrives, the client asks the server for every field block",
                "one by one, the server answers each, and the client re-renders and relights each block as its answer comes (about",
                "4,100 round trips for one wall chunk of a field at eight chunks' radius); a client near a large field freezes. true",
                "puts each block's data into the chunk packet itself and has the client use it from there, asking only for a block",
                "placed into a chunk it already has; false leaves MFFS's behaviour as it is. Both sides read it (Forge sends this file",
                "to each client at login). Only read while exactly MFFS 5.1.29 is loaded; any other MFFS skips the fix whatever this says.")
                .define("syncFieldBlocksInChunks", true);
        MFFS_SELECT_ON_SERVER_THREAD = b.comment(
                "MFFS works out which of a force field's positions to build next on a background thread (the common ForkJoinPool),",
                "and that work reads the world and the frequency grid while the server thread changes them: with a Fusion module it",
                "throws ConcurrentModificationException (\"Error selecting force field blocks\") as a player's arrival loads the",
                "base, and its block-state reads load chunks from off the server thread. true runs that selection on the server",
                "thread between ticks instead (the field's geometry is still calculated in the background); false leaves MFFS's",
                "behaviour as it is. Only read while exactly MFFS 5.1.29 is loaded; any other MFFS skips the fix whatever this says.")
                .define("selectOnServerThread", true);
        MFFS_FIELD_STAYS_IN_LOADED_CHUNKS = b.comment(
                "MFFS's selection, its teardown, the Fusion module's removals and a field block's own load each read block states",
                "before asking whether the chunk is loaded, and such a read loads the chunk for one tick: every chunk a field touched",
                "beyond the loaded area was loaded and dropped at least once a minute while the projector ran, each time creating",
                "and killing the chunk's field block entities (a million dead tickers in a hundred minutes around one projector).",
                "true leaves positions in unloaded chunks alone, so no field work loads a chunk: field blocks there stay as saved and",
                "are completed where missing when their chunk is loaded for another reason; false leaves MFFS's behaviour as it is.",
                "Only read while exactly MFFS 5.1.29 is loaded; any other MFFS skips the fix whatever this says.")
                .define("fieldStaysInLoadedChunks", true);
        b.pop();
        b.push("deepResonance");
        DR_PLACE_EVENTS_WITHOUT_PLACER = b.comment(
                "Deep Resonance watches every block placed, to notice a shielding block (obsidian, dense obsidian, dense glass, a",
                "lead block) going up near a radiation source, and reads the level from the entity that placed it. Forge allows",
                "that entity to be missing: a block placed by no entity (Ballistix blast debris landing, when the blast was lit by",
                "redstone or a chain and has no owner) crashes the handler on the server thread; Neruina then suspends the debris",
                "and announces an error to every player, and a server without Neruina would crash. true has the handler read the",
                "level from the event itself when there is no entity, so the block is recorded as before; false dereferences the",
                "missing entity as Deep Resonance's own code did, so the same exception leaves the handler. Read on both sides (Forge sends this file to each client at login). Only read while",
                "exactly Deep Resonance 1.20-5.0.6 is loaded; any other build skips the fix whatever this says.")
                .define("placeEventsWithoutPlacer", true);
        b.pop();
        b.push("ironFurnaces");
        IF_REPAIR_FURNACE_LIST = b.comment(
                "Iron Furnaces keeps on every player the positions of the furnaces they placed (its Million Furnace reads them), and",
                "when that list loads from the player's data it adds the saved entries to whatever the list already holds instead of",
                "replacing it. A second load of the same player doubles the list: BSP-InventorySync's restore at login is one, so",
                "the list doubled on every login, and one player's file reached two million entries for two furnaces and froze the",
                "server for minutes at each login. true has the list replaced when it loads, keeping the first entry of each",
                "position and dropping duplicates, with one log line per load naming the player when anything was dropped; false leaves",
                "Iron Furnaces' behaviour as it is. Read on both sides (Forge sends this file to each client at login). Only read",
                "while exactly Iron Furnaces 4.1.8 is loaded; any other build skips the fix whatever this says.")
                .define("repairFurnaceList", true);
        IF_MAX_FURNACES_PER_PLAYER = b.comment(
                "How many furnaces one player's list may hold, after duplicates are dropped, before the server logs a warning naming",
                "the player. Nothing is removed. 0 means no warning.")
                .defineInRange("maxFurnacesPerPlayer", 4096, 0, Integer.MAX_VALUE);
        b.pop();
        b.push("voltaic");
        VOLTAIC_CURIO_RADIATION_SHIELDING = b.comment(
                "Voltaic runs Nuclear Science's radiation and shields a dose by the four armour slots only (its own hazmat pieces,",
                "and anything carrying Mekanism's radiation-shielding capability), never by a Curios slot. Evolved Mekanism's",
                "Portable Hazmat Suit is a curio with that capability, so it shields against Mekanism's radiation (Mekanism reads",
                "curios itself) and not against Nuclear Science's. true scales the dose left after armour by the equipped curios'",
                "Mekanism shielding, summed as Mekanism sums it: a switched-on suit leaves nothing, a partial curio a part;",
                "armour, armour damage, iodine, enchantments and the recorded dose stay as Voltaic has them. false leaves",
                "Voltaic's behaviour as it is. Only read while exactly Voltaic 1.20.1-1.2.2 is loaded with Mekanism and Curios;",
                "otherwise the fix is skipped whatever this says.")
                .define("curioRadiationShielding", true);
        b.pop();
        b.push("chunks");
        CHUNKS_UNLOAD_OVER_COPIES = b.comment(
                "When a chunk unloads, Minecraft calls every block entity's onChunkUnloaded and then its setRemoved while walking the",
                "chunk's block entity map itself; a hook that changes that map meanwhile (a mod looking a neighbour up in the unloading",
                "chunk, which drops the removed entry and creates a new block entity; a block change) throws",
                "ConcurrentModificationException at the end of the pass, which crashes the server and leaves the chunk's block",
                "entities and tickers half-alive; a mod changing the map from another thread (a reader dropping a removed entry) does",
                "the same to any walk of the map. true walks each pass over a copy, retaking the copy when another thread changed it",
                "(five attempts, then the passes run from the next server task, then they are skipped with a warning), logs once per",
                "block entity type which one changed the map (+N/-M), once per chunk when another thread did, and removes any block",
                "entity added during the unload. false leaves vanilla's walk over the live map.")
                .define("unloadOverCopies", true);
        b.pop();
        b.push("diagnostics");
        DIAG_OFF_THREAD_TRACER = b.comment(
                "A chunk's block entity map is a plain HashMap that the server thread walks when the chunk unloads; a mod that changes",
                "it from another thread (a reader that drops a removed entry, a writer) races that walk, which then throws",
                "ConcurrentModificationException, the crash unloadOverCopies now survives. true logs, once per thread name and",
                "calling class per run, the first time such a thread changes a chunk's block entity map (setBlockEntity,",
                "removeBlockEntity, clearAllBlockEntities, or getBlockEntity dropping a removed entry) with the dimension, chunk,",
                "position and the full stack, so the mod at fault is named, and the F-48 warning then says whether this tracer saw a",
                "writer for that chunk. false logs nothing. Server levels only. A diagnostic: nothing is changed either way.")
                .define("offThreadBlockEntityTracer", true);
        DIAG_CHUNK_WATCH_TRACER = b.comment(
                "true logs, for every chunk inside chunkWatchAreas, each chunk ticket added or removed (its type, level and key, the",
                "class that asked, and once per ticket type and caller a minute a stack of up to twelve frames outside Minecraft,",
                "Forge and Java) and each load and unload, one INFO line each, so what keeps a chunk reloading can be read from the",
                "log. false logs nothing. Nothing is logged while chunkWatchAreas is empty. A diagnostic: nothing is changed.")
                .define("chunkWatchTracer", true);
        DIAG_CHUNK_WATCH_AREAS = b.comment(
                "The chunks the tracer watches, as areas, one string each: \"<dimension> <minChunkX> <minChunkZ> <maxChunkX> <maxChunkZ>\"",
                "in chunk coordinates (a block's x or z divided by 16, rounded down), both ends included. For example, a 5x5 area",
                "around a base in the Other and one Compact Machines room:",
                "chunkWatchAreas = [\"allthemodium:the_other 464 464 468 468\", \"compactmachines:compact_world 128 -64 128 -64\"]",
                "A string not in that form makes Forge refuse the whole list and put the default back, with a line in its log.",
                "Empty by default: nothing is watched.")
                .defineListAllowEmpty("chunkWatchAreas", Collections.<String>emptyList(), ChunkWatch::isValidLine);
        b.pop();
        b.push("integratedDynamics");
        ID_SKIP_DANGLING_PARTS = b.comment(
                "Integrated Dynamics' networks are world data that outlive their chunks and tick from the server tick. When a network",
                "shuts down (its last cable gone) or an element is removed, it asks each part for its state with no guard, and a part",
                "that left its cable while the cable had no network attached (the moments after a chunk loads, before the cable's",
                "ticker re-attaches it) leaves a record the network keeps: the next kill throws PartStateException out of the server",
                "tick and crashes the server. true skips such a record on the kill and removal paths with one WARN per position",
                "(F-49), and when a part is removed from a cable without a network, removes its record from the saved network that",
                "still lists it (F-50). false leaves Integrated Dynamics' behaviour as it is. Only read while exactly Integrated",
                "Dynamics 1.31.5 is loaded with Cyclops Core and Common Capabilities; otherwise the fixes are skipped whatever this says.")
                .define("skipDanglingParts", true);
        b.pop();
        SPEC = b.build();
    }

    private FixesConfig() {}
}
