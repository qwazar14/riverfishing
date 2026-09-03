# -*- coding: utf-8 -*-
"""§lm (0.9.0) — the pond growth arithmetic, mirrored in plain python so it can be argued with.

    py -X utf8 tools/check_pond_growth.py

Same numbers as StockedData.matureFry / growIfDue after tools/patches/p_lm.py. If you change a constant
there, change it here and watch which assert breaks — that is the whole point of the file. No Minecraft,
no gradle, no imports past the standard library.
"""
import math

SEASON_DAYS = 24          # engine/Calendar.SEASON_DAYS
YEAR_DAYS = 96
GROW_SHARE = 0.06         # of the species mean, per season
CAP_SHARE = 0.9           # of the species maximum
CROWD_PER_PAIR = 8        # over this many fish per pair, growth halves
SEASON_CAP = 4            # seasons paid out in one visit


def jround(x):
    """Java's Math.round: floor(x + 0.5). Python's round() goes to even and would drift off by one."""
    return int(math.floor(x + 0.5))


def mature_fry(e):
    """A spawn window closed: half the fry are fish, split ♀/♂. Returns how many made it."""
    fry = e["Fry"]
    e["Fry"] = 0
    if fry <= 0:
        return 0
    m = fry // 2
    e["F"] += m // 2
    e["M"] += m - m // 2
    e["Adults"] += m
    return m


