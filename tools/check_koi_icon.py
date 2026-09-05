# -*- coding: utf-8 -*-
"""§icon-topup: on 26.x a fish is drawn entirely from its own stack, so an unstamped one is blank.

    py -X utf8 tools/check_koi_icon.py <path-to-the-26.x-worktree>

The koi sprite is greyscale on purpose: one drawing, seventeen varieties, and the colour arrives as four
numbers in custom_model_data that FishItem.stampIcon writes. Nothing about that is visible to the
compiler, and every part of it fails silently and identically — as a white fish. So four things:

  1. FishItem tops the colours up in inventoryTick, guarded, for every stack that did not come from
     create() — a /give, a command block, a datapack, a world older than §koi-genes.
  2. the item definition asks for exactly as many tints as KOI_PAINT paints layers. One too few and the
     last layer renders untinted; one too many is harmless but means somebody miscounted.
  3. every variety Genome can NAME has paint. koiTint falls back to kohaku for one that does not, so a
     new variety without an entry is a white-and-red fish that looks deliberate.
  4. no variety's GROUND is -1. Ground is what every unworn layer borrows, and -1 borrowed from itself
     is white — the exact bug this file exists for, one map entry away.

1.21.1 and 1.20.1 compute the tint at draw time in FishTint.itemColor and need none of this; run it
against the 26.x worktree and it says so rather than pretending otherwise.
"""
import io, json, os, re, sys

if len(sys.argv) < 2:
    print("usage: py -X utf8 tools/check_koi_icon.py <path-to-the-26.x-worktree>")
    sys.exit(2)

ROOT = sys.argv[1]
FISH = os.path.join(ROOT, "common/src/main/java/com/riverfishing/item/FishItem.java")
MORPH = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/FishMorph.java")
GENOME = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/Genome.java")
DEF = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/items/koi_carp.json")

if not os.path.exists(FISH) or "stampIcon" not in io.open(FISH, encoding="utf-8").read():
    print("this tree has no stampIcon, so the icon is not stack-driven here — nothing to check")
    sys.exit(0)

fails = []
fish = io.open(FISH, encoding="utf-8").read()
morph = io.open(MORPH, encoding="utf-8").read()
genome = io.open(GENOME, encoding="utf-8").read()

# 1. the top-up, and its guard
tick = re.search(r"public void inventoryTick\(.*?\n    \}", fish, re.S)
if not tick:
    fails.append("FishItem does not override inventoryTick — a fish from /give, from a datapack, or "
                 "from a world older than §koi-genes carries no colours, and 26.x then draws it as the "
                 "raw sprite. For a koi that is a blank white fish.")
else:
    body = tick.group(0)
    if "stampIcon" not in body:
        fails.append("FishItem.inventoryTick no longer stamps the icon")
    if "colors()" not in body and "CUSTOM_MODEL_DATA" not in body:
        fails.append("FishItem.inventoryTick stamps UNCONDITIONALLY — that is stampIcon on every fish "
                     "in every inventory every tick. Guard it on the colours being missing.")

# 2. paint, read out of the Java
PAINT = {m.group(1): [x for x in re.findall(r"-1|0x[0-9A-Fa-f]+", m.group(2))]
         for m in re.finditer(r'Map\.entry\("(\w+)",\s*new int\[\]\{([^}]*)\}', morph)}
if not PAINT:
    fails.append("KOI_PAINT could not be read out of FishMorph.java")
width = {len(v) for v in PAINT.values()}
if len(width) > 1:
    fails.append("KOI_PAINT rows are not all the same width: %s" % sorted(width))
layers = width.pop() if len(width) == 1 else 4

# 3. every variety the table can name has paint
table = re.search(r"KOI_TABLE = \{(.*?)\};", genome, re.S)
named = sorted({r.split("=")[1] for r in re.findall(r'"([\w*_]+=\w+)"', table.group(1))}) if table else []
for v in named:
    if v not in PAINT:
        fails.append("Genome names the variety %r but KOI_PAINT has no colours for it — koiTint falls "
                     "back to kohaku, so it is a white-and-red fish that looks intentional" % v)

# 4. a ground of -1 borrows white from itself
for v, row in sorted(PAINT.items()):
    if row and row[0] == "-1":
        fails.append("%s has a GROUND of -1. Every layer it does not wear borrows the ground, and a "
                     "borrowed -1 is white — that is the blank fish." % v)

# 5. the item definition asks for one tint per painted layer
if os.path.exists(DEF):
    d = json.load(io.open(DEF, encoding="utf-8"))
    counts = set()

    def walk(n):
        if not isinstance(n, dict):
            return
        if "tints" in n:
            counts.add(len(n["tints"]))
        for k in ("entries", "cases"):
            for e in n.get(k, []):
                walk(e.get("model", {}))
        for k in ("fallback", "model"):
            if isinstance(n.get(k), dict):
                walk(n[k])

    walk(d.get("model", {}))
    for c in sorted(counts):
        if c != layers:
            fails.append("items/koi_carp.json has a model with %d tints but KOI_PAINT paints %d layers "
                         "— the unmatched layers render untinted (white)" % (c, layers))
else:
    fails.append("no items/koi_carp.json")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("koi icon: %d varieties painted over %d layers, all named ones covered, and an unstamped fish "
      "tops itself up" % (len(PAINT), layers))
