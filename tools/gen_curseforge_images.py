#!/usr/bin/env python3
"""Draws the images of the CurseForge description (docs/curseforge/DESCRIPTION.md) and the project avatar as SVG in the BSP
"Holo HUD" look and renders them to PNG with macOS Quick Look (qlmanage), cropped with sips, the way BSP-Teleport and
BSP-InventorySync do. Item pictures are read straight from the Numina and Modular Powersuits jars in the Gradle cache (nothing
of theirs is kept in the repo); without the jars those spots stay empty. Generated files are never edited by hand.

  python3 tools/gen_curseforge_images.py      # writes docs/curseforge/images/*.png (and keeps the .svg next to them) and docs/curseforge/avatar.png
"""
import glob
import math
import os
import struct
import subprocess
import zipfile
import zlib
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
OUT = TOOLS.parent / "docs/curseforge/images"
AVATAR = TOOLS.parent / "docs/curseforge/avatar.png"
SCALE = 2
BG, PANEL, INSET, LINE = "#0B0F14", "#10151C", "#0C0E12", "#2A2F3A"
ACCENT, VIOLET, GOLD, BAD, OK, TEXT, MUTED = "#19D3B0", "#B58CFF", "#FFD23A", "#FF6B5C", "#8FE04A", "#E6EAF2", "#9AA3B5"
SANS = "Helvetica Neue, Helvetica, Arial, sans-serif"
MONO = "Menlo, Monaco, monospace"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ------------------------------------------------------------------ textures from the two jars

def jar(slug):
    found = sorted(glob.glob(os.path.expanduser(f"~/.gradle/caches/modules-2/files-2.1/curse.maven/{slug}/*/*/*.jar")))
    return zipfile.ZipFile(found[-1]) if found else None


POWERSUITS = jar("modular-powersuits-235442")
NUMINA = jar("numina-235440")


