package com.riverfishing.fishing;

import com.riverfishing.engine.BiteContext;
import com.riverfishing.engine.BiteEngine;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import com.riverfishing.water.WaterType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * §fly-3 (0.10.0): the rising fish — the thing a fly angler actually fishes to.
 *
 * <p>You do not cast at random water: you watch for a ring where a fish came up and took something off
 * the top, and you put the fly there. So while a fly rod is in the hand the water in front of the angler
 * shows feeding fish, six to eighteen blocks out, three at a time, each holding its lie for fifteen to
 * twenty-five seconds and coming up again and again.
 *
 * <p>Two kinds, and they read differently across the water:
 * <ul>
 *   <li>a <b>sip</b> — a small ring and a quiet kiss: a fish picking insects off a calm surface;</li>
 *   <li>a <b>slash</b> — a wide ring, spray and a loud smack: something hunting.</li>
 * </ul>
 *
 * <p>A fly that lands (or drifts) within {@link #RISE_HIT_RADIUS} blocks of one is <em>on that fish</em>,
 * and it is already interested. Casting into empty water still fishes perfectly well; the rise is the
 * shortcut, and reading the water for it is the craft.
 */
public final class FlyRises {
    /** How close the fly has to be to a ring to be fishing to that fish. */
    public static final double RISE_HIT_RADIUS = 2.0;
    /** Blocks out from the angler a fish will show itself. */
    public static final double MIN_REACH = 6.0, MAX_REACH = 18.0;
    private static final int MAX_RISES = 3;
    private static final int LIFE_MIN = 300, LIFE_SPREAD = 200;
    private static final int SPAWN_GAP_MIN = 50, SPAWN_GAP_SPREAD = 90;
    /** One rise in three is a fish hunting rather than sipping. */
    private static final int SLASH_ONE_IN = 3;

    public static final class Rise {
        public final BlockPos pos;
        public final ResourceLocation species;
        /** A slashing rise: a hunting fish, and a streamer or a big fly is what it is looking for. */
        public final boolean slash;
        public final long until;
        long nextRing;

        Rise(BlockPos pos, ResourceLocation species, boolean slash, long until, long nextRing) {
            this.pos = pos;
            this.species = species;
            this.slash = slash;
            this.until = until;
            this.nextRing = nextRing;
        }
    }

    private static final Map<UUID, List<Rise>> RISES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SPAWN = new HashMap<>();

    private FlyRises() {}

    /** Once a second while a fly rod is in the hand: expire, ring, and now and then a new fish comes up. */
    public static void tick(ServerLevel level, ServerPlayer sp, long now) {
        List<Rise> list = RISES.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
        list.removeIf(r -> now >= r.until || !level.getFluidState(r.pos).is(net.minecraft.tags.FluidTags.WATER));
        for (Rise r : list) {
            if (now >= r.nextRing) {
                ring(level, r.pos, r.slash, level.getRandom());
                r.nextRing = now + (r.slash ? 50 : 40) + level.getRandom().nextInt(40);
            }
        }
        long next = NEXT_SPAWN.getOrDefault(sp.getUUID(), 0L);
        if (list.size() < MAX_RISES && now >= next) {
            NEXT_SPAWN.put(sp.getUUID(), now + SPAWN_GAP_MIN + level.getRandom().nextInt(SPAWN_GAP_SPREAD));
            Rise r = spawn(level, sp, now);
            if (r != null) {
                list.add(r);
                ring(level, r.pos, r.slash, level.getRandom());
            }
        }
    }

    /** The rise within reach of a spot, taken off the water (the fish is yours now), or null. */
    public static Rise take(ServerPlayer sp, BlockPos at) {
        List<Rise> list = RISES.get(sp.getUUID());
        if (list == null) return null;
        Rise best = null;
        double bestD = RISE_HIT_RADIUS * RISE_HIT_RADIUS;
        for (Rise r : list) {
            double d = r.pos.distSqr(at);
            if (d <= bestD) { bestD = d; best = r; }
        }
        if (best != null) list.remove(best);
        return best;
    }

    public static void forget(UUID uuid) {
        RISES.remove(uuid);
        NEXT_SPAWN.remove(uuid);
    }

    /** A fish comes up somewhere in front of the angler, on open water. */
    private static Rise spawn(ServerLevel level, ServerPlayer sp, long now) {
        RandomSource rng = level.getRandom();
        net.minecraft.world.phys.Vec3 look = sp.getLookAngle();
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        double base = hl < 1e-3 ? rng.nextDouble() * Math.PI * 2 : Math.atan2(look.z, look.x);
        boolean slash = rng.nextInt(SLASH_ONE_IN) == 0;
        for (int i = 0; i < 6; i++) {
            double a = base + (rng.nextDouble() - 0.5) * Math.toRadians(100);
            double d = MIN_REACH + rng.nextDouble() * (MAX_REACH - MIN_REACH);
            double x = sp.getX() + Math.cos(a) * d, z = sp.getZ() + Math.sin(a) * d;
            BlockPos p = FishingManager.findWaterColumn(level, x, sp.getEyeY() + 2.0, z);
            if (p == null || !level.getBlockState(p.above()).isAir()) continue;
            WaterBody body = WaterBodyCache.forLevel(level).get(level, p);
            if (body == null || body.type() == WaterType.NONE) continue;
            ResourceLocation species = pick(level, p, body, slash, rng);
            if (species == null) continue;
            return new Rise(p, species, slash, now + LIFE_MIN + rng.nextInt(LIFE_SPREAD), now + 20);
        }
        return null;
    }

    /** The species feeding here: the engine's own weights at this spot, tilted to what is feeding up top. */
    private static ResourceLocation pick(ServerLevel level, BlockPos p, WaterBody body, boolean slash, RandomSource rng) {
        BiteContext env = FishingManager.environmentAt(level, p, body);
        List<FishProfile> ids = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        double total = 0;
        for (FishProfile pr : FishProfileManager.get().all()) {
            double w = BiteEngine.environmentScore(pr, env);
            if (w <= 1e-4) continue;
            w *= pr.base * flyAppetite(pr.diet, slash);
            if (w <= 1e-4) continue;
            ids.add(pr);
            ws.add(w);
            total += w;
        }
        if (total <= 0) return null;
        double r = rng.nextDouble() * total;
        for (int i = 0; i < ids.size(); i++) {
            r -= ws.get(i);
            if (r <= 0) return ids.get(i).id;
        }
        return ids.get(ids.size() - 1).id;
    }

    /** Who comes up: an insect eater sips, a predator slashes — the ring tells you which you are looking at. */
    public static double flyAppetite(String diet, boolean slash) {
        if (diet == null) return 0.8;
        if (slash) {
            return switch (diet) {
                case "predator" -> 1.3;
                case "omnivore" -> 0.9;
                case "insectivore" -> 0.7;
                default -> 0.6;
            };
        }
        return switch (diet) {
            case "insectivore" -> 1.4;
            case "peaceful" -> 1.0;
            case "omnivore" -> 0.9;
            case "predator" -> 0.35;
            default -> 0.8;
        };
    }

    /** The ring on the water: a quiet kiss, or a hunting fish's smack. */
    private static void ring(ServerLevel level, BlockPos p, boolean slash, RandomSource rng) {
        double y = p.getY() + 0.92;
        int n = slash ? 10 : 6;
        double r = slash ? 0.55 : 0.3;
        for (int i = 0; i < n; i++) {
            double a = i * (Math.PI * 2.0 / n);
            double dx = Math.cos(a), dz = Math.sin(a);
            // count 0 turns the offsets into a velocity: the ring runs outward from the rise
            level.sendParticles(ParticleTypes.FISHING, p.getX() + 0.5 + dx * r, y, p.getZ() + 0.5 + dz * r,
                    0, dx, 0.0, dz, slash ? 0.22 : 0.15);
        }
        if (slash) {
            level.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, y + 0.1, p.getZ() + 0.5,
                    14, 0.3, 0.12, 0.3, 0.2);
            level.playSound(null, p, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                    0.8f, 0.95f + rng.nextFloat() * 0.2f);
        } else {
            level.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, y + 0.05, p.getZ() + 0.5,
                    2, 0.15, 0.0, 0.15, 0.0);
            level.playSound(null, p, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                    0.32f, 1.35f + rng.nextFloat() * 0.3f);
        }
    }
}
