package com.riverfishing.fishing;

import com.riverfishing.groundbait.GroundbaitMix;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-level store of fed spots (§5). Each spot is a 3x3 column zone holding two separate things:
 *
 * <ul>
 *   <li><b>freshness</b> — how much groundbait is still down there. Feeding tops it up, time takes it
 *       away, and how long it lasts depends on what the mix is made of.</li>
 *   <li><b>satiety</b> (§groundbait-mix, 0.8.0) — how FULL the fish over it are. This is the half the
 *       spot never had, and the reason the feature exists: groundbait is food as well as an attractant,
 *       so a rich mix pulls fish in and then stops them biting.</li>
 * </ul>
 *
 * <p>The two pull against each other, which is the decision the player now has. Everywhere else in this
 * mod more resource is better; here a rich mix on a cold or hammered water is worse than a lean one.
 */
public class FeedZoneData extends SavedData {
    public static final String NAME = "riverfishing_feed_zones";

    /** Fine dust clouds and washes out; grain lies on the bottom. Halflife scales with the fraction. */
    private static final long HALFLIFE_BASE_TICKS = 1800L;    // ~90 s for pure powder
    private static final long HALFLIFE_COARSE_TICKS = 3600L;  // + up to another 3 min for pure grain
    private static final long LIFETIME_BASE_TICKS = 3600L;    // ~3 min: the old hard stop, now the floor
    private static final long LIFETIME_COARSE_TICKS = 10800L; // coarse feed keeps working for ~12 min

    /**
     * Fish fill up fast and get hungry slowly, so satiety outlives the feed that caused it. Two minutes
     * to halve: a mistake costs a pause, not an evening — the author's call when this was designed.
     */
    private static final long SATIETY_HALFLIFE_TICKS = 2400L;

    private static final double FEED_AMOUNT = 0.6;
    private static final double EDGE_FACTOR = 0.6;     // outer ring of the 3x3 is weaker

    /**
     * How hard a full spot bites. At satiety 1.0 the bite is cut to 30% — badly, but never to nothing,
     * because a spot you can never recover is a spot the player just walks away from.
     */
    public static final double SATIETY_BITE_COST = 0.7;

    /** §groundbait-particles: the cloud a spot leaves on the water is the colour of what went in. */
    public static DustParticleOptions particleFor(int rgb) {
        // §26.1: DustParticleOptions takes a packed 0xRRGGBB int, which is what a mix already carries.
        return new DustParticleOptions(rgb, 1.0f);
    }

    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now; the CompoundTag
    // round-trip below reuses the existing save()/load() bodies unchanged.
    private static final net.minecraft.world.level.saveddata.SavedDataType<FeedZoneData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("riverfishing", NAME.replace("riverfishing_", "")),
                    FeedZoneData::new,
                    net.minecraft.nbt.CompoundTag.CODEC.xmap(t -> FeedZoneData.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);

    private final Map<Long, Zone> zones = new HashMap<>();

    public static FeedZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Throw groundbait at a spot, creating or refreshing its zone.
     *
     * @param appetite 0..1, how much the fish here can eat right now — cold water, a hammered chunk.
     *     Satiety is the feed DIVIDED by it, so the same jar that barely registers in a warm summer
     *     lake stuffs the spot solid under ice. That is the whole temperature gate, and it falls out of
     *     one division rather than a special case.
     */
    public void feed(BlockPos center, GroundbaitMix mix, long gameTime, double appetite) {
        long k = key(center.getX(), center.getZ());
        Zone zone = zones.get(k);
        double freshness = zone == null ? 0.0 : zone.freshness(gameTime);
        double satiety = zone == null ? 0.0 : zone.satiety(gameTime);

        // Floor the divisor rather than the result: without it a frozen lake divides by nearly zero.
        double gain = FEED_AMOUNT * mix.nutrition() / Math.max(0.15, appetite);

        zones.put(k, new Zone(center.getX(), center.getY(), center.getZ(), gameTime,
                Math.min(1.0, freshness + FEED_AMOUNT), Math.min(1.0, satiety + gain),
                mix.category(), mix.nutrition(), mix.fraction(), mix.rgb()));
        setDirty();
    }

