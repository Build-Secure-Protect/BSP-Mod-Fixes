#!/usr/bin/env python3
"""Checks the two lang files this mod ships (F-9): both parse as flat string maps, and Numina's carries every module category
name the Tinker Table shows. Run by `./gradlew check`.

    python3 -I tools/check_lang.py
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NUMINA = ROOT / "src/main/resources/assets/numina/lang/en_us.json"
POWERSUITS = ROOT / "src/main/resources/assets/powersuits/lang/en_us.json"
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
    empty = [k for d in (numina, powersuits) for k, v in d.items() if not v.strip()]
    if empty:
        sys.exit(f"empty values for {', '.join(empty)}")
    print(f"lang OK: numina {len(numina)} keys with all {len(CATEGORIES)} categories, powersuits {len(powersuits)} keys")


if __name__ == "__main__":
    main()
