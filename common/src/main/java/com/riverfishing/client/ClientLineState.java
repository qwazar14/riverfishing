package com.riverfishing.client;

import com.riverfishing.network.FightInputPacket;
import com.riverfishing.network.LineSyncPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side state of every visible fishing line (§line-multiplayer), keyed by the angler's entity
 * id and fed by {@link LineSyncPacket}. The server re-broadcasts each line every couple of seconds,
 * so entries that stop being refreshed (their angler reeled in while we weren't tracking them)
 * expire on their own.
 */
public final class ClientLineState {
    /** One player's line as the renderer needs it. */
    public static final class Line {
        public BlockPos target = BlockPos.ZERO;
        public float progress;         // authoritative (server) reel-in progress 0..1
        public float smoothProgress;   // eased for rendering
        public int color = 0xFFE8E4D0;
        public byte floatKind;         // §float-kind: 0 none / 1 plain peg / 2 proper float
        public boolean biting;         // bite in progress: bobber plunges / line twitches
        public float tension;          // §rod-bend: the line's break-risk 0..1 (taut, colour, creaks)
        public float smoothTension;    // eased break-risk
        public float rodLoad;          // §rod-load: how loaded the BLANK is 0..1 — the bend reads this
        public float smoothRodLoad;    // eased for the in-hand bend and the springs
        public boolean fighting;       // §pump-reel: the fight is on
        // §pump-reel: DO NOT REEL right now — the fish is taking line, or it is in the air
        // on a breach (§jump-cue). Both answer the same question for the player, so they are
        // one flag: a second one would be a second place for the HUD to disagree with the
        // server about whether to crank.
        public boolean running;
        /** §fight-course: FightCourse.ordinal() of the current run, 0 when it is not running. */
        public byte course;
        /** Eased lean of the rod tip, in degrees: x = sideways, y = up/down. */
        public float leanYaw;
        public float leanPitch;
        /**
         * §line-taut-eased: the DISPLAYED string state, 0..1 each — what the renderer hangs the line
         * by. Targets are shaped from tension/running, then chased with asymmetric easing: a line
         * SNAPS tight (the jerk is instantaneous) but relaxes at cable speed, so between the string
         * and the belly there is a whole readable middle of partial droop instead of a flick.
         */
        public float dispTaut;
        public float dispSlack;
        public long lastUpdate;        // client game time of the last packet (staleness check)

        /** Eases the rendered progress toward the server value; call once per frame. */
        public void tickSmoothing(float frameSeconds) {
            smoothProgress = Mth.lerp(Math.min(1f, frameSeconds * 6f), smoothProgress, progress);
            smoothTension = Mth.lerp(Math.min(1f, frameSeconds * 8f), smoothTension, tension);
            smoothRodLoad = Mth.lerp(Math.min(1f, frameSeconds * 8f), smoothRodLoad, rodLoad);
            // §fight-course: the tip is DRAGGED the way the fish is going — that is the read, and it is
            // also what physically happens. Eased hard enough to be unmistakable but not snappy, so a
            // run reads as the rod being pulled over rather than as the item teleporting.
            // The sign is what the bar says, not the opposite of it: a fish going LEFT drags the tip
            // LEFT. The first build had these the wrong way round and the two cues contradicted.
            float ty = course == 1 ? 1f : course == 2 ? -1f : 0f;
            float tp = course == 3 ? 1f : course == 4 ? -1f : 0f;
            float k = Math.min(1f, frameSeconds * 5f);
            leanYaw = Mth.lerp(k, leanYaw, ty * RodHandTransform.COURSE_YAW);
            leanPitch = Mth.lerp(k, leanPitch, tp * RodHandTransform.COURSE_PITCH);

            // §line-taut-eased: shape the targets over a WIDE band (0.02..0.35 tension covers the
            // whole straightening arc), then chase them — tightening 3x faster than relaxing.
            float tautTarget = 0f, slackTarget = 0f;
            if (fighting) {
                tautTarget = running ? 1f
                        : smoothstep(Mth.clamp((smoothTension - 0.02f) / 0.33f, 0f, 1f));
                slackTarget = running ? 0f : Mth.clamp((0.10f - smoothTension) / 0.10f, 0f, 1f);
            }
            float kUp = Math.min(1f, frameSeconds * 12f);   // a jerk snaps the line tight
            float kDown = Math.min(1f, frameSeconds * 3f);  // slack develops at cable speed
            dispTaut = Mth.lerp(tautTarget > dispTaut ? kUp : kDown, dispTaut, tautTarget);
            dispSlack = Mth.lerp(slackTarget > dispSlack ? kDown : kUp, dispSlack, slackTarget);
        }

        private static float smoothstep(float s) {
            return s * s * (3f - 2f * s);
        }
    }

    /** Server re-sends every ~40 t; anything this stale lost its owner and should vanish. */
    public static final long STALE_TICKS = 120;

    private static final Map<Integer, Line> LINES = new HashMap<>();

    private ClientLineState() {}

    public static void accept(LineSyncPacket p) {
        if (!p.active) {
            LINES.remove(p.playerId);
            return;
        }
        Line line = LINES.get(p.playerId);
        if (line == null) {
            line = new Line();
            line.smoothProgress = p.progress; // fresh cast: don't ease from a stale value
            LINES.put(p.playerId, line);
        }
        line.target = p.target;
        line.progress = p.progress;
        line.color = p.color;
        line.floatKind = p.floatKind;
        line.biting = p.biting;
        line.tension = p.tension;
        line.rodLoad = p.rodLoad;
        line.fighting = p.fighting;
        line.running = p.running;
        line.course = p.course;
        line.lastUpdate = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    /**
     * §fight-camera (0.8.0): the PRIMARY fight input is the camera — hold your view AGAINST the run,
     * like steering the rod in a fishing simulator. The read is the ROTATION DELTA from an anchor
     * stored at each course change (the input FightCourse's design notes blessed when the analogue
     * fight was shelved): a lean relative to where you were, so countering never means facing away
     * from the water, and a controller right-stick works for free. Yaw right of the anchor = pulling
     * right; pitch above = lifting; below = laying the rod down.
     *
     * <p>§fight-keys stays as the QUIET secondary input — the four bindings still override the camera
     * while held, but nothing advertises them any more; the rod, the line and the boss bar all speak
     * in rod terms that fit both devices.
     *
     * <p>Polled on the tick; only edges are sent, so a whole fight is a handful of bytes.
     */
    public static void pollFightInput() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Line own = LINES.get(mc.player.getId());
        byte dir = FightInputPacket.NONE;
        // §26.2: the screen field moved behind the gui (same seam JournalScreen carries).
        //? if <26.2 {
        boolean noScreen = mc.screen == null;
        //?} else {
        /*boolean noScreen = mc.gui.screen() == null;
        *///?}
        if (own != null && own.fighting && noScreen) {
            if (own.course != anchorCourse) {
                // every new run re-anchors: gestures are relative, and the view never drifts away
                anchorCourse = own.course;
                anchorYaw = mc.player.getYRot();
                anchorPitch = mc.player.getXRot();
            }
            if (FightKeys.PULL_LEFT.isDown()) dir = FightInputPacket.PULL_LEFT;
            else if (FightKeys.PULL_RIGHT.isDown()) dir = FightInputPacket.PULL_RIGHT;
            else if (FightKeys.PUSH.isDown()) dir = FightInputPacket.PUSH;
            else if (FightKeys.LIFT.isDown()) dir = FightInputPacket.LIFT;
            else {
                float dYaw = Mth.degreesDifference(anchorYaw, mc.player.getYRot());
                float dPitch = mc.player.getXRot() - anchorPitch;   // MC pitch grows looking DOWN
                if (Math.abs(dYaw) >= CAMERA_DEAD_DEG || Math.abs(dPitch) >= CAMERA_DEAD_DEG) {
                    if (Math.abs(dYaw) >= Math.abs(dPitch)) {
                        dir = dYaw < 0 ? FightInputPacket.PULL_LEFT : FightInputPacket.PULL_RIGHT;
                    } else {
                        dir = dPitch < 0 ? FightInputPacket.LIFT : FightInputPacket.PUSH;
                    }
                }
            }
        } else {
            anchorCourse = -1;
        }
        if (dir != sentDir) {
            sentDir = dir;
            ModNetwork.toServer(new FightInputPacket(dir));
        }
    }

    /** §fight-camera: how far the view must lean off its anchor before it counts as pulling. */
    private static final float CAMERA_DEAD_DEG = 6f;
    private static byte sentDir;
    private static byte anchorCourse = -1;
    private static float anchorYaw, anchorPitch;

    /**
     * §fight-course: the local angler's rod lean, {yaw, pitch} in degrees. Zero when nothing is running.
     * Only the local player's rod is posed by {@link RodHandTransform}, so only theirs is asked for.
     */
    public static float[] ownLean() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return NO_LEAN;
        Line line = LINES.get(mc.player.getId());
        return line == null ? NO_LEAN : new float[]{line.leanYaw, line.leanPitch};
    }

    private static final float[] NO_LEAN = {0f, 0f};

    /** All visible lines, keyed by angler entity id — the renderer iterates (and expires) these. */
    public static Map<Integer, Line> lines() {
        return LINES;
    }

    /** Whether OUR OWN line is out — drives rod hold behaviour and the cast-power HUD. */
    public static boolean active() {
        var mc = Minecraft.getInstance();
        return mc.player != null && LINES.containsKey(mc.player.getId());
    }

    public static void clear() {
        LINES.clear();
    }
}
