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
        public float tension;          // §rod-bend: authoritative fight tension 0..1
        public float smoothTension;    // eased for the in-hand bend
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
        public long lastUpdate;        // client game time of the last packet (staleness check)

        /** Eases the rendered progress toward the server value; call once per frame. */
        public void tickSmoothing(float frameSeconds) {
            smoothProgress = Mth.lerp(Math.min(1f, frameSeconds * 6f), smoothProgress, progress);
            smoothTension = Mth.lerp(Math.min(1f, frameSeconds * 8f), smoothTension, tension);
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
        line.fighting = p.fighting;
        line.running = p.running;
        line.course = p.course;
        line.lastUpdate = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    /**
     * §fight-course: poll the movement keys during our own fight and tell the server when they change.
     *
     * <p>Polled rather than hooked because this version of MC only ships raw movement input to the server
     * for players riding a vehicle. Only edges are sent, so a whole fight is a handful of bytes; leaving the
     * fight sends one final "hands off".
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
        // §fight-keys: the rod's own bindings, NOT the movement keys. Reading WASD here is what walked
        // people off piers while they answered the boss bar — the course never needed the movement, only
        // the keypress. WASD now means feet and nothing else (§fight-footwork).
        if (own != null && own.fighting && noScreen) {
            if (FightKeys.PULL_LEFT.isDown()) dir = FightInputPacket.PULL_LEFT;
            else if (FightKeys.PULL_RIGHT.isDown()) dir = FightInputPacket.PULL_RIGHT;
            else if (FightKeys.PUSH.isDown()) dir = FightInputPacket.PUSH;
            else if (FightKeys.LIFT.isDown()) dir = FightInputPacket.LIFT;
        }
        if (dir != sentDir) {
            sentDir = dir;
            ModNetwork.toServer(new FightInputPacket(dir));
        }
    }

    private static byte sentDir;

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