    /** Faintly tint active fed spots so the player can see where they baited (#7). */
    public void emitParticles(ServerLevel level, BlockPos near, long gameTime) {
        int range2 = 48 * 48;
        for (Zone z : zones.values()) {
            double freshness = z.freshness(gameTime);
            if (freshness <= 0.05) continue;
            double dx = z.x + 0.5 - near.getX();
            double dz = z.z + 0.5 - near.getZ();
            if (dx * dx + dz * dz > range2) continue;
            int count = 1 + (int) (freshness * 3);
            level.sendParticles(particleFor(z.rgb), z.x + 0.5, z.y + 1.05, z.z + 0.5,
                    count, 0.38, 0.04, 0.38, 0.0);
        }
    }

    /** Query the best fed zone covering a cast position. */
    public Query query(BlockPos castPos, long gameTime) {
        Query best = Query.NONE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Zone zone = zones.get(key(castPos.getX() + dx, castPos.getZ() + dz));
                if (zone == null) continue;
                double freshness = zone.freshness(gameTime);
                if (freshness <= 0.01) continue;
                double posFactor = (dx == 0 && dz == 0) ? 1.0 : EDGE_FACTOR;
                double effective = freshness * posFactor;
                if (effective > best.freshness) {
                    // Satiety is NOT weakened at the edge of the zone: the fish are equally full
                    // wherever you cast into the patch, they are simply less interested near its rim.
                    best = new Query(true, effective, zone.category, zone.satiety(gameTime),
                            zone.fraction, zone.rgb);
                }
            }
        }
        return best;
    }

    // ---- persistence ----

    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Zone z : zones.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", z.x);
            t.putInt("y", z.y);
            t.putInt("z", z.z);
            t.putLong("fed", z.fedTime);
            t.putDouble("potency", z.potency);
            t.putString("category", z.category);
            t.putDouble("satiety", z.satietyBase);
            t.putDouble("nutrition", z.nutrition);
            t.putDouble("fraction", z.fraction);
            t.putInt("rgb", z.rgb);
            list.add(t);
        }
        tag.put("zones", list);
        return tag;
    }

    public static FeedZoneData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        FeedZoneData data = new FeedZoneData();
        ListTag list = tag.getListOrEmpty("zones");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            String category = t.getStringOr("category", "");
            // A world saved before 0.8.0 has a category and nothing else. Its preset supplies the rest,
            // so an old fed spot keeps behaving as the groundbait that made it — no reset, no surprise.
            GroundbaitMix preset = GroundbaitMix.PRESETS.getOrDefault(category,
                    GroundbaitMix.PRESETS.get("powder"));
            // A world saved before 0.8.0 simply has no nutrition/fraction/rgb, and the OR-defaults
            // hand back the preset's numbers, so an old fed spot keeps behaving as what made it.
            Zone z = new Zone(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0),
                    t.getLongOr("fed", 0L), t.getDoubleOr("potency", 0d), t.getDoubleOr("satiety", 0d),
                    category,
                    t.getDoubleOr("nutrition", preset.nutrition()),
                    t.getDoubleOr("fraction", preset.fraction()),
                    t.getIntOr("rgb", preset.rgb()));
            data.zones.put(key(z.x, z.z), z);
        }
        return data;
    }

    private record Zone(int x, int y, int z, long fedTime, double potency, double satietyBase,
                        String category, double nutrition, double fraction, int rgb) {

        /** Coarse feed lies where it landed; dust is gone in minutes. */
        long lifetime() {
            return LIFETIME_BASE_TICKS + (long) (LIFETIME_COARSE_TICKS * Mth.clamp(fraction, 0, 1));
        }

        double freshness(long now) {
            double elapsed = Math.max(0, now - fedTime);
            if (elapsed > lifetime()) return 0.0;
            double halflife = HALFLIFE_BASE_TICKS + HALFLIFE_COARSE_TICKS * Mth.clamp(fraction, 0, 1);
            return Mth.clamp(potency * Math.pow(0.5, elapsed / halflife), 0.0, 1.0);
        }

        /**
         * Fullness outlives the feed on purpose — the fish are still digesting after the last crumb has
         * washed away, which is exactly why dumping a rich mix and casting straight in does not work.
         */
        double satiety(long now) {
            double elapsed = Math.max(0, now - fedTime);
            return Mth.clamp(satietyBase * Math.pow(0.5, elapsed / (double) SATIETY_HALFLIFE_TICKS),
                    0.0, 1.0);
        }
    }

    /** The fed-spot result handed to the bite context. */
    public record Query(boolean inZone, double freshness, String category, double satiety,
                        double fraction, int rgb) {
        public static final Query NONE = new Query(false, 0.0, null, 0.0, 0.0, 0xFFFFFF);
    }
}
