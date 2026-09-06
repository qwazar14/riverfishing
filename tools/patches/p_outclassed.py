# -*- coding: utf-8 -*-
"""§outclassed: a fish that out-pulls the line cannot be reeled — only played out on an open drag.

    py -X utf8 tools/patches/p_outclassed.py <root>

"People have caught 200 lb fish on 6 lb line — it took hours." The model already said so halfway:
tension is a real breaking strain and the drag pays out. What it lacked was the other half: with the
line weaker than the pull (tackleMargin < 0.85) the fight was simply unwinnable inside its clock.
Now such a fight is a different game — the fish is not reeled, it is worn down:
  * the clock stretches by 1/margin, up to six times;
  * a crank on a closed drag during a directed run puts the line straight into overstress;
  * a crank between runs gains a third of a normal one;
  * an OPEN drag during a run no longer bleeds progress — the fish tires against it, and that fatigue
    IS the progress, faster when the rod is held across the run.
No message: the bar goes red the first time you crank into a run, which is the whole lesson.
"""
import io, os, sys

ROOT = sys.argv[1]
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing")
S, M = os.path.join(J, "FishingSession.java"), os.path.join(J, "FishingManager.java")
s = io.open(S, encoding="utf-8").read()
if "outclassed" in s:
    print("  already patched"); sys.exit(0)
old = "    public double tackleMargin = 1.0;\n"
assert s.count(old) == 1
s = s.replace(old, old + "    public boolean outclassed;      // §outclassed: the line is weaker than the pull — play it out, never reel it\n", 1)
io.open(S, "w", encoding="utf-8", newline="\n").write(s)

m = io.open(M, encoding="utf-8").read()


def rep(old, new, n=1):
    global m
    assert m.count(old) == n, old
    m = m.replace(old, new)


rep("        session.requiredKg = requiredKg; // §tackle-stress: for the break-load message\n",
    "        session.requiredKg = requiredKg; // §tackle-stress: for the break-load message\n"
    "        session.outclassed = session.tackleMargin < 0.85;   // §outclassed\n")
rep('                        : "greyhounding".equals(profile.fightPattern) ? 400 : 0), 900, 3400);\n',
    '                        : "greyhounding".equals(profile.fightPattern) ? 400 : 0), 900, 3400);\n'
    "        // §outclassed: a fish the line cannot hold is played out, not reeled — give it the time.\n"
    "        if (session.outclassed) {\n"
    "            session.fightTimeout = (long) (session.fightTimeout * Mth.clamp(1.0 / Math.max(0.05, session.tackleMargin), 1.0, 6.0));\n"
    "        }\n")
rep("            session.landProgress = Math.max(0.0, session.landProgress\n"
    "                    - (session.runTicksLeft > 0 ? 0.004 : 0.0025));\n",
    "            // §outclassed: the open drag is the only way to win — the fish plays itself out against\n"
    "            // it, and what it loses is what you gain, faster with the rod held across the run.\n"
    "            if (session.outclassed && session.runTicksLeft > 0) {\n"
    "                session.landProgress = Math.min(1.0, session.landProgress + session.fatigueRunTick * 0.55 * courseGain);\n"
    "            } else {\n"
    "                session.landProgress = Math.max(0.0, session.landProgress\n"
    "                        - (session.runTicksLeft > 0 ? 0.004 : 0.0025));\n"
    "            }\n")
rep("        boolean inRun = session.runTicksLeft > 0;\n        // Reeling in a run spikes tension and barely gains line",
    "        boolean inRun = session.runTicksLeft > 0;\n"
    "        // §outclassed: winding into a run on a line the fish out-pulls is the line, gone — straight to\n"
    "        // overstress; the bar goes red and the snap follows unless the drag opens now.\n"
    "        if (session.outclassed && inRun && session.course.isRun()) {\n"
    "            session.tension = Math.max(session.tension, session.breakTension * 1.02);\n"
    "        }\n"
    "        // Reeling in a run spikes tension and barely gains line")
rep("                        * (1.0 + 0.6 * session.fatigue) * armStrength, 0.0, 1.0);\n",
    "                        * (1.0 + 0.6 * session.fatigue) * armStrength\n"
    "                        * (session.outclassed ? 0.35 : 1.0), 0.0, 1.0);   // §outclassed: a crank cannot win this one\n")
io.open(M, "w", encoding="utf-8", newline="\n").write(m)
print("  outclassed: the clock stretches, a crank into a run overstresses, the open drag wins")
