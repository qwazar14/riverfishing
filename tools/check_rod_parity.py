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

# The deliberate divergences, each named so the check stays trustworthy rather than becoming a list of
# things to ignore. Anything NOT here is still a failure.
#
# 1. §view-yaw — MEASURED, not assumed. /rfrod tipinfo on a 1.20.1 client returned dist 2.87 with
#    forward-dot -0.89: the tip is found at the right distance and pointing behind the angler. Right
#    length, wrong direction is a half turn, so this version's camera rotation and the hand pose
#    disagree about which axis is forward, and the two are reconciled with a rotateY(PI).
# 2. §hand-line — same file, the line is submitted through the pose it draws the blank with rather than
#    with an identity matrix. See the port's own comment.
# 3. /rfrod tipinfo — the diagnostic that produced (1). It has no canon counterpart and should not.
# RodDebugCommand carries /rfrod tipinfo, which has no canon counterpart and should not have one. The
# rule for it is not a list of strings to ignore but the thing that is actually true: this port may hold
# MORE than canon there, and must not hold less. An added line is fine; a removed one is still a failure.
ADD_ONLY = {"RodDebugCommand.java"}

EXPECTED = {
    "RodItemRenderer.java": ["rotateY((float) Math.PI)", "org.joml.Quaternionf q = new org.joml.Quaternionf(cam.rotation())",
                             "toLocal", "org.joml.Matrix4f m = new org.joml.Matrix4f(pose.last().pose())",
                             "org.joml.Matrix4f id = new org.joml.Matrix4f();", "m.determinant()",
                             "Vector3f l0", "Vector3f l1", ".V(m, l0", ".V(m, l1",
                             ".V(id, prev", ".V(id, p.x()", "drawHandLine(ItemStack stack,",
                             "MultiBufferSource buffers) {", "drawHandLine(stack, ctx, pose, buffers)",
                             "drawHandLine(stack, ctx, buffers)"],
    "LineRenderer.java": ["rotateY((float) Math.PI)", "cam3.rotation()", ".transform(new org.joml.Vector3f("],
}


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
    if name in ADD_ONLY:
        diff = [d for d in diff if d.startswith("-")]
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
