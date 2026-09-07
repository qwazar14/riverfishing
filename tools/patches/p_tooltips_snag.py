# -*- coding: utf-8 -*-
"""§line-name §card-group §hardcore-snag: three small ones.
  - the line's tooltip and the journal's tackle row printed `linetype.riverfishing.mono` / `item.riverfishing.line_mono`
    (keys that never existed); both go through linetype.riverfishing.<type>, added in three languages.
  - the catch card named the group through card.riverfishing.group.<x>, which the six groups the table brought never
    had; it now uses FishGroup.nameKey, the journal's own vocabulary, so one namespace names a family everywhere.
  - the hardcore preset's snag multiplier 1.6 -> 1.3 (16 % of casts snagged was the complaint).
Idempotent; run on each tree.  py tools/patches/p_tooltips_snag.py"""
import io, json, os, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
LANG = "common/src/main/resources/assets/riverfishing/lang"
LINETYPE = {
    "en_us": {"mono": "Monofilament", "fluoro": "Fluorocarbon", "braid": "Braid"},
    "ru_ru": {"mono": "Монофил", "fluoro": "Флюорокарбон", "braid": "Плетёнка"},
    "uk_ua": {"mono": "Монофіл", "fluoro": "Флюорокарбон", "braid": "Плетінка"},
}


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert old in s, what + " @ " + path
    wr(path, s.replace(old, new, 1))


def run(tree):
    j = lambda *a: os.path.join(tree, *a)
    # the journal's tackle row
    sub(j("common/src/main/java/com/riverfishing/client/JournalScreen.java"),
        'Component.translatable("item.riverfishing.line_" + c.lineType()).getString()',
        'Component.translatable("linetype.riverfishing." + c.lineType()).getString()   // §line-name', "journal tackle")
    # the catch card's group row (two dialects of the NBT getter)
    fc = j("common/src/main/java/com/riverfishing/client/FishCardClientTooltip.java")
    for getter in ('c.getString("Group")', 'c.getStringOr("Group", "")'):
        old = 'row("group", key("group." + %s), GREEN);' % getter
        new = 'row("group", Component.translatable(com.riverfishing.fish.FishGroup.nameKey(%s)), GREEN);   // §card-group' % getter
        if old in rd(fc): sub(fc, old, new, "card group")
    assert "§card-group" in rd(fc), "card group anchor @ " + fc
    # the preset
    sub(j("common/src/main/java/com/riverfishing/config/RiverFishingConfig.java"),
        "public static double snagChance() { return byPreset(0.3, 1.0, 1.6, snag); }",
        "public static double snagChance() { return byPreset(0.3, 1.0, 1.3, snag); }   // §hardcore-snag: 1.6 was a snag every sixth cast", "snag preset")
    # the lang keys, after tooltip.riverfishing.line_spec
    for code, names in LINETYPE.items():
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        if "linetype.riverfishing.mono" in d: continue
        out = OrderedDict()
        for k, v in d.items():
            out[k] = v
            if k == "tooltip.riverfishing.line_spec":
                for t, n in names.items(): out["linetype.riverfishing." + t] = n
        wr(p, json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    print("  patched", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES): run(t)