def seasons_since(last_day, today):
    return min(SEASON_CAP, today // SEASON_DAYS - last_day // SEASON_DAYS)


def grow(e, seasons, mean, wmax, share_s=0.0, share_f=0.0, fry_surv=0.0, fed=False):
    """One growIfDue payout: head count and average weight, stepped a season at a time."""
    pairs = min(e["F"], e["M"])
    if pairs <= 0 or seasons <= 0:
        return 0.0
    add = max(1, jround(pairs * (1.0 + 0.5 * share_f) * (1.0 + fry_surv)))
    cap = jround(wmax * CAP_SHARE)
    step = jround(mean * GROW_SHARE * (1.0 + 0.5 * share_s) * (1.25 if fed else 1.0))
    if e["AvgW"] <= 0:
        e["AvgW"] = jround(mean)          # a ledger from before AvgW held mean-weight fish
    for _ in range(seasons):
        e["Adults"] += add
        crowded = e["Adults"] > CROWD_PER_PAIR * pairs
        e["AvgW"] = min(cap, e["AvgW"] + (step // 2 if crowded else step))
    return seasons * 3.0 * pairs * (1.0 + 0.5 * share_f) * (1.0 + fry_surv)   # units into addStock


def add_brood(e, grams=0):
    """The running mean AvgW keeps, one released fish at a time."""
    e["Adults"] += 1
    if grams > 0:
        e["AvgW"] = grams if e["AvgW"] <= 0 else jround((e["AvgW"] * (e["Adults"] - 1) + grams) / e["Adults"])


def entry(f=0, m=0, fry=0, adults=0, avg=0):
    return {"F": f, "M": m, "Fry": fry, "Adults": adults, "AvgW": avg}


# -- 1. thirty fry released turn into a stock you can see ------------------------------------------
e = entry(fry=30)
assert mature_fry(e) == 15
assert (e["Adults"], e["F"], e["M"], e["Fry"]) == (15, 7, 8, 0), e
# and that stock breeds: seven pairs, so the pond is a farm the season after the window
assert min(e["F"], e["M"]) == 7

# an odd number loses its half fish to rounding, never gains one
e2 = entry(fry=31)
assert mature_fry(e2) == 15 and e2["Adults"] == 15
assert mature_fry(entry(fry=1)) == 0          # one fry is not a population

# -- 2. a one-pair pond grows one to two fish a season ---------------------------------------------
for share_f in (0.0, 0.5, 1.0):
    for fry_surv in (0.0, 0.15, 0.3):
        e = entry(f=1, m=1, adults=2, avg=1000)
        grow(e, 1, mean=1000, wmax=2000, share_f=share_f, fry_surv=fry_surv)
        assert 1 <= e["Adults"] - 2 <= 2, (share_f, fry_surv, e)
# five pairs, five to ten
e = entry(f=5, m=5, adults=10, avg=1000)
grow(e, 1, mean=1000, wmax=2000, share_f=1.0, fry_surv=0.3)
assert 5 <= e["Adults"] - 10 <= 10, e

# -- 3. AvgW climbs from mean x 0.6 towards 0.9 x max in about twenty seasons ----------------------
MEAN, WMAX = 1000, 2000
CAP = jround(WMAX * CAP_SHARE)                # 1800
e = entry(f=1, m=1, adults=2)
for _ in range(2):
    add_brood(e, grams=jround(MEAN * 0.6))    # two released fish, both 600 g
assert e["AvgW"] == 600, e
# thin the pond by hand each season so crowding never fires: this is the clean weight curve
seasons_to_cap = None
avg = 600
for s in range(1, 41):
    solo = entry(f=1, m=1, adults=2, avg=avg)
    grow(solo, 1, mean=MEAN, wmax=WMAX)
    avg = solo["AvgW"]
    if avg >= CAP and seasons_to_cap is None:
        seasons_to_cap = s
assert seasons_to_cap == 20, seasons_to_cap    # (1800 - 600) / 60
assert avg == CAP                              # -- 4. the cap holds, it does not overshoot

# the cap is a hard ceiling even from below it by one step
e = entry(f=1, m=1, adults=2, avg=CAP - 1)
grow(e, 1, mean=MEAN, wmax=WMAX)
assert e["AvgW"] == CAP, e

# a feeding station and a big-gene population get there faster, never further
e = entry(f=1, m=1, adults=2, avg=600)
grow(e, SEASON_CAP, mean=MEAN, wmax=WMAX, share_s=1.0, fed=True)
assert e["AvgW"] == 600 + 4 * jround(MEAN * GROW_SHARE * 1.5 * 1.25), e   # 4 x 112 = 448
assert e["AvgW"] < CAP

# -- 5. overcrowding halves the growth --------------------------------------------------------------
# one pair, nine fish already: the very first season is over 8 x pairs, so every step is halved
crowded = entry(f=1, m=1, adults=9, avg=600)
roomy = entry(f=1, m=1, adults=2, avg=600)
grow(crowded, 4, mean=MEAN, wmax=WMAX)
grow(roomy, 4, mean=MEAN, wmax=WMAX)
step = jround(MEAN * GROW_SHARE)                 # 60
assert crowded["AvgW"] == 600 + 4 * (step // 2), crowded    # 600 + 120
assert roomy["AvgW"] == 600 + 4 * step, roomy              # 600 + 240
assert crowded["AvgW"] < roomy["AvgW"]
# and it bites mid-run: 2 fish, +1 a season, crosses 8 at the seventh
mid = entry(f=1, m=1, adults=2, avg=600)
grow(mid, 4, mean=MEAN, wmax=WMAX)               # 3,4,5,6 fish — still roomy
assert mid["AvgW"] == 600 + 4 * step
grow(mid, 4, mean=MEAN, wmax=WMAX)               # 7,8,9,10 — halved from the ninth on
assert mid["AvgW"] == 600 + 4 * step + 2 * step + 2 * (step // 2), mid

# -- 6. the season clock: four visits or one, the pond pays the same, and never twice ---------------
assert seasons_since(0, 0) == 0
assert seasons_since(0, SEASON_DAYS - 1) == 0
assert seasons_since(0, SEASON_DAYS) == 1
assert seasons_since(SEASON_DAYS, SEASON_DAYS + 5) == 0        # same season, already paid
assert seasons_since(0, YEAR_DAYS) == SEASON_CAP               # a whole year is four seasons
assert seasons_since(0, YEAR_DAYS * 10) == SEASON_CAP          # ponytail: a decade pays a year
one_go = entry(f=2, m=2, adults=4, avg=600)
step_by_step = entry(f=2, m=2, adults=4, avg=600)
grow(one_go, 4, mean=MEAN, wmax=WMAX)
for _ in range(4):
    grow(step_by_step, 1, mean=MEAN, wmax=WMAX)
assert one_go == step_by_step, (one_go, step_by_step)

# -- 7. an old world: no Adults, no AvgW, but F and M -----------------------------------------------
old = entry(f=3, m=2)                             # seedAdults: the brood IS the head count
old["Adults"] = old["F"] + old["M"]
assert old["Adults"] == 5
units = grow(old, 1, mean=MEAN, wmax=WMAX)
assert old["AvgW"] == jround(MEAN) + step        # seeded at the species mean, then grew
assert units > 0

# -- 8. the running mean over a released brood -------------------------------------------------------
e = entry()
for g in (400, 600, 800):
    add_brood(e, grams=g)
assert e["Adults"] == 3 and e["AvgW"] == 600, e
add_brood(e)                                      # a fish nobody weighed leaves the average alone
assert e["Adults"] == 4 and e["AvgW"] == 600, e

print("check_pond_growth: %d seasons from %d g to the %d g cap; 30 fry -> 15 adults; all asserts pass"
      % (seasons_to_cap, 600, CAP))
