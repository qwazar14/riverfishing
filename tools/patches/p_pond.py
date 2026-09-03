# -*- coding: utf-8 -*-
"""§breeding stream POND: private ponds — the claimed water steps out of the wild simulation.

    py -X utf8 tools/patches/p_pond.py <repo root> [1211|1201|26]

Anchor replacement on four existing files; every insert carries a "§pond" marker so a rerun finds it
and does nothing. Exit 1 with the missing anchor when a tree has drifted. The new classes
(fishing/PondData, block/PondSignBlock), the ModBlocks line, the assets and the lang go into the tree
directly — this script is only the seams into code another stream is patching at the same time.

    engine/BiteContext.java     a `privatePond` flag beside waterDepth
    engine/BiteEngine.java      the depth/width hard gates skipped when the flag is set
    fishing/FishingManager.java communityFactor (no wild set in a claimed pond), nativeHere (never),
                                environmentAt + buildContext (set the flag), release (the "your pond:"
                                prefix), analyzeWater + finderPayload (the owner line), gateReason
                                (mirrors the engine)
    item/NetItem.java           the owner is legal for everything in his pond; anyone else poaches;
                                unsettled transplants are in the net's pool there

fishing/StockedData.java needs nothing: the region ledger already is "what the owner put in".

Dialect: 26.x names ResourceLocation `Identifier`, sends `sendOverlayMessage(c)` /
`sendSystemMessage(c)` where 1.21.1/1.20.1 use `displayClientMessage(c, true/false)`. Nothing else
inserted here reads NBT.
"""
import io, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§pond"

def overlay(target, comp):
    return ("%s.sendOverlayMessage(%s)" if DIALECT == "26" else "%s.displayClientMessage(%s, true)") % (target, comp)


def system(target, comp):
    return ("%s.sendSystemMessage(%s)" if DIALECT == "26" else "%s.displayClientMessage(%s, false)") % (target, comp)


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def sub1(rel, old, new):
    """Exactly one anchor, replaced once. A tree already carrying the insert is left alone."""
    assert MARK in new, "every insert must carry the marker: " + rel
    path = os.path.join(SRC, rel)
    text = read(path)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_pond: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ---------------------------------------------------------------- BiteContext: the flag
CTX_ANCHOR = "    public int waterDepth = 3;      // water-column depth (blocks) at the cast point — habitat gate\n"
sub1("engine/BiteContext.java", CTX_ANCHOR, CTX_ANCHOR + '''    /** §pond: a claimed private pond — the depth and width gates are waived; a dug pit is the size its owner made it. */
    public boolean privatePond;
''')

# ---------------------------------------------------------------- BiteEngine: the gates
GATES = '''        if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return 0.0;
        if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return 0.0;
'''
sub1("engine/BiteEngine.java", GATES, '''        // §pond: not in a private pond — a 2x5x2 pit holds whatever its owner put in it, and the water type,
        // climate and biome gates below still say whether the fish can live there at all.
        if (!c.privatePond) {
            if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return 0.0;
            if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return 0.0;
        }
''')

# ---------------------------------------------------------------- FishingManager
FM = "fishing/FishingManager.java"

# communityFactor: a claimed pond has no wild set.
sub1(FM, '''        int cx = waterPos.getX() >> 4, cz = waterPos.getZ() >> 4;
        return id -> {
''', '''        int cx = waterPos.getX() >> 4, cz = waterPos.getZ() >> 4;
        boolean claimed = PondData.isClaimed(level, waterPos);   // §pond
        return id -> {
''')
sub1(FM, '''            if (stocked.isCulled(region, id.getPath())) return 0.0;
            FishProfile pr = FishProfileManager.get().byId(id);
            if (pr == null || pr.base >= 0.95) return 1.0;
''', '''            if (stocked.isCulled(region, id.getPath())) return 0.0;
            // §pond: a claimed pond has NO wild community — not even the commons. What lives there is what
            // its owner put in: the settled species, and the temporary stock of releases still dispersing.
            if (claimed) {
                return stocked.isStocked(region, id.getPath()) ? 1.0
                        : Math.min(1.0, pd.surplusAround(cx, cz, id.getPath(), level.getGameTime()));
            }
            FishProfile pr = FishProfileManager.get().byId(id);
            if (pr == null || pr.base >= 0.95) return 1.0;
''')

# nativeHere: nothing is native to a dug pit — so a released pike is a transplant, not "local stock".
sub1(FM, '''        if (pr == null) return false;
        if (pr.base >= 0.95) return true;
''', '''        if (pr == null) return false;
        if (PondData.isClaimed(level, pos)) return false;   // §pond: nobody is native to a claimed pond
        if (pr.base >= 0.95) return true;
''')

