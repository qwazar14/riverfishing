package com.riverfishing.fishing;

import com.riverfishing.alife.Lake;
import com.riverfishing.alife.Life;
import com.riverfishing.alife.Species;
import com.riverfishing.engine.BarometricPressure;
import com.riverfishing.engine.BiteContext;
import com.riverfishing.engine.BiteEngine;
import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.engine.Weather;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.integration.SeasonProvider;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.DoubleFunction;

/**
 * §alife: the living water of a level — one {@link Lake} per ~128-block region (StockedData's region key),
 * one zone per chunk of water in it.
 *
 * <p>Nothing about a water nobody has touched is stored: its zones are read off the world and its fish grown
 * from the seed through the same habitat gates the old engine used, so throwing an untouched lake away and
 * building it again gives the same water. A lake the angler fed, fished or scared is {@link Lake#touched}
 * and is kept in the save — for good if it remembers fish of its own (a pond, a fish put back, an event's), else
 * until nobody has asked about it for a month (§alife-forget).
 */
public final class AlifeData extends SavedData {
    private static final String NAME = "riverfishing_alife";
    /** A region holds at most this many agents; past it a new zone's fish join a shoal of their kind. */
    private static final int MAX_AGENTS = 384;
    /** Species a zone brings, DRAWN by weight rather than taken off the top — so a rare fish is somewhere. */
    private static final int SPECIES_PER_ZONE = 6;
    /** Past the cap a zone's fish join the nearest shoal of their kind within this reach, or are not added. */
    private static final double MERGE_BLOCKS = 32;
    /** Big predators hunt alone or in a handful — a "shoal" of seventeen pike is not a thing; perch do school. */
    private static final int PREDATOR_GROUP = 3;
    private static final double LONE_HUNTER_G = 1000;
    private static final double KG_PER_BLOCK = 0.02;
    private static final double TROPHY_MIN_G = 1000, TROPHY_CHANCE = 0.25;
    /** An untouched lake nobody asked about for this long (ticks) is dropped from memory. */
    private static final long EVICT_TICKS = 12000;
    /** §alife-forget: a touched wild lake that remembers no fish, nobody asked about for thirty game days. */
    private static final long FORGET_TICKS = 30 * 24000L;
    /** One throw of groundbait, in the lake's portions (a kilogram each). */
    private static final double FEED_PORTIONS = 1.0;

    private final Map<Long, Lake> lakes = new HashMap<>();
    private final Map<Long, Set<Long>> dry = new HashMap<>();      // transient: chunks surveyed and found dry
    private final Map<Long, Long> lastAsked = new HashMap<>();     // transient
    /** §alife-wild: regions whose old stocking book has been read into their living water. */
    private final Set<Long> migrated = new HashSet<>();
    /** §life-clock: the water's own clock in ticks (-1 until first read), and the game and day clocks it last read. */
    private long lifeTicks = -1, lastGame, lastDay;

    private static final Map<FishProfile, Species> SPECIES = Collections.synchronizedMap(new WeakHashMap<>());

