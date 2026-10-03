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
        public net.minecraft.world.phys.Vec3 shownEnd;   // §line-glide: the drawn water end, eased between block centres
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

        // §hooked-fish: the fish on the line. The server says WHAT it is and what it is doing (a run
        // and its course, a breach, a head-shake, how spent it is); the client carries WHERE it is —
        // an offset from the line's water end, integrated every frame the way the shoal carries its
        // own fish — so the body moves at frame rate and nothing on the wire changed cadence.
        public String species = "";
        public int weightG, lengthCm;
        public boolean jumping, shaking, snagged;
        public float fatigue;
        public double fx, fy, fz;        // offset from the line's water end, blocks
        public float heading;            // radians, world; which way the body points
        public float tail;               // tail phase
        public float jumpT = -1f;        // -1 idle; 0..1 through a breach
        public float pitch;              // degrees, nose up (-) / down (+)
        public net.minecraft.world.item.ItemStack stack;   // the drawn item, rebuilt when the species changes
        public String stackSpecies = "";
        public boolean wasInAir;         // for the splash on the way out and the way back
        /** §hooked-fish: client game time the take began, -1 when none is on — the body climbs over its first eight ticks. */
        public long riseStart = -1;
        /**
         * §fight-moves: the fish's bearing round the rod, the server's and the drawn one (radians, − = the angler's
         * left), the move under way, and where the rod holds the line. The fish is the line's end turned round
         * that point — so a run that takes line carries it OUT, and a side run swings it round.
         */
        public float swing, swingShown;
        /**
         * §rod-follows-fish: how fast the DRAWN fish crosses the angler's view, blocks/s, + = to the right, eased. The
         * rod leans and bends off this, not off the run's course byte: the course arrives the instant the server
         * starts a run, the fish swings round at a fish's pace, and the rod went over before the fish had moved
         * (Besoulq, 26.3 beta).
         */
        public float sideSpeed;
        public byte move;
        public net.minecraft.world.phys.Vec3 pivot;
        /** Where the body was drawn last — the lift-out and the getaway start from here. */
        public net.minecraft.world.phys.Vec3 lastFishAt;
        private net.minecraft.world.phys.Vec3 prevAt;
        /** Eased: 0 upright, 75 on its side — a beaten fish, or a plank. */
        public float roll;
        public double depth;             // §hooked-dim: blocks under the surface this frame
        /** §fight-depth: where the bait hung and where the bed is, blocks under the surface (0 = not told). */
        public float hookDepth, bottomDepth;
        /** A beaten fish stays on its side through a twitch; only a real run or a breach rights it. */
        private boolean beatenShown;
        private float runFor;

        /** §fight-depth: how deep the body is drawn on the take — at the bait. */
        public float takeDepth() {
            return Math.max(0.05f, hookDepth);
        }

        /**
         * §hooked-fish: one frame of the body. {@code fwd} points from the angler to the water end,
         * horizontal and unit; {@code side} is its left-hand perpendicular — "LEFT" on a course means
         * the angler's left, which is what the rod lean and the bar already mean by it.
         */
        public double swimSpeed;         // blocks/s this frame — ramps, so a run starts like a fish, not a bullet

        public void tickFish(float dt, double fwdX, double fwdZ, net.minecraft.world.phys.Vec3 base, net.minecraft.world.phys.Vec3 pivot) {
            this.pivot = pivot;
            if (!fighting || species.isEmpty()) {
                boolean taking = biting && !species.isEmpty();
                if (taking) heading = (float) Math.atan2(fwdZ, fwdX);   // §hooked-fish: a fish on the take faces away from the angler
                fx *= Math.max(0f, 1f - dt * 4f); fz *= Math.max(0f, 1f - dt * 4f);
                // §fight-depth: on the take it is down at the bait, so the hookset starts the fight from there
                fy = taking ? -takeDepth() : fy * Math.max(0f, 1f - dt * 4f);
                beatenShown = false;
                runFor = 0f;
                jumpT = -1f;
                swingShown = swing;
                sideSpeed = 0f;
                prevAt = null;
                return;
            }
            fx = fz = 0.0;   // §fight-moves: the bearing carries it now, not an offset off the line's end
            // the server moves the bearing at a fish's pace every tick and says so every fifth: follow it smoothly
            float swingWas = swingShown;
            swingShown = Mth.lerp(Math.min(1f, dt * 5f), swingShown, swing);
            boolean charging = move == com.riverfishing.fishing.FightMoves.CHARGE || move == com.riverfishing.fishing.FightMoves.CHARGE_SLACK;
            // beaten: on its side and towed in — and it stays so through a twitch of the line (the server's short
            // runs and head-shakes flipped it upright and back every second); a real run or a breach rights it
            runFor = running ? runFor + dt : 0f;
            if (move == com.riverfishing.fishing.FightMoves.PLANK || (fatigue >= 0.75f && smoothProgress >= 0.7f && !running)) beatenShown = true;
            else if (runFor > 0.5f || jumping || fatigue < 0.6f) beatenShown = false;
            boolean beaten = beatenShown;
            // §fight-depth: how deep. It fights down where it took the bait, and towards the bed, and comes up only as
            // it tires and nears the bank; a dive or a sulk goes to the bottom, the weed holds it under, a charging or
            // jump-bound fish rides high, a beaten one lies on the surface. Never below the bed at the cast.
            float bottom = bottomDepth > 0f ? bottomDepth : 3f, hook = hookDepth > 0f ? hookDepth : 0.2f;
            double deep = Math.min(bottom, Math.max(hook, Math.min(bottom * 0.6, 3.0)));
            double up = Mth.clamp(fatigue * 0.9 + smoothProgress * 0.5, 0.0, 1.0);
            double rest = Mth.lerp(up * up, deep, 0.2);
            double reach = Mth.clamp(2.5 + lengthCm / 50.0, 2.0, 6.0) * (1.0 - 0.45 * fatigue)
                    * (0.3 + 0.7 * (1.0 - Mth.clamp(smoothProgress, 0f, 1f)));
            double ty = -rest;
            float tPitch = 0f;
            if (move == com.riverfishing.fishing.FightMoves.SULK || (running && course == 3)) { ty = -Math.min(bottom, Math.max(rest, reach * 0.8)); tPitch = 28f; }
            else if (move == com.riverfishing.fishing.FightMoves.WEEDED) ty = -Math.min(bottom, Math.max(rest, 0.7));
            else if (running && course == 4) { ty = -0.1; tPitch = -25f; }
            else if (charging || move == com.riverfishing.fishing.FightMoves.TORPEDO) ty = -Math.min(rest, 0.3);
            else if (running) ty = -Math.min(bottom, rest + 0.4);
            if (beaten) ty = -0.02;
            // a breach: an arc over three quarters of a second, then back to the surface
            if (jumping && jumpT < 0f) jumpT = 0f;
            if (jumpT >= 0f) {
                jumpT += dt / 0.75f;
                if (jumpT >= 1f) jumpT = jumping ? 0.999f : -1f;
            }
            fy = Mth.lerp(Math.min(1f, dt * 2.2f), fy, ty);
            // a head-shake, the weed, or straining on a snag: a sideways shudder at a fish's rate — a few beats a
            // second, wider on a big fish — a DISPLAY offset, never folded into the eased position
            // §candle: a fish in the air shakes its head to throw the hook — the same shudder
            double sideX = -fwdZ, sideZ = fwdX;
            double j = (shaking || snagged || move == com.riverfishing.fishing.FightMoves.WEEDED || jumpT >= 0f)
                    ? Math.sin(tail * 2.4) * (0.10 + lengthCm / 700.0) : 0.0;
            jx = sideX * j; jz = sideZ * j;
            double jumpY = jumpT >= 0f ? Math.sin(Math.PI * jumpT) * (1.0 + lengthCm / 120.0) : 0.0;
            // §candle: it leaves the water standing on its tail, hangs near-vertical at the top
            // and only tips over on the way down — hence the squared term rather than a flat flip.
            if (jumpT >= 0f) { fy = Math.max(fy, -0.05) ; tPitch = -78f + 140f * jumpT * jumpT; }
            // heading: at the rod when it charges or is towed in beaten; else the way it moved; else away
            net.minecraft.world.phys.Vec3 at = com.riverfishing.fishing.FightMoves.swung(pivot, base, swingShown);
            double vx = prevAt == null ? 0 : at.x - prevAt.x, vz = prevAt == null ? 0 : at.z - prevAt.z;
            prevAt = at;
            double r = Math.hypot(at.x - pivot.x, at.z - pivot.z);
            sideSpeed = Mth.lerp(Math.min(1f, dt * 8f), sideSpeed, (float) ((swingShown - swingWas) * r / Math.max(dt, 1e-3f)));
            float want = charging || beaten ? (float) Math.atan2(pivot.z - at.z, pivot.x - at.x)
                    : (vx * vx + vz * vz) > 1e-6 ? (float) Math.atan2(vz, vx)
                    : (float) Math.atan2(at.z - pivot.z, at.x - pivot.x);
            float d = want - heading;
            while (d > Math.PI) d -= (float) (2 * Math.PI);
            while (d < -Math.PI) d += (float) (2 * Math.PI);
            heading += d * Math.min(1f, dt * (running || charging ? 6f : 3f));
            pitch = Mth.lerp(Math.min(1f, dt * 6f), pitch, tPitch);
            // a flatfish is drawn lying flat already: rolled again it would stand on its edge
            float lie = beaten && jumpT < 0f && !com.riverfishing.fish.FishPose.isFlat(species) ? 75f : 0f;
            roll = Mth.lerp(Math.min(1f, dt * 3f), roll, lie);
            tail += dt * (running || charging ? 13f : beaten ? 3f : 6f) * (1f - 0.5f * fatigue);
            fyJump = (float) jumpY;
            lastFishAt = at.add(jx, fy + fyJump, jz);
        }

        /** The breach's lift above the eased offset — kept apart so the arc is not eased away. */
        public float fyJump;
        public double jx, jz;            // the shudder, this frame
        /** §rod-anim: where the line left the rod tip last frame (world), and LineFx's per-tick memory. */
        public net.minecraft.world.phys.Vec3 lastTipW;
        long fxTick;
        float fxTaut;
        net.minecraft.world.phys.Vec3 fxEntry;
        /** §line-calm: the kink as DRAWN — chases the clipped point instead of jumping to it. */
        public net.minecraft.world.phys.Vec3 kinkShown;

        /** §rod-follows-fish: -1..1, the course byte's sign (+1 = the fish going LEFT), full at 1.5 blocks/s across. */
        public float sideLean() {
            return Mth.clamp(-sideSpeed / 1.5f, -1f, 1f);
        }

        /** Where the body is this frame, given the line's water end. */
        public net.minecraft.world.phys.Vec3 fishAt(net.minecraft.world.phys.Vec3 end) {
            net.minecraft.world.phys.Vec3 at = pivot == null || !fighting ? end
                    : com.riverfishing.fishing.FightMoves.swung(pivot, end, swingShown);   // §fight-moves
            return at.add(fx + jx, fy + fyJump, fz + jz);
        }

        /** Eases the rendered progress toward the server value; call once per frame. */
        public void tickSmoothing(float frameSeconds) {
            smoothProgress = Mth.lerp(Math.min(1f, frameSeconds * 6f), smoothProgress, progress);
            // §line-glide: the water end walks between the server's block centres (a drifting float, a retrieve)
            // instead of jumping; a fresh cast, or anything six blocks off, snaps
            net.minecraft.world.phys.Vec3 tc = new net.minecraft.world.phys.Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            shownEnd = shownEnd == null || shownEnd.distanceToSqr(tc) > 36.0 ? tc : shownEnd.lerp(tc, Math.min(1f, frameSeconds * 4f));
            smoothTension = Mth.lerp(Math.min(1f, frameSeconds * 8f), smoothTension, tension);
            smoothRodLoad = Mth.lerp(Math.min(1f, frameSeconds * 8f), smoothRodLoad, rodLoad);
            // §fight-course: the tip is DRAGGED the way the fish is going — that is the read, and it is
            // also what physically happens. Eased hard enough to be unmistakable but not snappy, so a
            // run reads as the rod being pulled over rather than as the item teleporting.
            // The sign is what the bar says, not the opposite of it: a fish going LEFT drags the tip
            // LEFT. The first build had these the wrong way round and the two cues contradicted.
            // §rod-follows-fish: with a fish drawn on the line the lean is how fast it is really going sideways
            float ty = fighting && !species.isEmpty() ? sideLean() : course == 1 ? 1f : course == 2 ? -1f : 0f;
            float tp = course == 3 ? 1f : course == 4 ? -1f : 0f;
            float k = Math.min(1f, frameSeconds * 5f);
            leanYaw = Mth.lerp(k, leanYaw, ty * RodHandTransform.COURSE_YAW);
            leanPitch = Mth.lerp(k, leanPitch, tp * RodHandTransform.COURSE_PITCH);

            // §line-taut-eased: shape the targets over a WIDE band (0.02..0.35 tension covers the
            // whole straightening arc), then chase them — tightening 3x faster than relaxing.
            float tautTarget = 0f, slackTarget = 0f;
            if (fighting) {
                // §line-rest: with the fish itself on the line, the angler HOLDS it — the string stays
                // most of the way straight to the body between runs, and never bellies. The slack belly
                // was the "it is coming at you" read from before the fish was drawn; the body is that
                // read now, and a two-block loop of string on a resting fish was all the belly said.
                boolean hooked = !species.isEmpty();
                // §fight-moves: a fish coming at you faster than the reel takes line — the one slack a hooked line shows
                boolean slack = move == com.riverfishing.fishing.FightMoves.CHARGE_SLACK;
                tautTarget = slack ? 0f : running ? 1f
                        : Math.max(hooked ? 0.6f : 0f, smoothstep(Mth.clamp((smoothTension - 0.02f) / 0.33f, 0f, 1f)));
                slackTarget = slack ? 0.8f : running || hooked ? 0f : Mth.clamp((0.10f - smoothTension) / 0.10f, 0f, 1f);
                // §rod-anim: a fish in the air takes the pull off — slack while it flies, snapped tight when it lands
                if (jumpT >= 0f && jumpT < 0.85f) { tautTarget = 0.05f; slackTarget = 0.5f; }
            } else if (isOwn()) {
                // §line-retrieve: winding draws the line straight to the lure; a pause lets it belly
                int r = RodAnim.retrieveLine();
                if (r == 1) tautTarget = 0.55f;
                else if (r == 2) slackTarget = 0.5f;
            }
            float kUp = Math.min(1f, frameSeconds * 12f);   // a jerk snaps the line tight
            float kDown = Math.min(1f, frameSeconds * 3f);  // slack develops at cable speed
            dispTaut = Mth.lerp(tautTarget > dispTaut ? kUp : kDown, dispTaut, tautTarget);
            dispSlack = Mth.lerp(slackTarget > dispSlack ? kDown : kUp, dispSlack, slackTarget);
        }

        private boolean isOwn() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && LINES.get(mc.player.getId()) == this;
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
        // §hooked-fish: the 40-tick refresh names no fish; during a take it must not wipe the one the take sent
        if (!p.species.isEmpty() || !p.biting || p.fighting) line.species = p.species;   // §hooked-fish
        long t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        if (p.biting && !p.fighting) { if (line.riseStart < 0) line.riseStart = t; } else line.riseStart = -1;
        line.weightG = p.weightG;
        line.lengthCm = p.lengthCm;
        line.jumping = p.jumping;
        line.shaking = p.shaking;
        line.fatigue = p.fatigue;
        line.snagged = p.snagged;
        line.swing = p.swing;   // §fight-moves
        line.move = p.move;
        if (p.bottomDepth > 0f) { line.hookDepth = p.hookDepth; line.bottomDepth = p.bottomDepth; }   // §fight-depth
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
        /*boolean noScreen = mc.screen == null;
        *///?} else {
        boolean noScreen = mc.gui.screen() == null;
        //?}
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

    /** §jig-2: our own line is out and nothing is happening on it — a click over an ice hole is a hold. */
    public static boolean selfCalm() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Line l = LINES.get(mc.player.getId());
        return l != null && !l.biting && !l.fighting;
    }

    /** Whether OUR OWN line is out — drives rod hold behaviour and the cast-power HUD. */
    /**
     * How loaded THIS player's blank is, 0..1, eased. Zero when nothing is on — a rod at rest is
     * a straight rod, and the bend has to fall to nothing rather than hold its last value.
     */
    public static float ownRodLoad() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return 0f;
        Line own = lines().get(mc.player.getId());
        return own == null ? 0f : own.smoothRodLoad;
    }

    public static boolean active() {
        var mc = Minecraft.getInstance();
        return mc.player != null && LINES.containsKey(mc.player.getId());
    }

    public static void clear() {
        LINES.clear();
    }
}
