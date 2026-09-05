# -*- coding: utf-8 -*-
"""§fish-oil-potion: the brewed numbers, checked against the patchnote instead of against nothing.

    py -X utf8 tools/check_fish_oil_potion.py

A potion's durations are four integers buried in a registry class. Nobody notices when one of them drifts
away from the sentence in docs/patchnotes/0.9.0.md that promises it — the game still brews, the bottle still
works, the player just gets a minute where the patchnote said ninety seconds. So the numbers are read back
OUT of ModPotions.java here and compared with the table the design fixed:

    variant   dolphin's grace / water breathing   regeneration    resistance
    base      1:30 (1800t)                        I,  0:45 (900)  I,  1:30 (1800)
    strong    0:45 (900t)   glowstone halves      II, 0:22 (450)  II, 0:45 (900)
    long      4:00 (4800t)  redstone, WATER only  I,  0:45 (900)  I,  1:30 (1800)

Also checks the three brewing mixes and the marker effect that carries the Mining-Fatigue cure, because a
potion registered without them is a potion nobody can make. Exit code is non-zero on the first mismatch, so
this can gate a release next to tools/check_lang.py.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POTIONS = os.path.join(ROOT, "common", "src", "main", "java", "com", "riverfishing", "registry", "ModPotions.java")
EFFECT = os.path.join(ROOT, "common", "src", "main", "java", "com", "riverfishing", "effect", "FishOilEffect.java")
LANG = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "riverfishing", "lang")

# The spec, in ticks: (swim, mend, mend amplifier, brace, brace amplifier).
SPEC = {
    "fish_oil":        (1800, 900, 0, 1800, 0),
    "strong_fish_oil": (900,  450, 1,  900, 1),
    "long_fish_oil":   (4800, 900, 0, 1800, 0),
}

# Ingredient -> (potion brewed from, potion brewed into). Water bottle + fish oil is the entry to the chain.
MIXES = [
    ("Potions.WATER", "ModItems.FISH_OIL", "FISH_OIL"),
    ("FISH_OIL", "Items.GLOWSTONE_DUST", "STRONG_FISH_OIL"),
    ("FISH_OIL", "Items.REDSTONE", "LONG_FISH_OIL"),
]

LANG_KEYS = [
    "effect.riverfishing.fish_oil",
    "item.minecraft.potion.effect.fish_oil",
    "item.minecraft.splash_potion.effect.fish_oil",
    "item.minecraft.lingering_potion.effect.fish_oil",
    "item.minecraft.tipped_arrow.effect.fish_oil",
]

CONST = re.compile(r"static\s+final\s+int\s+([A-Z_]+)\s*=\s*(\d+)\s*;")
# REGISTER.register("<id>", () -> oil(SWIM, MEND, ampl, BRACE, ampl))  — constants or bare numbers, either way.
ENTRY = re.compile(
    r'REGISTER\.register\(\s*"([a-z_]+)"\s*,\s*\(\)\s*->\s*oil\(\s*'
    r'([A-Za-z0-9_]+)\s*,\s*([A-Za-z0-9_]+)\s*,\s*([A-Za-z0-9_]+)\s*,\s*'
    r'([A-Za-z0-9_]+)\s*,\s*([A-Za-z0-9_]+)\s*\)'
)
MIX = re.compile(r"builder\.addMix\(\s*([A-Za-z0-9_.()]+)\s*,\s*([A-Za-z0-9_.()]+)\s*,\s*([A-Za-z0-9_.()]+)\s*\)")

fails = []


def fail(msg):
    fails.append(msg)


def read(path):
    if not os.path.exists(path):
        fail("missing file: " + os.path.relpath(path, ROOT))
        return ""
    return io.open(path, encoding="utf-8").read()


def mmss(ticks):
    return "%d:%02d" % (ticks // 1200, (ticks % 1200) // 20)


def main():
    src = read(POTIONS)
    consts = {m.group(1): int(m.group(2)) for m in CONST.finditer(src)}

    def value(token):
        """A constant name or a literal — either is a number the game will actually use."""
        if token.isdigit():
            return int(token)
        if token in consts:
            return consts[token]
        fail("ModPotions: %r is neither a literal nor a constant declared in the file" % token)
        return None

    found = {}
    for m in ENTRY.finditer(src):
        found[m.group(1)] = tuple(value(t) for t in m.groups()[1:])

    for pid, want in SPEC.items():
        got = found.get(pid)
        if got is None:
            fail("ModPotions: no potion registered as %r" % pid)
            continue
        if got != want:
            fail("%s: swim/mend/mendAmp/brace/braceAmp = %s, spec says %s" % (pid, got, want))
        else:
            print("  ok  %-16s swim %s · regen %s %s · resistance %s %s"
                  % (pid, mmss(got[0]), mmss(got[1]), "I" * (got[2] + 1), mmss(got[3]), "I" * (got[4] + 1)))

    # The strong bottle is glowstone's: level II on the two that have levels, every duration halved.
    base, strong = found.get("fish_oil"), found.get("strong_fish_oil")
    if base and strong:
        if (strong[2], strong[4]) != (base[2] + 1, base[4] + 1):
            fail("strong_fish_oil: glowstone must raise regeneration AND resistance by one level")
        for i, what in ((0, "swim"), (1, "regeneration"), (3, "resistance")):
            if strong[i] * 2 != base[i]:
                fail("strong_fish_oil: %s is %d ticks, half of the base %d would be %d"
                     % (what, strong[i], base[i], base[i] // 2))

    # The long bottle is redstone's: the WATER effects stretch, nothing else moves.
    lng = found.get("long_fish_oil")
    if base and lng:
        if lng[0] <= base[0]:
            fail("long_fish_oil: redstone must stretch the water effects past the base %d ticks" % base[0])
        if lng[1:] != base[1:]:
            fail("long_fish_oil: only the water effects stretch — regeneration/resistance must match the base")

    # Same recipe in every dialect, spelled three ways: 1.20.1 hands the mix table raw Potions
    # (`FISH_OIL.get()`), 1.21 hands it the RegistrySupplier itself, and 26.2's Architectury 21 wants
    # `holder(FISH_OIL)` because a supplier stopped being a Holder there. Compare names, not spellings.
    def token(t):
        t = t.replace(".get()", "")
        return t[len("holder("):-1] if t.startswith("holder(") and t.endswith(")") else t

    mixes = [tuple(token(t) for t in m) for m in MIX.findall(src)]
    for want in MIXES:
        if want not in mixes:
            fail("ModPotions.addMixes: missing addMix%s" % (want,))
    if "addContainerRecipe" in src:
        fail("ModPotions: splash/lingering/tipped arrows are vanilla container mixes — no code needed here")

    eff = read(EFFECT)
    if "removeEffect" not in eff or "DIG_SLOWDOWN" not in eff.replace("MINING_FATIGUE", "DIG_SLOWDOWN"):
        fail("FishOilEffect: nothing here clears Mining Fatigue")
    if "MobEffects.FISH_OIL" in src or "ModEffects.FISH_OIL" not in src:
        fail("ModPotions: the bottle must carry the mod's own fish-oil marker effect")

    for loc in ("en_us", "ru_ru", "uk_ua"):
        text = read(os.path.join(LANG, loc + ".json"))
        for key in LANG_KEYS:
            if '"%s"' % key not in text:
                fail("%s.json: no name for %s — it would render as the raw key" % (loc, key))

    if fails:
        print()
        for f in fails:
            print("FAIL  " + f)
        return 1
    print("\nfish oil potion: %d variants, %d mixes, %d names x 3 locales — all match the spec"
          % (len(SPEC), len(MIXES), len(LANG_KEYS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
