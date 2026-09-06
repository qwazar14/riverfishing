# -*- coding: utf-8 -*-
"""§outclassed: four rules, each checked by ORDER where order is what makes it work.

    py -X utf8 tools/check_outclassed.py [root]
"""
import io, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing")
m = io.open(os.path.join(J, "FishingManager.java"), encoding="utf-8").read()
s = io.open(os.path.join(J, "FishingSession.java"), encoding="utf-8").read()
fails = []


def before(a, b, msg):
    i, j = m.find(a), m.find(b)
    if i < 0 or j < 0:
        fails.append(msg + " (missing)")
    elif i > j:
        fails.append(msg + " (wrong order)")


if "public boolean outclassed" not in s:
    fails.append("FishingSession has no outclassed flag")
before("session.tackleMargin = effectiveStrain", "session.outclassed = session.tackleMargin < 0.85",
       "outclassed must be read off tackleMargin AFTER it is computed")
before("if (session.outclassed) {\n            session.fightTimeout", "session.fatigueRunTick = 1.0 / Math.min(",
       "the stretched clock must be set BEFORE fatigueRunTick reads fightTimeout, or the fish tires on the short clock")
if "session.breakTension * 1.02" not in m:
    fails.append("a crank into a run must put the line into overstress")
if "session.fatigueRunTick * 0.55 * courseGain" not in m:
    fails.append("the open drag must turn the fish's fatigue into progress")
before("double courseGain = ", "session.fatigueRunTick * 0.55 * courseGain",
       "courseGain must exist before the open-drag gain uses it")
if "(session.outclassed ? 0.35 : 1.0)" not in m:
    fails.append("a crank between runs must gain a third, not a full pull")
if fails:
    print("FAILED:")
    for f in fails:
        print("  " + f)
    sys.exit(1)
print("outclassed: flag after margin, clock before fatigue, a crank into a run overstresses, the open drag tires the fish into the net")
