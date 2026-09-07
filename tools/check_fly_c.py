# -*- coding: utf-8 -*-
"""§fly, stream C: the hatch table, the factor rules, the anchors, the wiki and the lang.

    py -X utf8 tools/check_fly_c.py [root]

Reads the TABLE literal straight out of Hatch.java and asserts what the design promised: every
season has a hatch at some hour, every hatch except the still-water scud is reachable from the table,
every size fits the 16 mm tying canvas (or ×1.5 could never happen), and the factor constants are
the ones on the wiki page. Then the wiring — the five patched files carry their §fly blocks — and the
wiki: the page is in GROUPS, exists in all three languages with the same heading count, each README
lists it, and the lang patch has identical key sets.
"""
import io, json, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
fails = []


def rd(rel):
    return io.open(os.path.join(ROOT, rel), encoding="utf-8").read()


def need(rel, must, why):
    if must not in rd(rel):
        fails.append("%s: %s (missing %r)" % (rel, why, must))


# ---- the table ---------------------------------------------------------------------------------------
hatch = rd("common/src/main/java/com/riverfishing/engine/Hatch.java")
names = ["MIDGE", "STONEFLY", "MAYFLY", "CADDIS", "TERRESTRIAL", "SCUD", "BAITFISH"]
sizes = {m.group(1): int(m.group(2)) for m in re.finditer(r'(\w+)\("\w+", TiedDesign\.Template\.\w+, (\d+)\)', hatch)}
if sorted(sizes) != sorted(names):
    fails.append("Hatch: expected %s, found %s" % (names, sorted(sizes)))
for n, mm in sizes.items():
    if mm > 16:
        fails.append("Hatch.%s is %d mm — the tying canvas is 16, so no fly could ever be within 3 mm of it" % (n, mm))
tbl = re.search(r"TABLE = \{(.*?)\n    \};", hatch, re.S)
rows = re.findall(r"\{([^{}]*)\}", tbl.group(1)) if tbl else []
table = [[c.strip() for c in r.split(",")] for r in rows]
if len(table) != 4 or any(len(r) != 4 for r in table):
    fails.append("Hatch.TABLE must be 4 seasons x 4 times, found %s" % [len(r) for r in table])
else:
    for i, season in enumerate(["SPRING", "SUMMER", "AUTUMN", "WINTER"]):
        if all(c == "null" for c in table[i]):
            fails.append("Hatch.TABLE: nothing hatches in %s at any hour" % season)
    seen = {c for r in table for c in r if c != "null"}
    for n in names:
        if n != "SCUD" and n not in seen and n != "BAITFISH":
            fails.append("Hatch.%s is never reached from the table" % n)
    if "BAITFISH" not in seen and "return BAITFISH" not in hatch:
        fails.append("Hatch.BAITFISH is never reached")
    unknown = seen - set(names)
    if unknown:
        fails.append("Hatch.TABLE names %s, which are not hatches" % sorted(unknown))
# the still-water fallback and the fair-weather ant
need("common/src/main/java/com/riverfishing/engine/Hatch.java", "h = SCUD;", "still water falls back to the scud")
need("common/src/main/java/com/riverfishing/engine/Hatch.java", "h == TERRESTRIAL && w != Weather.CLEAR", "ants need clear weather")
need("common/src/main/java/com/riverfishing/engine/Hatch.java", "if (w == Weather.THUNDER) return BAITFISH;", "a storm puts the fish on fry")
# the factor rules
for must, why in (("<= 3 ? 1.5 : 1.0", "right kind within 3 mm is 1.5, else 1.0"),
                  ("return 0.6;", "wrong kind is 0.6"),
                  ("case DRY_FLY -> 0.7;", "no hatch: dry fly 0.7"),
                  ("case STREAMER -> 0.9;", "no hatch: streamer 0.9"),
                  ("default -> 1.0;", "no hatch: everything else 1.0")):
    need("common/src/main/java/com/riverfishing/engine/Hatch.java", must, why)

# ---- the wiring ------------------------------------------------------------------------------------
need("common/src/main/java/com/riverfishing/engine/BiteContext.java", "public Hatch hatch;", "the context carries the hatch")
need("common/src/main/java/com/riverfishing/engine/BiteEngine.java", "best *= Hatch.factor(c.hatch, c.tied);", "baitScore multiplies by the hatch")
fm = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
s = rd(fm)
if s.count("ctx.hatch = ctx.rod == RodType.FLY ? Hatch.now(") != 2:
    fails.append("FishingManager: the hatch must be read in buildContext AND reEvaluate")
