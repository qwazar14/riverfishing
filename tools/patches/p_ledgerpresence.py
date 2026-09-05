# -*- coding: utf-8 -*-
"""§ledger-presence: for a brood that has not settled, the fish in the water are the ledger's heads.

    py -X utf8 tools/patches/p_ledgerpresence.py <root>

Reported: fry released, matured into ~120 fish the sounder listed, netted "all of them" — and now the
koi "do not bite here" while the lake summary still shows adults. Two populations, unconnected:

  the BANK   FishingPressureData: weight units. §fry-bank puts half a unit a head in, × STOCK_RESTORE
             0.18, capped at the transplant floor (100%); every catch costs 0.09 and the whole thing
             halves every 25 minutes idle. Eleven catches or half an hour and it is dry.
  the BOOK   StockedData: heads. F/M/Adults, one less per fish taken, printed by the sounder.

The bite and the net read the bank; the sounder prints the book. So a pond of 120 grown fry went
"gone" with 110 still on the book — and the reverse hole too: net the last fish and a leftover bank
kept the species biting with nobody left.

Now, for an unsettled species that HAS a ledger: heads are the presence — none, and it is gone
whatever the bank holds; otherwise at least heads / FRY_TO_SETTLE, full at thirty, the bank counting
only if it says more. The net asks the same function when its own bank number is zero. And the last
fish out of a brood (catchFromBrood → cleared) empties the bank around it, so the two agree at zero.
Settled water is untouched: it already read its head count. A water with no ledger at all — an old
save's transplant — keeps the bank, which is all it ever had.
"""
import io, os, re, sys

ROOT = sys.argv[1]
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")


def patch(rel, marker, edits):
    p = os.path.join(J, rel)
    s = io.open(p, encoding="utf-8").read()
    if marker in s:
        print("  %s: already patched" % rel); return s
    for old, new in edits:
        assert s.count(old) == 1, "%s: anchor %r" % (rel, old[:60])
        s = s.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  %s: patched" % rel)
    return s


# ---- 1. presence: the book first --------------------------------------------------------------------
patch("fishing/FishingManager.java", "§ledger-presence", [(
    "            if (!stocked.isStocked(region, s)) return Math.min(1.0, pd.surplusAround(cx, cz, s, level.getGameTime()));",
    """            if (!stocked.isStocked(region, s)) {
                double bank = Math.min(1.0, pd.surplusAround(cx, cz, s, level.getGameTime()));
                // §ledger-presence: the fish that are IN the water are the ledger's heads. The bank is a
                // weight bank that eleven catches or half an hour empties, and it was the only thing the
                // bite read — so a pond of 120 grown fry went "gone" with 110 still on the book. No ledger
                // (an old save's transplant): the bank is all there is.
                if (!stocked.hasBrood(region, s)) return bank;
                int heads = stocked.adults(region, s);
                if (heads <= 0) return 0.0;
                return Math.max(bank, Math.min(1.0, heads / (double) StockedData.FRY_TO_SETTLE));
            }"""), (
    # anchored on the branch line alone: the message call under it is spelled differently on 26.x
    """        } else if (stocked.catchFromBrood(region, id)) {
""",
    """        } else if (stocked.catchFromBrood(region, id)) {
            // §ledger-presence: the last fish out — and the bank around it goes with the book, or a
            // leftover would keep the species biting with nobody left in the water.
            FishingPressureData.get(level).clearStockAround(pos.getX() >> 4, pos.getZ() >> 4, id);
""")])

# ---- 2. the bank learns to forget a species around a spot --------------------------------------------
p = os.path.join(J, "fishing/FishingPressureData.java")
s = io.open(p, encoding="utf-8").read()
if "§ledger-presence" in s:
    print("  FishingPressureData: already patched")
else:
    m = re.search(r"    public double surplusAround\(int chunkX, int chunkZ, String species, long gameTime\) \{.*?\n    \}\n", s, re.S)
    assert m, "surplusAround moved"
    key = re.search(r"surplus\(\s*(.+?),\s*species, gameTime\)", m.group(0), re.S)
    assert key, "surplusAround's chunk key expression not found"
    chunk_key = " ".join(key.group(1).split())     # this tree's own spelling of the 3x3 chunk key
    s = s.replace(m.group(0), m.group(0) + """
    /**
     * §ledger-presence: forget a species' stock in the 3x3 chunks around a spot — the last fish of a
     * brood was taken and the book is empty, so the bank must be too. Pressure (a positive number)
     * stays; only the stock (a negative one) is dropped.
     */
    public void clearStockAround(int chunkX, int chunkZ, String species) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Map<String, Entry> per = chunks.get(%s);
                if (per == null) continue;
                Entry e = per.get(species);
                if (e != null && e.pressure() < 0) per.put(species, new Entry(0.0, e.tick()));
            }
        }
        setDirty();
    }
""" % chunk_key, 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  FishingPressureData: clearStockAround (chunk key: %s)" % chunk_key)

# ---- 3. the net asks the same question when its bank number is zero --------------------------------
patch("item/NetItem.java", "§ledger-presence", [(
    """            int pct = pressure.stockPercent(chunk, id, now);
            if (pct <= 0) continue;""",
    """            int pct = pressure.stockPercent(chunk, id, now);
            // §ledger-presence: an unsettled brood is counted by its book, not by a weight bank that
            // eleven hauls empty — the same function the bite reads, so the net and the sounder agree.
            if (pct <= 0 && !stocked.isStocked(region, id)) {
                pct = (int) Math.round(100 * FishingManager.stockedPresence(level, pos).applyAsDouble(p.id));
            }
            if (pct <= 0) continue;""")])
print("done")
