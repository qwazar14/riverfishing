package com.riverfishing.fishing;

import com.riverfishing.network.LineSyncPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * §fly-4: the drift — the fly is on the water and the best thing the angler can do is nothing.
 *
 * <p>Nothing here is written on the screen. The fly rides the current; the line, lying across that
 * current, slowly bows and begins to <b>drag</b> it, and you know because you can see it: the fly stops
 * sitting still and starts cutting a wake across the surface, and you hear it. Past that point the careful
 * fish stop looking at it. When the line comes tight below you the drift is finished.
 *
 * <p>Two inputs, both single presses:
 * <ul>
 *   <li><b>Left click — the flick.</b> The rod lifts the line and drops it two blocks upstream, in an arc
 *       of spray you watch go, and the fly rides cleanly again.</li>
 *   <li><b>Right click — the strip.</b> Two blocks of line back. It is how a streamer is fished at all,
 *       and how a dry fly is nudged onto a fish. At your feet there is no line left out and the cast ends.</li>
 * </ul>
 * There is no gesture that abandons a cast: change hotbar slot, the way every other rod does it.
 */
public final class FlyDrift {
    /** Drag per ten ticks of current under the line. */
    public static final int DRAG_RATE = 2;
    /** Over this the fly is visibly skating; under it the drift is natural. */
    public static final int DRAG_CRITICAL = 70;
    /** Flicks past this one are heard by the fish. */
    public static final int QUIET_MENDS = 2;
    /** Blocks of line a single strip takes back. */
    public static final double STRIP_BLOCKS = 1.75;
    /** A streamer wants its strips this far apart. */
    public static final int RHYTHM_MIN = 10, RHYTHM_MAX = 25;

    private FlyDrift() {}

    /** The cast has landed: see whether it came down on a feeding fish. */
    public static void start(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        if (session.fly != null) checkRise(sp, level, session, now);
    }

