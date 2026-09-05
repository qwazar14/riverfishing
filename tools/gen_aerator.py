# -*- coding: utf-8 -*-
"""§aerator: the aerator's three faces — a submerged pump with a magma core behind an iron grille.

    py -X utf8 tools/gen_aerator.py                 # this tree
    py -X utf8 tools/gen_aerator.py <other-root>    # …and the other two worktrees

It used to be one `cube_all` texture: a grey box with a diagonal hatch and a column of BLUE bubbles up
the middle. Two things were wrong with that. The hatch was per-pixel noise, which vanilla never does at
16px, and the block lives underwater — a blue motif on a blue-tinted block is invisible, so the read has
to come from VALUE, not hue. And a machine with the same picture on all six sides reads as a patterned
cube, never as equipment; a furnace, a dispenser and an observer all earn their look from having a top
that differs from the sides.

So it is three sheets now, hung on `minecraft:block/cube_bottom_top`:

    aerator_side    the housing: an iron plate, bolts, and a recessed louvre with the core glowing out
    aerator_top     the outlet: a deck plate around a slotted vent, light escaping the middle slot
    aerator_bottom  the base plate nobody sees: bolts and an access hatch

The one idea is "a hot core behind a metal grille", which is also what the recipe says out loud — iron
ingots, redstone, and a MAGMA BLOCK in the middle. A texture cannot self-illuminate without emissive
support, so the glow is what vanilla does instead: a high-value orange against the darkest metal, kept
to the centre of each opening so it stays an accent and the block does not turn into a lantern.

Every colour below is a real pixel sampled out of the vanilla block it borrows from — iron_block.png,
magma.png, furnace_side.png, furnace_front_on.png — so the aerator sits next to its own recipe.

Four things a review against vanilla caught, and what the maps do about them:

  GRAIN.  The plate used to be flat `I`, which is the tell of a modded texture. The measure that catches
  it is distinct colours per 4x4 window: vanilla runs 5.4 (gravel) to 6.2 (oak_planks), and a flat plate
  scored 4.4. It is not about run length — iron_block is banded and has 14-texel runs — it is that
  wherever you put a 4x4 window down, half a dozen tones fall inside it. So every field here is broken
  up with neighbours one rung off (`J`/`K`/`n` in the iron, `o`/`D` in the shadow, `e`/`h` underneath),
  and even the rim columns are nicked, which is the last place a rectangle tool shows. The grain is
  hand-placed and frozen in these strings, never generated: the same texel is off-tone every build.

  MASS, NOT STRIPES.  The core used to be five 1px orange bars alternating with 1px louvre bars. Box-
  downscale that by two and the bars average into a uniform brown square — at 4px the block was "a grey
  box with a brown patch". furnace_front_on's fire is a contiguous MASS and still reads as fire that
  small, so the core is now three solid rows (7-9) with a dimmer halo above and below, and the louvre
  bars sit above and below it instead of cutting through it.

  LIGHT.  Top lip minus bottom row used to be +160 luminance. The strongest thing vanilla does is
  composter_side at +76. `I` over `m` instead of `i` over `d` puts it near +90.

  ONE LIGHT SOURCE.  There was a red redstone lamp two texels from the right edge — the only red and the
  only asymmetric mark on the block, which turned into a repeating tick along a wall of aerators. It is
  gone. The core is the block's light, and a second, chromatically unrelated one on a 16px face only
  competes with it; the freed texel is now the bolt that mirrors the one on the left, so the side
  carries four corner bolts like the top does.

To restyle it, edit the maps and re-run; nothing reads these but the model.
"""
import io, os, struct, sys, zlib

# ---- the palette: every value sampled from the vanilla texture named beside it ---------------------
C = {
    # metal — furnace_front_on.png for the dark housing, iron_block.png for the lit plate.
    # Read as a ladder: D d o e m h M n J I K i. Grain steps ONE rung, never two — vanilla's own dark
    # grain moves 12 to 16 luminance at a time, and a speck that jumps 40 stops reading as brushed metal
    # and starts reading as gravel. `e` and `h` exist only to make the shaded underside that fine.
    "D": (0x21, 0x21, 0x21),   # the depth inside an opening; the darkest thing here, never black
    "d": (0x3C, 0x3B, 0x3B),   # the louvre bars
    "o": (0x50, 0x4E, 0x4E),   # a bolt head, and the highlight on a louvre bar
    "e": (0x5D, 0x5B, 0x5B),   # furnace_side.png — the underside, one rung down
    "m": (0x68, 0x68, 0x68),   # mid metal: the vent bars, the underside
    "h": (0x77, 0x77, 0x77),   # furnace_side.png — the underside, one rung up
    "M": (0x91, 0x91, 0x91),   # lit metal: rims and ledges
    "n": (0xA8, 0xA8, 0xA8),   # furnace_side.png — the grain rung between the ledges and the plate
    "J": (0xB9, 0xB9, 0xB9),   # iron_block.png — the plate, one rung down
    "I": (0xC1, 0xC1, 0xC1),   # iron plate
    "K": (0xD1, 0xCF, 0xCF),   # iron_block.png — the plate, one rung up
    "i": (0xE6, 0xE6, 0xE6),   # iron, the brightest rung; the top of the vent deck catches it
    # the core — magma.png
    "c": (0x65, 0x28, 0x28),   # cooled crust, where the glow dies against the metal
    "1": (0xCA, 0x4E, 0x06),
    "2": (0xE6, 0x64, 0x10),
    "3": (0xF4, 0x85, 0x22),
    "4": (0xFB, 0xAA, 0x59),   # the hottest pixel; four of them in the whole block, and no more
}

