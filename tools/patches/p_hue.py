# -*- coding: utf-8 -*-
"""§pattern-hue: a pattern family turns a koi a few degrees, not seventy.

    py -X utf8 tools/patches/p_hue.py <root> [1211|1201|26]

Reported from the test chest, and true: kin hi utsuri and kujaku came out GREEN, hi utsuri red-on-black
came out magenta-on-black, kin showa came out swamp green with gold. The maths was doing exactly what
it was told — the twelve families were spread over ±70° of hue, and 60° of hue turn is a different
animal. On a fish whose whole identity is its colour (and koi are the only fish the pattern paints at
all) that is not a variation, it is a mistake.

Hue alone had to carry all twelve families, which is why it had to reach so far. It does not any more:
a family is a SMALL turn of hue and a lift or a shade of value, so the twelve read as pale, deep, warm
and cool versions of the same fish — a specimen, not another species. Gems are untouched: a gem is
meant to be a different colour, that is the whole prize.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/Pattern.java")

s = io.open(P, encoding="utf-8").read()
if "pattern-hue" in s:
    print("  already patched")
    sys.exit(0)

old = """    private static final int[] HUE = {0, 8, -10, 18, -20, 28, -30, 40, -42, 55, -58, 70};"""
assert old in s, "the HUE table moved"
s = s.replace(old, """    private static final int[] HUE = {0, 3, -4, 6, -7, 9, -11, 12, -14, 15, -17, 18};

    /**
     * §pattern-hue: and how much lighter or darker, in value. The families used to be hue alone, which
     * meant hue had to reach ±70° to tell twelve of them apart — and 60° turns a red koi green, which
     * is what the first test chest showed. Half the work moves here: a family is now a few degrees of
     * hue AND a lift or a shade, so the twelve read as pale, deep, warm and cool specimens of the same
     * fish. Same indexing as {@link #HUE} and {@link #FAMILY}.
     */
    private static final double[] LIFT = {0.0, 0.06, -0.06, 0.10, -0.10, 0.04,
                                          -0.04, 0.12, -0.12, 0.08, -0.08, 0.14};""", 1)

old = """        return HUE[i] + (t - 0.5) * 20.0;"""
assert old in s, "hueShift moved"
s = s.replace(old, """        // §pattern-hue: ±4° inside a band, not ±10. Neighbours are cousins; the ends of a band are a
        // shade apart, not a colour apart.
        return HUE[i] + (t - 0.5) * 8.0;""", 1)

old = """    public static int swatch(int familyIndex) {
        int i = Math.max(0, Math.min(HUE.length - 1, familyIndex));
        return shift(0xC8A25A, HUE[i], 0.0);
    }"""
assert old in s, "swatch moved"
s = s.replace(old, """    public static int swatch(int familyIndex) {
        int i = Math.max(0, Math.min(HUE.length - 1, familyIndex));
        // §pattern-hue: the board shows what the family actually does — its own turn AND its own lift.
        // Exaggerating either here would make the journal promise a fish the water does not paint.
        return shift(0xC8A25A, HUE[i], LIFT[i]);
    }""", 1)

old = """        if (!has(pattern)) return rgb;
        int gem = gemColor(pattern);
        if (gem >= 0) return gem;
        return shift(rgb, hueShift(pattern), patch ? offset(pattern) * 0.03 : 0.0);"""
assert old in s, "paint moved"
s = s.replace(old, """        if (!has(pattern)) return rgb;
        int gem = gemColor(pattern);
        if (gem >= 0) return gem;
        // §pattern-hue: the family's own lift, and on a PATCH the depth offset on top of it.
        double lift = LIFT[familyIndex(pattern)] + (patch ? offset(pattern) * 0.03 : 0.0);
        return shift(rgb, hueShift(pattern), lift);""", 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  Pattern: hue +/-18 max, a lift table, +/-4 inside a band")
print("done (%s)" % D)