def read_png(data):
    """A small PNG reader (non-interlaced; gray, RGB, RGBA, gray+alpha and palette at 1, 2, 4 or 8 bits; tRNS colour keys):
    rows of (r, g, b, a)."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert depth in (1, 2, 4, 8) and interlace == 0, "only 1-8 bit non-interlaced textures"
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
        pos += 12 + length
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bpp = max(1, channels * depth // 8)              # bytes per pixel for the filters
    stride = (w * channels * depth + 7) // 8
    rows, prev, p = [], bytearray(stride), 0
    key = None
    if trns and ctype == 2:
        key = struct.unpack(">HHH", trns[:6])
    elif trns and ctype == 0:
        key = struct.unpack(">H", trns[:2])[0]
    for _ in range(h):
        f, line = raw[p], bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        px = []
        if depth < 8:
            per_byte = 8 // depth
            mask = (1 << depth) - 1
            samples = [(line[x // per_byte] >> (8 - depth * (x % per_byte + 1))) & mask for x in range(w)]
            scale = 255 // mask
            for v in samples:
                if ctype == 3:
                    r, g, b_ = palette[v]
                    px.append((r, g, b_, trns[v] if trns and v < len(trns) else 255))
                else:
                    g = v * scale
                    px.append((g, g, g, 0 if key is not None and v == key else 255))
        else:
            for x in range(w):
                v = line[x * channels:(x + 1) * channels]
                if ctype == 6:
                    px.append(tuple(v))
                elif ctype == 2:
                    px.append((v[0], v[1], v[2], 0 if key is not None and (v[0], v[1], v[2]) == key else 255))
                elif ctype == 0:
                    px.append((v[0], v[0], v[0], 0 if key is not None and v[0] == key else 255))
                elif ctype == 4:
                    px.append((v[0], v[0], v[0], v[1]))
                else:
                    r, g, b_ = palette[v[0]]
                    px.append((r, g, b_, trns[v[0]] if trns and v[0] < len(trns) else 255))
        rows.append(px)
    return w, h, rows


def texture(z, basename):
    """The 16 x 16 item texture of that file name in the jar (the armor-model textures share names), or None."""
    if z is None:
        return None
    for name in z.namelist():
        if "/textures/item/" in name and name.endswith("/" + basename) and z.getinfo(name).file_size < 4000:
            try:
                tex = read_png(z.read(name))
            except AssertionError:
                continue
            if tex[0] == 16 and tex[1] == 16:
                return tex
    return None


# ------------------------------------------------------------------ svg

class Svg:
    def __init__(self, w, h, bg=BG):
        self.w, self.h, self.bg = w, h, bg
        self.parts = [f'<rect width="{w}" height="{h}" fill="{bg}"/>']

    def raw(self, s):
        self.parts.append(s)

    def rect(self, x, y, w, h, fill=PANEL, stroke=None, r=6, sw=1.5, opacity=1):
        s = f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ''
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"{s} opacity="{opacity}"/>')

    def text(self, x, y, s, size=18, fill=TEXT, weight="normal", anchor="start", family=SANS, spacing=0, style="", preserve=False):
        ls = f' letter-spacing="{spacing}"' if spacing else ''
        st = f' font-style="{style}"' if style else ''
        sp = ' xml:space="preserve"' if preserve else ''
        self.parts.append(f'<text x="{x}" y="{y}" font-family="{family}" font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}"{ls}{st}{sp}>{esc(s)}</text>')

    def line(self, x1, y1, x2, y2, stroke=LINE, sw=2, dash=None):
        d = f' stroke-dasharray="{dash}"' if dash else ''
        self.parts.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" stroke-width="{sw}"{d} stroke-linecap="round"/>')

    def arrow(self, x1, y1, x2, y2, stroke=ACCENT, sw=2.5):
        self.line(x1, y1, x2, y2, stroke, sw)
        a = math.atan2(y2 - y1, x2 - x1)
        for d in (-0.5, 0.5):
            self.line(x2, y2, x2 - 14 * math.cos(a + d), y2 - 14 * math.sin(a + d), stroke, sw)

    def circle(self, cx, cy, r, fill, stroke=None):
        s = f' stroke="{stroke}" stroke-width="2"' if stroke else ''
        self.parts.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"{s}/>')

    def icon(self, tex, x, y, size=48):
        """A 16 x 16 texture as crisp squares; an empty slot when the jar is missing."""
        if tex is None:
            self.rect(x, y, size, size, INSET, LINE, r=4)
            return
        w, h, rows = tex
        cell = size / w
        self.parts.append('<g shape-rendering="crispEdges">')
        for j, row in enumerate(rows[:w]):
            for i, (r, g, b, a) in enumerate(row):
                if a < 16:
                    continue
                op = "" if a > 240 else f' opacity="{a / 255:.2f}"'
                self.parts.append(f'<rect x="{x + i * cell:.2f}" y="{y + j * cell:.2f}" width="{cell + 0.3:.2f}" height="{cell + 0.3:.2f}" fill="#{r:02x}{g:02x}{b:02x}"{op}/>')
        self.parts.append('</g>')

    def badge(self, x, y, label, color=ACCENT):
        w = len(label) * 9.4 + 28
        self.rect(x, y, w, 34, INSET, color, r=17)
        self.text(x + w / 2, y + 23, label, 15, color, "bold", "middle")
        return w

    def card(self, x, y, w, h, title, color=ACCENT):
        self.rect(x, y, w, h, PANEL, color, r=10, sw=2)
        self.text(x + 22, y + 40, title.upper(), 19, color, "bold", spacing=2)
        self.line(x + 22, y + 56, x + w - 22, y + 56, LINE, 1)

    def bullet(self, x, y, s, color=ACCENT, size=17, fill=TEXT):
        self.circle(x + 6, y - 6, 6, color)
        self.text(x + 24, y, s, size, fill)

    def header(self, title, sub=""):
        self.text(40, 64, title, 40, TEXT, "bold")
        if sub:
            self.text(40, 100, sub, 20, MUTED)

    def pill(self, x, y, s, color):
        w = len(s) * 8.6 + 20
        self.rect(x, y - 16, w, 24, INSET, color, r=12)
        self.text(x + w / 2, y + 1, s.upper(), 12, color, "bold", "middle", spacing=1)
        return w

    def doc(self):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}">'
                + "\n".join(self.parts) + '</svg>')

    def save(self, name, out=None):
        """Square page (Quick Look renders squares), the picture centred, cropped back by sips."""
        side = max(self.w, self.h)
        doc = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{side}" height="{side}" viewBox="0 0 {side} {side}">',
               f'<rect width="{side}" height="{side}" fill="{self.bg}"/>',
               f'<g transform="translate({(side - self.w) // 2}, {(side - self.h) // 2})">'] + self.parts + ['</g>', '</svg>']
        OUT.mkdir(parents=True, exist_ok=True)
        svg = OUT / f"{name}.svg"
        svg.write_text("\n".join(doc), encoding="utf-8")
        render(svg, self.w, self.h, out)


def render(svg, w, h, out=None):
    png = out or svg.with_suffix(".png")
    side = max(w, h) * SCALE
    subprocess.run(["qlmanage", "-t", "-s", str(side), "-o", str(OUT), str(svg)], check=True, capture_output=True)
    produced = OUT / (svg.name + ".png")
    subprocess.run(["sips", "-c", str(h * SCALE), str(w * SCALE), str(produced), "--out", str(png)], check=True, capture_output=True)
    produced.unlink()
    print(f"{png.name} {w * SCALE}x{h * SCALE}")


# ------------------------------------------------------------------ item textures used on the plates

T = {
    "torso": texture(POWERSUITS, "powerarmor.torso.png"),
    "head": texture(POWERSUITS, "powerarmor.head.png"),
    "legs": texture(POWERSUITS, "powerarmor.legs.png"),
    "feet": texture(POWERSUITS, "powerarmor.feet.png"),
    "fist": texture(POWERSUITS, "handitem.png"),
    "night_vision": texture(POWERSUITS, "night_vision.png"),
    "kinetic": texture(POWERSUITS, "generator.kinetic.png"),
    "shock": texture(POWERSUITS, "shock_absorber.png"),
    "shield": texture(POWERSUITS, "energy_shield.png"),
    "jetpack": texture(POWERSUITS, "jetpack.png"),
    "battery": texture(NUMINA, "basic.png"),
    "circuit": texture(NUMINA, "controlcircuit.png"),
    "servo": texture(NUMINA, "servo.png"),
}


# ------------------------------------------------------------------ 1 banner

def banner():
    s = Svg(1200, 400)
    for i in range(0, 1200, 40):
        s.line(i, 0, i, 400, "#111821", 1)
    for j in range(0, 400, 40):
        s.line(0, j, 1200, j, "#111821", 1)
    s.rect(60, 60, 1080, 280, PANEL, ACCENT, r=12)
    s.text(100, 150, "BSP-Mod Fixes", 58, TEXT, "bold")
    s.text(100, 196, "Bug and exploit fixes for Modular Powersuits, Numina and EMI. No jar is touched.", 24, MUTED)
    x = 100
    for label, color in (("15 fixes", ACCENT), ("Mixins, no jar edits", VIOLET), ("Pinned to 2.3.13", GOLD), ("Tinkers recipes in EMI", OK)):
        x += s.badge(x, 240, label, color) + 14
    s.text(100, 312, "Minecraft 1.20.1  ·  Forge 47.3.10+  ·  client and server  ·  MIT", 16, MUTED, family=MONO)
    for i, key in enumerate(("head", "torso", "legs", "feet")):
        s.icon(T[key], 850 + i * 70, 84, 56)
    s.save("banner")


# ------------------------------------------------------------------ 2 what it patches

def patches():
    s = Svg(1200, 600)
    s.header("What it patches", "Three mods, pinned to the exact versions the fixes were written against. Nothing of theirs is shipped or changed.")
    cols = [
        ("Numina 2.3.13", ACCENT, ("battery", "circuit", "servo"), [
            "F-2  slider values clamped to 0..1",
            "F-3  the suit ticks once per tick",
            "F-9  English names (misnamed lang file)",
            "F-10 fire immunity as a server setting",
        ]),
        ("Modular Powersuits 2.3.13", VIOLET, ("torso", "night_vision", "kinetic", "shield"), [
            "F-1  creative install needs creative mode",
            "F-4  player update on the tick only",
            "F-5  Night Vision charges every 9 s",
            "F-6  Kinetic Generator pays per block",
            "F-7  Shock Absorber absorbs what it can",
            "F-8  Energy Shield absorbs hits for FE",
        ]),
        ("EMI 1.1.24, optional", GOLD, (), [
            "F-11 null items no longer drop recipes",
            "F-12 JEI tooltip methods EMI lacked",
            "F-13 Tinkers' fluid unit lines",
            "F-14 JEI widgets: arrows, icons, areas",
            "F-15 JEI text widgets are drawn",
            "Skipped on servers and without EMI",
        ]),
    ]
    for i, (title, color, icons, lines) in enumerate(cols):
        x = 40 + i * 380
        s.card(x, 130, 360, 440, title, color)
        for k, key in enumerate(icons):
            s.icon(T[key], x + 24 + k * 60, 80 + 130 - 10 + 14, 48) if False else None
        y = 210
        for k, key in enumerate(icons):
            s.icon(T[key], x + 24 + k * 62, 130 + 70, 48)
        y = 130 + 70 + (62 if icons else 0)
        for line in lines:
            s.bullet(x + 24, y + 6, line, color, 16)
            y += 40
        if i == 2:
            s.text(x + 24, 540, "needs JEI 15.74 beside EMI; client only", 14, MUTED, style="italic")
    s.save("what-it-patches")


# ------------------------------------------------------------------ 3 security

def security():
    s = Svg(1200, 560)
    s.header("Two exploits closed", "Both took only a modified client. Both are refused on the server now, and the log names the player.")
    for i, (title, before, after, log, key) in enumerate((
        ("Creative install packet", ["The Tinker Table's creative-install button", "sends a packet. Powersuits' server handler", "never checked the game mode, so any client", "could install any module, fully charged, free."],
         ["Refused unless the sender is in", "creative mode. Creative players", "still get the button, and it works."],
         'WARN F-1: refused a creative-install request from Steve (not in creative mode) for module powersuits:energy_shield', "shield"),
        ("Module slider values", ["A tweak packet carries a raw number. Numina", "stored it unchecked and the modules multiplied", "it: 1000x thrust, negative energy use,", "a thousand armor points."],
         ["Clamped to the slider's own 0..1 before", "it is stored; NaN and infinity become 0.", "Honest clients never notice."],
         "WARN F-2: clamped tweak 'fieldStrength' on powersuits:energy_shield from 1000.0 to 1.0 (sent by Steve)", "jetpack"),
    )):
        x = 40 + i * 580
        s.card(x, 130, 560, 400, title, BAD if i == 0 else GOLD)
        s.icon(T[key], x + 490, 146, 48)
        s.pill(x + 24, 206, "before", BAD)
        for k, line in enumerate(before):
            s.text(x + 24, 232 + k * 23, line, 16, MUTED)
        s.pill(x + 24, 340, "now", OK)
        for k, line in enumerate(after):
            s.text(x + 24, 366 + k * 23, line, 16, TEXT)
        s.rect(x + 24, 440, 512, 76, INSET, LINE, r=6)
        words = log.split(" ")
        for k, chunk in enumerate((words[:7], words[7:13], words[13:])):
            if chunk:
                s.text(x + 34, 462 + k * 20, " ".join(chunk), 12, GOLD, family=MONO)
    s.save("security")


# ------------------------------------------------------------------ 4 once per tick

def ticks():
    s = Svg(1200, 520)
    s.header("The suit ticks once per tick", "Numina's tick handler never looked at the phase, so every module ran at the start and the end of each tick.")
    # before
    s.card(40, 130, 560, 360, "before: twice a tick", BAD)
    for k, (label, col) in enumerate((("START", BAD), ("END", BAD))):
        bx = 70 + k * 270
        s.rect(bx, 200, 240, 120, INSET, col, r=8)
        s.text(bx + 120, 232, label, 18, col, "bold", "middle", spacing=2)
        s.text(bx + 120, 262, "modules tick", 15, MUTED, anchor="middle")
        s.text(bx + 120, 286, "thrust, drain, cooling", 15, MUTED, anchor="middle")
    s.arrow(310, 260, 340, 260, LINE)
    s.text(70, 360, "Jetpack thrust and drain doubled; Night Vision,", 16, TEXT)
    s.text(70, 386, "cooling and every other module ran twice as fast.", 16, TEXT)
    s.text(70, 430, "Powersuits' own update handler ran for every hurt,", 16, MUTED)
    s.text(70, 454, "jump, fall and drop event of every creature too (F-4).", 16, MUTED)
    # after
    s.card(620, 130, 540, 360, "now: once, at the start", OK)
    s.rect(650, 200, 240, 120, INSET, OK, r=8)
    s.text(770, 232, "START", 18, OK, "bold", "middle", spacing=2)
    s.text(770, 262, "modules tick", 15, MUTED, anchor="middle")
    s.text(770, 286, "once per tick", 15, MUTED, anchor="middle")
    s.rect(910, 200, 220, 120, INSET, LINE, r=8)
    s.text(1020, 232, "END", 18, MUTED, "bold", "middle", spacing=2)
    s.text(1020, 262, "skipped", 15, MUTED, anchor="middle")
    s.text(650, 360, "Thrust and drain halve, so the server config", 16, TEXT)
    s.text(650, 386, "re-tunes the jetpack and jet boots (table below).", 16, TEXT)
    s.text(650, 430, "The update handler runs on the living tick only,", 16, MUTED)
    s.text(650, 454, "never on another creature's events (F-4).", 16, MUTED)
    s.icon(T["jetpack"], 1080, 140, 48)
    s.save("once-per-tick")


# ------------------------------------------------------------------ 5 modules

def modules():
    s = Svg(1200, 660)
    s.header("Four modules that now do what they say", "Each had a bug in its arithmetic. Each is fixed where the bug is; the module's sliders and config keep their meaning.")
    cards = (
        ("Night Vision", "night_vision", ACCENT,
         ["Refreshed when under 500 ticks", "remained, with a 300-tick effect:", "it re-applied, and charged 100 FE,", "every single tick (4,000 FE/s)."],
         ["Refreshes under 220 ticks with a", "400-tick effect: one charge of", "100 FE about every 9 seconds,", "and no flicker."]),
        ("Kinetic Generator", "kinetic", VIOLET,
         ["Sampled one tick in twenty, only if", "on the ground at that tick; the", "'not generating' branch fell through", "and paid while flying."],
         ["Pays its rate per block walked,", "every tick, on the server. Nothing", "while airborne, flying, riding, or", "for a teleport."]),
        ("Shock Absorber", "shock", GOLD,
         ["All or nothing: a battery that could", "not pay for the whole fall absorbed", "none of it. Slider 1 'absorbed' the", "fall ten times over."],
         ["The slider sets the share absorbed", "(0 to 100 %), the battery pays per", "block, and a short battery absorbs", "the part it can pay for."]),
        ("Energy Shield", "shield", OK,
         ["Added plain armor points and", "never spent a FE (its drain was", "dead code); switched itself off for", "good when energy dipped."],
         ["Absorbs 4 % of a hit per point,", "80 % at most, for 5,000 FE per", "point at slider 1. No armor points,", "stays on when low."]),
    )
    for i, (title, key, color, before, after) in enumerate(cards):
        x, y = 40 + (i % 2) * 580, 130 + (i // 2) * 260
        s.card(x, y, 560, 240, title, color)
        s.icon(T[key], x + 490, y + 14, 48)
        s.pill(x + 24, y + 90, "before", BAD)
        for k, line in enumerate(before):
            s.text(x + 24, y + 118 + k * 23, line, 15, MUTED)
        s.pill(x + 300, y + 90, "now", OK)
        for k, line in enumerate(after):
            s.text(x + 300, y + 118 + k * 23, line, 15, TEXT)
        s.line(x + 282, y + 80, x + 282, y + 215, LINE, 1)
    s.save("modules")


# ------------------------------------------------------------------ 6 names

def names():
    s = Svg(1200, 530)
    s.header("Names instead of keys", "Numina ships its English as us_en.json, a file Minecraft never loads. Powersuits forgot its keybinding names.")
    rows = (
        ("numina.module.category.armor", "Armor Modules"),
        ("numina.module.category.energystorage", "Energy Storage"),
        ("item.numina.component_servo", "Servo Motor"),
        ("item.numina.battery_basic", "Basic Battery"),
        ("keybinding.powersuits.jetpack", "Toggle Jetpack"),
        ("keybinding.powersuits.goDownKey", "Fly Down (Flight Control)"),
        ("key..numina.fovfixtoggle", "Toggle field of view fix"),
        ("gui.tinkertable", "Tinker Table"),
    )
    s.card(40, 130, 1120, 370, "the Tinker Table, Controls and tooltips", ACCENT)
    for k, (key, name) in enumerate(rows):
        y = 210 + k * 32
        s.text(70, y, key, 15, BAD, family=MONO)
        s.arrow(560, y - 5, 610, y - 5, LINE, 2)
        s.text(640, y, name, 17, TEXT, "bold")
    s.icon(T["servo"], 1060, 150, 48)
    s.text(70, 480, "Two small lang files in this jar: all of Numina's plus the keys its code uses, and only the keys Powersuits lacks. Nothing of theirs is overridden.", 14, MUTED)
    s.save("names")


# ------------------------------------------------------------------ 7 emi

def emi():
    s = Svg(1200, 560)
    s.header("Tinkers' Construct recipes in EMI", "EMI's JEI layer stopped at an older JEI API. Tinkers calls the newer methods, so every category showed \"Error rendering\".")
    # before
    s.card(40, 130, 540, 400, "before", BAD)
    s.rect(70, 200, 480, 280, INSET, BAD, r=8)
    s.text(310, 250, "Error rendering", 26, BAD, "bold", "middle")
    s.text(310, 284, "java.lang.AbstractMethodError", 14, MUTED, anchor="middle", family=MONO)
    s.text(310, 306, "JemiTooltipBuilder.getLines()", 14, MUTED, anchor="middle", family=MONO)
    s.text(310, 328, "JemiRecipeExtrasBuilder.addDrawableWidget(...)", 14, MUTED, anchor="middle", family=MONO)
    s.text(310, 372, "Melting, casting, alloying, part builder,", 15, MUTED, anchor="middle")
    s.text(310, 396, "modifiers: every Tinkers category, and", 15, MUTED, anchor="middle")
    s.text(310, 420, "entity melting and severing were empty.", 15, MUTED, anchor="middle")
    # after: an abstract melting recipe
    s.card(620, 130, 540, 400, "now", OK)
    s.rect(650, 200, 480, 280, INSET, OK, r=8)
    s.text(890, 236, "Melting", 18, TEXT, "bold", "middle")
    s.rect(690, 270, 56, 56, "#1c1c1c", "#6a6a6a", r=4)
    s.rect(700, 280, 36, 36, "#8d8d8d", r=2)
    s.arrow(770, 298, 860, 298, ACCENT, 3)
    s.rect(890, 262, 40, 72, "#1c1c1c", "#6a6a6a", r=4)
    s.rect(896, 276, 28, 52, "#d9742b", r=2)
    s.rect(960, 270, 150, 56, PANEL, LINE, r=4)
    s.text(970, 292, "Molten Iron", 13, TEXT, "bold")
    s.text(970, 312, "1 Ingot", 13, MUTED)
    s.text(690, 372, "Temperature: 1000", 14, MUTED)
    s.text(690, 396, "Time: 2 s", 14, MUTED)
    s.text(890, 452, "Arrows animate, fluids show Tinkers' ingot and nugget lines,", 14, MUTED, anchor="middle")
    s.text(890, 472, "entity melting and severing have their recipes back.", 14, MUTED, anchor="middle")
    s.save("tinkers-in-emi")


# ------------------------------------------------------------------ 8 config

def config():
    s = Svg(1200, 520)
    s.header("Server settings to re-tune", "Two fixes change what Powersuits' numbers mean. Apply these in powersuits-server.toml (every world, and defaultconfigs).")
    s.card(40, 130, 1120, 360, "powersuits-server.toml", GOLD)
    heads = ("Key", "Was", "Set to", "Why")
    xs = (70, 640, 750, 860)
    for x, h in zip(xs, heads):
        s.text(x, 200, h.upper(), 13, MUTED, "bold", spacing=1)
    s.line(70, 212, 1130, 212, LINE, 1)
    rows = (
        ("[jetpack] jetpackThrust_thrust_multiplier", "0.16", "0.32", "the suit ticks once now (F-3)"),
        ("[jetpack] energyCon_thrust_multiplier", "15000", "2500", "about 16,000 FE/s at full thrust"),
        ("[jet_boots] jetbootsThrust_thrust_multiplier", "0.08", "0.16", "same as the jetpack"),
        ("[generator_kinetic] base_energyPerBlock", "2000", "250", "energy is per real block now (F-6)"),
        ("[generator_kinetic] energyPerBlock_energyGenerated_multiplier", "6000", "750", "about 5,600 FE/s sprinting, like solar"),
    )
    for k, (key, was, now, why) in enumerate(rows):
        y = 246 + k * 40
        s.text(70, y, key, 13, TEXT, family=MONO)
        s.text(640, y, was, 15, BAD, family=MONO)
        s.text(750, y, now, 15, OK, "bold", family=MONO)
        s.text(860, y, why, 13, MUTED)
    s.text(70, 462, "BSP-Mod Fixes' own file, bsp_mod_fixes-server.toml, has one switch: suitClearsFire (true keeps Powersuits' fire immunity).", 14, MUTED)
    s.save("server-settings")


# ------------------------------------------------------------------ avatar (400 x 400, the family's look: dark stripes, three red daggers, a white symbol, a barcode and the name)

def avatar():
    s = Svg(400, 400, "#1f1f1f")
    s.raw('<defs><linearGradient id="red" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff2b2b"/><stop offset="1" stop-color="#b30f14"/></linearGradient></defs>')
    s.raw('<g stroke="#262626" stroke-width="22">' + "".join(f'<line x1="{i}" y1="-20" x2="{i - 440}" y2="420"/>' for i in range(-40, 900, 90)) + '</g>')
    # daggers: blade down, converging on the symbol
    for cx, ang in ((110, -32), (200, 0), (290, 32)):
        s.raw(f'<g transform="translate({cx} 40) rotate({ang})">'
              '<polygon points="0,78 -9,42 -9,10 9,10 9,42" fill="url(#red)"/>'
              '<rect x="-16" y="4" width="32" height="7" rx="2" fill="#d31b1b"/>'
              '<rect x="-5" y="-26" width="10" height="30" rx="3" fill="#b01013"/>'
              '<rect x="-7" y="-32" width="14" height="8" rx="3" fill="#d31b1b"/>'
              '</g>')
        # sparks at the tip
        tx, ty = cx + 78 * math.sin(math.radians(ang)), 40 + 78 * math.cos(math.radians(ang))
        for d in (-50, -15, 20, 55):
            a = math.radians(ang + 90 + d)
            s.line(tx + 8 * math.cos(a), ty + 8 * math.sin(a), tx + 16 * math.cos(a), ty + 16 * math.sin(a), "#ffffff", 3)
    # the symbol: a chestplate outline with a bolt, and a wrench across it
    s.raw('<g fill="none" stroke="#ffffff" stroke-width="13" stroke-linejoin="round" stroke-linecap="round">'
          '<path d="M118 130 L158 118 Q200 136 242 118 L282 130 L282 258 Q200 284 118 258 Z"/>'
          '</g>')
    s.raw('<polygon points="208,150 176,206 200,206 190,246 226,186 202,186 214,150" fill="#ffffff"/>')
    s.raw('<g transform="translate(276 250) rotate(45)">'
          '<rect x="-9" y="-62" width="18" height="100" rx="5" fill="#ffffff"/>'
          '<path d="M-26 -62 L26 -62 L26 -92 L12 -78 L-12 -78 L-26 -92 Z" fill="#ffffff"/>'
          '<circle cx="0" cy="44" r="16" fill="#ffffff"/><circle cx="0" cy="44" r="6" fill="#1f1f1f"/>'
          '</g>')
    # barcode
    import random
    rnd = random.Random(42)
    x = 66
    while x < 334:
        w = rnd.choice((3, 3, 5, 7, 9))
        s.rect(x, 300, w, 32, "#ffffff", r=0)
        x += w + rnd.choice((3, 4, 6))
    s.text(200, 356, "M O D     F I X E S", 15, "#ffffff", family=SANS, anchor="middle", spacing=3, preserve=True)
    s.save("avatar", AVATAR)
    (OUT / "avatar.svg").unlink(missing_ok=True)
    subprocess.run(["sips", "-z", "400", "400", str(AVATAR)], check=True, capture_output=True)  # the family's avatars are 400 x 400


if __name__ == "__main__":
    banner()
    patches()
    security()
    ticks()
    modules()
    names()
    emi()
    config()
    avatar()
