# Configuration

## This mod's server config: `bsp_mod_fixes-server.toml`

Written into `world/serverconfig/` on first load (and `defaultconfigs/bsp_mod_fixes-server.toml` sets the default for new worlds). Forge sends it to each client at login, and only then: the clients need no copy, and a change on a running server (Forge re-reads a saved file within seconds; `/reload` does not) reaches each client at its next login. Sixteen settings:

| Key | Default | Meaning |
|---|---|---|
| `[fire] suitClearsFire` | `true` | Numina puts out a burning player who wears or holds a modular item while the suit's heat is under 95 % of its maximum, so suit wearers are all but immune to fire aspect and flame. `true` keeps that Powersuits behaviour; `false` lets the fire burn as on anyone else. (Fix F-10.) |
| `[flight] keepFlyingSpeed` | `true` | Powersuits' Glider, Sprint Assist and Jump Assist modules changed the player's creative flying speed and never put it back; vanilla saves that speed with the player, and creative flight (AdvancedAE's Flight card) then runs at it. The modules no longer change it whatever this says. `true` also puts a survival or adventure player's stored speed back to vanilla's 0.05 once a second when it is anything else, with one log line per reset (creative and spectator players are left alone, an op may have set their own); `false` leaves every stored speed as it is. (Fix F-24.) |
| `[gobber] fixDragonFlight` | `true` | Gobber 2's dragon armour sends the player's abilities to the other side every tick, for every piece worn, on the client and on the server; each side then overwrites its flying flag with the other's stale copy, so on a server creative flight flickers on and off and the double tap will not hold. `true` sends the update only when the flags changed, and never from the client; `false` leaves Gobber's behaviour as it is, for when Gobber fixes it itself (no rebuild of this mod needed). Only read while exactly Gobber 2 2.8.9 is loaded; any other Gobber skips the fix whatever this says. (Fix F-27.) |
| `[compactMachines] skipSaveOnLoad` | `true` | Compact Machines saves and flushes every loaded chunk of its room dimension to disk, on the server thread, each time a machine block loads or is placed and each time a Chunkloader upgrade is applied or removed: the work of `/save-all flush`, once per machine. `true` skips that save; the rooms are written by the world's normal saves. With `roomsFollowMachines` on, Compact Machines no longer reaches that code for its rooms, so this setting matters when that one is off. `false` leaves Compact Machines' behaviour as it is. Only read while exactly Compact Machines 6.0.3 is loaded; any other build skips the fix whatever this says. Server side only. (Fix F-28.) |
| `[compactMachines] roomsFollowMachines` | `true` | Compact Machines force-loads the room of every machine that has a tunnel, saves that with the world, and releases it only when a machine bound to it loads again with no tunnel left. `true` keeps a room loaded only while a machine block bound to it is itself loaded (by a player nearby, a chunk loader, a BSP-Core Anchor) or while a player is inside it; the forced flags saved in the room dimension are cleared at each server start (so is an admin's `/forceload` in that dimension); the Chunkloader upgrade follows the same rule. `false` leaves Compact Machines' behaviour as it is: within a second the rooms this mod holds are handed to Compact Machines' own forcing, and it forces rooms itself as machines load. The Infinity Booster mods for AE2 and Refined Storage force-load through the same saved flag: one of their blocks inside a room is cleared with the rest at start and forces its chunk again when it loads. Turning it on while the server runs takes full effect at the next restart. Server side only. (Fix F-29.) |
| `[compactMachines] roomReleaseSeconds` | `5` | How long a room stays loaded after the last loaded machine bound to it has gone, 0 to 600 seconds, so a machine at the edge of a player's view does not load and unload its room over and over. (Fix F-29.) |
| `[compactMachines] hardenPreview` | `true` | A right click on a Compact Machine with an empty hand makes the server capture the whole room, every container's contents included, and send it to that player, for any player, any machine, as often as they click. `true` answers such a click by the four settings below; `false` leaves Compact Machines' behaviour as it is and the four are not read. Server side only. (Fix F-31.) |
| `[compactMachines] previews` | `true` | Whether the room preview exists at all. `false`: a right click with an empty hand captures nothing and the player reads "Room previews are off on this server"; rooms are entered with the Personal Shrinking Device as before. |
| `[compactMachines] previewIntervalSeconds` | `1` | The least time between two previews for one player, 0 to 60 seconds; a click sooner than that does nothing. |
| `[compactMachines] previewHidesContents` | `true` | `true` sends each block entity of the room as a player standing in it would receive it (a sign's text, a machine's visible state), not its full save, so the contents of chests, drives and machines stay on the server; in the preview such blocks show empty or idle. `false` sends the full save, as Compact Machines does. If a mod's block is missing or wrong in previews, this is the setting to try. |
| `[compactMachines] previewMaxKilobytes` | `1000` | The largest preview that is sent, 10 to 1,040 kilobytes of room data; a larger one is not sent and the player reads "This room is too large to preview". One such packet cannot carry more than 1,048 kilobytes. |
| `[mffs] fixSponge` | `true` | MFFS's sponge module copies every position inside the force field, shuffles the copy on the server thread and walks it every three seconds, loading chunks as it goes; a cube of eight chunks' radius is seventeen million positions and about a gigabyte of memory, kept, and the server stalls on every pass. `true` has the sponge draw a few hundred random positions inside the field (MFFS's own cap: 50 plus 20 per unit of projection speed) in loaded chunks instead, drain the fluid blocks it finds (source and flowing, vanilla or modded, as MFFS does) and empty waterlogged blocks (which MFFS 5.1.29 never did); `false` leaves MFFS's behaviour as it is. Only read while exactly MFFS 5.1.29 is loaded; any other MFFS skips the fix whatever this says. Server side only. (Fix F-34.) |
| `[mffs] spongeMaxInteriorBlocks` | `0` | The largest field interior, in blocks, a sponge module works in while `fixSponge` is on: in a larger field it drains nothing and the server logs one line every ten seconds at most. `0` means no limit. A cube of radius r has (2r+1)³ interior blocks: 35,937 at one chunk, 2,146,689 at four chunks, 16,974,593 at eight. (Fix F-34.) |
| `[mffs] maxFieldBlocks` | `100000` | The largest force field a projector may project, in field blocks (the positions its shape would fill). MFFS puts no bound on a field, and every field block is a block entity that each nearby client asks the server about one by one as its chunk arrives; a cube of eight chunks' radius is about 396,000 of them, and a player's client at twenty chunks froze and died. A projector whose field is over the limit projects nothing, removes what it had built where the chunks are loaded, tells the players within 15 blocks on their action bar once a minute ("This force field is too large for this server") and logs one line every ten seconds at most. `0` means no limit. A cube of radius r has about 24 r² field blocks: 6,000 at one chunk, 98,000 at four chunks, 393,000 at eight. The count is taken after the field's modules have had their say (a Dome field is counted at its upper half). Existing fields over the limit vanish at their projector's next recalculation (a server start, a module change), and a raised limit takes effect the same way. An unpowered projector is quiet. Server side only. (Fix F-35.) |
| `[mffs] maxFieldRadius` | `0` | The largest radius a projector may project, in blocks from the field's centre along any axis (the largest Scale count on any side), judged beside `maxFieldBlocks` with the same effect. `0` means no limit; `128` is eight chunks. (Fix F-35.) |
| `[mffs] syncFieldBlocksInChunks` | `true` | Every MFFS force field block is a block entity holding its projector's position, its camouflage and the light its projector's Glow modules give it, and MFFS puts none of that into the chunk packet: as each chunk arrives, the client asks the server for every field block in it one by one, the server answers each, and the client re-renders and relights each block as its answer comes (about 4,100 round trips for one wall chunk of a field at eight chunks' radius, 6,100 at twenty); a client near a large field freezes. `true` puts each block's data into the chunk packet itself and has the client use it from there (its light checked once the chunk's light has arrived), asking only for a block placed into a chunk it already has (a field being built in view), at the end of the tick it appeared in; `false` leaves MFFS's behaviour as it is. Read on both sides (Forge sends this file to each client at login, so a change reaches a client at its next login). Only read while exactly MFFS 5.1.29 is loaded; any other MFFS skips the fix whatever this says. (Fix F-36.) |

A player already affected can also be put right at once with Catnip's command, bundled with Create: `/catnip util flySpeed reset <name>` (permission level 2). `/data get entity <name> abilities.flySpeed` shows the stored value; vanilla's is `0.05f`.

## This mod's client config: `bsp_mod_fixes-client.toml`

In the game's `config` folder on each client. Three values:

| Key | Default | Meaning |
|---|---|---|
| `[quantumReadout] collapsed` | `false` | Whether the quantum suit readout (the plate above the hotbar's left end while AdvancedAE's quantum armor is worn) is collapsed to its tab. Changed by the readout's key binding ("Toggle quantum suit readout" in Controls, unbound by default) or by clicking the tab while a screen such as the inventory is open; kept until changed again. (Fix F-25.) |
| `[compactMachines] freePreviewMemory` | `true` | Compact Machines' room preview (right click on a machine with an empty hand) keeps most of the video and native memory it takes for the rest of the session, more with every preview and every window resize while one is open. `true` releases it when the preview closes; `false` leaves Compact Machines' behaviour as it is. Only read while exactly Compact Machines 6.0.3 is installed. (Fix F-30.) |
| `[emi] clampJeiTransferSlots` | `true` | EMI lays a shaped crafting recipe out for a JEI transfer handler (Refined Storage's grid, Mekanism's QIO crafting window and Formulaic Assemblicator) as if it always had nine inputs; a shaped recipe of another mod's class with fewer (Mekanism's Laser Tractor Beam, EnderStorage's recolour recipes, Quark's dirty glass) crashes the game to the desktop when viewed with one of those screens open. `true` holds EMI's bounds within the recipe's input list and logs the first clamp of each recipe per session at WARN; `false` leaves EMI's behaviour as it is. Only read while exactly EMI 1.1.24+1.20.1+forge is installed; any other EMI skips the fix whatever this says. (Fix F-37.) |

## Powersuits server config to re-tune: `powersuits-server.toml`

Apply in every world's `world/serverconfig/powersuits-server.toml` and in `defaultconfigs/powersuits-server.toml`. The fixes change what the numbers mean, so the old values would feel wrong.

### After F-3 (the suit ticks once per tick, not twice)

Thrust and drain were both doubled by the bug. Halving the drain and doubling the thrust multipliers restores the current feel at a sane cost.

| Section | Key | Was | Set to | Why |
|---|---|---|---|---|
| `[Modules.Movement.jetpack]` | `jetpackThrust_thrust_multiplier` | 0.16 | **0.32** | restores the current feel |
| `[Modules.Movement.jetpack]` | `energyCon_thrust_multiplier` | 15000 | **2500** | full thrust ≈ 0.32 × 2500 × 20 ≈ 16,000 FE/s: a 1 M basic battery lasts about a minute, an advanced about ten, an elite about 1 h 45 |
| `[Modules.Movement.jet_boots]` | `jetbootsThrust_thrust_multiplier` | 0.08 | **0.16** | restores the current feel |

### After F-6 (the Kinetic Generator pays per real block)

Energy is now per block walked, every tick, so the old per-sample numbers are far too high.

| Section | Key | Was | Set to | Why |
|---|---|---|---|---|
| `[Modules.Energy_Generation.generator_kinetic]` | `base_energyPerBlock` | 2000 | **250** | sprinting at max ≈ 0.28 blocks/tick × 1000 × 20 ≈ 5,600 FE/s, like a solar module in full sun |
| `[Modules.Energy_Generation.generator_kinetic]` | `energyPerBlock_energyGenerated_multiplier` | 6000 | **750** | same |

The generator credits whole FE per tick, truncated: a rate under about 4 FE per block (walking is 0.22 block a tick, sprinting 0.28) would round to nothing every tick, so keep `base_energyPerBlock` well above that. A step of more than one block in a tick is treated as a teleport and pays nothing; that is also the most a modified client can claim per tick.

This is a deliberate re-balance toward solar: on open ground the old module's steady state was higher than it looked, but erratic (see the vault's assessment).

### Energy Shield (F-8)

Keep the defaults first (`armorEnergy` 6 and `armorEnergyPerDamage` 5000 per slider unit, so up to 5,000 FE per damage point absorbed) and tune after play.

### Known cosmetic issue

`powersuits-server.toml` has a garbled key, `coolingBonen_usergyCon_multiplier`, from an upstream string bug. Harmless; leave it.
