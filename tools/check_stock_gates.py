# -*- coding: utf-8 -*-
"""§founders / §cull-gate: two guards that live in the middle of long functions and fail silently.

    py -X utf8 tools/check_stock_gates.py [root]

Both were lost once already, the same way: a rework of the surrounding function kept every line that
compiled and dropped the one that gated. Neither has a compiler error, a log line or a test to lose.

  1. StockedData.overlay(): the "not settled -> copy a founder" branch sits BEFORE the cross. Without
     it a pond of 250 kohaku fry hands out their F1 on the day they went in (reported, with cards).
  2. FishingManager.release(): the cull check sits BEFORE the fit is computed. Without it a culled
     species is stocked back in by anyone, settles, and clears its own cull.
  3. FishingManager.stockedPresence(): the lambda returns 0 for a culled species before it reads the
     temporary surplus. Without it "the fish stops biting" is untrue for a species stocked then culled.
  4. NetItem: the card's eco word is native / stocked / "" — the rod's three answers — not a guess.

Each is checked by ORDER in the source, not by presence: the guard existing after the thing it guards
is the exact failure this file is for.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
fails = []


def body(path, start_pat, what):
    s = io.open(path, encoding="utf-8").read()
    m = re.search(start_pat, s)
    if not m:
        fails.append("%s: cannot find %s" % (os.path.basename(path), what))
        return ""
    depth, i = 0, m.end()
    # walk to the closing brace of the method body
    while i < len(s):
        if s[i] == "{": depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0: break
        i += 1
    return s[m.start():i]


def before(text, first, second, msg):
    a, b = text.find(first), text.find(second)
    if a < 0 or b < 0:
        fails.append(msg + " (missing: %s)" % ("guard" if a < 0 else "the thing it guards"))
    elif a > b:
        fails.append(msg + " (the guard is AFTER what it guards)")


# 1. overlay(): founders before the cross
ov = body(os.path.join(J, "fishing/StockedData.java"), r"public String overlay\(long region, String species, String rolled", "overlay()")
before(ov, "if (!isStocked(region, species))", "Genome.cross(a, b, rng)",
       "overlay(): a not-yet-settled water must hand back a FOUNDER before it crosses two")

# 2. release(): cull before fit
rl = body(os.path.join(J, "fishing/FishingManager.java"), r"private static void release\(ServerLevel level, BlockPos pos, FishProfile p, double units", "release()")
before(rl, "isCulled(region, id)", "BiteEngine.environmentScore(p, habitatContext(",
       "release(): a culled species must be refused BEFORE the fit is computed")
if "message.riverfishing.cull_done" not in rl:
    fails.append("release(): the cull refusal has no message — the fish just vanishes")

# 3. stockedPresence(): culled -> 0 before the surplus
sp = body(os.path.join(J, "fishing/FishingManager.java"), r"public static java\.util\.function\.ToDoubleFunction<\w+> stockedPresence\(", "stockedPresence()")
before(sp, "isCulled(region, s)) return 0.0", "surplusAround(",
       "stockedPresence(): a culled species must read 0 BEFORE the temporary surplus is consulted")

# 4. the net's eco word
ni = io.open(os.path.join(J, "item/NetItem.java"), encoding="utf-8").read()
m = re.search(r'String eco = (.*?);', ni, re.S)
if not m:
    fails.append("NetItem: no eco assignment")
else:
    e = m.group(1)
    if "nativeHere(" not in e or '"stocked"' not in e or '""' not in e:
        fails.append("NetItem: eco must be native / stocked / \"\" — it was guessing \"native\" for an unsettled transplant")

# 5. §net-ledger: the net pays the ledger through the rod's function, not a copy of half of it
if "broodAfterCatch(level, sp, pos, p.id)" not in ni:
    fails.append("NetItem: a netted fish must go through FishingManager.broodAfterCatch — a private copy "
                 "of the settled half left an unsettled brood uncounted (reported: 120 fish netted, sounder unchanged)")
if "stocked.takeAdult(" in ni:
    fails.append("NetItem: still carries its own takeAdult — that is the copy broodAfterCatch replaces")
fm = io.open(os.path.join(J, "fishing/FishingManager.java"), encoding="utf-8").read()
if "public static void broodAfterCatch(" not in fm:
    fails.append("FishingManager.broodAfterCatch is not public — NetItem cannot reach it")

# 6. §fry-bank: fry bank nothing on release; the bank skips zero; maturity banks the fish
if not re.search(r"release\(level, pos, p, 0\.0, thrower", fm):
    fails.append("releaseFry() banks stock units on release — a water of week-old fry then nets 3 kg adults")
before(rl, "if (units > 0)", "pressure.addStock(", "release(): addStock must be guarded on units > 0 (it floors at 0.01 otherwise)")
sd = io.open(os.path.join(J, "fishing/StockedData.java"), encoding="utf-8").read()
md = body(os.path.join(J, "fishing/StockedData.java"), r"public void matureIfDue\(ServerLevel level, long region, String species\)", "matureIfDue()")
before(md, "matureFry(t)", "addStock(", "matureIfDue(): the matured fish must be banked AFTER matureFry() says how many")
if "return mature;" not in sd:
    fails.append("StockedData.matureFry() no longer returns the count — matureIfDue() banks nothing")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("stock gates: founders before the cross, cull before the fit and before the surplus, the net's eco honest, "
      "the net pays the ledger, fry bank nothing until they are fish")
