# -*- coding: utf-8 -*-
"""§gravel-bed: the gravel bed's texture, a finer gravel than vanilla's with iron flecked through it.

    py -X utf8 tools/gen_gravel_bed.py                 # this tree
    py -X utf8 tools/gen_gravel_bed.py <other-root>    # …and the other two worktrees

The block is 4 vanilla gravel + 2 iron nuggets in a gng/gng, so the picture has to read as exactly that
and sit next to both without clashing. The palette is therefore not invented: the six greys are six of
the eight tones in `minecraft:block/gravel` — every one of them, byte for byte, including the two the
first draft went without (#726B69, gravel's third-most-used tone, and #645B5B, its darkest). The only
two gravel omits are its 165 and 143, which the bed has no room for at this size. The warm ones are the
LOWER half of the ramp in `minecraft:block/iron_ore` — its three dimmest warms, not its brightest, so
the flecks say "iron" without out-shouting the stone they sit in.

That last point is the whole reason this file was rewritten. The draft reached for iron_ore's two
brightest warms and for a #4E4745 that exists nowhere in gravel, and it left the 108 rung empty: mean
adjacent-texel contrast came out at 25.9 against vanilla gravel's 18.0, and a wall of it read as
salt-and-pepper rather than as stone. The palette below measures 20.0, with the wrap edge at 0.78 of
the interior against vanilla's 0.80 — the two checks at the bottom of this file hold both.

The texture before that was a 3x2 grid of tan slabs: one motif repeated eight times, so a wall of it
read as bathroom tiling, and the tan sat nowhere near the grey of the gravel it is made of.

WHAT IS DRAWN. Twenty-six stone lumps scattered over a mid-grey field. Each is 2 or 3 texels — smaller
than the lumps in vanilla gravel, which is what "finer fraction" has to mean at sixteen pixels. Sixteen
of them catch the light: an `O` on the lump's top-left corner with a shadow texel diagonally down-right
of it, never the other way round and never both. That pair is the whole trick, and it only works if it
points one way: with a darker texel on every side a highlight is just a speck and the picture collapses
into noise. THE LIGHT COMES FROM THE UPPER LEFT, as it does on the aerator, the outflow and the feeding
station. The other ten lumps are unlit — vanilla gravel spends only 8 of its 256 texels on its two
bright tones, so most lumps are supposed to be dull. Eleven deeper pockets sit in the gaps so the bed
has a floor under the stones.

Four iron flecks, deliberately unequal — three texels, two, three, one — because four identical marks on
a 16x16 tile line up into a lattice the moment you lay two blocks side by side. Each is lit the same way
as the stones (`I` up-left, dimming down-right) and each has a `#` directly under it, so it reads as a
lump with a shadow rather than as a bright dash; without that body they were the four things the eye
found first, and tiling the block made a grid of them.

IT MUST TILE. This is a `cube_all`, so the sheet meets itself on all four edges and diagonally. The map
below is already wrapped: a pebble that runs off the right edge is drawn continuing at the left, and the
same top to bottom. Edit a texel on one edge and you owe its partner on the other. Four lumps straddle
the wrap on purpose — that is what keeps the 16px period from showing on a wall.

To restyle it, edit the map and re-run — or just replace the PNG, nothing reads it except the model.
"""
import io, os, struct, sys, zlib

# ---- the palette ----------------------------------------------------------------------------------
# Six of vanilla gravel's eight greys, and vanilla iron ore's three dimmest warms. Luminances in the
# comments are Rec.709, the scale the contrast check below works in.
C = {
    "#": (0x64, 0x5B, 0x5B),   # 92  the pocket between stones — gravel's darkest tone, its floor
    "-": (0x72, 0x6B, 0x69),   # 108 a stone's shadowed side, always down-right of the lit corner
    ".": (0x81, 0x7F, 0x7F),   # 127 the bed itself, the tone everything else is read against
    ",": (0x89, 0x81, 0x7E),   # 130 …and its warm drift, which keeps the field from going dead flat
    "o": (0x97, 0x97, 0x97),   # 151 a stone's body
    "O": (0xB0, 0xAE, 0xAE),   # 174 …and the top-left corner of it the light actually lands on
    "x": (0x77, 0x67, 0x4F),   # 104 iron, in shadow
    "i": (0x88, 0x74, 0x55),   # 118 iron
    "I": (0xAF, 0x8E, 0x77),   # 147 iron, lit — still dimmer than a lit stone, on purpose
}

# ---- the bed, 16 x 16, already wrapped at every edge ------------------------------------------------
BED = [
    ".-.#..Oo-......,",
    "Oo.-..-#..oo.Ooo",
    ",-oIiOo...,-.,#-",
    ".,.-#x-.........",
    "o.Oo....oo..Oo.O",
    "-.,#.Ooo,-Ii--.,",
    "..oo..-oo-#.....",
    ".-,-,-.--.#.Oo#.",
    "....oo.oo.,.--,.",
    "oo..,-.--..Oo..O",
    "-.Ooo......,-ooo",
    "Oo,--oIi.Ooo-o-.",
    "--..,,x.,---..oo",
    "-ooo..#oOo.oooi-",
    ".-,..oo,,-#-,,#.",
    "Oo...,,.-....-.o",
]

W = H = 16
ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/block/gravel_bed.png")

if len(BED) != H or any(len(r) != W for r in BED):
    raise SystemExit("the bed must be exactly %dx%d — a cube_all reads the whole sheet" % (W, H))

px = [[C[ch] for ch in row] for row in BED]


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


# The two things the review caught, so an edit to the map cannot quietly undo them again.
dark = [(x, y) for y in range(H) for x in range(W) if BED[y][x] == "O"
        and lum(px[(y - 1) % H][(x - 1) % W]) - lum(px[(y + 1) % H][(x + 1) % W]) <= 8]
if dark:
    raise SystemExit("the light comes from the upper left: these highlights are not shadowed "
                     "down-right, or sit in shadow up-left: %s" % dark)

d = [abs(lum(px[y][x]) - lum(px[y][(x + 1) % W])) for y in range(H) for x in range(W)] + \
    [abs(lum(px[y][x]) - lum(px[(y + 1) % H][x])) for y in range(H) for x in range(W)]
if sum(d) / len(d) > 21.0:
    raise SystemExit("mean adjacent-texel contrast %.1f — vanilla gravel is 18.0, and past ~21 a wall "
                     "of this reads as salt-and-pepper" % (sum(d) / len(d)))

raw = b""
for row in px:
    raw += b"\x00" + b"".join(bytes(p) + b"\xff" for p in row)


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


io.open(OUT, "wb").write(
    b"\x89PNG\r\n\x1a\n"
    + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(raw, 9))
    + chunk(b"IEND", b""))
iron = sum(row.count(c) for row in BED for c in "xiI")
print("wrote %s: %dx%d, %d colours, %d iron texels, contrast %.1f (vanilla gravel 18.0)"
      % (os.path.relpath(OUT, ROOT), W, H, len({c for c in C.values()}), iron, sum(d) / len(d)))
