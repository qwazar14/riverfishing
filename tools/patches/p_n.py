# -*- coding: utf-8 -*-
"""§breeding stream N (layer 6): the pond decides the fish you catch.

    py -X utf8 tools/patches/p_n.py <repo root> [1211|1201|26]

Three existing files by anchor replacement — StockedData (one accessor the fry trap needs),
FishingManager (the weight roll, the landing's head count, the presence gate) and NetItem (one line).
Every insert carries a "§n" marker so a rerun finds it and does nothing; a drifted tree exits 1 with the
anchor printed. Written in the 1.21.1 dialect; 26.x gets the NBT getters rewritten by to26 — applied to
the ANCHORS too, because earlier streams' inserts already sit in the 26 tree in that dialect. 1.20.1
reads the 1.21.1 text unchanged for everything touched here.

Calls stream L/M's StockedData.adults / avgWeight / takeAdult by contract, unstubbed, and stream N's own
PondData.sameWater (edited directly, not here). BaitTrapBlockEntity and PondData are direct edits too.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§n"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def to26(java):
    """The 26.x dialect of a 1.21.1 snippet: only the idioms this stream's text actually uses."""
    if DIALECT != "26":
        return java
    java = re.sub(r"\.getLong\(([^()]+)\)", r".getLongOr(\1, 0L)", java)
    return java


def sub1(rel, old, new):
    """Exactly one anchor, replaced once. A tree already carrying the insert (its §n marker, or the
    literal replacement) is left alone — that is what makes a rerun a no-op."""
    path = os.path.join(SRC, rel)
    text = read(path)
    old, new = to26(old), to26(new)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_n: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ------------------------------------------------- StockedData: where the entry's release happened
# The trap has to compare its own water with the water the fry were released into; the position is
# already on the ledger (§k's notePos), it just had no reader.
sub1("fishing/StockedData.java",
     '''    public void notePos(long region, String species, BlockPos pos) {
        entry(region, species).putLong("Pos", pos.asLong());
        setDirty();
    }
''',
     '''    public void notePos(long region, String species, BlockPos pos) {
        entry(region, species).putLong("Pos", pos.asLong());
        setDirty();
    }

    /**
     * §n §breeding: where this brood was put in, or null when nobody released it here (it settled before
     * §k, or grew on its own). The fry trap asks it to tell one pond from the next inside one region.
     * Pos 0 is the origin block, which no released fish is realistically in — cheaper than a contains().
     */
    public BlockPos broodPos(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        long packed = t == null ? 0L : t.getLong("Pos");
        return packed == 0L ? null : BlockPos.of(packed);
    }
''')

# ------------------------------------------------- FishingManager: the pond's average specimen
FM = "fishing/FishingManager.java"

sub1(FM,
     '''    // ---- fish generation ----
''',
     '''    /**
     * §n §breeding: the average specimen of the population SETTLED here, in grams — 0 when the species is
     * not settled in the region, or when the ledger never measured one (a world from before the head
     * count, or a water that settled on fry alone). 0 means "ask the profile", the way it always did.
     */
    private static double pondAvgWeight(ServerLevel level, BlockPos pos, String species) {
        StockedData stocked = StockedData.get(level);
        long region = StockedData.region(pos);
        return stocked.isStocked(region, species) ? stocked.avgWeight(region, species) : 0;
    }

    // ---- fish generation ----
''')

# The weight roll. The bias here is the FINAL one — the livebait and lure floors have already raised it
# above — so a floored roll still comes up big out of a pond; §h's genes and §f's ecosystem scale it
# after, exactly as they scale a wild fish.
sub1(FM,
     '''        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;
        // §h §breeding: a settled population's size genes''',
     '''        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;
        // §n §breeding: where the species is settled and the ledger knows its average specimen, the POND
        // decides the size, not the profile: the same roll re-centred on the pond's own AvgW, keeping the
        // profile's spread (0.6..1.4 of the average) and clamped to the species' range. A pond stocked
        // with small fish gives small fish until it grows them (§m raises AvgW a season at a time) — the
        // answer to "my pond is a vending machine for profile-mean fish".
        // ponytail: the clamp is the profile's range, and the two multipliers below can still nudge a
        // fish a few percent past it — exactly as they already do for a wild one.
        double pondAvg = pondAvgWeight(level, session.target, p.id.getPath());
        if (pondAvg > 0) weight = Mth.clamp(pondAvg * (0.6 + 0.8 * biased), p.weightMin, p.weightMax);
        // §h §breeding: a settled population's size genes''')

# The landing pays the head count. Every landed fish routes through broodAfterCatch (legendary included),
# so this is the one place a rod takes a fish out of the water.
sub1(FM,
     '''        stocked.growIfDue(level, region, id);   // §k §farm: a landing is a touch of the water too
''',
     '''        stocked.growIfDue(level, region, id);   // §k §farm: a landing is a touch of the water too
        // §n §breeding: one fish out is one fish fewer. A settled water pays from its head count (§l);
        // an unsettled one still pays from the brood ledger below, which is the only population it has.
        // ponytail: a 20 g roach costs one head like a 5 kg carp — the ledger counts fish, not kilograms;
        // takeAdult(grams) if a pond should feel the difference.
        if (stocked.isStocked(region, id) && stocked.adults(region, id) > 0) stocked.takeAdult(region, id);
''')

# Presence: a settled water is only as full as its head count.
sub1(FM,
     '''        return id -> stocked.isStocked(region, id.getPath()) ? 1.0
                : Math.min(1.0, pd.surplusAround(cx, cz, id.getPath(), level.getGameTime()));''',
     '''        return id -> {
            String s = id.getPath();
            if (!stocked.isStocked(region, s)) return Math.min(1.0, pd.surplusAround(cx, cz, s, level.getGameTime()));
            // §n §breeding: fish the last adult out of a settled pond and the species is GONE there until
            // it grows back (§m) — a stocked water is a head count, not a permanent licence. Guarded on
            // AvgW: a ledger from before the head count, or one that settled on fry alone, has no Adults
            // to read and its 0 must not empty a water that was working.
            // ponytail: all-or-nothing — one adult bites like a hundred. Scale by adults/(8×pairs) if a
            // thin pond should also FEEL thin.
            if (stocked.avgWeight(region, s) > 0 && stocked.adults(region, s) <= 0) return 0.0;
            return 1.0;
        };''')

# ------------------------------------------------- NetItem: a netted fish is a fish out of the water too
sub1("item/NetItem.java",
     '''            pressure.addCatch(chunk, p.id.getPath(), now);

            // POACHING''',
     '''            pressure.addCatch(chunk, p.id.getPath(), now);
            // §n §breeding: out of a settled water, a netted fish costs a head like a landed one.
            if (stocked.isStocked(region, p.id.getPath()) && stocked.adults(region, p.id.getPath()) > 0) {
                stocked.takeAdult(region, p.id.getPath());
            }

            // POACHING''')

print("p_n: ok (%s)" % DIALECT)
