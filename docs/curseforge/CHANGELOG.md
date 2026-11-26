# BSP-Mod Fixes 0.19.1

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios, and Integrated Dynamics 1.31.5 with Cyclops Core and Common Capabilities (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.75.0.243 or 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix (JEI 15.75 has that fix itself).

## Changed

**JEI 15.75 does the JEI start fix itself, so F-19 now steps aside there.** JEI 15.75.0.243's start observer waits for the login's tag packet before starting, the very thing F-19 (0.9.0) did for 15.74. On 15.75 the fix is skipped with one log line saying so; on 15.74 it applies as before. 0.19.0 had applied it on 15.75 as well, where it did nothing useful. Nothing else changes; the EMI fixes are unaffected by the JEI update.

# BSP-Mod Fixes 0.19.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios, and Integrated Dynamics 1.31.5 with Cyclops Core and Common Capabilities (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.75.0.243 or 15.74.0.234 for the EMI fixes; any JEI 15.74 or 15.75 build for the JEI fix.

## Changed

**Ready for JEI 15.75 and Cyclops Core 1.22.5.** The pack moved to JEI 15.75.0.243 and Cyclops Core 1.22.5. The JEI start fix (F-19) was made to apply to 15.75 builds as well as 15.74 (0.19.1 reverses that: JEI 15.75 has the fix itself, see above), and every JEI interface the EMI fixes implement is the same, so those need nothing. The Integrated Dynamics fixes (F-49, F-50) read nothing of Cyclops Core that changed. No fix behaves differently; a 0.18.0 client on JEI 15.75 would only have lost F-19.

# BSP-Mod Fixes 0.18.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios, and Integrated Dynamics 1.31.5 with Cyclops Core and Common Capabilities (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**An Integrated Dynamics network with a stale part record no longer crashes the server.** F-49, for Integrated Dynamics 1.31.5: when a network shut itself down because its last cable was gone, or an element was removed, it asked every part for its state with no guard; if a part had left its cable while the network still listed it, the whole server went down with "Exception in server tick loop". Such a record is now skipped and dropped, and the server log names the position and side once, so the cable can be looked at.

**A part removed from a cable that has just loaded no longer leaves a stale record behind.** F-50, for Integrated Dynamics 1.31.5: in the moments after a chunk loads, a cable has not yet re-attached to its network, and a part removed then told no network it was going. The saved network that still lists it is now told, so nothing is left for F-49 to meet. Both fixes apply only beside exactly Integrated Dynamics 1.31.5 with Cyclops Core and Common Capabilities, and `skipDanglingParts` in `bsp_mod_fixes-server.toml` turns them off.

# BSP-Mod Fixes 0.17.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, and Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**The server no longer crashes with ConcurrentModificationException when a chunk unloads.** F-48, for Minecraft 1.20.1 itself: when a chunk unloads, the game tells each of its block entities that the chunk is going and then that they are removed, walking its own list of them as it goes. If any mod's block entity reacts by looking a neighbour up in that chunk, or by changing a block there, the list changes under the game's feet and it crashes at the end of the pass with "Exception ticking world", and the chunk's block entities are left half-removed. The walk now runs over a copy, so the unload always finishes; the first time a block entity of some type does this the server log names it, its position and its dimension, so the mod at fault can be found; and any block entity created during the unload is removed with the chunk. `unloadOverCopies` in `bsp_mod_fixes-server.toml` turns it off.

# BSP-Mod Fixes 0.16.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, and Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**The Rail Gun no longer shoots once and goes dead until you change weapon.** F-47, for Modular Powersuits 2.3.13: the Rail Gun's 15-tick cooldown was counted down only while its module was switched on, but the trigger never checked that, so a Rail Gun whose module was off (by a toggle key, the Tinker Table's toggle, or possibly an unlucky default) fired once and then nothing counted its cooldown down; changing weapon happened to reset it, so it fired once more. The cooldown now runs by the game clock inside the shot itself and ends 15 ticks after each shot whether the module is on or off. A fist from before this fix fires at once. Changing weapon still resets the cooldown, as before.

# BSP-Mod Fixes 0.15.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, and Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**Silk Touch no longer drains 50,000 FE a tick, and the server's setting for it counts.** F-45, for Numina 2.3.13 and Modular Powersuits 2.3.13: Powersuits' Fortune, Silk Touch and Aqua Affinity modules asked for their settings under a config section named `Mining_Enchantment`, while Powersuits' own config file writes them under `Mining_Enhancement`, beside the other mining modules, and never writes a `Mining_Enchantment` section (one added by hand is removed on load). So the file's values were never read: Silk Touch cost the code's 50,000 FE every tick it was switched on, however the server set it, and the three modules could not be disallowed. The modules now read the section the file has, so `base_silkTouchEnCon` (2,500 by default) and `isAllowed` apply. One default differs: Aqua Affinity's `base_energyCon` is 0.0 in Powersuits' file where its code charged 50,000 FE a tick, so Aqua Affinity is free until the server sets that key.

**The Rail Gun no longer plays the charging animation and glitches.** F-46, for Modular Powersuits 2.3.13: the Rail Gun fires the moment it is clicked, but Powersuits also started a 25-tick "item use" on every click, shot or not, and the Power Fist shows the bow pose for any use, so each click drew the Plasma Cannon's charge look for 25 ticks and a click during the Rail Gun's cooldown restarted it with nothing fired. The shot, its cooldown, sound and recoil are unchanged; the fist is simply never "in use" for the Rail Gun. With the use gone, a click no longer slows the player for a second and a half, and a held button fires at the cooldown's rate (every 16 ticks) instead of once per animation.

# BSP-Mod Fixes 0.14.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, and Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**A force field projector no longer errors on a background thread when a player arrives.** F-43, for MFFS 5.1.29: the projector worked out which field blocks to build next on a background thread while reading things the server was changing at that moment, and with a Fusion module a player arriving at the base made it throw an error and skip that round. The work now runs on the server thread between ticks, as the building itself always did. `selectOnServerThread` under `[mffs]` in `bsp_mod_fixes-server.toml` turns it off.

**A force field no longer loads and drops the chunks around it over and over.** F-44, for MFFS 5.1.29: a projector whose field reached beyond the loaded area pulled every chunk it touched into memory, dropped it a tick later, and did it again at least once a minute for as long as it ran, each time creating and killing that chunk's field blocks; on a busy server that was a million dead block entities an hour, memory climbing until the server was killed, and lag spikes at every clean-up. The field now stays within the chunks a player or a chunk loader keeps: the projector's selection, its teardown, the Fusion module's removals and the field blocks' own load no longer load chunks, and field blocks in unloaded chunks simply stay as they were saved. `fieldStaysInLoadedChunks` under `[mffs]` in `bsp_mod_fixes-server.toml` turns it off.

# BSP-Mod Fixes 0.13.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8, Voltaic 1.20.1-1.2.2 with Mekanism and Curios, and Evolved Mekanism 1.3.0-fix3 with Elytra Slot and Curios (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**The HDPE Reinforced Elytra no longer crashes the game.** F-42, for Evolved Mekanism 1.3.0-fix3 beside Elytra Slot: Evolved Mekanism makes fourteen items wearable in Curios slots, thirteen of Mekanism's and its own Portable Hazmat Suit, Mekanism's HDPE Reinforced Elytra among them, and Elytra Slot already makes every elytra wearable in its back slot. Both gave that one item the same Curios capability, which Forge refuses, so the first time anything looked at an HDPE Reinforced Elytra the game crashed: typing in the creative inventory's search, a tooltip in JEI or EMI, or a server handling one in an inventory. Evolved Mekanism now leaves the elytra to Elytra Slot; the other thirteen items are untouched. From the code, the elytra still fits and flies from either mod's slot; the in-game check is on the list.

# BSP-Mod Fixes 0.12.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8 and Voltaic 1.20.1-1.2.2 with Mekanism and Curios (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**No more "Error Rendering" cards in EMI from a machine's screen.** F-41, for EMI 1.1.24, client only: open a machine such as Ender IO's Enchanter, Alloy Smelter, SAG Mill, Slice'N'Splice or Soul Binder, look up its recipes in EMI, and every recipe showed "Error Rendering". EMI's JEI layer was written against an older JEI and lacks three things the pack's JEI asks of it when a machine has a JEI recipe transfer, the "+" button; the same happened in the machine screens of Apotheosis, Botania, Farmer's Delight, Integrated Dynamics, Mekanism, Railcraft and RFTools Control. The three are now supplied, the recipes show, and the "+" transfer works from those screens.

# BSP-Mod Fixes 0.11.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6, Iron Furnaces 4.1.8 and Voltaic 1.20.1-1.2.2 with Mekanism and Curios (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**The Portable Hazmat Suit now protects against Nuclear Science radiation.** F-40, for Voltaic 1.20.1-1.2.2, the library that runs Nuclear Science's radiation: it only ever looked at the four armour slots when it worked out how much of a dose gets through, so Evolved Mekanism's Portable Hazmat Suit, which sits in a Curios slot, protected against Mekanism's radiation and not against Nuclear Science's. The dose that gets past your armour is now also reduced by the shielding of what you wear in your Curios slots, the way Mekanism itself counts it: the suit switched on blocks it all, switched off it does nothing, and your Geiger counter still reads the field. `curioRadiationShielding` under `[voltaic]` in `bsp_mod_fixes-server.toml` turns it off.

# BSP-Mod Fixes 0.10.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3, MFFS (Modular Force Field System) 5.1.29, Deep Resonance 1.20-5.0.6 and Iron Furnaces 4.1.8 (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**Ballistix blast debris no longer trips Deep Resonance.** F-38, for Deep Resonance 1.20-5.0.6: Deep Resonance watches every block placed, to notice shielding blocks (obsidian, dense obsidian, dense glass, lead blocks) going up near a radiation source, and reads the world from whoever placed the block. A block placed by nobody, which is what a Ballistix blast's debris is when the blast was set off by redstone or by another blast, made it throw an error on the server; Neruina then froze the falling block and told every player about an error, and a server without Neruina would have crashed. The handler now reads the world from the event itself when nobody placed the block, so the block is still noticed as shielding. `placeEventsWithoutPlacer` under `[deepResonance]` in `bsp_mod_fixes-server.toml` turns it off.

**A player's Iron Furnaces list no longer grows on every login.** F-39, for Iron Furnaces 4.1.8: the mod remembers on each player where they placed their furnaces, for the Million Furnace, and when that memory loads it adds the saved entries to what it already has instead of replacing it. With the inventory sync restoring a player's data at login on top of the server's own copy, the list doubled every login; one player's file reached two million entries for two furnaces and froze the server for four minutes whenever they joined. The list is now replaced when it loads and duplicates are dropped, with one line in the server log naming the player when anything was dropped, and a warning when a player has more furnaces than `maxFurnacesPerPlayer` (4096; nothing is removed). `repairFurnaceList` under `[ironFurnaces]` in `bsp_mod_fixes-server.toml` turns it off.

# BSP-Mod Fixes 0.9.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3 and MFFS (Modular Force Field System) 5.1.29 (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**Viewing certain recipes with a storage screen open no longer crashes the game.** F-37, for EMI 1.1.24, client only: with a Refined Storage grid, a Mekanism QIO crafting window or a Formulaic Assemblicator open, looking at Mekanism's Laser Tractor Beam, EnderStorage's three recolour recipes or Quark's dirty glass in EMI crashed the game to the desktop. EMI's layer for those screens assumed every shaped recipe has nine ingredient slots, and these have fewer. The layout now uses the slots there are and pads the rest, as EMI does for its own recipes. `clampJeiTransferSlots` in `bsp_mod_fixes-client.toml` turns it off.

# BSP-Mod Fixes 0.8.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9, Compact Machines 6.0.3 and MFFS (Modular Force Field System) 5.1.29 (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**MFFS force fields stop stalling the server and freezing players.** Three fixes for Modular Force Field System 5.1.29, each with its own switch in `bsp_mod_fixes-server.toml` under `[mffs]`.

- F-34: the sponge module drains fluids by sampling. It used to copy every position inside the field every three seconds, shuffle the copy and walk it, loading parts of the world as it went: a field eight chunks across each way is seventeen million positions and about a gigabyte of memory, and the server stalled on every pass. Now it picks a few hundred random spots inside the field in loaded chunks and dries what it finds there, waterlogged blocks included (which it never dried before). Modded fluids were always handled. `fixSponge` turns it off; `spongeMaxInteriorBlocks` can forbid the sponge in fields over a chosen size.
- F-35: a force field over the server's size limit is not projected. MFFS put no bound on a field. The limits are `maxFieldBlocks` (default 100,000 field blocks, about a cube four chunks across each way; 0 for none) and `maxFieldRadius` (default off). A projector asked for a bigger field builds nothing, removes what it had built, and players within fifteen blocks of it read "This force field is too large for this server" once a minute; the server log says so once. **Fields built before the limit go the next time their projector starts up or has a module changed.**
- F-36: a force field block's projector, camouflage and glow travel in the chunk data. Every field block is a block entity, and as each chunk arrived the player's game asked the server for every one of them one by one, then re-drew and re-lit each as its answer came: thousands of round trips per chunk near a large field, and a player near a field of twenty chunks froze. Now the data arrives with the chunk and the game asks for nothing; a block placed in front of a watching player still asks once, as before. `syncFieldBlocksInChunks` turns it off.

The settings are in `docs/CONFIG.md`: five under `[mffs]` in `bsp_mod_fixes-server.toml`.

# BSP-Mod Fixes 0.7.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, each skipped with one log line beside any other build: AdvancedAE 1.3.6-1.20.1, Gobber 2 2.8.9 and Compact Machines 6.0.3 (client and server). Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**Compact Machines stops costing the server and the client what it did.** An audit of Compact Machines 6.0.3, the last build for 1.20.1, found four ways it wastes time and memory. Each has its own switch in the config.

- F-28: a machine block loading no longer makes the server write every loaded room to disk and wait for the disk. It did, once per machine, each time a chunk holding machines loaded.
- F-29: a room runs only while a machine block bound to it is itself loaded (a player nearby, a chunk loader, an anchor), or while a player is inside it. Compact Machines kept every room that had a tunnel loaded for good, from server start, whether or not anyone was online, even after its machine was broken. **Players who relied on a room running with nobody near and nothing keeping its machine loaded will see it stop a few seconds after they leave.**
- F-30 (client): the room preview, the look inside a machine on a right click with an empty hand, gives back its memory when it closes. It kept about 54 MB of working memory and several screen-sized images per look for the rest of the session. This is what the Compact Machines Preview Fixer mod does for 1.21.1.
- F-31: the server sends a player at most one preview a second, without the contents of the room's chests and machines (signs keep their text), refuses a room too large to send, and can have previews turned off. Before, any player could have any room captured, contents and all, on every click.

The settings are in `docs/CONFIG.md`: eight under `[compactMachines]` in `bsp_mod_fixes-server.toml`, one in `bsp_mod_fixes-client.toml`.

# BSP-Mod Fixes 0.6.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional: AdvancedAE 1.3.6-1.20.1 (client and server) for the quantum armor fix, and Gobber 2 2.8.9 (client and server) for the dragon armour fix; with any other build of either, that fix is skipped and one log line says why. Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**Gobber 2's dragon armour flight holds on a server.** Players in the full dragon set found that creative flight made them vibrate and often refused to come on with the double tap. Gobber's armour sends the player's abilities to the other side every tick, for every piece worn, from the client and from the server alike, and the game then overwrites each side's "flying" flag with the other side's stale copy; on a server, where a message takes a moment to cross, the flag echoes on and off for ever. Single player never showed it. Gobber's own tracker has the same reports for this build, which is the last one for 1.20.1.

- F-27: the dragon armour sends the update only when something changed (the grant when the set is completed, the revoke when a piece comes off), and never from the client. The double tap turns flight on and it stays on. `fixDragonFlight` in `bsp_mod_fixes-server.toml` turns the fix off, for the day Gobber fixes it itself; a change there reaches each client at its next login.

The new server setting is in `docs/CONFIG.md`.

# BSP-Mod Fixes 0.5.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional: AdvancedAE 1.3.6-1.20.1 (client and server) for the quantum armor fix; with any other AdvancedAE it is skipped and one log line says why. Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the JEI fix.

## Added

**Modular Powersuits no longer leaves a flying speed on the player.** A player who had used Powersuits' Glider, Sprint Assist or Jump Assist could later fly at absurd speed in anyone's creative flight (AdvancedAE's Flight card, for one): those modules change the player's creative flying speed every tick they run and never put it back, and the game saves that speed with the player. Powersuits' own flight never uses it.

- F-24: the three modules leave the flying speed as they found it, and a survival or adventure player's stored speed is put back to vanilla's once a second, with one line in the server log per reset, so every player already affected heals on their next join. Creative and spectator players are left alone. `keepFlyingSpeed` in `bsp_mod_fixes-server.toml` turns the reset off.

**The quantum armor shows what it absorbs.** AdvancedAE's quantum armor takes 30 % of the damage left after the armor out of the four pieces' AE charge, a quarter each, and never said so; against the armor's own 80 % and a buffer of hundreds of millions of AE nobody could see it work.

- F-25: a suit readout. While any quantum piece is worn, a plate slides in at the screen's left edge above the hotbar with the suit's charge as a bar and figure; for three seconds after a hit a damage row shows the hit, the share the suit took and the AE it paid. A tab on the plate's edge collapses it to the tab alone, by the key binding "Toggle quantum suit readout" (unbound by default, under BSP-Mod Fixes in Controls) or a click on the tab with the inventory open; collapsed stays collapsed until opened again (`bsp_mod_fixes-client.toml`). The absorption itself is unchanged.
- F-26: Powersuits' HUD energy meter shows only while a Powersuits piece is worn or a Power Fist held. It used to appear for anyone wearing energy-storing armor from any mod, the quantum set included.

The new server setting is in `docs/CONFIG.md`. An affected player can also be put right at once with `/catnip util flySpeed reset <name>` (Create's bundled Catnip, operators).

# BSP-Mod Fixes 0.4.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes; any JEI 15.74 build for the new fix.

## Added

**Mekanism's recipes show on a server as they do in single player.** On a dedicated server, JEI showed no Metallurgic Infuser recipe for Infused Alloy, nor most of Mekanism's ore processing, and the Electrodynamics family's fluid recipes (sulfates, acids, ethanol, the nuclear fuel chain) were missing too. The cause is in JEI 15.74: it starts the moment the server's recipes arrive, which in Minecraft 1.20.1 is just before the server's tags arrive, so every mod that looks a tag up while handing JEI its recipes saw an empty tag. Mekanism quietly dropped every recipe with a tag input, and Electrodynamics' failed on the way in. Single player was fine because the local server has its tags ready first.

- F-19: JEI starts only after the server's tags have arrived. Two lines in the client log mark the hold and the release; JEI then starts a moment later than it did, with everything in place. Applies to any JEI 15.74 build whose start code is as expected; with another JEI it is skipped and one log line says why. Client only; a dedicated server skips it.

# BSP-Mod Fixes 0.3.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234. Nothing extra for the two new fixes; they matter on clients that switch servers through a Velocity proxy with Forge Client Reset Packet.

## Added

**Server switches no longer drop the connection.** Switching servers through Velocity with Forge Client Reset Packet sometimes ended in "Internal Exception: io.netty.handler.codec.EncoderException: java.io.IOException: Can't serialize unregistered packet" the instant the reset finished. The cause is a race in Minecraft 1.20.1 itself: a packet a client mod sent while the world was unloading was queued for the old protocol, the reset moved the connection to the login protocol, and vanilla then wrote the old packet anyway.

- F-16: a packet queued before a server switch moved the connection to another protocol is dropped instead of closing the connection. One warning line in the client log names the packet and, for a mod channel, the channel, so the mod that sends during a switch can be found.
- F-17: a disconnected screen set from the network thread is opened on the render thread instead. When a switch failed, Client Reset Packet opened the screen from the wrong thread, which crashed clients that carry the Fabric Screen API (Forgified Fabric API, pulled in by Enhanced Block Entities) with "The current screen (DisconnectedScreen) has not been correctly initialised". Those clients now see the disconnected screen.

Both apply only to the client's own connection; on a dedicated server F-16 is inert and F-17 is skipped.

# BSP-Mod Fixes 0.2.0

Minecraft 1.20.1, Forge 47.3.10 or newer, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly. Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234.

## Added

**Tinkers' Construct recipes render in EMI.** EMI's JEI compatibility layer stopped at an older JEI API and Tinkers calls the newer methods, so every Tinkers category in EMI showed "Error rendering" and entity melting and severing were empty. Five fixes, applied only when EMI 1.1.24+1.20.1+forge is loaded and only on the client; without EMI, or with another EMI version, they are skipped and one log line says why.

- F-11: a null entry in a JEI ingredient list (which JEI's API allows) becomes an empty EMI stack instead of dropping the whole recipe. Tinkers passes null for every entity without a spawn egg, so entity melting and severing show their recipes.
- F-12: EMI's JEI tooltip builder gains `getLines()` (a live list, so a plugin's edits change what EMI draws), `clear()`, `clearIngredient()` and `addKeyUsageComponent()`.
- F-13: a fluid's amount line is offered to the plugin under JEI's key, so Tinkers' "1 Ingot" and nugget lines replace EMI's "1,000 mB" line instead of sitting beside it.
- F-14: EMI's recipe extras builder gains the JEI 15.56 widget methods (drawables, arrows, plus signs, flames, tooltip areas, buttons) with their tooltips, so Tinkers' arrows, icons and hover areas draw and animate.
- F-15: JEI text widgets are drawn (EMI returned them without drawing them), so Tinkers' titles, temperatures and times show.

## Changed

**Log flooding closed.** The refusal and clamp warnings of F-1 and F-2 are throttled to one per player every ten seconds, with a count of the lines held back, so a hostile client cannot fill the server log.

**Kinetic Generator in single player.** Its position memory is touched by the server thread only. In single player the client's player shares its entity id with the server's, so a jump on the client could cost a tick's credit on the server.

**Forge range.** The mod now asks for Forge 47.3.10 or newer, the first version that hands the loading context to a mod constructor; older 47.x would have refused to load it anyway.

**Startup report.** On a dedicated server the EMI fixes are reported as skipped ("client-side only") instead of waiting for classes that never load there.

# BSP-Mod Fixes 0.1.0

First release. Minecraft 1.20.1, Forge 47.4, Java 17. Needed on the client and the server. Requires Numina 2.3.13 and Modular Powersuits 2.3.13 exactly: if either is updated the game refuses to start until this mod is updated too.

## What it does

Ten fixes for Modular Powersuits and Numina, patched in place with mixins and event handlers; nothing of theirs is shipped or changed. Each fix is aimed at the exact line that was wrong, and each has a headless test. The server log lists the fixes once loading is complete.

## Fixed

**The suit ticked twice a tick (F-3).** Numina's tick handler never looked at the tick phase, and Forge fires the event at the start and the end of every tick, so every module ran twice: double jetpack thrust and drain, double Night Vision cost, double cooling. The suit ticks once now, at the start. Thrust and drain halve with it; the server settings in the description re-tune the jetpack, the jet boots and the kinetic generator.

**Powersuits' player update ran on every living event (F-4).** It subscribed to the base event, so it ran for every hurt, jump, fall, death and drop of every creature on the server, rewriting the player's fall distance each time. It runs on the player's tick only.

**Night Vision charged every tick (F-5).** It refreshed the effect whenever under 500 ticks remained but applied a 300-tick effect, so every tick qualified and cost 100 FE (4,000 FE a second with the double tick). It refreshes under 220 ticks with a 400-tick effect: one charge about every 9 seconds, and no flicker.

**The Kinetic Generator paid almost at random (F-6).** It sampled the walked distance one tick in twenty, only when the player happened to be on the ground at that tick, and its "not generating" branch fell through and paid while flying. It pays its rate per block walked, every tick, on the server, and nothing while airborne, flying, riding, or for a teleport.

**The Shock Absorber was all or nothing (F-7).** A battery that could not pay for the whole fall absorbed none of it, which is why it failed after jetpacking, and its multiplier ran to ten times the fall. The slider sets the share of the fall absorbed (0 to 100 %), the battery pays its cost per block, and a short battery absorbs the part it can pay for. Falls of three blocks or less are left alone as before. The old landing sound is not played.

**The Energy Shield was plain armor that never used energy (F-8).** Powersuits added the shield's points to the armor attribute, its energy drain was dead code (the armor cannot be depleted, so Forge never called it), and its tick switched the module off for good whenever energy dipped under one hit's cost. The shield now absorbs part of each hit for energy: 4 % per point as vanilla armor does, 80 % at most, at the module's FE per point absorbed; with less energy it absorbs what it can pay for; damage that bypasses armor is untouched; the armor bar no longer rises; knockback resistance stays; the module stays on.

**Raw translation keys everywhere (F-9).** Numina ships its English as `us_en.json`, which Minecraft never loads, so every Numina name showed as its key; Powersuits lacked a keybinding name for every module and a few screen titles. Two small language files in this jar fill the gaps: all of Numina's plus the keys its code uses, and only the keys Powersuits lacks.

## Security

**Free modules through the creative-install packet (F-1).** The Tinker Table's creative-install packet put a fully charged module into a worn item and Powersuits' server handler never checked the game mode, so any modified client could install any module for free in survival. It is refused unless the sender is in creative mode, and the log names the player.

**Unchecked slider values (F-2).** A module tweak packet carries a raw number that Numina stored unchecked, so a modified client could send 1000 for a thousand times the thrust or a negative energy use. Values are clamped to the slider's own 0..1 before they are stored (NaN and infinity become 0), and the log names the player.

## Added

**Fire immunity as a setting (F-10).** Numina puts out a burning player who wears or holds a modular item while the suit's heat is under 95 % of its maximum. The new `bsp_mod_fixes-server.toml` has `suitClearsFire`: `true` (the default) keeps that, `false` lets the fire burn as on anyone else.
