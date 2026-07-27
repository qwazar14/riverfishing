package com.riverfishing.client;

import com.riverfishing.network.LineSyncPacket;
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
        public boolean bobber;         // float rigs show a bobber at the line end
        public boolean biting;         // bite in progress: bobber plunges / line twitches
        public float tension;          // §rod-bend: authoritative fight tension 0..1
        public float smoothTension;    // eased for the in-hand bend
        public boolean fighting;       // §pump-reel: the fight is on
        public boolean running;        // §pump-reel: the fish is taking line RIGHT NOW
        public long lastUpdate;        // client game time of the last packet (staleness check)

        /** Eases the rendered progress toward the server value; call once per frame. */
        public void tickSmoothing(float frameSeconds) {
            smoothProgress = Mth.lerp(Math.min(1f, frameSeconds * 6f), smoothProgress, progress);
            smoothTension = Mth.lerp(Math.min(1f, frameSeconds * 8f), smoothTension, tension);
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
        line.bobber = p.bobber;
        line.biting = p.biting;
        line.tension = p.tension;
        line.fighting = p.fighting;
        line.running = p.running;
        line.lastUpdate = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0;
    }

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
