# -*- coding: utf-8 -*-
"""§poach-credit: a poached fish put back buys no pardon.

    py -X utf8 tools/patches/p_poachcredit.py <root> [1211|1201|26]

The author: "рыба, что словлена сетью и помечена как браконьерская, при отпускании в воду не должна
восполнять репутацию". Warden.credit() paid five kilograms a point for any mature fish released into
wild water — including the one you netted out of somebody's stocked pond a minute ago. Net it, throw it
back, and the same haul that cost five points paid them back. A card that says POACHED is a fish that
was never yours to give; it goes on the ledger like any other (the water is no worse for it), but the
reputation stays where the net left it.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java")
POACHED = 'card != null && card.getBooleanOr("Poached", false)' if D == "26" else 'card != null && card.getBoolean("Poached")'

s = io.open(P, encoding="utf-8").read()
if "§poach-credit" in s:
    print("  already patched"); sys.exit(0)
old = """        if (thrower != null && mature && !PondData.isClaimed(level, pos)) {
            Warden.credit(thrower, weightG * Math.max(1, count));
        }"""
assert s.count(old) == 1, "the credit block moved"
s = s.replace(old, """        // §poach-credit: a fish the card says was POACHED was never yours to give — net it out of a
        // stocked pond and throw it back, and the haul that cost five points must not pay them back.
        boolean poached = %s;
        if (thrower != null && mature && !poached && !PondData.isClaimed(level, pos)) {
            Warden.credit(thrower, weightG * Math.max(1, count));
        }""" % POACHED, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  FishingManager: a poached fish released credits nothing (%s)" % D)
