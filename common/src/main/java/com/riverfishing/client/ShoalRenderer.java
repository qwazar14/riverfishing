package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.riverfishing.RiverFishing;
import com.riverfishing.network.ShoalPacket;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * §shoal render: the fish the water actually holds, drifting under the surface.
 *
 * <p>Each fish is ONE textured quad carrying its own item sprite — not the item model. That distinction is
 * the whole reason this can show seventy fish at once: the fish icons are 256px, and vanilla's
 * {@code item/generated} model turns a sprite that size into on the order of a thousand quads (a side
 * strip per pixel run of the silhouette). Seventy of those is several chunks' worth of geometry rebuilt
 * every frame. Seventy single quads is nothing, and it buys real alpha into the bargain.
 *
 * <p>Visibility is deliberately generous. A player who leans over the water and looks should be able to
 * make out what is sitting on the bottom, so depth does NOT hide a fish — only the water itself does: a
 * swamp, rain, dusk. What the shoal never gives you is a label. You get a shape and a size, and working
 * out what it is stays yours to do.
 */
public final class ShoalRenderer {
    /** 3×3 chunks corner to corner, plus slack. The server is not describing anything past this. */
    /**
     * §fish-lens: how far the mid-body stands proud of the nose-and-tail line, as a fraction of the
     * fish's length. A fish is about a twelfth as thick as it is long; this is a hair more, because it
     * is a single surface standing in for two flanks and so only gets to be half a body deep.
     */
    private static final float THICK = 0.08f;
    private static final double MAX_DIST = 42.0;
    /** Fish do not pop in at the edge of range — the last few blocks are a fade. */
    private static final double FADE_BAND = 10.0;

    /** species id → its icon texture, so the per-frame atlas lookup costs one map get and no garbage. */
    private static final Map<Identifier, Identifier> TEX = new HashMap<>();
    /** Species already complained about, so a missing sprite is one line in the log and not a flood. */
    private static final java.util.Set<Identifier> MISSING = new java.util.HashSet<>();

    private ShoalRenderer() {}

    //? if <26.2 {
    // 26.1: immediate mode — pull the shared buffer source and flush the batch ourselves.
    public static void render(PoseStack pose, Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        TextureAtlas atlas = fishAtlas(mc);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType layer = LAYER;
        if (drawAll(mc, atlas, pose, cam, partialTick, buffers.getBuffer(layer))) {
            // The buffer batches; without this the shoal would not appear until something else flushed it.
            buffers.endBatch(layer);
        }
    }
    //?} else {
    /*// 26.2: MultiBufferSource is gone — the quads ride the frame's SubmitNodeCollector, like every
    // other piece of custom geometry in this build (see LineRenderer.submit).
    public static void submit(PoseStack pose, Vec3 cam, float partialTick,
                              net.minecraft.client.renderer.SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        TextureAtlas atlas = fishAtlas(mc);
        collector.submitCustomGeometry(pose, LAYER,
                (posePose, vc) -> drawAll(mc, atlas, pose, cam, partialTick, vc));
    }
    *///?}