# ---- the side: plate, bolts, and a recess with a core burning behind the middle of the grille -------
# The louvre bars run above the core and below it, never across it: three solid rows of orange survive
# a box-downscale to 4px as one hot thing, five 1px bars do not.
SIDE = [
    "MIJIMIIJIKIIJIIM",   # top lip: `I`, not `i` — the light here is a lip, not a lamp
    "MIoJIKIJIIKJIoIM",   # the upper plate, and the first two of the four bolts
    "MIJDDdDDDDdDDIIn",   # the recess opening — its top wall is in shadow
    "MJIdodddDodddJIM",   # louvre bar
    "nIIDDdDDoDDdDIJM",
    "MIKodddDddoddKIM",   # louvre bar
    "MJIDc122221cDIIM",   # the halo above the core, narrower and cooler
    "MIIc12333321cJKM",
    "MKIc12344321cIIM",   # the core: rows 7-9 are one contiguous mass, dead centre
    "MIJc12333321cIJn",
    "MIIDc112211cDKIM",   # the halo below — dimmer than the one above, because heat rises
    "nJKdodddDdoddIIM",   # louvre bar
    "MIIDdoddddodDJIM",   # a thicker bar at the bottom, so the grille sits on something
    "MIJMnMMnMMnMMIIM",   # the recess floor, lit from above
    "mMonMnMMMnMMMoMm",   # the lower plate, a shade down, and the other two bolts
    "mmomdmmommomdmom",   # `m`, not `d`: the underside is shaded, not silhouetted
]

# ---- the top: a deck plate around a slotted vent ---------------------------------------------------
# Three slots two pixels wide, bars between them. The middle slot is the lit one — it is the one over
# the core, and it is the whole reason you can tell from above that the thing is running. In the four
# rows level with the core the light spills into the outer slots too and the bars between them go to
# crust rather than grey, so what escapes the deck is a MASS: a 2px-wide column of orange averages
# away to nothing at 4px, the same way the old side did.
TOP = [
    "MnMMnMMMnMMnMMnM",
    "MIoIJIKIiJIKIoIM",
    "MIDdDDDdDDdDDDIM",
    "MIDmDdm11mdDmDKM",
    "nIDMDDm22mDDMDIM",
    "MJDmdDm23mDdmDIM",
    "MIDmD1c33c1DmDIn",
    "MIDm12c43c21mDIM",
    "MIDm21c34c12mDIM",
    "MIDmd1c33c1dmDJM",
    "nIDMDDm32mDDMDIM",
    "MKDmDdm22mdDmDIM",
    "MIDmDDM11MDDmDIn",
    "MIDDdDDDdDDDdDIM",
    "MIoIKIJIiKIJIoIM",
    "MMnMMnMMMnMMnMMM",
]

# ---- the bottom: the intake. It stands on the bed; the water it moves has to come in somewhere. -----
BOTTOM = [
    "dDddoddDdddoddDd",
    "dmhmemhmmemhmemd",
    "dmomhemhmehmeomd",   # bolt, bolt
    "dhmemmehemmhmehd",
    "dmehDDdoDdDDhmed",   # the access hatch starts
    "oemhDmhemhedmhmd",
    "dmheDdDoDDdDehmd",
    "dhmmdhemhmeDmmho",
    "dmehDDdDdoDDhemd",
    "dehmDemhemhDemed",
    "DmmeDdoDDdDDemhd",
    "dhmeMnMMnMMnmemd",   # the hatch lip, the one thing down here catching any light
    "dmehmmhemhmehmmD",
    "dmohmemhmmehmomd",   # bolt, bolt
    "demmhmemhemmhmed",
    "ddDddoddDdddoddD",
]

W = H = 16
ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/block")


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def sheet(name, rows):
    if len(rows) != H:
        raise SystemExit("%s is %d rows, not %d" % (name, len(rows), H))
    raw = b""
    for y, row in enumerate(rows):
        if len(row) != W:
            raise SystemExit("%s row %d is %d wide, not %d: %r" % (name, y, len(row), W, row))
        raw += b"\x00" + b"".join(bytes(C[ch]) + b"\xff" for ch in row)
    path = os.path.join(DIR, name + ".png")
    io.open(path, "wb").write(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b""))
    print("wrote %s: %dx%d, %d colours"
          % (os.path.relpath(path, ROOT), W, H, len({ch for row in rows for ch in row})))


sheet("aerator_side", SIDE)
sheet("aerator_top", TOP)
sheet("aerator_bottom", BOTTOM)

dead = os.path.join(DIR, "aerator.png")
if os.path.exists(dead):
    os.remove(dead)
    print("removed the old cube_all aerator.png — the model reads the three sheets above now")
