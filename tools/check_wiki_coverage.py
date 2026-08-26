# -*- coding: utf-8 -*-
"""Which items and blocks does the wiki never mention?

    python tools/check_wiki_coverage.py            (summary + the gaps)
    python tools/check_wiki_coverage.py --all      (every entry, covered or not)

The registry is taken from the LANG FILE, not from the Java: rods, reels, rigs, lines, hooks and fish are
registered in loops with computed ids, so parsing ModItems misses most of them. Every player-visible thing
must have an `item.riverfishing.<id>` or `block.riverfishing.<id>` key to have a name at all, which makes
en_us.json the only complete and authoritative list.

The wiki refers to things by their DISPLAY NAME, never by id, so each entry is searched for by its own
localised name in its own language tree: en names in docs/wiki/*.md, ru names in docs/wiki/ru/*.md, uk in
docs/wiki/uk/*.md. An entry documented in English but missing from the Russian pages is a real gap for a
Russian reader, so the three languages are reported separately.

Matching is deliberately conservative:
  * whole-word, case-insensitive, so "Float" does not match "Floating";
  * SHORT or ambiguous names (a name that is a substring of another entry's name) are flagged, because a
    hit on "Hook" tells you nothing about whether hooks are documented.
Fish species are counted apart from gear: they live in one big generated table, so "documented" means
something different for them.
"""
import io, json, os, re, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(REPO, "common/src/main/resources/assets/riverfishing")
WIKI = os.path.join(REPO, "docs/wiki")
LANGS = [("en_us", ""), ("ru_ru", "ru"), ("uk_ua", "uk")]
SHOW_ALL = "--all" in sys.argv


def wiki_text(sub):
    """All markdown of one language tree, concatenated."""
    d = os.path.join(WIKI, sub) if sub else WIKI
    out = []
    for f in sorted(os.listdir(d)):
        if f.endswith(".md"):
            out.append(io.open(os.path.join(d, f), encoding="utf-8").read())
    return "\n".join(out)


def main():
    lang = {code: json.load(io.open(os.path.join(A, "lang", code + ".json"), encoding="utf-8"))
            for code, _ in LANGS}
    en = lang["en_us"]

    fish = set()
    m = re.search(r"FISH_SPECIES = \{(.*?)\};",
                  io.open(os.path.join(REPO, "common/src/main/java/com/riverfishing/registry/ModItems.java"),
                          encoding="utf-8").read(), re.S)
    if m:
        fish = set(re.findall(r'"([a-z0-9_]+)"', m.group(1)))

    # id -> kind, from the key namespace. A few ids are both an item and a block (ice_hole); block wins.
    entries = {}
    for key in en:
        mm = re.match(r"^(item|block)\.riverfishing\.([a-z0-9_]+)$", key)
        if not mm:
            continue
        kind, i = mm.group(1), mm.group(2)
        if i in fish:
            kind = "fish"
        entries[i] = "block" if entries.get(i) == "block" or kind == "block" else kind

    texts = {sub: wiki_text(sub) for _, sub in LANGS}
    names = {}
    for i in entries:
        names[i] = {}
        for code, sub in LANGS:
            key = ("block." if entries[i] == "block" else "item.") + "riverfishing." + i
            names[i][sub] = lang[code].get(key) or lang[code].get("item.riverfishing." + i) \
                or lang[code].get("block.riverfishing." + i)

    # A name that is contained in another entry's name cannot be matched reliably on its own.
    ambiguous = set()
    for i in entries:
        for j in entries:
            if i == j:
                continue
            a, b = names[i][""], names[j][""]
            if a and b and len(a) < len(b) and re.search(r"\b" + re.escape(a) + r"\b", b, re.I):
                ambiguous.add(i)

    rows = []
    for i in sorted(entries):
        hits = {}
        for _, sub in LANGS:
            n = names[i][sub]
            hits[sub] = len(re.findall(r"(?<![\w])" + re.escape(n) + r"(?![\w])", texts[sub], re.I)) if n else -1
        rows.append((i, entries[i], hits))

    def report(kind, title):
        sel = [r for r in rows if r[1] == kind]
        missing = [r for r in sel if all(v <= 0 for v in r[2].values())]
        partial = [r for r in sel if r not in missing and any(v <= 0 for v in r[2].values())]
        print("\n== %s: %d entries — %d documented nowhere, %d missing in some language"
              % (title, len(sel), len(missing), len(partial)))
        for label, group in (("NOWHERE", missing), ("PARTIAL", partial)):
            for i, _, h in group:
                flag = "  (short/ambiguous name — check by hand)" if i in ambiguous else ""
                print("   %-8s %-26s en=%-3s ru=%-3s uk=%-3s  %s%s"
                      % (label, i, h[""], h["ru"], h["uk"], names[i][""], flag))
        if SHOW_ALL:
            for i, _, h in sel:
                if (i, kind, h) not in missing and (i, kind, h) not in partial:
                    print("   ok       %-26s en=%-3s ru=%-3s uk=%-3s  %s"
                          % (i, h[""], h["ru"], h["uk"], names[i][""]))

    print("registry from en_us.json: %d items, %d blocks, %d fish"
          % (sum(1 for r in rows if r[1] == "item"),
             sum(1 for r in rows if r[1] == "block"),
             sum(1 for r in rows if r[1] == "fish")))
    report("block", "BLOCKS")
    report("item", "ITEMS")
    report("fish", "FISH")
    return 0


if __name__ == "__main__":
    sys.exit(main())
