package com.riverfishing.fishing;

import com.riverfishing.engine.BiteContext;
import com.riverfishing.engine.BiteEngine;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.network.ModNetwork;
import com.riverfishing.network.ShoalPacket;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import com.riverfishing.water.WaterType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * §shoal (0.7.0): decides what the player can SEE in the water, and tells the client.
 *
 * <p>The mod has always known which species live in a given pond, how hard that spot has been fished and
 * what has been stocked into it — and never showed the player any of it. This is that state made visible:
 * the fish drifting under the surface are the species this water body actually holds, and the shoal thins
 * as the spot is fished out and fills back in as it recovers.
 *
 * <p>Four deliberate properties:
 * <ul>
 *   <li><b>Residents, not takers.</b> The shoal is chosen from habitat, season, time, weather and biome —
 *       NOT from your tackle. Swapping a lure must not make fish appear and vanish; that would read as a
 *       bug, and the honest question a player asks when looking at water is "what lives here".</li>
 *   <li><b>The whole view, not your feet.</b> Every {@link #CELL}-block cell of water across the 3×3
 *       chunks around you gets its own shoal, so a lake is populated to the far bank.</li>
 *   <li><b>Anchored and stable.</b> Cells are pinned to the WORLD grid and seeded from the cell plus the
 *       in-game hour, so walking adds and drops shoals at the edges instead of dragging every fish along
 *       with you, and each shoal holds still while you fish it.</li>
 *   <li><b>Cheap.</b> One pass every {@link #PERIOD} ticks per player, O(1) heightmap probes to find the
 *       water, a cached water-body lookup per cell (the grid anchoring is what makes that cache hit), and
 *       nothing at all when there is no water in view. The client animates everything itself.</li>
 * </ul>
 */
public final class ShoalTracker {
    /** Two seconds. The shoal is ambient scenery, not a HUD — it does not need to be current. */
    private static final int PERIOD = 40;
    /** 3×3 chunks around the player: 24 blocks each way from where they stand. */
    private static final int RADIUS_BLOCKS = 24;
    /** One shoal per 12×12 cell, pinned to the world grid so the shoals do not walk with the player. */
    private static final int CELL = 12;
    /** A 5×5 grid of cells covers the 3×3 chunks; twenty of them is the whole visible sheet of water. */
    private static final int MAX_SPOTS = 20;
    /**
     * Fish are allotted by distance, not first-come. Nearest-first with one flat cap starves the far
     * cells — the near shoals eat the whole budget and the far bank comes out empty, which is the exact
     * opposite of "let me see them further away". So a cell at your feet gets a shoal, and a cell across
     * the water gets the two fish that are actually big enough to make out from there.
     */
    private static final int NEAR = 12, MID = 26, FAR = 40;
    private static final int WANT_NEAR = 9, WANT_MID = 8, WANT_FAR = 6;
    /** Length below which a fish is not worth sending at all from {@link #MID} / {@link #FAR} away. */
    private static final int SEE_MID_CM = 35, SEE_FAR_CM = 90;
    /** Total across all shoals — the render cost is per sprite, and this is the only place to bound it. */
    private static final int MAX_FISH_TOTAL = 160;
    /** A lake 30 blocks below the clifftop you are standing on is not the water you are looking at. */
    private static final int Y_BAND = 20;
    /** Spook at which a patch shows no fish at all; below it the shoal thins in proportion. */
    private static final double SPOOK_GONE = 0.55;

    /** Last shoal sent, so an unchanged view is not re-sent and walking away clears exactly once. */
    private static final Map<UUID, String> LAST = new HashMap<>();

    private ShoalTracker() {}

    public static void tick(ServerPlayer sp) {
        ServerLevel level = sp.level();
        long now = level.getGameTime();
        if ((now + sp.getId()) % PERIOD != 0) return;         // stagger players across ticks

        // The in-game hour is the shoal's clock: it holds still while you fish, and has moved on when you
        // come back. floorDiv, not /, so it does not jitter around midnight of a negative game time.
        long hour = Math.floorDiv(now, 1000L);
        // §shoal-live: with the living water on, what you see is its fish — the same agents that bite
        boolean living = com.riverfishing.config.RiverFishingConfig.alife();
        List<ShoalPacket.Spot> spots = living ? scanLiving(level, sp, now) : scan(level, sp, now, hour);

        if (spots.isEmpty()) {
            if (LAST.remove(sp.getUUID()) != null) ModNetwork.toPlayer(sp, ShoalPacket.empty());
            return;
        }
        StringBuilder sig = new StringBuilder(24).append(living ? 0 : hour);
        for (ShoalPacket.Spot s : spots) {
            if (living) for (ShoalPacket.Entry e : s.fish()) sig.append(',').append(e.group()).append(e.kind());
            if (s.hasBait()) sig.append('b').append((int) s.baitX()).append((int) s.baitZ());
            // §shoal-spook: the spook bucket is part of the signature, or a shoal that has just been
            // frightened never gets told to the client — the composition has not changed, so nothing
            // would be sent, and the flight would never animate.
            sig.append('|').append(s.centre().asLong()).append(':').append(s.fish().size())
                    .append(':').append(Math.round(s.clarity() * 20))
                    .append(':').append(s.spook() / 12);
        }
        String key = sig.toString();
        if (key.equals(LAST.get(sp.getUUID()))) return;
        LAST.put(sp.getUUID(), key);
        ModNetwork.toPlayer(sp, new ShoalPacket(spots));
    }

    /**
     * Every cell of water in view, nearest first, each with its own shoal. Cells are world-aligned, which
     * is what lets the water-body cache hit and what keeps the fish still while the player moves.
     */
    private static List<ShoalPacket.Spot> scan(ServerLevel level, ServerPlayer sp, long now, long hour) {
        int px = sp.getBlockX(), py = sp.getBlockY(), pz = sp.getBlockZ();
        int cx0 = Math.floorDiv(px - RADIUS_BLOCKS, CELL), cx1 = Math.floorDiv(px + RADIUS_BLOCKS, CELL);
        int cz0 = Math.floorDiv(pz - RADIUS_BLOCKS, CELL), cz1 = Math.floorDiv(pz + RADIUS_BLOCKS, CELL);

        List<BlockPos> cells = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                BlockPos surface = surfaceInCell(level, cx, cz, py);
                if (surface != null) cells.add(surface);
            }
        }
        if (cells.isEmpty()) return List.of();
        cells.sort(Comparator.comparingDouble(p -> p.distToLowCornerSqr(px, py, pz)));

        FishingPressureData pressure = FishingPressureData.get(level);

        List<ShoalPacket.Spot> out = new ArrayList<>();
        int budget = MAX_FISH_TOTAL;
        for (BlockPos surface : cells) {
            if (out.size() >= MAX_SPOTS || budget < 2) break;
            double d = Math.sqrt(surface.distToLowCornerSqr(px, py, pz));
            int want = d > FAR ? WANT_FAR : d > NEAR ? WANT_MID : WANT_NEAR;
            int minLen = d > FAR ? SEE_FAR_CM : d > MID ? SEE_MID_CM : 0;
            WaterBody body = WaterBodyCache.forLevel(level).get(level, surface);
            if (body == null || body.type() == WaterType.NONE) continue;
            BiteContext env = FishingManager.environmentAt(level, surface, body);
            // §shoal-deep: the species gate reads the WATER's depth (the deepest column within three
            // blocks), the fish are placed by the probe's own column so none is drawn inside the bank.
            int colDepth = env.waterDepth;
            env.waterDepth = FishingManager.deepestAround(level, surface, 3);
            Pool pool = poolFor(env, surface, pressure, now, hour);
            if (pool.total <= 0) continue;

            // §spook: frightened fish leave, and that is the only readout this mechanic ever gets — you
            // watch the water empty. Above SPOOK_GONE the patch shows nothing at all.
            double spook = SpookData.of(level).at(surface, now);
            if (spook >= SPOOK_GONE * 1.6) continue;
            byte spookByte = (byte) Mth.clamp((int) Math.round(spook / SPOOK_GONE * 100.0), 0, 100);

            RandomSource rng = RandomSource.create(surface.asLong() * 31L + hour);
            // §shoal-stable: the draw must not depend on the budget. It did: nearer cells eat the
            // budget first, so a step that reordered the cells changed this cell's cap, which changed
            // how many draws it made, which changed WHICH species came out — and the client put the
            // new species into the old fish's positions. Startle a shoal and it turned into a
            // different shoal. So the cell draws its own shoal, and the budget only trims the tail.
            List<ShoalPacket.Entry> fish = pick(pool, colDepth, want, minLen, rng);
            if (fish.size() > budget) fish = new ArrayList<>(fish.subList(0, budget));
            if (fish.isEmpty()) continue;
            budget -= fish.size();
            // Circuits have to stay inside the cell, or two neighbouring shoals swim through each other
            // and the outer laps cross the bank.
            byte spread = (byte) Mth.clamp((int) Math.round(Math.min(body.width() * 0.4, CELL / 2.0)), 1, 5);
            out.add(new ShoalPacket.Spot(surface, clarity(level, body, surface), spread, spookByte, fish));
        }
        return out;
    }

    // ---- §shoal-live: the living water's own fish ----

    /** How far around the player the living water is shown, blocks. */
    private static final int VIEW = 40;
    /** Every fish on screen at once, across all shoals — the render cost is per fish. */
    private static final int MAX_FISH_LIVE = 320;

    private record ZoneView(com.riverfishing.alife.Lake lake, com.riverfishing.alife.Lake.Zone zone, int index, double dist) {}

    /**
     * Every zone of living water in view — the region lakes around the player and any pond — each drawn as
     * one shoal at its deepest water, with a lane per group of fish in it. A pond shows its fish one by one
     * (up to two dozen a group); a wild shoal shows about two per square root of its count, so a big shoal
     * reads big; a batch of fry is a flicker of tiny fish. Nothing here is made up: move the agent and the
     * fish swim to its new zone, catch one and it is gone from the water.
     */
    private static List<ShoalPacket.Spot> scanLiving(ServerLevel level, ServerPlayer sp, long now) {
        int px = sp.getBlockX(), py = sp.getBlockY(), pz = sp.getBlockZ();
        // §fish-world: the diver sees the water as it is — every fish of a shoal near him, clear water
        boolean diving = sp.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        AlifeData data = AlifeData.get(level);
        List<com.riverfishing.alife.Lake> ponds = new ArrayList<>();
        for (PondData.Claim c : PondData.near(level, sp.blockPosition(), VIEW + 64)) {
            com.riverfishing.alife.Lake l = data.pond(level, c);
            if (l != null) ponds.add(l);
        }
        List<com.riverfishing.alife.Lake> lakes = new ArrayList<>(ponds);
        java.util.Set<Long> regions = new java.util.HashSet<>();
        for (int dx = -VIEW; dx <= VIEW; dx += VIEW) for (int dz = -VIEW; dz <= VIEW; dz += VIEW) {
            BlockPos q = new BlockPos(px + dx, py, pz + dz);
            if (regions.add(StockedData.region(q))) lakes.add(data.lakeAt(level, q));
        }
        List<ZoneView> zones = new ArrayList<>();
        for (com.riverfishing.alife.Lake lake : lakes) {
            boolean pond = ponds.contains(lake);
            for (int i = 0; i < lake.zones.size(); i++) {
                com.riverfishing.alife.Lake.Zone z = lake.zones.get(i);
                if (z.ay == Integer.MIN_VALUE || Math.abs(z.ay - py) > Y_BAND) continue;
                double d = Math.hypot(z.ax - px, z.az - pz);
                if (d > VIEW) continue;
                if (diving && Math.abs(z.ay - py) > Y_BAND + 16) continue;
                // the wild zone over a claimed pond is the pond's to show
                if (!pond && PondData.isClaimed(level, new BlockPos(z.ax, z.ay, z.az))) continue;
                zones.add(new ZoneView(lake, z, i, d));
            }
        }
        zones.sort(Comparator.comparingDouble(ZoneView::dist));
        long tod = level.getOverworldClockTime() % 24000L;
        boolean edge = tod < 2000L || (tod > 11500L && tod < 13500L);   // dawn and dusk: the rise
        com.riverfishing.engine.TimeOfDay hourNow = com.riverfishing.engine.TimeOfDay.fromDayTime(level.getOverworldClockTime());
        boolean heat = hourNow == com.riverfishing.engine.TimeOfDay.DAY && !level.isRaining()
                && com.riverfishing.integration.SeasonProvider.getSeason(level) == com.riverfishing.engine.Season.SUMMER;
        List<ShoalPacket.Spot> out = new ArrayList<>();
        int budget = diving ? MAX_FISH_DIVING : MAX_FISH_LIVE;
        for (ZoneView zv : zones) {
            if (budget < 1) break;
            java.util.function.Predicate<String> spawning = AlifeData.conditions(level,
                    new BlockPos(zv.zone().ax, zv.zone().ay, zv.zone().az)).apply(zv.lake().hour).spawning();
            double[] bait = zv.lake().baitSpot(zv.index());
            List<ShoalPacket.Entry> fish = new ArrayList<>();
            byte lane = 0;
            for (com.riverfishing.alife.Lake.Agent a : zv.lake().agents) {
                if (a.zone != zv.index() || !a.alive() || lane > 7) continue;
                int before = fish.size();
                Look look = new Look(edge, hourNow, heat, diving, spawning, bait != null);
                live(zv.lake(), a, zv.zone(), lane, look, Math.min(budget - fish.size(), diving ? 40 : 24), fish);
                if (fish.size() > before) lane++;
            }
            if (fish.isEmpty()) continue;
            budget -= fish.size();
            BlockPos at = new BlockPos(zv.zone().ax, zv.zone().ay, zv.zone().az);
            double spook = SpookData.of(level).at(at, now);
            byte spookByte = (byte) Mth.clamp((int) Math.round(spook / SPOOK_GONE * 100.0), 0, 100);
            WaterBody body = WaterBodyCache.forLevel(level).get(level, at);
            float clear = clarity(level, body, at);
            if (diving) clear = Math.max(clear, 0.9f);
            out.add(new ShoalPacket.Spot(at, clear, (byte) 6, spookByte, fish,
                    bait != null, bait == null ? 0f : (float) bait[0], bait == null ? 0f : (float) bait[1]));
        }
        return out;
    }

    /** Every fish on screen at once when the player is under water — the abundance is the point. */
    private static final int MAX_FISH_DIVING = 700;

    /** What the moment looks like to the shoals: the hour, the heat, whether the viewer is diving, who spawns, the bait. */
    private record Look(boolean edge, com.riverfishing.engine.TimeOfDay time, boolean heat, boolean diving,
                        java.util.function.Predicate<String> spawning, boolean bait) {}

    /** §fish-world: the lurkers — they hang still in cover and strike. */
    private static final java.util.regex.Pattern AMBUSHERS = java.util.regex.Pattern.compile(
            ".*(pike|pickerel|muskellunge|gar|snakehead|wels|barracuda|grouper).*");

    /** One agent's fish, as entries of lane {@code lane}. */
    private static void live(com.riverfishing.alife.Lake lake, com.riverfishing.alife.Lake.Agent a,
                             com.riverfishing.alife.Lake.Zone zone, byte lane, Look look, int room,
                             List<ShoalPacket.Entry> out) {
        boolean edge = look.edge();
        FishProfile p = FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(a.sp.id()));
        if (p == null || room <= 0) return;
        int group = System.identityHashCode(a);
        RandomSource rng = RandomSource.create(group * 0x9E3779B97F4A7C15L);
        int n = look.diving()
                ? (a.heads != null ? Math.min(a.heads.size(), 40)
                   : a.fry ? Math.min(40, 5 + a.count / 3)
                   : a.trophy ? 1 : (int) Math.min(a.count, Mth.clamp(Math.round(4.0 * Math.sqrt(a.count)), 1, 40)))
                : (a.heads != null ? Math.min(a.heads.size(), 24)
                   : a.fry ? Math.min(12, 3 + a.count / 8)
                   : a.trophy ? 1 : (int) Mth.clamp(Math.round(2.2 * Math.sqrt(a.count)), 1, 16));
        n = Math.min(n, room);
        double mean = p.weightMeanSet ? p.weightMean : (p.weightMin + p.weightMax) / 2.0;
        boolean shoaling = a.fry || (n > 1 && (mean < SHOAL_UNDER_G || a.heads == null));
        boolean bottom = "bottom".equals(p.depthPref);
        boolean feeding = lake.feeding(a);
        int kind = ("predator".equals(p.group) || a.sp.predator() ? ShoalPacket.Entry.PREDATOR : 0)
                | (JUMPERS.contains(p.id.getPath()) && !a.fry ? ShoalPacket.Entry.JUMPER : 0)
                | (shoaling ? ShoalPacket.Entry.SHOALING : 0)
                | (feeding && bottom ? ShoalPacket.Entry.FEEDING : 0)
                | (feeding && !bottom && edge ? ShoalPacket.Entry.RISING : 0)
                | (lake.hunting(a) ? ShoalPacket.Entry.HUNTING : 0)
                | (a.fry ? ShoalPacket.Entry.FRY : 0);
        // §fish-world: how this fish lives, and what it is doing right now
        String path = p.id.getPath(), grp = p.group == null ? "" : p.group;
        boolean predator = a.sp.predator() || "predator".equals(p.group);
        boolean dweller = !a.fry && (com.riverfishing.fish.FishPose.isFlat(path) || "catfish".equals(grp)
                || "sturgeon".equals(grp) || "ray".equals(grp) || (bottom && mean >= 800));
        boolean ambush = !a.fry && predator && !dweller && AMBUSHERS.matcher(path).matches();
        boolean rest = !a.fry && (p.timeFactor(look.time()) < 0.8 || a.hunger < 0.12);
        boolean spawning = !a.fry && look.spawning().test(path) && com.riverfishing.alife.Lake.spawningGround(zone);
        boolean baited = look.bait() && feeding;
        kind |= (dweller ? ShoalPacket.Entry.BOTTOM : 0)
                | (ambush ? ShoalPacket.Entry.AMBUSH : 0)
                | (predator && !ambush && !dweller ? ShoalPacket.Entry.CHASER : 0)
                | (shoaling && !predator && !dweller ? ShoalPacket.Entry.SCHOOL : 0)
                | (rest && !spawning && !baited ? ShoalPacket.Entry.REST : 0)
                | (spawning ? ShoalPacket.Entry.SPAWNING : 0)
                | (baited ? ShoalPacket.Entry.BAITED : 0);
        int waterDepth = Math.max(1, zone.adepth);
        int baseDepth = a.fry ? Math.min(1, waterDepth - 1) : depthFor(p, waterDepth, rng);
        // the day shows in the depth: spawners up in the weed, feeders and dwellers and the resting on the
        // bottom, the open-water fish deeper in the heat of noon and higher in the night
        if (spawning) baseDepth = Math.min(1, waterDepth - 1);
        else if (dweller || baited || rest) baseDepth = Math.max(0, waterDepth - 1);
        else if (look.heat()) baseDepth = Math.min(waterDepth - 1, baseDepth + 1);
        else if (look.time() == com.riverfishing.engine.TimeOfDay.NIGHT) baseDepth = Math.max(0, baseDepth - 1);
        int basePhase = rng.nextInt(64);
        boolean koi = com.riverfishing.fish.Genome.isKoiId(p.id.getPath());
        for (int k = 0; k < n; k++) {
            int grams, pattern = 0;
            String variety = "";
            boolean trophy = a.trophy;
            if (a.heads != null) {
                com.riverfishing.alife.Life.Head h = a.heads.get(k);
                grams = (int) Math.round(h.weightG);
                net.minecraft.nbt.CompoundTag card = PondLife.record(h).getCompoundOrEmpty("Card");
                variety = card.getStringOr("Variety", "");
                pattern = com.riverfishing.fish.CatchCard.pattern(card);
                trophy = h.trophy;
            } else if (a.fry) {
                grams = (int) Math.max(1, Math.round(a.weightG));
            } else {
                grams = (int) Mth.clamp(a.weightG * (0.7 + rng.nextDouble() * 0.6), p.weightMin, p.weightMax);
            }
            if (koi && variety.isEmpty()) variety = "koi_" + com.riverfishing.fish.Genome.wildKoi(rng.nextDouble());
            int lengthCm = a.fry ? 2 : lengthOf(p, grams);
            int ph = shoaling ? (basePhase + rng.nextInt(9) - 4 + 64) % 64 : basePhase;
            int dd = Mth.clamp(baseDepth + (shoaling ? rng.nextInt(3) - 1 : 0), 0, Math.max(0, waterDepth - 1));
            byte age = (byte) Math.round(com.riverfishing.fish.FishMorph.ageFraction(p, grams) * 100);
            out.add(new ShoalPacket.Entry(p.id, grams, Math.max(1, lengthCm), age, (byte) dd, lane, (byte) ph,
                    kind | (trophy ? ShoalPacket.Entry.TROPHY : 0), group, variety, pattern));
        }
    }

    /** Length from weight, the cube-root law anchored to the species range. */
    private static int lengthOf(FishProfile p, int grams) {
        double lo = Math.cbrt(Math.max(1.0, p.weightMin)), hi = Math.cbrt(Math.max(p.weightMin + 1, p.weightMax));
        double frac = Mth.clamp((Math.cbrt(Math.max(1.0, grams)) - lo) / Math.max(1e-6, hi - lo), 0.0, 1.2);
        return (int) Math.round(p.lengthMin + frac * (p.lengthMax - p.lengthMin));
    }

    /**
     * The water surface inside one cell, or null. Four heightmap probes rather than a scan: the heightmap
     * already knows the top block of every column, so this is O(1) per probe and costs nothing to repeat
     * every couple of seconds.
     */
    private static BlockPos surfaceInCell(ServerLevel level, int cx, int cz, int py) {
        int bx = cx * CELL, bz = cz * CELL;
        BlockPos best = null; int bestDepth = 0;
        for (int i = 0; i < 4; i++) {
            int x = bx + ((i & 1) == 0 ? CELL / 4 : CELL - CELL / 4);
            int z = bz + ((i & 2) == 0 ? CELL / 4 : CELL - CELL / 4);
            if (!level.hasChunkAt(x, z)) continue;          // never force-load for scenery
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (Math.abs(top - py) > Y_BAND) continue;
            BlockPos p = new BlockPos(x, top, z);
            if (level.getFluidState(p).isEmpty()) continue;
            // §shoal-deep: the deepest of the four probes, not the first — the fish sit where the water is.
            int depth = FishingManager.measureDepth(level, p);
            if (depth > bestDepth) { bestDepth = depth; best = p; }
        }
        return best;
    }

    /**
     * The species this water holds — asked of the bite engine itself, not worked out again here.
     *
     * <p>This function used to re-derive the weighting from the same JSON: water type, depth, width,
     * season, time, weather. It looked equivalent and was not. It silently missed the maximum depth and
     * width gates, the biome range, and above all §community — the per-water species set that decides
     * that THIS lake simply does not hold grass carp — so the shoal drew fish the fish finder did not
     * list and that stocking refused to settle. {@code environmentScore} is the one function that owns
     * that answer (it is also what the finder calls), and it costs less than the copy did.
     *
     * <p>Pressure is applied on top, so a hammered swim visibly empties: {@code surplus} is negative
     * where the stock has been fished down.
     */
    /** Below this bite rate a species is RARE: shown in passes, not as furniture (§shoal-rare). */
    private static final double RARE_BASE = 0.3;
    /** ...and a pass is one hour in this many, per cell. */
    private static final int RARE_ONE_IN = 4;

    private static Pool poolFor(BiteContext env, BlockPos surface, FishingPressureData pressure, long now,
                                long hour) {
        int sx = SectionPos.blockToSectionCoord(surface.getX());
        int sz = SectionPos.blockToSectionCoord(surface.getZ());
        Map<Identifier, Double> weights = new HashMap<>();
        double total = 0;
        for (FishProfile p : FishProfileManager.get().all()) {
            double w = BiteEngine.environmentScore(p, env);
            if (w <= 1e-4) continue;
            // §shoal-honest: the shoal was weighted by how well the water SUITS a species and never by
            // how often it TAKES. A beluga at base 0.1 and a roach at 1.0 came up equally often, so
            // the water was full of the fish you never catch and empty of the ones you do. The bite
            // is base × environment; so is the shoal now.
            w *= p.base;
            // §shoal-rare: a species that takes once in ten stays rare on the screen too — it passes
            // through one hour in four, seeded per cell, so "I saw a beluga" is an event and not a
            // permanent fixture that cheapens the catch.
            if (p.base < RARE_BASE) {
                RandomSource gate = RandomSource.create(surface.asLong() * 7L + hour * 13L + p.id.hashCode());
                if (gate.nextInt(RARE_ONE_IN) != 0) continue;
            }
            // §shoal-pressure: fewer fish where the fishing has been heavy.
            w *= Mth.clamp(1.0 + pressure.surplusAround(sx, sz, p.id.getPath(), now), 0.05, 2.5);
            if (w > 1e-4) {
                weights.put(p.id, w);
                total += w;
            }
        }
        return new Pool(new ArrayList<>(weights.keySet()), weights, total);
    }

    /** One shoal drawn from a pool: groups for the small species, singles for the big ones. */
    private static List<ShoalPacket.Entry> pick(Pool pool, int waterDepth, int cap, int minLen,
                                               RandomSource rng) {
        // How busy the water looks follows the total weight — rich water is crowded, poor water bare.
        int want = (int) Mth.clamp(Math.round(Math.sqrt(pool.total) * 2.2), 2, cap);
        List<ShoalPacket.Entry> out = new ArrayList<>(want);
        byte lane = 0;
        int tries = 0;
        while (out.size() < want && lane < 6 && tries++ < 24) {
            Identifier pickId = pool.pick(rng);
            if (pickId == null) break;
            FishProfile p = FishProfileManager.get().byId(pickId);
            if (p == null) continue;
            double mean = p.weightMeanSet ? p.weightMean : (p.weightMin + p.weightMax) / 2.0;
            // §shoal-groups: bleak and roach move in numbers; a pike does not. Sharing a lane puts the
            // group on one circuit, and near-identical phases keep it together instead of strung out.
            boolean shoaling = mean < SHOAL_UNDER_G;
            // §shoal-far: a lone small fish is not worth sending to a far cell — the client could not
            // draw it big enough to see. A SCHOOL of them is: you cannot make out one bleak from the
            // far bank, but you can make out a shimmer of eight. So the length cut applies to loners
            // only, and the small species reach the far water as the groups they actually move in.
            boolean far = p.lengthMax < minLen;
            if (far && !shoaling) continue;
            int n = Math.min(shoaling ? (far ? 5 + rng.nextInt(4) : 3 + rng.nextInt(4)) : 1, want - out.size());
            byte kind = (byte) (("predator".equals(p.group) ? ShoalPacket.Entry.PREDATOR : 0)
                    | (JUMPERS.contains(p.id.getPath()) ? ShoalPacket.Entry.JUMPER : 0)
                    | (shoaling ? ShoalPacket.Entry.SHOALING : 0));
            int basePhase = rng.nextInt(64);
            int baseDepth = depthFor(p, waterDepth, rng);

            for (int k = 0; k < n; k++) {
                // Everyday fish, not trophies: the shoal is the population, not the record book.
                int grams = (int) Mth.clamp(mean * (0.55 + rng.nextDouble() * 0.9), p.weightMin, p.weightMax);
                double frac = (grams - p.weightMin) / Math.max(1.0, p.weightMax - p.weightMin);
                int lengthCm = (int) Math.round(p.lengthMin + frac * (p.lengthMax - p.lengthMin));
                int ph = shoaling ? (basePhase + rng.nextInt(9) - 4 + 64) % 64 : basePhase;
                // Never past the bottom: a fish nudged into the mud is a fish drawn inside the terrain.
                int dd = Mth.clamp(baseDepth + (shoaling ? rng.nextInt(3) - 1 : 0), 0, Math.max(0, waterDepth - 1));
                // §morph: the same age figure a caught fish carries, so a shoal of small pale fish and
                // one dark old one look like what they are.
                byte age = (byte) Math.round(com.riverfishing.fish.FishMorph.ageFraction(p, grams) * 100);
                out.add(new ShoalPacket.Entry(pickId, grams, Math.max(1, lengthCm), age,
                        (byte) dd, lane, (byte) ph, kind));
            }
            lane++;
        }
        return out;
    }

    /** Ниже этой массы вид выходит СТАЕЙ, а не одиночкой. */
    private static final int SHOAL_UNDER_G = 900;

    /**
     * §shoal-jump: the species that clear the water. Carp roll, salmon and trout leap, asp and taimen
     * hit the surface chasing. A name here that is not a species is a jumper nobody will ever see, so
     * the set is checked against the profiles once and the strays are logged.
     */
    private static final java.util.Set<String> JUMPERS = jumpers();

    private static java.util.Set<String> jumpers() {
        String[] want = {"carp", "mirror_carp", "wild_carp", "grass_carp", "silver_carp", "salmon",
                "rainbow_trout", "trout", "char", "pink_salmon", "grayling", "asp", "taimen", "tarpon", "sturgeon", "beluga"};
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String s : want) {
            if (java.util.Arrays.asList(com.riverfishing.registry.ModItems.FISH_SPECIES).contains(s)) out.add(s);
            else com.riverfishing.RiverFishing.LOGGER.debug("§shoal-jump: no species '{}', skipped", s);
        }
        return out;
    }

    /** A species pool for one kind of water, reused across every cell that answers the same. */
    private record Pool(List<Identifier> ids, Map<Identifier, Double> weights, double total) {
        Identifier pick(RandomSource rng) {
            double r = rng.nextDouble() * total;
            for (Identifier id : ids) {
                r -= weights.getOrDefault(id, 0.0);
                if (r <= 0) return id;
            }
            return ids.isEmpty() ? null : ids.get(ids.size() - 1);
        }
    }

    /** Blocks under the surface, from the species' own depth preference. */
    private static int depthFor(FishProfile p, int waterDepth, RandomSource rng) {
        int max = Math.max(1, waterDepth - 1);
        return switch (p.depthPref == null ? "mid" : p.depthPref) {
            case "surface" -> rng.nextInt(2);
            case "bottom" -> Math.max(0, max - rng.nextInt(2));
            default -> Mth.clamp(max / 2 + rng.nextInt(2) - 1, 0, max);
        };
    }

    /**
     * How well this water shows what it holds, 0..1. Muddy water, rain and night hide their fish — the
     * same factors an angler actually reads. Depth deliberately does NOT enter into it: a player who
     * bothers to look should be able to make out what is sitting on the bottom.
     */
    private static float clarity(ServerLevel level, WaterBody body, BlockPos surface) {
        float c = switch (body.type()) {
            case PUDDLE, POND -> 1.0f;
            case LAKE -> 0.9f;
            case RIVER -> 0.8f;
            case SWAMP -> 0.5f;
            case SEA -> 0.75f;
            default -> 0.0f;
        };
        if (level.isThundering()) c *= 0.45f;
        else if (level.isRaining()) c *= 0.7f;
        // Sky light at the surface: dusk and night dim the water without needing a separate time check.
        c *= 0.35f + 0.65f * (level.getBrightness(LightLayer.SKY, surface.above()) / 15f);
        return Mth.clamp(c, 0f, 1f);
    }
}
