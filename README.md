# BSP-Mod Fixes

Fixes for other mods in the Build Secure Protect (BSP) modpack: Minecraft 1.20.1, Forge 47.4.x. MIT.

The mod patches bugs and exploits in **Numina 2.3.13** and **Modular Powersuits 2.3.13** without shipping or changing their jars: mixins into their classes, event handlers of this mod's own, and language files that fill the gaps in theirs. It is installed on every client and every server, and it requires exactly the versions it was built against, so a different Numina or Powersuits refuses to start instead of being patched wrongly.

It also patches **EMI 1.1.24+1.20.1+forge**'s JEI compatibility layer so that Tinkers' Construct 3.12.1.231 recipes render in EMI. EMI is optional and client-side: those fixes apply only when exactly that EMI build is loaded, and any other EMI (or none) costs them and nothing else, with one line in the log.

And it patches two spots of **Minecraft 1.20.1** itself for the pack's Velocity network, where players switch servers with Forge Client Reset Packet: a packet queued just before the switch changed the connection's protocol no longer closes the connection, and a disconnected screen is only ever opened on the render thread. Mixins into Minecraft classes are remapped for production; the one into a client-only class is skipped on dedicated servers.

It also patches **Modular Powersuits** twice more: for a value it leaves on the player (the creative flying speed its Glider, Sprint Assist and Jump Assist modules change and never restore) and for its HUD energy meter, which showed for any mod's energy-storing armor; and **AdvancedAE 1.3.6-1.20.1**'s quantum armor so its damage absorption shows on a suit readout. AdvancedAE is optional, on both sides: that fix applies only when exactly that build is loaded and is skipped with one line in the log otherwise.

And it patches **Gobber 2 2.8.9**'s dragon armour, whose creative flight flickered and would not hold on a server because the armour sent the player's abilities every tick from both sides. Gobber is optional, on both sides, gated to exactly that build, and the fix has a switch in the server config for the day Gobber fixes it itself.

What each fix does is in `CHANGELOG.md`; the server settings that go with it are in `docs/CONFIG.md`; the checks are in `docs/TESTING.md`. The server log lists the fixes once loading is complete.

