# Testing

Headless checks run with `./gradlew runGameTestServer`. The sections below are the in-game checks, one per task, newest at the bottom. Every fix is checked in single player as well as on a dedicated server, because a mixin listed for one side only would be missed by the headless server.

## 2026-11-04: MF-0, the project

Headless: 1 GameTest passes (`numina_and_powersuits_are_loaded_at_the_pinned_versions_and_a_mock_player_can_be_made`).

In game:
1. Put `bsp-mod-fixes-0.1.0.jar` in a client's `mods` folder beside Numina 2.3.13 and Modular Powersuits 2.3.13. The game starts, the mod list shows "BSP-Mod Fixes 0.1.0", and the log has "BSP-Mod Fixes 0.1.0 loaded (Numina 2.3.13, Modular Powersuits 2.3.13)".
2. Open a single-player world: it loads.
3. Put the jar on a Forge server and on a Mohist server with the same two mods: each starts with the same log line.
4. Remove Numina or Modular Powersuits from a test copy: Forge shows the missing-dependency screen naming BSP-Mod Fixes, and the game does not start.

## 2026-11-04: MF-1, the two security fixes (F-1 creative-install guard, F-2 tweak clamp)

Headless: 4 new GameTests (5 in all) pass. The packets go through the real Powersuits and Numina handlers from a mock player's connection: `creative_install_is_refused_for_a_survival_player`, `creative_install_still_works_for_a_creative_player`, `tweak_clamp_maps_out_of_range_and_broken_values_into_0_to_1`, `tweak_request_of_5_is_stored_as_1_and_a_request_of_a_half_is_kept`. The startup report on the test server lists "2 fixes active" with F-1 and F-2.

