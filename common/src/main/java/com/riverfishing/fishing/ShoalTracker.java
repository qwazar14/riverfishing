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
import net.minecraft.resources.ResourceLocation;
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
    private static final int WANT_NEAR = 7, WANT_MID = 4, WANT_FAR = 2;
    /** Length below which a fish is not worth sending at all from {@link #MID} / {@link #FAR} away. */
    private static final int SEE_MID_CM = 35, SEE_FAR_CM = 90;
    /** Total across all shoals — the render cost is per sprite, and this is the only place to bound it. */
    private static final int MAX_FISH_TOTAL = 96;
    /** A lake 30 blocks below the clifftop you are standing on is not the water you are looking at. */
    private static final int Y_BAND = 20;
    /** Spook at which a patch shows no fish at all; below it the shoal thins in proportion. */
    private static final double SPOOK_GONE = 0.55;

    /** Last shoal sent, so an unchanged view is not re-sent and walking away clears exactly once. */
    private static final Map<UUID, String> LAST = new HashMap<>();

    private ShoalTracker() {}

    public static void tick(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if ((now + sp.getId()) % PERIOD != 0) return;         // stagger players across ticks

        // The in-game hour is the shoal's clock: it holds still while you fish, and has moved on when you
        // come back. floorDiv, not /, so it does not jitter around midnight of a negative game time.
        long hour = Math.floorDiv(now, 1000L);
        List<ShoalPacket.Spot> spots = scan(level, sp, now, hour);

        if (spots.isEmpty()) {
            if (LAST.remove(sp.getUUID()) != null) ModNetwork.toPlayer(sp, ShoalPacket.empty());
            return;
        }
        StringBuilder sig = new StringBuilder(24).append(hour);
        for (ShoalPacket.Spot s : spots) {
            sig.append('|').append(s.centre().asLong()).append(':').append(s.fish().size())
                    .append(':').append(Math.round(s.clarity() * 20));
        }
        String key = sig.toString();
        if (key.equals(LAST.get(sp.getUUID()))) return;
        LAST.put(sp.getUUID(), key);
        ModNetwork.toPlayer(sp, new ShoalPacket(spots));
    }

    /** Forget a player's shoal so their next tick re-sends it. */
    public static void forget(ServerPlayer sp) {
        LAST.remove(sp.getUUID());
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
            Pool pool = poolFor(env, surface, pressure, now);
            if (pool.total <= 0) continue;

            // §spook: frightened fish leave, and that is the only readout this mechanic ever gets — you
            // watch the water empty. Above SPOOK_GONE the patch shows nothing at all.
            double spook = SpookData.of(level).at(surface, now);
            if (spook >= SPOOK_GONE) continue;
            want = (int) Math.round(want * (1.0 - spook / SPOOK_GONE));
            if (want < 1) continue;

            RandomSource rng = RandomSource.create(surface.asLong() * 31L + hour);
            List<ShoalPacket.Entry> fish = pick(pool, env.waterDepth, Math.min(want, budget), minLen, rng);
            if (fish.isEmpty()) continue;
            budget -= fish.size();
            // Circuits have to stay inside the cell, or two neighbouring shoals swim through each other
            // and the outer laps cross the bank.
            byte spread = (byte) Mth.clamp((int) Math.round(Math.min(body.width() * 0.4, CELL / 2.0)), 1, 5);
            out.add(new ShoalPacket.Spot(surface, clarity(level, body, surface), spread, fish));
        }
        return out;
    }

    /**
     * The water surface inside one cell, or null. Four heightmap probes rather than a scan: the heightmap
     * already knows the top block of every column, so this is O(1) per probe and costs nothing to repeat
     * every couple of seconds.
     */
    private static BlockPos surfaceInCell(ServerLevel level, int cx, int cz, int py) {
        int bx = cx * CELL, bz = cz * CELL;
        for (int i = 0; i < 4; i++) {
            int x = bx + ((i & 1) == 0 ? CELL / 4 : CELL - CELL / 4);
            int z = bz + ((i & 2) == 0 ? CELL / 4 : CELL - CELL / 4);
            if (!level.hasChunkAt(x, z)) continue;          // never force-load for scenery
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (Math.abs(top - py) > Y_BAND) continue;
            BlockPos p = new BlockPos(x, top, z);
            if (level.getFluidState(p).isEmpty()) continue;
            return p;
        }
        return null;
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
    private static Pool poolFor(BiteContext env, BlockPos surface, FishingPressureData pressure, long now) {
        int sx = SectionPos.blockToSectionCoord(surface.getX());
        int sz = SectionPos.blockToSectionCoord(surface.getZ());
        Map<ResourceLocation, Double> weights = new HashMap<>();
        double total = 0;
        for (FishProfile p : FishProfileManager.get().all()) {
            double w = BiteEngine.environmentScore(p, env);
            if (w <= 1e-4) continue;
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
            ResourceLocation pickId = pool.pick(rng);
            if (pickId == null) break;
            FishProfile p = FishProfileManager.get().byId(pickId);
            if (p == null) continue;
            // Too small to make out from where the player is standing: do not spend a lane, or bandwidth,
            // on a fish the client would only throw away again.
            if (p.lengthMax < minLen) continue;
            double mean = p.weightMeanSet ? p.weightMean : (p.weightMin + p.weightMax) / 2.0;

            // §shoal-groups: bleak and roach move in numbers; a pike does not. Sharing a lane puts the
            // group on one circuit, and near-identical phases keep it together instead of strung out.
            boolean shoaling = mean < SHOAL_UNDER_G;
            int n = Math.min(shoaling ? 3 + rng.nextInt(4) : 1, want - out.size());
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
                        (byte) dd, lane, (byte) ph));
            }
            lane++;
        }
        return out;
    }

    /** Ниже этой массы вид выходит СТАЕЙ, а не одиночкой. */
    private static final int SHOAL_UNDER_G = 900;

    /** A species pool for one kind of water, reused across every cell that answers the same. */
    private record Pool(List<ResourceLocation> ids, Map<ResourceLocation, Double> weights, double total) {
        ResourceLocation pick(RandomSource rng) {
            double r = rng.nextDouble() * total;
            for (ResourceLocation id : ids) {
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
