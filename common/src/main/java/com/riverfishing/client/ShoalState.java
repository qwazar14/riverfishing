package com.riverfishing.client;

import com.riverfishing.network.ShoalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §shoal client state: every patch of water the server has described, and the fish swimming in it.
 *
 * <p>It began as one list and one global fade — every packet replaced the lot, so a patch missing from
 * the next one was gone that frame. Keying patches by their own centre fixed the replacement, but the
 * first attempt decided a patch had left by TIMING OUT since its last packet, and that was worse: the
 * server sends only when the shoal actually changes ({@code ShoalTracker} compares a signature), so a
 * calm shoal produces no packets at all and the timer deleted it after a few seconds. It came back on
 * the next change, which on screen is fish spawning and vanishing on a loop.
 *
 * <p>So departure is not a timer. A patch leaves when a packet arrives that does not mention it, which
 * is exactly when the server means it, and never otherwise. Silence means nothing has changed.
 *
 * <p>The fish themselves are simulated ({@link ShoalSim}) and are the same objects from one frame to the
 * next for as long as their patch lives. Nothing is placed by formula, so nothing can jump.
 */
public final class ShoalState {
    /** How long a departed shoal takes to swim off, in seconds — long enough to read as leaving. */
    private static final float FADE_OUT_SECONDS = 1.6f;
    /** And how quickly a new one eases in. */
    private static final float FADE_IN_SECONDS = 0.8f;

    /** One patch of water: what the server said about it, the fish in it, and how alive it is. */
    public static final class Live {
        public ShoalPacket.Spot spot;
        public ShoalSim.Fish[] fish;
        /** 0..1 — eased in on arrival, eased out once the server stops mentioning it. */
        public float fade;
        /** 0..1 — how far into fleeing this shoal is (§shoal-spook); drives speed, heading and depth. */
        public float flight;
        /** Set when a packet arrives without this patch in it. The only way a shoal ever leaves. */
        public boolean leaving;

        Live(ShoalPacket.Spot spot, ShoalSim.Fish[] fish) {
            this.spot = spot;
            this.fish = fish;
        }
    }

    /** Keyed by the patch's own centre, which is pinned to the world grid and therefore stable. */
    private static final Map<Long, Live> LIVE = new LinkedHashMap<>();
    private static volatile Level owner;
    private static volatile List<Live> snapshot = List.of();
    private static long lastNanos;

    private ShoalState() {}

    public static synchronized void accept(ShoalPacket p) {
        Level level = Minecraft.getInstance().level;
        if (level != owner) {
            LIVE.clear();
            owner = level;
        }
        if (level == null) return;

        // Anything the server no longer mentions is leaving — and only that.
        for (Live live : LIVE.values()) live.leaving = true;

        for (ShoalPacket.Spot s : p.spots()) {
            long key = s.centre().asLong();
            Live live = LIVE.get(key);
            if (live == null) {
                LIVE.put(key, new Live(s, ShoalSim.populate(level, s)));
            } else {
                // The same water, redescribed. The fish keep swimming from where they are; only when the
                // POPULATION changed is there anything to re-place, and then only the newcomers.
                if (live.fish.length != s.fish().size()) {
                    live.fish = resize(level, s, live.fish);
                }
                live.spot = s;
                live.leaving = false;
            }
        }
        snapshot = List.copyOf(new ArrayList<>(LIVE.values()));
    }

    /**
     * The population changed. Keep where the surviving fish ARE, but take what they are from the packet
     * that just arrived.
     *
     * <p>This used to arraycopy the old Fish objects over the new ones, which kept their positions — and
     * also their entries, which are final and came from the previous packet. So every time a patch's
     * count changed, the fish that stayed carried the OLD species, length and depth, and the new packet's
     * entries at those slots were dropped on the floor. Silent, and only visible as a fish that is the
     * wrong kind or the wrong size, which reads as the server being confused rather than the client.
     *
     * <p>Copying the coordinates the other way round gets both: no fish jumps, and nobody lies about
     * what it is. The phase is not copied because it is derived from the patch centre and the index, so
     * the new object already has the same one.
     */
    private static ShoalSim.Fish[] resize(Level level, ShoalPacket.Spot s, ShoalSim.Fish[] had) {
        ShoalSim.Fish[] fresh = ShoalSim.populate(level, s);
        int keep = Math.min(had.length, fresh.length);
        for (int i = 0; i < keep; i++) {
            ShoalSim.Fish was = had[i], now = fresh[i];
            now.x = was.x;
            now.y = was.y;
            now.z = was.z;
            now.heading = was.heading;
        }
        return fresh;
    }

    public static synchronized void clear() {
        LIVE.clear();
        owner = null;
        snapshot = List.of();
    }

    public static Level owner() {
        return owner;
    }

    /** The patches to draw this frame. */
    public static List<Live> live() {
        return snapshot;
    }

    /** Call once per frame: swims every fish, eases every fade, drops what has finished leaving. */
    public static synchronized void tick(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || level != owner || LIVE.isEmpty()) return;

        long nanos = System.nanoTime();
        double dt = lastNanos == 0L ? 0.05 : Math.min(0.1, (nanos - lastNanos) / 1_000_000_000.0);
        lastNanos = nanos;
        float time = level.getGameTime() + partialTick;
        Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition(partialTick);

        boolean dropped = false;
        for (var it = LIVE.values().iterator(); it.hasNext(); ) {
            Live live = it.next();
            float rate = (float) (dt / (live.leaving ? FADE_OUT_SECONDS : FADE_IN_SECONDS));
            live.fade = Mth.clamp(live.fade + ((live.leaving ? 0f : 1f) - live.fade) * Math.min(1f, rate * 3f),
                    0f, 1f);
            // §shoal-spook: the flight eases in too, so a splash does not teleport the shoal outward —
            // it breaks for open water over a second, the way a shoal actually scatters. A patch that is
            // leaving flees as it goes, so the last thing you see is fish swimming off, not a fade.
            float wanted = Math.max(live.spot.spookFraction(), live.leaving ? 1f : 0f);
            live.flight += (wanted - live.flight) * Math.min(1f, (float) dt * 1.5f);
            ShoalSim.advance(level, live.spot, live.fish, live.flight, eye, time, dt);
            if (live.leaving && live.fade <= 0.01f) {
                it.remove();
                dropped = true;
            }
        }
        if (dropped) snapshot = List.copyOf(new ArrayList<>(LIVE.values()));
    }
}
