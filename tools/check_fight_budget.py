# -*- coding: utf-8 -*-
"""§fight-budget: every fish in the mod can actually be landed inside its own fight.

    py -X utf8 tools/check_fight_budget.py           # every species, min / median / max weight
    py -X utf8 tools/check_fight_budget.py beluga    # one species, with the tick-by-tick numbers

The beluga has now been reported three times as "bites, cannot be landed" — 0.8.0 (the required line
weight was 1202 kg), 0.8.1 (a dive cost two thirds of the bar) and again after that. Every one of them
was the same failure: THE LAND BAR DRAINS FASTER THAN AN ANGLER CAN FILL IT. Nobody could see it
because it is not in any one number — it is the sum of the passive give, the dives, the head-shakes and
the clock against what a crank is worth and how much stamina there is to spend on cranks.

So this file sums it. It re-implements the land-bar half of tickFight/reelPulse in Python, reading the
constants out of FishingManager.java, and runs a competent angler against every species: cranking five
times a second whenever there is stamina for it, holding the right course through every run, never
crouching. If that angler cannot land the fish inside its own timeout, the fish is not hard, it is
broken, and this fails.

Tension is modelled too, because it is what really rations the cranks: the angler here refuses to wind
above seven tenths of his breaking strain, which is what turns a twenty-second bar-fill into a real
fight. The tackle is assumed ADEQUATE and no better (a margin of TACKLE_MARGIN over what the fish asks) and the
drag is taken as a kilogram per thousand of reel size — both stated here rather than hidden, because a
player with worse tackle than that is supposed to lose.

What this file does NOT do is decide whether a fight is FUN. It answers one question — can it be won at
all — and a fish that fails it is broken rather than hard.
"""
import io, glob, json, math, os, random, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MGR = io.open(os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java"),
              encoding="utf-8").read()
PROF = os.path.join(ROOT, "common/src/main/resources/data/riverfishing/fish_profiles")


def num(pattern, what):
    m = re.search(pattern, MGR)
    if not m:
        print("FAILED: cannot read %s out of FishingManager.java (%s)" % (what, pattern))
        sys.exit(1)
    return float(m.group(1))


# ---- the constants, straight out of the Java -------------------------------------------------------
DIVE_COST = num(r"double DIVE_COST = ([\d.]+);", "DIVE_COST")
FATIGUE_SHARE = num(r"FATIGUE_FIGHT_SHARE = ([\d.]+);", "FATIGUE_FIGHT_SHARE")
KNEE = num(r"GIANT_KNEE_KG = ([\d.]+),", "GIANT_KNEE_KG")
GIVE = num(r"landProgress - ([\d.]+)\);", "the passive give")          # tickFight's per-tick bleed
SHAKE_COST = num(r"landProgress - ([\d.]+)\);\s*\n\s*level\.playSound", "the head-shake's line loss")
CRANK_STAM = num(r"anglerStamina - \(inRun \? [\d.]+ \* wrongWay : ([\d.]+)\)", "the calm crank's cost")
RUN_STAM = num(r"anglerStamina - \(inRun \? ([\d.]+) \* wrongWay", "the run crank's cost")
REGEN = num(r"isCrouching\(\) \? [\d.]+ : ([\d.]+);", "the stamina regen")
RUN_HOLD = num(r"anglerStamina -= ([\d.]+) \+ ", "the cost of holding a run")
TACKLE_MARGIN = 1.2      # the line out-guns the fish by a fifth: adequate, not lavish
GIANT_TAPER = num(r"GIANT_TAPER = ([\d.]+);", "GIANT_TAPER")

# Run shaping, by pattern: {pattern: (chance, dur_lo, dur_span, gap_lo, gap_span, run_bonus, timeout_bonus)}
def table(fn, cast=int):
    """Read one `case "x" -> ...;` switch out of the Java as {pattern: [numbers]}."""
    body = re.search(r"private static \w+ %s\(.*?\{\s*return switch.*?\{(.*?)\n        \};" % fn, MGR, re.S)
    out = {}
    def read(expr):
        expr = expr.split("//")[0]
        if "?" in expr:                       # a ternary on `progress`: take the early-fight branch
            expr = expr.split("?")[1]
        return [cast(x) for x in re.findall(r"\d+\.\d+" if cast is float else r"\b\d+\b", expr)]

    for case, expr in re.findall(r'case "(\w+)" -> (.*?);', body.group(1)):
        out[case] = read(expr)
    dflt = re.search(r"default -> (.*?);", body.group(1))
    if dflt:
        out["*"] = read(dflt.group(1))
    return out


CHANCE = table("rawRunChance", float)
DUR = table("rawRunDuration")
GAP = table("runInterval")
RUNS_BONUS = {}
for case, expr in re.findall(r'case "(\w+)" -> runs \+= (\d+);', MGR):
    RUNS_BONUS[case] = int(expr)


