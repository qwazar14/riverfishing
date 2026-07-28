package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.riverfishing.RiverFishing;
import com.riverfishing.network.ShoalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
    private static final double MAX_DIST = 42.0;
    /** Fish do not pop in at the edge of range — the last few blocks are a fade. */
    private static final double FADE_BAND = 10.0;

    /** species id → its icon texture, so the per-frame atlas lookup costs one map get and no garbage. */
    private static final Map<ResourceLocation, ResourceLocation> TEX = new HashMap<>();
    /** Species already complained about, so a missing sprite is one line in the log and not a flood. */
    private static final java.util.Set<ResourceLocation> MISSING = new java.util.HashSet<>();

    private ShoalRenderer() {}

    public static void render(PoseStack pose, Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // Shoals belong to the level they were sent for; a world change must not leave stale fish behind.
        if (ShoalState.owner() != mc.level) return;

        float fade = ShoalState.tickFade(partialTick);
        if (fade <= 0.01f) return;
        List<ShoalPacket.Spot> spots = ShoalState.spots();
        if (spots.isEmpty()) return;

        TextureAtlas atlas = (TextureAtlas) mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        if (atlas == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType layer = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vc = buffers.getBuffer(layer);
        float time = mc.level.getGameTime() + partialTick;
        boolean drew = false;

        for (ShoalPacket.Spot spot : spots) {
            List<ShoalPacket.Entry> fish = spot.fish();
            if (fish.isEmpty()) continue;

            BlockPos c = spot.centre();
            Vec3 centre = new Vec3(c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5);
            double dist = Math.sqrt(centre.distanceToSqr(cam));
            if (dist > MAX_DIST) continue;

            // Muddy water, rain and dusk are what dim a fish — not how deep it is sitting.
            float distFade = (float) Mth.clamp((MAX_DIST - dist) / FADE_BAND, 0.0, 1.0);
            int alpha = (int) (fade * spot.clarity() * distFade * 255f);
            if (alpha <= 6) continue;

            // Lanes are laid out across this shoal's own cell, so two neighbouring shoals do not swim
            // through each other and the outer laps do not cross the bank (§shoal-spread).
            int lanes = 1;
            for (ShoalPacket.Entry e : fish) lanes = Math.max(lanes, e.lane() + 1);
            double rOuter = Mth.clamp(spot.spread() * 0.9, 1.1, 5.0);

            for (int i = 0; i < fish.size(); i++) {
                ShoalPacket.Entry e = fish.get(i);
                // Small fish thin out with distance, the way they actually do: a 20 cm roach 30 blocks off
                // is two pixels of noise, while a metre of pike is worth seeing from the far bank. The
                // server drops these too — this is here because a fish list outlives the walk that made it.
                if (dist > 40 && e.lengthCm() < 90) continue;
                if (dist > 26 && e.lengthCm() < 35) continue;
                TextureAtlasSprite sprite = spriteFor(atlas, e.species());
                if (sprite == null) continue;

                // Each lane is one slow circuit around the spot; a shoal shares its lane, and the packet
                // phase places each fish on it, so a group travels together instead of strung out.
                float phase = e.phase() / 64f * Mth.TWO_PI;
                float t = time * 0.012f + phase;
                double radius = lanes == 1 ? rOuter * 0.55
                        : 0.9 + (rOuter - 0.9) * e.lane() / (double) (lanes - 1);
                double x = centre.x + Mth.cos(t) * radius;
                double z = centre.z + Mth.sin(t) * radius * 0.75;
                // Depth from the server, plus a gentle rise and fall — fish do not hold a perfect line.
                double y = centre.y - 0.35 - e.depth() + Mth.sin(time * 0.03f + i) * 0.18;

                // The circuit is drawn around one surface block, which near a bank means part of a lap can
                // leave the water. The client has the blocks, so it just checks: a fish is only drawn where
                // there IS water. Without this you get fish over the grass and fish buried in the bottom.
                if (mc.level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) continue;

                // §shoal-side: a flat sprite must stay BROADSIDE to whoever is looking, or it turns
                // edge-on and vanishes — the aquarium's rule (§aquarium-side), generalised from a fixed
                // viewing face to a camera that moves. Rotating by atan2(dx, dz) puts the quad's +Z, and
                // so its face, on the camera; its +X then lands on the viewer's right-hand side.
                double toCamX = cam.x - x, toCamZ = cam.z - z;
                float yaw = (float) Math.toDegrees(Math.atan2(toCamX, toCamZ));
                // Every fish sprite is drawn head-to-the-LEFT, so the head leads when the fish travels
                // left across the view; when it travels right, the sprite is mirrored instead of turned —
                // a 180° turn would put the quad's back to the camera and cull it away.
                // The side is decided against the camera's BLOCK, not its exact position: at the moment a
                // fish swims straight at you the sign sits on zero, and head-bob alone would mirror it
                // every frame. Rounding to a block makes the turn happen once, cleanly.
                double vx = -Mth.sin(t), vz = Mth.cos(t) * 0.75;
                double rightX = Math.floor(cam.z) + 0.5 - z, rightZ = -(Math.floor(cam.x) + 0.5 - x);
                boolean mirror = vx * rightX + vz * rightZ > 0;

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
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05f + i) * 3f));   // a slight roll
                float size = spriteSize(e.lengthCm());
                // §morph: the fish in the water are painted by the same table as the one in your hand.
                double age = e.age() / 100.0;
                String path = e.species().getPath();
                quad(pose.last().pose(), vc, sprite, size, alpha, mirror,
                        com.riverfishing.fish.FishMorph.tint(path, age, ""),
                        FishTint.whiten(com.riverfishing.fish.FishMorph.pale(path, age, "")));
                pose.popPose();
                drew = true;
            }
        }
        // The buffer batches; without this the shoal would not appear until something else flushed it.
        if (drew) buffers.endBatch(layer);
    }

    /** The icon sprite for a species, or null when it is not on the atlas (a dev build, a broken pack). */
    private static TextureAtlasSprite spriteFor(TextureAtlas atlas, ResourceLocation species) {
        ResourceLocation tex = TEX.computeIfAbsent(species, id -> RiverFishing.id("item/fish/" + id.getPath()));
        TextureAtlasSprite sprite = atlas.getSprite(tex);
        // getSprite hands back the missing-texture checkerboard rather than null; an empty patch of water
        // is better than a magenta square. Say so once, because the alternative failure mode of this whole
        // feature — silently drawing nothing at all — is otherwise indistinguishable from empty water.
        if (sprite == null || !tex.equals(sprite.contents().name())) {
            if (MISSING.add(species)) {
                RiverFishing.LOGGER.warn("§shoal: {} is not on the block atlas ({}), so it will not be drawn",
                        species, tex);
            }
            return null;
        }
        return sprite;
    }

    /**
     * One quad, centred on the origin, facing +Z. The sprite canvas is square and the fish is drawn along
     * it, so the quad is square too: the transparent margin costs nothing on a single quad.
     */
    private static void quad(Matrix4f m, VertexConsumer vc, TextureAtlasSprite sp, float size, int alpha,
                            boolean mirror, int tint, int overlay) {
        float r = size / 2f;
        float u0 = mirror ? sp.getU1() : sp.getU0();
        float u1 = mirror ? sp.getU0() : sp.getU1();
        float v0 = sp.getV0(), v1 = sp.getV1();
        // Counter-clockwise seen from +Z, so the front face is the one pointing at the camera.
        vertex(m, vc, -r, -r, u0, v1, alpha, tint, overlay);
        vertex(m, vc, r, -r, u1, v1, alpha, tint, overlay);
        vertex(m, vc, r, r, u1, v0, alpha, tint, overlay);
        vertex(m, vc, -r, r, u0, v0, alpha, tint, overlay);
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float u, float v,
                              int alpha, int tint, int overlay) {
        // Full-bright deliberately: the fade is alpha, so a fish 15 blocks down is faint but not black.
        // The normal is world UP rather than the quad's own facing — the entity shader mixes directional
        // light by the normal, and a billboard whose normal follows the camera would brighten and darken
        // as you walk around it. Up is constant and is the brightest of the two vanilla light directions.
        vc.addVertex(m, x, y, 0f)
                .setColor((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(LightTexture.FULL_BRIGHT)
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
