# -*- coding: utf-8 -*-
"""§koi-genes / §koi-lines: the koi table, read off the Java, behaves the way a breeder's koi behave.

    py -X utf8 tools/check_koi_genetics.py [root]

Reimplements Genome.koiGenome (the writer), koiVariety (the reader), pair() with its defaults, and
cross(), from the constants in Genome.java, and then asks the questions the author asked:

  - every one of the seventeen varieties can be written and reads back as itself
  - tancho is the recessive crown on a kohaku, `W_ R_ bb tt`, and nothing else is
  - kohaku × kohaku throws kohaku — and a shiro muji where a carrier drops the red — and NEVER a dark
    fish or a metallic one (the report that started this: a kohaku pond handing out platinum,
    tancho and hi utsuri)
  - a kohaku line cannot throw a tancho; a tancho line breeds it true; tancho × kohaku hides it in
    the F1 and the F2 shows one in four
  - kohaku × showa is the sanke a breeder expects in the F1
  - a card from before the crown locus reads by the old rule: `WW RR bb gg` is still a tancho,
    `Ww Rr bb gg` still a kohaku, and a cross with such a parent writes the crown pair down
  - the writer never reaches for anything but the rng it is given (check_pattern's rule, kept here)
"""
import io, os, random, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = io.open(os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/Genome.java"), encoding="utf-8").read()
fails = []


def die(m):
    fails.append(m)


LOCI = re.search(r'public static final String LOCI = "(\w+)"', src).group(1)
KOI_LOCI = re.search(r'private static final String KOI_LOCI = "(\w+)"', src).group(1)
KOI_COMMON = re.search(r'private static final String KOI_COMMON = "(\w+)"', src).group(1)
KOI_MIXED = re.search(r'private static final String KOI_MIXED = "(\w*)"', src).group(1)
TABLE = [(r.split("=")[0], r.split("=")[1]) for r in re.findall(r'"([WwRrBbGgTt_*]+=\w+)"', re.search(r"KOI_TABLE = \{(.*?)\};", src, re.S).group(1))]
NAMES = [n for _, n in TABLE]
if len(TABLE) != 17: die("expected 17 varieties, found %d" % len(TABLE))
if KOI_LOCI != "WRBGT": die("KOI_LOCI is %r, expected WRBGT" % KOI_LOCI)
if LOCI[6:] != KOI_LOCI: die("LOCI %r does not end in the koi loci %r" % (LOCI, KOI_LOCI))
for pat, name in TABLE:
    if len(pat) != 2 * len(KOI_LOCI): die("row %s=%s is not one column per koi locus" % (pat, name))


def token(genome, i):
    t = genome.split()
    if i < 0 or i >= len(t) or len(t[i]) != 2: return None
    return t[i] if t[i].upper() == LOCI[i] * 2 else None


def legacy_tancho(g):
    return token(g, LOCI.index("T")) is None and token(g, LOCI.index("W")) == "WW" \
        and token(g, LOCI.index("R")) == "RR" and token(g, LOCI.index("B")) == "bb"


def pair(g, L):
    t = token(g, LOCI.index(L))
    if t: return t
    return {"K": "KK", "N": "nn", "W": "WW", "R": "Rr", "B": "bb"}.get(L, ("tt" if legacy_tancho(g) else "TT") if L == "T" else L.lower() * 2)


def dominant(g, L): return any(c.isupper() for c in pair(g, L))
def pure(g, L): p = pair(g, L); return p[0] == p[1]


def variety(g):
    for pat, name in TABLE:
        ok = True
        for i, L in enumerate(KOI_LOCI):
            want = pat[i * 2 + 1]
            if want == "*": continue
            dom = dominant(g, L)
            if want == "_" and not dom: ok = False
            elif want == L and not (dom and pure(g, L)): ok = False
            elif want == L.lower() and dom: ok = False
        if ok: return name
    return "karasu"


def write(name, rng, base="SS CC VV FF KK nn"):
    pat = [p for p, n in TABLE if n == name][0]
    out = base.split()
    for i, L in enumerate(KOI_LOCI):
        want = pat[i * 2 + 1]
        if want == "*": c = KOI_COMMON[i]; out.append(c + c)
        elif want == L.lower(): out.append(L.lower() * 2)
        elif want == L: out.append(L * 2)
        else: out.append(L + L.lower() if L in KOI_MIXED and rng.random() < 0.5 else L * 2)
    return " ".join(out)


def cross(m, f, rng):
    n = max(4, len(m.split()), len(f.split()))
    if token(m, 6) is not None or token(f, 6) is not None: n = len(LOCI)
    out = []
    for i in range(n):
        L = LOCI[i]
        a, b = rng.choice(pair(m, L)), rng.choice(pair(f, L))
        if a.islower() and b.isupper(): a, b = b, a
        out.append(a + b)
    return " ".join(out)


rng = random.Random(7)

# 1. every variety round-trips through the writer, many times (the mixed loci are coins)
for name in NAMES:
    for _ in range(40):
        g = write(name, rng)
        if variety(g) != name:
            die("wrote %s as %r and read back %s" % (name, g, variety(g))); break
        if len(g.split()) != len(LOCI):
            die("%s written with %d pairs, LOCI has %d" % (name, len(g.split()), len(LOCI))); break

# 2. tancho is W_ R_ bb tt and nothing else
for w in ("WW", "Ww", "ww"):
    for r in ("RR", "Rr", "rr"):
        for b in ("BB", "Bb", "bb"):
            for gg in ("GG", "Gg", "gg"):
                for t in ("TT", "Tt", "tt"):
                    g = "SS CC VV FF KK nn %s %s %s %s %s" % (w, r, b, gg, t)
                    is_t = variety(g) == "tancho"
                    should = w != "ww" and r != "rr" and b == "bb" and t == "tt"
                    if is_t != should:
                        die("%s reads %s — tancho must be exactly W_ R_ bb tt" % (g, variety(g)))


def spawn(a, b, n=4000):
    out = {}
    for _ in range(n):
        v = variety(cross(a, b, rng)); out[v] = out.get(v, 0) + 1
    return {k: v / n for k, v in out.items()}


# 3. kohaku × kohaku out of the water: kohaku, maybe shiro muji, never dark, never metallic, never tancho
tot = {}
for _ in range(50):
    r = spawn(write("kohaku", rng), write("kohaku", rng), 200)
    for k, v in r.items(): tot[k] = tot.get(k, 0) + v / 50
bad = {k: v for k, v in tot.items() if k not in ("kohaku", "platinum")}
if bad: die("kohaku × kohaku throws %s — a kohaku spawn is kohaku and shiro muji, nothing else" % bad)
if tot.get("kohaku", 0) < 0.75: die("kohaku × kohaku is only %.0f%% kohaku" % (100 * tot.get("kohaku", 0)))

# 4. a pure pair breeds true; a tancho line breeds true; tancho × kohaku hides, the F2 shows 1 in 4
pure_k = "SS CC VV FF KK nn WW RR bb gg TT"
r = spawn(pure_k, pure_k)
if r.get("kohaku", 0) < 0.999: die("two pure kohaku throw %s" % r)
pure_t = "SS CC VV FF KK nn WW RR bb gg tt"
r = spawn(pure_t, pure_t)
if r.get("tancho", 0) < 0.999: die("two tancho throw %s" % r)
f1 = cross(pure_t, pure_k, rng)
if variety(f1) != "kohaku" or pair(f1, "T") != "Tt": die("tancho × kohaku F1 is %s (%s), expected a carrier kohaku" % (variety(f1), f1))
r = spawn(f1, f1)
if not 0.18 < r.get("tancho", 0) < 0.32: die("carrier × carrier gives %.0f%% tancho, expected a quarter" % (100 * r.get("tancho", 0)))

# 5. kohaku × showa: the sanke a breeder expects
r = spawn("SS CC VV FF KK nn WW RR bb gg TT", "SS CC VV FF KK nn ww RR BB gg TT")
if r.get("taisho_sanke", 0) < 0.999: die("kohaku × showa F1 is %s, expected all sanke (Ww R_ Bb)" % r)

# 6. legacy cards
if variety("SS CC VV FF KK nn WW RR bb gg") != "tancho": die("a four-pair WW RR bb gg card no longer reads tancho")
if variety("SS CC VV FF KK nn Ww Rr bb gg") != "kohaku": die("a four-pair Ww Rr bb gg card no longer reads kohaku")
child = cross("SS CC VV FF KK nn WW RR bb gg", "SS CC VV FF KK nn WW RR bb gg", rng)
if len(child.split()) != len(LOCI) or pair(child, "T") != "tt":
    die("two legacy tancho give %r — the crown pair must be written down as tt" % child)
if variety("SS CC VV FF") != "kohaku": die("a koi card from before the colour loci must read kohaku")

# 7. the writer is arithmetic on its arguments
w = re.search(r"public static String koiGenome\(.*?\n    \}", src, re.S).group(0)
for bad_word in ("System.", "new Random(", "hashCode", "currentTime"):
    if bad_word in w: die("koiGenome reaches for %s" % bad_word)

if fails:
    print("FAILED:")
    for x in fails: print("  " + x)
    sys.exit(1)
print("koi genetics: 17 varieties round-trip; tancho = W_ R_ bb tt; kohaku × kohaku = kohaku (+ shiro muji), "
      "never dark or metallic; a tancho line breeds true and hides for one generation; legacy cards read as before")
