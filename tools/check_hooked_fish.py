# -*- coding: utf-8 -*-
"""§hooked-fish: the fish on the line is wired end to end, and in the order that makes it move.

    py -X utf8 tools/check_hooked_fish.py [root]

  1. LineSyncPacket carries species / weight / length / jumping / shaking / fatigue, written AND read
     in the same order (a field written and not read desyncs every packet after it).
  2. The fight send fires on the hook's first ticks — "straight away", not up to five ticks later.
  3. The client integrates the body (tickFish) BEFORE the line is drawn, and lineEnd() ends the line
     on the body (fishAt) — otherwise the string and the fish part company.
  4. The renderer exists and is called from the line pass.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
rd = lambda p: io.open(os.path.join(J, p), encoding="utf-8").read()
pk, fm, cs, lr = rd("network/LineSyncPacket.java"), rd("fishing/FishingManager.java"), rd("client/ClientLineState.java"), rd("client/LineRenderer.java")
fails = []
w = [m for m in re.findall(r"buf\.write(\w+)\(", pk)]
r = [m for m in re.findall(r"buf\.read(\w+)\(", pk)]
if w != r:
    fails.append("LineSyncPacket writes %s but reads %s" % (w, r))
for f in ("species", "weightG", "lengthCm", "jumping", "shaking", "fatigue"):
    if "public final" not in pk or ("public final " not in pk) or (" %s;" % f) not in pk:
        fails.append("LineSyncPacket has no %s" % f)
if "now - session.fightStartTick < 2" not in fm:
    fails.append("the fight send must fire on the hook's first ticks — the body must be on the line straight away")
if "session.species == null ? \"\" : session.species.getPath()" not in fm:
    fails.append("the fight send does not carry the species")
i, j = lr.find("state.tickFish("), lr.find("renderLine(mc,")
if i < 0 or j < 0 or i > j:
    fails.append("LineRenderer: tickFish must run BEFORE renderLine, or the string ends where the fish was last frame")
if "state.fishAt(end)" not in lr:
    fails.append("LineRenderer.lineEnd() must end the line on the fish (fishAt)")
if "HookedFishRenderer." not in lr:
    fails.append("LineRenderer never draws the hooked fish")
if not os.path.exists(os.path.join(J, "client/HookedFishRenderer.java")):
    fails.append("HookedFishRenderer.java is missing")
if "public void tickFish(" not in cs or "public net.minecraft.world.phys.Vec3 fishAt(" not in cs:
    fails.append("ClientLineState.Line lacks tickFish/fishAt")
if fails:
    print("FAILED:")
    for f in fails:
        print("  " + f)
    sys.exit(1)
print("hooked fish: packet symmetric, sent on the hook, body integrated before the line, line ends on the fish, renderer wired")
