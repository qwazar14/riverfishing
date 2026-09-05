# -*- coding: utf-8 -*-
"""§warm-outflow: the warm outflow's three faces, drawn as three whole 16x16 textures.

    py -X utf8 tools/gen_warm_outflow.py                 # this tree
    py -X utf8 tools/gen_warm_outflow.py <other-root>    # …and the other two worktrees

The block used to be `cube_all` — ONE texture on all six sides, a copper pipe with a red smear at its
foot repeated top, bottom and round. Six identical faces is what makes a block read as a *material*;
equipment reads as equipment because its faces differ, which is why a furnace has a top, a front and
plain sides. The block class here is shared by five upgrades and must not learn a facing, so the front
is the only thing we cannot have: instead the six faces are three pictures.

    warm_outflow_top      the deck: a louvred copper vent, four slots straight down onto the heater
    warm_outflow_bottom   four riveted plates and nothing else; the face nobody looks at stays quiet
    warm_outflow_side     the housing, then the mouth, then the water — see below

The two lit faces are deliberately different in KIND, not just in arrangement. The top is a GRATE: hard
copper bars, bright between them, because you are looking straight at the fire. The side is a HOLE: two
near-black rows of depth and the fire small at the foot of it, because you are looking down a pipe. Give
them the same treatment and a player reads the block as a patterned cube again.

The recipe is copper block + blaze powder + bucket, so the picture is exactly those three: a copper
housing, blaze powder burning in it, water leaving it. The copper is not invented — every shade is
lifted out of vanilla's copper_block.png and the hot ones out of blaze_powder.png and magma.png, so the
block sits beside the copper it was crafted from. The blues are the mod's own water, the ones the pond
sign and the aerator already use.

Three rules the maps below keep, all of them measured against the copper this block is crafted from:

  GRAIN.  Vanilla copper is never a flat field. copper_block carries about five distinct colours in any
  4x4 window; a slab of one tone with two rivets on it carries two, and at close range that reads as a
  painted panel rather than as metal. So roughly one texel in four is off-tone — no shape, no gradient,
  just grain, hand-placed here and frozen in the strings so it never moves between runs.

  NO UNBROKEN EDGE COLUMNS.  Column 0 is the lit edge and column 15 the shaded one, which is right —
  copper_block leans the same way, harder. But if both are a solid 1px line, two of these blocks side by
  side make a full-height dark-to-bright ridge that rings like a picture frame. Both columns are
  interrupted a few times, so the join reads as grain.

  THE WATER IS NOT A RECTANGLE.  It is the most saturated thing on the block and it survives the
  downscale to 4px, so its shape is what a player actually sees. It gets a ragged top (the strands start
  on different rows), a crest broken by a tooth of dry lip, flanks that step outward as the sheet
  spreads, and short vertical dashes of light rather than full-height streaks — which is how vanilla's
  own water_flow is drawn. No border of `v` around it: a frame is exactly what makes it a box.

Every face is 16x16 and fully opaque; the model is plain `cube_bottom_top`, so there is no uv arithmetic
here and nothing to check — a face either has its own texture or it does not. To restyle it, edit a map
below and re-run, or just replace a PNG, nothing reads them but the model.
"""
import io, os, struct, sys, zlib

# ---- the palette ----------------------------------------------------------------------------------
# copper: sampled from assets/minecraft/textures/block/copper_block.png, lightest to darkest
# heat:   from blaze_powder.png (Y, O, B) and magma.png (R)
# water:  the mod's own, the pair pond_sign.py and the aerator draw with
C = {
    "L": (0xE3, 0x82, 0x6C),   # copper, the lit edge
    "c": (0xD6, 0x7B, 0x5B),   # copper, light
    "C": (0xC8, 0x74, 0x56),   # copper, the body of the housing
    "d": (0xA7, 0x5A, 0x40),   # copper, shade
    "D": (0x90, 0x49, 0x31),   # copper, the seams and the frames
    "Y": (0xFF, 0xCB, 0x00),   # blaze powder, the core of the fire
    "O": (0xFF, 0xA3, 0x00),   # blaze powder, the flame
    "R": (0xE6, 0x64, 0x10),   # magma, the fire seen edge-on
    "B": (0x95, 0x33, 0x00),   # blaze powder, its burnt edge
    "b": (0x4A, 0x1E, 0x0A),   # the inside of the bore — dark enough that the fire in it is a light
    "w": (0x2E, 0x6B, 0xC4),   # water
    "W": (0x8F, 0xC6, 0xF2),   # water, the streaks down the falling sheet
    "v": (0x1B, 0x4A, 0x8E),   # water, its shaded edges
}