# environmentAt / buildContext: the flag the engine reads.
sub1(FM, "        env.waterDepth = measureDepth(level, pos);\n",
     "        env.waterDepth = measureDepth(level, pos);\n"
     "        env.privatePond = PondData.isClaimed(level, pos);   // §pond: size gates waived\n")
sub1(FM, "        ctx.waterDepth = measureDepth(level, waterPos);\n",
     "        ctx.waterDepth = measureDepth(level, waterPos);\n"
     "        ctx.privatePond = PondData.isClaimed(level, waterPos);   // §pond: size gates waived\n")

# release(): the farmer hears "your pond:", not "the water".
RELEASE_MSG = "        " + overlay("thrower", "msg") + ";\n"
sub1(FM, RELEASE_MSG, '''        // §pond: said as the farmer hears it — this is his pond, not "the water".
        if (thrower.getUUID().equals(PondData.owner(level, pos))) {
            msg = Component.translatable("message.riverfishing.your_pond", msg).withStyle(msg.getStyle());
        }
''' + RELEASE_MSG)

# analyzeWater: the owner line first — an empty wild list is the claim working, not a broken finder.
FINDER_ANCHOR = "        // Player-facing fish finder: just the species list, no numbers.\n"
sub1(FM, FINDER_ANCHOR, '''        // §pond: whose water this is, before the list — an empty wild list is the claim working.
        String pondOwner = PondData.ownerName(level, waterPos);
        if (!pondOwner.isEmpty()) {
            ''' + system("sp", 'Component.translatable("finder.riverfishing.owner", pondOwner)\n                    .withStyle(ChatFormatting.GOLD)') + ''';
        }
''' + FINDER_ANCHOR)

# finderPayload: the owner rides along for the screen.
BED_ANCHOR = '        w.putByte("bed", bedType(level, waterPos));\n'
sub1(FM, BED_ANCHOR, BED_ANCHOR + '''        // §pond: whose water this is, if anyone's — the screen has no SavedData to ask.
        String pondOwner = PondData.ownerName(level, waterPos);
        if (!pondOwner.isEmpty()) w.putString("owner", pondOwner);
''')

# gateReason: mirrors the engine, so the blocked list never blames depth in a pond.
sub1(FM, '''        if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return "depth(" + c.waterDepth + ")";
        if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return "width";
''', '''        if (!c.privatePond) {   // §pond: a dug pit is as deep and as wide as its owner wants it
            if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return "depth(" + c.waterDepth + ")";
            if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return "width";
        }
''')

# ---------------------------------------------------------------- NetItem: the owner's net is legal
NET = "item/NetItem.java"
RNG_LINE = "        RandomSource rng = level.%s;\n" % ("getRandom()" if DIALECT == "26" else "random")
sub1(NET, RNG_LINE, RNG_LINE + '''        // §pond: in a claimed pond the OWNER is legal for every species — his fish, his net. Anyone else
        // is poaching the lot, native book or no. Outside claimed water the stocking book rules as before.
        UUID pondOwner = com.riverfishing.fishing.PondData.owner(level, pos);
''')
sub1(NET, "            if (!FishingManager.residentHere(level, pos, body, p.id)) continue;\n",
     '''            // §pond: nothing is resident in a claimed pond but what was put in — so the transplants still
            // dispersing there count too, or the net would come up empty the day after stocking.
            if (!FishingManager.residentHere(level, pos, body, p.id)
                    && !(pondOwner != null && pressure.surplusAround(pos.getX() >> 4, pos.getZ() >> 4, id, now) > 0)) continue;
''')
sub1(NET, "            boolean poachedFish = owner == null || !owner.equals(sp.getUUID());\n",
     '''            boolean poachedFish = pondOwner != null ? !pondOwner.equals(sp.getUUID())   // §pond
                    : owner == null || !owner.equals(sp.getUUID());
''')
# the poaching line is wrapped over two lines in every tree; anchor on its first line only
POACH_LINE = "            sp.%s(Component.translatable(\"message.riverfishing.poaching\")\n" % (
    "sendSystemMessage" if DIALECT == "26" else "displayClientMessage")
sub1(NET, POACH_LINE, '''            if (pondOwner != null) {   // §pond: name whose pond it was
                ''' + system("sp", 'Component.translatable("message.riverfishing.pond_not_yours",\n                        com.riverfishing.fishing.PondData.ownerName(level, pos)).withStyle(ChatFormatting.RED)') + ''';
            }
''' + POACH_LINE)

print("p_pond: ok (%s)" % DIALECT)