def pick(t, pattern, i, default=0):
    row = t.get(pattern, t.get("*", []))
    return row[i] if i < len(row) else default


def fight_mass(kg):
    return kg if kg <= KNEE else KNEE * math.pow(kg / KNEE, GIANT_TAPER)


# ---- one fight, tick by tick ------------------------------------------------------------------------
def fight(kg, pattern, runs_field, stamina_field, leader, reel, shake_dives, rng, trace=None):
    """Returns (landed, ticks, bars_needed) for a competent angler. Mirrors tickFight + reelPulse."""
    weight_stress = min(2.0, max(0.2, kg / 5.0))
    sens = 1.3 if reel == 0 else min(1.5, max(0.6, 1.0 + (4000 - reel) / 4000.0 * 0.5))
    land_pulse = 0.05 / (0.7 + 0.6 * weight_stress) * (0.9 + reel / 14000.0)
    timeout = min(3400.0, max(900.0, 700 + kg * 80 + pick(TIMEOUT_BONUS, pattern, 0)))

    runs = max(1, runs_field) + RUNS_BONUS.get(pattern, 0) + (1 if kg > 2.0 else 0)
    predator = leader                      # a bottom rod: ACTIVE tackle would only add to this
    shake_chance = 0.0
    if predator:
        w_amp = min(1.5, max(0.0, kg / 4.0))
        land_pulse *= 0.85
        runs += 1 + int(round(w_amp))
        shake_chance = 0.008 + 0.011 * w_amp
        timeout += 300

    # tension, exactly as hookUp builds it
    small_damp = min(1.0, 0.25 + kg / 1.5)
    load = min(2.0, max(0.25, math.pow(TACKLE_MARGIN, -0.6)))
    run_pulse = 0.18 * sens * (0.7 + 0.6 * weight_stress) * load * small_damp
    calm_pulse = 0.07 * sens * load * small_damp
    drag_relief = min(0.5, max(0.0, reel / 1000.0 / 10.0))
    relax = 0.010 + drag_relief * 0.02
    break_tension = min(1.0, max(0.1, min(1.0, TACKLE_MARGIN)))
    if predator:
        run_pulse *= 1.35 + 0.25 * w_amp
        calm_pulse *= 1.1
        break_tension *= 0.92
        relax *= 0.92

    stam_factor = min(1.6, max(0.5, stamina_field / 0.70))
    fatigue_tick = 1.0 / min(20.0 * (10.4 + 6.5 * kg) * stam_factor, timeout * FATIGUE_SHARE * stam_factor)

    prog, fatigue, stamina, resting, tension = 0.0, 0.0, 1.0, False, 0.0
    bill = {"give": 0.0, "dives": 0.0, "shakes": 0.0, "cranks": 0.0, "n": 0}
    run_left, run_total, scripted, next_run = 0, 0, False, 30 + rng.randint(0, 39)
    since_crank, drained = 99, 0.0
    for t in range(int(timeout)):
        prog = max(0.0, prog - GIVE)
        drained += GIVE
        bill["give"] += GIVE
        tension = max(0.0, tension - relax)
        if run_left > 0:
            run_left -= 1
            if run_left == 0:
                next_run = t + pick(GAP, pattern, 0) + rng.randint(0, pick(GAP, pattern, 1) - 1)
                scripted = False
        elif t >= next_run:
            if runs > 0 and rng.random() < (1.0 - 0.65 * fatigue) * pick(CHANCE, pattern, 0, 0.6):
                raw = pick(DUR, pattern, 0) + rng.randint(0, pick(DUR, pattern, 1) - 1)
                run_left = run_total = max(14, int(raw * (1.0 - 0.35 * fatigue)))
                runs -= 1
                scripted = True                      # a scripted run always gets a course
            else:
                next_run = t + 50
        # fatigue: held across its own run, a fish tires almost three times as fast
        gain = 1.0 + 1.8 if (run_left > 0 and scripted) else 1.0
        fatigue = min(1.0, fatigue + (fatigue_tick * gain if run_left > 0 else fatigue_tick * 0.2))
        if run_left > 0:
            stamina -= RUN_HOLD
            # §run-load: a running fish loads the tackle by itself; held across its course, less so
            tension += run_pulse * 0.12 * (1.0 - 0.55 * fatigue) * (1.9 - 1.35 if scripted else 1.0)
        if since_crank > 20:
            stamina += REGEN
        stamina = min(1.0, max(0.0, stamina))
        # the head-shake — NOT a run, and it must not be charged as one
        if predator and run_left == 0 and prog > 0.05 and rng.random() < shake_chance:
            run_left = run_total = 6 + rng.randint(0, 5)
            scripted = False
            prog = max(0.0, prog - SHAKE_COST)
            drained += SHAKE_COST
            bill["shakes"] += SHAKE_COST
        if pattern == "sounding" and run_left > 0 and (scripted or shake_dives):
            d = DIVE_COST / max(1, run_total)
            prog = max(0.0, prog - d)
            drained += d
            bill["dives"] += d
        # The angler: five cranks a second, and he STOPS when the mod tells him he is spent
        # (the actionbar warning is at 0.22) and rests until his arms are back. Cranking on down to
        # zero is what a first-timer does; pacing is what the fight teaches in the first minute.
        if resting and stamina >= 0.85:
            resting = False
        elif not resting and stamina <= 0.35:
            resting = True
        if not resting and since_crank >= 4 and tension < 0.70 * break_tension:
            in_run = run_left > 0
            directed = in_run and scripted
            mul = 1.0 if not in_run else (0.7 if directed else 0.2)
            prog = min(1.0, prog + land_pulse * mul * (1.0 + 0.6 * fatigue) * (0.35 + 0.65 * stamina))
            stamina -= RUN_STAM * 0.5 if directed else CRANK_STAM
            tension += (run_pulse if in_run else calm_pulse) * (1.0 - 0.55 * fatigue)                 * (2.2 - 1.7 if directed else 1.0) * (1.0 + 0.5 * (1.0 - stamina))
            since_crank = 0
            bill["cranks"] += land_pulse * mul * (1.0 + 0.6 * fatigue) * (0.35 + 0.65 * stamina)
            bill["n"] += 1
        else:
            since_crank += 1
        if prog >= 1.0:
            if trace is not None:
                trace.update(bill)
            return True, t, 1.0 + drained
    if trace is not None:
        trace.update(bill)
    return False, int(timeout), 1.0 + drained


