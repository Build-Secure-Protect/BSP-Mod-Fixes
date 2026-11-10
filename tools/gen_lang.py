#!/usr/bin/env python3
"""Writes the lang files this mod ships: the two for Numina and Powersuits (F-9), from the two mods' own lang files plus the
keys they lack, and this mod's own (the quantum suit readout of F-25).

    python3 -I tools/gen_lang.py <numina jar> <powersuits jar>

Numina 2.3.13 ships its English as assets/numina/lang/us_en.json, a file name Minecraft never loads, so every Numina key shows
raw; our assets/numina/lang/en_us.json carries all of it plus the keys the code uses that the file lacks. Powersuits' en_us.json is
loaded but misses the keybinding names of every module and a few GUI keys; our assets/powersuits/lang/en_us.json carries only the
missing keys, so nothing of theirs is overridden (Forge merges lang files per key across mods). Run it again after a dependency
bump and commit the two files it writes.
"""
import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT_NUMINA = ROOT / "src/main/resources/assets/numina/lang/en_us.json"
OUT_POWERSUITS = ROOT / "src/main/resources/assets/powersuits/lang/en_us.json"
OUT_OURS = ROOT / "src/main/resources/assets/bsp_mod_fixes/lang/en_us.json"

# This mod's own text: the quantum suit readout (F-25): its key binding and the two row labels on the plate; and the two
# lines a player is shown about Compact Machines' room preview (F-31).
OURS = {
    "message.bsp_mod_fixes.preview_off": "Room previews are off on this server",
    "message.bsp_mod_fixes.preview_too_large": "This room is too large to preview",
    "key.categories.bsp_mod_fixes": "BSP-Mod Fixes",
    "key.bsp_mod_fixes.quantum_readout": "Toggle quantum suit readout",
    "hud.bsp_mod_fixes.quantum_dmg": "DMG",
    "hud.bsp_mod_fixes.quantum_ae": "AE",
}

NUMINA_EXTRA = {
    "key.categories.numina": "Numina",
    "key..numina.fovfixtoggle": "Toggle field of view fix",  # the code registers the key with two dots
    "item.numina.component_capacitor_lv": "Low Voltage Capacitor",
    "item.numina.component_capacitor_mv": "Medium Voltage Capacitor",
    "item.numina.component_capacitor_hv": "High Voltage Capacitor",
    "item.numina.component_capacitor_ev": "Extreme Voltage Capacitor",
    "item.numina.component_liquid_nitrogen": "Liquid Nitrogen Bucket",
    "item.numina.component_plating_iron": "Iron Plating",
    "item.numina.component_plating_diamond": "Diamond Plating",
    "item.numina.component_plating_leather": "Leather Plating",
}

# Items of Powersuits that are not modules and so have no toggle key.
NOT_MODULES = ("powerarmor_", "powerfist")

POWERSUITS_EXTRA = {
    "keybinding.powersuits.goDownKey": "Fly Down (Flight Control)",
    "keybinding.powersuits.openKeybindGui": "Open MPS Keybind GUI",
    "keybinding.powersuits.openCosmeticGUI": "Open MPS Cosmetic GUI",
    "keybinding.powersuits.openModuleTweakGUI": "Open MPS Module Tweak GUI",
    "keybinding.powersuits.openInstallSalvageGUI": "Open MPS Install/Salvage GUI",
    "keybinding.minecraft.clock": "Toggle Clock",
    "keybinding.minecraft.compass": "Toggle Compass",
    "keybinding.minecraft.recovery_compass": "Toggle Recovery Compass",
    "keybinding.powersuits.clock": "Toggle Clock",
    "keybinding.powersuits.compass": "Toggle Compass",
    "keybinding.ae2.meteorite_compass": "Toggle Meteorite Compass",
    "keybinding.powersuits.plating_leather": "Toggle Leather Plating",
    "item.powersuits.plating_leather": "Leather Plating",
    "item.powersuits.plating_leather.desc": "Leather plating: light, a little protection.",
    "item.powersuits.debug_module": "Debug Module",
    "module.tradeoff.armorToughness": "Armor Toughness",
    "module.tradeoff.invisibilityEnergy": "Camouflage Energy Use",
    "module.tradeoff.maxHeat": "Maximum Heat",
    "gui.tinkertable": "Tinker Table",
    "gui.powersuits.tab.keybinds.toggle": "Toggle",
    "container.crafting": "Crafting",
}


def read_lang(jar: pathlib.Path, path: str) -> dict:
    with zipfile.ZipFile(jar) as z:
        with z.open(path) as f:
            return json.load(f)


def write(path: pathlib.Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(dict(sorted(data.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")


def main(numina_jar: str, powersuits_jar: str) -> None:
    numina = read_lang(pathlib.Path(numina_jar), "assets/numina/lang/us_en.json")
    powersuits = read_lang(pathlib.Path(powersuits_jar), "assets/powersuits/lang/en_us.json")

    ours_numina = dict(numina)
    for key, value in NUMINA_EXTRA.items():
        ours_numina.setdefault(key, value)

    ours_powersuits = {}
    for key, name in powersuits.items():
        if key.startswith("item.powersuits.") and not key.endswith(".desc"):
            module = key[len("item.powersuits."):]
            if module.startswith(NOT_MODULES):
                continue
            ours_powersuits[f"keybinding.powersuits.{module}"] = f"Toggle {name}"
    for key, value in POWERSUITS_EXTRA.items():
        ours_powersuits.setdefault(key, value)
    # only what Powersuits lacks
    ours_powersuits = {k: v for k, v in ours_powersuits.items() if k not in powersuits}

    write(OUT_NUMINA, ours_numina)
    write(OUT_POWERSUITS, ours_powersuits)
    write(OUT_OURS, OURS)
    print(f"{OUT_NUMINA.relative_to(ROOT)}: {len(ours_numina)} keys ({len(numina)} from Numina)")
    print(f"{OUT_POWERSUITS.relative_to(ROOT)}: {len(ours_powersuits)} keys, all absent from Powersuits")
    print(f"{OUT_OURS.relative_to(ROOT)}: {len(OURS)} keys of this mod's own")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
