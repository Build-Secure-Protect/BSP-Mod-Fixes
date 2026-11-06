# Testing

Headless checks run with `./gradlew runGameTestServer`. The sections below are the in-game checks, one per task, newest at the bottom. Every fix is checked in single player as well as on a dedicated server, because a mixin listed for one side only would be missed by the headless server.

The network runs Forge 1.20.1-47.4.23 only since 2026-11-06; the Mohist checks in the older sections below no longer apply.

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

## 2026-11-04: MF-9, the EMI gate and the null ingredient (F-11)

Headless: 2 new GameTests (19 in all): `emi_gate_applies_the_fixes_for_the_exact_version_only` (the verdict for the pinned build, no EMI, an older, a newer and the fabric build) and `emi_fixes_are_skipped_without_emi_and_never_applied_on_a_server` (without EMI the plugin skipped F-11 and the report says "[skipped: EMI is not loaded]"; with `-PwithEmi` the gate reads EMI's version, applies, and F-11 waits for its target, which never loads on a server). Both `./gradlew runGameTestServer` and `./gradlew runGameTestServer -PwithEmi` pass 19/19.

In game, on a client with EMI 1.1.24+1.20.1+forge, JEI 15.74.0.234, Tinkers' Construct 3.12.1.231 and Mantle 1.11.117 (`./gradlew runClient -PwithEmi`, or the pack):
1. The log has "BSP-Mod Fixes: EMI 1.1.24+1.20.1+forge is loaded, the EMI fixes apply" before the mod list, and the startup report lists F-11 (EMI 1.1.24+1.20.1+forge) "[applies when its target class loads]"; once a world is open and EMI has reloaded, "F-11 applied to dev.emi.emi.jemi.JemiUtil (first use of its target)".
2. `latest.log` has no "Exception thrown adding adding JEI recipe" lines (the pack's log had three, from `EntityMeltingRecipeCategory` twice and `SeveringCategory`).
3. EMI's Entity Melting and Severing categories (Tinkers' Construct) show recipes.
4. With a different EMI version in `mods` (any other build): the game starts, the log has one WARN "EMI <version> is loaded but the EMI fixes were built for 1.1.24+1.20.1+forge; they are skipped", and the report lists F-11 "[skipped: EMI is not 1.1.24+1.20.1+forge]". Everything else works as before.
5. On a dedicated server (no EMI) and in single player without EMI: the log has "EMI is not loaded, the EMI fixes are skipped" and the report lists F-11 as skipped; nothing else changes.

Untested in game at the time of writing: all of the above (the dev client reaches the title screen only; see MF-12).

## 2026-11-04: MF-10, the tooltip builder (F-12) and the fluid amount line (F-13)

Headless: 3 new GameTests (22 in all) on the generic parts, without client classes: `live_lines_mirror_every_add_and_write_removals_and_inserts_through` (the live line list over EMI's two lists: adds through EMI are recorded, a line EMI drops is not, a ListIterator removal and inserts reach both lists, set between text and rich lines keeps the plain text in order, entries changed behind the view's back are realigned by identity, clear empties all three), `amount_probe_is_replaced_by_the_callbacks_lines_or_put_back_where_it_was` (the amount line's index, Tinkers' removal and units in its place, another mod's callback leaving it, no amount line) and `key_usage_line_formats_the_key_name_in_bold_on_an_italic_gray_line`. `./gradlew runGameTestServer` 22/22; without EMI the report lists F-12 and F-13 "[skipped: EMI is not loaded]".

In game, on a client with EMI 1.1.24+1.20.1+forge, JEI 15.74.0.234, Tinkers' Construct 3.12.1.231 and Mantle 1.11.117, in a world:
1. The startup report lists F-12 and F-13 (EMI 1.1.24+1.20.1+forge); after EMI's reload the log has "F-12 applied to dev.emi.emi.jemi.impl.JemiTooltipBuilder" and "F-13 applied to dev.emi.emi.jemi.widget.JemiSlotWidget".
2. Open EMI's recipes for Molten Iron (melting): no "Error Rendering" box; hovering the molten iron tank shows "Molten Iron", then Mantle's unit lines ("1 Ingot" for 90 mB of molten iron... in the fluid's units: ingots, nuggets, a leftover mB line), then "Tinkers' Construct" in blue italics; EMI's own "90mB"-style line is gone (F-13). No stack trace tooltip anywhere.
3. A fuel slot in the same recipe (lava): the fluid name and mod name only; no amount line at all (Tinkers removes JEI's and adds none for fuels), as in JEI.
4. A casting table recipe and an alloying recipe: hovering their fluids reads as in 2; hovering the items shows the usual item tooltip.
5. A vanilla fluid in another mod's EMI recipe (for example water in a JEI-plugin recipe without a Tinkers callback): the tooltip is unchanged from EMI's (name, amount line, mod name).
6. Any recipe of any mod that adds a "press key" hint through JEI's `addKeyUsageComponent` reads as in JEI (italic gray, the key in bold).

Untested in game at the time of writing: all of the above (the dev client reaches the title screen only; see MF-12).

## 2026-11-04: MF-11, the extras widgets (F-14) and the text widgets (F-15)

Headless: 3 new GameTests (25 in all) on the generic parts: `cycle_button_values_step_round_and_start_at_the_initial_value`, `text_layout_measures_lines_as_jei_does` (line height, block height for centring, the line limit of an area) and `extras_and_text_fixes_are_known_and_skipped_without_emi`. The widgets themselves draw on a client only.

In game, on a client with EMI 1.1.24+1.20.1+forge, JEI 15.74.0.234, Tinkers' Construct 3.12.1.231 and Mantle 1.11.117, in a world:
1. The startup report lists F-14 and F-15; after opening any Tinkers recipe in EMI the log has "F-14 applied to dev.emi.emi.jemi.impl.extras.JemiRecipeExtrasBuilder" (and `JemiPlaceable`) and "F-15 applied to dev.emi.emi.jemi.impl.extras.JemiTextWidget". No "Exception adding JEMI extras" lines.
2. Melting (Molten Iron, Seared Brick): the arrow animates (fills left to right over the recipe's time), hovering it shows the melting time; the ore-rate icon shows at the top right with its tooltip; the temperature ("1,000°"-style, red, at the top right) and the fuel label are drawn (F-15).
3. Casting table and basin: the cooling arrow animates and shows the cooling time when hovered; the cast-consumed/kept icon shows its tooltip.
4. Alloying: the recipe shows its title and temperature text; part builder: the arrow and the material text; tool building: the arrow and the slot icons with their tooltips.
5. Modifier recipes: the slot icons draw with their tooltips; the modifier worktable shows its title and, over the title line, the recipe's description as a tooltip (`addTooltipArea`).
6. Entity melting and severing: the arrow draws; the damage text is drawn.
7. Any other mod's JEI plugin that uses buttons in a recipe (none known in the pack): a grey bordered button with its message; clicking it runs its action.
8. Compare each of 2 to 6 with the same recipe in JEI: positions and texts match; EMI's arrow art differs from JEI's by design.

Untested in game at the time of writing: all of the above (see MF-12 for what the headless client run covered).

## 2026-11-04: MF-12, the headless client runs with EMI

Headless, done in this session (the dev client, `./gradlew runClient -PwithEmi -PemiProbe -PquickPlay=emitest`, where `emitest` is a copy of the GameTest server's world in `run/saves`):
1. The client starts with EMI 1.1.24+1.20.1+forge, JEI 15.74.0.234, Tinkers' Construct 3.12.1.231 and Mantle 1.11.117; the log has "EMI 1.1.24+1.20.1+forge is loaded, the EMI fixes apply" and the report "15 fixes in this build, 11 already applied, 0 skipped" (F-11 applies at EMI's plugin init; F-12 to F-15 wait for their targets).
2. The probe (`gametest/EmiClientProbe`, not in the jar) runs at load complete: 17 checks, 0 failed. It constructs EMI's real `JemiTooltipBuilder` and `JemiRecipeExtrasBuilder`, calls `getLines` (a ListIterator removal reaches EMI's lists), `addAll`, `addKeyUsageComponent`, `clear`, the eight F-14 methods, `addText(...).setPosition(...)` and the aligned overloads, builds the resulting EMI widgets into a collecting holder (12 widgets from 10 builders) and reads their tooltips through EMI's widgets. The log shows "F-12 applied to ...JemiTooltipBuilder", "F-14 applied to ...JemiRecipeExtrasBuilder" and "...JemiPlaceable", "F-15 applied to ...JemiTextWidget". The probe is what found the covariant `ITextWidget.setPosition` gap (F-15 addendum).
3. The world opens; EMI reloads ("Reloaded EMI in 2,9 s"), JEMI collects every Tinkers category including Entity Melting and Severing, "F-13 applied to ...JemiSlotWidget", and `latest.log` has zero "Exception thrown adding adding JEI recipe" lines (the pack's log had three).
4. EMI warns "<class> is tainted by com.mrgregles.bsp_mod_fixes.mixin.<mixin>" once per patched class: EMI's notice that another mod's mixin touched its class, expected and harmless. EMI also reports "2 recipes loaded with the same id: jei:/tconstruct/smeltery/melting/..." for a few Tinkers recipes; that is EMI's own, present without this mod.
5. `./gradlew runGameTestServer` without EMI: 25/25, "15 fixes in this build, 8 already applied, 5 skipped" with F-11 to F-15 "[skipped: EMI is not loaded]"; with `-PwithEmi`: 25/25, nothing skipped.

Untested, for the owner (nothing draws without a screen): everything that is seen, in MF-9 to MF-11 above: the arrows animating, the texts and icons in place, the fluid tooltips with Tinkers' units and without EMI's mB line, the buttons; the full BSP instance with its 300 mods (JeiTweaker included); a dedicated Forge or Mohist server with the jar (the GameTest server stands in for it: the gate skips cleanly); a client with a different EMI version (the WARN line and the skip). `./gradlew runServer` still needs `run/eula.txt` from the owner.

## 2026-11-04: audit before the commit

Two independent reviews of the code (the Numina/Powersuits fixes; the EMI fixes), each given the owner's briefs and the target jars, and a scan of all 293 jars in the BSP instance. Findings fixed: the Kinetic Generator's position map is server-only (the client player shares the server player's entity id in single player); refusal WARN lines are throttled per player; the Forge range is `[47.3.10,)` (constructor injection arrived there); `checkLang` warns instead of failing without `python3`; the id sorter tolerates any id; F-13 handles multi-fluid slots; F-12 keeps blank lines for unrenderable components; F-15 uses JEI's no-shadow default and a tooltip for truncated text; the EMI fixes report "client-side only" on a dedicated server; the F-13 probe sits inside its `try`. Headless: 26 GameTests pass with and without `-PwithEmi` (1 new: `refusal_warnings_are_throttled_per_player_and_fix_ids_sort_by_number`); the client probe passes again after the changes (see the Task Queue entry for the figures).

Pack scan (instance `Build Secure Protect (Adding Mods)`, 293 jars): no other mod mixes into Numina, Powersuits or EMI's JEI layer (only Numina's own mixin into vanilla and EMI's own into JEI); no other mod ships `assets/numina/lang` or `assets/powersuits/lang`; no other mod references Numina or Powersuits classes; Applied Mekanistics calls EMI's `JemiUtil.getStack(Object)`, not the overload F-11 patches, and never with null; 14 mods carry EMI plugins through EMI's own API (untouched); the mixin libraries bundled in six mods (MixinSquared and the like) target their own mods; JeiTweaker and the one CraftTweaker script do not touch Tinkers or EMI; Numina, Powersuits, EMI, JEI, Tinkers and Mantle are at exactly the pinned versions.

In game, for the owner, in addition to MF-9 to MF-12:
1. Alloying in EMI with a tag-backed input (for example an alloy that accepts any molten copper): hovering the input tank shows the picked fluid's name, Tinkers' unit lines, then the mod name, with no "mB" line of EMI's.
2. A Tinkers recipe title or temperature draws without a drop shadow, as in JEI.
3. A survival player spamming creative-install or tweak packets from a modified client produces one WARN line per 10 seconds with the count held back.

## 2026-11-06: MF-13 and MF-14, the server-switch fixes (F-16 stale packets, F-17 disconnected screen thread)

Headless: 3 new GameTests (29 in all) pass: `stale_packet_decision_drops_only_a_client_packet_queued_before_the_protocol_moved` (the pure decision over seven cases, and the log description naming a custom payload's channel), `connection_mixin_drops_a_stale_play_packet_on_a_client_channel_moved_to_login_and_sends_the_rest` (a real `Connection` on an in-memory netty channel with the mixin applied, called through the test-only `ConnectionInvoker`: moved to LOGIN, a PLAY-on-PLAY send is dropped, counted and logged "[BSP Mod Fixes] Dropped a stale PLAY packet (net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket on channel bsp_mod_fixes:stale) after a server switch; the connection is now in LOGIN"; a send carrying its own protocol change switches the channel and goes out; a PLAY-on-PLAY send in PLAY goes out; a server's connection in the same state sends and counts nothing) and `disconnected_screen_is_deferred_only_off_the_render_thread_and_the_fix_is_skipped_on_a_dedicated_server`. The test server's report: "17 fixes in this build, 8 already applied, 6 skipped", F-16 "[applies when its target class loads]" then "F-16 applied to net.minecraft.network.Connection (first use of its target)", F-17 "[skipped: client-side only, nothing to patch on a dedicated server]". The production jar: the refmap maps `doSendPacket` to `m_243087_` and `setScreen` to `m_91152_`, and the mixin's shadowed fields are renamed at reobfuscation (`f_129468_`, `f_129466_`), `ATTRIBUTE_PROTOCOL` to `f_129461_`, `isSameThread` to `m_18695_` (javap on `build/libs`).

Dev client (headless, `./gradlew runClient`, to the title screen): "Mixing DisconnectedScreenThreadMixin ... into net.minecraft.client.Minecraft" during bootstrap and "F-17 applied to net.minecraft.client.Minecraft", then "F-16 applied to net.minecraft.network.Connection" at mod construction and the report "17 fixes in this build, 12 already applied, 5 skipped" (the five are the EMI fixes without EMI); no Mixin warnings for either.

In game, for the owner (the dev client cannot reach the Velocity network), a client with this jar and Forge Client Reset Packet 0.3.2 behind the proxy (Ambassador 1.4.5):
1. The startup report lists F-16 and F-17 (Minecraft 1.20.1) as applied: F-17 at once, F-16 at the first connection ("F-16 applied to net.minecraft.network.Connection").
2. Switch servers through portals ten or more times, with Oculus shaders on and Distant Horizons enabled (they lengthen the reset and widen the race window). No "Can't serialize unregistered packet" disconnect. Every switch that would have failed leaves "[BSP Mod Fixes] Dropped a stale PLAY packet (<class> on channel <id>) after a server switch; the connection is now in LOGIN" in `latest.log`. Note which packet classes and channels appear: they name the mod that sends during the reset. A switch that still fails without a "Dropped" line is not F-16 failing: a mod that sends a PLAY packet after the channel is already in LOGIN takes vanilla's protocol-change path (untouched by design) and the proxy, not the encoder, ends the connection; report such a case with the log.
3. A switch that fails for another reason (stop the destination server mid-switch) on a client with Forgified Fabric API: the disconnected screen shows and the game does not crash; `latest.log` has "F-17: the disconnected screen was set from thread 'Netty Client IO #n'; opening it on the render thread instead".
4. A dedicated Forge 47.4.23 server with the jar: the report lists F-16 applied at the first connection and F-17 skipped "client-side only"; no "[BSP Mod Fixes] Dropped" line ever appears there; nothing else changes.
5. Single player with the jar: both fixes listed as applied; nothing is logged by either (the local connection never moves protocol behind a queued send, and no disconnected screen is set off the render thread).

Untested at the time of writing: all of 1 to 5 in the pack (the headless server stands in for 4; the dev client stands in for the applied lines of 1 and 5).

## 2026-11-06: MF-18, JEI starts after the tag sync (F-19)

Headless: 2 new GameTests (31 in all) pass: `jei_start_is_held_until_the_connections_tags_arrive_whatever_the_order` (the pure session: held with no connection and until this connection's tags; released by them and staying released for a later recipe sync; a new connection held again and the old one no longer counting; tags before recipes not held; only a client tag packet counts) and `jei_gate_applies_the_fix_to_any_15_74_build_and_skips_it_on_a_dedicated_server` (15.74.0.234, 15.74.0.229 and a later 15.74 apply; 15.20 and 15.75 do not; absent; the report's view). The test server's report: "18 fixes in this build, 8 already applied, 7 skipped", F-19 "[skipped: client-side only, nothing to patch on a dedicated server]".

Dev client (headless, `./gradlew runClient -PwithEmi -PwithMekanism -PrecipeProbe -PquickPlay=emitest`, EMI 1.1.24, JEI 15.74.0.234, Mekanism 10.4.16.80, single player): "JEI 15.74.0.234 is loaded, the JEI fix applies" and "F-19 applied to mezz.jei.forge.startup.StartEventObserver" at startup, the report "18 fixes in this build, 14 already applied, 0 skipped"; in the world, in this order: "[EMI] Recipes synchronized, waiting for tags to reload EMI...", "F-19: holding JEI's start until the server's tags arrive", "[EMI] Tags synchronized...", "F-19: the server's tags arrived, JEI may start", "Starting JEI..." (1.3 s), "[JEMI] JEI reloaded!". The probe then reports the same as without the fix (run of 15:33): JEI and EMI both hold 26 Metallurgic Infuser recipes, Infused, Reinforced and Atomic Alloy each made by their infusing recipe, 0 "Found a broken recipe". (The first build of the mixin injected at the tail of JEI's `register` and failed verification, "Bad type on operand stack", because JEI's R8-optimised bytecode reuses the `this` slot there; the shipped mixin injects at the head.)

In game, for the owner, a client with this jar on the network (Forge 47.4.23 backends behind Velocity):
1. The startup report lists F-19 (JEI 15.74) as applied; `latest.log` has "BSP-Mod Fixes: JEI 15.74.0.234 is loaded, the JEI fix applies".
2. Join a backend. `latest.log` shows, in this order: "F-19: holding JEI's start until the server's tags arrive", "F-19: the server's tags arrived, JEI may start", then "Starting JEI...". No "Found a broken recipe" block follows (the 2026-11-06 02:23 log had 83 per join).
3. Look up Infused Alloy: the Metallurgic Infuser tab shows its recipe (iron ingot + redstone infusion). Reinforced and Atomic Alloy, Enriched Iron and Steel likewise; a Mekanism ore-processing look-up (Purification Chamber, Chemical Injection Chamber) shows the tag-based recipes; the Mineral Washer and Chemical Crystallizer show the sulfate chain.
4. Switch servers through a portal: the two F-19 lines appear again for the new connection and the recipes are still there.
5. Single player with the jar: the same two lines, then "Starting JEI..."; recipes as before.
6. A client with another JEI build (not 15.74): one WARN "JEI <version> is loaded but the JEI fix was built for 15.74.x; it is skipped", the game runs, the recipes are missing again on a server (JEI's behaviour).
7. A dedicated Forge 47.4.23 server with the jar: the report lists F-19 skipped "client-side only"; nothing else changes.

Untested at the time of writing: 1 to 4, 6 and 7 in the pack (the dev client stands in for 5; the headless server for 7). Whether the pack's other client mods behave with JEI starting a few milliseconds later than before is the owner's observation in 2.

## 2026-11-06: audit of F-19 before the commit

Two independent reviews, handed the code and the jars (not conclusions): one over the F-19 classes against the JEI 15.74.0.234, Forge 47.4.23, Client Reset Packet and Mekanism jars; one a scripted scan of the pack's 295 jars plus 120 nested ones (311 mixin configs, 100 JEI plugins) for anything touching JEI's start or the tag and recipe events. Every finding verified against the bytecode before a change.

Fixed:
1. The gate remembered the tag sync per netty `Connection`, which a Velocity switch through Client Reset Packet keeps (same `Connection`, moved to LOGIN and back, with a new `ClientPacketListener`), so the new backend's recipe packet would have started JEI before its tags on every switch. It now keys on the client's packet listener, new for every login (`Minecraft.getConnection()` is `player.connection`, set in `handleLogin` before Forge fires the login event). Found by me and by the reviewer; the earlier dev-client lines came from that build.
2. The "tags arrived" line and counter fired on every later client tag packet of the same login (a server `/reload` sends tags, then recipes); now only while a hold is pending (GameTest).
3. JEI started inside the tag event's dispatch, ahead of every tag listener registered after the mixin's; Thermal rebuilds the recipe lists its JEI plugin reads on that event. JEI's start is now queued for the next client tick (`Minecraft.tell`, which always queues; `execute` would run at once on the render thread), after every listener of the event. The hold is re-checked when it runs.
4. Any 15.74 build (D33) with a renamed `startIfReady` or `register` would have stopped the client with a Mixin injection failure (the config is `required`). The gate now reads the target class through Mixin's bytecode provider and skips the fix with a WARN when either method is missing (`JeiGate.hasMethods`, GameTest).
5. The dev probe used a `ResourceLocation` constructor marked for removal.

Documented, not changed:
- JEI's own fallback (a container screen opening before JEI has started: ERROR line "A Screen is opening but JEI hasn't started yet", then an immediate start) bypasses the hold; nothing opens a container screen between the recipe and the tag packet of a login, which the client handles in order on the render thread.
- A pack scan found no other mixin into JEI's start classes (EMI and AllTheLeaks wrap `PluginCaller.callOnPlugins` only) and no mod calling JEI's start by reflection. Thirteen JEI plugins resolve tags while registering (ae2, create, electrodynamics, industrialforegoing, jeresources, mekanism, mekanismtools, pneumaticcraft, railcraft, sophisticatedbackpacks, sophisticatedstorage, tconstruct, zeta) and JEI itself resolves every recipe's ingredients at registration; all of them see bound tags with F-19. Recipe Essentials replaces the client recipe manager at listener construction, before either packet; unaffected.

Headless after the audit: `./gradlew build` clean, `runGameTestServer` 31/31 (the two F-19 tests now also drive the static gate: one held line per login, one release, none for a later tag packet; and the target-method check over an ASM class node). Dev client with EMI, JEI and Mekanism after the audit (same command as above): F-19 applied, "holding JEI's start until the server's tags arrive" after the recipe packet, "the server's tags arrived, JEI may start" at the tag packet, "Starting JEI..." on the next tick (after EMI's own "reloading EMI" line, as the deferral intends), 1.1 s; the probe again reports 26 Metallurgic Infuser recipes in JEI and EMI with the three alloys, 0 broken recipes.

