# -*- coding: utf-8 -*-
"""§home-water: a fish put back where it came out of is never "unfit for this water".

    py -X utf8 tools/patches/p_homewater.py <root>

Reported: land a fish thirty blocks out, walk to the bank, drop it in — "the water is unfit, it will
not survive here". The release judged the column at the angler's feet (§fit-body widened that to the
best water within three blocks, which is still the bank), while the fish had just been living in the
deep water it was caught from. The card now remembers WHERE the fish came out ("At"), and a release
within reach of that spot is judged by the water there as well as here — whichever fits better. The
fish lived there; the bank does not get a vote. Old cards without "At" behave as before.
"""
import io, os, sys

ROOT = sys.argv[1]
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
C = os.path.join(J, "fish/CatchCard.java")
F = os.path.join(J, "fishing/FishingManager.java")
s = io.open(C, encoding="utf-8").read()
if "§home-water" in s:
    print("  already patched"); sys.exit(0)
old = 'c.putString("Water", ctx == null ? "" : ctx.water.key());\n'
assert s.count(old) == 1
s = s.replace(old, old + '        c.putLong("At", s.target.asLong());   // §home-water: where it came out, for the release\n', 1)
old = 'c.putString("Water", com.riverfishing.water.WaterBodyCache.forLevel(level).get(level, pos).type().key());\n'
assert s.count(old) == 2
s = s.replace(old, old + '        c.putLong("At", pos.asLong());   // §home-water\n')
io.open(C, "w", encoding="utf-8", newline="\n").write(s)

f = io.open(F, encoding="utf-8").read()
d26 = "getBooleanOr(" in f
old = "                                java.util.function.ObjLongConsumer<StockedData> ledger) {\n"
assert f.count(old) == 1
f = f.replace(old, "                                java.util.function.ObjLongConsumer<StockedData> ledger,\n"
                   "                                @org.jetbrains.annotations.Nullable BlockPos caughtAt) {\n", 1)
old = "        double fit = BiteEngine.environmentScore(p, habitatContext(level, pos, body));\n"
assert f.count(old) == 1
f = f.replace(old, old + """        // §home-water: a fish released within reach of the spot it came out of is judged by THAT
        // water too — it lived there. The bank at the angler's feet is not where the fish will live.
        if (fit <= 0 && caughtAt != null && caughtAt.closerThan(pos, 96.0)
                && level.getFluidState(caughtAt).is(net.minecraft.tags.FluidTags.WATER)) {
            WaterBody home = WaterBodyCache.forLevel(level).get(level, caughtAt);
            if (home.type() != WaterType.NONE) fit = Math.max(fit, BiteEngine.environmentScore(p, habitatContext(level, caughtAt, home)));
        }
""", 1)
old = "release(level, pos, p, 0.0, thrower, (stocked, region) -> {"
assert f.count(old) == 1
i = f.index(old); j = f.index("});", i) + 3
f = f[:j].rstrip(";") + ", null);" + f[j:] if False else f[:j-2] + "}, null);" + f[j:]
old = "release(level, pos, p, units, thrower, (stocked, region) -> {"
assert f.count(old) == 1
i = f.index(old); j = f.index("});", i) + 3
f = f[:j-2] + "}, card != null && card.contains(\"At\") ? BlockPos.of(%s) : null);" % ('card.getLongOr("At", 0L)' if d26 else 'card.getLong("At")') + f[j:]
io.open(F, "w", encoding="utf-8", newline="\n").write(f)
print("  CatchCard remembers where; release() judges the home water too (%s)" % ("26" if d26 else "1.x"))
