# -*- coding: utf-8 -*-
"""§founders: until the brood has spawned here, what comes out of the water is what went in.

    py -X utf8 tools/patches/p_founders.py <root> [1211|1201|26]

Reported, with cards: 250 kohaku fry released, genes `Ww Rr bb gg`, and the same world day the net
brought up kohaku, platinum, tancho and hi utsuri. Seven fish: 2 kohaku, 2 platinum, 1 tancho,
2 hi utsuri — which is, to the fish, the 9:3:3:1 of Ww Rr × Ww Rr. The genetics are right: a kohaku
is heterozygous at W and R by construction (WW RR bb IS a tancho), so kohaku × kohaku always throws
white, tancho and hi utsuri, and the fry's own card shows both hidden recessives.

What is wrong is the calendar. §brood-pool made every fish out of a stocked water the CROSS of two
released genomes — an F1 — from the moment of release. But nothing has spawned on day one. The fry that
went in are the fish that are in there, and until the brood has lived through a spawn window (which is
exactly what "settled" means in this ledger) a fish taken out should be one of THEM, grown or not.

So: not settled -> copy one founder. Settled -> cross two, as before. The switch is isStocked(), the
same flag tickSettle() raises on the closing day, so there is no second clock to drift.

And the net: it stamped an unsettled transplant "native" — its eco fallback was the wrong word. It now
asks the same question the rod asks: native here, settled here, or neither.

The pattern index still drifts a few units inside its family on a founder copy (#540 -> #546); the
roster stores genomes, not patterns. ponytail: store "genome|pattern" in the roster if an exact copy
ever matters — it is cosmetic inside one family.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
SD = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/StockedData.java")
NI = os.path.join(ROOT, "common/src/main/java/com/riverfishing/item/NetItem.java")

ELEM = (lambda l, i: '%s.getStringOr(%s, "")' % (l, i)) if D == "26" \
    else (lambda l, i: '%s.getString(%s)' % (l, i))

# ---- 1. the water hands back a founder until the brood has spawned --------------------------------
s = io.open(SD, encoding="utf-8").read()
if "§founders" in s:
    print("  StockedData: already patched")
else:
    old = """        if (!pool.isEmpty()) {
            String a = %s;
            String b = %s;""" % (ELEM("pool", "rng.nextInt(pool.size())"), ELEM("pool", "rng.nextInt(pool.size())"))
    assert old in s, "overlay()'s pool draw moved (dialect %s)" % D
    s = s.replace(old, """        if (!pool.isEmpty()) {
            // §founders: nothing has spawned here until the brood has lived through a window, which is
            // what settled means. Before that the fish in the water ARE the released ones, so a fish
            // taken out is one of them — 250 kohaku fry are 250 kohaku, not their F1 on day one.
            if (!isStocked(region, species)) {
                return lay(%s, rolled);
            }
            String a = %s;
            String b = %s;""" % (ELEM("pool", "rng.nextInt(pool.size())"),
                                 ELEM("pool", "rng.nextInt(pool.size())"),
                                 ELEM("pool", "rng.nextInt(pool.size())")), 1)
    io.open(SD, "w", encoding="utf-8", newline="\n").write(s)
    print("  StockedData: a founder until settled, a cross after")

# ---- 2. the net's eco word: the rod's question, not a guess ---------------------------------------
n = io.open(NI, encoding="utf-8").read()
if "§founders" in n:
    print("  NetItem: already patched")
else:
    old = '            String eco = stocked.isStocked(region, p.id.getPath()) ? "stocked" : "native";'
    assert old in n, "NetItem eco line moved"
    n = n.replace(old, """            // §founders: the same three answers the rod gives — an unsettled transplant is neither
            // native nor stocked, and the card used to call it native.
            String eco = com.riverfishing.fishing.FishingManager.nativeHere(level, pos, body, p.id) ? "native"
                    : stocked.isStocked(region, p.id.getPath()) ? "stocked" : "";""", 1)
    io.open(NI, "w", encoding="utf-8", newline="\n").write(n)
    print("  NetItem: eco is native / stocked / neither")
print("done (%s)" % D)