In game, with the jar beside Numina 2.3.13 and Modular Powersuits 2.3.13, in single player and on a dedicated server:
1. The log has "BSP-Mod Fixes: 2 fixes active" followed by one line each for F-1 (Modular Powersuits 2.3.13) and F-2 (Numina 2.3.13). It appears once loading is complete, not at common setup.
2. Stock behaviour is unchanged: in creative mode, open a power armor piece's modding screen and use the creative install button on a module; it is installed and charged. In survival the button is not offered (no change from Powersuits) and installing by hand still works.
3. Stock behaviour is unchanged: tweak a module's slider (for example the energy shield's field strength) in survival; the value sticks after closing and reopening the screen, and the summary shows the matching property values.
4. F-1 refusal (needs a crafted packet, so this is the headless test's job): a survival player who sends the creative-install packet gets no module, and the server log shows "F-1: refused a creative-install request from <name> (not in creative mode) for module ... into the chest slot".
5. F-2 clamp (headless as well): a tweak request above 1 is stored as 1 and logged "F-2: clamped tweak 'fieldStrength' on module powersuits:energy_shield from 5.0 to 1.0 (sent by <name>)".

Untested in game at the time of writing: all of the above; nothing has been seen in a client or on Mohist yet.

## 2026-11-04: MF-2, the two tick fixes (F-3 tick phase, F-4 living-event filter)

Headless: 2 new GameTests (7 in all) pass, posting the Forge events by hand to the real handlers: `numina_ticks_the_suit_on_the_start_phase_only` (an END-phase tick leaves a burning player alone, a START-phase tick runs Numina's handler, which clears the fire) and `powersuits_updates_the_player_on_the_living_tick_only` (a visibility event leaves the fall distance alone, a living tick clamps it). The startup report lists 4 fixes.

In game, in single player and on a dedicated server, with a stock `powersuits-server.toml`:
1. The log lists F-3 (Numina 2.3.13) and F-4 (Modular Powersuits 2.3.13) among the active fixes.
2. Jetpack: with the pack's old config, thrust and drain are about half of what they were before the mod. The suit still flies; the energy readout falls at half the old rate.
3. Heat: stand in fire with a charged suit; the heat bar climbs, and once out of the fire the fire goes out at once while heat is low (unchanged behaviour), and cooling is slower than before.
4. Fall damage with a shock absorber and jump assist behaves as before; glider, jetpack and jet-boots sounds start and stop as before on the client.
5. Nothing else changes for mobs: hurting, jumping or killing a mob near a suited player has no effect on the player.

Untested in game at the time of writing: all of the above.

## 2026-11-04: MF-3, Night Vision timing (F-5) and the Kinetic Generator (F-6)

Headless: 2 new GameTests (9 in all) pass by calling the real modules' tick methods on a dressed mock player: `night_vision_charges_once_and_refreshes_under_220_ticks_with_a_400_tick_effect` and `kinetic_generator_pays_per_block_walked_and_nothing_airborne_or_teleporting`. The module classes load on first use, so the startup report marks F-5 and F-6 "applies when its target class loads" and logs the application when a suit with such a module first exists.

In game, in single player and on a dedicated server:
1. Helmet with a battery and Night Vision, module on, at night: night vision stays on without flicker and the energy readout drops by 100 FE about every 9 seconds (watch for a minute: about 600 to 700 FE, not 2,000 a second).
2. Leggings with a battery and the Kinetic Generator, empty hands: walking charges the battery steadily (at the old config about 1,200 FE per block sprinted); standing still, jumping, flying or riding charges nothing; teleporting charges nothing.
3. With a Power Fist in hand the Fist charges first (Numina fills hands before armor; unchanged, documented).

Untested in game at the time of writing: all of the above.

## 2026-11-04: MF-4, the Shock Absorber (F-7)

Headless: 2 new GameTests (11 in all) pass: `shock_absorber_plan_shares_the_fall_by_slider_and_battery` (the arithmetic) and `shock_absorber_at_slider_1_absorbs_a_30_block_fall_with_a_full_battery_and_a_third_with_1000_fe` (fall events posted to the bus: 30 blocks with a charged battery end at 0 for 3,000 FE; with 1,000 FE they end at 20 and the battery is empty; a 3-block fall is untouched).

In game, in single player and on a dedicated server, boots with a battery and the Shock Absorber:
1. Slider at 1, charged battery: jump off a 30-block tower, land without damage; the energy readout drops by about 3,000 FE.
2. Slider at 1, battery nearly empty (a few hundred FE): the same jump hurts, but less than without the module, and the battery reads 0 afterwards.
3. Slider at 0.5: half the fall is absorbed (a 30-block jump hurts like a 15-block one).
4. Slider at 0: full fall damage, no drain.
5. The old install-sound on landing no longer plays (known, cosmetic).

Untested in game at the time of writing: all of the above.

## 2026-11-04: MF-5, the Energy Shield (F-8)

Headless: 4 new GameTests (15 in all) pass: the share arithmetic; a chestplate with the shield at slider 1 has the same armor points as one without and keeps 0.25 knockback resistance; the module stays online after a tick with an empty battery; a 10-point cactus hit with the shield at slider 1 lets 7.6 through and drains 12,000 FE, magic damage is untouched, with 6,000 FE left the hit lets 8.8 through and empties the battery, and with nothing left nothing is absorbed.

In game, in single player and on a dedicated server, chestplate with a battery and the Energy Shield at slider 1:
1. The armor bar does not rise when the shield is switched on (plating still counts).
2. Take a hit from a mob or a cactus: the damage is lower than without the shield and the energy readout drops by about 5,000 FE per damage point absorbed (a 10-damage hit: about 12,000 FE).
3. Poison, starvation or /kill-style damage is not reduced and costs nothing.
4. Drain the battery and keep fighting: the module stays on (the Tinker Table still shows it enabled) and absorbs nothing until charged.
5. Knockback from hits is still reduced with the shield on.

Untested in game at the time of writing: all of the above.

## 2026-11-04: MF-6, the lang files (F-9)

Headless: `./gradlew check` runs `tools/check_lang.py` (both files parse, all 14 module categories present); 1 new GameTest (16 in all) reads the two files from the classpath and checks the categories, the key category, the two-dot field-of-view key, a Numina item name, a module toggle-key name, that non-module items get no toggle key, that nothing Powersuits already has is overridden, and the GUI keys.

In game, on a client with both mods and this jar:
1. The Tinker Table's module categories read "Armor Modules", "Energy Storage", "Vision" and so on, not `numina.module.category.*`.
2. Numina items (Servo Motor, Control Circuit, batteries) show their names in JEI and tooltips.
3. Options > Controls has a "Numina" category with "Toggle field of view fix", and the Powersuits keys read "Toggle Jetpack", "Fly Down (Flight Control)", "Open MPS Keybind GUI" and so on.
4. The Tinker Table's keybinds tab and the crafting container show "Toggle" and "Crafting"; the install/salvage GUI title reads "Tinker Table".
5. Nothing Powersuits already named changed (module names and descriptions are theirs).

Untested in game at the time of writing: all of the above (lang is client-only; the headless server cannot show it).

## 2026-11-04: MF-7, the fire-immunity toggle (F-10)

Headless: 1 new GameTest (17 in all): with `suitClearsFire = false` a START-phase suit tick leaves a burning player burning; set back to `true`, the same tick puts the fire out.

In game, on a dedicated server (the setting is a server config, synced to clients):
1. `world/serverconfig/bsp_mod_fixes-server.toml` appears after the first load with `suitClearsFire = true`.
2. Default: a player in a charged suit hit by a flame arrow or fire-aspect sword stops burning at once (Powersuits' behaviour).
3. Set `suitClearsFire = false` and `/reload` or restart: the same player keeps burning like anyone else; heat still rises from fire damage as before.

Untested in game at the time of writing: all of the above.
