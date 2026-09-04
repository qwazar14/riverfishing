# -*- coding: utf-8 -*-
"""§shake-dive: a head-shake is not a dive, and must not be charged as one.

    py -X utf8 tools/patches/p_shakedive.py <root> [1211|1201|26]

Reported by K1rhgoff: the beluga still cannot be landed, not even at its minimum 40 kg. Third report on
the same fish, and this time the cause is a line of arithmetic that two earlier fixes walked past.

A head-shake borrows the run fields — `runTicksLeft = 6 + rand(6)` and `runTicksTotal` with it — because
a shake is a brief thrash and the fight already knows how to be busy. The sounding pattern's dive drain
reads exactly those fields:

    if ("sounding".equals(pattern) && runTicksLeft > 0)
        landProgress -= DIVE_COST / runTicksTotal;

In 0.5.0 that line was a flat 0.0035 a tick, so a shake cost 0.02 of the bar and nobody noticed. §dive-cost
then rewrote it as a SHARE OF THE SPAN so a dive would cost 0.30 whatever its length — correct for a dive,
and it turned every six-tick head-shake into a whole dive as well. A beluga shakes about every two seconds
between runs (0.0245 a tick, 40 kg is past the cap), which is 44 shakes and thirteen extra bars of drain in
a three-minute fight. The bar cannot be filled that fast by anyone. It was not a hard fish, it was
arithmetic.

The fix is the distinction the code already has: a SCRIPTED run gets a course from FightCourse.forPattern,
which never returns NONE; a head-shake sets no course, and the run that ends clears it. So `course.isRun()`
is exactly "this is a real run", and every other consumer of runTicksLeft in the fight already asks it.

tools/check_fight_budget.py is the check, and it fails on the un-gated line as well as on the arithmetic.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java")

s = io.open(P, encoding="utf-8").read()
if "shake-dive" in s:
    print("  already patched")
    sys.exit(0)

old = '''        if ("sounding".equals(session.fightPattern) && session.runTicksLeft > 0) {'''
assert old in s, "the sounding dive block moved"
s = s.replace(old, '''        // §shake-dive: …and only for a REAL run. A head-shake borrows runTicksLeft/runTicksTotal for
        // its six ticks, so before this line asked, every shake was billed a whole DIVE_COST — about
        // forty of them in a beluga fight, thirteen bars of drain nobody could wind back. A scripted
        // run always carries a course (FightCourse.forPattern never returns NONE) and a shake never
        // does, which is the distinction every other reader of runTicksLeft in this fight already makes.
        if ("sounding".equals(session.fightPattern) && session.runTicksLeft > 0
                && session.course.isRun()) {''', 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  FishingManager: the dive drain is charged for dives only")
print("done (%s)" % D)