TIMEOUT_BONUS = {"burst": [300], "relentless": [500], "sounding": [700], "greyhounding": [400], "*": [0]}

# ---- run it over every species -----------------------------------------------------------------------
only = sys.argv[1] if len(sys.argv) > 1 else None
fails, rows = [], []
for f in sorted(glob.glob(os.path.join(PROF, "*.json"))):
    sp = os.path.basename(f)[:-5]
    if only and sp != only:
        continue
    d = json.load(io.open(f, encoding="utf-8"))
    fi, ideal = d.get("fight", {}), d.get("ideal", {})
    pattern = fi.get("pattern", "steady")
    w = d.get("weight_g", {})
    reel = ideal.get("reel_size", 3000)
    leader = bool(ideal.get("requires_leader", False))
    for label in ("min", "mean", "max"):
        kg = w.get(label, w.get("mean", 1000)) / 1000.0
        rng = random.Random(hash((sp, label)) & 0xFFFF)
        won = sum(fight(kg, pattern, fi.get("runs", 3), fi.get("stamina", 0.7), leader, reel, False, rng)[0]
                  for _ in range(40))
        if won < 34:                    # a competent angler should land it at least 85 times in 100
            landed, ticks, need = fight(kg, pattern, fi.get("runs", 3), fi.get("stamina", 0.7),
                                        leader, reel, False, random.Random(1))
            fails.append("%s at %.0f kg (%s): landed %d of 40 — the bar needs %.1f fills in %.0f s"
                         % (sp, kg, pattern, won, need, ticks / 20.0))
        if only:
            for bug in (False, True):
                rng = random.Random(7)
                tr = {}
                res = [fight(kg, pattern, fi.get("runs", 3), fi.get("stamina", 0.7), leader, reel, bug, rng,
                             trace=tr) for _ in range(40)]
                rows.append("  %-5s %7.1f kg  %-10s %2d/40 landed in %3.0f s | drain: give %.1f dives "
                            "%.1f shakes %.1f = %.1f | %d cranks won %.1f"
                            % (label, kg, "shake=dive" if bug else "fixed", sum(r[0] for r in res),
                               sum(r[1] for r in res) / 40.0 / 20.0, tr["give"], tr["dives"],
                               tr["shakes"], sum(r[2] for r in res) / 40.0, tr["n"], tr["cranks"]))

# ---- and the line that caused it ---------------------------------------------------------------------
dive = MGR[MGR.index('if ("sounding".equals(session.fightPattern)'):][:900]
if "course.isRun()" not in dive.split("\n")[0] and "session.course.isRun()" not in dive[:400]:
    fails.append("the sounding dive-drain does not check that this is a RUN — a head-shake sets "
                 "runTicksLeft/runTicksTotal too, so every shake is charged a whole DIVE_COST (%.2f "
                 "of the bar) over its six ticks" % DIVE_COST)

for r in rows:
    print(r)
if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("fight budget: every species lands for a competent angler at min, median and max weight "
      "(give %.4f/t, dive %.2f, shake %.2f, crank %.3f stamina, regen %.4f/t)"
      % (GIVE, DIVE_COST, SHAKE_COST, CRANK_STAM, REGEN))
