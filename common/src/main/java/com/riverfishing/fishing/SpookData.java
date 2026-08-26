package com.riverfishing.fishing;

import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * §spook (0.7.0): how badly the fish at a spot have just been frightened, 0..1.
 *
 * <p>The deliberate counterpart to {@link FishingPressureData}: that is the LONG-lived record of a swim
 * being fished out, saved with the world and measured in in-game days. This is the SHORT-lived one — it
 * lives only in memory, is never written to disk, and is gone in under two minutes. Losing it on a restart
 * is correct behaviour, not a defect.
 *
 * <p>It is a field, not a player flag, and that is the whole design. Running along the bank frightens the
 * water <em>around you</em>; a bait sitting twenty blocks out does not care. What frightens the fish out
 * there is the cast landing on their heads. So every source of disturbance has a position and a reach, and
 * the bite roll asks about the water where the bait actually is.
 *
 * <p>Recovery is where the water's character shows: a murky swamp or a deep lake forgets you in about
 * thirty seconds, while clear shallows and a small pond stay wary for a minute and a half. Standing still
 * and crouching does not speed that up — it simply stops feeding it, which is the same thing from the
 * bank and much easier to reason about.
 */
public final class SpookData {
    /** Four blocks. Spook is a patch of water, not a block — and this keeps the map tiny. */
    private static final int CELL = 4;
    /** Everything in here expires in ninety seconds, so this cap is a leak guard, not a policy. */
    private static final int MAX_CELLS = 512;
    /** Rings at most this often per patch: the feedback is a hint, not a strobe. */
    private static final int RING_EVERY = 20;

    private static final Map<Level, SpookData> BY_LEVEL = new WeakHashMap<>();

    private final Map<Long, Cell> cells = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cell> eldest) {
            return size() > MAX_CELLS;
        }
    };

    private SpookData() {}

    /** Server-thread only, like the water cache it leans on. */
    public static SpookData of(Level level) {
        return BY_LEVEL.computeIfAbsent(level, l -> new SpookData());
    }

    /** How frightened the fish are at this spot, 0..1, decayed up to {@code now}. */
    public double at(BlockPos pos, long now) {
        Cell c = cells.get(key(pos));
        return c == null ? 0.0 : c.value(now);
    }

    /**
     * Frighten the water around {@code surface}, falling off to nothing at {@code radius} blocks, and ring
     * the surface so the player can see they did it.
     *
     * @param surface a water surface block — the disturbance's own position, not the player's
     * @param amount  how much spook lands at the centre, 0..1
     */
    public void disturb(ServerLevel level, BlockPos surface, double amount, double radius, long now) {
        if (amount <= 0.0) return;
        // The recovery rate is read at the disturbance's centre and shared by every patch it touches — a
        // single event reaches a few blocks, and water does not change character across six of them.
        // Worked out LAZILY, and therefore at most once per patch ever: it classifies the water body,
        // which is a flood fill, and this runs four times a second per moving player.
        double[] perTick = {-1.0};
        int reach = Math.max(0, (int) Math.ceil(radius / CELL));
        int cx = Math.floorDiv(surface.getX(), CELL), cz = Math.floorDiv(surface.getZ(), CELL);
        Cell centre = null;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                // Distance from the event to this patch's middle, so the falloff is smooth in blocks
                // rather than stepping at cell borders.
                double px = (cx + dx) * CELL + CELL / 2.0, pz = (cz + dz) * CELL + CELL / 2.0;
                double d = Math.sqrt(Math.pow(px - (surface.getX() + 0.5), 2)
                        + Math.pow(pz - (surface.getZ() + 0.5), 2));
                if (d > radius) continue;
                double add = amount * (1.0 - d / radius);
                if (add < 0.01) continue;
                long k = ((long) (cx + dx) << 32) ^ ((cz + dz) & 0xffffffffL);
                Cell c = cells.get(k);
                if (c == null) {
                    if (perTick[0] < 0) perTick[0] = 1.0 / (20.0 * recoverySeconds(level, surface));
                    c = new Cell(perTick[0]);
                    cells.put(k, c);
                }
                c.add(add, now);
                if (dx == 0 && dz == 0) centre = c;
            }
        }
        if (centre != null && now - centre.lastRing >= RING_EVERY) {
            centre.lastRing = now;
            ring(level, surface);
        }
    }

    /**
     * The only thing that ever tells the player about this: rings running outward across the surface.
     * No message, no HUD — you either notice the water react or you do not (§spook-quiet).
     */
    private static void ring(ServerLevel level, BlockPos surface) {
        double y = surface.getY() + 0.92;
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a = i * (Math.PI * 2.0 / n);
            double dx = Math.cos(a), dz = Math.sin(a);
            // count 0 turns the offsets into a velocity, so each particle runs outward from the middle.
            level.sendParticles(ParticleTypes.FISHING,
                    surface.getX() + 0.5 + dx * 0.7, y, surface.getZ() + 0.5 + dz * 0.7,
                    0, dx, 0.0, dz, 0.22);
        }
    }

    /**
     * Seconds from full spook back to calm, 30..90. Murky and deep water forgets you quickly — the fish
     * cannot see the bank in the first place. Clear shallows and a small pond, where you are standing over
     * the fish in plain sight, stay wary far longer.
     */
    private static double recoverySeconds(ServerLevel level, BlockPos surface) {
        WaterBody body = WaterBodyCache.forLevel(level).get(level, surface);
        double clear = switch (body.type()) {
            case PUDDLE, POND -> 1.0;
            case LAKE -> 0.85;
            case RIVER -> 0.7;
            case SEA -> 0.6;
            case SWAMP -> 0.25;
            default -> 0.6;
        };
        int depth = FishingManager.measureDepth(level, surface);
        double shallow = Mth.clamp(1.0 - (depth - 2) / 8.0, 0.0, 1.0);
        double small = Mth.clamp(1.0 - (body.width() - 8) / 40.0, 0.0, 1.0);
        return 30.0 + 60.0 * (clear * 0.5 + shallow * 0.3 + small * 0.2);
    }

    private static long key(BlockPos pos) {
        return ((long) Math.floorDiv(pos.getX(), CELL) << 32)
                ^ (Math.floorDiv(pos.getZ(), CELL) & 0xffffffffL);
    }

    /** One patch of water. Decay is computed on read, so nothing has to be ticked. */
    private static final class Cell {
        private final double perTick;
        private double value;
        private long tick;
        private long lastRing = Long.MIN_VALUE;

        Cell(double perTick) {
            this.perTick = perTick;
        }

        double value(long now) {
            return Math.max(0.0, value - Math.max(0, now - tick) * perTick);
        }

        void add(double amount, long now) {
            value = Math.min(1.0, value(now) + amount);
            tick = now;
        }
    }
}
