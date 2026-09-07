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
import net.minecraft.resources.Identifier;
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
 * §fly-2 (0.10.0): the rising fish — the thing a fly angler actually fishes to.
 *
 * <p>On a real river you do not cast at random: you watch the water for a rise, a ring where a fish came
 * up and took something off the surface, and you put the fly a metre above it and let it drift down. So
 * while a fly rod is in the hand, the water in front of the angler shows feeding fish: a ring, a splash,
 * a sip — a few at a time, each one holding its lie for ten seconds or so and rising again and again.
 * A fly that lands (or drifts) within {@link #REACH} blocks of one is <em>on the fish</em>: the take comes
 * inside a couple of seconds, and it is that fish. A cast into empty water still fishes — the slow way.
 *
 * <p>The fish is drawn from the same weights the bite engine would use at that spot, tilted toward what
 * eats insects, so a rise is a promise the engine can keep. Everything here is server-side; the rings are
 * particles, so everyone on the bank sees the same fish feeding.
 */
public final class FlyRises {
    /** How close the fly has to land to a rise to be on that fish. */
    public static final double REACH = 2.5;
    private static final int MAX_RISES = 3;
    private static final int LIFE_MIN = 240, LIFE_SPREAD = 200;
    private static final int SPAWN_GAP_MIN = 50, SPAWN_GAP_SPREAD = 90;

    public static final class Rise {
        public final BlockPos pos;
        public final Identifier species;
        public final long until;
        long nextRing;

        Rise(BlockPos pos, Identifier species, long until, long nextRing) {
            this.pos = pos;
            this.species = species;
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
                ring(level, r.pos, level.getRandom());
                r.nextRing = now + 40 + level.getRandom().nextInt(40);
            }
        }
        long next = NEXT_SPAWN.getOrDefault(sp.getUUID(), 0L);
        if (list.size() < MAX_RISES && now >= next) {
            NEXT_SPAWN.put(sp.getUUID(), now + SPAWN_GAP_MIN + level.getRandom().nextInt(SPAWN_GAP_SPREAD));
            Rise r = spawn(level, sp, now);
            if (r != null) {
                list.add(r);
                ring(level, r.pos, level.getRandom());
                level.sendParticles(ParticleTypes.SPLASH, r.pos.getX() + 0.5, r.pos.getY() + 1.0, r.pos.getZ() + 0.5,
                        6, 0.2, 0.05, 0.2, 0.1);
            }
        }
    }

    /** The rise within reach of a spot, taken off the water (the fish is yours now), or null. */
    public static Rise take(ServerPlayer sp, BlockPos at) {
        List<Rise> list = RISES.get(sp.getUUID());
        if (list == null) return null;
        Rise best = null;
        double bestD = REACH * REACH;
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

    /** A fish comes up somewhere in front of the angler, 5–16 blocks out, on open water. */
    private static Rise spawn(ServerLevel level, ServerPlayer sp, long now) {
        RandomSource rng = level.getRandom();
        net.minecraft.world.phys.Vec3 look = sp.getLookAngle();
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        double base = hl < 1e-3 ? rng.nextDouble() * Math.PI * 2 : Math.atan2(look.z, look.x);
        for (int i = 0; i < 6; i++) {
            double a = base + (rng.nextDouble() - 0.5) * Math.toRadians(100);
            double d = 5.0 + rng.nextDouble() * 11.0;
            double x = sp.getX() + Math.cos(a) * d, z = sp.getZ() + Math.sin(a) * d;
            BlockPos p = FishingManager.findWaterColumn(level, x, sp.getEyeY() + 2.0, z);
            if (p == null || !level.getBlockState(p.above()).isAir()) continue;
            WaterBody body = WaterBodyCache.forLevel(level).get(level, p);
            if (body == null || body.type() == WaterType.NONE) continue;
            Identifier species = pick(level, p, body, rng);
            if (species == null) continue;
            return new Rise(p, species, now + LIFE_MIN + rng.nextInt(LIFE_SPREAD), now + 30);
        }
        return null;
    }

    /** The species feeding here: the engine's own weights at this spot, tilted to what eats off the top. */
    private static Identifier pick(ServerLevel level, BlockPos p, WaterBody body, RandomSource rng) {
        BiteContext env = FishingManager.environmentAt(level, p, body);
        List<FishProfile> ids = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        double total = 0;
        for (FishProfile pr : FishProfileManager.get().all()) {
            double w = BiteEngine.environmentScore(pr, env);
            if (w <= 1e-4) continue;
            w *= pr.base * flyAppetite(pr.diet);
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

    /** Who comes up for a fly: the insect eaters first, the predators only for a streamer's sake. */
    public static double flyAppetite(String diet) {
        if (diet == null) return 0.8;
        return switch (diet) {
            case "insectivore" -> 1.4;
            case "peaceful" -> 1.0;
            case "omnivore" -> 0.9;
            case "predator" -> 0.35;
            default -> 0.8;
        };
    }

    /** The ring on the water, and the sip. */
    private static void ring(ServerLevel level, BlockPos p, RandomSource rng) {
        double y = p.getY() + 0.92;
        int n = 6;
        for (int i = 0; i < n; i++) {
            double a = i * (Math.PI * 2.0 / n);
            double dx = Math.cos(a), dz = Math.sin(a);
            level.sendParticles(ParticleTypes.FISHING, p.getX() + 0.5 + dx * 0.3, y, p.getZ() + 0.5 + dz * 0.3,
                    0, dx, 0.0, dz, 0.15);
        }
        level.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, y + 0.05, p.getZ() + 0.5, 2, 0.15, 0.0, 0.15, 0.0);
        level.playSound(null, p, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35f, 1.3f + rng.nextFloat() * 0.3f);
    }
}
