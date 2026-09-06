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
# §line-snag: the server clips the line every fourth tick, BEFORE the crank reads lineSnagged; the
# client draws the kink from the same clip
if "tickSnag(sp, level, session, now);" not in fm or "private static void tickSnag(" not in fm:
    fails.append("the fight tick must clip the line (tickSnag) — the snag is server-authoritative")
if "(session.lineSnagged ? 0.0 : 1.0)" not in fm or "(session.lineSnagged ? 1.6 : 1.0)" not in fm:
    fails.append("a snagged line must hold the crank (gain 0) and rub (tension x1.6)")
if "horizontalDistanceSqr() > 2.5 * 2.5" not in fm:
    fails.append("a hit within 2.5 blocks of the angler is his own pier and must not count")
if "snagPoint(mc, tip, end)" not in lr or "static Vec3 snagPoint(" not in lr:
    fails.append("LineRenderer must draw the kink from its own clip when the packet says snagged")
if "jx = sideX * j" not in cs:
    fails.append("the shudder must be a display offset (jx/jz), never folded into the eased position")
if fails:
    print("FAILED:")
    for f in fails:
        print("  " + f)
    sys.exit(1)
print("hooked fish: packet symmetric, sent on the hook, body integrated before the line, line ends on the fish, renderer wired; the snag is clipped on the server, held at the crank, kinked on the client")
