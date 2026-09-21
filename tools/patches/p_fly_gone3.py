# -*- coding: utf-8 -*-
"""The last of the fly on 26.x, where the same lines are spelled differently (getIntOr, RodChain instead
of RodItemRenderer, a use handler that returns a result). A no-op on the trees that had none of it.

Run after p_fly_gone / p_fly_gone2.  py -X utf8 tools/patches/p_fly_gone3.py [tree ...]"""
import io, os, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def swap(tree, rel, old, new):
    p = os.path.join(tree, J, rel)
    if not os.path.exists(p):
        return
    s = rd(p)
    if old not in s:
        return
    wr(p, s.replace(old, new))


EDITS = [
    ("fishing/JournalData.java",
     "\n    public static final String FLY = \"fly\";   // §progression: fish landed on the fly rod\n"
     "\n    /** §progression: a fish landed on the fly rod — the counter the stage-7 quests read. */\n"
     "    public static void addFlyCatch(Player player) {\n"
     "        CompoundTag root = get(player);\n"
     "        root.putInt(FLY, root.getIntOr(FLY, 0) + 1);\n"
     "        PlayerData.root(player).put(TAG, root);\n"
     "        PlayerData.markDirty(player);\n"
     "    }\n", ""),
    ("command/JournalCommand.java",
     "        root.putInt(JournalData.FLY, Math.max(root.getIntOr(JournalData.FLY, 0), 60));\n", ""),
    ("fishing/FishingManager.java",
     "        if (jr.getIntOr(JournalData.FLY, 0) >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, \"fly_fifty\");\n", ""),
    ("client/RodChain.java",
     "            // §fly-3d: three sections on a cork handle, the lightest chain in the fleet\n"
     "            java.util.Map.entry(\"fly\", new float[]{19.0f, 11.0f, 2.975f}),\n", ""),
    ("client/RodChain.java",
     "        String rodKey = rod.rodType().modelKey();   // §fly: the fly rod borrows the ultralight blank",
     "        String rodKey = rod.rodType().modelKey();"),
    ("client/RodModelLayers.java",
     "                ? r.rodType().modelKey() : null;   // §fly", "                ? r.rodType().modelKey() : null;"),
    ("client/HookedFishRenderer.java",
     "return null;   // §fly: drawn on the rise too", "return null;   // §hooked-fish: drawn on the take too"),
]


def run(tree):
    for rel, old, new in EDITS:
        swap(tree, rel, old, new)
    print("  last of it:", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES):
    run(t)