    /**
     * §26.x: the atlas the fish sprites are stitched into — the ITEM atlas.
     *
     * <p>Two 26.x traps in one line. First, 26.x gave items an atlas of their own: a fish icon is a
     * {@code textures/item/...} file, so it is stitched into {@code minecraft:items} and simply is not
     * on the block atlas at all — asking there returns the missing-texture checkerboard and every
     * species is skipped. Second, the AtlasManager is keyed by the atlas's ID ({@code minecraft:items},
     * the name of {@code atlases/items.json}) while the render layer is keyed by the atlas's TEXTURE
     * ({@code minecraft:textures/atlas/items.png}, which is what {@link TextureAtlas#LOCATION_ITEMS}
     * is). Both are an Identifier, so handing one to the other compiles and throws "Invalid atlas id"
     * on the first frame of a world. One place asks for the atlas, so the 26.1 and 26.2 entry points
     * cannot drift apart on it again.
     */
    private static TextureAtlas fishAtlas(Minecraft mc) {
        return mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.ITEMS);
    }

    /**
     * §26.x: the atlas-backed layer the fish quads live on, named once. 26.x dropped the culled
     * translucent factory, and the unculled one is the same layer minus the back-face reject — which
     * this never needed anyway, since a billboard is turned to the camera and mirrored, never flipped.
     */
    private static final RenderType LAYER =
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(
                    TextureAtlas.LOCATION_ITEMS);

    /** The shared per-frame loop. Returns true when anything was actually drawn. */
    private static boolean drawAll(Minecraft mc, TextureAtlas atlas, PoseStack pose, Vec3 cam,
                                   float partialTick, VertexConsumer vc) {
        if (mc.player == null) return false;
        // Shoals belong to the level they were sent for; a world change must not leave stale fish behind.
        if (ShoalState.owner() != mc.level) return false;

        ShoalState.tick(partialTick);
        List<ShoalState.Live> spots = ShoalState.live();
        if (spots.isEmpty()) return false;
        float time = mc.level.getGameTime() + partialTick;
        boolean drew = false;

        for (ShoalState.Live live : spots) {
            ShoalPacket.Spot spot = live.spot;
            List<ShoalPacket.Entry> fish = spot.fish();
            if (fish.isEmpty() || live.fade <= 0.01f) continue;
            // §shoal-spook: 0 calm, 1 gone. Every patch fades on its OWN clock now, so one shoal
            // bolting or one dropping off the edge of the send radius leaves the rest alone.
            float fade = live.fade;
            float flight = Mth.clamp(live.flight, 0f, 1f);

            BlockPos c = spot.centre();
            Vec3 centre = new Vec3(c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5);
            double dist = Math.sqrt(centre.distanceToSqr(cam));
            if (dist > MAX_DIST) continue;

            // Muddy water, rain and dusk are what dim a fish — not how deep it is sitting.
            float distFade = (float) Mth.clamp((MAX_DIST - dist) / FADE_BAND, 0.0, 1.0);
            // A frightened shoal is not just absent — it is leaving, and dimming as it goes.
            int alpha = (int) (fade * spot.clarity() * distFade * (1f - 0.85f * flight) * 255f);
            if (alpha <= 6) continue;

            // §shoal-sim: the fish carry their own positions between frames — nothing is placed here,
            // only drawn where it already swam to.
            ShoalSim.Fish[] swimming = live.fish;
            for (int i = 0; i < swimming.length; i++) {
                ShoalSim.Fish f = swimming[i];
                ShoalPacket.Entry e = f.entry;
                // Small fish thin out with distance, the way they actually do: a 20 cm roach 30 blocks off
                // is two pixels of noise, while a metre of pike is worth seeing from the far bank. The
                // server drops these too — this is here because a fish list outlives the walk that made it.
                if (dist > 40 && e.lengthCm() < 90) continue;
                if (dist > 26 && e.lengthCm() < 35) continue;
                TextureAtlasSprite sprite = spriteFor(atlas, e.species());
                if (sprite == null) continue;

                double x = f.x, y = f.y, z = f.z;


                // §shoal-heading: the fish is turned the way it is SWIMMING, not the way you happen to
                // be standing. It was a billboard before — always broadside, the sprite mirrored left or
                // right by which way it travelled — and the price of that is the thing you cannot unsee:
                // a fish going away from you moved SIDEWAYS, because its picture had nowhere else to face.
                //
                // The sprite is drawn head-to-the-LEFT, i.e. along local −X, and a Y rotation by θ sends
                // local −X to (−cos θ, 0, sin θ). Set that equal to the heading (cos h, 0, sin h) and
                // θ = π − h, which is the whole derivation and the reason for the 180 below.
                // §fish-beat: a fish does not hold a dead-straight line — its tail beats and its nose
                // swings a few degrees either side of its course. Eight degrees, under a hertz, phased
                // per fish. Broadside this is barely perceptible; head-on it is the whole difference
                // between a fish and a vertical line, because the flank it shows you goes from nothing
                // to a seventh of its own length and back on every beat. One sine, and it does more for
                // a fish swimming at you than any amount of geometry would.
                float yaw = 180f - (float) Math.toDegrees(f.heading)
                        + Mth.sin(time * 0.22f + f.phase) * 7f;

                pose.pushPose();
                pose.translate(x - cam.x, y - cam.y, z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                // §fish-pose: a flounder, a halibut and a ray are drawn from ABOVE — their sprite is the
                // broad face of a fish that lies horizontal, and these three travel along the bottom.
                // Parallel to it, then: from the bank one is barely anything, which is exactly what a
                // flatfish looks like from there.
                if (com.riverfishing.fish.FishPose.isFlat(e.species().getPath())) {
                    pose.mulPose(Axis.XP.rotationDegrees(com.riverfishing.fish.FishPose.lay()));
                }
                // Local Z is the fish's own left-right axis now, so this is a nose-up, nose-down pitch
                // rather than the screen-plane roll it used to be. It reads better, and it is free.
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05f + f.phase) * 3f));
                float size = spriteSize(e.lengthCm());
                // §morph: the fish in the water are painted by the same table as the one in your hand.
                double age = e.age() / 100.0;
                String path = e.species().getPath();
                // Which flank you are on. The stack began as identity AT the camera, so column 3 of
                // the matrix is the camera-to-fish vector and column 2 is where the fish's own +Z
                // landed — one dot product decides it, and because it reads the finished matrix it
                // follows the flatfish lay and the pitch for free. Verified against JOML rather than
                // reasoned about: a fish ten blocks down +Z at yaw 0 has its +Z pointing away, and the
                // same fish at yaw 180 has it pointing back at the camera.
                Matrix4f m = pose.last().pose();
                boolean plusNear = m.m20() * m.m30() + m.m21() * m.m31() + m.m22() * m.m32() < 0f;
                lens(m, vc, sprite, size, plusNear ? size * THICK : -size * THICK, alpha,
                        com.riverfishing.fish.FishMorph.tint(path, age, ""),
                        FishTint.whiten(com.riverfishing.fish.FishMorph.pale(path, age, "")));
                pose.popPose();
                drew = true;
            }
        }
        return drew;
    }

    /** The icon sprite for a species, or null when it is not on the atlas (a dev build, a broken pack). */
    private static TextureAtlasSprite spriteFor(TextureAtlas atlas, Identifier species) {
        Identifier tex = TEX.computeIfAbsent(species, id -> RiverFishing.id("item/fish/" + id.getPath()));
        TextureAtlasSprite sprite = atlas.getSprite(tex);
        // getSprite hands back the missing-texture checkerboard rather than null; an empty patch of water
        // is better than a magenta square. Say so once, because the alternative failure mode of this whole
        // feature — silently drawing nothing at all — is otherwise indistinguishable from empty water.
        if (sprite == null || !tex.equals(sprite.contents().name())) {
            if (MISSING.add(species)) {
                RiverFishing.LOGGER.warn("§shoal: {} is not on the item atlas ({}), so it will not be drawn",
                        species, tex);
            }
            return null;
        }
        return sprite;
    }

    /**
     * §fish-lens: the fish as one CREASED surface rather than one flat one — pinched to the centreline
     * at nose and tail, standing {@code t} proud of it at mid-body, always bulging toward the viewer.
     *
     * <p>It is thickness that cannot ghost, and that is the entire design. A dropped fish is solid
     * because {@code item/generated} extrudes its sprite into a mesh — front face, back face, and a
     * side quad traced round the silhouette — and we cannot have that one: measured against our own
     * sprites it is 241x this quad count on 1.20.1 and 1.21.1 and 909x on 26.x, where the baker emits
     * a side quad per edge TEXEL, to draw a rim one 256th of a sprite wide. Nor can we have the cheap
     * six-quad version of it, because a dropped item is OPAQUE and these fish are not: two translucent
     * surfaces at alpha read as 1-(1-alpha)^2, so a front and a back would silently double the density
     * of every fish and wreck the fade, and where the two failed to overlap you would get §shoal-ghost
     * back. Nor can that be dodged by choosing the draw order — the batch is sorted far-to-near on
     * upload on all four versions, so the order is not ours to choose.
     *
     * <p>One surface has nothing to blend against, so all of that goes away. Broadside it is the same
     * silhouette it always was; the bulge is along the line of sight, which is exactly where it is
     * free. End-on the fish stops being a rasterised line and becomes a band as wide as it is thick.
     *
     * <p>The crease sits at mid-body, and so does the split in the uv, so each half maps linearly and
     * nothing is stretched. On a flatfish, which §fish-pose has already laid horizontal, local Z is
     * vertical and the same code gives it a back that stands proud of its edges — which is what a
     * flatfish is.
     */
    private static void lens(Matrix4f m, VertexConsumer vc, TextureAtlasSprite sp, float size, float t,
                             int alpha, int tint, int overlay) {
        float r = size / 2f;
        float u0 = sp.getU0(), u1 = sp.getU1(), um = (u0 + u1) * 0.5f;
        float v0 = sp.getV0(), v1 = sp.getV1();
        // Head half: from the nose on the centreline back to the crease.
        vertex(m, vc, -r, -r, 0f, u0, v1, alpha, tint, overlay);
        vertex(m, vc, 0f, -r, t, um, v1, alpha, tint, overlay);
        vertex(m, vc, 0f, r, t, um, v0, alpha, tint, overlay);
        vertex(m, vc, -r, r, 0f, u0, v0, alpha, tint, overlay);
        // Tail half: from the crease back to the tail, returning to the centreline.
        vertex(m, vc, 0f, -r, t, um, v1, alpha, tint, overlay);
        vertex(m, vc, r, -r, 0f, u1, v1, alpha, tint, overlay);
        vertex(m, vc, r, r, 0f, u1, v0, alpha, tint, overlay);
        vertex(m, vc, 0f, r, t, um, v0, alpha, tint, overlay);
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float z, float u, float v,
                              int alpha, int tint, int overlay) {
        // Full-bright deliberately: the fade is alpha, so a fish 15 blocks down is faint but not black.
        // The normal is world UP rather than the quad's own facing — the entity shader mixes directional
        // light by the normal, and a face whose normal turns with the fish would brighten and darken as
        // it swam round. Up is constant and is the brighter of the two vanilla light directions.
        vc.addVertex(m, x, y, z)
                .setColor((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0f, 1f, 0f);
    }

    /**
     * True length: one block is one metre, with a small readability bias. A 20 cm roach is a flicker you
     * have to be close to see and a 3 m sturgeon is a shadow you notice from the bank — which is the point
     * of drawing them at all.
     */
    private static float spriteSize(int lengthCm) {
        return Mth.clamp(lengthCm / 100f * 1.2f, 0.16f, 4.5f);
    }
}
