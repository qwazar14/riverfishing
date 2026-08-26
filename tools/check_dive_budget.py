# -*- coding: utf-8 -*-
"""§dive-cost: a sounding fish must be landable inside its own fight timeout.

    py tools/check_dive_budget.py

Two Discord reports after 0.8.1 said the same thing from opposite ends: "we spent two hours and never
landed even a small beluga — its thirteen dives take the whole fight", and "the problem isn't the
600 kg one, even a small one runs 185 seconds and then it's gone". Both are the fight timing out.

The arithmetic behind it: a dive drains the land bar, and that drain was written as a RATE PER TICK in
0.5.0, when a dive was 60-109 ticks and therefore cost about a third of the bar. §fight-course then
lengthened every run ~2.2x and left the rate alone, so a dive silently went to two thirds of a bar —
and a beluga makes ten of them.

This check does the sum the engine does: total drain across every dive against the bar the angler can
actually pump back inside the timeout. It fails if a sounding species cannot be landed by a good
angler, which is the state 0.8.1 shipped in.
"""
import io, json, os, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROF = os.path.join(REPO, "common/src/main/resources/data/riverfishing/fish_profiles")

# --- the engine's own numbers (FishingManager) ---
DIVE_COST = 0.30                    # §dive-cost, whole bar per dive (FishingManager)
RAW_RUN = {"sounding": (135, 242)}  # rawRunDuration
GAP = {"sounding": (70, 129)}       # runInterval
EXTRA_RUNS = {"sounding": 3}
PATTERN_BONUS = {"sounding": 700}
CRANKS_PER_SEC = 5.0                # holding RMB repeats about five times a second
STAMINA_PER_CRANK = 0.014           # out of a run
DRIFT = 0.0008                      # landProgress bleeds this every tick, always
FATIGUE_FIGHT_SHARE = 0.55          # §tire-within-the-fight, the ceiling on the clock
RUN_MEAN = {"sounding": 188.5, "greyhounding": 54.5, "relentless": 124.5,
            "aggressive": 67.5, "burst": 147.5, "steady": 77.5,
            "active_then_passive": 90.5}
GAP_MEAN = {"sounding": 99.5, "greyhounding": 49.5, "relentless": 32.0,
            "aggressive": 39.5, "burst": 119.5, "steady": 74.5,
            "active_then_passive": 75.0}
EXTRA = {"aggressive": 2, "relentless": 3, "sounding": 3, "greyhounding": 2}
BONUS = {"burst": 300, "relentless": 500, "sounding": 700, "greyhounding": 400}
FATIGUE_FLOOR = 0.45                # below this a fight has no second act


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def land_pulse(kg, reel=14000):
    stress = clamp(kg / 5.0, 0.2, 2.0)
    return 0.05 / (0.7 + 0.6 * stress) * (0.9 + reel / 14000.0)


def budget(p, kg):
    pat = p["fight"].get("pattern", "steady")
    runs = max(1, p["fight"]["runs"]) + EXTRA_RUNS.get(pat, 0) + (1 if kg > 2 else 0)
    timeout = clamp(700 + kg * 80 + PATTERN_BONUS.get(pat, 0), 900, 3400)
    run_mean = sum(RAW_RUN[pat]) / 2.0
    gap_mean = sum(GAP[pat]) / 2.0

    dives = min(runs, int(timeout // (run_mean + gap_mean)))
    drain = dives * DIVE_COST + timeout * DRIFT

    # what the angler can put back: every tick outside a dive, cranking, stamina falling to its floor
    pump_ticks = timeout - dives * run_mean
    cranks = pump_ticks / 20.0 * CRANKS_PER_SEC
    # armStrength runs 1.0 -> 0.35 as stamina empties; take the average over the cranks that fit
    to_empty = 1.0 / STAMINA_PER_CRANK
    if cranks <= to_empty:
        arm = 1.0 - 0.325 * (cranks / to_empty)
    else:
        arm = (to_empty * 0.675 + (cranks - to_empty) * 0.35) / cranks
    gain = cranks * land_pulse(kg) * arm
    return dives, timeout, drain, gain


def fatigue_reached(p, kg):
    """How spent a fish is by the end of a fight it fought to the clock."""
    f = p["fight"]
    pat = f.get("pattern", "steady")
    fac = clamp(f["stamina"] / 0.70, 0.5, 1.6)
    timeout = clamp(700 + kg * 80 + BONUS.get(pat, 0), 900, 3400)
    runs = max(1, f["runs"]) + EXTRA.get(pat, 0) + (1 if kg > 2 else 0)
    run, gap = RUN_MEAN[pat], GAP_MEAN[pat]
    running = min(runs * run, timeout * run / (run + gap))
    burn = min(20.0 * (10.4 + 6.5 * kg) * fac, timeout * FATIGUE_FIGHT_SHARE * fac)
    return min(1.0, running / burn)


def main():
    profiles = {f[:-5]: json.load(io.open(os.path.join(PROF, f), encoding="utf-8"))
                for f in os.listdir(PROF)}
    sounding = sorted(k for k, v in profiles.items() if v["fight"].get("pattern") == "sounding")
    print("dive costs %.2f of the bar; the angler needs 1.00 of it left at the end\n" % DIVE_COST)
    print("%-18s %8s %6s %7s %8s %8s %7s" %
          ("species", "kg", "dives", "timeout", "drain", "pump", "spare"))
    bad = []
    for sp in sounding:
        p = profiles[sp]
        for kg in (p["weight_g"]["min"] / 1000.0, p["weight_g"]["mean"] / 1000.0,
                   p["weight_g"]["max"] / 1000.0):
            dives, timeout, drain, gain = budget(p, kg)
            spare = gain - drain - 1.0
            flag = "" if spare > 0 else "   <-- CANNOT BE LANDED"
            if spare <= 0:
                bad.append((sp, kg, spare))
            print("%-18s %8.0f %6d %6.0fs %8.2f %8.2f %7.2f%s"
                  % (p["display"], kg, dives, timeout / 20, drain, gain, spare, flag))
    # §tire-within-the-fight: a diver spends most of its fight running, so it is the one pattern where
    # "it never tires" is a fault rather than a character. A fish that reaches the net as fresh as it
    # started has no second act: fatigue is what shortens its runs, thins them out and lifts the
    # angler's gain, and a beluga was ending a full fight 0.11 spent.
    #
    # Deliberately NOT applied to every pattern. A flounder barely tires because a flounder barely
    # runs, and a marlin's runs are short between jumps — those are the fish, not the clock.
    limp = []
    for sp in sounding:
        got = fatigue_reached(profiles[sp], profiles[sp]["weight_g"]["mean"] / 1000.0)
        if got < FATIGUE_FLOOR:
            limp.append((sp, profiles[sp]["weight_g"]["mean"] / 1000.0, got))
    print("\nspent by the end of a full fight, divers only (floor %.2f):" % FATIGUE_FLOOR)
    for sp in sounding:
        kg = profiles[sp]["weight_g"]["mean"] / 1000.0
        got = fatigue_reached(profiles[sp], kg)
        print("  %-18s %6.0f kg -> %.2f%s" % (sp, kg, got, "   <-- never tires" if got < FATIGUE_FLOOR else ""))

    if bad:
        print("\n%d sounding case(s) cannot be landed inside the timeout:" % len(bad))
        for sp, kg, spare in bad:
            print("  %-16s %.0f kg, short by %.2f of the bar" % (sp, kg, -spare))
        return 1
    if limp:
        return 1
    print("\nevery sounding fish is landable inside its own timeout, and tires while it fights")
    return 0


if __name__ == "__main__":
    sys.exit(main())
