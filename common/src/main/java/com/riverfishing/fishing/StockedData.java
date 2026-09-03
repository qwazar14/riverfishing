package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * §stocking (0.5.0): the book of released fish. A caught fish thrown back into water joins that
 * water's community (§community) for good — the way a server stocks a pond with a species the
 * seed didn't put there. Keyed by the same ~128-block water region the community hash uses.
 */
public final class StockedData extends SavedData {
    private static final String NAME = "riverfishing_stocked";

    private final Map<Long, Set<String>> regions = new HashMap<>();
    /**
     * §cull (0.7.0): species an operator has removed from a region with the electrofisher. Kept beside the
     * stocking book rather than in a file of its own because they are the same statement about the same
     * region — one says "this lives here now", the other "this does not live here any more" — and a second
     * SavedData keyed the same way would be a second thing to keep in step.
     */
    private final Map<Long, Set<String>> culled = new HashMap<>();

    /** The ~128-block community region a position belongs to (shared with §community's hash). */
    public static long region(BlockPos pos) {
        return (((long) (pos.getX() >> 7)) << 32) ^ ((pos.getZ() >> 7) & 0xFFFFFFFFL);
    }

    public static StockedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(StockedData::load, StockedData::new, NAME);
    }

    public boolean isStocked(long region, String species) {
        Set<String> s = regions.get(region);
        return s != null && s.contains(species);
    }

    public void markStocked(long region, String species) {
        regions.computeIfAbsent(region, k -> new HashSet<>()).add(species);
        // A fish you physically put back is in the water, whatever the book said before: releasing one
        // lifts a cull. That keeps the world honest rather than leaving a species that visibly swims here
        // and cannot be caught, and it gives the operator an in-world undo.
        Set<String> c = culled.get(region);
        if (c != null) c.remove(species);
        setDirty();
    }


    // ---- §c §breeding (0.9.0): the brood ledger ------------------------------------------------
    // Settling is no longer a dice roll: a species belongs to a water once a BROOD has lived through
    // one full spawn window there. One compound per region+species, kept as raw NBT because every
    // field is a counter or a string and save/load is then a copy:
    //   F, M     mature ♀ / ♂ released (Card.Size ≥ 2)        Fry    fry released, still fry
    //   Since    world day the settle condition first held    Due    day one full window has passed (0 = unpriced)
    //   Fit      best habitat fit any release measured        Genome last brood/fry genome (ponytail: last writer wins)
    //   Owner    UUID of the releasing player — the nets ask it (§poaching)
    private final Map<String, CompoundTag> brood = new HashMap<>();
    /** Fry short of this settle nothing: a couple of pairs' worth of a real spawn. */
    public static final int FRY_TO_SETTLE = 30;
    /** Water the species cannot really live in fills the ledger but never settles. */
    public static final double FIT_TO_SETTLE = 0.5;

    public static long worldDay(ServerLevel level) {
        return level.getServer().overworld().getDayTime() / 24000L;
    }

    private static String key(long region, String species) {
        return region + "|" + species;
    }

    private CompoundTag entry(long region, String species) {
        return brood.computeIfAbsent(key(region, species), k -> new CompoundTag());
    }

    /** sex: 0 ♀, 1 ♂ (Card.Sex), -1 unknown — an old or villager-bought fish has no card and fills whichever side the pair is missing. */
    public void addBrood(long region, String species, int sex, long day, String genome, java.util.UUID owner) {
        CompoundTag t = entry(region, species);
        String side = sex == 1 || (sex < 0 && t.getInt("M") < t.getInt("F")) ? "M" : "F";
        t.putInt(side, t.getInt(side) + 1);
        tally(t, genome);   // §h
        stamp(t, day, genome, owner);
    }

    public void addFry(long region, String species, int count, long day, String genome, java.util.UUID owner) {
        CompoundTag t = entry(region, species);
        t.putInt("Fry", t.getInt("Fry") + Math.max(0, count));
        tally(t, genome);   // §h: a fry stack is one spawn's worth — two alleles, like one fish
        stamp(t, day, genome, owner);
    }

    /** The clock starts the first day the condition holds; a brood that merely grew keeps its date. */
    private void stamp(CompoundTag t, long day, String genome, java.util.UUID owner) {
        if (genome != null && !genome.isEmpty()) t.putString("Genome", genome);
        if (owner != null) t.putString("Owner", owner.toString());
        if (t.getLong("Since") <= 0 && ready(t)) t.putLong("Since", Math.max(1, day));
        setDirty();
    }

    private static boolean ready(CompoundTag t) {
        return Math.min(t.getInt("F"), t.getInt("M")) >= 1 || t.getInt("Fry") >= FRY_TO_SETTLE;
    }

    /** Mature fish of one sex on the ledger: 0 ♀, 1 ♂. */
    public int broodCount(long region, String species, int sex) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getInt(sex == 1 ? "M" : "F");
    }

    public int broodPairs(long region, String species) {
        return Math.min(broodCount(region, species, 0), broodCount(region, species, 1));
    }

    public int fryCount(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getInt("Fry");
    }

    /** Anything at all on the ledger — the landing hook's cheap first question. */
    public boolean hasBrood(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t != null && (t.getInt("F") + t.getInt("M") + t.getInt("Fry")) > 0;
    }

    /** Net up to {@code n} fry; returns how many actually came out. */
    public int takeFry(long region, String species, int n) {
        int take = Math.min(n, fryCount(region, species));
        if (take > 0) {
            CompoundTag t = entry(region, species);
            t.putInt("Fry", t.getInt("Fry") - take);
            setDirty();
        }
        return take;
    }

    /** The species with the most fry in a region, or null — what a fry net pulls up first. */
    public String richestFry(long region) {
        String best = null;
        int most = 0;
        String prefix = region + "|";
        for (Map.Entry<String, CompoundTag> e : brood.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue().getInt("Fry") > most) {
                most = e.getValue().getInt("Fry");
                best = e.getKey().substring(prefix.length());
            }
        }
        return best;
    }

    // ---- §h §breeding (0.9.0): the population's genome is an AVERAGE, not the last writer -----------
    // Dom0..Dom3 count the strong alleles per locus (S C V F) over every brood fish and fry stack ever
    // recorded, Tot the alleles counted (two each). They live in the entry compound so save/load carry
    // them for free, and tickSettle leaves them: a settled water remembers what it was stocked with.
    private static void tally(CompoundTag t, String genome) {
        if (genome == null || genome.isEmpty()) return;
        for (int i = 0; i < com.riverfishing.fish.Genome.LOCI.length(); i++) {
            char l = com.riverfishing.fish.Genome.LOCI.charAt(i);
            int dom = !com.riverfishing.fish.Genome.dominant(genome, l) ? 0
                    : com.riverfishing.fish.Genome.pure(genome, l) ? 2 : 1;
            t.putInt("Dom" + i, t.getInt("Dom" + i) + dom);
        }
        t.putInt("Tot", t.getInt("Tot") + 2);
    }

    /** Share of strong alleles per locus (S C V F), 0..1; all 0 where nothing was ever recorded. */
    public double[] shares(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        double[] out = new double[4];
        int tot = t == null ? 0 : t.getInt("Tot");
        if (tot > 0) for (int i = 0; i < 4; i++) out[i] = t.getInt("Dom" + i) / (double) tot;
        return out;
    }

    /**
     * The population's genome, averaged: a locus is SS at a strong-allele share of 2/3 or more, Ss at
     * 1/3, ss below. "" when nobody stocked it; a ledger from before the tally (Tot 0) still answers
     * with the last genome it wrote, so old worlds keep their string.
     */
    public String genome(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return "";
        if (t.getInt("Tot") <= 0) return t.getString("Genome");
        double[] s = shares(region, species);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            char u = com.riverfishing.fish.Genome.LOCI.charAt(i), l = Character.toLowerCase(u);
            if (i > 0) out.append(' ');
            out.append(s[i] >= 0.66 ? u : l).append(s[i] >= 0.33 ? u : l);
        }
        return out.toString();
    }

    /** Every species with a genome in a region, species → string: what the journal shows for "here". */
    public CompoundTag genomes(long region) {
        CompoundTag out = new CompoundTag();
        String prefix = region + "|";
        for (String k : brood.keySet()) {
            if (!k.startsWith(prefix)) continue;
            String g = genome(region, k.substring(prefix.length()));
            if (!g.isEmpty()) out.putString(k.substring(prefix.length()), g);
        }
        return out;
    }

    /** Who stocked this water, or null: natives and seed communities have no owner. */
    public java.util.UUID owner(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        String s = t == null ? "" : t.getString("Owner");
        return s.isEmpty() ? null : java.util.UUID.fromString(s);
    }

    /** The water's fit for the species as the release measured it; the best of several releases counts. */
    public void noteFit(long region, String species, double fit) {
        CompoundTag t = entry(region, species);
        if (fit > t.getDouble("Fit")) {
            t.putDouble("Fit", fit);
            setDirty();
        }
    }

    public void clearBrood(long region, String species) {
        if (brood.remove(key(region, species)) != null) setDirty();
    }

    /**
     * A landed fish of an unsettled species came out of the brood — it is the only population there is.
     * Adults go first, from the side with the surplus (a pair holds as long as it can), then fry ten
     * at a time. Returns true when the ledger just emptied; a brood that drops below the settle
     * condition loses its clock and starts over when it is complete again.
     */
    public boolean catchFromBrood(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return false;
        int f = t.getInt("F"), m = t.getInt("M");
        if (f + m > 0) t.putInt(f >= m ? "F" : "M", (f >= m ? f : m) - 1);
        else t.putInt("Fry", Math.max(0, t.getInt("Fry") - 10));
        if (!ready(t)) {
            t.remove("Since");
            t.remove("Due");
        }
        setDirty();
        if (hasBrood(region, species)) return false;
        clearBrood(region, species);
        return true;
    }

    /** Days until the brood settles as tickSettle last priced it; -1 when nothing is on the clock. */
    public int daysToSettle(long region, String species, long today) {
        CompoundTag t = brood.get(key(region, species));
        return t == null || t.getLong("Due") <= 0 ? -1 : (int) Math.max(0, t.getLong("Due") - today);
    }

    /**
     * The settle check, run at every release and every landing of the species in the region (there
     * is no world ticker; the water comes of age the next time somebody touches it). Settles when the
     * condition holds — a pair, or {@link #FRY_TO_SETTLE} fry — the water fits ({@link #FIT_TO_SETTLE})
     * and one FULL spawn window of the species has passed since the condition first held. Returns
     * true on the tick that settled it.
     */
    public boolean tickSettle(ServerLevel level, long region, String species, com.riverfishing.fish.FishProfile p) {
        if (isStocked(region, species)) return false;
        CompoundTag t = brood.get(key(region, species));
        if (t == null || !ready(t) || t.getDouble("Fit") < FIT_TO_SETTLE) return false;
        long today = worldDay(level);
        if (t.getLong("Due") <= 0) {
            // Priced ONCE, when the brood is complete: the next whole window, start to end. A window the
            // fish entered halfway does not count — a spawn is a season's work, not a weekend's — so
            // inside one the clock runs to the window after it.
            int len = p.spawnSub == null ? com.riverfishing.engine.Calendar.SEASON_DAYS : com.riverfishing.engine.Calendar.SUB_DAYS;
            long due;
            if (com.riverfishing.engine.Calendar.inWindow(level, p)) {
                // Calendar.daysUntil's arithmetic asked the other way round: how far INTO the window are we.
                int start = p.spawnSeason.ordinal() * com.riverfishing.engine.Calendar.SEASON_DAYS
                        + (p.spawnSub == null ? 0 : p.spawnSub.ordinal() * com.riverfishing.engine.Calendar.SUB_DAYS);
                int into = Math.floorMod(com.riverfishing.engine.Calendar.dayOfYear(level) - start, com.riverfishing.engine.Calendar.YEAR_DAYS);
                due = today + (into == 0 ? 0 : com.riverfishing.engine.Calendar.YEAR_DAYS - into) + len;
            } else {
                due = today + com.riverfishing.engine.Calendar.daysUntil(level, p.spawnSeason, p.spawnSub) + len;
            }
            t.putLong("Due", due);
            setDirty();
        }
        if (today < t.getLong("Due")) return false;
        markStocked(region, species);
        // The brood IS the population now: pairs and fry grew into it. Genome and owner stay — the
        // nets ask who stocked a water long after it settled.
        // §k §farm: the pairs stay — a settled pond's brood is what grows it (growIfDue); fry became fish.
        for (String k : new String[]{"Fry", "Since", "Due"}) t.remove(k);
        setDirty();
        return true;
    }

    // ---- §k §breeding (0.9.0): the pond as a farm ----------------------------------------------
    // A settled species with a pair on the ledger grows by itself once a year: every time its spawn
    // window CLOSES, the chunk of the last release (Pos) banks 3 units per pair, more for fertile stock
    // and for a bank with cover and oxygen. LastGrow is the world day of the last close paid out, so
    // however many times the water is touched, a window pays once. There is no world ticker: release,
    // landing and a once-a-minute player tick (ModEvents) all ask growIfDue.
    /** The last release spot — the chunk the growth lands in, and where "you are near your pond" is measured. */
    public void notePos(long region, String species, BlockPos pos) {
        entry(region, species).putLong("Pos", pos.asLong());
        setDirty();
    }

    /** Species on the farm here: settled, or with a brood in the making. */
    public Set<String> farmSpecies(long region) {
        Set<String> out = new HashSet<>();
        Set<String> s = regions.get(region);
        if (s != null) out.addAll(s);
        String prefix = region + "|";
        for (String k : brood.keySet()) if (k.startsWith(prefix)) out.add(k.substring(prefix.length()));
        return out;
    }

    /** Days since the species' window last closed, 0 on the closing day. Calendar arithmetic, like Due. */
    private static int sinceClose(ServerLevel level, com.riverfishing.fish.FishProfile p) {
        int start = p.spawnSeason.ordinal() * com.riverfishing.engine.Calendar.SEASON_DAYS
                + (p.spawnSub == null ? 0 : p.spawnSub.ordinal() * com.riverfishing.engine.Calendar.SUB_DAYS);
        int len = p.spawnSub == null ? com.riverfishing.engine.Calendar.SEASON_DAYS : com.riverfishing.engine.Calendar.SUB_DAYS;
        return Math.floorMod(com.riverfishing.engine.Calendar.dayOfYear(level) - (start + len), com.riverfishing.engine.Calendar.YEAR_DAYS);
    }

    /** Days until the window next closes, 1..96 — the farm view's "grows in". */
    public static int daysToGrow(ServerLevel level, com.riverfishing.fish.FishProfile p) {
        return com.riverfishing.engine.Calendar.YEAR_DAYS - sinceClose(level, p);
    }

    /** Every farm species in the region the position is in — the per-player tick's call. */
    public void growAround(ServerLevel level, BlockPos pos) {
        long region = region(pos);
        for (String s : farmSpecies(region)) growIfDue(level, region, s);
    }

    public void growIfDue(ServerLevel level, long region, String species) {
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
    }

    private CompoundTag saveBrood() {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : brood.entrySet()) out.put(e.getKey(), e.getValue().copy());
        return out;
    }

    private void loadBrood(CompoundTag in) {
        for (String k : in.getAllKeys()) brood.put(k, in.getCompound(k).copy());
    }

    /** §cull: is this species banned from this region? */
    public boolean isCulled(long region, String species) {
        Set<String> s = culled.get(region);
        return s != null && s.contains(species);
    }

    public void setCulled(long region, String species, boolean on) {
        if (on) {
            culled.computeIfAbsent(region, k -> new HashSet<>()).add(species);
            Set<String> stockedHere = regions.get(region);
            if (stockedHere != null) stockedHere.remove(species);   // it is not stocked here any more either
        } else {
            Set<String> s = culled.get(region);
            if (s != null) s.remove(species);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<Long, Set<String>> e : regions.entrySet()) {
            ListTag list = new ListTag();
            for (String s : e.getValue()) list.add(StringTag.valueOf(s));
            all.put(Long.toString(e.getKey()), list);
        }
        tag.put("Regions", all);
        CompoundTag out = new CompoundTag();
        for (Map.Entry<Long, Set<String>> e : culled.entrySet()) {
            ListTag list = new ListTag();
            for (String s : e.getValue()) list.add(StringTag.valueOf(s));
            out.put(Long.toString(e.getKey()), list);
        }
        tag.put("Culled", out);
        tag.put("Brood", saveBrood());   // §c
        return tag;
    }

    public static StockedData load(CompoundTag tag) {
        StockedData d = new StockedData();
        CompoundTag all = tag.getCompound("Regions");
        for (String key : all.getAllKeys()) {
            ListTag list = all.getList(key, Tag.TAG_STRING);
            Set<String> s = new HashSet<>();
            for (int i = 0; i < list.size(); i++) s.add(list.getString(i));
            d.regions.put(Long.parseLong(key), s);
        }
        CompoundTag out = tag.getCompound("Culled");
        for (String key : out.getAllKeys()) {
            ListTag list = out.getList(key, Tag.TAG_STRING);
            Set<String> s = new HashSet<>();
            for (int i = 0; i < list.size(); i++) s.add(list.getString(i));
            d.culled.put(Long.parseLong(key), s);
        }
        d.loadBrood(tag.getCompound("Brood"));   // §c
        return d;
    }
}
