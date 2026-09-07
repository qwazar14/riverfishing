# -*- coding: utf-8 -*-
"""§runs-by-size-2: the pattern's extra runs (relentless +3, sounding +3, aggressive/greyhounding +2)
were added AFTER the size scaling, so a 72 g barbel still made five runs at a 97 % run chance — the
"small barbel fights like a 2 kg fish" report. The bonus now scales with the specimen too; the two
budget checks mirror it. Idempotent; run on each tree.  py tools/patches/p_runs_pattern_size.py"""
import io, os, sys

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"

OLD = """        int runs = Math.max(1, (int) Math.round(profile.fightRuns * size));
        switch (profile.fightPattern) {
            case "aggressive" -> runs += 2;
            case "relentless" -> runs += 3; // §grass-carp: the amur just keeps charging
            case "burst" -> runs = Math.max(2, runs);
            case "sounding" -> runs += 3;      // §big-game: tuna dives, again and again
            case "greyhounding" -> runs += 2;  // §big-game: billfish jump series
            default -> { /* steady / active_then_passive use the profile value */ }
        }
"""
NEW = """        // §runs-by-size-2: the pattern's extra runs are the full-grown fish's too — a 72 g barbel with
        // the amur's +3 was still a five-run fight
        int bonus = switch (profile.fightPattern) {
            case "aggressive" -> 2;
            case "relentless" -> 3;   // §grass-carp: the amur just keeps charging
            case "sounding" -> 3;     // §big-game: tuna dives, again and again
            case "greyhounding" -> 2; // §big-game: billfish jump series
            default -> 0;             // steady / burst / active_then_passive use the profile value
        };
        int runs = Math.max(1, (int) Math.round((profile.fightRuns + bonus) * size));
        if ("burst".equals(profile.fightPattern)) runs = Math.max(2, runs);
"""


def run(tree):
    p = os.path.join(tree, FM); s = io.open(p, encoding="utf-8").read()
    if NEW not in s:
        assert OLD in s, "fightRunCount anchor"
        s = s.replace(OLD, NEW, 1)
        io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    # the two checks
    for f, old, new in (
        ("tools/check_fight_budget.py",
         "    runs = max(1, round(runs_field * min(1.0, max(0.4, 0.4 + 0.6 * kg / max(0.001, max_kg))))) + RUNS_BONUS.get(pattern, 0) + (1 if kg > 2.0 else 0)",
         "    runs = max(1, round((runs_field + RUNS_BONUS.get(pattern, 0)) * min(1.0, max(0.4, 0.4 + 0.6 * kg / max(0.001, max_kg))))) + (1 if kg > 2.0 else 0)   # §runs-by-size-2"),
        ("tools/check_dive_budget.py",
         "    runs = max(1, round(p[\"fight\"][\"runs\"] * min(1.0, max(0.4, 0.4 + 0.6 * kg / max(0.001, max_kg))))) + EXTRA_RUNS.get(pat, 0) + (1 if kg > 2 else 0)   # §runs-by-size",
         "    runs = max(1, round((p[\"fight\"][\"runs\"] + EXTRA_RUNS.get(pat, 0)) * min(1.0, max(0.4, 0.4 + 0.6 * kg / max(0.001, max_kg))))) + (1 if kg > 2 else 0)   # §runs-by-size-2"),
        ("tools/check_dive_budget.py",
         "    runs = max(1, round(f[\"runs\"] * min(1.0, max(0.4, 0.4 + 0.6 * kg / max(0.001, p[\"weight_g\"][\"max\"] / 1000.0))))) + EXTRA.get(pat, 0) + (1 if kg > 2 else 0)   # §runs-by-size",
         "    runs = max(1, round((f[\"runs\"] + EXTRA.get(pat, 0)) * min(1.0, max(0.4, 0.4 + 0.6 * kg / max(0.001, p[\"weight_g\"][\"max\"] / 1000.0))))) + (1 if kg > 2 else 0)   # §runs-by-size-2"),
    ):
        q = os.path.join(tree, f); t = io.open(q, encoding="utf-8").read()
        if new not in t:
            assert old in t, f
            t = t.replace(old, new, 1); io.open(q, "w", encoding="utf-8", newline="\n").write(t)
    print("  patched", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES): run(t)
