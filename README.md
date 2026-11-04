# BSP-Mod Fixes

Fixes for other mods in the Build Secure Protect (BSP) modpack: Minecraft 1.20.1, Forge 47.4.x. MIT.

The mod patches bugs and exploits in **Numina 2.3.13** and **Modular Powersuits 2.3.13** without shipping or changing their jars: mixins into their classes, event handlers of this mod's own, and language files that fill the gaps in theirs. It is installed on every client and every server, and it requires exactly the versions it was built against, so a different Numina or Powersuits refuses to start instead of being patched wrongly.

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

## Requirements

- Minecraft 1.20.1 with Forge 47.4.10 or newer (the network runs 47.4.23).
- Numina 2.3.13 and Modular Powersuits 2.3.13, on the client and on the server.

## Build

```
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew check` also runs `tools/check_lang.py` over the two shipped lang files (regenerate them with `tools/gen_lang.py <numina jar> <powersuits jar>` after a dependency bump). `./gradlew runGameTestServer` runs the headless tests (add `-PmixinVerbose` to see every injection Mixin applies); `./gradlew runServer` and `./gradlew runClient` start a dev server or client with both patched mods loaded.
