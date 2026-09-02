package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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

    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now.
    private static final net.minecraft.world.level.saveddata.SavedDataType<StockedData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("riverfishing", "stocked"),
                    StockedData::new,
                    CompoundTag.CODEC.xmap(t -> StockedData.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);

    public static StockedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
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
        return level.getServer().overworld().getOverworldClockTime() / 24000L;
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
        String side = sex == 1 || (sex < 0 && t.getIntOr("M", 0) < t.getIntOr("F", 0)) ? "M" : "F";
        t.putInt(side, t.getIntOr(side, 0) + 1);
        stamp(t, day, genome, owner);
    }

    public void addFry(long region, String species, int count, long day, String genome, java.util.UUID owner) {
        CompoundTag t = entry(region, species);
        t.putInt("Fry", t.getIntOr("Fry", 0) + Math.max(0, count));
        stamp(t, day, genome, owner);
    }

    /** The clock starts the first day the condition holds; a brood that merely grew keeps its date. */
    private void stamp(CompoundTag t, long day, String genome, java.util.UUID owner) {
        if (genome != null && !genome.isEmpty()) t.putString("Genome", genome);
        if (owner != null) t.putString("Owner", owner.toString());
        if (t.getLongOr("Since", 0L) <= 0 && ready(t)) t.putLong("Since", Math.max(1, day));
        setDirty();
    }

    private static boolean ready(CompoundTag t) {
        return Math.min(t.getIntOr("F", 0), t.getIntOr("M", 0)) >= 1 || t.getIntOr("Fry", 0) >= FRY_TO_SETTLE;
    }

    /** Mature fish of one sex on the ledger: 0 ♀, 1 ♂. */
    public int broodCount(long region, String species, int sex) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getIntOr(sex == 1 ? "M" : "F", 0);
    }

    public int broodPairs(long region, String species) {
        return Math.min(broodCount(region, species, 0), broodCount(region, species, 1));
    }

    public int fryCount(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getIntOr("Fry", 0);
    }

    /** Anything at all on the ledger — the landing hook's cheap first question. */
    public boolean hasBrood(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t != null && (t.getIntOr("F", 0) + t.getIntOr("M", 0) + t.getIntOr("Fry", 0)) > 0;
    }

    /** Net up to {@code n} fry; returns how many actually came out. */
    public int takeFry(long region, String species, int n) {
        int take = Math.min(n, fryCount(region, species));
        if (take > 0) {
            CompoundTag t = entry(region, species);
            t.putInt("Fry", t.getIntOr("Fry", 0) - take);
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
            if (e.getKey().startsWith(prefix) && e.getValue().getIntOr("Fry", 0) > most) {
                most = e.getValue().getIntOr("Fry", 0);
                best = e.getKey().substring(prefix.length());
            }
        }
        return best;
    }

    /** The population's genome — the last brood or fry released; "" when nobody stocked it. */
    public String genome(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? "" : t.getStringOr("Genome", "");
    }

    /** Who stocked this water, or null: natives and seed communities have no owner. */
    public java.util.UUID owner(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        String s = t == null ? "" : t.getStringOr("Owner", "");
        return s.isEmpty() ? null : java.util.UUID.fromString(s);
    }

    /** The water's fit for the species as the release measured it; the best of several releases counts. */
    public void noteFit(long region, String species, double fit) {
        CompoundTag t = entry(region, species);
        if (fit > t.getDoubleOr("Fit", 0.0)) {
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
        int f = t.getIntOr("F", 0), m = t.getIntOr("M", 0);
        if (f + m > 0) t.putInt(f >= m ? "F" : "M", (f >= m ? f : m) - 1);
        else t.putInt("Fry", Math.max(0, t.getIntOr("Fry", 0) - 10));
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
        return t == null || t.getLongOr("Due", 0L) <= 0 ? -1 : (int) Math.max(0, t.getLongOr("Due", 0L) - today);
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
        if (t == null || !ready(t) || t.getDoubleOr("Fit", 0.0) < FIT_TO_SETTLE) return false;
        long today = worldDay(level);
        if (t.getLongOr("Due", 0L) <= 0) {
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
        if (today < t.getLongOr("Due", 0L)) return false;
        markStocked(region, species);
        // The brood IS the population now: pairs and fry grew into it. Genome and owner stay — the
        // nets ask who stocked a water long after it settled.
        for (String k : new String[]{"F", "M", "Fry", "Since", "Due"}) t.remove(k);
        setDirty();
        return true;
    }

    private CompoundTag saveBrood() {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : brood.entrySet()) out.put(e.getKey(), e.getValue().copy());
        return out;
    }

    private void loadBrood(CompoundTag in) {
        for (String k : in.keySet()) brood.put(k, in.getCompoundOrEmpty(k).copy());
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

    // §26.1: species stored as a compound of boolean keys (the ListTag string API changed;
    // no pre-0.5.0 saves exist on this branch, so the format is free to differ).
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<Long, Set<String>> e : regions.entrySet()) {
            CompoundTag set = new CompoundTag();
            for (String s : e.getValue()) set.putBoolean(s, true);
            all.put(Long.toString(e.getKey()), set);
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

    public static StockedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StockedData d = new StockedData();
        CompoundTag all = tag.getCompoundOrEmpty("Regions");
        for (String key : all.keySet()) {
            d.regions.put(Long.parseLong(key), new HashSet<>(all.getCompoundOrEmpty(key).keySet()));
        }
        CompoundTag out = tag.getCompoundOrEmpty("Culled");
        for (String key : out.keySet()) {
            ListTag list = out.getListOrEmpty(key);
            Set<String> s = new HashSet<>();
            for (int i = 0; i < list.size(); i++) s.add(list.getStringOr(i, ""));
            d.culled.put(Long.parseLong(key), s);
        }
        d.loadBrood(tag.getCompoundOrEmpty("Brood"));   // §c
        return d;
    }
}
