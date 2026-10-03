package com.riverfishing.fishing;

import com.riverfishing.alife.Lake;
import com.riverfishing.alife.Life;
import com.riverfishing.alife.Species;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fish.Genome;
import com.riverfishing.item.FishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * §alife-pond: a player's pond as a living water that remembers every fish in it.
 *
 * <p>The pond is its own {@link Lake}, keyed by the pond's ledger key, one zone per chunk of its claimed
 * columns with the real volume of water in it. Nothing is seeded: a pond holds the fish that were put in and
 * the ones born there ({@link Life}). Every fish is a {@link Life.Head} whose tag is the record the old roster
 * kept — {@code Card} (genes, sex, nature, pattern, variety), {@code Morph}, {@code Name} — so the fish that
 * comes out, by rod, net or trap, is the fish that went in, trophy and legend and all.
 *
 * <p>The first time a pond is opened this way, the old ledger is read into heads ({@link #migrate}): every
 * remembered fish as itself, and every head the old count had beyond the records as a fish of the pond's
 * genes, sex from its F/M count, weight from its average. Players have bred these ponds for a long time; the
 * old book is left untouched on disk and simply not read again.
 */
public final class PondLife {
    private PondLife() {}

    /** A cast, a net or a thrown fish this close to a claimed column is in the pond (the claim is frozen at the sign). */
    private static final int NEAR = 2;

    /** The claim at or right beside a spot, or null for wild water. */
    public static PondData.Claim claimNear(ServerLevel level, BlockPos pos) {
        PondData.Claim c = PondData.claim(level, pos);
        for (int dx = -NEAR; c == null && dx <= NEAR; dx++) {
            for (int dz = -NEAR; c == null && dz <= NEAR; dz++) c = PondData.claim(level, pos.offset(dx, 0, dz));
        }
        return c;
    }

    /** The living pond at a spot, brought up to now — null when this is no pond, A-Life is off, or it is not loaded. */
    public static Lake lake(ServerLevel level, BlockPos pos) {
        if (!com.riverfishing.config.RiverFishingConfig.alife()) return null;
        PondData.Claim c = claimNear(level, pos);
        return c == null ? null : AlifeData.get(level).pond(level, c);
    }

    // ---- the fish, in and out ----

    /** A fish thrown into the pond: its own head, every badge kept. False when this is not a living pond. */
    public static boolean release(ServerLevel level, BlockPos pos, FishProfile p, int weightG, int count,
                                  CompoundTag card, ItemStack stack) {
        Lake lake = lake(level, pos);
        return lake != null && into(lake, level, pos, p, weightG, count, card, stack, null);
    }

    /**
     * §alife-wild: a fish put back into wild water, after the bank has judged the water. It swims there as
     * itself — a trophy stays a trophy for whoever meets it — and it remembers who put it in, which is what
     * makes a net on it legal for that angler and poaching for anyone else.
     */
    public static boolean releaseWild(ServerLevel level, BlockPos pos, FishProfile p, int weightG, int count,
                                      CompoundTag card, ItemStack stack, java.util.UUID owner) {
        if (!com.riverfishing.config.RiverFishingConfig.alife()) return false;
        Lake lake = AlifeData.get(level).lakeAt(level, pos);
        if (lake.zones.isEmpty()) return false;
        AlifeData.admit(lake, p);
        return into(lake, level, pos, p, weightG, count, card, stack, owner);
    }

    private static boolean into(Lake lake, ServerLevel level, BlockPos pos, FishProfile p, int weightG, int count,
                                CompoundTag card, ItemStack stack, java.util.UUID owner) {
        Random rng = new Random(level.getRandom().nextLong());
        CompoundTag tags = stack == null ? new CompoundTag() : com.riverfishing.item.StackNbt.get(stack);
        for (int i = 0; i < Math.max(1, count); i++) {
            CompoundTag rec = stack == null ? new CompoundTag()
                    : FishingManager.pondRecord(stack, card, weightG, StockedData.worldDay(level));
            CompoundTag c = rec.getCompoundOrEmpty("Card");
            int sex = c.contains("Sex") ? c.getByteOr("Sex", (byte) 0) : rng.nextInt(2);
            c.putByte("Sex", (byte) sex);
            rec.put("Card", c);
            if (owner != null) rec.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
            Life.Head h = new Life.Head(rng.nextLong(), sex, weightG, lake.hour);
            h.trophy = tags.getBooleanOr(FishItem.TAG_TROPHY, false);
            h.legend = tags.getBooleanOr(FishItem.TAG_LEGEND, false);
            h.returned = true;
            h.tag = rec;
            Life.join(lake, AlifeData.species(p), h, AlifeData.zoneAt(lake, pos));
        }
        return true;
    }

    /** Fry thrown into the pond: a batch that becomes fish in its own time, as many as there is room for. */
    public static boolean releaseFry(ServerLevel level, BlockPos pos, FishProfile p, String genome, int count, int pattern) {
        Lake lake = lake(level, pos);
        return lake != null && fryInto(lake, level, pos, p, genome, count, pattern);
    }

    /** §alife-wild: fry into wild water, after the bank has judged it: a batch the river's own predators thin. */
    public static boolean releaseFryWild(ServerLevel level, BlockPos pos, FishProfile p, String genome, int count, int pattern) {
        if (!com.riverfishing.config.RiverFishingConfig.alife()) return false;
        Lake lake = AlifeData.get(level).lakeAt(level, pos);
        if (lake.zones.isEmpty()) return false;
        AlifeData.admit(lake, p);
        return fryInto(lake, level, pos, p, genome, count, pattern);
    }

    private static boolean fryInto(Lake lake, ServerLevel level, BlockPos pos, FishProfile p, String genome, int count, int pattern) {
        Life.Head parent = synth(0, genome, pattern, p.weightMean, lake.hour, level.getRandom().nextLong());
        Lake.Agent fry = new Lake.Agent(AlifeData.species(p), count, 0.5, AlifeData.zoneAt(lake, pos), false);
        fry.fry = true;
        fry.bornHour = lake.hour;
        fry.mother = parent;
        fry.father = parent;   // the same fish twice: the batch keeps the bucket's genes rather than re-crossing them
        lake.agents.add(fry);
        lake.touch();
        return true;
    }

    /** Who put this fish into the water, or null for a fish born there or grown from the seed. */
    public static java.util.UUID owner(Life.Head h) {
        CompoundTag r = h.tag instanceof CompoundTag t ? t : null;
        return r == null ? null : r.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    /** §alife-wild: how many of this species swim in the water — shoals and remembered fish together. */
    public static int count(Lake lake, String species) {
        int n = 0;
        for (Lake.Agent a : lake.agents) if (!a.fry && a.sp.id().equals(species)) n += a.count;
        return n;
    }

    /** §alife-wild: the old wild book of a region, read into its living water — as for a pond, minus what a pond has adopted. */
    static void migrateWild(ServerLevel level, Lake lake, long region) {
        migrate(level, lake, region, true);
    }

    /** The record a caught head hands the fish item (a copy — the pond keeps nothing of a fish that left). */
    public static CompoundTag record(Life.Head h) {
        return h.tag instanceof CompoundTag t ? t.copy() : new CompoundTag();
    }

    public static String genes(Life.Head h) {
        return h == null ? "" : record(h).getCompoundOrEmpty("Card").getStringOr("Genes", "");
    }

    // ---- what the sign, the finder and the ecosystem read ----

    /**
     * Per species: grown fish, fry (and eggs), females, males, and how many of the grown are still young.
     * §census-sex: the sexes count EVERY grown fish. Counting only the mature ones showed a pond of fish grown
     * from bought fry as grown fish with no sex (♀0 ♂0) for the whole season before they mature — the sex is
     * decided at the hatch, and the finder should say it.
     */
    public static Map<String, int[]> census(Lake lake) {
        Map<String, int[]> out = new TreeMap<>();
        for (Lake.Agent a : lake.agents) {
            int[] n = out.computeIfAbsent(a.sp.id(), k -> new int[5]);
            if (a.fry) { n[1] += a.count; continue; }
            if (a.heads == null) continue;
            n[0] += a.heads.size();
            for (Life.Head h : a.heads) {
                n[2 + Math.min(1, Math.max(0, h.sex))]++;
                if (!h.mature(a.sp)) n[4]++;
            }
        }
        for (Life.Roe r : lake.roe) out.computeIfAbsent(r.sp.id(), k -> new int[5])[1] += (int) r.eggs;
        out.values().removeIf(n -> n[0] + n[1] == 0);
        return out;
    }

    /** §fry-stack: up to 32 fry out of the pond's biggest batch — the trap's catch, one stack — or nothing. */
    public static final int NET_FRY = 32;

    public static ItemStack netFry(Lake lake) {
        Lake.Agent best = null;
        for (Lake.Agent a : lake.agents) if (a.fry && a.alive() && (best == null || a.count > best.count)) best = a;
        if (best == null) return ItemStack.EMPTY;
        int n = Math.min(NET_FRY, best.count);
        best.count -= n;
        if (best.count <= 0) lake.agents.remove(best);
        lake.touch();
        return com.riverfishing.item.FryItem.of(com.riverfishing.RiverFishing.id(best.sp.id()), genes(best.mother), n);
    }

    public static boolean holds(Lake lake, String species) {
        for (Lake.Agent a : lake.agents) if (!a.fry && a.heads != null && !a.heads.isEmpty() && a.sp.id().equals(species)) return true;
        return false;
    }

    /** How full the pond is, 0..100+ %: its fish against the room its water has. */
    public static int fullness(Lake lake) {
        double room = Life.headRoom(lake);
        return room <= 0 ? 0 : (int) Math.round(100 * Life.heads(lake) / room);
    }

    // ---- building a pond ----

    /** Zones from the claim's own columns, the upgrade blocks' effect on them, and — once — the old book. */
    static Lake build(ServerLevel level, PondData.Claim claim, long key, double hour) {
        Map<Long, List<long[]>> byChunk = new HashMap<>();   // chunk → {x, z, depth, bed, plant}
        for (long col : claim.water) {
            BlockPos c = PondData.columnPos(col);
            if (!level.hasChunk(c.getX() >> 4, c.getZ() >> 4)) return null;   // not all of it loaded: ask again later
            BlockPos p = new BlockPos(c.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ()) - 1, c.getZ());
            boolean plant = false;
            if (level.getBlockState(p).is(Blocks.LILY_PAD)) { plant = true; p = p.below(); }
            if (!level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) continue;
            int d = FishingManager.measureDepth(level, p);
            if (!level.getBlockState(p.below(d - 1)).is(Blocks.WATER)) plant = true;
            byChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.pack(c.getX() >> 4, c.getZ() >> 4), k -> new ArrayList<>())
                    .add(new long[]{c.getX(), c.getZ(), d, FishingManager.bedType(level, p), plant ? 1 : 0, p.getY()});
        }
        if (byChunk.isEmpty()) return null;
        Lake lake = new Lake(level.getSeed() ^ key, hour);
        lake.touch();
        lake.catchUpH = 24 * com.riverfishing.engine.Calendar.YEAR_DAYS;
        lake.nursery = NURSERY;
        lake.pond = true;
        for (List<long[]> cols : byChunk.values()) {
            double x = 0, z = 0, depth = 0, plants = 0;
            int[] beds = new int[16];
            for (long[] c : cols) { x += c[0]; z += c[1]; depth += c[2]; plants += c[4]; beds[(int) Math.min(15, Math.max(0, c[3]))]++; }
            int bed = 0;
            for (int b = 1; b < beds.length; b++) if (beds[b] > beds[bed]) bed = b;
            Lake.Zone zone = new Lake.Zone("p" + lake.zones.size(), x / cols.size() + 0.5, z / cols.size() + 0.5,
                    depth / cols.size(), bed, plants / cols.size());
            zone.volume = depth;
            long[] deepest = cols.get(0);
            for (long[] c : cols) if (c[2] > deepest[2]) deepest = c;
            AlifeData.anchor(zone, new BlockPos((int) deepest[0], (int) deepest[5], (int) deepest[1]), (int) deepest[2]);
            lake.zones.add(zone);
        }
        upgrades(level, lake);
        migrate(level, lake, key, false);
        return lake;
    }

    /**
     * The water upgrade blocks, re-read every time the pond is asked: snags hide fry and make room, a
     * feeding station and an aerator let the water carry more.
     */
    static void upgrades(ServerLevel level, Lake lake) {
        for (Lake.Zone z : lake.zones) {
            // at the pond's own surface: a hillside or a cave pond is not at sea level, and WaterUpgrades only
            // looks six blocks up and down
            java.util.Set<String> up = WaterUpgrades.at(level, BlockPos.containing(z.x, z.ay == Integer.MIN_VALUE ? 62 : z.ay, z.z));
            z.richness = 1.0 + (up.contains("feeding_station") ? 0.5 : 0) + (up.contains("aerator") ? 0.25 : 0);
            z.cover = Math.min(1.0, z.baseCover + (up.contains("snags") ? 0.4 : 0));
        }
    }

    /** The old book, read into heads — as much of it as it knows, nothing thrown away. */
    private static void migrate(ServerLevel level, Lake lake, long key, boolean wild) {
        StockedData st = StockedData.get(level);
        Random rng = new Random(level.getSeed() ^ key ^ 0x5DEECE66DL);
        for (String s : st.farmSpecies(key)) {
            if (st.isCulled(key, s)) continue;
            BlockPos at = st.broodPos(key, s);
            if (wild && at != null && PondData.isClaimed(level, at)) continue;   // the pond next door took this brood
            java.util.UUID owner = st.owner(key, s);
            FishProfile p = FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(s));
            if (p == null) continue;
            Species sp = AlifeData.species(p);
            int zone = zoneOf(lake, st.broodPos(key, s));
            List<CompoundTag> recs = st.fishRecords(key, s);
            int females = st.broodCount(key, s, 0), males = st.broodCount(key, s, 1);
            for (CompoundTag rec : recs) {
                CompoundTag card = rec.getCompoundOrEmpty("Card");
                int sex = card.contains("Sex") ? card.getByteOr("Sex", (byte) 0) : (females > males ? 0 : 1);
                if (sex == 0) females--; else males--;
                card.putByte("Sex", (byte) sex);
                rec.put("Card", card);
                int w = st.grownWeight(level, key, s, p, rec);
                Life.Head h = new Life.Head(rec.contains("Uid") ? rec.getLongOr("Uid", 0L) : rng.nextLong(), sex, w, lake.hour);
                h.trophy = card.getByteOr("Size", (byte) 0) >= 4 || w >= FishItem.trophyThresholdG(p.weightMin, p.weightMax);
                h.returned = true;
                if (owner != null && rec.read("Owner", net.minecraft.core.UUIDUtil.CODEC).isEmpty()) rec.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
                h.tag = rec;
                Life.join(lake, sp, h, zone);
            }
            // the heads the old count had and no record did — what the sign showed, so what the player owns
            int extra = Math.max(0, st.adults(key, s) - recs.size());
            if (extra == 0 && recs.isEmpty() && st.fryCount(key, s) == 0 && st.isStocked(key, s)) extra = 2;   // a book with no count: a pair
            double w = st.avgWeight(key, s) > 0 ? st.avgWeight(key, s) : p.weightMean;
            String genes = st.genome(key, s);
            int pattern = st.pattern(key, s);
            for (int i = 0; i < extra; i++) {
                int sex = females > 0 ? 0 : males > 0 ? 1 : rng.nextInt(2);
                if (sex == 0) females--; else males--;
                Life.Head h = synth(sex, genes, pattern, w, lake.hour, rng.nextLong());
                if (owner != null) ((CompoundTag) h.tag).store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
                Life.join(lake, sp, h, zone);
            }
            int fry = st.fryCount(key, s);
            if (fry > 0) {
                Life.Head parent = synth(0, genes, pattern, p.weightMean, lake.hour, rng.nextLong());
                Lake.Agent batch = new Lake.Agent(sp, fry, 0.5, zone, false);
                batch.fry = true;
                batch.bornHour = lake.hour;
                batch.mother = parent;
                batch.father = parent;
                lake.agents.add(batch);
                lake.touch();   // §alife-save: a book of nothing but fry is still remembered water (it was evicted as untouched)
            }
        }
    }

    private static int zoneOf(Lake lake, BlockPos pos) {
        return pos == null || lake.zones.isEmpty() ? 0 : AlifeData.zoneAt(lake, pos);
    }

    /** A fish the pond knows only by its genes, sex and weight — the old count's heads, a fry bucket's parents. */
    static Life.Head synth(int sex, String genes, int pattern, double weightG, double hour, long uid) {
        Life.Head h = new Life.Head(uid, sex, weightG, hour);
        CompoundTag card = new CompoundTag();
        if (!genes.isEmpty()) card.putString("Genes", genes);
        card.putByte("Sex", (byte) sex);
        card.putInt(com.riverfishing.fish.Pattern.TAG, pattern);
        CompoundTag rec = new CompoundTag();
        rec.put("Card", card);
        h.tag = rec;
        return h;
    }

    // ---- genes ----

    /**
     * The game's genetics for a pond: the clutch the mother's genome and weight lay, the young's genome
     * crossed from both parents (a lethal cross never hatches), fry survival by the V locus as in the tank,
     * and the variety a koi or a carp shows read off the new genome.
     */
    static final Life.Nursery NURSERY = new Life.Nursery() {
        @Override
        public int clutch(Species sp, Life.Head mother) {
            FishProfile p = profile(sp);
            return Genome.clutch(genes(mother), (int) mother.weightG, p, null);
        }

        @Override
        public Life.Head hatch(Species sp, Life.Head mother, Life.Head father, long uid, double hour, Random r) {
            FishProfile p = profile(sp);
            String gm = genes(mother), gf = genes(father);
            boolean clone = mother == father || (p != null && p.gynogenesis);
            String g = clone ? gm : Genome.cross(gm, gf, r);
            for (int i = 0; !clone && i < 8 && Genome.lethal(g); i++) g = Genome.cross(gm, gf, r);
            if (Genome.lethal(g)) return null;
            // V is vigour: in a pond it tips the odds rather than halving them — the pond has predators and room of its own
            double survival = !Genome.dominant(g, 'V') ? 0.75 : Genome.pure(g, 'V') ? 1.0 : 0.9;
            if (r.nextDouble() >= survival) return null;
            int sex = p != null && p.gynogenesis ? 0 : r.nextInt(2);
            Life.Head h = synth(sex, g, com.riverfishing.fish.CatchCard.pattern(record(mother).getCompoundOrEmpty("Card")), 0.5, hour, uid);
            CompoundTag card = ((CompoundTag) h.tag).getCompoundOrEmpty("Card");
            if (Genome.isKoiId(sp.id())) card.putString("Variety", "koi_" + Genome.koiVariety(g));
            else if ("carp".equals(sp.id())) card.putString("Variety", Genome.carpVariety(g));
            return h;
        }

        @Override
        public boolean selfing(Species sp) {
            FishProfile p = profile(sp);
            return p != null && p.gynogenesis;
        }
    };

    private static FishProfile profile(Species sp) {
        return FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(sp.id()));
    }

    // ---- save ----

    static CompoundTag saveHead(Life.Head h) {
        CompoundTag t = new CompoundTag();
        t.putLong("U", h.uid);
        t.putByte("S", (byte) h.sex);
        t.putDouble("W", h.weightG);
        t.putDouble("B", h.bornHour);
        t.putBoolean("T", h.trophy);
        t.putBoolean("L", h.legend);
        t.putBoolean("R", h.returned);
        t.putDouble("P", h.lastSpawnHour);
        if (h.tag instanceof CompoundTag rec) t.put("Rec", rec);
        return t;
    }

    static Life.Head loadHead(CompoundTag t) {
        Life.Head h = new Life.Head(t.getLongOr("U", 0L), t.getByteOr("S", (byte) 0), t.getDoubleOr("W", 0.0), t.getDoubleOr("B", 0.0));
        h.trophy = t.getBooleanOr("T", false);
        h.legend = t.getBooleanOr("L", false);
        h.returned = t.getBooleanOr("R", false);
        h.lastSpawnHour = t.getDoubleOr("P", 0.0);
        if (t.contains("Rec")) h.tag = t.getCompoundOrEmpty("Rec");
        return h;
    }
}
