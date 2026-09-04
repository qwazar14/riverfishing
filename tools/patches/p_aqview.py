# -*- coding: utf-8 -*-
"""§aq-lanes §aq-size: six fish in the tank swim as six fish, and they are big enough to look at.

    py -X utf8 tools/patches/p_aqview.py <root> [1211|1201|26]

Two reports from the tank after it went from three fish to six.

1. They looked GLUED IN PAIRS. The phase step was a fixed 120° and the lane was `i % 3`, so with six
   fish, i and i+3 got the same phase AND the same lane — two fish on the same path, 0.14 apart in
   height. Both now come off the COUNT: n phases, n lanes, nothing coincides at any size of shoal.

2. They got SMALLER. §fish-item put the tank on the same true-length rule as open water — one block
   per metre — which is honest and unreadable: a 20 cm perch is a fifth of a two-wide tank, and the
   old drawing had an effective floor at 0.315 that the new one bypassed. The tank is a display, so it
   zooms: the proportions between fish are kept, the whole shoal is drawn larger.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/AquariumRenderer.java")

s = io.open(P, encoding="utf-8").read()
if "aq-lanes" in s:
    print("  already patched")
    sys.exit(0)

# The size step is 1.21.1 only: that is the one tree where §fish-item draws the tank's fish as the
# extruded item at true length. 1.20.1 and 26.x still use the older icon scale, which never shrank.
old = """            float fishLen = Math.min(1.8f, ShoalRenderer.itemSize(FishItem.getLengthCm(fish)));"""
if old not in s:
    print("  fish size: this tree does not draw the item (§fish-item is 1.21.1 only) - left alone")
else:
    s = s.replace(old, """            // §aq-size: the tank is a DISPLAY, so it zooms. True length reads tiny behind glass — a
            // 20 cm perch at one block per metre is a fifth of the tank, and smaller than the same fish
            // looked before §fish-item, which had a 0.45 floor under it. The proportions between fish
            // are the water's own; only the whole shoal is drawn larger, floored so a fry is visible
            // and capped so a big fish stays inside two blocks of glass.
            float fishLen = Mth.clamp(ShoalRenderer.itemSize(FishItem.getLengthCm(fish)) * 1.6f,
                    0.32f, 1.7f);""", 1)
    print("  fish size: 1.6x, floored at 0.32 and capped at 1.7")

old = """            // Spread the fish out in phase and depth so they don't overlap.
            float t = time * 0.05f + i * 2.094f;                 // 120° apart
            double depth = ((i % 3) - 1) * 0.20;                  // §aq-fix: three lanes, six fish — the second trio shares them                        // front/mid/back lane"""
assert old in s, "the tank's phase/lane spread moved"
s = s.replace(old, """            // §aq-lanes: phase and lane come off the COUNT. They used to be a fixed 120° step and
            // `i % 3`, which is three of each — so with six fish, i and i+3 shared a phase AND a lane
            // and swam as one fish with a shadow 0.14 below it. n phases, n lanes, no two alike.
            int n = Math.max(1, fishes.size());
            float t = time * 0.05f + i * (float) (Math.PI * 2.0 / n);
            double lane = n == 1 ? 0.5 : i / (double) (n - 1);     // 0..1 from the front glass to the back
            double depth = (lane - 0.5) * 0.40;""", 1)

old = """                height = 1.5 + Mth.sin(time * 0.09f + i) * 0.04 - (i / 3) * 0.14;"""
assert old in s, "the big fish height moved"
s = s.replace(old, """                height = 1.5 + Mth.sin(time * 0.09f + i) * 0.04 + (lane - 0.5) * 0.16;""", 1)

old = """                height = 1.5 + 0.5 * Mth.sin(2 * t) * 0.28 + ((i % 3) - 1) * 0.04 - (i / 3) * 0.14;"""
assert old in s, "the figure-8 height moved"
s = s.replace(old, """                height = 1.5 + 0.5 * Mth.sin(2 * t) * 0.28 + (lane - 0.5) * 0.16;""", 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  AquariumRenderer: %d lanes and phases off the count, fish drawn 1.6x" % 6)
print("done (%s)" % D)
