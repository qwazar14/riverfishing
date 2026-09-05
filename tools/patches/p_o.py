# -*- coding: utf-8 -*-
"""§breeding stream O (layer 6): the debt to the fishermen — negative reputation, kilograms as restitution.

    py -X utf8 tools/patches/p_o.py <repo root> [1211|1201|26]

Anchor replacement, every insert marked "§o" so a rerun finds it and does nothing; exit 1 with the
missing anchor when a tree has drifted. Written in the 1.21.1 dialect; to26 rewrites the NBT getters
for the 26.x tree (the ANCHORS too — earlier streams' inserts already sit there in that dialect).
1.20.1 reads the 1.21.1 text unchanged for everything touched here.

Files: FishingManager.java and NetItem.java are shared with streams L/M/N, which is why they are here.
ModVillagers/ContractBoardState/FinderScreen are stream O's alone and are edited directly in the main
tree — they are repeated here so the 1.20.1 and 26.x trees get the same three edits from one run (on
the main tree those three sub1 calls find their own text and do nothing).

Depends on p_i having run (it inserted the Warden.workOff line this script removes, and the `banned`
flag on the board). p_i must NOT be re-applied afterwards: it would put workOff back — a method that
no longer exists — and its two ContractBoardState anchors are rewritten here.

Direct edits NOT in this script (the integrator ports them): fishing/Warden.java (the whole file —
credit/onPoach/banned) and one comment in fishing/Contracts.java. Lang: tools/patches/lang_o.json —
note it OVERRIDES screen.riverfishing.contract_board.banned, whose old text named the poach count.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§o"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def to26(java):
    """The 26.x dialect of a 1.21.1 snippet: only the idioms this stream's text actually uses."""
    if DIALECT != "26":
        return java
    java = re.sub(r"\.getInt\(([^()]+)\)", r".getIntOr(\1, 0)", java)
    java = re.sub(r"\.getBoolean\(([^()]+)\)", r".getBooleanOr(\1, false)", java)
    java = re.sub(r"\.getByte\(([^()]+)\)", r".getByteOr(\1, (byte) 0)", java)
    # §port26: GuiGraphics.drawString is GuiGraphicsExtractor.text in this tree.
    java = java.replace("g.drawString(", "g.text(")
    return java


def sub1(rel, old, new):
    """Exactly one anchor, replaced once. A tree already carrying the insert (the literal replacement,
    which always contains a §o marker) is left alone — that is what makes a rerun a no-op."""
    path = os.path.join(SRC, rel)
    text = read(path)
    old, new = to26(old), to26(new)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_o: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ---------------------------------------------------------------- FishingManager: the release pays the debt
FM = "fishing/FishingManager.java"

# The poach record is no longer what closes the board (reputation is), so a released fish no longer
# rubs a count out — it buys reputation back by weight, below.
sub1(FM,
     "                stocked.addBrood(region, species.getPath(), sex, day, genes, thrower == null ? null : thrower.getUUID());\n"
     "                if (thrower != null) com.riverfishing.fishing.Warden.workOff(thrower);   // §i: a fish in pays a poach off\n",
     "                stocked.addBrood(region, species.getPath(), sex, day, genes, thrower == null ? null : thrower.getUUID());   // §o: the work-off is Warden.credit now, by weight\n")

# The credit itself. Placed before release(), which is where the fish's fate is decided anyway, and
# outside the ledger callback on purpose: that callback runs only for a species neither native nor
# settled here, and putting a native bream back into the river IS the restitution the fishermen want.
sub1(FM,
     "        release(level, pos, p, units, thrower, (stocked, region) -> {\n",
     "        // §o: the debt to the fishermen is paid in fish PUT BACK, and only into water anybody may\n"
     "        // fish. A fish released into your own claimed pond is not restitution: it is still yours —\n"
     "        // you stocked it, it grows for you, you catch it again — while the water a net emptied is\n"
     "        // no fuller than it was. Kilograms rather than fish, because five kilos is five kilos\n"
     "        // whether it comes as one carp or ten roach; mature only, the same size class the ledger\n"
     "        // takes as brood, so a bucket of undersized fish buys no pardon.\n"
     "        if (thrower != null && mature && !PondData.isClaimed(level, pos)) {\n"
     "            Warden.credit(thrower, weightG * Math.max(1, count));\n"
     "        }\n"
     "        release(level, pos, p, units, thrower, (stocked, region) -> {\n")

