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

    /** The old six-argument call: a fish whose weight the caller never had. §lm */
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
        String side = sex == 1 || (sex < 0 && t.getInt("M") < t.getInt("F")) ? "M" : "F";
        t.putInt(side, t.getInt(side) + 1);
        tally(t, genome);   // §h
        stamp(t, day, genome, owner);
    }

    public void addFry(long region, String species, int count, long day, String genome, java.util.UUID owner) {
        CompoundTag t = entry(region, species);
        // §fry-clock: the batch's day, stamped when a water goes from no fry to some. Fry are fry for
        // FRY_DAYS and then they are fish — the spawn window they were tied to comes round once a YEAR,
        // so a bucket of fry sat in the ledger for ninety-six days and looked like a bug, because it was.
        if (t.getInt("Fry") <= 0) t.putLong("FryDay", day);
        t.putInt("Fry", t.getInt("Fry") + Math.max(0, count));
        tally(t, genome);   // §h: a fry stack is one spawn's worth — two alleles, like one fish
        stamp(t, day, genome, owner);
    }

    /**
     * §pattern: the pattern index this water's line runs at, or {@link com.riverfishing.fish.Pattern#NONE}
     * when nobody has stocked one. A fish landed out of the water inherits it, so a family bred in a tank
     * and released keeps coming back out of the pond.
     */
    public int pattern(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return com.riverfishing.fish.Pattern.NONE;
        return t.contains("Pattern") ? t.getInt("Pattern") : com.riverfishing.fish.Pattern.NONE;
    }

    /**
     * §pattern: what was just released moves the water's line HALFWAY toward its own index, rather than
     * overwriting it — a pond is the fish in it, so one gem carp dropped into a stocked lake shifts the
     * line without becoming it. The first fish sets it outright, because there is nothing to average.
     */
    public void setPattern(long region, String species, int pattern) {
        if (!com.riverfishing.fish.Pattern.has(pattern)) return;
        CompoundTag t = entry(region, species);
        int have = t.contains("Pattern") ? t.getInt("Pattern") : com.riverfishing.fish.Pattern.NONE;
        t.putInt("Pattern", com.riverfishing.fish.Pattern.has(have) ? (have + pattern) / 2 : pattern);
        setDirty();
    }

    /** The clock starts the first day the condition holds; a brood that merely grew keeps its date. */
    private void stamp(CompoundTag t, long day, String genome, java.util.UUID owner) {
        if (genome != null && !genome.isEmpty()) {
            t.putString("Genome", genome);
            roster(t, genome);   // §brood-pool
        }
        if (owner != null) t.putString("Owner", owner.toString());
        if (t.getLong("Since") <= 0 && ready(t)) t.putLong("Since", Math.max(1, day));
        setDirty();
    }

    /**
     * §brood-pool: how many released genomes a water remembers. Small on purpose — this is the brood
     * standing in the pond, not its whole history, and a roster long enough to hold every fish ever
     * released would drift back toward the average it exists to avoid. The oldest drops out, so a
     * water that has been restocked is the fish that are in it now.
     */
    private static final int ROSTER = 12;

    /** §brood-pool: remember this genome as one of the fish that is actually in this water. */
    private static void roster(CompoundTag t, String genome) {
        ListTag list = t.getList("Pool", Tag.TAG_STRING);
        list.add(net.minecraft.nbt.StringTag.valueOf(genome));
        while (list.size() > ROSTER) list.remove(0);
        t.put("Pool", list);
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
    /** Half a season: long enough to be a stage, short enough to watch. */
    public static final int FRY_DAYS = 12;


    /**
     * §fry-clock: fry that have had their twelve days become fish, wherever they are — the maturing
     * used to live inside growIfDue, which returns early for water that has not settled yet, so the
     * fry a player was waiting on were exactly the ones the clock never reached.
     */
    public void matureIfDue(ServerLevel level, long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null || t.getInt("Fry") <= 0) return;
        long day = worldDay(level);
        long stamped = t.getLong("FryDay");
        if (stamped <= 0) { t.putLong("FryDay", day); setDirty(); return; }
        if (day - stamped < FRY_DAYS) return;
        t.remove("FryDay");
        int grown = matureFry(t);
        // §fry-bank: the fry banked nothing when they went in (FishingManager.releaseFry); the fish they
        // have just become are banked here, at the pond, half a unit each — §stock-units' mean fish.
        // Until this ran, the water held nothing a net could lift; now it holds these.
        BlockPos at = broodPos(region, species);
        if (grown > 0 && at != null) {
            FishingPressureData.get(level).addStock(new net.minecraft.world.level.ChunkPos(at).toLong(), species,
                    level.getGameTime(), grown * 0.5, FishingPressureData.FLOOR_TRANSPLANT);
        }
    }

    private int matureFry(CompoundTag t) {
        int fry = t.getInt("Fry");
        t.remove("Fry");
        if (fry <= 0) return 0;
        int mature = fry / 2;
        int adults = seedAdults(t) + mature;   // seeded before F/M take the new fish in
        t.putInt("F", t.getInt("F") + mature / 2);
        t.putInt("M", t.getInt("M") + mature - mature / 2);
        t.putInt("Adults", adults);
        setDirty();
        return mature;   // §fry-bank: how many fish the water just gained
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
        // §stocked-genes: EVERY locus, not the four common ones. The old comment said a population has
        // no average scale cover, which is true and beside the point — a population has a scale-gene
        // FREQUENCY, and without it nothing a player stocks about how a fish looks is written down at
        // all. Dom0..3 keep their meaning (and the global Tot with them, for shares()); the rest are
        // new counters with their own totals, because a locus only the carps carry is only counted on
        // the fish that carry it.
        String loci = com.riverfishing.fish.Genome.LOCI;
        int pairs = com.riverfishing.fish.Genome.pairs(genome);
        for (int i = 0; i < loci.length(); i++) {
            if (i >= pairs) break;                       // this fish does not carry that locus
            char l = loci.charAt(i);
            int dom = !com.riverfishing.fish.Genome.dominant(genome, l) ? 0
                    : com.riverfishing.fish.Genome.pure(genome, l) ? 2 : 1;
            t.putInt("Dom" + i, t.getInt("Dom" + i) + dom);
            t.putInt("Tot" + i, t.getInt("Tot" + i) + 2);
        }
        t.putInt("Tot", t.getInt("Tot") + 2);
    }

    /**
     * §brood-pool: the bred genome laid over the rolled one, locus by locus — the water's answer wins
     * where it has one, and a locus the brood does not carry keeps whatever the roll gave it. That is
     * what lets a pond of carp still hand out the size and vigour the water itself decides.
     */
    private static String lay(String bred, String rolled) {
        if (bred == null || bred.isEmpty()) return rolled;
        String[] mine = bred.split(" ");
        String[] had = rolled == null || rolled.isEmpty() ? new String[0] : rolled.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.max(mine.length, had.length); i++) {
            if (i > 0) out.append(' ');
            out.append(i < mine.length ? mine[i] : had[i]);
        }
        return out.toString();
    }

    /**
     * §stocked-genes: the rolled genome with the POOL's alleles laid over it — one independent draw
     * per allele at the frequency this water holds, which is what makes a stocked pond give back what
     * was stocked in it and in the proportions it was stocked. A locus nobody ever released here keeps
     * whatever the roll gave it, so unstocked water behaves exactly as it always did.
     */
    public String overlay(long region, String species, String rolled, java.util.Random rng) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return rolled;
        // §brood-pool: the water crosses two of the fish that are IN it. This is the whole difference
        // between "you get back what you put in" and a gene cloud: two founders make an F1, not an
        // equilibrium, and a recessive waits until two carriers meet instead of arriving on day one.
        ListTag pool = t.getList("Pool", Tag.TAG_STRING);
        if (!pool.isEmpty()) {
            // §founders: nothing has spawned here until the brood has lived through a window, which is
            // what settled means. Before that the fish in the water ARE the released ones, so a fish
            // taken out is one of them — 250 kohaku fry are 250 kohaku, not their F1 on day one.
            if (!isStocked(region, species)) {
                return lay(pool.getString(rng.nextInt(pool.size())), rolled);
            }
            String a = pool.getString(rng.nextInt(pool.size()));
            String b = pool.getString(rng.nextInt(pool.size()));
            String bred = com.riverfishing.fish.Genome.cross(a, b, rng);
            for (int i = 0; i < 8 && com.riverfishing.fish.Genome.lethal(bred); i++) {
                bred = com.riverfishing.fish.Genome.cross(a, b, rng);
            }
            return lay(bred, rolled);
        }
        String loci = com.riverfishing.fish.Genome.LOCI;
        String[] had = rolled == null || rolled.isEmpty() ? new String[0] : rolled.split(" ");
        StringBuilder out = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < loci.length(); i++) {
            char u = loci.charAt(i), l = Character.toLowerCase(u);
            int tot = t.getInt("Tot" + i);
            String pair;
            if (tot > 0) {
                double share = t.getInt("Dom" + i) / (double) tot;
                char a = rng.nextDouble() < share ? u : l, b = rng.nextDouble() < share ? u : l;
                if (a == l && b == u) { a = u; b = l; }   // dominant first, as it is written
                pair = "" + a + b;
                any = true;
            } else if (i < had.length) {
                pair = had[i];
            } else {
                // Never stocked and never rolled: the defaults Genome.pair() hands out for a string
                // that stops short — a scaled carp, and the recessive of everything else.
                pair = u == 'K' ? "KK" : u == 'N' ? "nn" : ("" + l + l);
            }
            if (i > 0) out.append(' ');
            out.append(pair);
        }
        return any ? out.toString() : rolled;
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
            char u = com.riverfishing.fish.Genome.COMMON_LOCI.charAt(i), l = Character.toLowerCase(u);
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
        if (f + m > 0) {
            t.putInt(f >= m ? "F" : "M", (f >= m ? f : m) - 1);
            t.putInt("Adults", Math.max(0, seedAdults(t) - 1));   // §lm: one fish out is one fish fewer
        } else t.putInt("Fry", Math.max(0, t.getInt("Fry") - 10));
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
        matureFry(t);   // §lm: literally — half of them, split ♀/♂, instead of the ledger just dropping Fry
        for (String k : new String[]{"Since", "Due"}) t.remove(k);
        setDirty();
        return true;
    }

    // ---- §k §breeding (0.9.0): the pond as a farm ----------------------------------------------
    // A settled species with a pair on the ledger grows by itself. §lm: every SEASON, not every year —
    // a window closes once in 96 days and a pond that only moved on that one day read as dead, which is
    // the complaint this answers. The chunk of the last release (Pos) banks 3 units per pair per season,
    // more for fertile stock and for a bank with cover and oxygen, and the fish in it put on weight.
    // LastGrow is the world day last paid, LastMat the window close last matured, so however many times
    // the water is touched each pays once. There is no world ticker: release, landing and a
    // once-a-minute player tick (ModEvents) all ask growIfDue.
    /** The last release spot — the chunk the growth lands in, and where "you are near your pond" is measured. */
    public void notePos(long region, String species, BlockPos pos) {
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

    /**
     * Days until the next growth tick, 1..24 — the farm view's "grows in". §lm: growth is per SEASON now,
     * so this counts to the next season boundary of the same world-day clock growIfDue runs on (not
     * {@link com.riverfishing.engine.Calendar#dayOfYear}, which Serene Seasons can move). {@code p} stays
     * in the signature for the callers, and because the window close it measures still matures the fry.
     */
    public static int daysToGrow(ServerLevel level, com.riverfishing.fish.FishProfile p) {
        long today = worldDay(level);
        return (int) (com.riverfishing.engine.Calendar.SEASON_DAYS
                - Math.floorMod(today, (long) com.riverfishing.engine.Calendar.SEASON_DAYS));
    }

    /** Every farm species in the region the position is in — the per-player tick's call. */
    public void growAround(ServerLevel level, BlockPos pos) {
        long region = region(pos);
        for (String s : farmSpecies(region)) { matureIfDue(level, region, s); growIfDue(level, region, s); }
    }

    public void growIfDue(ServerLevel level, long region, String species) {
        if (!isStocked(region, species)) return;
        CompoundTag t = brood.get(key(region, species));
        if (t == null || !t.contains("Pos")) return;
        com.riverfishing.fish.FishProfile p = com.riverfishing.fish.FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(species));
        if (p == null) return;
        long today = worldDay(level);
        // §fry-clock: maturing is its own clock now (matureIfDue), on its own days.
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
