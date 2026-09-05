# -*- coding: utf-8 -*-
"""§net-ledger / §fry-bank: a net pays the ledger like a rod, and fry are not fish until they are.

    py -X utf8 tools/patches/p_netledger.py <root>

Reported, twice in one message:

  1. "~250 fry became ~120 adults; net them out and the sounder still says they are there."
     The rod's landing runs broodAfterCatch(): a settled water pays from its head count, an UNSETTLED
     one pays from the brood ledger (catchFromBrood), and fishing the last one out ends the attempt.
     The net carried its own copy of HALF of that — takeAdult(), gated on isStocked() — so on a water
     whose fry had matured (§fry-clock, 12 days) but not yet settled (a whole spawn window), a haul
     took nothing off F/M at all. The sounder prints F/M. It was telling the truth about the ledger.
     One call to the shared function now, and the net's copy is gone.

  2. "Released fry, used the net, got a 3 kg fish."
     releaseFry() banked alive*0.02 stock units on the spot, and the temporary population that buys is
     what the net hauls from — at the species' adult weights. Fry bank NOTHING now; the fish they turn
     into are banked in matureIfDue(), at the pond, half a unit each (§stock-units: a mean fish). A
     water that holds only fry holds nothing a net can lift, for twelve days, and then it does.

Dialect-neutral: no NBT accessor is added, only a return value and a call.
"""
import io, os, re, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
# 26.x: ChunkPos is a record with an (int, int) constructor, and the BlockPos form is ChunkPos.pack()
CHUNK = "net.minecraft.world.level.ChunkPos.pack(at)" if D == "26" else "new net.minecraft.world.level.ChunkPos(at).toLong()"
FM = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java")
SD = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/StockedData.java")
NI = os.path.join(ROOT, "common/src/main/java/com/riverfishing/item/NetItem.java")


def method_span(s, start_pat):
    m = re.search(start_pat, s)
    assert m, "cannot find " + start_pat
    depth, i = 0, m.end()
    while i < len(s):
        if s[i] == "{": depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0: return m.start(), i + 1
        i += 1
    raise AssertionError("unbalanced " + start_pat)


# ---- FishingManager: the shared function goes public; fry bank nothing; the bank skips zero --------
s = io.open(FM, encoding="utf-8").read()
if "§fry-bank" in s:
    print("  FishingManager: already patched")
else:
    old = "    private static void broodAfterCatch(ServerLevel level, ServerPlayer sp, BlockPos pos, ResourceLocation species) {"
    old26 = old.replace("ResourceLocation", "Identifier")
    if old in s:
        s = s.replace(old, old.replace("private static", "public static"), 1)
    elif old26 in s:
        s = s.replace(old26, old26.replace("private static", "public static"), 1)
    else:
        raise AssertionError("broodAfterCatch signature moved")

    old = "        release(level, pos, p, alive * 0.02, thrower, (stocked, region) -> {"
    assert old in s, "releaseFry's release() call moved"
    s = s.replace(old, """        // §fry-bank: fry bank NOTHING. The stock units a release banks are what the net hauls from, at
        // adult weights, and a water that holds only fry holds nothing a net can lift. The fish they
        // become are banked in StockedData.matureIfDue(), on the day they become them.
        release(level, pos, p, 0.0, thrower, (stocked, region) -> {""", 1)

    old = """        pressure.addStock(chunk, id, now, units, nativeHere ? FishingPressureData.FLOOR_NATIVE
                : resident ? FishingPressureData.FLOOR_SETTLED : FishingPressureData.FLOOR_TRANSPLANT);"""
    assert old in s, "release()'s addStock moved"
    s = s.replace(old, """        if (units > 0) {   // §fry-bank: addStock floors at 0.01 units, and fry are not that
            pressure.addStock(chunk, id, now, units, nativeHere ? FishingPressureData.FLOOR_NATIVE
                    : resident ? FishingPressureData.FLOOR_SETTLED : FishingPressureData.FLOOR_TRANSPLANT);
        }""", 1)
    io.open(FM, "w", encoding="utf-8", newline="\n").write(s)
    print("  FishingManager: broodAfterCatch public; fry bank 0; the bank skips 0")

# ---- StockedData: matureFry says how many, and matureIfDue banks them -------------------------------
d = io.open(SD, encoding="utf-8").read()
if "§fry-bank" in d:
    print("  StockedData: already patched")
else:
    # the pattern stops BEFORE the brace: the span walker counts from the first `{` it meets
    a, b = method_span(d, r"    private void matureFry\(CompoundTag t\) ")
    body = d[a:b]
    assert body.count("if (fry <= 0) return;") == 1 and body.rstrip().endswith("setDirty();\n    }"), "matureFry body changed"
    body = body.replace("private void matureFry(", "private int matureFry(", 1)
    body = body.replace("if (fry <= 0) return;", "if (fry <= 0) return 0;", 1)
    body = body[:body.rstrip().rfind("setDirty();")] + "setDirty();\n        return mature;   // §fry-bank: how many fish the water just gained\n    }"
    d = d[:a] + body + d[b:]

    old = """        t.remove("FryDay");
        matureFry(t);"""
    assert d.count(old) == 1, "matureIfDue's tail moved"
    d = d.replace(old, """        t.remove("FryDay");
        int grown = matureFry(t);
        // §fry-bank: the fry banked nothing when they went in (FishingManager.releaseFry); the fish they
        // have just become are banked here, at the pond, half a unit each — §stock-units' mean fish.
        // Until this ran, the water held nothing a net could lift; now it holds these.
        BlockPos at = broodPos(region, species);
        if (grown > 0 && at != null) {
            FishingPressureData.get(level).addStock(%s, species,
                    level.getGameTime(), grown * 0.5, FishingPressureData.FLOOR_TRANSPLANT);
        }""" % CHUNK, 1)
    io.open(SD, "w", encoding="utf-8", newline="\n").write(d)
    print("  StockedData: matured fry are banked on the day they mature")

# ---- NetItem: the rod's function, not half a copy of it ---------------------------------------------
n = io.open(NI, encoding="utf-8").read()
if "§net-ledger" in n:
    print("  NetItem: already patched")
else:
    old = """            // §n §breeding: out of a settled water, a netted fish costs a head like a landed one.
            if (stocked.isStocked(region, p.id.getPath()) && stocked.adults(region, p.id.getPath()) > 0) {
                stocked.takeAdult(region, p.id.getPath());
            }"""
    assert old in n, "NetItem's takeAdult block moved"
    n = n.replace(old, """            // §net-ledger: a netted fish pays the ledger exactly as a landed one does — a settled water
            // from its head count, an unsettled brood from F/M, and the last one out ends the attempt.
            // This used to be a copy of the settled half only, so 120 matured-but-unsettled fish could
            // be netted out one by one while the sounder went on counting every one of them.
            com.riverfishing.fishing.FishingManager.broodAfterCatch(level, sp, pos, p.id);""", 1)
    io.open(NI, "w", encoding="utf-8", newline="\n").write(n)
    print("  NetItem: every netted fish goes through broodAfterCatch")
print("done")