    /**
     * Once a tick while the line is on the water and nothing has taken yet: the current carries the fly,
     * the line bows, and the fish that is coming is watched for.
     */
    public static void tick(ServerLevel level, ServerPlayer sp, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null) return;
        if (fly.state == FlySession.State.STRIKING) {
            FlyStrike.tick(level, sp, session, now);
            return;
        }
        if (!fly.straight && now % 10 == 0) {
            BlockPos t = fly.spot;
            Vec3 flow = level.getFluidState(t).getFlow(level, t);
            double fl = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
            if (fl >= 0.05) {
                fly.drag = Math.min(100, fly.drag + DRAG_RATE);
                if (fly.drag >= 100) fly.straight = true;
                BlockPos next = FishingManager.findWaterColumn(level, t.getX() + 0.5 + Math.round(flow.x / fl),
                        t.getY() + 1.0, t.getZ() + 0.5 + Math.round(flow.z / fl));
                if (next != null && !next.equals(t)) {
                    fly.spot = next;
                    ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, next, (float) fly.reel,
                            session.lineColor, session.floatKind, false));
                    checkRise(sp, level, session, now);
                }
            }
        }
        // The skate: the fly cuts a wake instead of sitting on the drift. This is the whole tell — there is
        // no number and no word for it anywhere, because a fly dragging is a thing you can see.
        if (fly.drag > DRAG_CRITICAL) {
            Vec3 at = fly.flyAt(sp);
            if (now % 3 == 0) {
                level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.95, at.z, 2, 0.2, 0.0, 0.2, 0.015);
            }
            if (now % 30 == 0) {
                level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_RETRIEVE,
                        SoundSource.PLAYERS, 0.22f, 0.65f);
            }
        }
        // A skating fly and a straight line put the careful fish off. A streamer is a fleeing baitfish
        // either way, so it keeps fishing.
        boolean careful = fly.kind != FlySession.Kind.STREAMER;
        if (careful && !fly.onRise && (fly.drag > DRAG_CRITICAL || fly.straight) && now >= session.biteAtTick - 5) {
            session.biteAtTick = now + 25;
        }
        FlyStrike.tick(level, sp, session, now);
    }

    /**
     * §fly-4: the flick. The rod lifts the line and drops it two blocks upstream — you watch it go — and
     * the fly rides cleanly again. On still water there is nothing to correct, so it is a twitch, which is
     * its own way of being noticed.
     */
    public static void flick(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null) return;
        Vec3 up = upstream(level, fly.spot);
        Vec3 from = fly.flyAt(sp);
        BlockPos moved = null;
        if (up.lengthSqr() > 1e-6) {
            for (int d = 2; d >= 1 && moved == null; d--) {
                moved = FishingManager.findWaterColumn(level, from.x + up.x * d, from.y + 1.0, from.z + up.z * d);
            }
        }
        // the arc: the line coming off the water and going back up the current
        for (int i = 0; i <= 6; i++) {
            double f = i / 6.0;
            level.sendParticles(ParticleTypes.SPLASH, from.x + up.x * 2 * f,
                    from.y + 1.0 + Math.sin(Math.PI * f) * 0.5, from.z + up.z * 2 * f, 2, 0.06, 0.02, 0.06, 0.01);
        }
        level.playSound(null, BlockPos.containing(from), SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.PLAYERS, 0.45f, 1.5f);
        if (moved != null && !moved.equals(fly.spot)) {
            fly.spot = moved;
            fly.reel = Math.max(0.0, fly.reel - 0.05);
            ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, moved, (float) fly.reel,
                    session.lineColor, session.floatKind, false));
        }
        fly.drag = 0;
        fly.straight = false;
        fly.mends++;
        Vec3 at = fly.flyAt(sp);
        level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.95, at.z, 5, 0.12, 0.0, 0.12, 0.02);
        // Every flick after the second is a rod waved over the fish's head, and they feel it.
        if (fly.mends > QUIET_MENDS) {
            SpookTracker.onCastLanded(level, fly.spot, 0.08 * (fly.mends - QUIET_MENDS));
        }
        checkRise(sp, level, session, now);
    }

    /**
     * §fly-4: the strip — a couple of blocks of line back. A streamer lives on it (a rhythm of strips and
     * pauses calls the fish); a nymph gets a twitch out of it; a dry fly is only nudged. When the fly is at
     * your feet there is no line left on the water and the cast is over.
     */
    public static void strip(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null) return;
        long gap = fly.lastStrip == Long.MIN_VALUE ? RHYTHM_MIN : now - fly.lastStrip;
        fly.prevStrip = fly.lastStrip;
        fly.lastStrip = now;

        double dx = sp.getX() - (fly.spot.getX() + 0.5), dz = sp.getZ() - (fly.spot.getZ() + 0.5);
        double dist = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        fly.reel = Math.min(1.0, fly.reel + STRIP_BLOCKS / dist);
        if (fly.reel >= 0.85) {
            FishingManager.endSession(sp, session);   // the fly is at your feet: nothing is fishing
            return;
        }
        fly.drag = Math.max(0, fly.drag - 30);   // a strip takes the bow out of the line

        Vec3 at = fly.flyAt(sp);
        boolean streamer = fly.kind == FlySession.Kind.STREAMER;
        if (!fly.onRise && session.biteAtTick > now) {
            // A streamer swum in the right rhythm looks alive and brings the take on; anything else twitches.
            boolean rhythm = gap >= RHYTHM_MIN && gap <= RHYTHM_MAX;
            long pull = streamer ? (rhythm ? 22 : 6) : fly.kind == FlySession.Kind.NYMPH ? 6 : 3;
            session.biteAtTick = Math.max(now + 8, session.biteAtTick - pull);
            if (streamer && rhythm && level.getRandom().nextInt(3) == 0) {
                // the follow: a small fish's wake, right behind the fly
                level.sendParticles(ParticleTypes.BUBBLE_POP, at.x - dx / dist * 0.8, at.y + 0.95,
                        at.z - dz / dist * 0.8, 4, 0.2, 0.0, 0.2, 0.0);
            }
        }
        // A dry fly dragged about is a dry fly the fish have seen move: it costs a little quiet.
        if (fly.kind == FlySession.Kind.DRY) SpookTracker.onCastLanded(level, fly.spot, 0.05);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, 0.25f, 1.4f);
        level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.95, at.z, 3, 0.1, 0.0, 0.1, 0.02);
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, fly.spot, (float) fly.reel,
                session.lineColor, session.floatKind, false));
        checkRise(sp, level, session, now);
    }

    /**
     * The fly has come down on (or drifted onto) a feeding fish. The ring stops appearing — that is how you
     * know — and the fish is already interested.
     */
    static void checkRise(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null || fly.onRise || session.bitten) return;
        FlyRises.Rise rise = FlyRises.take(sp, BlockPos.containing(fly.flyAt(sp)));
        if (rise == null) return;
        session.species = rise.species;
        fly.onRise = true;
        // A fish that is already looking up takes almost at once — a dry fly put on its nose soonest of all.
        int lead = 10 + level.getRandom().nextInt(16);
        if (fly.kind == FlySession.Kind.DRY) lead = Math.max(8, lead - 4);
        session.biteAtTick = now + lead;
        Vec3 at = fly.flyAt(sp);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y + 0.5, at.z, 5, 0.25, 0.05, 0.25, 0.0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.PLAYERS, 0.3f, 1.7f);
    }

    /** A unit vector pointing up the current at this spot, or nothing on still water. */
    private static Vec3 upstream(ServerLevel level, BlockPos at) {
        Vec3 flow = level.getFluidState(at).getFlow(level, at);
        double fl = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
        return fl < 0.05 ? new Vec3(0, 0, 0) : new Vec3(-flow.x / fl, 0, -flow.z / fl);
    }
}
