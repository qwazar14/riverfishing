# -*- coding: utf-8 -*-
"""Add the species sprite to the markdown wiki's main species table, for the GitHub/VS Code view.

    python tools/gen_wiki_md_sprites.py        (from the repo root)

Idempotent: it does nothing if species.md already contains an <img>, so it is safe to re-run after
adding species. Only the ONE table that leads with a row number and an English name — the reference
tables repeat the same 70 species, and 70 more images each would make them worse, not clearer. Sized
with an <img width> (GitHub honours it) because the source art is 256px.

This is for the markdown view only. The bundled single-page wiki gets its own, richer illustration
from tools/gen_wiki_bundle.py — it does not read anything this writes.
"""
import io, json, re, sys

WIKI = "docs/wiki/species.md"
LANG = "common/src/main/resources/assets/riverfishing/lang/en_us.json"
REL = "../../common/src/main/resources/assets/riverfishing/textures/item/fish"

lang = json.load(io.open(LANG, encoding="utf-8"))
by_name = {}
for k, v in lang.items():
    if k.startswith("fish.riverfishing."):
        by_name.setdefault(v, k.rsplit(".", 1)[1])

src = io.open(WIKI, encoding="utf-8").read()
if "<img" in src:
    print("already illustrated — nothing to do")
    sys.exit(0)

# Rows of the main table: | 1 | Bream | `bream` | ...
ROW = re.compile(r"^(\|\s*\d+\s*\|\s*)([A-Za-z][A-Za-z '\-]*?)(\s*\|\s*`([a-z_0-9]+)`)", re.M)
hit = [0]


def sub(m):
    name, sid = m.group(2).strip(), m.group(4)
    if by_name.get(name) != sid:
        return m.group(0)          # name and id must agree, or we're in the wrong column
    hit[0] += 1
    img = '<img src="%s/%s.png" width="28" alt=""> ' % (REL, sid)
    return "%s%s%s%s" % (m.group(1), img, name, m.group(3))


out = ROW.sub(sub, src)
io.open(WIKI, "w", encoding="utf-8", newline="\n").write(out)
print("illustrated %d species rows in %s" % (hit[0], WIKI))
