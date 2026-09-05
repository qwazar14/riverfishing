# -*- coding: utf-8 -*-
"""§breeding stream A: the calendar wired into existing files.

    py -X utf8 tools/patches/p_a.py <repo root> [1211|1201|26]

Idempotent: every replacement carries a `§breeding-A` marker and is skipped when the marker is already in
the file, so a rerun is a no-op. A missing anchor is printed and the script exits 1 — a silent partial
patch is worse than none. The dialect argument is accepted and ignored: nothing here touches NBT,
messages or ResourceLocation, so the same anchors hold in all three trees (checked against rf1201/rf26).

Also drops engine/Calendar.java into the target tree when it is not there yet (a port tree), because
FishProfile references it the moment it is patched.
"""
import io, os, shutil, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "common", "src", "main", "java", "com", "riverfishing")
HERE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MARK = "§breeding-A"


def sub1(rel, old, new):
    path = os.path.join(SRC, rel)
    text = io.open(path, encoding="utf-8").read()
    if new in text:
        return False
    if text.count(old) != 1:
        print("anchor not found (or not unique) in %s:\n%s" % (rel, old))
        sys.exit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new))
    return True


changed = 0

# ---- new file into a port tree -------------------------------------------------------------------
cal = os.path.join(SRC, "engine", "Calendar.java")
if not os.path.exists(cal):
    shutil.copy(os.path.join(HERE, "common", "src", "main", "java", "com", "riverfishing", "engine", "Calendar.java"), cal)
    changed += 1

# ---- integration/SeasonProvider: the own calendar when SS is absent, plus the sub-season/day reads ----
changed += sub1("integration/SeasonProvider.java",
    " * and runs without it; when the mod is absent {@link #getSeason} returns null and the engine\n"
    " * uses a season factor of 1.0 for everyone.\n",
    " * and runs without it. " + MARK + ": when the mod is absent {@link #getSeason} answers from\n"
    " * {@link Calendar}'s own 96-day year, so the bite engine, the order board and the spawning windows\n"
    " * all see seasons whether or not Serene Seasons is installed — and the SAME seasons when it is.\n")
changed += sub1("integration/SeasonProvider.java",
    "import com.riverfishing.engine.Season;\n",
    "import com.riverfishing.engine.Calendar;\nimport com.riverfishing.engine.Season;\n")
changed += sub1("integration/SeasonProvider.java",
    "    private static Method getSeason;\n",
    "    private static Method getSeason;\n"
    "    // " + MARK + ": optional — an older SS without them still gives us the season.\n"
    "    private static Method getSubSeason, getCycleTicks, getCycleDuration;\n")
changed += sub1("integration/SeasonProvider.java",
    "            getSeason = state.getMethod(\"getSeason\");\n",
    "            getSeason = state.getMethod(\"getSeason\");\n"
    "            // " + MARK + ": each optional method in its own try — one missing must not cost the season.\n"
    "            try { getSubSeason = state.getMethod(\"getSubSeason\"); } catch (Throwable ignored) {}\n"
    "            try {\n"
    "                getCycleTicks = state.getMethod(\"getSeasonCycleTicks\");\n"
    "                getCycleDuration = state.getMethod(\"getSeasonCycleDuration\");\n"
    "            } catch (Throwable ignored) { getCycleTicks = getCycleDuration = null; }\n")
changed += sub1("integration/SeasonProvider.java",
    "    /** @return the current season, or null if Serene Seasons is not present. */\n"
    "    public static Season getSeason(Level level) {\n"
    "        if (!init()) return null;\n",
    "    /** " + MARK + ": Serene Seasons' season when present and readable, else {@link Calendar}'s own. Never null. */\n"
    "    public static Season getSeason(Level level) {\n"
    "        Season s = serene(level);\n"
    "        return s != null ? s : Calendar.season(level);\n"
    "    }\n"
    "\n"
    "    /** True when Serene Seasons is loaded and its API resolved. */\n"
    "    public static boolean present() {\n"
    "        return init();\n"
    "    }\n"
    "\n"
    "    /** @return Serene Seasons' season, or null if it is not present (or the read failed). */\n"
    "    public static Season serene(Level level) {\n"
    "        if (!init()) return null;\n")
