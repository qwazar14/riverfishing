# -*- coding: utf-8 -*-
"""§shoal-deep: the shoal measured the water's depth in ONE column at the cell's probe point — a bank
column more often than not, depth 1 or 2 — and gated the species on it, so every fish that wants three
blocks of water (132 of the 160 species the table brought) was absent from water that has it two steps
away. The probe now prefers the deepest of its four columns, the gate reads the deepest column within
three blocks (what habitatContext already does for the finder), and the fish are still placed by the
probe's own column so none is drawn inside the bank. Idempotent; run on each tree.
    py tools/patches/p_shoal_deep.py [tree ...]"""
import io, os, sys

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
ST = "common/src/main/java/com/riverfishing/fishing/ShoalTracker.java"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    if new in s: return s
    assert old in s, what
    return s.replace(old, new, 1)


def run(tree):
    fm = os.path.join(tree, FM); s = rd(fm)
    s = sub(s, "    static int measureDepth(ServerLevel level, BlockPos surface) {",
            "    /** §shoal-deep: the deepest column within {@code r} blocks — the water's depth, not the bank's. */\n"
            "    static int deepestAround(ServerLevel level, BlockPos pos, int r) {\n"
            "        int best = measureDepth(level, pos);\n"
            "        BlockPos.MutableBlockPos scan = pos.mutable();\n"
            "        for (int dx = -r; dx <= r; dx++) {\n"
            "            for (int dz = -r; dz <= r; dz++) {\n"
            "                if (dx == 0 && dz == 0) continue;\n"
            "                scan.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);\n"
            "                if (!level.getFluidState(scan).is(net.minecraft.tags.FluidTags.WATER)) continue;\n"
            "                best = Math.max(best, measureDepth(level, scan));\n"
            "            }\n"
            "        }\n"
            "        return best;\n"
            "    }\n\n"
            "    static int measureDepth(ServerLevel level, BlockPos surface) {", "measureDepth anchor")
    wr(fm, s)

    st = os.path.join(tree, ST); s = rd(st)
    s = sub(s, "            BiteContext env = FishingManager.environmentAt(level, surface, body);\n",
            "            BiteContext env = FishingManager.environmentAt(level, surface, body);\n"
            "            // §shoal-deep: the species gate reads the WATER's depth (the deepest column within three\n"
            "            // blocks), the fish are placed by the probe's own column so none is drawn inside the bank.\n"
            "            int colDepth = env.waterDepth;\n"
            "            env.waterDepth = FishingManager.deepestAround(level, surface, 3);\n", "environmentAt anchor")
    s = sub(s, "pick(pool, env.waterDepth, want, minLen, rng);", "pick(pool, colDepth, want, minLen, rng);", "pick anchor")
    s = sub(s, "            if (level.getFluidState(p).isEmpty()) continue;\n            return p;\n        }\n        return null;\n",
            "            if (level.getFluidState(p).isEmpty()) continue;\n"
            "            // §shoal-deep: the deepest of the four probes, not the first — the fish sit where the water is.\n"
            "            int depth = FishingManager.measureDepth(level, p);\n"
            "            if (depth > bestDepth) { bestDepth = depth; best = p; }\n"
            "        }\n        return best;\n", "probe anchor")
    s = sub(s, "        int bx = cx * CELL, bz = cz * CELL;\n        for (int i = 0; i < 4; i++) {",
            "        int bx = cx * CELL, bz = cz * CELL;\n        BlockPos best = null; int bestDepth = 0;\n        for (int i = 0; i < 4; i++) {", "probe head anchor")
    wr(st, s)
    print("  patched", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES): run(t)
