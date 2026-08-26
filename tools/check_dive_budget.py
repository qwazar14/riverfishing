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
    if bad:
        print("\n%d sounding case(s) cannot be landed inside the timeout:" % len(bad))
        for sp, kg, spare in bad:
            print("  %-16s %.0f kg, short by %.2f of the bar" % (sp, kg, -spare))
        return 1
    print("\nevery sounding fish is landable inside its own timeout")
    return 0


if __name__ == "__main__":
    sys.exit(main())