changed += sub1("integration/SeasonProvider.java",
    "        } catch (Throwable t) {\n"
    "            return null;\n"
    "        }\n"
    "    }\n"
    "}\n",
    "        } catch (Throwable t) {\n"
    "            return null;\n"
    "        }\n"
    "    }\n"
    "\n"
    "    /**\n"
    "     * " + MARK + ": SS's sub-season (EARLY_SPRING, MID_SUMMER, ...) folded to its first word, or null when SS\n"
    "     * is absent, too old to say, or names one we do not know.\n"
    "     */\n"
    "    public static Calendar.Sub sereneSub(Level level) {\n"
    "        if (!init() || getSubSeason == null) return null;\n"
    "        try {\n"
    "            String name = ((Enum<?>) getSubSeason.invoke(getSeasonState.invoke(null, level))).name();\n"
    "            return Calendar.subOf(name.substring(0, Math.max(0, name.indexOf('_'))));\n"
    "        } catch (Throwable t) {\n"
    "            return null;\n"
    "        }\n"
    "    }\n"
    "\n"
    "    /**\n"
    "     * " + MARK + ": where SS is in its year, scaled onto Calendar's 96 days; -1 when SS is absent.\n"
    "     *\n"
    "     * <p>The cycle's ticks over its duration is the one ratio SS exposes that needs no knowledge of\n"
    "     * its day length or sub-season length (both are config). Its seasons are quarters and its\n"
    "     * sub-seasons twelfths of the cycle, so floor(96 * ratio) / 24 is exactly SS's season and\n"
    "     * (floor(96 * ratio) % 24) / 8 exactly its sub-season — the scaled day cannot contradict them.\n"
    "     * Without those methods the middle of the reported sub-season is the honest answer.\n"
    "     */\n"
    "    public static int sereneDayOfYear(Level level) {\n"
    "        if (!init()) return -1;\n"
    "        try {\n"
    "            Object state = getSeasonState.invoke(null, level);\n"
    "            if (getCycleTicks != null) {\n"
    "                long ticks = ((Number) getCycleTicks.invoke(state)).longValue();\n"
    "                long total = ((Number) getCycleDuration.invoke(state)).longValue();\n"
    "                if (total > 0) return (int) Math.floorMod(ticks * Calendar.YEAR_DAYS / total, (long) Calendar.YEAR_DAYS);\n"
    "            }\n"
    "            Season s = serene(level);\n"
    "            Calendar.Sub sub = sereneSub(level);\n"
    "            if (s == null) return -1;\n"
    "            return s.ordinal() * Calendar.SEASON_DAYS\n"
    "                    + (sub == null ? Calendar.SEASON_DAYS / 2 : sub.ordinal() * Calendar.SUB_DAYS + Calendar.SUB_DAYS / 2);\n"
    "        } catch (Throwable t) {\n"
    "            return -1;\n"
    "        }\n"
    "    }\n"
    "}\n")

# ---- fish/FishProfile: the spawning window ------------------------------------------------------
changed += sub1("fish/FishProfile.java",
    "import com.riverfishing.engine.Season;\n",
    "import com.riverfishing.engine.Calendar;\nimport com.riverfishing.engine.Season;\n")
changed += sub1("fish/FishProfile.java",
    "    public final String latin;\n",
    "    public final String latin;\n"
    "    /**\n"
    "     * " + MARK + ": when this species spawns. Never null — a profile without a \"spawn\" block gets its\n"
    "     * family's habit ({@link #defaultSpawnSeason}), the same table tools/add_spawn.py wrote from.\n"
    "     */\n"
    "    public final Season spawnSeason;\n"
    "    /** " + MARK + ": the third of that season, or null for the whole of it. */\n"
    "    public final Calendar.Sub spawnSub;\n")
