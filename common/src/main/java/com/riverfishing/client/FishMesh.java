package com.riverfishing.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riverfishing.RiverFishing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * §fish-3d: a fish with a body, pulled out of its own sprite — no model, no entity.
 *
 * <p>Ninety-three species and no artist for ninety-three models, and an entity per fish is a server
 * object per fish, which scenery must never cost. So the third dimension is DERIVED: the sprite's
 * silhouette is read once per species — the top and bottom of the fish at each station along its
 * length — and the body is that outline given a cross-section. A fish is a slab: two near-vertical
 * flanks that carry the whole sprite, a thin cap on the back and one under the belly, the flanks
 * standing off the centreline by a width profile that peaks a third of the way back and tapers to
 * nothing at nose and tail. The same swimming wave the flat fish carries (§fish-wave) runs down the
 * centreline, so the body swims.
 *
 * <p>The caps wear the sprite's edge texels, so the colour continues over the top and under the belly
 * rather than stopping at a seam — and from above you see a narrow back, not a flank. Ten segments of
 * six quads is a hundred and twenty triangles, which at a hundred and sixty fish is nothing.
 *
 * <p>Faces that look away from the camera are not emitted. The shoal draws translucent and cannot use
 * the culling render type (see ShoalRenderer's §shoal-layer note), and a closed translucent body drawn
 * both sides reads at 1-(1-a)^2 where its two flanks overlap. Dropping the far faces by hand keeps one
 * surface under every pixel, which is the same property the flat fish had.
 *
 * <p>ROLLBACK: {@code /rfrod fish3d off} returns every fish to the flat wave, persisted client-side, no
 * rebuild. The flat path is untouched and is also what a species falls back to when its sprite cannot
 * be read.
 */
public final class FishMesh {
    /** Stations along the body. Ten is where more stops being visible at fish size. */
    private static final int STATIONS = 10;
    /** Half-width of the body at its fattest, as a fraction of length. A fish is a slim thing. */
    private static final float HALF_WIDTH = 0.075f;

    /** species -> silhouette, or an empty array when the sprite could not be read (then: flat fish). */
    private static final Map<ResourceLocation, float[][]> SIL = new HashMap<>();

    private FishMesh() {}

    /**
     * The outline: for each of STATIONS+1 columns across the fish's opaque span, {top, bottom} as
     * fractions of the sprite's height measured from its centre (negative = above), and the span
     * itself as {u-from, u-to} fractions of the sprite width in the last row.
     */
    private static float[][] silhouette(ResourceLocation species) {
        return SIL.computeIfAbsent(species, id -> {
            try {
                ResourceLocation tex = RiverFishing.id("textures/item/fish/" + id.getPath() + ".png");
                var res = Minecraft.getInstance().getResourceManager().getResource(tex);
                if (res.isEmpty()) return new float[0][];
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    int w = img.getWidth(), h = img.getHeight();
                    int x0 = w, x1 = -1;
                    for (int x = 0; x < w; x++) {
                        for (int y = 0; y < h; y++) {
                            if (alpha(img, x, y) > 24) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); break; }
                        }
                    }
                    if (x1 < x0 + 8) return new float[0][];
                    // §fish-3d-fins: the outline of the BODY, not of the fish. Any-alpha top and
                    // bottom gave a marlin a slab as tall as its sail and a slab across the fork of
                    // every tail, with the sprite's transparent cut-outs showing the caps through them
                    // as stripes. So: the fin outline per station, then a body band no taller than
                    // 1.2x the median height of the middle stations, centred on that column's
                    // alpha-weighted middle so a dorsal spike does not drag it up. The fins themselves
                    // are drawn by the flat sprite the renderer lays down the centreline — thin,
                    // transparent, forked, exactly as drawn.
                    int[] finTop = new int[STATIONS + 1], finBot = new int[STATIONS + 1];
                    float[] mid = new float[STATIONS + 1];
                    for (int s = 0; s <= STATIONS; s++) {
                        int x = x0 + (int) Math.round((x1 - x0) * (s / (double) STATIONS));
                        x = Mth.clamp(x, x0, x1);
                        int top = -1, bot = -1;
                        double sum = 0, wsum = 0;
                        for (int y = 0; y < h; y++) {
                            int al = 0;
                            for (int dx = -1; dx <= 1; dx++) al = Math.max(al, alpha(img, Mth.clamp(x + dx, 0, w - 1), y));
                            if (al > 24) {
                                if (top < 0) top = y;
                                bot = y;
                                sum += y * al;
                                wsum += al;
                            }
                        }
                        if (top < 0) { top = h / 2 - 1; bot = h / 2 + 1; sum = h / 2.0; wsum = 1; }
                        finTop[s] = top;
                        finBot[s] = bot;
                        mid[s] = (float) (sum / wsum);
                    }
                    int[] heights = new int[5];
                    for (int s = 3; s <= 7; s++) heights[s - 3] = finBot[s] - finTop[s];
                    java.util.Arrays.sort(heights);
                    float bodyH = heights[2] * 1.2f;
                    float[][] out = new float[STATIONS + 2][];
                    for (int s = 0; s <= STATIONS; s++) {
                        float half = Math.min(finBot[s] - finTop[s], bodyH) / 2f;
                        float top = Math.max(finTop[s], mid[s] - half);
                        float bot = Math.min(finBot[s] + 1, mid[s] + half);
                        out[s] = new float[]{(top - h / 2f) / h, (bot - h / 2f) / h};
                    }
                    out[STATIONS + 1] = new float[]{x0 / (float) w, (x1 + 1) / (float) w};
                    return out;
                }
            } catch (Exception e) {
                RiverFishing.LOGGER.warn("§fish-3d: could not read the sprite for {} ({}); drawing it flat", id, e.toString());
                return new float[0][];
            }
        });
    }

    private static int alpha(NativeImage img, int x, int y) {
        return (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
    }

    /**
     * Emit the body into the shoal's buffer, in the fish's local frame: nose at -size/2 on X, tail at
     * +size/2, Y up, Z across. Returns false when this species has no readable sprite, and the caller
     * draws the flat fish instead.
     */
    public static boolean emit(Matrix4f m, VertexConsumer vc, TextureAtlasSprite sp, ResourceLocation species,
                               float size, ShoalSim.Fish f, float time, int alpha, int tint, int overlay) {
        float[][] sil = silhouette(species);
        if (sil.length == 0) return false;
        float r = size / 2f;
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        float su0 = sil[STATIONS + 1][0], su1 = sil[STATIONS + 1][1];

        // The swimming wave, as on the flat fish: grows toward the tail, runs on the tail's beat.
        float amp = size * (0.05f + 0.09f * f.kick);
        float phase = time * (0.35f + 0.45f * f.kick) + f.phase * 5f;

        // §fish-3d-section: six vertices a ring — ridge, upper flank, lower flank, keel, each side.
        // The first cut was a four-point diamond, which put the sprite's top half on a face sloping
        // 45 degrees from the ridge: from the side the fish was squashed with its dorsal folded over
        // the spine, and from above you saw half a flank mirrored across the ridge — a fish lying on
        // its side. A fish is a slab: near-vertical flanks that carry the whole sprite, and thin caps
        // on the back and the belly that carry only the edge colour, so the side view is the picture
        // and the top view is a narrow back.
        float[][] ring = new float[STATIONS + 1][];
        float[] us = new float[STATIONS + 1];
        float[] vt = new float[STATIONS + 1], vb = new float[STATIONS + 1];
        for (int s = 0; s <= STATIONS; s++) {
            float t = s / (float) STATIONS;
            float x = -r + t * size;
            float top = -sil[s][0] * size, bot = -sil[s][1] * size;   // sprite rows grow downward
            float h = Math.max(0.02f * size, top - bot);
            float body = (float) Math.sin(Math.PI * Math.pow(t, 0.75));
            float half = size * HALF_WIDTH * body;
            float wave = amp * t * t * Mth.sin(phase - t * 6.5f);
            float up = top - h * 0.12f, lo = bot + h * 0.12f;
            ring[s] = new float[]{
                    x, top, wave,             // 0 ridge
                    x, up, wave + half,       // 1 +Z upper flank
                    x, lo, wave + half,       // 2 +Z lower flank
                    x, bot, wave,             // 3 keel
                    x, lo, wave - half,       // 4 -Z lower flank
                    x, up, wave - half};      // 5 -Z upper flank
            us[s] = u0 + (u1 - u0) * (su0 + (su1 - su0) * t);
            vt[s] = v0 + (v1 - v0) * Mth.clamp(0.5f + sil[s][0] + 0.005f, 0f, 1f);
            vb[s] = v0 + (v1 - v0) * Mth.clamp(0.5f + sil[s][1] - 0.005f, 0f, 1f);
        }

        for (int s = 0; s < STATIONS; s++) {
            float[] a = ring[s], b = ring[s + 1];
            float ua = us[s], ub = us[s + 1];
            // The flanks carry the sprite top to bottom; the caps carry one edge row of it.
            quad(m, vc, a, b, 0, 1, ua, ub, vt[s], vt[s], alpha, tint, overlay);     // back, +Z
            quad(m, vc, a, b, 1, 2, ua, ub, vt[s], vb[s], alpha, tint, overlay);     // +Z flank
            quad(m, vc, a, b, 2, 3, ua, ub, vb[s], vb[s], alpha, tint, overlay);     // belly, +Z
            quad(m, vc, a, b, 3, 4, ua, ub, vb[s], vb[s], alpha, tint, overlay);     // belly, -Z
            quad(m, vc, a, b, 4, 5, ua, ub, vb[s], vt[s], alpha, tint, overlay);     // -Z flank
            quad(m, vc, a, b, 5, 0, ua, ub, vt[s], vt[s], alpha, tint, overlay);     // back, -Z
        }
        return true;
    }

    /** One strip quad between ring vertices i and j of stations a and b, skipped when it faces away. */
    private static void quad(Matrix4f m, VertexConsumer vc, float[] a, float[] b, int i, int j,
                             float ua, float ub, float va, float vb2, int alpha, int tint, int overlay) {
        float ax = a[i * 3], ay = a[i * 3 + 1], az = a[i * 3 + 2];
        float bx = b[i * 3], by = b[i * 3 + 1], bz = b[i * 3 + 2];
        float cx = b[j * 3], cy = b[j * 3 + 1], cz = b[j * 3 + 2];
        float dx = a[j * 3], dy = a[j * 3 + 1], dz = a[j * 3 + 2];
        // Face normal in local space, then into view space through the pose to decide if it looks at us.
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
        float e2x = dx - ax, e2y = dy - ay, e2z = dz - az;
        float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return;
        nx /= len; ny /= len; nz /= len;
        // The stack began as identity AT the camera, so the fish's position in view space is column 3
        // and the normal transforms by the upper 3x3; their dot says which way this face looks.
        float vnx = m.m00() * nx + m.m10() * ny + m.m20() * nz;
        float vny = m.m01() * nx + m.m11() * ny + m.m21() * nz;
        float vnz = m.m02() * nx + m.m12() * ny + m.m22() * nz;
        if (vnx * m.m30() + vny * m.m31() + vnz * m.m32() > 0f) return;   // facing away: not drawn
        vertex(m, vc, ax, ay, az, ua, va, nx, ny, nz, alpha, tint, overlay);
        vertex(m, vc, bx, by, bz, ub, va, nx, ny, nz, alpha, tint, overlay);
        vertex(m, vc, cx, cy, cz, ub, vb2, nx, ny, nz, alpha, tint, overlay);
        vertex(m, vc, dx, dy, dz, ua, vb2, nx, ny, nz, alpha, tint, overlay);
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int alpha, int tint, int overlay) {
        // Full-bright like the flat fish — the fade is the alpha. The normal is world UP, as on the
        // flat fish: the entity shader mixes directional light by the normal, and a real normal made
        // the nose a black blob head-on and the flanks flicker as the fish turned.
        // §1.20.1: the old builder chain, closed with endVertex().
        vc.vertex(m, x, y, z)
                .color((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, alpha)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0f, 1f, 0f)
                .endVertex();
    }
}