# The finder's sample view has no PlayerData to ask: the standing rides along with the water.
sub1(FM,
     '            w.putString("groups", String.join(";", new java.util.TreeSet<>(env.biomeGroups)));\n',
     '            w.putString("groups", String.join(";", new java.util.TreeSet<>(env.biomeGroups)));\n'
     "            // §o: where the angler stands with the fishermen, and how much of a kilogram is banked.\n"
     '            w.putInt("rep", Contracts.rep(sp));\n'
     '            w.putInt("rep_grams", Warden.repGrams(sp));\n')

# ---------------------------------------------------------------- NetItem: the penalty moves, and loses its floor
# The CompoundTag / PlayerData imports are left in place: they cost a javac warning at most, and
# removing an import line cannot be made idempotent by "is the replacement already there".
sub1("item/NetItem.java",
     "            CompoundTag root = PlayerData.root(sp);\n"
     '            root.putInt("contract_rep", Math.max(0, root.getInt("contract_rep") - 5));\n'
     "            PlayerData.markDirty(sp);\n",
     "            // §o: the reputation hit lives in Warden.onPoach now, beside the record it belongs to,\n"
     "            // and it lost its clamp at zero. Reputation goes NEGATIVE: the board stops showing a\n"
     "            // number and starts showing a debt, in kilograms of fish owed back to wild water.\n")

# ---------------------------------------------------------------- ModVillagers: the board packet
sub1("registry/ModVillagers.java",
     '        t.putBoolean("banned", banned);\n',
     '        t.putBoolean("banned", banned);\n'
     '        t.putInt("rep_grams", com.riverfishing.fishing.Warden.repGrams(player));   // §o: what the debt costs, in kilograms\n')

# ---------------------------------------------------------------- ContractBoardState: the caption is the standing
CBS = "client/ContractBoardState.java"

sub1(CBS,
     "        int total = HEAD + 4;\n",
     "        // §o: in the red the caption IS the standing — the points owed, the kilograms to the next\n"
     "        // one and the kilograms that clear it. It wraps, so the head grows with it.\n"
     '        int rep = board.getInt("rep"), repGrams = board.getInt("rep_grams");\n'
     "        List<FormattedCharSequence> caption = font.split(rep < 0\n"
     '                ? Component.translatable("screen.riverfishing.contract_board.debt", -rep,\n'
     "                        com.riverfishing.fishing.Warden.kg(com.riverfishing.fishing.Warden.toNextPoint(repGrams)),\n"
     "                        com.riverfishing.fishing.Warden.kg(com.riverfishing.fishing.Warden.toClear(rep, repGrams)))\n"
     '                : Component.translatable("screen.riverfishing.contract_board.rep", rep), W - 10);\n'
     "        int head = HEAD + LINE * (caption.size() - 1);\n"
     "        int total = head + 4;\n")

sub1(CBS,
     '        g.drawString(font, Component.translatable("screen.riverfishing.contract_board.rep", board.getInt("rep")),\n'
     "                x + 5, y + 15, INK2, false);\n"
     "        for (int i = 0; i < banned.size(); i++) {   // §i\n"
     "            g.drawString(font, banned.get(i), x + 5, y + HEAD + 4 + LINE * i, INK, false);\n"
     "        }\n",
     "        for (int i = 0; i < caption.size(); i++) {   // §o: one line, three when it is a debt\n"
     "            g.drawString(font, caption.get(i), x + 5, y + 15 + LINE * i, rep < 0 ? INK : INK2, false);\n"
     "        }\n"
     "        for (int i = 0; i < banned.size(); i++) {   // §i\n"
     "            g.drawString(font, banned.get(i), x + 5, y + head + 4 + LINE * i, INK, false);\n"
     "        }\n")

# ---------------------------------------------------------------- FinderScreen: one line on the sample
sub1("client/FinderScreen.java",
     '        if (data.contains("owner")) {\n',
     "        // §o: where the angler stands with the fishermen, and in the red what it takes to clear it.\n"
     '        if (w.contains("rep")) out.add(pairLine("finder.riverfishing.rep", w.getInt("rep") < 0\n'
     '                ? Component.translatable("finder.riverfishing.rep_debt", w.getInt("rep"),\n'
     '                        com.riverfishing.fishing.Warden.kg(com.riverfishing.fishing.Warden.toClear(w.getInt("rep"), w.getInt("rep_grams"))))\n'
     '                : Component.literal(String.valueOf(w.getInt("rep")))));\n'
     '        if (data.contains("owner")) {\n')

print("p_o: ok (%s)" % DIALECT)
