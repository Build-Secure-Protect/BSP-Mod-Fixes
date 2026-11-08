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
