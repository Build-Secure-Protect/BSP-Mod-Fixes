#!/usr/bin/env python3
"""Checks the lang files this mod ships: the two of F-9 parse as flat string maps and Numina's carries every module category
name the Tinker Table shows; this mod's own carries the keys its code uses. Run by `./gradlew check`.

    python3 -I tools/check_lang.py
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NUMINA = ROOT / "src/main/resources/assets/numina/lang/en_us.json"
POWERSUITS = ROOT / "src/main/resources/assets/powersuits/lang/en_us.json"
OURS = ROOT / "src/main/resources/assets/bsp_mod_fixes/lang/en_us.json"
OUR_KEYS = ("key.categories.bsp_mod_fixes", "key.bsp_mod_fixes.quantum_readout", "hud.bsp_mod_fixes.quantum_dmg", "hud.bsp_mod_fixes.quantum_ae")
CATEGORIES = ("none", "debug", "armor", "energystorage", "energygeneration", "tool", "weapon", "movement", "cosmetic", "vision",
              "environment", "special", "miningenhancement", "miningenchantment")


def load(path: pathlib.Path) -> dict:
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in data.items()):
        sys.exit(f"{path}: not a flat map of strings")
    return data


def main() -> None:
    numina = load(NUMINA)
    powersuits = load(POWERSUITS)
    missing = [f"numina.module.category.{c}" for c in CATEGORIES if f"numina.module.category.{c}" not in numina]
    if missing:
        sys.exit(f"{NUMINA}: missing {', '.join(missing)}")
    ours = load(OURS)
    lacking = [k for k in OUR_KEYS if k not in ours]
    if lacking:
        sys.exit(f"{OURS}: missing {', '.join(lacking)}")
    empty = [k for d in (numina, powersuits, ours) for k, v in d.items() if not v.strip()]
    if empty:
        sys.exit(f"empty values for {', '.join(empty)}")
    print(f"lang OK: numina {len(numina)} keys with all {len(CATEGORIES)} categories, powersuits {len(powersuits)} keys, ours {len(ours)} keys")


if __name__ == "__main__":
    main()
