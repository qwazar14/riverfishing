# -*- coding: utf-8 -*-
"""§feeder-fill: the feeding station's faces, and the four levels of feed you can see in them.

    py -X utf8 tools/gen_feeding_station.py                 # this tree
    py -X utf8 tools/gen_feeding_station.py <other-root>    # …and the other two worktrees

The station was one `cube_all` texture — the same plank-and-hopper pattern on all six faces, which is
why it read as a patterned cube instead of equipment, and why a full station and an empty one looked
exactly alike. It holds WaterUpgrades.MAX_CHARGES of groundbait and spends one a world day, and until
now the only way to know how much was left was to right-click it and read the message.

So it is a feed bin now: a plank box with iron straps, an iron-framed WINDOW on all four sides and an
open MOUTH on top, and the groundbait behind both is drawn at four heights. The window is 12x7 of a
16x16 face — nearly half the wall — because the state has to read from the far bank, not from arm's
length. Empty is a dark hole, full is a pale slab of meal, and the two middle steps sit visibly between
them. Nothing subtle.

All four walls are the same texture on purpose (the vanilla composter does the same): whichever way you
come at the pond the level faces you, and the block needs no facing property to manage it.

    feeding_station_side0..3.png   the walls; the window's 12x7 interior carries the level
    feeding_station_top0..3.png    the open mouth; the heap in it carries the same level
    feeding_station_bottom.png     planks and the outlet plate — it sits on the bank, nobody sees it

Every map below starts as OAK, which is `minecraft:block/oak_planks` transcribed texel for texel: the
recipe is planks around a hopper, so the wood IS oak plank and the metal IS hopper grey, and neither
had to be invented. The meal is the groundbait item's own cream. Levels come from
FeedingStationBlock.FILL (0..3); tools/patches/p_feeder.py holds the mapping from charges to level.
To restyle, edit the maps and re-run — nothing but the models reads these files.
"""
import io, json, os, re, struct, sys, zlib

# ---- the palette --------------------------------------------------------------------------------
# Wood: all seven tones of vanilla oak_planks.png. Iron: four of hopper_outside.png. Meal: the
# groundbait item's cream, plus one brown for the coarse grain in it.
C = {
    "+": (0xC2, 0x9D, 0x62),   # oak, lit
    ".": (0xB8, 0x94, 0x5F),   # oak
    ":": (0xAF, 0x8F, 0x55),   # oak
    ",": (0x9F, 0x84, 0x4D),   # oak
    ";": (0x96, 0x74, 0x41),   # oak
    "-": (0x7E, 0x62, 0x37),   # oak, seam
    "#": (0x67, 0x50, 0x2C),   # oak, seam dark
    "H": (0x67, 0x61, 0x61),   # iron, lit — the top strap and the rim's corner brackets
    "I": (0x59, 0x58, 0x58),   # iron — the window frame
    "i": (0x4F, 0x4F, 0x4F),   # iron — the floor of the bin, where the light from the lid lands
    "j": (0x3F, 0x3E, 0x42),   # iron, dark — the back wall
    "o": (0x2D, 0x2D, 0x32),   # the shaft: the darkest thing here, and still not black
    "s": (0xB3, 0xAE, 0x8B),   # meal, in shadow
    "f": (0xE0, 0xDB, 0xB8),   # meal
    "F": (0xFC, 0xF7, 0xD4),   # meal, catching the light
    "g": (0x8A, 0x6A, 0x42),   # a grain of the coarse stuff
}

# vanilla oak_planks.png, for reference — the maps below are this sheet with things stamped on it.
OAK = [
    ".:.+++++.+++++.;",
    "..::;,:...::..,,",
    ":...:.:,,,,::..;",
    ";--;;-##--#-##-#",
    ".+,++++,+++.::,.",
    ":..:,:,;,::....:",
    ",,:.:::;...::,,,",
    "##--;-####---;-#",
    ".++.::++++++++.,",
    ".:....:,,,:,:,,;",
    "+.::,,,,::::,,.,",
    "##-;;-###-;--###",
    "+,.++..,+++,+.++",
    "::..,,:,,..:.:::",
    ":,,:.:,;..:,,,,,",
    "#--##-;;;-#-####",
]

# ---- the wall: OAK, an iron strap top and bottom, and a window at cols 2..13, rows 5..11 ----------
SIDE = [
    ".:.+++++.+++++.;",
    "..::;,:...::..,,",
    ":...:.:,,,,::..;",
    ";--;;-##--#-##-#",
    ".oHHHHHHHHHHHHo.",       # the top strap, catching the light; a bolt at each end
    ":I@@@@@@@@@@@@I:",
    ",I@@@@@@@@@@@@I,",
    "#I@@@@@@@@@@@@I#",
    ".I@@@@@@@@@@@@I,",
    ".I@@@@@@@@@@@@I;",
    "+I@@@@@@@@@@@@I,",
    "#I@@@@@@@@@@@@I#",
    "+oIIIIIIIIIIIIo+",       # the bottom strap, in the wall's own shadow
    "::..,,:,,..:.:::",
    ":,,:.:,;..:,,,,,",
    "#--##-;;;-#-####",
]

