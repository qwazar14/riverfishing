# -*- coding: utf-8 -*-
"""The guide shelf: every page JournalScreen puts on it exists in three languages, and so does its group.

    py -X utf8 tools/check_guide_shelf.py [root]

Three things that fail silently in-game and that no compiler sees:

  1. addGuide("x", …) with no guide.riverfishing.x.title/.text in a locale renders the raw key on the
     shelf. Every page needs both in all three languages; a .table in one language needs it in all.
  2. guideGroupNow = N with no guidegroup.riverfishing.N renders the raw key as the shelf heading — and
     an N in the lang files that no page uses is a heading nobody will ever see. The numbers are
     renumbered by hand when a group is inserted (§fish-farming did exactly that), which is how the two
     drift.
  3. modStack("id") with no such item is an empty stack — a blank slot on the shelf with a title under
     it. The item's own lang key is the cheapest proof it exists.

And two shapes the three languages must agree on, because the renderer splits on them: the number of
`## ` headings in a page's text, and the row-by-cell shape of its table.
"""
import io, json, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang")
JS = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/JournalScreen.java")
LOCALES = ("en_us", "ru_ru", "uk_ua")

fails = []
src = io.open(JS, encoding="utf-8").read()
lang = {loc: json.load(io.open(os.path.join(LANG, loc + ".json"), encoding="utf-8")) for loc in LOCALES}

# walk the addGuide block in order, tracking the group each page lands in
pages, groups_used, group = [], set(), None
for m in re.finditer(r'guideGroupNow = (\d+);|addGuide\("(\w+)",\s*(modStack\("(\w+)"\)|new ItemStack\([^)]*\))', src):
    if m.group(1):
        group = int(m.group(1)); groups_used.add(group)
    else:
        pages.append((m.group(2), group, m.group(4)))
if not pages:
    fails.append("no addGuide(...) calls found in JournalScreen")

for pid, g, item in pages:
    for loc in LOCALES:
        d = lang[loc]
        for suf in ("title", "text"):
            if "guide.riverfishing.%s.%s" % (pid, suf) not in d:
                fails.append("%s: page %r has no .%s — the shelf shows the raw key" % (loc, pid, suf))
    has_table = [loc for loc in LOCALES if "guide.riverfishing.%s.table" % pid in lang[loc]]
    if has_table and len(has_table) != len(LOCALES):
        fails.append("page %r has a table in %s only" % (pid, has_table))
    if g is None:
        fails.append("page %r is added before any guideGroupNow" % pid)
    if item and not any(("%s.riverfishing.%s" % (kind, item)) in lang["en_us"] for kind in ("item", "block")):
        fails.append("page %r uses modStack(%r) but no item/block lang key exists for it — empty icon" % (pid, item))
    # shapes: headings and table
    heads = {loc: len(re.findall(r"^## ", lang[loc].get("guide.riverfishing.%s.text" % pid, ""), re.M)) for loc in LOCALES}
    if len(set(heads.values())) > 1:
        fails.append("page %r: heading count differs — %s" % (pid, heads))
    if has_table:
        shapes = {loc: [len(r.split("|")) for r in lang[loc]["guide.riverfishing.%s.table" % pid].split("\n")] for loc in LOCALES}
        if len({tuple(v) for v in shapes.values()}) > 1:
            fails.append("page %r: table shape differs — %s" % (pid, shapes))

for loc in LOCALES:
    d = lang[loc]
    have = {int(k.rsplit(".", 1)[1]) for k in d if k.startswith("guidegroup.riverfishing.")}
    for g in sorted(groups_used - have):
        fails.append("%s: guideGroupNow = %d is used but guidegroup.riverfishing.%d is missing — raw key as heading" % (loc, g, g))
    for g in sorted(have - groups_used):
        fails.append("%s: guidegroup.riverfishing.%d exists but no page sits under group %d" % (loc, g, g))

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("guide shelf: %d pages in %d groups, every page and heading present in %d locales, shapes agree"
      % (len(pages), len(groups_used), len(LOCALES)))
