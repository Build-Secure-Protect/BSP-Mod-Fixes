# Configuration

## This mod's server config: `bsp_mod_fixes-server.toml`

Written into `world/serverconfig/` on first load (and `defaultconfigs/bsp_mod_fixes-server.toml` sets the default for new worlds). One setting:

| Key | Default | Meaning |
|---|---|---|
| `[fire] suitClearsFire` | `true` | Numina puts out a burning player who wears or holds a modular item while the suit's heat is under 95 % of its maximum, so suit wearers are all but immune to fire aspect and flame. `true` keeps that Powersuits behaviour; `false` lets the fire burn as on anyone else. (Fix F-10.) |

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

This is a deliberate re-balance toward solar: on open ground the old module's steady state was higher than it looked, but erratic (see the vault's assessment).

### Energy Shield (F-8)

Keep the defaults first (`armorEnergy` 6 and `armorEnergyPerDamage` 5000 per slider unit, so up to 5,000 FE per damage point absorbed) and tune after play.

### Known cosmetic issue

`powersuits-server.toml` has a garbled key, `coolingBonen_usergyCon_multiplier`, from an upstream string bug. Harmless; leave it.