| Fix | Mod at fault | What changes |
|---|---|---|
| F-1 | Powersuits | The creative-install packet only works for a sender in creative mode (it had no check); refusals are logged with the player's name. |
| F-2 | Numina | Module slider values from the client are clamped to 0..1 (they were stored unchecked); clamps are logged with the player's name. |
| F-3 | Numina | The suit ticks once per tick, not twice (the tick phase was never checked). Jet thrust and drains halve; see `docs/CONFIG.md`. |
| F-4 | Powersuits | The player update handler runs on the living tick only, not on every living event of every entity. |
| F-5 | Powersuits | Night Vision refreshes about every 9 seconds instead of every tick, so it costs its stated 100 FE. |
| F-6 | Powersuits | The Kinetic Generator pays per block walked, every tick, nothing while airborne; see `docs/CONFIG.md`. |
| F-7 | Powersuits | The Shock Absorber absorbs the share of a fall its slider sets and the battery can pay for (it was all or nothing). |
| F-8 | Powersuits | The Energy Shield absorbs part of each hit for energy instead of adding armor points, and stays on when the battery is low. |
| F-9 | Numina, Powersuits | English names: Numina's misnamed lang file and Powersuits' missing keybinding and GUI keys. |
| F-10 | Numina | Option `suitClearsFire` in `bsp_mod_fixes-server.toml` (default on) decides whether a suit puts its wearer's fire out. |
| F-11 | EMI | A null entry in a JEI ingredient list (allowed by JEI's API) becomes an empty EMI stack instead of dropping the recipe; Tinkers' entity melting and severing categories show in EMI. |
| F-12 | EMI | EMI's JEI tooltip builder gains the JEI 15.x methods it lacked (`getLines`, `clear`, `clearIngredient`, `addKeyUsageComponent`), so Tinkers' fluid tooltips no longer turn a recipe into "Error Rendering". |
| F-13 | EMI | A fluid's amount line is offered to a JEI rich tooltip callback under JEI's key, so Tinkers' unit lines ("1 Ingot") replace EMI's "mB" line as they do in JEI. |
| F-14 | EMI | EMI's JEI recipe extras builder gains the JEI 15.56 widget methods (drawables, arrows, plus signs, flames, tooltip areas, buttons) with their tooltips, so Tinkers' arrows and icons draw. |
| F-15 | EMI | JEI text widgets are drawn (EMI returned them without drawing them), so Tinkers' titles, temperatures and times show. |
| F-16 | Minecraft | A packet sent just before a server switch moved the connection to another protocol is dropped and logged with its class and channel, instead of "Can't serialize unregistered packet" closing the connection. |
| F-17 | Minecraft (set by Client Reset Packet) | A disconnected screen set from the network thread is opened on the render thread, so clients with the Fabric Screen API no longer crash on a failed switch. |
| F-19 | JEI | JEI starts only after the server's tags have arrived, so Mekanism's tag-based recipes (the Metallurgic Infuser alloys among them) and the Electrodynamics family's fluid recipes show on a server as they do in single player. |
| F-24 | Powersuits | The Glider, Sprint Assist and Jump Assist modules leave the player's creative flying speed as they found it (the Glider added 0.03 to it every tick of gliding, the assists overwrote it, none restored it; vanilla saves it with the player and creative flight then ran at it). A survival or adventure player's stored speed is put back to vanilla's once a second (`keepFlyingSpeed` in `bsp_mod_fixes-server.toml`). |
| F-25 | AdvancedAE | The quantum armor's damage absorption is shown on a suit readout: a plate at the left edge while the armor is worn, with the suit's charge, and for three seconds after a hit the share taken off and the AE paid; a tab (or a key binding) collapses it, and collapsed stays collapsed. The arithmetic and rules are AdvancedAE's own. |
| F-26 | Powersuits | The HUD energy meter shows only while a Powersuits modular item is worn or held (it showed for any mod's energy-storing armor). |
| F-27 | Gobber 2 | The dragon armour sends the player's abilities only when they changed, and never from the client (it sent them every tick for every piece worn, on both sides, and the two sides overwrote each other's flying flag, so on a server creative flight flickered and the double tap would not hold). `fixDragonFlight` in `bsp_mod_fixes-server.toml` turns the rule off. |

## Requirements

- Minecraft 1.20.1 with Forge 47.3.10 or newer (the network runs 47.4.23).
- Numina 2.3.13 and Modular Powersuits 2.3.13, on the client and on the server.
- Optional, client only: EMI 1.1.24+1.20.1+forge with JEI 15.74.0.234 for the EMI fixes (F-11 onward); they are skipped with any other EMI. Any JEI 15.74 build for the JEI fix (F-19); it is skipped with any other JEI.
- Nothing extra for the server-switch fixes (F-16, F-17); they matter on clients that switch servers through Velocity with Forge Client Reset Packet 0.3.2 and do nothing elsewhere.
- Optional, both sides: AdvancedAE 1.3.6-1.20.1 for the quantum armor fix (F-25); it is skipped with any other AdvancedAE.
- Optional, both sides: Gobber 2 2.8.9 for the dragon armour flight fix (F-27); it is skipped with any other Gobber.

## Build

```
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew check` also runs `tools/check_lang.py` over the two shipped lang files (regenerate them with `tools/gen_lang.py <numina jar> <powersuits jar>` after a dependency bump). `./gradlew runGameTestServer` runs the headless tests (add `-PmixinVerbose` to see every injection Mixin applies); `./gradlew runServer` and `./gradlew runClient` start a dev server or client with both patched mods loaded; add `-PwithEmi` to any run to load EMI, JEI, Tinkers' Construct and Mantle as well (the exact pack files, from CurseMaven) for the EMI checks. `./gradlew runClient -PwithEmi -PquickPlay=<world>` opens a world from `run/saves` straight away, which is how the headless check reaches EMI's recipe reload. `-PemiProbe` on that run calls the EMI fixes' methods on EMI's real classes once loading is complete and logs one "BSP-Mod Fixes probe" line per check (the probe class lives in the gametest package and is not in the jar). `-PtitleProbe` on a client run closes the client by itself once the title screen has been up for a few seconds, so a headless run that only wants the startup log ends on its own. `-PwithAae` on any run loads AdvancedAE, AE2, GuideME and GeckoLib (the exact pack files), so the quantum armor GameTests hit a dressed player through the real mod; without it they check the pure logic and the gate's skip. `-PskipFixes=<mixin class names, comma-separated>` switches those mixins off for a run (the startup report lists their fixes as skipped), which is how a test shows the patched mod's own behaviour: `./gradlew runGameTestServer -PwithAae -PskipFixes=AaeDamageAbsorptionMixin` runs the quantum armor hit against AdvancedAE's own handler. `-PwithGobber` on any run loads Gobber 2 (the exact pack file), so the dragon armour GameTest ticks a dressed player through the real item and counts the abilities packets; `-PwithGobber -PskipFixes=GobberDragonFlightMixin` shows Gobber's own four a tick.
