# -*- coding: utf-8 -*-
"""§giant-taper: every fish in the mod must be landable by tackle that exists in the mod.

    py tools/check_giant_taper.py

The 0.8.0 report was arithmetic, not difficulty: a 400 kg marlin asked 802 kg of line and the
strongest braid in the game carries 108, so the fight was lost before it started. This check reads
the shipped fish profiles and the strongest legal tackle and fails if any species' HEAVIEST possible
specimen is out of reach again — which is exactly what happens the next time someone adds a
half-tonne fish without touching the taper.

Margin is effectiveStrain / requiredKg, the same number FishingManager clamps into breakTension.
Below 0.4 the line snaps at a fifth of the bar and no play can save it; 1.0 means the tackle exactly
matches the fish.
"""
import io, json, os, sys

PROF = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "common/src/main/resources/data/riverfishing/fish_profiles")

KNEE, TAPER = 20.0, 0.55          # FishingManager.GIANT_KNEE_KG / GIANT_TAPER
BEST_LINE_KG = 100 * 0.60 ** 2 * 3.0   # braid 0.60 mm: 100 x d^2 x 3.0  (LineType)
BEST_DRAG_KG = 7.0 + (14000 - 7000) / 1000.0 * 2.5   # ReelItem.dragKgFor(14000)
BEST_STRAIN = BEST_LINE_KG + 0.5 * BEST_DRAG_KG      # FishingManager.effectiveStrain
FLOOR = 0.4                       # below this the fish is not hard, it is impossible


def fight_mass(kg):
    return kg if kg <= KNEE else KNEE * (kg / KNEE) ** TAPER


def required(strength, kg):
    return max(0.5, strength * (1.0 + fight_mass(kg)) * 2.0)


def main():
    # the taper must not move a single fish the mod was balanced around
    for kg in (0.005, 0.5, 5.0, 19.999, 20.0):
        assert abs(fight_mass(kg) - kg) < 1e-9, "taper moved a sub-knee fish: %s" % kg
    assert fight_mass(20.0001) > 20.0, "taper is not continuous at the knee"
    assert fight_mass(400) < fight_mass(600) < required(1.0, 600), "taper must stay monotonic"

    worst = []
    for f in sorted(os.listdir(PROF)):
        p = json.load(io.open(os.path.join(PROF, f), encoding="utf-8"))
        heaviest = max(p["weight_g"]["max"], (p.get("legendary") or {}).get("weight_g", 0)) / 1000.0
        margin = BEST_STRAIN / required(p["fight"]["strength"], heaviest)
        worst.append((margin, f[:-5], heaviest))
    worst.sort()

    print("strongest tackle in the mod: %.0f kg line + %.0f kg drag -> %.1f kg of strain"
          % (BEST_LINE_KG, BEST_DRAG_KG, BEST_STRAIN))
    print("\nthe ten hardest fish at their heaviest:")
    for margin, name, kg in worst[:10]:
        print("  %-20s %7.1f kg   needs %6.1f kg   margin %.2f%s"
              % (name, kg, required(json.load(io.open(os.path.join(PROF, name + ".json"),
                                                      encoding="utf-8"))["fight"]["strength"], kg),
                 margin, "   <-- UNLANDABLE" if margin < FLOOR else ""))

    dead = [(m, n, k) for m, n, k in worst if m < FLOOR]
    if dead:
        print("\n%d species cannot be landed by any tackle in the mod:" % len(dead))
        for m, n, k in dead:
            print("  %s at %.0f kg (margin %.2f)" % (n, k, m))
        return 1
    print("\nOK - all %d species landable at their heaviest (worst margin %.2f)" % (len(worst), worst[0][0]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
