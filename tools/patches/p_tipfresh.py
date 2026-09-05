# -*- coding: utf-8 -*-
"""§tip-fresh: a per-frame capture cannot have its freshness measured in ticks.

    py -X utf8 tools/patches/p_tipfresh.py <root> [1211|1201|26]

Reported: with the line out and the rod in hand, the START of the line jumps for a split second — and
the guess in the report was exactly right, it jumps to where the 2D sprite's tip would be.

The rod renderer captures where it actually drew the tip and stamps the capture; LineRenderer uses that
capture if it is fresh and falls back to a CONSTANT — the sprite blank's tip, a number found by eye —
if it is not. The stamp was {@code level.getGameTime()} and the test was "within one tick of now".

But the tip is captured once a FRAME, and the world pass that reads it runs before the hand pass that
writes it, so the reader is always looking at the previous frame's stamp. That holds while frames come
at least every two ticks — a hundred milliseconds — and fails on every hitch and at any framerate under
twenty. One frame of fallback is one frame of the line starting somewhere else, which is precisely the
flicker.

Frames are not ticks. All three captures (the first-person tip, the third-person tip, the hand-drawn
string) now carry a wall-clock nanosecond stamp and are good for three tenths of a second — every
framerate down to three, while a rod that has genuinely stopped being drawn still goes stale.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
# 26.x split the rod renderer: the same three captures live in RodChain there.
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/"
                 + ("RodChain.java" if D == "26" else "RodItemRenderer.java"))

s = io.open(P, encoding="utf-8").read()
if "tip-fresh" in s:
    print("  already patched")
    sys.exit(0)

HELPER = '''    /**
     * §tip-fresh: how long a captured anchor stays usable, on the WALL CLOCK.
     *
     * <p>These three were measured in GAME TICKS — stamped with {@code getGameTime()} as the rod drew,
     * accepted within one tick of now. But the capture happens once a FRAME, and the world pass that
     * reads it runs before the hand pass that writes it, so the reader always sees the previous frame's
     * stamp. That is fine while frames arrive at least every two ticks and wrong the moment they do
     * not: one hitch, or anything under twenty frames a second, and the anchor reads stale for a frame.
     * A stale anchor is not a small error — {@link LineRenderer} falls back to
     * the sprite blank's constant tip, so the line visibly starts somewhere else and snaps back.
     * Reported as "the start of the line shifts for a split second", and the report guessed the cause.
     *
     * <p>Frames are not ticks, so a per-frame capture cannot be aged in ticks. Three tenths of a second
     * covers every framerate down to three, and a rod that has genuinely stopped being drawn still goes
     * stale — which is all the test was ever for.
     */
    private static final long FRESH_NS = 300_000_000L;

    /** True while {@code nanos} is a stamp from the last {@link #FRESH_NS}. 0 means "never captured". */
    private static boolean fresh(long nanos) {
        return nanos != 0L && System.nanoTime() - nanos < FRESH_NS;
    }

'''

# The three fields, their stamps and their tests — one idiom, three copies.
for field, test in (("tipNdcFrame", "tipNdcFresh"), ("tipViewFrame", "tipViewFresh"),
                    ("handLineFrame", "handLineFresh")):
    nanos = field.replace("Frame", "Nanos")
    old = """    public static boolean %s() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && %s >= mc.level.getGameTime() - 1;
    }""" % (test, field)
    assert old in s, "%s moved" % test
    s = s.replace(old, """    public static boolean %s() {
        return fresh(%s);   // §tip-fresh
    }""" % (test, nanos), 1)
    assert "%s = mc.level.getGameTime();" % field in s, "%s's stamp moved" % field
    s = s.replace("%s = mc.level.getGameTime();" % field, "%s = System.nanoTime();   // §tip-fresh" % nanos)
    s = s.replace("public static long %s = Long.MIN_VALUE;" % field,
                  "public static long %s;" % nanos)
    s = s.replace(field, nanos)          # …and any prose that names it, which should follow it
    assert field not in s, "%s is still referenced somewhere" % field

s = s.replace(
    "    /** Frame counter the NDC above was written on; stale means the rod was not drawn. */",
    "    /** §tip-fresh: when the NDC above was written, System.nanoTime(); 0 until the rod has drawn. */")

# The helper goes in just above the first test that uses it.
at = s.index("    public static boolean tipNdcFresh()")
# …but after the javadoc block that introduces the field it sits under.
head = s.rindex("\n\n", 0, at) + 2
s = s[:head] + HELPER + s[head:]

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  RodItemRenderer: three captures aged on the wall clock, not the game clock")
print("done (%s)" % D)