# Behind the window: the bin, and 0 / 3 / 5 / 7 of its seven rows under meal. The top row of a heap is
# never flat — there is a crest above the surface at the middle levels.
WINDOW = [
    ["oooooooooooo",
     "ojjjjjjjjjjo",
     "ojjjjjjjjjjo",
     "ojjjjjjjjjjo",
     "ojjjjjjjjjjo",
     "ojjjjjjjjjjo",
     "oiiiooooiiio"],    # the floor, and the chute cut through it
    ["oooooooooooo",
     "ojjjjjjjjjjo",
     "ojjjjjjjjjjo",
     "ojjsfFfsjjjo",
     "ssfFffffFfss",
     "sffgffffffgs",
     "ssffffgfffss"],
    ["oooooooooooo",
     "ojjjjjjjjjjo",
     "ojjjsffsjjjo",
     "ssfFffffFfss",
     "sfffgffffffs",
     "sffffffgfffs",
     "ssfgffffffss"],
    ["sffFffffFffs",
     "ffFfffffffFf",
     "sfffgffffffs",
     "sffffffgfffs",
     "sfgfffffffFs",
     "sffffgffffgs",
     "ssffffgfffss"],
]

# ---- the lid: OAK with an iron rim and the mouth open at cols 3..12, rows 3..12 -------------------
TOP = [
    ".:.+++++.+++++.;",
    "..::;,:...::..,,",
    ":.HIiiiiiiiiIH.;",       # the rim, with a bracket at each corner
    ";-I@@@@@@@@@@I-#",
    ".+I@@@@@@@@@@I,.",
    ":.I@@@@@@@@@@I.:",
    ",,I@@@@@@@@@@I,,",
    "##I@@@@@@@@@@I-#",
    ".+I@@@@@@@@@@I.,",
    ".:I@@@@@@@@@@I,;",
    "+.I@@@@@@@@@@I.,",
    "##I@@@@@@@@@@I##",
    "+,I@@@@@@@@@@I++",
    "::HIiiiiiiiiIH::",
    ":,,:.:,;..:,,,,,",
    "#--##-;;;-#-####",
]

# Looking down the shaft: empty, then a heap that grows out to the walls and finally over the rim.
MOUTH = [
    ["IIIIIIIIIi",
     "ijjjjjjjji",
     "ijjjjjjjji",
     "ijjoooojji",
     "ijjoooojji",
     "ijjoooojji",
     "ijjjjjjjji",
     "ijjjjjjjji",
     "ijjjjjjjji",
     "jjjjjjjjjj"],
    ["IIIIIIIIIi",
     "ijjjjjjjji",
     "ijjjjjjjji",
     "ijjsffsjji",
     "ijsfFFfsji",
     "ijsfFgfsji",
     "ijjsffsjji",
     "ijjjjjjjji",
     "ijjjjjjjji",
     "jjjjjjjjjj"],
    ["IIIIIIIIIi",
     "issfsssssi",
     "isffffgfsi",
     "isfFFFFffi",
     "isfFFgFFsi",
     "isffFFFfsi",
     "isfFFFffsi",
     "issfffgfsi",
     "isssssfssi",
     "jjjjjjjjjj"],
    ["sffffgfffs",
     "ffFfffFfff",
     "fFFfffFFff",
     "ffFFgfFFff",
     "fFFFffFgFf",
     "ffFgFFFfff",
     "fFFfffgFFf",
     "ffFFFffFff",
     "ffffFfffff",
     "ssfffgffss"],
]

# ---- the underside: OAK and the outlet plate ------------------------------------------------------
BOTTOM = [
    ".:.+++++.+++++.;",
    "..::;,:...::..,,",
    ":...:.:,,,,::..;",
    ";--;;-##--#-##-#",
    ".+,++++,+++.::,.",
    ":..:,iiiiii....:",
    ",,:.:ijjjji::,,,",
    "##--;ijooji--;-#",
    ".++.:ijooji+++.,",
    ".:...ijjjji,:,,;",
    "+.::,iiiiii:,,.,",
    "##-;;-###-;--###",
    "+,.++..,+++,+.++",
    "::..,,:,,..:.:::",
    ":,,:.:,;..:,,,,,",
    "#--##-;;;-#-####",
]

W = H = 16
HOLE = "@"
ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing")
TEX = os.path.join(A, "textures/block")
LEVELS = 4          # FeedingStationBlock.FILL is 0..3


