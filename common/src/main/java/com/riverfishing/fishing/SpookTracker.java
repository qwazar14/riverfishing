package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * §spook sources (0.7.0): everything a player does that a fish would notice, turned into a number and
 * dropped on the nearest water.
 *
 * <p>The values are all small on purpose. One careless second does not empty a lake; a minute of tramping
 * around does. And every one of them is scaled by how the player is moving, so the mechanic has exactly one
 * counter-play and it is the real one: come in slowly, crouch, and stop.
 *
 * <p>Everything here is positional. A player on the bank frightens the water in front of them and nothing
 * else; the bait twenty blocks out is disturbed by its own cast landing and by nothing the angler does
 * afterwards. That asymmetry is the point — it is what makes a long cast worth making.
 */
public final class SpookTracker {
    /** Four samples a second. Spook accumulates over seconds, so a per-tick sample buys nothing. */
    private static final int PERIOD = 5;
    /** How far a person on the bank carries. Beyond this you are just someone in the distance. */
    private static final double REACH = 6.0;
    /** A broken block carries further than footsteps and is much louder. */
    private static final double BREAK_REACH = 8.0;

    // Per-sample amounts (this is 4× a second, so a second of sprinting is roughly 4× the figure).
    private static final double SPRINT = 0.055;
    private static final double WALK = 0.020;
    private static final double JUMP = 0.045;
    private static final double WADE = 0.030;
    private static final double BOAT = 0.045;
    private static final double SHADOW = 0.012;
    private static final double BREAK = 0.30;

    /** Where each player was at the last sample, to tell walking from standing without trusting flags. */
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

    private SpookTracker() {}

    public static void tick(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if ((now + sp.getId()) % PERIOD != 0) return;

        Vec3 pos = sp.position();
        Vec3 was = LAST_POS.put(sp.getUUID(), pos);
        double moved = was == null ? 0.0 : pos.distanceTo(was);
        boolean crouch = sp.isCrouching();
        boolean still = moved < 0.02;

        double noise = 0.0;
        // Crouched and stationary is the one state that makes no noise at all — that is the whole reward
        // for approaching properly, and the reason this mechanic has an answer instead of just a tax.
        if (!(crouch && still)) {
            if (sp.isSprinting()) noise += SPRINT;
            else if (moved > 0.04) noise += crouch ? WALK * 0.25 : WALK;
            // Airborne and rising: a jump. Falling is the second half of the same jump, so only count up.
            if (!sp.onGround() && sp.getDeltaMovement().y > 0.08) noise += JUMP;
            // Standing IN the water is the worst thing an angler can do and costs nothing to detect.
            if (sp.isInWater()) noise += WADE;
        }
        // A boat that has actually moved recently. A drifting, stopped boat is quiet.
        if (sp.getVehicle() instanceof Boat boat
                && boat.getDeltaMovement().horizontalDistanceSqr() > 0.0015) {
            noise += BOAT;
        }

        // A player standing still on the bank at midday makes no noise and casts no useful shadow, which
        // is the common case — do not pay for a water scan to discover that.
        if (noise <= 0.0 && !sunIsLow(level)) return;

        BlockPos water = nearestSurface(level, sp.blockPosition(), REACH);
        if (water == null) return;
        // §spook-shadow: a low sun behind you throws your shadow across the water, and fish leave a
        // moving shadow long before they hear anything. Two comparisons: the sun is low in the first and
        // last two thousand ticks of the day, and in Minecraft it always travels along the X axis.
        if (shadowFalls(level, sp, water)) noise += SHADOW;

        noise *= com.riverfishing.config.RiverFishingConfig.spookRate();
        if (noise <= 0.0) return;
        SpookData.of(level).disturb(level, water, noise, REACH, now);
    }

    /** A block broken near water. Loud, close, and entirely the player's fault. */
    public static void onBlockBreak(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos water = nearestSurface(sl, pos, BREAK_REACH);
        if (water == null) return;
        SpookData.of(sl).disturb(sl, water, BREAK * com.riverfishing.config.RiverFishingConfig.spookRate(),
                BREAK_REACH, sl.getGameTime());
    }

    /**
     * The tackle hitting the water. This is the one disturbance that reaches a spot the player is nowhere
     * near, and it is why a far cast is worth making: everything else they do stays on the bank.
     */
    public static void onCastLanded(ServerLevel level, BlockPos water, double amount) {
        SpookData.of(level).disturb(level, water,
                amount * com.riverfishing.config.RiverFishingConfig.spookRate(), 2.5, level.getGameTime());
    }

    /** Forget a player who left, so the map cannot grow across sessions. */
    public static void forget(UUID player) {
        LAST_POS.remove(player);
    }

    /**
     * Is the player's shadow on the water? True when the sun is low, the sky is clear overhead, and the
     * player stands between the sun and the water. Minecraft's sun runs along the X axis — east at dawn,
     * west at dusk — so the horizontal light direction is a sign, not a trigonometry problem.
     */
    private static boolean shadowFalls(ServerLevel level, ServerPlayer sp, BlockPos water) {
        if (!sunIsLow(level) || level.isRaining() || !level.canSeeSky(sp.blockPosition())) return false;
        // Light travels away from the sun: westward (−X) in the morning, eastward (+X) in the evening.
        double lightX = level.getDayTime() % 24000L < 2000L ? -1.0 : 1.0;
        return (water.getX() + 0.5 - sp.getX()) * lightX > 0;
    }

    /**
     * Is the sun under about thirty degrees? Minecraft's day starts at sunrise and noon is 6000, so the
     * elevation is sin(pi * t / 12000) and the first and last two thousand ticks are exactly the half of
     * the arc below 30 degrees — no trigonometry needed at runtime.
     */
    private static boolean sunIsLow(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return t < 2000L || (t > 10000L && t <= 12000L);
    }

    /**
     * The nearest water surface within {@code reach}, or null. Heightmap probes on a 2-block lattice —
     * O(1) each, so this is cheap enough to run four times a second per player.
     */
    /** Package-visible: the order board asks the same question about the same water (§order-board). */
    static BlockPos nearestSurface(ServerLevel level, BlockPos from, double reach) {
        int r = (int) reach;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                double d = dx * dx + dz * dz;
                if (d > r * r || d >= bestD) continue;
                int x = from.getX() + dx, z = from.getZ() + dz;
                if (!level.hasChunkAt(x, z)) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (Math.abs(top - from.getY()) > 6) continue;    // not the water you are standing over
                BlockPos p = new BlockPos(x, top, z);
                if (level.getFluidState(p).isEmpty()) continue;
                best = p;
                bestD = d;
            }
        }
        return best;
    }
}
