# -*- coding: utf-8 -*-
"""Does the 1.20.1 rod render stack still SAY what canon says, once dialect is discounted?

    python tools/check_rod_parity.py

The check that was missing when the 3D rods were ported and every blank silently fell back to a sprite.
The port built perfectly the whole time: RodModelLayers had lost the one line that registers the segment
models for baking, and RodHandTransform its entire 3D pose set. A compiler proves a port COMPILES; only
this proves it still contains the feature.

Both were lost by a three-way merge doing exactly what a three-way merge should: the port had deleted
those lines back when it had no segmented blanks, canon@HEAD did not touch them, so git saw "ours
deleted, theirs unchanged" and kept the deletion silently. For a port that is several features behind,
its own deletions are not dialect — they are absence — so canon is the truth for these files and only
the API dialect is re-applied.

Both sides are rewritten into one canonical shape, so a legitimate 1.20.1 spelling vanishes and anything
else survives. Comments are dropped: they carry no behaviour, and a port is entitled to explain itself.
"""
import difflib, io, os, re, sys

CANON = r"C:\Users\Qwazar\VS Code Projects\fishing mod"
PORT = r"C:\Users\Qwazar\wt\rf1201"
J = os.path.join("common", "src", "main", "java", "com", "riverfishing", "client")

FILES = ["RodModelLayers.java", "RodHandTransform.java", "RodItemRenderer.java", "RodPodRenderer.java",
         "LineRenderer.java", "RodDebugCommand.java", "RodPhysics.java", "RodRenderTypes.java",
         "RodClientSettings.java", "ClientLineState.java", "ClientInit.java"]

# Each rule folds the two spellings of one API onto a single token. Deliberately crude: a rule that
# normalised too much would hide a real difference, which is the whole thing this exists to catch.
NORM = [
    (r"ResourceLocation\.fromNamespaceAndPath\(|new ResourceLocation\(", "RL("),
    (r"\.addVertex\(|\.vertex\(", ".V("),
    (r"\.setColor\(|\.color\(", ".C("),
    (r"\.setNormal\(|\.normal\(nrm, |\.normal\(nid, ", ".N("),
    (r"\.endVertex\(\);", ";"),
    (r"\.getTimer\(\)\.getGameTimeDeltaPartialTick\(false\)|\.getFrameTime\(\)", ".PT()"),
    (r"\.getTimer\(\)\.getGameTimeDeltaTicks\(\)|\.getDeltaFrameTime\(\)", ".DT()"),
    (r"pose\.mulPoseMatrix\(|pose\.mulPose\(", "pose.MUL("),
    (r"\.getUseDuration\(mc\.player\)|\.getUseDuration\(\)", ".USEDUR()"),
    (r"org\.joml\.Matrix3f nid = new org\.joml\.Matrix3f\(\);", ""),
]

# The ONE deliberate divergence, named so the check stays trustworthy. §hand-line submits its vertices
# with an identity matrix — view space — and depends on which matrix is live when the buffer flushes.
# 1.20.1 does not agree with 1.21.1 about that, and the far end of the line came out behind the camera:
# the tackle end flew over the angler's shoulder, on Fabric and Forge alike. 1.20.1 leaves the hand pass
# early so LineRenderer's world pass draws the string instead. Anything ELSE differing is still a fail.
EXPECTED = {"RodItemRenderer.java": ["if (true) return;"]}


def norm(text):
    out = []
    for line in text.split("\n"):
        line = re.sub(r"//.*$", "", line)
        for pat, repl in NORM:
            line = re.sub(pat, repl, line)
        line = re.sub(r"\s+", " ", line).strip()
        if line and not line.startswith(("*", "/*")):
            out.append(line)
    return out


bad = 0
for name in FILES:
    a = io.open(os.path.join(CANON, J, name), encoding="utf-8").read().replace("\r\n", "\n")
    b = io.open(os.path.join(PORT, J, name), encoding="utf-8").read().replace("\r\n", "\n")
    diff = [d for d in difflib.unified_diff(norm(a), norm(b), lineterm="", n=0)
            if d.startswith(("+", "-")) and not d.startswith(("+++", "---"))]
    diff = [d for d in diff if not any(e in d for e in EXPECTED.get(name, []))]
    if diff:
        bad += 1
        print("  %-24s %d line(s) differ beyond dialect:" % (name, len(diff)))
        for d in diff[:6]:
            print("      " + d[:150])
    else:
        print("  %-24s says exactly what canon says" % name)

if bad:
    print("\nFAILED: %d file(s) differ — in these files a difference is a feature that did not arrive" % bad)
    sys.exit(1)
print("\nthe 1.20.1 rod render stack matches canon, bar the one recorded divergence")
