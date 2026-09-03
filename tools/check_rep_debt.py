# -*- coding: utf-8 -*-
"""§o §breeding: the debt to the fishermen — poaching, restitution by weight, and the ban line.

    py -X utf8 tools/check_rep_debt.py

Mirrors fishing/Warden.java (credit / toNextPoint / toClear / banned) so the arithmetic can be run
without a JVM, and reads the three constants back out of the Java so the mirror cannot drift. Exit 1
on the first rule that breaks.
"""
import io, os, re, sys

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                   "common/src/main/java/com/riverfishing/fishing/Warden.java")
java = io.open(SRC, encoding="utf-8").read()


def const(name):
    m = re.search(r"public static final int %s = (-?\d+);" % name, java)
    if not m:
        sys.exit("check_rep_debt: %s is gone from Warden.java — the mirror below is stale" % name)
    return int(m.group(1))


BAN_REP = const("BAN_REP")                  # -15
POACH_REP = const("POACH_REP")              # 5
PER_POINT = const("GRAMS_PER_POINT")        # 5000


class Angler:
    """The two integers in PlayerData: contract_rep and rep_grams."""

    def __init__(self, rep=0, grams=0):
        self.rep, self.grams = rep, grams

    def poach(self):                        # Warden.onPoach — no clamp at zero any more
        self.rep -= POACH_REP

    def credit(self, grams):                # Warden.credit
        if grams <= 0:
            return 0
        banked = self.grams + grams
        points = banked // PER_POINT
        self.grams = banked - points * PER_POINT
        self.rep += points
        return points

    @property
    def banned(self):                       # Warden.banned
        return self.rep <= BAN_REP


def to_next(grams):
    return PER_POINT - grams


def to_clear(rep, grams):
    return 0 if rep >= 0 else -rep * PER_POINT - grams


ok = 0


def check(cond, what):
    global ok
    if not cond:
        sys.exit("check_rep_debt: FAILED — %s" % what)
    ok += 1


# 1. Nothing is lost: however the same kilograms arrive, the standing is the same. A hundred roach of
#    50 g must pay exactly what one 5 kg carp pays.
for rep0 in (0, -3, -20):
    one = Angler(rep0)
    one.credit(5000)
    many = Angler(rep0)
    for _ in range(100):
        many.credit(50)
    check((one.rep, one.grams) == (many.rep, many.grams), "5 kg at once != 5 kg in mouthfuls (%s)" % rep0)
    check(one.rep == rep0 + 1 and one.grams == 0, "5 kg is not exactly one point")

# 2. Nothing is gained: total grams in == points * PER_POINT + remainder, for any dribble of releases.
a = Angler(-7)
total = 0
for g in (10, 4990, 1, 12345, 999, 4000, 7):
    a.credit(g)
    total += g
check(a.rep == -7 + total // PER_POINT and a.grams == total % PER_POINT, "grams leak or double-count")

# 3. to_clear is exactly the restitution: release it and the standing is zero, with nothing banked.
for rep0, g0 in ((-1, 0), (-3, 2000), (-15, 4999), (-40, 1)):
    a = Angler(rep0, g0)
    a.credit(to_clear(rep0, g0))
    check((a.rep, a.grams) == (0, 0), "to_clear(%s, %s) does not land on zero" % (rep0, g0))
    # and one gram less is still a debt — the caption must not promise early
    b = Angler(rep0, g0)
    b.credit(to_clear(rep0, g0) - 1)
    check(b.rep < 0, "to_clear(%s, %s) overshoots" % (rep0, g0))

# 4. to_next is exactly one point, from any remainder.
for g0 in (0, 1, 2500, 4999):
    a = Angler(-2, g0)
    check(a.credit(to_next(g0)) == 1 and a.grams == 0, "to_next(%s) is not one point" % g0)
    b = Angler(-2, g0)
    check(b.credit(to_next(g0) - 1) == 0, "to_next(%s) pays early" % g0)

# 5. The ban line: three hauls close the board, two do not, and the board opens again the moment the
#    debt is worked back above BAN_REP — not only when it reaches zero.
a = Angler()
a.poach()
a.poach()
check(not a.banned, "two hauls should not close the board")
a.poach()
check(a.banned, "three hauls should close the board")
check(a.rep == 3 * -POACH_REP == BAN_REP, "three hauls should land exactly on the ban line")
a.credit(PER_POINT)                          # 5 kg back
check(not a.banned, "one point back should reopen the board")
a.credit(to_clear(a.rep, a.grams))
check((a.rep, a.grams) == (0, 0), "restitution should end at zero, not above it")

# 6. Poaching never pays: a haul always costs, however deep the hole already is.
a = Angler(-100)
before = a.rep
a.poach()
check(a.rep == before - POACH_REP, "the rep hit is clamped somewhere — it must not be")

# 7. The trusted shelf (ModVillagers.trustedSlots: `rep < step` skips the row) is shut for any debt,
#    because every Contracts.TRUST_STEPS entry is positive. Guard that assumption here.
steps = re.search(r"TRUST_STEPS = \{([^}]*)\}",
                  io.open(os.path.join(os.path.dirname(SRC), "Contracts.java"), encoding="utf-8").read())
check(steps and all(int(s) > 0 for s in steps.group(1).split(",")),
      "a TRUST_STEPS entry is <= 0 — the trusted shelf would open at a negative reputation")

print("check_rep_debt: ok (%d rules; ban at %d, %d g a point, %d off a haul)"
      % (ok, BAN_REP, PER_POINT, POACH_REP))
