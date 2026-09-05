# -*- coding: utf-8 -*-
"""§fish-farming: five guide pages for 0.9.0, and the shelf group they sit under.

    py -X utf8 tools/patches/p_guidepages.py <root> <fragments-dir>

<fragments-dir> holds one folder per page — stocking, breeding, genes, upgrades, geography — each with
en_us.json / ru_ru.json / uk_ua.json carrying the page's title, text and table. They were written by
five agents against the code, every number traced; this script is the merge they were told not to do.

What it does, and why each half is here rather than in the fragments:

  lang   — every bare % is doubled on the way in. The guide shelf renders through I18n.get, which is
           String.format, and a bare % there replaces the whole page with "Format error:". One of the
           five writers concluded, wrongly, that the literal % in its table was safe; it was reasoning
           from the Component.translatable path, which is not the path a guide takes.
  shelf  — a new group 5, "Fish farming", between "Reading the water" and "Under the ice": stocking,
           breeding, genes, upgrades, in the order a player meets them (you release a pair before you
           build a tank; the genes only mean something once you have a tank). Ice, sea and admin move
           to 6, 7, 8 — in JournalScreen and in the three lang files together, because the group name
           is looked up by number and a shelf with the wrong heading over it is worse than no heading.
  geography — goes into group 4 straight after `community`: same instrument, one zoom level out.
"""
import io, json, os, re, sys

ROOT, FRAG = sys.argv[1], sys.argv[2]
LANG = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang")
JS = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/JournalScreen.java")
LOCALES = ("en_us", "ru_ru", "uk_ua")
PAGES = ("stocking", "breeding", "genes", "upgrades", "geography")
GROUP5 = {"en_us": "Fish farming", "ru_ru": "Рыбоводство", "uk_ua": "Рибництво"}

# the one place a fragment reads badly: RU geography said "и для блока это навсегда" for "fixed forever"
FIXUPS = {("geography", "ru_ru", "text"): [("и для блока это навсегда", "и это навсегда")]}

ALLOWED = re.compile(r"%(?:%|(?:\d+\$)?s)")


def double_percents(v):
    """Every % that is not already %%, %s or %n$s becomes %% — what String.format wants for a literal."""
    out, i = [], 0
    while i < len(v):
        m = ALLOWED.match(v, i)
        if m:
            out.append(m.group(0)); i = m.end()
        elif v[i] == "%":
            out.append("%%"); i += 1
        else:
            out.append(v[i]); i += 1
    return "".join(out)


def dump(d):
    return json.dumps(d, ensure_ascii=False, indent=2) + "\n"


# ---- 1. the lang files ---------------------------------------------------------------------------
for loc in LOCALES:
    p = os.path.join(LANG, loc + ".json")
    raw = io.open(p, encoding="utf-8").read()
    d = json.load(io.open(p, encoding="utf-8"))
    if "guide.riverfishing.stocking.title" in d and "guidegroup.riverfishing.8" in d:
        print("  %s: already patched" % loc)
        continue
    assert dump(d) == raw, "%s is not json.dumps(indent=2) as it stands — merge by hand" % loc

    new = {}
    for page in PAGES:
        f = json.load(io.open(os.path.join(FRAG, page, loc + ".json"), encoding="utf-8"))
        for suf in ("title", "text", "table"):
            k = "guide.riverfishing.%s.%s" % (page, suf)
            if k not in f:
                continue
            v = f[k]
            for old, rep in FIXUPS.get((page, loc, suf), []):
                assert old in v, "fixup target missing in %s %s" % (page, loc)
                v = v.replace(old, rep)
            new[k] = double_percents(v)
    assert len(new) == 15, "%s: expected 15 keys from five pages, got %d" % (loc, len(new))

    out = {}
    last_guide = max(i for i, k in enumerate(d) if k.startswith("guide.riverfishing."))
    for i, (k, v) in enumerate(d.items()):
        # groups 5/6/7 become 6/7/8; the new 5 goes in where 5 was
        if k == "guidegroup.riverfishing.5":
            out["guidegroup.riverfishing.5"] = GROUP5[loc]
            out["guidegroup.riverfishing.6"] = v
        elif k == "guidegroup.riverfishing.6":
            out["guidegroup.riverfishing.7"] = v
        elif k == "guidegroup.riverfishing.7":
            out["guidegroup.riverfishing.8"] = v
        else:
            out[k] = v
        if i == last_guide:
            out.update(new)
    assert "guidegroup.riverfishing.8" in out and "guide.riverfishing.genes.text" in out
    io.open(p, "w", encoding="utf-8", newline="\n").write(dump(out))
    print("  %s: +15 guide keys, groups 5-7 -> 6-8, group 5 = %s" % (loc, GROUP5[loc]))

# ---- 2. the shelf ---------------------------------------------------------------------------------
s = io.open(JS, encoding="utf-8").read()
if "fish-farming" in s:
    print("  JournalScreen: already patched")
    sys.exit(0)

old = """        addGuide("community", modStack("fish_finder"));
        addGuide("nets", modStack("seine_net")); // §D"""
assert old in s, "group 4 moved"
s = s.replace(old, """        addGuide("community", modStack("fish_finder"));
        addGuide("geography", new ItemStack(net.minecraft.world.item.Items.FILLED_MAP));   // §provinces
        addGuide("nets", modStack("seine_net")); // §D""", 1)

old = """        guideGroupNow = 5;   // under the ice — quest stage 6, and until now the only mode with no page
        addGuide("icefishing", modStack("ice_auger"));

        guideGroupNow = 6;   // the sea, and the fish that need a boat"""
assert old in s, "groups 5/6 moved"
s = s.replace(old, """        guideGroupNow = 5;   // §fish-farming: water of your own — in the order a player meets it
        addGuide("stocking", modStack("pond_sign"));
        addGuide("breeding", modStack("roe"));
        addGuide("genes", modStack("koi_carp"));
        addGuide("upgrades", modStack("aerator"));

        guideGroupNow = 6;   // under the ice — quest stage 6, and until now the only mode with no page
        addGuide("icefishing", modStack("ice_auger"));

        guideGroupNow = 7;   // the sea, and the fish that need a boat""", 1)

old = "        guideGroupNow = 7;   // not for anglers:"
assert old in s, "group 7 moved"
s = s.replace(old, "        guideGroupNow = 8;   // not for anglers:", 1)

io.open(JS, "w", encoding="utf-8", newline="\n").write(s)
print("  JournalScreen: geography after community; group 5 = stocking, breeding, genes, upgrades; 5-7 -> 6-8")
print("done")