for must, why in (("startFlyRise(sp, session, now);", "the bite starts the rise on a fly rod"),
                  ("session.floatPeriod = 2 * window;", "the rise marker is a clock"),
                  ("session.ctx.hatch.particles(level, session.target);", "the water shows the hatch"),
                  ("message.riverfishing.fly_too_fast", "the early miss has its message"),
                  ("message.riverfishing.fly_too_slow", "the late miss has its message"),
                  ("SpookData.of(level).disturb(level, session.target, 0.5, 3.0, now);", "a miss puts the fish down")):
    need(fm, must, why)
# the rise's fish packet goes out with biting=true and the species: the long constructor
if not re.search(r"new LineSyncPacket\(sp\.getId\(\), true, session\.target, 0f, session\.lineColor,\s*session\.floatKind, true, 0f, 0f, false, false, \(byte\) 0,\s*session\.species", s):
    fails.append("FishingManager.startFlyRise must send the species with biting=true, fighting=false")
need("common/src/main/java/com/riverfishing/client/ClientLineState.java", "public long riseStart = -1;", "the client times the rise")
need("common/src/main/java/com/riverfishing/client/ClientLineState.java", "if (!p.species.isEmpty() || !p.biting || p.fighting) line.species = p.species;", "a refresh must not wipe the rising fish")
need("common/src/main/java/com/riverfishing/client/HookedFishRenderer.java", "if (!(state.fighting || state.biting) || state.species.isEmpty()", "the renderer draws the rise")
need("common/src/main/java/com/riverfishing/client/HookedFishRenderer.java", "riseY = Mth.lerp(rt, -0.4f, -0.05f);", "the body climbs from -0.4 to -0.05")
need("common/src/main/java/com/riverfishing/client/HookedFishRenderer.java", "risePitch = -35f;", "nose up on the rise")

# ---- the wiki ----------------------------------------------------------------------------------------
need("tools/gen_wiki_bundle.py", '"ice-fishing", "fly-fishing",', "the page must be in GROUPS or it is never published")
HEAD = re.compile(r"^#{1,6}\s", re.M)
counts = {}
for lang in ("", "ru/", "uk/"):
    rel = "docs/wiki/%sfly-fishing.md" % lang
    if not os.path.exists(os.path.join(ROOT, rel)):
        fails.append("%s missing — a language is complete or absent" % rel); continue
    counts[lang or "en"] = len(HEAD.findall(rd(rel)))
    need("docs/wiki/%sREADME.md" % lang, "(fly-fishing.md)", "the README must list the page")
if len(set(counts.values())) > 1:
    fails.append("fly-fishing.md heading counts differ: %s (wiki_anchors pairs them positionally)" % counts)
if not os.path.exists(os.path.join(ROOT, "docs/patchnotes/0.10.0.md")):
    fails.append("docs/patchnotes/0.10.0.md missing")
else:
    need("docs/patchnotes/0.10.0.md", "### Fly fishing", "the notes need a Fly fishing section")

# ---- the lang ----------------------------------------------------------------------------------------
lang = json.load(io.open(os.path.join(ROOT, "tools/patches/lang_fly_c.json"), encoding="utf-8"))
keys = {loc: set(d) for loc, d in lang.items()}
if set(keys) != {"en_us", "ru_ru", "uk_ua"}:
    fails.append("lang_fly_c.json: locales %s" % sorted(keys))
if len({frozenset(k) for k in keys.values()}) != 1:
    fails.append("lang_fly_c.json: key sets differ between locales")
for n in names:
    if "hatch.riverfishing." + n.lower() not in keys.get("en_us", ()):
        fails.append("lang_fly_c.json: no name for hatch %s" % n.lower())
for k in ("message.riverfishing.fly_too_fast", "message.riverfishing.fly_too_slow"):
    if k not in keys.get("en_us", ()):
        fails.append("lang_fly_c.json: missing %s" % k)
for loc, d in lang.items():
    for k, v in d.items():
        if re.search(r"%(?!s|\d\$s|%)", v):
            fails.append("%s %s: a format other than %%s / %%n$s silently drops every argument" % (loc, k))

if fails:
    print("check_fly_c: %d problem(s)" % len(fails))
    for f in fails:
        print("  - " + f)
    sys.exit(1)
print("check_fly_c: ok — %d hatches, table %dx%d, wiki in %d languages" % (len(sizes), len(table), len(table[0]) if table else 0, len(counts)))
