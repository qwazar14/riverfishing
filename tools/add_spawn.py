# -*- coding: utf-8 -*-
"""§breeding: the spawning window, written into every fish profile as one "spawn" line.

    py -X utf8 tools/add_spawn.py

One table, in this file, so a wrong guess is one line to correct: a default per family, then the species
that do it differently in real life. Textual insert right after the "latin" line — the way add_latin.py
does it — so each file's diff is one line and its own layout survives. Reruns are no-ops.

Calendar: 24-day seasons, "sub" = which 8-day third (early/mid/late), absent = the whole season.
FishProfile.defaultSpawnSeason/defaultSpawnSub in Java carry the SAME group table for profiles that
never got the line (datapacks).
"""
import io, json, os, glob, re

GROUP = {                       # (season, sub|None)
    "cyprinid": ("spring", "late"),
    "predator": ("spring", "early"),
    "salmonid": ("autumn", "mid"),
    "sturgeon": ("spring", "late"),
    "sea":      ("spring", "late"),
    "big_game": ("summer", None),
    "koi":      ("spring", "late"),
}

SPECIES = {
    # predators: the ice-off spawners first, then the warm-water ones
    "pike": ("spring", "early"), "perch": ("spring", "mid"), "zander": ("spring", "late"),
    "volga_zander": ("spring", "late"), "burbot": ("winter", "mid"),
    "catfish": ("summer", "early"), "channel_catfish": ("summer", "early"),
    # the eel does not spawn in fresh water at all (Sargasso Sea, late winter); it needs a value like
    # everyone else, so it gets the sea's late winter and the aquarium will simply never see it happen.
    "eel": ("winter", "late"),
    # cyprinids: the early runners, then the warm-water carp family
    "asp": ("spring", "mid"), "ide": ("spring", "early"), "roach": ("spring", "mid"),
    "bream": ("spring", "late"), "common_dace": ("spring", "early"), "nase": ("spring", "early"),
    "carp": ("summer", "early"), "wild_carp": ("summer", "early"), "mirror_carp": ("summer", "early"),
    "naked_carp": ("summer", "early"), "linear_carp": ("summer", "early"), "tench": ("summer", "early"),
    "crucian_carp": ("summer", "early"), "golden_crucian": ("summer", "early"),
    "grass_carp": ("summer", "early"), "silver_carp": ("summer", "early"),
    # salmonids: autumn gravel, except the spring ones
    "grayling": ("spring", "mid"), "smelt": ("spring", "early"),
    "trout": ("autumn", "mid"), "rainbow_trout": ("spring", "mid"), "lenok": ("spring", "mid"),
    "taimen": ("spring", "mid"), "char": ("autumn", "mid"), "whitefish": ("autumn", "mid"),
    "salmon": ("autumn", "mid"), "pink_salmon": ("autumn", "mid"),
    # sturgeon
    "sterlet": ("spring", "late"), "sturgeon": ("spring", "late"), "beluga": ("spring", "late"),
    # sea
    "cod": ("winter", "late"), "herring": ("spring", "early"), "mackerel": ("summer", None),
    "flounder": ("winter", None),
    # §deep-twelve (0.9.0): the sea giants spawn in the warm season, the deep pair have no season at
    # all down there, and the nelma runs upriver in late autumn like the whitefish it used to be.
    "mullet": ("autumn", "late"), "pollock": ("winter", "mid"), "nelma": ("autumn", "late"),
    "anglerfish": ("spring", "early"), "blobfish": ("summer", None),
    "black_marlin": ("summer", None), "bluefin_tuna": ("summer", "early"),
    "whale_shark": ("summer", None), "ocean_sunfish": ("summer", None),
    "tiger_shark": ("summer", None), "red_piranha": ("summer", "early"),
    "loach": ("spring", "late"),
}

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(REPO, "common", "src", "main", "resources", "data", "riverfishing", "fish_profiles")
written, unchanged, defaulted = 0, 0, []
for f in sorted(glob.glob(os.path.join(DIR, "*.json"))):
    sp = os.path.basename(f)[:-5]
    raw = io.open(f, encoding="utf-8").read()
    if sp in SPECIES:
        season, sub = SPECIES[sp]
    else:
        season, sub = GROUP[json.loads(raw).get("group", "cyprinid")]
        defaulted.append(sp)
    body = '"season": "%s"' % season + (', "sub": "%s"' % sub if sub else "")
    if '"spawn": { %s },' % body in raw:
        unchanged += 1
        continue
    raw = re.sub(r'^\s*"spawn":.*\n', "", raw, flags=re.M)
    m = re.search(r'^(\s*)"latin":.*\n', raw, flags=re.M)
    line = '%s"spawn": { %s },\n' % (m.group(1) if m else "  ", body)
    raw = raw[:m.end()] + line + raw[m.end():] if m else raw.replace("{\n", "{\n" + line, 1)
    io.open(f, "w", encoding="utf-8", newline="\n").write(raw)
    written += 1
print("written %d, unchanged %d" % (written, unchanged))
print("group default: " + ", ".join(defaulted))
