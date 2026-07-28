package com.riverfishing.fishing;

import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.network.ModNetwork;
import com.riverfishing.network.ShoalPacket;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import com.riverfishing.water.WaterType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
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
 * <p>Three deliberate properties:
 * <ul>
 *   <li><b>Residents, not takers.</b> The shoal is chosen from habitat, season, time, weather and biome —
 *       NOT from your tackle. Swapping a lure must not make fish appear and vanish; that would read as a
 *       bug, and the honest question a player asks when looking at water is "what lives here".</li>
 *   <li><b>Stable.</b> The RNG is seeded from the chunk plus the in-game hour, so the shoal holds still
 *       and changes slowly. Reseeding per packet would make the water flicker.</li>
 *   <li><b>Cheap.</b> One pass every {@link #PERIOD} ticks per player, a cached water-body lookup, at most
 *       {@link #MAX_FISH} entries, and nothing at all when the player is not near water. The client
 *       animates them itself.</li>
 * </ul>
 */
public final class ShoalTracker {
    /** Two seconds. The shoal is ambient scenery, not a HUD — it does not need to be current. */
    private static final int PERIOD = 40;
    private static final int MAX_FISH = 6;
    /** How far to look for water. Beyond this the sprites would be too small to read anyway. */
    private static final int SEARCH_R = 10;

    /** Last shoal sent, so an unchanged spot is not re-sent and walking away clears exactly once. */
    private static final Map<UUID, String> LAST = new HashMap<>();

    private ShoalTracker() {}

    public static void tick(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if ((now + sp.getId()) % PERIOD != 0) return;         // stagger players across ticks

        BlockPos surface = nearestSurface(level, sp);
        if (surface == null) {
            if (LAST.remove(sp.getUUID()) != null) ModNetwork.toPlayer(sp, ShoalPacket.empty());
            return;
        }
        WaterBody body = WaterBodyCache.forLevel(level).get(level, surface);
        if (body == null || body.type() == WaterType.NONE) {
            if (LAST.remove(sp.getUUID()) != null) ModNetwork.toPlayer(sp, ShoalPacket.empty());
            return;
        }

        long chunkKey = new ChunkPos(surface).toLong();
        // The in-game hour is the shoal's clock: it holds still while you fish, and has moved on when you
        // come back. floorDiv, not /, so it does not jitter around midnight of a negative game time.
        long hour = Math.floorDiv(now, 1000L);
        RandomSource rng = RandomSource.create(chunkKey * 31L + hour);

        List<ShoalPacket.Entry> fish = pick(level, sp, body, surface, chunkKey, now, rng);
        float clarity = clarity(level, body, surface);
        ShoalPacket pkt = new ShoalPacket(surface, clarity, fish);

        String sig = surface.toString() + "|" + hour + "|" + fish.size() + "|" + Math.round(clarity * 20);
        if (sig.equals(LAST.get(sp.getUUID()))) return;
        LAST.put(sp.getUUID(), sig);
        ModNetwork.toPlayer(sp, pkt);
    }

    /** Forget a player's shoal so their next tick re-sends it. */
    public static void forget(ServerPlayer sp) {
        LAST.remove(sp.getUUID());
    }

    /**
     * The water surface the player is closest to, searched outward from their feet. Returns the block
     * whose top face is the surface — the same anchor the cast flow uses — or null if there is no water
     * within {@link #SEARCH_R}.
     */
    private static BlockPos nearestSurface(ServerLevel level, ServerPlayer sp) {
        BlockPos feet = sp.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -SEARCH_R; dx <= SEARCH_R; dx += 2) {
            for (int dz = -SEARCH_R; dz <= SEARCH_R; dz += 2) {
                double d = dx * dx + dz * dz;
                if (d > SEARCH_R * SEARCH_R || d >= bestD) continue;
                // Scan a short column around the player's own level: a lake below a cliff is not "here".
                for (int dy = 2; dy >= -4; dy--) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    if (level.getFluidState(p).isEmpty()) continue;
                    if (!level.getFluidState(p.above()).isEmpty()) continue;   // want the top block
                    best = p;
                    bestD = d;
                    break;
                }
            }
        }
        return best;
    }

    /**
     * The species this water body holds, weighted the way the bite engine weights habitat — but with no
     * tackle term at all. Pressure scales each species' presence, so a fished-out swim visibly empties.
     */
    private static List<ShoalPacket.Entry> pick(ServerLevel level, ServerPlayer sp, WaterBody body,
                                                BlockPos surface, long chunkKey, long now,
                                                RandomSource rng) {
        FishingPressureData pressure = FishingPressureData.get(level);
        var season = com.riverfishing.integration.SeasonProvider.getSeason(level);
        var time = com.riverfishing.engine.TimeOfDay.fromDayTime(level.getDayTime());
        var weather = level.isThundering() ? com.riverfishing.engine.Weather.THUNDER
                : (level.isRaining() ? com.riverfishing.engine.Weather.RAIN : com.riverfishing.engine.Weather.CLEAR);
        var biome = level.getBiome(surface);
        int depth = waterDepth(level, surface);

        Map<ResourceLocation, Double> weights = new HashMap<>();
        for (FishProfile p : FishProfileManager.get().all()) {
            // The profile's own accessors, so the shoal is weighted by exactly the same numbers the bite
            // engine reads — no second interpretation of the same JSON to drift out of step.
            double w = p.waterFactor(body.type());
            if (w <= 0) continue;
            if (depth < p.depthMin || (p.depthMax > 0 && depth > p.depthMax)) continue;
            if (body.width() < p.widthMin) continue;
            w *= p.base;
            if (season != null) w *= p.seasonFactor(season);
            w *= p.timeFactor(time);
            w *= p.weatherFactor(weather);
            // §shoal-pressure: a hammered swim shows fewer fish. surplus is negative when depleted.
            double surplus = pressure.surplusAround(SectionPos.blockToSectionCoord(surface.getX()),
                    SectionPos.blockToSectionCoord(surface.getZ()), p.id.getPath(), now);
            w *= Mth.clamp(1.0 + surplus, 0.05, 2.5);
            if (w > 1e-4) weights.put(p.id, w);
        }
        if (weights.isEmpty()) return List.of();

        // How many are visible at all follows the total weight: rich water looks busy, poor water empty.
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        int count = (int) Mth.clamp(Math.round(Math.sqrt(total) * 1.4), 1, MAX_FISH);

        List<ShoalPacket.Entry> out = new ArrayList<>(count);
        List<ResourceLocation> ids = new ArrayList<>(weights.keySet());
        for (int i = 0; i < count; i++) {
            ResourceLocation pickId = weightedPick(ids, weights, total, rng);
            if (pickId == null) break;
            FishProfile p = FishProfileManager.get().byId(pickId);
            if (p == null) continue;
            // A believable everyday fish, not a trophy: the shoal is the population, not the record book.
            double mean = p.weightMeanSet ? p.weightMean : (p.weightMin + p.weightMax) / 2.0;
            int grams = (int) Mth.clamp(mean * (0.55 + rng.nextDouble() * 0.9), p.weightMin, p.weightMax);
            byte d = (byte) Mth.clamp(depthFor(p, depth, rng), 0, 15);
            out.add(new ShoalPacket.Entry(pickId, grams, d, (byte) i, (byte) rng.nextInt(64)));
        }
        return out;
    }

    private static ResourceLocation weightedPick(List<ResourceLocation> ids,
                                                 Map<ResourceLocation, Double> weights,
                                                 double total, RandomSource rng) {
        double r = rng.nextDouble() * total;
        for (ResourceLocation id : ids) {
            r -= weights.getOrDefault(id, 0.0);
            if (r <= 0) return id;
        }
        return ids.isEmpty() ? null : ids.get(ids.size() - 1);
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

    private static int waterDepth(ServerLevel level, BlockPos surface) {
        int d = 0;
        BlockPos p = surface;
        while (d < 24 && !level.getFluidState(p).isEmpty()) {
            d++;
            p = p.below();
        }
        return d;
    }

    /**
     * How far you can see into this water, 0..1. Deep water hides its fish, rain and thunder churn the
     * surface, and night takes the light away — the same factors an angler actually reads.
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
        c *= 0.35f + 0.65f * (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, surface.above()) / 15f);
        return Mth.clamp(c, 0f, 1f);
    }

    /** Imported here rather than at the top so the one use is obvious. */
    private static final class SectionPos {
        static int blockToSectionCoord(int b) {
            return net.minecraft.core.SectionPos.blockToSectionCoord(b);
        }
    }
}
