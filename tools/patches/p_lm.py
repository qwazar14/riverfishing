# -*- coding: utf-8 -*-
"""§breeding streams L+M (layer 6): the head count, fry maturing, and growth per SEASON.

    py -X utf8 tools/patches/p_lm.py <repo root> [1211|1201|26]

Anchor replacement on ONE existing file, fishing/StockedData.java; every insert carries a "§lm" marker
so a rerun finds it and does nothing. Exit 1 with the missing anchor when a tree has drifted. Written in
the 1.21.1 dialect; 26.x gets the NBT getters and the message call rewritten by to26 — applied to the
ANCHORS too, because earlier streams' inserts already sit in the 26 tree in that dialect. 1.20.1 reads
the 1.21.1 text unchanged for everything touched here.

What it does, in one paragraph: the ledger entry gains `Adults` (the head count — F+M only ever counted
BREEDERS, so a pond of thirty fish that were never sexed showed as nothing) and `AvgW` (grams, the
pond's average specimen, a running mean over the released brood). Fry stop evaporating: at every spawn
window close half of them become fish, split ♀/♂. And growth stops being an annual event — it fires
every season, which is what makes a private pond look different on day 30 instead of day 100.

Callers to update (another stream owns them): `addBrood` gains a 7th parameter `int grams`; the 6-arg
form stays as an overload passing 0, so nothing breaks if the caller is not patched. New API:
`adults(region, species)`, `avgWeight(region, species)`, `takeAdult(region, species)`.
Lang: tools/patches/lang_lm.json — `message.riverfishing.pond_grew` is CHANGED (4 arguments now).
Check: tools/check_pond_growth.py mirrors the arithmetic.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§lm"


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
    java = re.sub(r"\.getInt\(([^()]+)\)", r".getIntOr(\1, 0)", java)
    java = re.sub(r"\.getLong\(([^()]+)\)", r".getLongOr(\1, 0L)", java)
    java = re.sub(r"\.getDouble\(([^()]+)\)", r".getDoubleOr(\1, 0.0)", java)
    java = re.sub(r"\.getString\(([^()]+)\)", r'.getStringOr(\1, "")', java)
    java = re.sub(r"new net\.minecraft\.world\.level\.ChunkPos\((\w+)\)\.toLong\(\)",
                  r"net.minecraft.world.level.ChunkPos.pack(\1)", java)
    java = re.sub(r"displayClientMessage\((.+?), true\);", r"sendOverlayMessage(\1);", java, flags=re.S)
    return java


def sub1(rel, old, new):
    """Exactly one anchor, replaced once. A tree already carrying the insert (its §lm marker, or the
    literal replacement) is left alone — that is what makes a rerun a no-op."""
    path = os.path.join(SRC, rel)
    text = read(path)
    old, new = to26(old), to26(new)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_lm: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


SD = "fishing/StockedData.java"

# ---------------------------------------------------------------- addBrood: the head count and the average
# F/M are BREEDERS. A pond where every fish was thrown back without a card had F=M=0 and looked empty
# even with forty fish in it, which is half the "my pond never changes" complaint. Adults is the other
# number, and AvgW is what stream N rolls the bite's weight around.
sub1(SD,
     '''    /** sex: 0 ♀, 1 ♂ (Card.Sex), -1 unknown — an old or villager-bought fish has no card and fills whichever side the pair is missing. */
    public void addBrood(long region, String species, int sex, long day, String genome, java.util.UUID owner) {
        CompoundTag t = entry(region, species);
        String side = sex == 1 || (sex < 0 && t.getInt("M") < t.getInt("F")) ? "M" : "F";''',
     '''    /** The old six-argument call: a fish whose weight the caller never had. §lm */
    public void addBrood(long region, String species, int sex, long day, String genome, java.util.UUID owner) {
        addBrood(region, species, sex, day, genome, owner, 0);
    }

    /**
     * sex: 0 ♀, 1 ♂ (Card.Sex), -1 unknown — an old or villager-bought fish has no card and fills whichever side the pair is missing.
     *
     * <p>§lm (0.9.0): {@code grams} is the fish's weight, and AvgW is the running mean of it over the
     * brood — the pond's average specimen, which is what you catch out of it later (stream N). 0 means
     * "not weighed": the average stays where it was rather than being dragged towards nothing.
     */
    public void addBrood(long region, String species, int sex, long day, String genome, java.util.UUID owner, int grams) {
        CompoundTag t = entry(region, species);
        int adults = seedAdults(t) + 1;   // seeded from F/M FIRST, or this fish is counted twice
        t.putInt("Adults", adults);
        if (grams > 0) {
            int avg = t.getInt("AvgW");
            t.putInt("AvgW", avg <= 0 ? grams : (int) Math.round((avg * (double) (adults - 1) + grams) / adults));
        }
        String side = sex == 1 || (sex < 0 && t.getInt("M") < t.getInt("F")) ? "M" : "F";''')

# ---------------------------------------------------------------- the head count: accessors, maturing, taking one out
sub1(SD,
     '''    public int fryCount(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getInt("Fry");
    }
''',
     '''    public int fryCount(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getInt("Fry");
    }

    // ---- §lm §breeding (0.9.0): the head count -------------------------------------------------
    // Adults is every fish in the water; F/M stay what they always were, the ones known to breed. The
    // two disagree on purpose: fry that matured go into both, a villager-bought fish with no card goes
    // into both, and a pond can be full of fish with no pair in it.

    /** Fish in this water, brood and grown-on fry together. 0 for a water nobody stocked. */
    public int adults(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : seedAdults(t);
    }

    /** The pond's average specimen in grams; 0 = unknown (an old ledger, or nothing was ever weighed). */
    public int avgWeight(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getInt("AvgW");
    }

    /**
     * A ledger written before Adults existed knows only its breeders — so the first time anyone asks,
     * the brood IS the head count. Zero with no brood stays zero: that water has no fish to seed from,
     * and inventing some would hand the player a pond that filled itself on an update.
     */
    private int seedAdults(CompoundTag t) {
        int a = t.getInt("Adults");
        int fm = t.getInt("F") + t.getInt("M");
        if (a <= 0 && fm > 0) {
            t.putInt("Adults", a = fm);
            setDirty();
        }
        return a;
    }

    /** One fish out of the water — landed, netted, sold. The sexes take turns so a pair holds longest. */
    public void takeAdult(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return;
        t.putInt("Adults", Math.max(0, seedAdults(t) - 1));
        int f = t.getInt("F"), m = t.getInt("M");
        if (f + m > 0) t.putInt(f >= m ? "F" : "M", (f >= m ? f : m) - 1);
        setDirty();
    }

    /**
     * A spawn window closed: half the fry in the water are fish now, the other half fed the perch. They
     * join the brood evenly, so thirty fry become seven ♀ and eight ♂ — a stock the finder can show and
     * the pond can grow from. Called at the settle (tickSettle) and at every window close after it
     * (growIfDue); an unsettled brood's fry mature the day it settles, which is the only day they count.
     */
    private void matureFry(CompoundTag t) {
        int fry = t.getInt("Fry");
        t.remove("Fry");
        if (fry <= 0) return;
        int mature = fry / 2;
        int adults = seedAdults(t) + mature;   // seeded before F/M take the new fish in
        t.putInt("F", t.getInt("F") + mature / 2);
        t.putInt("M", t.getInt("M") + mature - mature / 2);
        t.putInt("Adults", adults);
        setDirty();
    }
''')

# ---------------------------------------------------------------- catchFromBrood keeps Adults honest
sub1(SD,
     '''        int f = t.getInt("F"), m = t.getInt("M");
        if (f + m > 0) t.putInt(f >= m ? "F" : "M", (f >= m ? f : m) - 1);
        else t.putInt("Fry", Math.max(0, t.getInt("Fry") - 10));''',
     '''        int f = t.getInt("F"), m = t.getInt("M");
        if (f + m > 0) {
            t.putInt(f >= m ? "F" : "M", (f >= m ? f : m) - 1);
            t.putInt("Adults", Math.max(0, seedAdults(t) - 1));   // §lm: one fish out is one fish fewer
        } else t.putInt("Fry", Math.max(0, t.getInt("Fry") - 10));''')

# ---------------------------------------------------------------- the settle: fry become the first stock
sub1(SD,
     '''        // §k §farm: the pairs stay — a settled pond's brood is what grows it (growIfDue); fry became fish.
        for (String k : new String[]{"Fry", "Since", "Due"}) t.remove(k);''',
     '''        // §k §farm: the pairs stay — a settled pond's brood is what grows it (growIfDue); fry became fish.
        matureFry(t);   // §lm: literally — half of them, split ♀/♂, instead of the ledger just dropping Fry
        for (String k : new String[]{"Since", "Due"}) t.remove(k);''')

# ---------------------------------------------------------------- the farm header: it is per season now
sub1(SD,
     '''    // A settled species with a pair on the ledger grows by itself once a year: every time its spawn
    // window CLOSES, the chunk of the last release (Pos) banks 3 units per pair, more for fertile stock
    // and for a bank with cover and oxygen. LastGrow is the world day of the last close paid out, so
    // however many times the water is touched, a window pays once. There is no world ticker: release,
    // landing and a once-a-minute player tick (ModEvents) all ask growIfDue.''',
     '''    // A settled species with a pair on the ledger grows by itself. §lm: every SEASON, not every year —
    // a window closes once in 96 days and a pond that only moved on that one day read as dead, which is
    // the complaint this answers. The chunk of the last release (Pos) banks 3 units per pair per season,
    // more for fertile stock and for a bank with cover and oxygen, and the fish in it put on weight.
    // LastGrow is the world day last paid, LastMat the window close last matured, so however many times
    // the water is touched each pays once. There is no world ticker: release, landing and a
    // once-a-minute player tick (ModEvents) all ask growIfDue.''')

# ---------------------------------------------------------------- daysToGrow: to the next season, not the next window
sub1(SD,
     '''    /** Days until the window next closes, 1..96 — the farm view's "grows in". */
    public static int daysToGrow(ServerLevel level, com.riverfishing.fish.FishProfile p) {
        return com.riverfishing.engine.Calendar.YEAR_DAYS - sinceClose(level, p);
    }''',
     '''    /**
     * Days until the next growth tick, 1..24 — the farm view's "grows in". §lm: growth is per SEASON now,
     * so this counts to the next season boundary of the same world-day clock growIfDue runs on (not
     * {@link com.riverfishing.engine.Calendar#dayOfYear}, which Serene Seasons can move). {@code p} stays
     * in the signature for the callers, and because the window close it measures still matures the fry.
     */
    public static int daysToGrow(ServerLevel level, com.riverfishing.fish.FishProfile p) {
        long today = worldDay(level);
        return (int) (com.riverfishing.engine.Calendar.SEASON_DAYS
                - Math.floorMod(today, (long) com.riverfishing.engine.Calendar.SEASON_DAYS));
    }''')

# ---------------------------------------------------------------- growIfDue: two clocks, a head count, a weight curve
sub1(SD,
     '''    public void growIfDue(ServerLevel level, long region, String species) {
        if (!isStocked(region, species)) return;
        CompoundTag t = brood.get(key(region, species));
        if (t == null || !t.contains("Pos")) return;
        com.riverfishing.fish.FishProfile p = com.riverfishing.fish.FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(species));
        if (p == null) return;
        long today = worldDay(level);
        long lastClose = today - sinceClose(level, p);
        long last = t.getLong("LastGrow");
        if (last <= 0) {
            // The clock starts the first time the water is looked at as a farm — no back pay for the
            // years before, and the settle day itself (Due IS a closing day) does not pay either.
            t.putLong("LastGrow", today);
            setDirty();
            return;
        }
        if (lastClose <= last) return;
        // Windows closed in (last, lastClose]: one per year, rounded up because the first close after
        // an arbitrary start day counts. ponytail: capped at 3 — a pond nobody visited for a decade
        // pays three years, not ten; and with a Serene Seasons year longer than 96 the count is off the
        // way Calendar.daysUntil is (see its note).
        int windows = (int) Math.min(3, (lastClose - last + com.riverfishing.engine.Calendar.YEAR_DAYS - 1) / com.riverfishing.engine.Calendar.YEAR_DAYS);
        t.putLong("LastGrow", lastClose);
        setDirty();
        int pairs = broodPairs(region, species);
        if (pairs <= 0) return;
        BlockPos pos = BlockPos.of(t.getLong("Pos"));
        double units = windows * 3.0 * pairs * (1.0 + 0.5 * shares(region, species)[3])
                * (1.0 + Ecosystem.frySurvival(level, pos));
        FishingPressureData pd = FishingPressureData.get(level);
        long chunk = new net.minecraft.world.level.ChunkPos(pos).toLong();
        long now = level.getGameTime();
        pd.addStock(chunk, species, now, units, FishingPressureData.FLOOR_SETTLED);
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.translatable(
                "message.riverfishing.pond_grew", net.minecraft.network.chat.Component.translatable("fish.riverfishing." + species),
                pd.stockPercent(chunk, species, now)).withStyle(net.minecraft.ChatFormatting.AQUA);
        for (net.minecraft.server.level.ServerPlayer sp : level.players()) {
            if (sp.blockPosition().closerThan(pos, 64)) sp.displayClientMessage(msg, true);
        }
    }''',
     '''    public void growIfDue(ServerLevel level, long region, String species) {
        if (!isStocked(region, species)) return;
        CompoundTag t = brood.get(key(region, species));
        if (t == null || !t.contains("Pos")) return;
        com.riverfishing.fish.FishProfile p = com.riverfishing.fish.FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(species));
        if (p == null) return;
        long today = worldDay(level);
        // §lm: TWO clocks on one entry. LastMat is the spawn window's close — once a year, when the fry
        // that lived through it become fish. LastGrow is the season — four times a year, because the
        // pond has to visibly move between windows or it reads as scenery.
        long lastClose = today - sinceClose(level, p);
        long lastMat = t.getLong("LastMat");
        if (lastMat <= 0 || lastClose > lastMat) {
            if (lastMat > 0) matureFry(t);   // the first read only starts the clock: no back pay
            t.putLong("LastMat", lastClose);
            setDirty();
        }
        long last = t.getLong("LastGrow");
        if (last <= 0) {
            // The clock starts the first time the water is looked at as a farm — no back pay for the
            // years before, and the settle day itself does not pay either.
            t.putLong("LastGrow", today);
            setDirty();
            return;
        }
        // Seasons crossed since, counted on the calendar's own boundaries so daysToGrow can answer
        // without the ledger. ponytail: capped at 4 — a pond nobody visited for a decade pays a year,
        // not ten; and with a Serene Seasons year longer than 96 the count is off the way
        // Calendar.daysUntil is (see its note).
        int seasons = (int) Math.min(4L, today / com.riverfishing.engine.Calendar.SEASON_DAYS
                - last / com.riverfishing.engine.Calendar.SEASON_DAYS);
        if (seasons <= 0) return;
        t.putLong("LastGrow", today);
        setDirty();
        int pairs = broodPairs(region, species);
        if (pairs <= 0) return;
        BlockPos pos = BlockPos.of(t.getLong("Pos"));
        double[] sh = shares(region, species);
        double fry = Ecosystem.frySurvival(level, pos);
        boolean fed = WaterUpgrades.at(level, pos).contains("feeding_station");
        // Per season: the pairs put out a fish or two (one pair one to two, five pairs five to ten), and
        // every fish in the water puts on weight — 6% of the species mean, more from big genes and from
        // a feeding station, never past nine tenths of the species maximum because a pond does not make
        // record fish. Stepped one season at a time: the crowding check reads the head count the season
        // before it, so four seasons at once must not be one big multiplication.
        int add = Math.max(1, (int) Math.round(pairs * (1.0 + 0.5 * sh[3]) * (1.0 + fry)));
        int cap = (int) Math.round(p.weightMax * 0.9);
        int step = (int) Math.round(p.weightMean * 0.06 * (1.0 + 0.5 * sh[0]) * (fed ? 1.25 : 1.0));
        int adults = seedAdults(t);
        int avg = t.getInt("AvgW");
        // A ledger from before AvgW has no average; the fish there were rolled off the species mean, so
        // that is what the pond is holding. It grows from there like any other.
        if (avg <= 0) avg = (int) Math.round(p.weightMean);
        for (int i = 0; i < seasons; i++) {
            adults += add;
            // Overcrowding: eight fish to a pair is a pond eating itself thin, and it grows half as
            // fast. Thinning it — a rod, a net, the keepnet — is what gets the weight back.
            avg = Math.min(cap, avg + (adults > 8 * pairs ? step / 2 : step));
        }
        t.putInt("Adults", adults);
        t.putInt("AvgW", avg);
        double units = seasons * 3.0 * pairs * (1.0 + 0.5 * sh[3]) * (1.0 + fry);
        FishingPressureData pd = FishingPressureData.get(level);
        long chunk = new net.minecraft.world.level.ChunkPos(pos).toLong();
        long now = level.getGameTime();
        pd.addStock(chunk, species, now, units, FishingPressureData.FLOOR_SETTLED);
        // §lm: the head count and the average are the two numbers the player asked for — a percentage
        // alone never told anyone whether the pond had ten small fish or three big ones.
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.translatable(
                "message.riverfishing.pond_grew", net.minecraft.network.chat.Component.translatable("fish.riverfishing." + species),
                adults, com.riverfishing.item.FishItem.weightText(avg),
                pd.stockPercent(chunk, species, now)).withStyle(net.minecraft.ChatFormatting.AQUA);
        for (net.minecraft.server.level.ServerPlayer sp : level.players()) {
            if (sp.blockPosition().closerThan(pos, 64)) sp.displayClientMessage(msg, true);
        }
    }''')

print("p_lm: ok (%s)" % DIALECT)
