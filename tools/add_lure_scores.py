# -*- coding: utf-8 -*-
"""§lures: the four new artificial lures, written into each fish profile's "ideal.bait" map.

    py -X utf8 tools/add_lure_scores.py [tree]      # tree defaults to this script's own repo

One table, in this file, so a wrong guess is one line to correct. The entries are appended textually to
the end of the "bait" object — the way add_latin.py / add_spawn.py insert their line — so each file's
diff is only the new bait entries and its own layout (one-line or expanded) survives. A rerun that would
produce the same text writes nothing; a species dropped from the table has its entries removed again.

Scoring, from real fishing practice:
  spinnerbait  wire bait for weedy / coloured water — pike, bass, snakehead, dorado, asp; nothing for
               bottom feeders, nothing for clear-river salmonids, nothing at sea beyond the flats crowd.
  bladebait    flat vibrating blade, cold water and vertical jigging — zander/perch/burbot, cod & saithe
               from a boat; carp family and summer salmonids do not see it.
  swimbait     big soft or jointed body, the trophy end — nothing under ~1 kg mean weight.
  wacky_worm   finesse soft plastic — the bass-pond crowd; only species that already eat soft plastic.

Two rules the table obeys everywhere (asserted below, so a bad edit fails loudly):
  * a species whose bait map holds no artificial lure at all (pure bait/plant feeder — carp, bream,
    tench, crucian, roach, bluegill, rudd ...) gets none of the four;
  * a new score may not beat the species' best existing lure score by more than 0.10.
"""
import io, json, os, re, sys, glob

LURES = ("spinnerbait", "bladebait", "swimbait", "wacky_worm")

# the artificial-lure ids that already exist, used to calibrate and to police the +0.10 ceiling
KNOWN = ("spinner", "spoon", "wobbler", "silicone", "popper", "crankbait", "jig",
         "castmaster", "octopus_jig", "giant_spoon", "livebait", "fish_strip")

TABLE = {                       # species: (spinnerbait, bladebait, swimbait, wacky_worm), 0 = not taken
    # --- fresh-water predators -------------------------------------------------------------------
    "pike":               (0.90, 0.70, 1.00, 0),
    "zander":             (0.60, 1.00, 0.80, 0),
    "volga_zander":       (0,    0.95, 0,    0),     # 450 g mean — under the swimbait floor
    "perch":              (0.70, 0.95, 0,    0.85),
    "burbot":             (0,    0.80, 0.60, 0),
    "catfish":            (0,    0,    0.90, 0),
    "channel_catfish":    (0,    0,    0.70, 0),
    "largemouth_bass":    (1.10, 0,    1.00, 1.05),  # the spinnerbait's home water
    "peacock_bass":       (0.90, 0,    1.05, 0.90),
    "bullseye_snakehead": (1.00, 0,    0.95, 0),
    "golden_dorado":      (0.90, 0,    0.95, 0),
    "oscar":              (0,    0,    0,    1.00),
    "mayan_cichlid":      (0,    0,    0,    0.90),
    # --- cyprinids that hunt (the rest of the family takes none of the four) ----------------------
    "asp":                (0.85, 0.70, 0.70, 0.50),
    "chub":               (0.70, 0.60, 0,    0.60),
    "ide":                (0.60, 0.60, 0,    0.60),
    # "rudd":     named as a moderate wacky worm, but its bait map is bread/dough/maggot — no lure at
    # "bluegill": all, so both would break the "no lures today, none of the four" rule. Uncomment to add.
    # --- salmonids: clear cold water, they keep their spinners and spoons -------------------------
    # (trout, grayling, char, lenok, salmon, whitefish, smelt, pink_salmon, rainbow_trout: nothing)
    "taimen":             (0,    0,    0.95, 0),     # the one salmonid big enough for a swimbait
    # --- big fresh water ---------------------------------------------------------------------------
    "arapaima":           (0,    0,    0.85, 0),
    "piraiba":            (0,    0,    0.70, 0),
    # --- sea ---------------------------------------------------------------------------------------
    "cod":                (0,    0.85, 0.80, 0),
    "saithe":             (0,    0.80, 0.80, 0),
    "halibut":            (0,    0,    0.90, 0),
    "seabass":            (0,    0,    0.80, 0),
    "barracuda":          (0.70, 0,    1.05, 0),
    "jack_crevalle":      (0.80, 0,    1.10, 0),
    "snook":              (0.80, 0,    1.15, 0),
    "striped_bass":       (0,    1.00, 1.15, 0),
    "bluefish":           (0,    0,    1.10, 0),
    "tarpon":             (0,    0,    1.10, 0),
    "goliath_grouper":    (0,    0,    0.80, 0),
    "mahi":               (0,    0,    1.00, 0),
    "wahoo":              (0,    0,    1.00, 0),
    "yellowfin_tuna":     (0,    0,    1.00, 0),
    "mako":               (0,    0,    0.95, 0),
    "bull_shark":         (0,    0,    0.90, 0),
    # marlin / sailfish / swordfish stay on their trolling spread — a swimbait is not that fishery.
}

BAIT = re.compile(r'("bait"\s*:\s*\{)(.*?)(\})', re.S)      # bait values are scalars, so no nesting
MINE = re.compile(r',\s*"(?:%s)"\s*:\s*[-0-9.]+\s*$' % "|".join(LURES))


def num(v):
    s = "%g" % v
    return s if "." in s else s + ".0"


def patch(raw, scores):
    """Append (or refresh) our entries at the end of the bait object, keeping the file's layout."""
    m = BAIT.search(raw)
    inner = m.group(2)
    head, tail = inner.rstrip(), inner[len(inner.rstrip()):]
    while MINE.search(head):                                 # drop what an earlier run wrote
        head = MINE.sub("", head)
    if scores:
        ml = "\n" in inner
        ind = re.findall(r'\n([ \t]*)"', inner)[-1] if ml else ""
        sep = "\n" + ind if ml else " "
        head += "," + ",".join('%s"%s": %s' % (sep, k, num(v)) for k, v in scores)
    return raw[:m.start(2)] + head + tail + raw[m.end(2):]


tree = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(tree, "common", "src", "main", "resources", "data", "riverfishing", "fish_profiles")
written, unchanged, tally = 0, 0, dict.fromkeys(LURES, 0)
for f in sorted(glob.glob(os.path.join(DIR, "*.json"))):
    sp = os.path.basename(f)[:-5]
    raw = io.open(f, encoding="utf-8").read()
    scores = [(k, v) for k, v in zip(LURES, TABLE.get(sp, ())) if v]

    old = json.loads(raw)["ideal"].get("bait", {})
    best = max([old[k] for k in KNOWN if k in old] or [0])
    for k, v in scores:
        assert best > 0, "%s: takes no artificial lure today, so it may not take %s" % (sp, k)
        assert v <= best + 0.1001, "%s: %s %.2f beats its best lure %.2f by more than 0.10" % (sp, k, v, best)

    out = patch(raw, scores)
    if out == raw:
        unchanged += 1
    else:
        json.loads(out)                                      # never leave a profile unparseable
        io.open(f, "w", encoding="utf-8", newline="\n").write(out)
        written += 1
    for k, _ in scores:
        tally[k] += 1

missing = sorted(set(TABLE) - set(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(DIR, "*.json"))))
print("%s\nwritten %d, unchanged %d" % (DIR, written, unchanged))
print("  " + "  ".join("%s %d" % (k, tally[k]) for k in LURES))
if missing:
    print("  in the table but no such profile: " + ", ".join(missing))
