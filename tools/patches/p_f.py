# -*- coding: utf-8 -*-
"""§breeding stream F: the ecosystem wired into the bite context.

    py -X utf8 tools/patches/p_f.py <repo root> [1211|1201|26]

Idempotent: every insert carries a `§f` marker and is skipped when already present; a missing or
non-unique anchor is printed and the script exits 1. Four call sites, not one, because FishingManager
builds a BiteContext in three places and refreshes one of them every fifteen seconds:
  environmentAt   the finder / analysis / habitat context (speciesFactor is null there -> ours alone)
  buildContext    the cast
  reEvaluate      the live refresh — it REPLACES speciesFactor and the feed fields, which would silently
                  drop the ecosystem from every running session fifteen seconds after the cast
  finderPayload   the "eco" string the finder prints (full payload only; the per-second strip skips it)
plus BiteContext.clarity and one loop in the client FinderScreen list. Also drops fishing/Ecosystem.java
into a port tree that lacks it (26: ResourceLocation -> Identifier).
"""
import io, os, shutil, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
HERE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVA = "common/src/main/java/com/riverfishing"
SRC = os.path.join(ROOT, JAVA)
MARK = "§f"


def sub1(rel, old, new):
    path = os.path.join(SRC, rel)
    text = io.open(path, encoding="utf-8").read()
    if new in text:
        return False
    if text.count(old) != 1:
        print("p_f: anchor found %d times in %s, expected 1:\n%s" % (text.count(old), rel, old))
        sys.exit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new))
    return True


changed = 0

# ---- new file into a port tree --------------------------------------------------------------------
eco = os.path.join(SRC, "fishing", "Ecosystem.java")
if not os.path.exists(eco):
    src = io.open(os.path.join(HERE, JAVA, "fishing", "Ecosystem.java"), encoding="utf-8").read()
    if DIALECT == "26":
        src = src.replace("ResourceLocation", "Identifier")
    io.open(eco, "w", encoding="utf-8", newline="\n").write(src)
    changed += 1

# ---- BiteContext: how clear the water is --------------------------------------------------------
changed += sub1("engine/BiteContext.java",
    "    public int bed;\n",
    "    public int bed;\n"
    "    /** " + MARK + " §ecosystem: water clarity, 1.0 untouched; filter-feeders raise it, rooting carp lower it. */\n"
    "    public double clarity = 1.0;\n")

# ---- FishingManager: the four sites ---------------------------------------------------------------
FM = "fishing/FishingManager.java"
changed += sub1(FM,
    "        env.stockedPresence = stockedPresence(level, pos);\n"
    "        return env;\n",
    "        env.stockedPresence = stockedPresence(level, pos);\n"
    "        Ecosystem.apply(level, pos, env);   // " + MARK + " §ecosystem: what the settled fish did to this water\n"
    "        return env;\n")
changed += sub1(FM,
    "        ctx.feedMix = feed.mix();\n"
    "\n"
    "        return ctx;\n",
    "        ctx.feedMix = feed.mix();\n"
    "        Ecosystem.apply(level, waterPos, ctx);   // " + MARK + " §ecosystem: after the feed read, so a feeder can top it up\n"
    "\n"
    "        return ctx;\n")
changed += sub1(FM,
    "        ctx.feedMix = feed.mix();\n"
    "\n"
    "        RandomSource random = level.getRandom();\n",
    "        ctx.feedMix = feed.mix();\n"
    "        // " + MARK + " §ecosystem: the factor and the feed were just replaced — put the water's effects back.\n"
    "        Ecosystem.apply(level, session.target, ctx);\n"
    "\n"
    "        RandomSource random = level.getRandom();\n")
changed += sub1(FM,
    "        root.put(\"water\", w);\n",
    "        // " + MARK + " §ecosystem: the active effects as lang-key tails; the strip has no room and asks every second.\n"
    "        if (full) w.putString(\"eco\", String.join(\";\", Ecosystem.effects(level, waterPos)));\n"
    "        root.put(\"water\", w);\n")

# ---- FinderScreen: one gold line per effect above the species list ------------------------------
get_eco = 'water().getStringOr("eco", "")' if DIALECT == "26" else 'water().getString("eco")'
changed += sub1("client/FinderScreen.java",
    "        List<Row> out = new ArrayList<>();\n"
    "        out.add(new Row(null, false, Component.translatable(\"finder.riverfishing.biting\", here.size())));\n",
    "        List<Row> out = new ArrayList<>();\n"
    "        // " + MARK + " §ecosystem: what a settled species or a bank-side upgrade did to this water, one line each.\n"
    "        for (String k : " + get_eco + ".split(\";\")) {\n"
    "            if (!k.isEmpty()) out.add(new Row(null, false, Component.translatable(\"ecosystem.riverfishing.\" + k)));\n"
    "        }\n"
    "        out.add(new Row(null, false, Component.translatable(\"finder.riverfishing.biting\", here.size())));\n")

print("p_f: %d change(s)" % changed)
