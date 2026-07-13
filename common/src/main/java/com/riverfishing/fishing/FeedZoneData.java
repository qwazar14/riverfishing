package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-level store of fed spots (§5). Each spot is a 3x3 column zone with a "freshness" that
 * halves about every 10 minutes; re-feeding the same spot tops it back up.
 */
public class FeedZoneData extends SavedData {
    public static final String NAME = "riverfishing_feed_zones";

    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now; the CompoundTag
    // round-trip below reuses the existing save()/load() bodies unchanged.
    private static final net.minecraft.world.level.saveddata.SavedDataType<FeedZoneData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("riverfishing", NAME.replace("riverfishing_", "")),
                    FeedZoneData::new,
                    net.minecraft.nbt.CompoundTag.CODEC.xmap(t -> FeedZoneData.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);
    private static final long HALFLIFE_TICKS = 1800L;   // ~90 s: the spot fades over its lifetime
    private static final long MAX_LIFETIME_TICKS = 3600L; // hard stop: groundbait works ~3 minutes
    private static final double FEED_AMOUNT = 0.6;
    private static final double EDGE_FACTOR = 0.6;     // outer ring of the 3x3 is weaker

    /** §groundbait-particles: each groundbait leaves its own coloured cloud on the water. */
    public static DustParticleOptions particleFor(String category) {
        // §26.1: DustParticleOptions takes a packed 0xRRGGBB int now, not a Vector3f.
        int c = switch (category == null ? "" : category) {
            case "grain" -> 0xEBCC52;   // golden grain
            case "pellet" -> 0x8C663D;  // brown pellets
            case "cake" -> 0xA8944C;    // olive oil-cake
            default -> 0xE0DBB8;        // pale powder cloud
        };
        return new DustParticleOptions(c, 1.0f);
    }

    private final Map<Long, Zone> zones = new HashMap<>();

    public static FeedZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Throw groundbait at a spot, creating or refreshing its zone. */
    public void feed(BlockPos center, String category, long gameTime) {
        long k = key(center.getX(), center.getZ());
        Zone zone = zones.get(k);
        double current = zone == null ? 0.0 : zone.freshness(gameTime);
        Zone updated = new Zone(center.getX(), center.getY(), center.getZ(), gameTime,
                Math.min(1.0, current + FEED_AMOUNT), category);
        zones.put(k, updated);
        setDirty();
    }

    /** Faintly tint active fed spots red so the player can see where they baited (#7). */
    public void emitParticles(ServerLevel level, BlockPos near, long gameTime) {
        int range2 = 48 * 48;
        for (Zone z : zones.values()) {
            double freshness = z.freshness(gameTime);
            if (freshness <= 0.05) continue;
            double dx = z.x + 0.5 - near.getX();
            double dz = z.z + 0.5 - near.getZ();
            if (dx * dx + dz * dz > range2) continue;
            int count = 1 + (int) (freshness * 3);
            level.sendParticles(particleFor(z.category), z.x + 0.5, z.y + 1.05, z.z + 0.5,
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
                    best = new Query(true, effective, zone.category);
                }
            }
        }
        return best;
    }

    // ---- persistence ----

    // §26.1: SavedData has no save() anymore — kept as the body behind the TYPE codec above.
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
            Zone z = new Zone(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0), t.getLongOr("fed", 0L),
                    t.getDoubleOr("potency", 0d), t.getStringOr("category", ""));
            data.zones.put(key(z.x, z.z), z);
        }
        return data;
    }

    private record Zone(int x, int y, int z, long fedTime, double potency, String category) {
        double freshness(long now) {
            double elapsed = Math.max(0, now - fedTime);
            if (elapsed > MAX_LIFETIME_TICKS) return 0.0; // groundbait spent (§groundbait: ~3 min)
            return Math.max(0.0, Math.min(1.0, potency * Math.pow(0.5, elapsed / (double) HALFLIFE_TICKS)));
        }
    }

    /** The fed-spot result handed to the bite context. */
    public record Query(boolean inZone, double freshness, String category) {
        public static final Query NONE = new Query(false, 0.0, null);
    }
}
