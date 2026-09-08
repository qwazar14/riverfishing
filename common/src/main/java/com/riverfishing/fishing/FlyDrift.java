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
 * §fly-3 (0.10.0): the drift — the fly is on the water and the best thing the angler can do is nothing.
 *
 * <p>The fly rides the current. The line, lying across that current, slowly bows and begins to <b>drag</b>
 * the fly: the count is internal and never shown, only its consequences are. Under
 * {@link #DRAG_CRITICAL} the fly rides naturally and every fish is interested; over it the fly starts to
 * skate, leaves a wake, and the careful fish stop looking at it — a streamer or an angry predator will
 * still take. At a hundred the line is straight below and this drift is finished.
 *
 * <p>Two inputs, both single presses, neither of them timed:
 * <ul>
 *   <li><b>Left click — mend.</b> The line is flipped upstream and the drag is gone. The first is free,
 *       the second stirs the water a little, the third and every one after are heard.</li>
 *   <li><b>Right click — strip.</b> Two blocks of line come back. It is how a streamer is fished at all,
 *       how a dry fly is nudged onto a fish, and — twice quickly — how the whole line is picked up.</li>
 * </ul>
 */
public final class FlyDrift {
    /** Drag per ten ticks of current under the line. */
    public static final int DRAG_RATE = 2;
    /** Over this the fly is visibly skating; under it the drift is natural. */
    public static final int DRAG_CRITICAL = 70;
    /** Mends past this one are heard by the fish. */
    public static final int QUIET_MENDS = 2;
    /** Blocks of line a single strip takes back. */
    public static final double STRIP_BLOCKS = 1.75;
    /** Two strips inside this many ticks are one gesture: pick the line up. */
    public static final int DOUBLE_TAP = 10;
    /** A streamer wants its strips this far apart. */
    public static final int RHYTHM_MIN = 10, RHYTHM_MAX = 25;

    /** HUD codes on the status line. */
    public static final int HUD_DRIFT = 0, HUD_DRAG = 1, HUD_STRAIGHT = 2, HUD_APPROACH = 3,
            HUD_STRIKE_LMB = 4, HUD_STRIKE_RMB = 5, HUD_ON_FISH = 6;

    private FlyDrift() {}

    /** The cast has landed: the drift begins, and the water is told about it. */
    public static void start(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null) return;
        checkRise(sp, level, session, now);
        push(sp, session, true);
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
                if (fly.drag >= 100) {
                    fly.straight = true;
                    FishingManager.actionbar(sp, net.minecraft.network.chat.Component
                            .translatable("message.riverfishing.fly_straight")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
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
        // The skate: a wake off the fly, so the drag is something you SEE rather than a number you read.
        if (fly.drag > DRAG_CRITICAL && now % 4 == 0) {
            Vec3 at = fly.flyAt(sp);
            level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.95, at.z, 2, 0.18, 0.0, 0.18, 0.01);
            if (now % 40 == 0) {
                level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_RETRIEVE,
                        SoundSource.PLAYERS, 0.18f, 0.7f);
            }
        }
        // A skating fly and a straight line put the careful fish off — the take is pushed back. A streamer
        // is a fleeing baitfish either way, so it keeps fishing.
        boolean careful = fly.kind != FlySession.Kind.STREAMER;
        if (careful && !fly.onRise && (fly.drag > DRAG_CRITICAL || fly.straight) && now >= session.biteAtTick - 5) {
            session.biteAtTick = now + 25;
        }
        FlyStrike.tick(level, sp, session, now);
        push(sp, session, now % 40 == 0);   // on every change, and once every two seconds regardless
    }

    /** §fly-3: the mend — the line flipped upstream, the drag gone, and a little noise if it is a habit. */
    public static void mend(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null) return;
        fly.drag = 0;
        fly.mends++;
        Vec3 at = fly.flyAt(sp);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.PLAYERS, 0.35f, 1.6f);
        // the line lifting and going back upstream: a short arc of drops off the water
        Vec3 up = upstream(level, fly.spot);
        for (int i = 1; i <= 5; i++) {
            level.sendParticles(ParticleTypes.SPLASH, at.x + up.x * i * 0.35, at.y + 1.0, at.z + up.z * i * 0.35,
                    1, 0.05, 0.02, 0.05, 0.02);
        }
        // Every mend after the second is a rod waved over the fish's head.
        if (fly.mends > QUIET_MENDS) {
            SpookTracker.onCastLanded(level, fly.spot, 0.08 * (fly.mends - QUIET_MENDS));
        }
        push(sp, session, true);
    }

    /**
     * §fly-3: the strip — a couple of blocks of line back. A streamer lives on it (a rhythm of strips and
     * pauses calls the fish); a nymph gets a twitch out of it; a dry fly is only nudged. Twice in quick
     * succession is not two strips, it is "pick it up".
     */
    public static void strip(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null) return;
        if (now - fly.lastStrip <= DOUBLE_TAP && fly.lastStrip != Long.MIN_VALUE) {
            quickRetrieve(sp, session);   // the second of two quick pulls: the line comes off the water
            return;
        }
        long gap = fly.lastStrip == Long.MIN_VALUE ? RHYTHM_MIN : now - fly.lastStrip;
        fly.prevStrip = fly.lastStrip;
        fly.lastStrip = now;

        double dx = sp.getX() - (fly.spot.getX() + 0.5), dz = sp.getZ() - (fly.spot.getZ() + 0.5);
        double dist = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        fly.reel = Math.min(1.0, fly.reel + STRIP_BLOCKS / dist);
        if (fly.reel >= 0.85) {
            quickRetrieve(sp, session);
            return;
        }
        fly.drag = Math.max(0, fly.drag - 30);   // a strip takes the bow out of the line

        Vec3 at = fly.flyAt(sp);
        boolean streamer = fly.kind == FlySession.Kind.STREAMER;
        if (!fly.onRise && session.biteAtTick > now) {
            // A streamer swum in the right rhythm looks alive and brings the take on; anything else is a twitch.
            boolean rhythm = gap >= RHYTHM_MIN && gap <= RHYTHM_MAX;
            long pull = streamer ? (rhythm ? 22 : 6) : fly.kind == FlySession.Kind.NYMPH ? 6 : 3;
            session.biteAtTick = Math.max(now + 8, session.biteAtTick - pull);
            if (streamer && rhythm && level.getRandom().nextInt(3) == 0) {
                // the follow: a small fish's wake, right behind the fly
                level.sendParticles(ParticleTypes.BUBBLE_POP, at.x - dx / dist * 0.8, at.y + 0.95,
                        at.z - dz / dist * 0.8, 4, 0.2, 0.0, 0.2, 0.0);
            }
        }
        // A dry fly dragged about is a dry fly the fish has seen move: it costs a little quiet.
        if (fly.kind == FlySession.Kind.DRY) SpookTracker.onCastLanded(level, fly.spot, 0.05);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.PLAYERS, 0.25f, 1.4f);
        level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.95, at.z, 3, 0.1, 0.0, 0.1, 0.02);
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, fly.spot, (float) fly.reel,
                session.lineColor, session.floatKind, false));
        checkRise(sp, level, session, now);
        push(sp, session, false);
    }

    /** The line comes off the water: this cast is over and the next hold begins a new one. */
    public static void quickRetrieve(ServerPlayer sp, FishingSession session) {
        FishingManager.endSession(sp, session);
        FishingManager.actionbar(sp, net.minecraft.network.chat.Component
                .translatable("message.riverfishing.fly_pickup"));
    }

    /** The fly has come down on (or drifted onto) a feeding fish: that fish, and it is already interested. */
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
        FishingManager.actionbar(sp, net.minecraft.network.chat.Component
                .translatable("message.riverfishing.fly_on_fish").withStyle(net.minecraft.ChatFormatting.GREEN));
        level.playSound(null, fly.spot, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.6f);
        push(sp, session, true);
    }

    /** A unit vector pointing up the current at this spot, or a token one on still water. */
    private static Vec3 upstream(ServerLevel level, BlockPos at) {
        Vec3 flow = level.getFluidState(at).getFlow(level, at);
        double fl = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
        return fl < 0.05 ? new Vec3(0, 0, 0) : new Vec3(-flow.x / fl, 0, -flow.z / fl);
    }

    /** Send the status line — what the fly is doing, in words, never in numbers. */
    static void push(ServerPlayer sp, FishingSession session, boolean force) {
        FlySession fly = session.fly;
        if (fly == null) return;
        int state = fly.state == FlySession.State.STRIKING
                ? (fly.taken ? (fly.kind.strikeIsRightClick() ? HUD_STRIKE_RMB : HUD_STRIKE_LMB) : HUD_APPROACH)
                : fly.onRise ? HUD_ON_FISH : fly.straight ? HUD_STRAIGHT
                : fly.drag > DRAG_CRITICAL ? HUD_DRAG : HUD_DRIFT;
        if (!force && state == fly.shownState) return;
        fly.shownState = state;
        FlyCast.status(sp, state, fly.metres(sp), fly.onRise);
    }
}