# ---- the deck: a louvred vent, four slots over the heater ------------------------------------------
# The vent is centred on the same columns as the side's mouth (one texel narrower, so the deck keeps a
# margin the mouth does not need), which is what makes the two lit faces read as one machine.
TOP = [
    "LLcLLccLLLcLLcLc",
    "LcCCcCCdCCCcCcCd",
    "LCCcCCCdCCcCCCCC",
    "LCcdDDdDDDdDdCCd",   # the vent's lip
    "ccCDROOYORORDCcc",
    "LCCDDdDBDDdDDCCd",   # a bar, burnt where it sits closest to the fire
    "LCcDOYYOYYYODCCd",   # the two middle slots are over the heater itself
    "LCCDdDDDBdDDDCcd",
    "ccCDOYYYOYYODCCC",
    "LCCDDdBDDDdDDcCd",
    "LCcDROYOOYORDCCd",
    "LCCdDdDDDdDDdCcc",
    "LCcCCdCCLCcCCCCd",
    "cCCLcCCCdCCcCLCd",
    "LcCcCCLCCCdCCCcC",
    "cddDddcddddDdcdD",
]

# ---- the housing, the mouth, the water --------------------------------------------------------------
# Half the face is the mouth on purpose: at 16 px one bold shape beats three careful ones. The water
# runs off the bottom edge rather than stopping short, so the block looks like it is still discharging.
SIDE = [
    "LLcLLccLLLcLLcLc",
    "LcCCcCCdCCCcCcCd",
    "LCLCcCCCdCcCCLCC",   # two rivets, and that is all the housing needs
    "cCDCCdcCCcCdCDCd",
    "LCdDDdDDDdDDDdCC",   # the mouth's lip
    "LCDDbbbbbbbbbDCd",   # the bore: mostly dark, so it reads as a HOLE and not as the top's grate
    "LcDbbbbbbbbbDDCd",   # two black rows are the depth of it
    "LCDbbbBBBBbbbDcD",   # the blaze powder burning at the foot, seen down the pipe
    "cCDbbBROORBbbDCd",
    "LCDbBROYYORBbDCd",
    "LCDbbBROORBbbDcd",
    "LCdDDdDDDDdDDdCc",   # the lip it pours over, unbroken — the water starts below it
    "LCCCCwWwCwWwCCcd",   # the crest: strands, not a bar, and a tooth of the lip still dry at col 8
    "LCCWwwWwvwWwWCCd",   # the sheet spreads as it falls, so its top edge is ragged and its
    "LcwWvwwwvwwWWwCd",   # flanks step outward; the light tones are short dashes the way
    "cdwwvwwWwwwWwwdD",   # water_flow draws them, never a full-height line
]

# ---- the underside: four plates bolted together -----------------------------------------------------
# One 6x6 plate drawn once and then mirrored and rolled, so the four read as the same stamped part. Its
# grain is light only — c and L. The dark pair d/D is the frame and the seam; scattering it through a
# plate as well would read as pitting, and the seam would stop being a seam.
BOTTOM = [
    "DDdDDdDDDdDDdDDD",
    "DCcCCLcdDcLCCcCD",
    "DcLCcCCddCCcCLcD",   # the rivets, with their shadow on the row below
    "dCdcCCcDdcCCcdCD",
    "DcCLCcCddCcCLCcd",
    "DCCcCdCdDCdCcCCD",
    "DcCCLCcddcCLCCcD",
    "DddDddDdddDddDdD",   # the seam
    "DdDddDdDdDddDddD",
    "DcCLCcCDdCcCLCcD",
    "dCCcCdCddCdCcCCD",
    "DcCCLCcdDcCLCCcD",
    "DCcCCLcddcLCCcCD",
    "DcLCcCCdDCCcCLcd",
    "DCdcCCcDdcCCcdCD",
    "DdDDdDDdDDdDDdDD",
]

W = H = 16
ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/block")


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write(name, rows):
    if len(rows) != H or any(len(r) != W for r in rows):
        raise SystemExit("%s is not %dx%d: %s" % (name, W, H, [len(r) for r in rows]))
    bad = sorted({ch for r in rows for ch in r} - set(C))
    if bad:
        raise SystemExit("%s uses %s, which the palette does not name — a cube face must be opaque "
                         "everywhere, so there is no 'nothing' colour here" % (name, bad))
    raw = b""
    for r in rows:
        raw += b"\x00" + b"".join(bytes(C[ch]) + b"\xff" for ch in r)
    path = os.path.join(OUT, name + ".png")
    io.open(path, "wb").write(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b""))
    print("wrote %s: %dx%d, %d colours"
          % (os.path.relpath(path, ROOT).replace("\\", "/"), W, H, len({ch for r in rows for ch in r})))


if __name__ == "__main__":
    idle = sorted(set(C) - {ch for m in (TOP, SIDE, BOTTOM) for r in m for ch in r})
    if idle:
        raise SystemExit("the palette names %s and no map uses them — a colour nobody draws with is a "
                         "leftover from an earlier draft, not a spare" % idle)
    write("warm_outflow_top", TOP)
    write("warm_outflow_side", SIDE)
    write("warm_outflow_bottom", BOTTOM)
    # the cube_all sheet the block used to wear; nothing reads it now
    stale = os.path.join(OUT, "warm_outflow.png")
    if os.path.exists(stale):
        os.remove(stale)
        print("removed warm_outflow.png — the block is cube_bottom_top now (tools/gen_upgrade_tex.py "
              "still has a warm_outflow() that would put it back)")
