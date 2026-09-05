package com.riverfishing.fishing;

import com.riverfishing.groundbait.GroundbaitMix;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-level store of fed spots (§5). Each spot is a 3x3 column zone holding the mix that was thrown in it
 * and how much of it is still down there.
 *
 * <p>§no-overfeeding (0.8.0): <b>a spot cannot be ruined by feeding it.</b> There is no fullness, no
 * cooldown and no punishment for throwing another jar — more feed is more fish, which is what feeding is
 * for and what every angler expects. What a jar cannot do is out-fish its own contents: how far a spot
 * can build up is capped by what is in the bowl, so the decision moved from "how much" (where it was a
 * trap) to "what of" (where it is a craft).
 *
 * <p>§last-thrown-wins: two jars of the SAME recipe add up in the water. Two different recipes do not —
 * the swim takes on whatever went in last, and the old mix is simply gone. Feeding worm over a corn swim
 * does not give you a worm-and-corn swim; it gives you a worm swim, which is the same thing the real
 * water does and the reason mixing your blend before you throw it matters.
 */
public class FeedZoneData extends SavedData {
    public static final String NAME = "riverfishing_feed_zones";

    /** Fine dust clouds and washes out; grain lies on the bottom. Halflife scales with the fraction. */
    private static final long HALFLIFE_BASE_TICKS = 1800L;    // ~90 s for pure dust
    private static final long HALFLIFE_COARSE_TICKS = 3600L;  // + up to another 3 min for pure grain
    private static final long LIFETIME_BASE_TICKS = 3600L;    // ~3 min: the old hard stop, now the floor
    private static final long LIFETIME_COARSE_TICKS = 10800L; // coarse feed keeps working for ~12 min

    /** How much one throw puts down. Two throws of a good mix take a swim to its ceiling. */
    private static final double FEED_AMOUNT = 0.6;
    private static final double EDGE_FACTOR = 0.6;     // outer ring of the 3x3 is weaker

    /**
     * §no-overfeeding: how strong a spot this mix can ever build, however many jars go in.
     *
     * <p>This is where the whole feature lives now that fullness is gone. Nutrition is the food on the
     * table and variety is how many different things are on it — the two reasons a real swim gathers a
     * crowd — and a bare jar off the shelf reaches under half of what a thought-out blend does. Nobody is
     * punished for feeding; they are simply out-fished by somebody who mixed.
     *
     * <pre>
     *   plain jar (0.50 nutrition, 1 part)      -> 0.475
     *   base + worm + maggot + barley           -> ~0.76
     *   base + four rich parts                  -> 0.94, and no legal 3x3 mix goes higher: the base is
     *                                              mandatory at 0.50 nutrition, so the mean never
     *                                              reaches the 1.00 the formula would need
     * </pre>
     */
    public static double ceiling(GroundbaitMix mix) {
        double variety = Mth.clamp((mix.variety() - 1) / 4.0, 0.0, 1.0);
        return Mth.clamp(0.25 + 0.45 * Mth.clamp(mix.nutrition(), 0.0, 1.0) + 0.30 * variety, 0.0, 1.0);
    }

    /** §groundbait-particles: the cloud a spot leaves on the water is the colour of what went in. */
    public static DustParticleOptions particleFor(int rgb) {
        return new DustParticleOptions(new org.joml.Vector3f(
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.0f);
    }

    private final Map<Long, Zone> zones = new HashMap<>();

    public static FeedZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FeedZoneData::load, FeedZoneData::new, NAME);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Throw groundbait at a spot, creating or refreshing its zone.
     *
     * <p>Same recipe as what is already down there: it adds up, towards the ceiling that recipe can
     * reach. Different recipe: it takes the swim over outright, at one throw's worth. That is not a
     * penalty for changing your mind — it is what a real bed of feed does, and it is why the blend is
     * decided in the grid rather than by dribbling four different jars in one after another.
     */
    public void feed(BlockPos center, GroundbaitMix mix, long gameTime) {
        long k = key(center.getX(), center.getZ());
        Zone old = zones.get(k);
        boolean sameMix = old != null && old.mix().signature().equals(mix.signature());
        double standing = sameMix ? old.freshness(gameTime) : 0.0;

        zones.put(k, new Zone(center.getX(), center.getY(), center.getZ(), gameTime,
                Math.min(ceiling(mix), standing + FEED_AMOUNT), mix, mix.rgb()));
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
                    best = new Query(true, effective, zone.mix());
                }
            }
        }
        return best;
    }

    // ---- persistence ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone z : zones.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", z.x);
            t.putInt("y", z.y);
            t.putInt("z", z.z);
            t.putLong("fed", z.fedTime);
            t.putDouble("potency", z.potency);
            t.putInt("rgb", z.rgb);
            // The PARTS are what is written down, not the numbers they stir into. One list, one read —
            // the same rule the jar itself follows, so a saved spot and a saved jar can never disagree.
            ListTag parts = new ListTag();
            for (GroundbaitMix.Part p : z.mix().parts()) {
                CompoundTag part = new CompoundTag();
                part.putString("id", p.id());
                part.putInt("n", p.spoons());
                parts.add(part);
            }
            t.put("parts", parts);
            list.add(t);
        }
        tag.put("zones", list);
        return tag;
    }

    public static FeedZoneData load(CompoundTag tag) {
        FeedZoneData data = new FeedZoneData();
        ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ListTag parts = t.getList("parts", Tag.TAG_COMPOUND);
            List<GroundbaitMix.Part> recipe = new ArrayList<>();
            for (int j = 0; j < parts.size(); j++) {
                CompoundTag part = parts.getCompound(j);
                recipe.add(new GroundbaitMix.Part(part.getString("id"), part.getInt("n")));
            }
            GroundbaitMix mix = GroundbaitMix.of(recipe);
            // A spot saved before 0.8.0 named a category and nothing else, and that category no longer
            // means anything. It is DROPPED rather than guessed at: a fed spot is twelve minutes of state
            // at the very most, so the honest cost of the wipe is one more throw of groundbait.
            if (mix == null) continue;
            Zone z = new Zone(t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getLong("fed"),
                    t.getDouble("potency"), mix, t.contains("rgb") ? t.getInt("rgb") : mix.rgb());
            data.zones.put(key(z.x, z.z), z);
        }
        return data;
    }

    private record Zone(int x, int y, int z, long fedTime, double potency, GroundbaitMix mix, int rgb) {

        /** Coarse feed lies where it landed; dust is gone in minutes. */
        long lifetime() {
            return LIFETIME_BASE_TICKS + (long) (LIFETIME_COARSE_TICKS * Mth.clamp(mix.fraction(), 0, 1));
        }

        double freshness(long now) {
            double elapsed = Math.max(0, now - fedTime);
            if (elapsed > lifetime()) return 0.0;
            double halflife = HALFLIFE_BASE_TICKS + HALFLIFE_COARSE_TICKS * Mth.clamp(mix.fraction(), 0, 1);
            return Mth.clamp(potency * Math.pow(0.5, elapsed / halflife), 0.0, 1.0);
        }
    }

    /**
     * The fed-spot result handed to the bite context.
     *
     * <p>It hands over the MIX, not a handful of numbers copied out of it. Every question the bite engine
     * asks about a fed spot — how coarse, how rich, what is on the menu, how varied — is a question about
     * the same object, so a reader that forgets to copy one field cannot exist.
     */
    public record Query(boolean inZone, double freshness, GroundbaitMix mix) {
        public static final Query NONE = new Query(false, 0.0, null);
    }
}
