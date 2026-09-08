package com.riverfishing.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * §fly-3 (0.10.0): the take, in two acts.
 *
 * <p><b>The fish comes.</b> A dozen ticks before it eats, something moves under the fly: a shadow, a
 * push of water, a bulge. Nothing is asked of the angler yet, and lifting now pulls the fly out of a
 * mouth that has not closed.
 *
 * <p><b>The fish takes.</b> The fly goes under in a boil and the screen says <i>STRIKE</i> with the button
 * to press. {@link #STRIKE_WINDOW} ticks to answer. A dry fly or a nymph is <b>lifted</b> (left click); a
 * streamer is <b>strip-set</b> (right click) — the wrong one still hooks the fish, just badly, and a bad
 * hook is a fish that can throw it on the first jump. Too late and it has spat the fly out.
 */
public final class FlyStrike {
    /** Ticks to answer a take. */
    public static final int STRIKE_WINDOW = 35;
    /** Answer inside this many ticks of the take and the hook is set properly. */
    public static final int CLEAN_STRIKE = 20;
    /** How long before the take the fish is first seen coming. */
    public static final int APPROACH_MIN = 10, APPROACH_SPREAD = 11;

    /** Which button the player pressed. */
    public enum Input { LIFT, STRIP }

    private FlyStrike() {}

    /** Every tick of a drifting fly: run the approach, the take, and the closing of the window. */
    static void tick(ServerLevel level, ServerPlayer sp, FishingSession session, long now) {
        FlySession fly = session.fly;
        if (fly == null || session.fighting) return;

        if (fly.state == FlySession.State.DRIFTING) {
            // the lead is stable for a given take, so the tell does not flicker in and out
            long lead = APPROACH_MIN + Math.floorMod(session.biteAtTick * 31L, APPROACH_SPREAD);
            if (session.biteAtTick > 0 && now >= session.biteAtTick - lead
                    && !FishingManager.spookedNow(level, session, now)) {
                beginApproach(level, sp, session, now);
            }
            return;
        }
        if (fly.state != FlySession.State.STRIKING) return;

        if (!fly.taken) {
            // the fish rising under the fly: a bulge, then the boil that is the take
            if (now % 3 == 0) {
                Vec3 at = fly.flyAt(sp);
                level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y + 0.6, at.z, 3, 0.18, 0.05, 0.18, 0.0);
                level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.95, at.z, 2, 0.22, 0.0, 0.22, 0.01);
            }
            if (now >= session.biteAtTick) bite(level, sp, session, now);
            return;
        }
        if (now > fly.strikeUntil) {
            // nothing came: the fish worked it out and let go
            Vec3 at = fly.flyAt(sp);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, 10, 0.25, 0.1, 0.25, 0.1);
            FishingManager.eatBaitPublic(sp, session);
            FishingManager.endSession(sp, session);
            FishingManager.actionbar(sp, Component.translatable("message.riverfishing.fly_spat")
                    .withStyle(ChatFormatting.GRAY));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
        }
    }

    /** A fish has decided and is on its way up: the tell, and the moment a strike becomes early. */
    private static void beginApproach(ServerLevel level, ServerPlayer sp, FishingSession session, long now) {
        FlySession fly = session.fly;
        fly.state = FlySession.State.STRIKING;
        fly.taken = false;
        Vec3 at = fly.flyAt(sp);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.35f, 0.6f);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y + 0.5, at.z, 8, 0.3, 0.05, 0.3, 0.0);
        FlyDrift.push(sp, session, true);
    }

    /** The fly goes under: the window opens, and the HUD says which hand does the work. */
    private static void bite(ServerLevel level, ServerPlayer sp, FishingSession session, long now) {
        FlySession fly = session.fly;
        fly.taken = true;
        session.bitten = true;
        fly.strikeUntil = now + STRIKE_WINDOW;
        session.biteWindowEnd = fly.strikeUntil;
        Vec3 at = fly.flyAt(sp);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, 26, 0.35, 0.15, 0.35, 0.3);
        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.95, at.z, 10, 0.25, 0.05, 0.25, 0.05);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.PLAYERS, 1.0f, 1.15f);
        FlyDrift.push(sp, session, true);
    }

    /**
     * The angler answered. Early, right, wrong or late — none of them is a coin flip: what changes is how
     * well the hook is set, and a badly set hook is a fish that may come off in the first minute.
     *
     * @return true when the press was the fly rod's business and must not fall through to anything else
     */
    public static boolean tryStrike(ServerPlayer sp, Input input) {
        FishingSession session = FishingManager.session(sp);
        if (session == null || session.fly == null || session.fighting) return false;
        FlySession fly = session.fly;
        if (fly.state != FlySession.State.STRIKING) return false;
        ServerLevel level = sp.level();
        long now = level.getGameTime();

        if (!fly.taken) {
            // The fish is still coming up. The fly is pulled out of the way — most of the time.
            if (level.getRandom().nextFloat() < 0.3f) {
                fly.hookStrength = 0;
                hook(level, sp, session, now);
            } else {
                Vec3 at = fly.flyAt(sp);
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, 8, 0.2, 0.1, 0.2, 0.1);
                SpookData.of(level).disturb(level, fly.spot, 0.5, 3.0, now);
                FishingManager.eatBaitPublic(sp, session);
                FishingManager.endSession(sp, session);
                FishingManager.actionbar(sp, Component.translatable("message.riverfishing.fly_too_fast")
                        .withStyle(ChatFormatting.GRAY));
                GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
            }
            return true;
        }
        boolean right = (input == Input.STRIP) == fly.kind.strikeIsRightClick();
        boolean quick = now - (fly.strikeUntil - STRIKE_WINDOW) <= CLEAN_STRIKE;
        fly.hookStrength = !right ? 0 : quick ? 2 : 1;
        hook(level, sp, session, now);
        return true;
    }

    /** Into the fight, with the hook the strike earned. */
    private static void hook(ServerLevel level, ServerPlayer sp, FishingSession session, long now) {
        FlySession fly = session.fly;
        session.hookStrength = fly.hookStrength;
        session.bitten = true;
        FlyCast.statusOff(sp);
        FishingManager.actionbar(sp, Component.translatable(fly.hookStrength == 0
                        ? "message.riverfishing.fly_hook_weak"
                        : fly.hookStrength == 2 ? "message.riverfishing.fly_hook_solid" : "message.riverfishing.fly_hook")
                .withStyle(fly.hookStrength == 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        FishingManager.flyHookUp(sp, level, session, now);
    }
}
