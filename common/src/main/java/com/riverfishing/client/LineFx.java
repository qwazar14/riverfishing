package com.riverfishing.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * §rod-anim: what the line does to the water, and what is left of it when the fish is gone.
 * <ul>
 *   <li>A line under a running fish CUTS the water: a hiss of spray where it goes in and a wake of bubbles
 *       trailing the cut; a line that snaps tight throws a spray of drops off itself.</li>
 *   <li>A snapped line leaves a limp piece hanging off the tip that falls and curls away; a thrown hook
 *       whips the whole line back through the air toward the rod. Both fade in about a second.</li>
 * </ul>
 * Every line's, not just ours: the anglers around you fight in the same water.
 */
public final class LineFx {
    private LineFx() {}

    private record Snap(Vec3 tip, Vec3 end, long start, boolean broke, int color) {}

    private static final List<Snap> SNAPS = new ArrayList<>();
    private static final long SNAP_NS = 1_200_000_000L;

    /** Once a game tick per line, from the line pass: the cut, the wake, the spray. */
    static void tick(Minecraft mc, ClientLineState.Line s, Vec3 tip, Vec3 end) {
        if (mc.level == null || !s.fighting || tip == null) return;
        long t = mc.level.getGameTime();
        if (t == s.fxTick) return;
        s.fxTick = t;
        double surf = s.target.getY() + 0.9;
        // where the string goes into the water: the crossing of the surface, or its end if it never dips under
        Vec3 entry;
        if (tip.y > surf && end.y < surf) {
            double f = (tip.y - surf) / (tip.y - end.y);
            entry = tip.lerp(end, f);
        } else {
            entry = new Vec3(end.x, surf, end.z);
        }
        var r = mc.level.getRandom();
        boolean hard = s.running || s.dispTaut > 0.85f;
        if (hard && r.nextInt(3) != 0) {
            mc.level.addParticle(ParticleTypes.SPLASH, entry.x + (r.nextDouble() - 0.5) * 0.15, surf,
                    entry.z + (r.nextDouble() - 0.5) * 0.15, 0, 0.12, 0);
        }
        if (s.running && s.fxEntry != null) {
            // the wake: bubbles left behind where the line has just cut through
            double dx = entry.x - s.fxEntry.x, dz = entry.z - s.fxEntry.z, dl = Math.sqrt(dx * dx + dz * dz);
            double bx = dl > 1e-3 ? -dx / dl : 0, bz = dl > 1e-3 ? -dz / dl : 0;
            for (int k = 0; k < 2; k++) {
                double back = 0.2 + r.nextDouble() * 0.5, side = (r.nextDouble() - 0.5) * 0.4;
                mc.level.addParticle(ParticleTypes.FISHING, entry.x + bx * back - bz * side, surf,
                        entry.z + bz * back + bx * side, 0, 0.0, 0);
            }
        }
        // snapped tight this tick: drops fly off the wet part of the line
        if (s.dispTaut - s.fxTaut > 0.22f) {
            for (int k = 0; k < 7; k++) {
                double f = 0.55 + r.nextDouble() * 0.45;   // the stretch nearest the water is the wet one
                Vec3 p = tip.lerp(entry, f);
                mc.level.addParticle(ParticleTypes.SPLASH, p.x, p.y, p.z,
                        (r.nextDouble() - 0.5) * 0.1, 0.12 + r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.1);
            }
        }
        s.fxTaut = s.dispTaut;
        s.fxEntry = entry;
    }

    /** A breach comes down: the splash where it lands. */
    static void landingSplash(Minecraft mc, ClientLineState.Line s) {
        if (mc.level == null || s.lastFishAt == null) return;
        var r = mc.level.getRandom();
        double surf = s.target.getY() + 0.9;
        for (int k = 0; k < 14 + s.lengthCm / 8; k++) {
            mc.level.addParticle(ParticleTypes.SPLASH, s.lastFishAt.x + (r.nextDouble() - 0.5) * 0.9, surf,
                    s.lastFishAt.z + (r.nextDouble() - 0.5) * 0.9, 0, 0.2, 0);
        }
    }

    /** The fish is gone; unless it was landed, the line shows how. Called while the line still exists. */
    public static void gone(ClientLineState.Line line, boolean landed, boolean broke) {
        if (landed || line == null || line.lastTipW == null) return;
        Vec3 end = line.lastFishAt != null ? line.lastFishAt : line.shownEnd;
        if (end == null) return;
        SNAPS.add(new Snap(line.lastTipW, end, System.nanoTime(), broke, line.color));
    }

    static boolean any() {
        return !SNAPS.isEmpty();
    }

    /** Draws the snapped and thrown lines into the line pass's lines() buffer (asked for at the point of use). */
    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f nrm) {
        long now = System.nanoTime();
        for (Iterator<Snap> it = SNAPS.iterator(); it.hasNext(); ) {
            Snap s = it.next();
            long age = now - s.start;
            if (age > SNAP_NS) { it.remove(); continue; }
            float a = age / 1.0e9f;
            int alpha = (int) (255 * Math.max(0f, 1f - a / 1.2f));
            int cr = (s.color >> 16) & 0xFF, cg = (s.color >> 8) & 0xFF, cb = s.color & 0xFF;
            Vec3 d = s.end.subtract(s.tip);
            Vec3 prev = s.tip;
            for (int k = 1; k <= 12; k++) {
                double f = k / 12.0;
                Vec3 p;
                if (s.broke) {
                    // the piece left on the rod: a third of the line, going limp, falling and curling
                    double len = 0.35 * (1.0 - 0.25 * a);
                    double wig = Math.sin(a * 18.0 + f * 9.0) * 0.08 * (1.0 - a) * f;
                    p = s.tip.add(d.x * f * len + wig, d.y * f * len - (1.6 * a * a + 0.5 * a) * f * f, d.z * f * len - wig);
                } else {
                    // a thrown hook: the whole line whips back up and toward the rod
                    double w = Math.min(1.0, a * 1.7);
                    double e = 1.0 - (1.0 - w) * (1.0 - w);
                    Vec3 tail = s.end.lerp(s.tip, e * 0.85).add(0, Math.sin(Math.PI * w) * 1.4, 0);
                    Vec3 chord = tail.subtract(s.tip);
                    p = s.tip.add(chord.scale(f)).add(0, -Math.sin(Math.PI * f) * (0.6 * w + 0.2), 0);
                }
                LineRenderer.line(vc, m, nrm, prev, p, cr, cg, cb, alpha);
                prev = p;
            }
        }
    }
}