def hole(frame, what):
    """The rectangle the level art drops into — and a shout if it is not a solid rectangle."""
    cells = [(x, y) for y, row in enumerate(frame) for x, ch in enumerate(row) if ch == HOLE]
    x0, y0 = min(x for x, _ in cells), min(y for _, y in cells)
    x1, y1 = max(x for x, _ in cells) + 1, max(y for _, y in cells) + 1
    if len(cells) != (x1 - x0) * (y1 - y0):
        raise SystemExit("%s: the '%s' region is not a solid rectangle" % (what, HOLE))
    return x0, y0, x1, y1


def draw(frame, inner, what):
    """The frame with `inner` painted into its hole. Every texel must end up opaque: this is a cube."""
    if len(frame) != H or any(len(r) != W for r in frame):
        raise SystemExit("%s: the frame is not %dx%d" % (what, W, H))
    x0, y0, x1, y1 = hole(frame, what)
    if len(inner) != y1 - y0 or any(len(r) != x1 - x0 for r in inner):
        raise SystemExit("%s: the level art is %dx%d, the hole is %dx%d"
                         % (what, max(len(r) for r in inner), len(inner), x1 - x0, y1 - y0))
    px = [[None if ch == HOLE else C[ch] for ch in row] for row in frame]
    for y, row in enumerate(inner):
        for x, ch in enumerate(row):
            px[y0 + y][x0 + x] = C[ch]
    if any(p is None for row in px for p in row):
        raise SystemExit("%s: a texel was left unpainted" % what)
    return px


def png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) + b"\xff" for p in row) for row in px)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    io.open(path, "wb").write(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b""))


def wr(path, s):
    io.open(path, "w", encoding="utf-8", newline="\n").write(s)


def name(level):
    """fill=0 keeps the plain name: the block item's model parents it."""
    return "feeding_station" if level == 0 else "feeding_station_%d" % level


for lv in range(LEVELS):
    png(os.path.join(TEX, "feeding_station_side%d.png" % lv), draw(SIDE, WINDOW[lv], "side%d" % lv))
    png(os.path.join(TEX, "feeding_station_top%d.png" % lv), draw(TOP, MOUTH[lv], "top%d" % lv))
    wr(os.path.join(A, "models/block/%s.json" % name(lv)), json.dumps({
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "riverfishing:block/feeding_station_top%d" % lv,
            "bottom": "riverfishing:block/feeding_station_bottom",
            "side": "riverfishing:block/feeding_station_side%d" % lv,
        },
    }, indent=2) + "\n")
png(os.path.join(TEX, "feeding_station_bottom.png"), [[C[ch] for ch in row] for row in BOTTOM])

wr(os.path.join(A, "blockstates/feeding_station.json"), json.dumps({
    "variants": {"fill=%d" % lv: {"model": "riverfishing:block/%s" % name(lv)} for lv in range(LEVELS)},
}, indent=2) + "\n")

# ---- the check --------------------------------------------------------------------------------------
# Three ways this quietly breaks and nothing in Minecraft says a word: a blockstate that misses a value
# of the property (a MISSINGNO cube), a model pointing at a texture nobody drew (a hole), and a
# charges->level mapping that never reaches one of the pictures. So the block is read back and asked.
java = os.path.join(ROOT, "common/src/main/java/com/riverfishing/block/FeedingStationBlock.java")
ledger = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/WaterUpgrades.java")
if os.path.exists(java):
    src = io.open(java, encoding="utf-8").read()
    top = re.search(r'IntegerProperty\.create\("fill", 0, (\d+)\)', src)
    assert top and int(top.group(1)) + 1 == LEVELS, "FILL and this generator disagree about how many steps"
    m = re.search(r"Math\.min\((\d+), \(charges \+ (\d+)\) / (\d+)\)", src)
    assert m, "fill() is no longer the shape this check can read — update both or neither"
    cap, add, div = (int(g) for g in m.groups())
    mx = int(re.search(r"MAX_CHARGES = (\d+)", io.open(ledger, encoding="utf-8").read()).group(1))
    steps = [min(cap, (c + add) // div) for c in range(mx + 1)]
    assert steps == sorted(steps), "fill() goes backwards somewhere: %s" % steps
    assert steps[0] == 0 and steps[-1] == LEVELS - 1, "empty or full does not land on an end step: %s" % steps
    assert sorted(set(steps)) == list(range(LEVELS)), "a level of the art is unreachable: %s" % steps
state = json.load(io.open(os.path.join(A, "blockstates/feeding_station.json"), encoding="utf-8"))
assert sorted(state["variants"]) == ["fill=%d" % lv for lv in range(LEVELS)], "a fill value has no variant"
for key, var in state["variants"].items():
    model = json.load(io.open(os.path.join(A, "models/block/%s.json" % var["model"].split("/")[-1]),
                              encoding="utf-8"))
    for slot, ref in model["textures"].items():
        p = os.path.join(TEX, ref.split("/")[-1] + ".png")
        assert os.path.exists(p), "%s.%s points at %s, which nothing drew" % (key, slot, ref)
print("feeding station: %d levels x (wall + lid) + the underside, %d colours; %d models, 1 blockstate"
      % (LEVELS, len(C), LEVELS))
