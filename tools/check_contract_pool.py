# -*- coding: utf-8 -*-
"""§contracts: every fish a contract could name has to be a fish somebody buys.

    py tools/check_contract_pool.py [--selftest]

A contract pays in emeralds, and the rate comes off the SAME map the fisherman's counter prices from
(ModVillagers.BASE_PRICE, filled as the trades are built). A species with no entry there cannot be
priced, so Contracts.pool() drops it — quietly, because there is nothing sensible to say to a player
about a fish that has no buyer.

That is correct behaviour and a bad thing to discover by surprise. A wave of new species added without
their buy trades would shrink the contract pool with nothing logged and nothing thrown; the board would
just get thinner, and on a young account it would go empty. So the gap is measured here, by name.

The check FAILS only when the pool is too thin to run a board — three jobs need three species. Below
that number it prints who is missing so the fix is one buyPrime line away.
"""
import io, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = "common/src/main/java/com/riverfishing/registry/ModItems.java"
VILLAGERS = "common/src/main/java/com/riverfishing/registry/ModVillagers.java"
# 26.x moved the trades into a datapack: one file per species per level, named by the id shape
# ModVillagers.buyTrade() reads back. Same question, a different place to ask it.
TRADES = "common/src/main/resources/data/riverfishing/villager_trade/fisherman"

# Contracts.PER_DAY: the board draws three distinct species, so fewer than three priced fish in the
# whole mod means a board that can never fill.
PER_DAY = 3

BUY = re.compile(r'\bbuyPrime\s*\(\s*[^,]+,\s*[^,]+,\s*"([a-z0-9_]+)"')


def species(text):
    """The FISH_SPECIES array, as written."""
    m = re.search(r"FISH_SPECIES\s*=\s*\{(.*?)\}", text, re.S)
    assert m, "FISH_SPECIES not found in ModItems.java"
    return re.findall(r'"([a-z0-9_]+)"', m.group(1))


def priced(text):
    return set(BUY.findall(text))


def priced_datapack(root):
    """26.x: every buy_<species>.json under the fisherman's trade folder."""
    out = set()
    folder = os.path.join(root, TRADES)
    if not os.path.isdir(folder):
        return out
    for base, _, names in os.walk(folder):
        for n in names:
            if n.startswith("buy_") and n.endswith(".json"):
                out.add(n[len("buy_"):-len(".json")])
    return out


def selftest():
    items = 'public static final String[] FISH_SPECIES = {\n "roach", "bream", "carp",\n};'
    good = ('buyPrime(t, 1, "roach", 1, 2);\n'
            'buyPrime(t, 2, "bream", 2, 3);\n'
            'buyPrime(t, 3, "carp", 4, 5);\n')
    thin = 'buyPrime(t, 1, "roach", 1, 2);\n'
    assert species(items) == ["roach", "bream", "carp"], species(items)
    assert priced(good) == {"roach", "bream", "carp"}, priced(good)
    assert len(priced(thin)) < PER_DAY, "a one-species map must read as too thin"
    assert priced("// buyPrimeOf(path, emeralds, xp)") == set(), "matched the wrong helper"
    assert priced_datapack("/nowhere/at/all") == set(), "an absent datapack must read as empty"
    print("self-test ok: reads the species list and the buy map, and a thin map reads as thin")
    return 0


def main():
    items = io.open(os.path.join(ROOT, ITEMS), encoding="utf-8").read()
    villagers = io.open(os.path.join(ROOT, VILLAGERS), encoding="utf-8").read()
    all_sp = species(items)
    buys = priced(villagers)
    where = "the buyPrime calls"
    if not buys:
        buys = priced_datapack(ROOT)
        where = "the villager_trade datapack"
    print("prices read from %s" % where)

    pool = [s for s in all_sp if s in buys]
    missing = [s for s in all_sp if s not in buys]

    print("%d species, %d of them buyable — that is the contract pool" % (len(all_sp), len(pool)))
    if missing:
        print("\n%d species no fisherman buys, so no contract can name them:" % len(missing))
        for s in missing:
            print("  %s" % s)
    if len(pool) < PER_DAY:
        print("\nthe pool cannot fill a board of %d" % PER_DAY)
        return 1
    print("\nthe pool can fill a board of %d" % PER_DAY)
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
