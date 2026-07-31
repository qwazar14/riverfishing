package com.riverfishing.client;

import com.riverfishing.network.ShoalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §shoal client state: the shoals the server last described, each with a life of its own.
 *
 * <p>It used to be one list and one global fade: every packet replaced the lot, so a shoal that fell out
 * of the next one — because you took two steps, or because the water was spooked — was simply gone that
 * frame. On screen that is fish blinking out of existence, which is the one thing this feature is trying
 * not to look like.
 *
 * <p>Now each patch of water is kept under its own centre and carries its own fade, so one arriving does
 * not disturb the others and one leaving swims off over {@link #FADE_OUT_TICKS} rather than ceasing to
 * be. A patch also survives {@link #GRACE_TICKS} of silence before it begins to leave at all, which
 * covers the ordinary case of walking a bank and clipping the edge of the send radius: the shoal you are
 * looking at stays where it is instead of flickering with every packet.
 *
 * <p>Held statically because there is exactly one local player.
 */
public final class ShoalState {
    /** How long a departed shoal takes to swim off, in ticks — long enough to read as leaving. */
    private static final float FADE_OUT_TICKS = 30f;
    /** How long it is merely out of sight before it starts to. Two packets' worth, plus slack. */
    private static final long GRACE_TICKS = 90L;

    /** One patch of water: what the server said about it, and how alive it is right now. */
    public static final class Live {
        public final ShoalPacket.Spot spot;
        /** 0..1 — eased in on arrival, eased out once the server stops describing it. */
        public float fade;
        /** 0..1 — how far into fleeing this shoal is (§shoal-spook); the renderer scatters on it. */
        public float flight;
        /** Game time of the last packet that mentioned it. */
        public long seen;

        Live(ShoalPacket.Spot spot, long now) {
            this.spot = spot;
            this.seen = now;
        }
    }

    /** Keyed by the patch's own centre, which is pinned to the world grid and therefore stable. */
    private static final Map<Long, Live> LIVE = new LinkedHashMap<>();
    private static volatile Level owner;
    private static volatile List<Live> snapshot = List.of();

    private ShoalState() {}

    public static synchronized void accept(ShoalPacket p) {
        Level level = Minecraft.getInstance().level;
        if (level != owner) {
            LIVE.clear();
            owner = level;
        }
        long now = level == null ? 0L : level.getGameTime();
        for (ShoalPacket.Spot s : p.spots()) {
            Live was = LIVE.get(s.centre().asLong());
            Live live = new Live(s, now);
            if (was != null) {
                // The same water, redescribed: clarity and spook move, the fish rarely do. Whatever
                // visibility it had already earned is carried over, or every packet would re-fade it in.
                live.fade = was.fade;
                live.flight = was.flight;
            }
            LIVE.put(s.centre().asLong(), live);
        }
        snapshot = List.copyOf(new ArrayList<>(LIVE.values()));
    }

    public static synchronized void clear() {
        LIVE.clear();
        owner = null;
        snapshot = List.of();
    }

    public static Level owner() {
        return owner;
    }

    /** The patches to draw this frame, each with its own fade. */
    public static List<Live> live() {
        return snapshot;
    }

    /**
     * Call once per frame: eases every patch towards where it should be, and drops the ones that have
     * finished leaving.
     */
    public static synchronized void tick(float partialTick) {
        Level level = Minecraft.getInstance().level;
        if (level == null || level != owner || LIVE.isEmpty()) return;
        long now = level.getGameTime();
        float step = Math.min(1f, 0.06f * Math.max(1f, partialTick * 20f));
        float leaveStep = Math.min(1f, Math.max(1f, partialTick * 20f) / FADE_OUT_TICKS);

        boolean dropped = false;
        for (var it = LIVE.values().iterator(); it.hasNext(); ) {
            Live live = it.next();
            boolean leaving = now - live.seen > GRACE_TICKS;
            live.fade += ((leaving ? 0f : 1f) - live.fade) * (leaving ? leaveStep : step);
            live.fade = Math.max(0f, Math.min(1f, live.fade));
            // §shoal-spook: the flight itself eases, so a splash does not teleport the shoal outward —
            // it breaks for open water over a second or so, the way a real shoal scatters. A patch that
            // is leaving flees as it goes, so the last thing you see is fish heading away, not a fade.
            float wanted = Math.max(live.spot.spookFraction(), leaving ? 1f : 0f);
            live.flight += (wanted - live.flight) * step;
            if (leaving && live.fade <= 0.01f) {
                it.remove();
                dropped = true;
            }
        }
        if (dropped) snapshot = List.copyOf(new ArrayList<>(LIVE.values()));
    }
}
