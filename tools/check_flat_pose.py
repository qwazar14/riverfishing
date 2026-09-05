# -*- coding: utf-8 -*-
"""§fish-pose: exactly the flatfish lie down, and nothing else does.

    py -X utf8 tools/check_flat_pose.py [root]

FishPose.FLAT is the one list: flounder, halibut, ray. On 1.21.1 and 1.20.1 the renderer reads it
and lays the fish down itself, so no item model may carry a ground rotation at all — one there would
lay the fish down twice. On 26.x there is no renderer; the pose is a display transform the generator
writes into the base model and its twelve scale buckets, and the set of models carrying it has to be
FishPose.FLAT exactly.

Reported: the goliath grouper lying on the bank like a halibut, on 26.1.2. Its base model had been
templated off a flatfish's, and the generator only ever ADDED the rotation for flat species — it
never took one off — so three giants lay down through every regeneration. Read by set equality, not
by presence, so a fish that lies down by mistake fails as loudly as one that fails to.
"""
import io, json, os, re, sys, glob

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing")
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/FishPose.java")
fails = []

src = io.open(J, encoding="utf-8").read()
m = re.search(r"FLAT\s*=\s*Set\.of\(([^)]*)\)", src)
if not m:
    print("FAILED:\n  FishPose.FLAT not found"); sys.exit(1)
FLAT = set(re.findall(r'"([^"]+)"', m.group(1)))
lay = float(re.search(r"return\s+(-?[\d.]+)f;", src[src.index("public static float lay()"):]).group(1))

is26 = os.path.isdir(os.path.join(A, "items"))
fish_ids = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(A, "models/item/*.json"))
            if os.path.exists(os.path.join(A, "textures/item/fish/%s.png" % os.path.basename(p)[:-5]))}


def rot(path):
    d = json.load(io.open(path, encoding="utf-8"))
    return d.get("display", {}).get("ground", {}).get("rotation", [0, 0, 0])


lying = {sp for sp in fish_ids if rot(os.path.join(A, "models/item/%s.json" % sp)) != [0, 0, 0]}
want = FLAT if is26 else set()
if lying != want:
    for sp in sorted(lying - want):
        fails.append("%s lies on the ground (rotation %s) and is not in FishPose.FLAT%s"
                     % (sp, rot(os.path.join(A, "models/item/%s.json" % sp)),
                        "" if is26 else " — on this tree the renderer lays fish down, a model rotation lays it twice"))
    for sp in sorted(want - lying):
        fails.append("%s is in FishPose.FLAT but its base model stands upright" % sp)

if is26:
    for sp in sorted(fish_ids):
        for i in range(12):
            p = os.path.join(A, "models/item/fish_scaled/%s_%d.json" % (sp, i))
            if not os.path.exists(p):
                break
            r = rot(p)
            if (r != [0, 0, 0]) != (sp in FLAT):
                fails.append("fish_scaled/%s_%d disagrees with FishPose (%s)" % (sp, i, r)); break
            if sp in FLAT and r[0] != lay:
                fails.append("fish_scaled/%s_%d lies at %s, FishPose.lay() is %s" % (sp, i, r[0], lay)); break

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("flat pose: %s lie down (%s), %d other fish stand — as FishPose.FLAT says"
      % (", ".join(sorted(want)) or "nobody (renderer's job here)", "26.x models" if is26 else "no model rotation", len(fish_ids) - len(want)))
