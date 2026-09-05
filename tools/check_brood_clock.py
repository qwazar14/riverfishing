# -*- coding: utf-8 -*-
"""§breeding: the settle clock and the brood ledger, modelled in Python so the rules can be run without a JVM.

    py -X utf8 tools/check_brood_clock.py

Mirrors StockedData (tools/patches/p_c.py): `due` is priced once, when the brood is complete, as the END
of the next FULL spawn window; a window entered halfway does not count. The ledger's catch rule takes
adults from the surplus side first, then fry ten at a time, and drops the clock when the condition no
longer holds. Exit 1 on the first rule that breaks.
"""
YEAR, SEASON, SUB = 96, 24, 8


def days_until(doy, season, sub):          # Calendar.daysUntil, verbatim
    start = season * SEASON + (0 if sub is None else sub * SUB)
    length = SEASON if sub is None else SUB
    since = (doy - start) % YEAR
    return 0 if since < length else YEAR - since


def due(today, season, sub):                # StockedData.tickSettle's pricing
    doy = today % YEAR
    length = SEASON if sub is None else SUB
    if days_until(doy, season, sub) == 0:
        start = season * SEASON + (0 if sub is None else sub * SUB)
        into = (doy - start) % YEAR
        return today + (0 if into == 0 else YEAR - into) + length
    return today + days_until(doy, season, sub) + length


def full_window_between(a, b, season, sub):
    """Ground truth: is there a whole window [s, s+len) with a <= s and s+len <= b?"""
    length = SEASON if sub is None else SUB
    start = season * SEASON + (0 if sub is None else sub * SUB)
    for s in range(a, b + 1):
        if s % YEAR == start and s + length <= b:
            return True
    return False


# 1. the clock: for every day of the year and every window shape, `due` is the FIRST day by which one
#    full window has passed — never earlier (a half window would count) and never later (the player waits
#    a year for nothing).
for season in range(4):
    for sub in (None, 0, 1, 2):
        for today in range(0, 2 * YEAR):
            d = due(today, season, sub)
            assert full_window_between(today, d, season, sub), (today, season, sub, d)
            assert not full_window_between(today, d - 1, season, sub), (today, season, sub, d)

# 2. the ledger: StockedData's counters, minus the NBT.
class Ledger:
    def __init__(s): s.F = s.M = s.Fry = 0; s.due = 0
    def ready(s): return min(s.F, s.M) >= 1 or s.Fry >= 30
    def add(s, sex):                       # -1 = no card: fill the side the pair is missing
        side = "M" if sex == 1 or (sex < 0 and s.M < s.F) else "F"
        setattr(s, side, getattr(s, side) + 1)
    def catch(s):                          # StockedData.catchFromBrood
        if s.F + s.M > 0:
            if s.F >= s.M: s.F -= 1
            else: s.M -= 1
        else:
            s.Fry = max(0, s.Fry - 10)
        if not s.ready(): s.due = 0
        return s.F + s.M + s.Fry == 0

l = Ledger()
l.add(-1); l.add(-1)
assert (l.F, l.M) == (1, 1), "two cardless fish make a pair"
l.add(-1); l.add(-1)
assert (l.F, l.M) == (2, 2)
l.due = 100
assert not l.catch() and (l.F, l.M) == (1, 2) and l.due == 100, "a spare fish goes first, the pair holds"
assert not l.catch() and (l.F, l.M) == (1, 1) and l.due == 100
assert not l.catch() and l.due == 0, "breaking the pair drops the clock"
assert l.catch(), "the last fish empties the ledger"
l = Ledger(); l.Fry = 35; l.due = 50
assert not l.catch() and l.Fry == 25 and l.due == 0, "fry go ten at a time; under 30 the clock stops"
for _ in range(3): l.catch()
assert l.Fry == 0
print("check_brood_clock: ok")
