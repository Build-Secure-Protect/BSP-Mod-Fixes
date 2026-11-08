# Configuration

## This mod's server config: `bsp_mod_fixes-server.toml`

Written into `world/serverconfig/` on first load (and `defaultconfigs/bsp_mod_fixes-server.toml` sets the default for new worlds). Forge sends it to each client at login, and only then: the clients need no copy, and a change on a running server (Forge re-reads a saved file within seconds; `/reload` does not) reaches each client at its next login. Three settings:

| Key | Default | Meaning |
|---|---|---|
| `[fire] suitClearsFire` | `true` | Numina puts out a burning player who wears or holds a modular item while the suit's heat is under 95 % of its maximum, so suit wearers are all but immune to fire aspect and flame. `true` keeps that Powersuits behaviour; `false` lets the fire burn as on anyone else. (Fix F-10.) |
| `[flight] keepFlyingSpeed` | `true` | Powersuits' Glider, Sprint Assist and Jump Assist modules changed the player's creative flying speed and never put it back; vanilla saves that speed with the player, and creative flight (AdvancedAE's Flight card) then runs at it. The modules no longer change it whatever this says. `true` also puts a survival or adventure player's stored speed back to vanilla's 0.05 once a second when it is anything else, with one log line per reset (creative and spectator players are left alone, an op may have set their own); `false` leaves every stored speed as it is. (Fix F-24.) |
| `[gobber] fixDragonFlight` | `true` | Gobber 2's dragon armour sends the player's abilities to the other side every tick, for every piece worn, on the client and on the server; each side then overwrites its flying flag with the other's stale copy, so on a server creative flight flickers on and off and the double tap will not hold. `true` sends the update only when the flags changed, and never from the client; `false` leaves Gobber's behaviour as it is, for when Gobber fixes it itself (no rebuild of this mod needed). Only read while exactly Gobber 2 2.8.9 is loaded; any other Gobber skips the fix whatever this says. (Fix F-27.) |

A player already affected can also be put right at once with Catnip's command, bundled with Create: `/catnip util flySpeed reset <name>` (permission level 2). `/data get entity <name> abilities.flySpeed` shows the stored value; vanilla's is `0.05f`.

## This mod's client config: `bsp_mod_fixes-client.toml`

In the game's `config` folder on each client. One value, which the game writes itself:

| Key | Default | Meaning |
|---|---|---|
| `[quantumReadout] collapsed` | `false` | Whether the quantum suit readout (the plate above the hotbar's left end while AdvancedAE's quantum armor is worn) is collapsed to its tab. Changed by the readout's key binding ("Toggle quantum suit readout" in Controls, unbound by default) or by clicking the tab while a screen such as the inventory is open; kept until changed again. (Fix F-25.) |

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
