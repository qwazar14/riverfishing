# -*- coding: utf-8 -*-
"""§nutrition-earns: re-derive the fed-spot balance after changing any of its constants.

The three numbers in FeedZoneData that decide whether groundbait has a trade-off at all —
FRESH_FLOOR, SATIETY_AMOUNT and SATIETY_BITE_COST — cannot be checked by an assert, because the
answer is not a value but a SHAPE: which nutrition wins, as the season and the throw cadence change.
This is the check. Run it after touching any of them and read the table.

What the table must show, or the feature has no decision in it:

    summer, unhurried feeding   ->  rich wins
    winter and under the ice    ->  lean wins
    feeding often               ->  leaner than feeding rarely, at every season

Both failures this was written to catch are one-liners away, and both looked reasonable at the time:

  * Cap the pull by nutrition and change nothing else, and RICH becomes strictly dominant instead of
    lean. Freshness swings the bite rate by up to 3.3x; satiety lives inside groundbaitScore, which is
    15% of matchScore, and can take at most 0.7 of it — about 10%. Whichever lever nutrition is tied
    to simply wins. That is why satiety also discounts the pull.
  * Leave the satiety gain at FEED_AMOUNT and it saturates at 1.0 within two throws of anything
    nourishing. A saturated cost no longer varies, so dividing it by appetite does nothing, and winter
    fishes exactly like summer.

    python tools/sim_feed_zone.py
    python tools/sim_feed_zone.py --floor 0.5 --satiety 0.15
"""
import argparse

# ---- the model, transcribed from FeedZoneData + BiteEngine + FishingManager ----
FEED_AMOUNT = 0.6            # how fast a spot builds up
SAT_HALFLIFE = 2400.0        # ticks
HALFLIFE_BASE, HALFLIFE_COARSE = 1800.0, 3600.0
LIFETIME_BASE, LIFETIME_COARSE = 3600.0, 10800.0
GROUNDBAIT_WEIGHT = 0.15     # groundbaitScore's share of matchScore


def simulate(nutrition, appetite, period, floor, satiety_amount, bite_cost,
             fraction=0.55, preferred=0.55, horizon=36000):
    """Fish a spot for `horizon` ticks, re-feeding every `period`. Returns mean result per tick."""
    ceiling = floor + (1.0 - floor) * nutrition
    halflife = HALFLIFE_BASE + HALFLIFE_COARSE * fraction
    lifetime = LIFETIME_BASE + LIFETIME_COARSE * fraction
    fraction_term = 0.5 + 0.5 * (1.0 - min(1.0, abs(fraction - preferred)))

    potency = satiety_base = 0.0
    last = 0
    total = 0.0
    t = 0
    while t < horizon:
        if t % period == 0:
            elapsed = t - last
            potency = min(ceiling, potency * 0.5 ** (elapsed / halflife) + FEED_AMOUNT)
            satiety_base = min(1.0, satiety_base * 0.5 ** (elapsed / SAT_HALFLIFE)
                               + satiety_amount * nutrition / max(0.15, appetite))
            last = t
        elapsed = t - last
        fresh = 0.0 if elapsed > lifetime else potency * 0.5 ** (elapsed / halflife)
        satiety = satiety_base * 0.5 ** (elapsed / SAT_HALFLIFE)
        satiety_term = 1.0 - bite_cost * satiety

        # A full fish is not interested: fullness discounts the pull, not only the bite.
        pull = min(1.0, fresh * satiety_term)
        rate = (1.0 + pull) / max(0.2, 1.0 - 0.40 * pull)          # feedBonus x the two speed terms
        quality = (1.0 - GROUNDBAIT_WEIGHT) + GROUNDBAIT_WEIGHT * fraction_term * satiety_term
        total += rate * quality
        t += 20
    return total / (horizon / 20)


def best_nutrition(appetite, period, floor, satiety_amount, bite_cost):
    return max(((simulate(n / 20.0, appetite, period, floor, satiety_amount, bite_cost), n / 20.0)
                for n in range(21)))[1]


SEASONS = [(1.00, "summer"), (0.75, "spring"), (0.50, "cool"),
           (0.30, "late autumn"), (0.15, "winter"), (0.08, "under the ice")]
PERIODS = [900, 1200, 1800, 2400]


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--floor", type=float, default=0.40, help="FRESH_FLOOR (default 0.40)")
    ap.add_argument("--satiety", type=float, default=0.20, help="SATIETY_AMOUNT (default 0.20)")
    ap.add_argument("--cost", type=float, default=0.70, help="SATIETY_BITE_COST (default 0.70)")
    a = ap.parse_args()

    print("FRESH_FLOOR %.2f   SATIETY_AMOUNT %.2f   SATIETY_BITE_COST %.2f\n"
          % (a.floor, a.satiety, a.cost))
    print("%-16s %s" % ("appetite", "best nutrition, feeding every " +
                        " / ".join("%d s" % (p // 20) for p in PERIODS)))
    rows = []
    for appetite, name in SEASONS:
        row = [best_nutrition(appetite, p, a.floor, a.satiety, a.cost) for p in PERIODS]
        rows.append(row)
        print("  %-14s %s" % ("%.2f %s" % (appetite, name),
                              " / ".join("%.2f" % v for v in row)))

    warm, cold = rows[0], rows[-1]
    print()
    if max(warm) - max(cold) < 0.3:
        print("BROKEN: the season does not change the answer — there is no trade-off, only a dominance.")
    elif all(rows[i][0] <= rows[i][-1] + 1e-9 for i in range(len(rows))):
        print("OK: rich in warm water, lean in cold, and leaner the more often you feed.")
    else:
        print("SUSPECT: feeding more often does not push the answer leaner at every season — look again.")


if __name__ == "__main__":
    main()
