# -*- coding: utf-8 -*-
"""§provinces §biomes-require: which part of the world each species lives in, written into the profiles.

    py -X utf8 tools/add_provinces.py            # writes the fields; prints the counts and what was left open

One table, in this file, so a wrong range is one line to correct — the same shape as tools/add_latin.py.

A species NOT listed here keeps no `provinces` field at all, which means everywhere: that is what every
sea fish wants (one ocean, one fauna) and the honest answer for a fish that has been stocked into every
continent by people. The listed ones are gated hard: absent from any province they do not name.

Ranges are the real ones, with introductions counted — a common carp really is on four continents, a
largemouth bass really is in Japan and Spain, and a rainbow trout really is in Patagonia. That is what
keeps a province from being a desert while still making a taimen worth travelling for.
"""
import io, json, os, glob, re

# ---- the ranges ---------------------------------------------------------------------------------
PAL, NEA, NEO, IND = "palearctic", "nearctic", "neotropic", "indomalaya"

PROVINCES = {
    # --- Europe and northern Asia -----------------------------------------------------------------
    "asp": [PAL], "bleak": [PAL], "blue_bream": [PAL], "bream": [PAL], "burbot": [PAL, NEA],
    "chub": [PAL], "common_dace": [PAL], "crucian_carp": [PAL, IND], "golden_crucian": [PAL],
    "gorchak": [PAL], "gudgeon": [PAL], "ide": [PAL], "kutum": [PAL], "loach": [PAL, IND],
    "nase": [PAL], "roach": [PAL], "rudd": [PAL], "ruffe": [PAL], "sabrefish": [PAL],
    "sculpin": [PAL], "smelt": [PAL, NEA], "sterlet": [PAL], "sturgeon": [PAL], "tubenose_goby": [PAL],
    "round_goby": [PAL], "verkhovka": [PAL], "vimba": [PAL], "volga_zander": [PAL],
    "white_bream": [PAL], "white_eye_bream": [PAL], "whitefish": [PAL, NEA], "zander": [PAL],
    "beluga": [PAL], "catfish": [PAL], "eel": [PAL, IND], "garfish": [PAL],
    # the cold north, and the fish worth the walk
    "taimen": [PAL], "lenok": [PAL], "grayling": [PAL], "nelma": [PAL],
    # --- Holarctic: the same fish, or near enough, on both northern continents ---------------------
    "pike": [PAL, NEA], "perch": [PAL, NEA], "salmon": [PAL, NEA], "pink_salmon": [PAL, NEA],
    "char": [NEA, PAL],
    # --- North America ----------------------------------------------------------------------------
    "bluegill": [NEA, PAL, IND], "largemouth_bass": [NEA, PAL, IND], "channel_catfish": [NEA, PAL],
    "striped_bass": [NEA], "rainbow_trout": [NEA, PAL, NEO, IND],
    # --- Central and South America ----------------------------------------------------------------
    "arapaima": [NEO], "peacock_bass": [NEO], "piraiba": [NEO], "golden_dorado": [NEO],
    "red_piranha": [NEO], "oscar": [NEO], "mayan_cichlid": [NEO], "snook": [NEO, NEA],
    "tarpon": [NEO, NEA],
    # --- South and East Asia ----------------------------------------------------------------------
    "bullseye_snakehead": [IND], "rotan": [PAL, IND],
    # --- carried everywhere by people --------------------------------------------------------------
    # The common carp and its scale varieties, the two Asian carps and the brown trout are on every
    # continent a person could carry them to, which is all four.
    "carp": [PAL, NEA, NEO, IND], "wild_carp": [PAL, NEA, NEO, IND],
    "mirror_carp": [PAL, NEA, NEO, IND], "linear_carp": [PAL, NEA, NEO, IND],
    "naked_carp": [PAL, NEA, NEO, IND],
    "grass_carp": [IND, PAL, NEA, NEO], "silver_carp": [IND, PAL, NEA, NEO],
    "trout": [PAL, NEA, NEO], "tench": [PAL, NEA],
}

# ---- the specialists ----------------------------------------------------------------------------
# §biomes-require: every group in the list must be present, where the `biomes` map is best-of. Only
# for fish that genuinely need a conjunction — a cold river, a jungle backwater — never as a second
# way of saying what `biomes` already says.
REQUIRE = {
    "taimen": ["cold", "river_biome"],
    "lenok": ["cold", "river_biome"],
    "grayling": ["cold", "river_biome"],
    "sculpin": ["cold", "river_biome"],
    "nelma": ["cold"],
    "whitefish": ["cold"],
    "burbot": ["cold"],
    "arapaima": ["warm", "jungle"],
    "peacock_bass": ["warm", "jungle"],
    "piraiba": ["warm", "jungle"],
    "red_piranha": ["warm", "jungle"],
    "oscar": ["warm", "jungle"],
    "golden_dorado": ["warm", "river_biome"],
    "bullseye_snakehead": ["warm", "swamp"],
    "mayan_cichlid": ["warm", "swamp"],
    "rotan": ["swamp"],
}

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(REPO, "common", "src", "main", "resources", "data", "riverfishing", "fish_profiles")


def put(raw, key, values):
    """Write one array field after "biomes" (or after "display"), textually — one line in the diff."""
    line = '  "%s": [%s],\n' % (key, ", ".join('"%s"' % v for v in values))
    raw = re.sub(r'^\s*"%s":.*\n' % key, "", raw, flags=re.M)
    m = re.search(r'^(\s*)"biomes":', raw, flags=re.M) or re.search(r'^(\s*)"display":.*\n', raw, flags=re.M)
    if not m:
        return raw.replace("{\n", "{\n" + line, 1)
    at = raw.rindex("\n", 0, m.start()) + 1 if raw[m.start():].startswith(" ") else m.start()
    return raw[:at] + line.replace("  ", m.group(1), 1) + raw[at:]


written, open_range = 0, []
for f in sorted(glob.glob(os.path.join(DIR, "*.json"))):
    sp = os.path.basename(f)[:-5]
    prov, req = PROVINCES.get(sp), REQUIRE.get(sp)
    if not prov and not req:
        open_range.append(sp)
        continue
    raw = io.open(f, encoding="utf-8").read()
    before = raw
    if prov:
        raw = put(raw, "provinces", prov)
    if req:
        raw = put(raw, "biomes_require", req)
    if raw != before:
        io.open(f, "w", encoding="utf-8", newline="\n").write(raw)
        written += 1

counts = {}
for sp, ps in PROVINCES.items():
    for p in ps:
        counts[p] = counts.get(p, 0) + 1
print("written %d profiles" % written)
print("freshwater species per province: " + ", ".join("%s %d" % (k, counts[k]) for k in sorted(counts)))
print("%d species left open (no province = everywhere): %s" % (len(open_range), ", ".join(open_range)))