    public static AlifeData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(AlifeData::load, AlifeData::new, NAME);
    }

    /** The lake around {@code pos}, with every loaded chunk of its region surveyed and its clock brought to now. */
    public Lake lakeAt(ServerLevel level, BlockPos pos) {
        long region = StockedData.region(pos);
        long now = level.getGameTime(), life = clock(level);
        Lake lake = lakes.computeIfAbsent(region, r -> new Lake(level.getSeed() ^ r, hourOf(life)));
        lastAsked.put(region, now);
        int rx = (pos.getX() >> 7) << 3, rz = (pos.getZ() >> 7) << 3;
        for (int cx = rx; cx < rx + 8; cx++) for (int cz = rz; cz < rz + 8; cz++) survey(level, region, lake, cx, cz);
        // §alife-wild: the fish players put into this water under the old book become fish of the living water, once
        if (!lake.zones.isEmpty() && level.dimension() == net.minecraft.world.level.Level.OVERWORLD && migrated.add(region)) {   // §alife-wild: once, overworld
            PondLife.migrateWild(level, lake, region);
            setDirty();
        }
        lake.advance(hourOf(life), conditions(level, pos));
        evict(now, life);
        return lake;
    }

    /**
     * §alife-pond: the living pond of a claim, built (and its old book read) the first time, brought up to
     * now. Null while some of its water is not loaded — the caller then leaves the pond alone.
     */
    public Lake pond(ServerLevel level, PondData.Claim claim) {
        long key = StockedData.get(level).pondKey(level, claim);
        long now = level.getGameTime(), life = clock(level);
        Lake lake = lakes.get(key);
        if (lake == null) {
            lake = PondLife.build(level, claim, key, hourOf(life));
            if (lake == null) return null;
            lakes.put(key, lake);
            setDirty();
        }
        lastAsked.put(key, now);
        PondLife.upgrades(level, lake);
        lake.advance(hourOf(life), conditions(level, BlockPos.of(claim.sign)));
        return lake;
    }

    /** §alife-pond: the living pond here if it has already been built — never builds or advances one. */
    public Lake existingPond(ServerLevel level, BlockPos pos) {
        if (!com.riverfishing.config.RiverFishingConfig.alife()) return null;
        PondData.Claim c = PondLife.claimNear(level, pos);
        return c == null ? null : lakes.get(StockedData.get(level).pondKey(level, c));
    }

    /** §alife-wild: a species put into this water may live in every zone of it the old gates let it — a transplant is not stuck where it landed. */
    static void admit(Lake lake, FishProfile p) {
        for (Lake.Zone z : lake.zones) if (!z.lives.isEmpty()) z.lives.add(p.id.getPath());
    }

    /**
     * §alife: the electrofisher's "put it here" — a pond gets ten remembered fish (five pairs, the pond's
     * rules), wild water an ordinary shoal of twenty that may swim wherever the water lets anything swim.
     */
    public static void stock(ServerLevel level, Lake lake, boolean pond, FishProfile p, BlockPos at) {
        int zone = zoneAt(lake, at);
        Species sp = species(p);
        if (pond) {
            java.util.Random rng = new java.util.Random(level.getRandom().nextLong());
            for (int i = 0; i < 10; i++) {
                Life.Head h = PondLife.synth(i % 2, "", 0, p.weightMean, lake.hour, rng.nextLong());
                Life.join(lake, sp, h, zone);
            }
            return;
        }
        admit(lake, p);
        lake.agents.add(new Lake.Agent(sp, 20, p.weightMean, zone, false));
    }

    /** §shoal-live: where a zone's fish are drawn — its deepest surveyed surface block. */
    static void anchor(Lake.Zone z, BlockPos at, int depth) {
        if (at == null) return;
        z.ax = at.getX(); z.ay = at.getY(); z.az = at.getZ(); z.adepth = depth;
    }

    /** The zone a block is in — its own chunk's, or the nearest one if that chunk holds no fishable water. */
    public static int zoneAt(Lake lake, BlockPos pos) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < lake.zones.size(); i++) {
            Lake.Zone z = lake.zones.get(i);
            double d = Math.hypot(z.x - pos.getX(), z.z - pos.getZ());
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    public static double hourOf(long ticks) { return ticks / 1000.0; }

    /**
     * §life-clock: the water's clock, in ticks. It runs with the calendar: a night slept through or a /time add ages
     * the fish as far as the date moved — their spawning windows, read off the date, always did. On the game's ticks
     * alone, a player who slept every night had a pond spawn on time while its fry never reached their sixteen days.
     * A clock set back, or a frozen daylight cycle, still runs with the ticks, so a water never stops or goes back.
     */
    public long clock(ServerLevel level) {
        long g = level.getGameTime(), d = level.getDayTime();
        if (lifeTicks < 0) lifeTicks = g;   // a save from before the clock: carry on from the ticks the lakes were on
        else lifeTicks += Math.max(Math.max(0L, g - lastGame), d - lastDay);
        lastGame = g;
        lastDay = d;
        return lifeTicks;
    }

    /**
     * §alife: hand a cast the living water it fishes, brought up to now — or leave {@code ctx.lake} null and
     * let the old engine answer. Claimed ponds, farmed/stocked water and ice holes stay on the old engine
     * until stocking and winter are agents too (phase 4): switching them now would empty every pond.
     */
    public static void attach(ServerLevel level, BlockPos water, BiteContext ctx) {
        ctx.lake = null;
        if (!com.riverfishing.config.RiverFishingConfig.alife() || ctx.iceHole) return;
        PondData.Claim claim = PondLife.claimNear(level, water);
        if (claim != null) {
            // §alife-pond: the pond's own fish, and nothing else — no community, no book, no bank
            Lake pond = get(level).pond(level, claim);
            if (pond == null || pond.zones.isEmpty()) return;   // some of it not loaded: the old engine takes this one cast
            ctx.lake = pond;
            ctx.lakeZone = zoneAt(pond, water);
            ctx.lakeNow = biteConditions(level, water, pond.hour);
            ctx.privatePond = true;
            ctx.communityFactor = null;
            ctx.stockedPresence = id -> PondLife.holds(pond, id.getPath()) ? 1.0 : 0.0;
            return;
        }
        if (ctx.privatePond) return;
        Lake lake = get(level).lakeAt(level, water);
        if (lake.zones.isEmpty()) return;
        ctx.lake = lake;
        ctx.lakeZone = zoneAt(lake, water);
        ctx.lakeNow = biteConditions(level, water, lake.hour);
    }

    /**
     * §warm-outflow: what a bite at this spot is judged under. By a warm outflow the season comes early — in winter
     * its fish are as awake as in spring: a winter hotspot a player can build. Only the bite here; the lake lives
     * its own season, and the spawn keeps the calendar.
     */
    private static Species.Conditions biteConditions(ServerLevel level, BlockPos water, double hour) {
        Species.Conditions c = conditions(level, water).apply(hour);
        if (c.season() != Season.WINTER || !WaterUpgrades.at(level, water).contains("warm_outflow")) return c;
        return new Species.Conditions(Season.SPRING, c.time(), c.weather(), Math.min(1.0, c.waterTemp() + 0.25),
                c.pressureFactor(), c.spawning());
    }

    /**
     * §boilies: a handful of boilies thrown in — prebaiting. They feed like any bed of feed (for the fish that
     * eat boilies) and they carry their flavours, which is what the fish that eat them get used to.
     */
    public static void fedBoilies(ServerLevel level, BlockPos pos, com.riverfishing.fish.Boilie b, double kg) {
        if (!com.riverfishing.config.RiverFishingConfig.alife()) return;
        Map<String, Double> keys = new HashMap<>();
        keys.put("boilie", 1.0);
        for (com.riverfishing.fish.Flavour f : b.flavours()) keys.put("flavour:" + f.id(), 0.0);
        Lake lake = PondLife.lake(level, pos);
        if (lake == null && PondLife.claimNear(level, pos) != null) return;
        if (lake == null) lake = get(level).lakeAt(level, pos);
        if (!lake.zones.isEmpty()) lake.feed(zoneAt(lake, pos), kg, keys, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /** §alife: groundbait landed here — the zone gets a bed of it, made of what the mix is made of. */
    public static void fed(ServerLevel level, BlockPos pos, com.riverfishing.groundbait.GroundbaitMix mix) {
        if (!com.riverfishing.config.RiverFishingConfig.alife()) return;
        int spoons = mix.additiveSpoons();
        if (spoons <= 0) return;   // a plain base says nothing to any fish
        Map<String, Double> keys = new HashMap<>();
        mix.diets().forEach((k, n) -> keys.put(k, n / (double) spoons));
        // §alife-pond: into a pond it feeds the pond's own fish — a pond's growth is what it is given
        Lake lake = PondLife.lake(level, pos);
        if (lake == null && PondLife.claimNear(level, pos) != null) return;   // a pond not loaded yet
        if (lake == null) lake = get(level).lakeAt(level, pos);
        if (!lake.zones.isEmpty()) lake.feed(zoneAt(lake, pos), FEED_PORTIONS, keys, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Conditions across a span of hours. The hour of the day is exact; the season, the weather and the glass
     * are today's for the whole span.
     */
    // ponytail: a catch-up across a week of rain and sun is lived under today's sky; keep a weather log if it shows.
    public static DoubleFunction<Species.Conditions> conditions(ServerLevel level, BlockPos pos) {
        Season season = SeasonProvider.getSeason(level);
        Weather weather = level.isThundering() ? Weather.THUNDER : level.isRaining() ? Weather.RAIN : Weather.CLEAR;
        double glass = BarometricPressure.biteFactor(level);
        double biome = (level.getBiome(pos).value().getBaseTemperature() - 0.8) * 0.3;
        double base = season == Season.SUMMER ? 0.7 : season == Season.SPRING ? 0.45
                : season == Season.AUTUMN ? 0.4 : 0.05;
        long offset = level.getDayTime() - get(level).clock(level);   // §life-clock: a lake hour to the date
        return h -> {
            TimeOfDay t = TimeOfDay.fromDayTime((long) (h * 1000) + offset);
            double temp = Math.max(0, Math.min(1, base + biome + (t == TimeOfDay.DAY ? 0.15 : 0)));
            return new Species.Conditions(season, t, weather, temp, glass, id -> spawning(level, id, h, offset));
        };
    }

    /**
     * §alife-life: is this species in its spawning window at game hour {@code h}? Off the mod's own calendar
     * for that very hour, so a pond caught up across a month spawns in the week it should have; under Serene
     * Seasons, whose date only it knows, today's answer.
     */
    private static boolean spawning(ServerLevel level, String id, double h, long offset) {
        FishProfile p = FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(id));
        if (p == null || p.spawnSeason == null) return false;
        if (SeasonProvider.present()) return com.riverfishing.engine.Calendar.inWindow(level, p);
        int year = com.riverfishing.engine.Calendar.YEAR_DAYS;
        int day = (int) Math.floorMod(Math.floorDiv((long) (h * 1000) + offset, 24000L), (long) year);
        int start = p.spawnSeason.ordinal() * com.riverfishing.engine.Calendar.SEASON_DAYS
                + (p.spawnSub == null ? 0 : p.spawnSub.ordinal() * com.riverfishing.engine.Calendar.SUB_DAYS);
        int length = p.spawnSub == null ? com.riverfishing.engine.Calendar.SEASON_DAYS : com.riverfishing.engine.Calendar.SUB_DAYS;
        return Math.floorMod(day - start, year) < length;
    }

    public static Species species(FishProfile p) {
        return SPECIES.computeIfAbsent(p, q -> new Species(q.id.getPath(), q.diet == null ? "" : q.diet,
                q.weightMean, q.weightMax, q.season, q.time, q.weather, q.depthPref == null ? "" : q.depthPref,
                q.baitScores));
    }

    // ---- building the water ----

    private void survey(ServerLevel level, long region, Lake lake, int cx, int cz) {
        double zx = (cx << 4) + 8, zz = (cz << 4) + 8;
        for (Lake.Zone z : lake.zones) if (z.x == zx && z.z == zz) return;
        Set<Long> dryHere = dry.computeIfAbsent(region, r -> new HashSet<>());
        long chunk = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
        if (dryHere.contains(chunk) || !level.hasChunk(cx, cz)) return;

        // sixteen columns, a heightmap probe each: the water's surface, its depth, its bed, and whether
        // anything grows in it — weed, kelp, lilies
        int wet = 0, depthSum = 0, cover = 0, bestDepth = -1;
        int[] beds = new int[16];
        int[][] depthAt = new int[4][4];
        BlockPos[][] at = new BlockPos[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int x = (cx << 4) + 2 + 4 * i, z = (cz << 4) + 2 + 4 * j;
                BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (level.getBlockState(p).is(Blocks.LILY_PAD)) { cover++; p = p.below(); }
                if (!level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) continue;
                int d = FishingManager.measureDepth(level, p);
                wet++;
                depthSum += d;
                beds[Math.min(15, Math.max(0, FishingManager.bedType(level, p)))]++;
                if (!level.getBlockState(p.below(d - 1)).is(Blocks.WATER)) cover++;   // seagrass, kelp, weed
                depthAt[i][j] = d;
                at[i][j] = p;
            }
        }
        // §shoal-open: the fish are drawn around this column, so it must be OPEN water — the deepest pit in
        // the chunk was a one-block hole where every fish of the shoal circled inside a block. Depth counts,
        // and so does having water all round it.
        BlockPos deepest = null;
        double bestScore = -1;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (at[i][j] == null) continue;
                int open = 0;
                for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int a = i + n[0], b = j + n[1];
                    if (a >= 0 && a < 4 && b >= 0 && b < 4 && at[a][b] != null) open += Math.min(depthAt[a][b], depthAt[i][j]);
                }
                double score = depthAt[i][j] + open;
                if (score > bestScore) { bestScore = score; deepest = at[i][j]; bestDepth = depthAt[i][j]; }
            }
        }
        if (wet < 2) { dryHere.add(chunk); return; }
        int bed = 0;
        for (int b = 1; b < beds.length; b++) if (beds[b] > beds[bed]) bed = b;
        double depth = depthSum / (double) wet;
        Lake.Zone zone = new Lake.Zone("c" + cx + "," + cz, zx, zz, depth, bed, Math.min(1.0, cover / (double) wet));
        anchor(zone, deepest, bestDepth);
        lake.zones.add(zone);
        populate(level, lake, lake.zones.size() - 1, deepest, wet / 16.0 * 256 * depth * KG_PER_BLOCK);
        if (lake.touched) lake.changed = true;   // §alife-save: a kept water grew a zone
    }

    /**
     * The fish a zone brings with it: the species whose habitat passes at its deepest column, ranked by
     * habitat × density with a seeded jitter so neighbouring chunks do not all hold the same three, sharing
     * the zone's biomass; now and then a trophy of a big species on its own.
     */
    private void populate(ServerLevel level, Lake lake, int zone, BlockPos at, double biomassKg) {
        WaterBody body = WaterBodyCache.forLevel(level).get(level, at);
        BiteContext env = FishingManager.environmentAt(level, at, body);
        long seed = level.getSeed() ^ at.asLong() * 0x9E3779B97F4A7C15L;
        List<FishProfile> pick = new ArrayList<>();
        Map<FishProfile, Double> weight = new HashMap<>();
        for (FishProfile p : FishProfileManager.get().all()) {
            double h = BiteEngine.habitatScore(p, env);
            if (h <= 0) continue;
            weight.put(p, h * p.base);
            pick.add(p);
            lake.zones.get(zone).lives.add(p.id.getPath());
        }
        // drawn by weight, without putting back: the common fish are nearly always there, and now and then a
        // chunk holds the rare one the water can carry — which is the whole variety of a real water
        java.util.Random draw = new java.util.Random(seed);
        List<FishProfile> chosen = new ArrayList<>();
        while (chosen.size() < SPECIES_PER_ZONE && !pick.isEmpty()) {
            double tot = 0;
            for (FishProfile p : pick) tot += weight.get(p);
            double r = draw.nextDouble() * tot;
            FishProfile got = pick.get(pick.size() - 1);
            for (FishProfile p : pick) if ((r -= weight.get(p)) <= 0) { got = p; break; }
            pick.remove(got);
            chosen.add(got);
        }
        pick = chosen;
        double total = 0;
        for (FishProfile p : pick) total += weight.get(p);
        for (FishProfile p : pick) {
            Species sp = species(p);
            double kg = biomassKg * weight.get(p) / total;
            int count = (int) Math.max(1, Math.min(groupCap(sp),
                    Math.round(kg * 1000 / Math.max(1, p.weightMean))));
            add(lake, new Lake.Agent(sp, count, p.weightMean, zone, false));
            double roll = jitter(seed ^ 0x5bd1e995L, p) - 0.5;
            if (p.weightMean >= TROPHY_MIN_G && roll < TROPHY_CHANCE * Math.min(1.0, weight.get(p))) {
                double w = Math.min(p.weightMax * 0.9, p.weightMean * (2.0 + 4.0 * roll));
                add(lake, new Lake.Agent(sp, 1, w, zone, true));
            }
        }
    }

    private static void add(Lake lake, Lake.Agent a) {
        if (lake.agents.size() < MAX_AGENTS) { lake.agents.add(a); return; }
        if (a.trophy) return;
        Lake.Zone at = lake.zones.get(a.zone);
        Lake.Agent near = null;
        double best = MERGE_BLOCKS;
        for (Lake.Agent o : lake.agents) {
            if (o.trophy || !o.sp.id().equals(a.sp.id())) continue;
            Lake.Zone z = lake.zones.get(o.zone);
            double d = Math.hypot(z.x - at.x, z.z - at.z);
            if (d <= best) { best = d; near = o; }
        }
        if (near == null) return;
        int cap = groupCap(near.sp);
        int grown = Math.min(cap, near.capacity + a.count) - near.capacity;
        near.count += grown;
        near.capacity += grown;
    }

    // ---- §event-fish (1.1.0): an organiser's hand in the water, for competitions ----

    /** How far past MAX_AGENTS an organiser may go: the cap is for the water's own growth, not for an event. */
    private static final int EVENT_ROOM = 128;

    /**
     * §event-fish: {@code count} fish of {@code p} at about {@code grams} each, as shoals no bigger than the
     * species keeps (a big lone hunter is a group of three at most), each shoal in its own zone, nearest the
     * organiser first — or {@code trophy}: that many single trophy fish. They are ordinary fish of this water in
     * every way the engine asks (they feed, move, bite, age, are caught), and marked, so /rffish clear takes them
     * out again without touching the water's own. Returns the groups made, or -1 when the water holds no more.
     */
    public static int spawnEvent(Lake lake, FishProfile p, BlockPos at, int count, double grams, boolean trophy) {
        Species sp = species(p);
        admit(lake, p);
        int here = zoneAt(lake, at);
        Lake.Zone origin = lake.zones.get(here);
        List<Integer> byDistance = new ArrayList<>();
        for (int i = 0; i < lake.zones.size(); i++) byDistance.add(i);
        byDistance.sort(java.util.Comparator.comparingDouble(i -> Math.hypot(lake.zones.get(i).x - origin.x, lake.zones.get(i).z - origin.z)));
        int cap = trophy ? 1 : groupCap(sp);
        int groups = 0;
        for (int left = count; left > 0; groups++) {
            if (lake.agents.size() >= MAX_AGENTS + EVENT_ROOM) return groups == 0 ? -1 : groups;
            int n = Math.min(cap, left);
            Lake.Agent a = new Lake.Agent(sp, n, grams, byDistance.get(groups % byDistance.size()), trophy);
            a.event = true;
            lake.agents.add(a);
            left -= n;
        }
        lake.touch();
        return groups;
    }

    /** §event-fish: take the event's fish out of this water — or, {@code all}, every fish and every egg in it. Returns {fish, groups}. */
    public static int[] clearEvent(Lake lake, boolean all) {
        int fish = 0, groups = 0;
        for (java.util.Iterator<Lake.Agent> it = lake.agents.iterator(); it.hasNext(); ) {
            Lake.Agent a = it.next();
            if (!all && !a.event) continue;
            fish += a.count;
            groups++;
            it.remove();
        }
        if (all) lake.roe.clear();
        lake.touch();
        return new int[]{fish, groups};
    }

    /** §event-fish: this water's fish by species, largest head count first — {groups, fish, kg, event fish}. */
    public static List<Map.Entry<String, double[]>> census(Lake lake) {
        Map<String, double[]> by = new HashMap<>();
        for (Lake.Agent a : lake.agents) {
            if (!a.alive()) continue;
            double[] v = by.computeIfAbsent(a.sp.id(), k -> new double[4]);
            v[0]++;
            v[1] += a.count;
            v[2] += a.count * a.weightG / 1000.0;
            if (a.event) v[3] += a.count;
        }
        List<Map.Entry<String, double[]>> out = new ArrayList<>(by.entrySet());
        out.sort((x, y) -> Double.compare(y.getValue()[1], x.getValue()[1]));
        return out;
    }

    private static int groupCap(Species sp) {
        return sp.predator() && sp.meanG() >= LONE_HUNTER_G ? PREDATOR_GROUP : 60;
    }

    /** A stable 0.5..1.5 from (seed, species). */
    private static double jitter(long seed, FishProfile p) {
        long h = seed ^ (long) p.id.getPath().hashCode() * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33;
        return 0.5 + (h >>> 11) / (double) (1L << 53);
    }

    private void evict(long now, long life) {
        if (lakes.size() < 32) return;
        boolean forgot = false;
        for (java.util.Iterator<Map.Entry<Long, Lake>> it = lakes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Lake> e = it.next();
            Lake l = e.getValue();
            // §alife-forget: a touched wild water that remembers no fish of its own and nobody has fished for a
            // month is let go like an untouched one — grown from the seed when someone comes back. Its memories
            // (wariness, familiarity, feed) are long gone by then; what it forgets is how hard it was fished.
            // Its clock is the last time anyone asked (every ask brings it up to now), so it needs no field.
            boolean idle = l.touched
                    ? life - (long) (l.hour * 1000) > FORGET_TICKS && !remembers(l)
                    : now - lastAsked.getOrDefault(e.getKey(), 0L) > EVICT_TICKS;
            if (!idle) continue;
            it.remove();
            forgot |= l.touched;
        }
        if (forgot) setDirty();   // the save lets it go too
        lastAsked.keySet().retainAll(lakes.keySet());
        dry.keySet().retainAll(lakes.keySet());
    }

    /**
     * §alife-forget: what a water must not forget — a player's pond, a fish someone put back (a remembered head,
     * or the fry and roe of one), an organiser's event fish. None of it grows back from the seed.
     */
    static boolean remembers(Lake l) {
        if (l.pond) return true;
        for (Lake.Agent a : l.agents) {
            if (a.event || (a.heads != null && !a.heads.isEmpty()) || a.mother != null) return true;
        }
        for (com.riverfishing.alife.Life.Roe r : l.roe) if (r.mother != null) return true;
        return false;
    }

    // ---- save ----

    /**
     * §alife-save: written when some kept water changed since the last save — it lived a step or was touched —
     * not on every autosave for as long as any water had ever been touched (and, with a migrated region, for good).
     */
    @Override
    public boolean isDirty() {
        if (super.isDirty()) return true;
        for (Lake l : lakes.values()) if (l.touched && l.changed) return true;
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        // §alife-save: every species name once, in "Names" — a zone's species are bits over it, an agent's and an
        // egg's kind an index into it. A sea region wrote sixty names in each of its sixty-four zones.
        List<String> names = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        java.util.function.ToIntFunction<String> name = n -> index.computeIfAbsent(n, k -> { names.add(k); return names.size() - 1; });
        ListTag list = new ListTag();
        for (Map.Entry<Long, Lake> e : lakes.entrySet()) {
            Lake lake = e.getValue();
            if (!lake.touched) continue;
            lake.changed = false;
            CompoundTag l = new CompoundTag();
            l.putLong("Region", e.getKey());
            l.putDouble("Hour", lake.hour);
            l.putBoolean("Pond", lake.nursery == PondLife.NURSERY);
            ListTag roe = new ListTag();
            for (com.riverfishing.alife.Life.Roe r : lake.roe) {
                CompoundTag t = new CompoundTag();
                t.putInt("I", name.applyAsInt(r.sp.id()));
                t.putInt("Z", r.zone);
                t.putDouble("E", r.eggs);
                t.putDouble("H", r.laidHour);
                if (r.mother != null) t.put("Mo", PondLife.saveHead(r.mother));
                if (r.father != null) t.put("Fa", PondLife.saveHead(r.father));
                roe.add(t);
            }
            if (!roe.isEmpty()) l.put("Roe", roe);
            ListTag zones = new ListTag();
            for (Lake.Zone z : lake.zones) {
                CompoundTag t = new CompoundTag();
                t.putString("Name", z.name);
                t.putDouble("X", z.x);
                t.putDouble("Z", z.z);
                t.putDouble("D", z.depth);
                t.putInt("B", z.bed);
                t.putDouble("C", z.baseCover);   // the survey's; the upgrades are re-read on every visit
                t.putDouble("N", z.natural);
                t.putDouble("V", z.volume);
                if (z.ay != Integer.MIN_VALUE) t.putIntArray("A", new int[]{z.ax, z.ay, z.az, z.adepth});
                t.putDouble("Ri", z.richness);
                ListTag feeds = new ListTag();
                for (Lake.Feed f : z.feeds) {
                    CompoundTag ft = new CompoundTag();
                    ft.putDouble("A", f.amount);
                    if (!Double.isNaN(f.x)) { ft.putDouble("X", f.x); ft.putDouble("Zp", f.z); }
                    ft.put("K", doubles(f.keys));
                    feeds.add(ft);
                }
                if (!feeds.isEmpty()) t.put("F", feeds);
                if (!z.lives.isEmpty()) t.putLongArray("Lv", bits(z.lives, name));
                zones.add(t);
            }
            l.put("Zones", zones);
            ListTag agents = new ListTag();
            for (Lake.Agent a : lake.agents) {
                CompoundTag t = new CompoundTag();
                t.putInt("I", name.applyAsInt(a.sp.id()));
                t.putInt("N", a.count);
                t.putDouble("W", a.weightG);
                t.putInt("Z", a.zone);
                t.putDouble("H", a.hunger);
                // §alife-save: what is at its default is left out — the load gives it back
                if (a.trophy) t.putBoolean("T", true);
                if (a.capacity != a.count) t.putInt("Cap", a.capacity);
                if (a.wary != 0) t.putDouble("R", a.wary);
                if (a.respawnAt != 0) t.putDouble("Re", a.respawnAt);
                if (a.lastSpawnHour > -1e8) t.putDouble("Sp", a.lastSpawnHour);
                if (a.event) t.putBoolean("Ev", true);   // §event-fish
                if (!a.fam.isEmpty()) t.put("Fam", doubles(a.fam));
                if (a.heads != null) {
                    ListTag heads = new ListTag();
                    for (com.riverfishing.alife.Life.Head h : a.heads) heads.add(PondLife.saveHead(h));
                    t.put("Heads", heads);
                }
                if (a.fry) {
                    t.putBoolean("Fry", true);
                    t.putDouble("Born", a.bornHour);
                    if (a.mother != null) t.put("Mo", PondLife.saveHead(a.mother));
                    if (a.father != null) t.put("Fa", PondLife.saveHead(a.father));
                }
                agents.add(t);
            }
            l.put("Agents", agents);
            list.add(l);
        }
        tag.put("Lakes", list);
        ListTag table = new ListTag();
        for (String n : names) table.add(net.minecraft.nbt.StringTag.valueOf(n));
        tag.put("Names", table);
        tag.putLongArray("Migrated", migrated.stream().mapToLong(Long::longValue).toArray());
        tag.putLong("Life", lifeTicks);   // §life-clock
        tag.putLong("LifeGame", lastGame);
        tag.putLong("LifeDay", lastDay);
        return tag;
    }

    public static AlifeData load(CompoundTag tag) {
        AlifeData data = new AlifeData();
        for (long r : tag.getLongArray("Migrated")) data.migrated.add(r);
        data.lifeTicks = tag.contains("Life") ? tag.getLong("Life") : -1L;   // §life-clock
        data.lastGame = tag.getLong("LifeGame");
        data.lastDay = tag.getLong("LifeDay");
        List<String> names = new ArrayList<>();   // §alife-save: empty in a save from before the table
        ListTag table = tag.getList("Names", Tag.TAG_STRING);
        for (int i = 0; i < table.size(); i++) names.add(table.getString(i));
        ListTag list = tag.getList("Lakes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag l = list.getCompound(i);
            long region = l.getLong("Region");
            Lake lake = new Lake(region ^ l.getLong("Hour"), l.getDouble("Hour"));
            lake.touched = true;
            ListTag zones = l.getList("Zones", Tag.TAG_COMPOUND);
            for (int j = 0; j < zones.size(); j++) {
                CompoundTag t = zones.getCompound(j);
                Lake.Zone z = new Lake.Zone(t.getString("Name"), t.getDouble("X"), t.getDouble("Z"),
                        t.getDouble("D"), t.getInt("B"), t.getDouble("C"));
                z.natural = t.getDouble("N");
                if (t.contains("V")) z.volume = t.getDouble("V");
                int[] a = t.getIntArray("A");
                if (a.length == 4) { z.ax = a[0]; z.ay = a[1]; z.az = a[2]; z.adepth = a[3]; }
                if (t.contains("Ri")) z.richness = t.getDouble("Ri");
                ListTag feeds = t.getList("F", Tag.TAG_COMPOUND);
                for (int k = 0; k < feeds.size(); k++) {
                    CompoundTag ft = feeds.getCompound(k);
                    Lake.Feed f = new Lake.Feed(ft.getDouble("A"), readDoubles(ft.getCompound("K")));
                    if (ft.contains("X")) { f.x = ft.getDouble("X"); f.z = ft.getDouble("Zp"); }
                    z.feeds.add(f);
                }
                unbits(t.getLongArray("Lv"), names, z.lives);
                ListTag lives = t.getList("L", Tag.TAG_STRING);   // a save from before the table
                for (int k = 0; k < lives.size(); k++) z.lives.add(lives.getString(k));
                lake.zones.add(z);
            }
            ListTag agents = l.getList("Agents", Tag.TAG_COMPOUND);
            for (int j = 0; j < agents.size(); j++) {
                CompoundTag t = agents.getCompound(j);
                FishProfile p = FishProfileManager.get().byId(
                        new ResourceLocation("riverfishing", kind(t, names)));
                if (p == null) continue;   // a species that left the game leaves the water with it
                Lake.Agent a = new Lake.Agent(species(p), t.getInt("N"), t.getDouble("W"), t.getInt("Z"), t.getBoolean("T"));
                if (t.contains("Cap")) a.capacity = t.getInt("Cap");
                a.hunger = t.getDouble("H");
                a.wary = t.getDouble("R");
                a.respawnAt = t.getDouble("Re");
                if (t.contains("Sp")) a.lastSpawnHour = t.getDouble("Sp");
                a.fam.putAll(readDoubles(t.getCompound("Fam")));
                if (t.contains("Heads")) {
                    a.heads = new ArrayList<>();
                    ListTag heads = t.getList("Heads", Tag.TAG_COMPOUND);
                    for (int k = 0; k < heads.size(); k++) a.heads.add(PondLife.loadHead(heads.getCompound(k)));
                    a.sync();
                }
                if (t.getBoolean("Fry")) {
                    a.fry = true;
                    a.bornHour = t.getDouble("Born");
                    if (t.contains("Mo")) a.mother = PondLife.loadHead(t.getCompound("Mo"));
                    if (t.contains("Fa")) a.father = PondLife.loadHead(t.getCompound("Fa"));
                }
                a.event = t.getBoolean("Ev");   // §event-fish
                if (a.zone < lake.zones.size()) lake.agents.add(a);
            }
            if (l.getBoolean("Pond")) {
                lake.pond = true;
                lake.nursery = PondLife.NURSERY;
                lake.catchUpH = 24 * com.riverfishing.engine.Calendar.YEAR_DAYS;
            }
            ListTag roe = l.getList("Roe", Tag.TAG_COMPOUND);
            for (int j = 0; j < roe.size(); j++) {
                CompoundTag t = roe.getCompound(j);
                FishProfile p = FishProfileManager.get().byId(
                        new ResourceLocation("riverfishing", kind(t, names)));
                if (p == null || t.getInt("Z") >= lake.zones.size()) continue;
                lake.roe.add(new com.riverfishing.alife.Life.Roe(species(p), t.getInt("Z"), t.getDouble("E"), t.getDouble("H"),
                        t.contains("Mo") ? PondLife.loadHead(t.getCompound("Mo")) : null,
                        t.contains("Fa") ? PondLife.loadHead(t.getCompound("Fa")) : null));
            }
            data.lakes.put(region, lake);
        }
        return data;
    }

    /** §alife-save: a set of names as bits over the save's table. */
    static long[] bits(Set<String> set, java.util.function.ToIntFunction<String> name) {
        long[] b = new long[0];
        for (String n : set) {
            int i = name.applyAsInt(n);
            if (i >> 6 >= b.length) b = java.util.Arrays.copyOf(b, (i >> 6) + 1);
            b[i >> 6] |= 1L << (i & 63);
        }
        return b;
    }

    /** §alife-save: the names whose bits are set. */
    static void unbits(long[] b, List<String> names, Set<String> into) {
        for (int w = 0; w < b.length; w++) {
            for (long v = b[w]; v != 0; v &= v - 1) {
                int i = (w << 6) + Long.numberOfTrailingZeros(v);
                if (i < names.size()) into.add(names.get(i));
            }
        }
    }

    /** §alife-save: an agent's or an egg's species — an index into the table, or the name in a save from before it. */
    private static String kind(CompoundTag t, List<String> names) {
        int i = t.contains("I") ? t.getInt("I") : -1;
        return i >= 0 && i < names.size() ? names.get(i) : t.getString("S");
    }

    private static CompoundTag doubles(Map<String, Double> m) {
        CompoundTag t = new CompoundTag();
        m.forEach(t::putDouble);
        return t;
    }

    private static Map<String, Double> readDoubles(CompoundTag t) {
        Map<String, Double> m = new HashMap<>();
        for (String k : t.getAllKeys()) m.put(k, t.getDouble(k));
        return m;
    }
}