changed += sub1("fish/FishProfile.java",
    "        this.latin = b.latin;\n",
    "        this.latin = b.latin;\n"
    "        this.spawnSeason = b.spawnSeason;\n"
    "        this.spawnSub = b.spawnSub;\n")
changed += sub1("fish/FishProfile.java",
    "        b.latin = GsonHelper.getAsString(json, \"latin\", \"\");\n",
    "        b.latin = GsonHelper.getAsString(json, \"latin\", \"\");\n"
    "        // " + MARK + ": \"spawn\": {\"season\": \"spring\", \"sub\": \"late\"}, sub optional. A block that names a season\n"
    "        // but no sub means the whole season; no block at all means the family's habit, sub included.\n"
    "        if (json.has(\"spawn\")) {\n"
    "            JsonObject spawn = GsonHelper.getAsJsonObject(json, \"spawn\");\n"
    "            Season s = Calendar.seasonOf(GsonHelper.getAsString(spawn, \"season\", \"\"));\n"
    "            b.spawnSeason = s != null ? s : defaultSpawnSeason(b.group);\n"
    "            b.spawnSub = Calendar.subOf(GsonHelper.getAsString(spawn, \"sub\", \"\"));\n"
    "        } else {\n"
    "            b.spawnSeason = defaultSpawnSeason(b.group);\n"
    "            b.spawnSub = defaultSpawnSub(b.group);\n"
    "        }\n")
changed += sub1("fish/FishProfile.java",
    "    private static Map<String, Double> readDoubleMap(JsonObject obj) {\n",
    "    /**\n"
    "     * " + MARK + ": the family's spawning window for a profile that does not say. Cyprinids, sturgeon and\n"
    "     * the inshore sea fish go on the late-spring warm-up, predators as soon as the ice is off, salmonids\n"
    "     * on the autumn gravel, the big-game fish through the summer. Mirrors the table in tools/add_spawn.py.\n"
    "     */\n"
    "    public static Season defaultSpawnSeason(String group) {\n"
    "        return switch (group == null ? \"\" : group) {\n"
    "            case FishGroup.SALMONID -> Season.AUTUMN;\n"
    "            case FishGroup.BIG_GAME -> Season.SUMMER;\n"
    "            default -> Season.SPRING;\n"
    "        };\n"
    "    }\n"
    "\n"
    "    /** " + MARK + ": the family's sub-season, null = the whole season. */\n"
    "    public static Calendar.Sub defaultSpawnSub(String group) {\n"
    "        return switch (group == null ? \"\" : group) {\n"
    "            case FishGroup.PREDATOR -> Calendar.Sub.EARLY;\n"
    "            case FishGroup.SALMONID -> Calendar.Sub.MID;\n"
    "            case FishGroup.BIG_GAME -> null;\n"
    "            default -> Calendar.Sub.LATE;\n"
    "        };\n"
    "    }\n"
    "\n"
    "    private static Map<String, Double> readDoubleMap(JsonObject obj) {\n")
changed += sub1("fish/FishProfile.java",
    "        String latin = \"\";\n",
    "        String latin = \"\";\n"
    "        Season spawnSeason;                 // " + MARK + "\n"
    "        Calendar.Sub spawnSub;\n")

# ---- comments that said "null without Serene Seasons" and are no longer true --------------------
changed += sub1("engine/BiteContext.java",
    "    public Season season;           // null when Serene Seasons is absent -> factor 1.0\n",
    "    public Season season;           // " + MARK + ": SS's or Calendar's own; null only if never set -> factor 1.0\n")
changed += sub1("fishing/FishingManager.java",
    "     * (needs Serene Seasons; without it the season is null and recovery stays neutral).\n",
    "     * (" + MARK + ": Serene Seasons' spring when present, else Calendar's own — never neutral any more).\n")
changed += sub1("engine/Season.java",
    "/** Season buckets (§10.1). Mapped from Serene Seasons if present, otherwise unused. */\n",
    "/** Season buckets (§10.1). Mapped from Serene Seasons if present, else Calendar's own (" + MARK + "). */\n")

print("p_a: %d change(s)" % changed)
