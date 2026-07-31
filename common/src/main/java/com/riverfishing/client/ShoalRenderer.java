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

        ShoalState.tick(partialTick);
        List<ShoalState.Live> spots = ShoalState.live();
        if (spots.isEmpty()) return;

        TextureAtlas atlas = (TextureAtlas) mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        if (atlas == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType layer = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vc = buffers.getBuffer(layer);
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
                float yaw = 180f - (float) Math.toDegrees(f.heading);

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
                quad(pose.last().pose(), vc, sprite, size, alpha,
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
     * One quad, centred on the origin, and one is enough — {@code entityTranslucent} is built with
     * culling OFF on every version we ship (1.20.1, 1.21.1, 26.1.2, 26.2 all set NO_CULL / withCull
     * (false) on that pipeline), so this single face is already drawn from both sides.
     *
     * <p>That is worth writing down, because getting it wrong is what caused §shoal-ghost. Turning the
     * fish by its heading (§shoal-heading) looked like it needed a second, opposite-facing flank to
     * survive being turned away — but nothing here is ever culled, so the second flank was never
     * hidden. It was a parallel copy an eighth of a body-length to one side, and since the emission
     * order is fixed while WHICH of the two is nearer depends on where you stand, the far one is
     * painted first as often as not and stays visible wherever the near one does not cover it. That
     * offset leftover is the second fish players saw, and side-on to a fish swimming at you the two
     * slivers land beside each other and read as a pair.
     *
     * <p>So: one face, and the mirror image you see from the far side is free, because you are the one
     * who moved. A fish seen exactly end-on is a sliver, which is what a real fish looks like from in
     * front, and there is no honest way to give a sprite thickness on an unculled layer — any second
     * surface ghosts exactly like this one did.
     */
    private static void quad(Matrix4f m, VertexConsumer vc, TextureAtlasSprite sp, float size, int alpha,
                             int tint, int overlay) {
        float r = size / 2f;
        float u0 = sp.getU0(), u1 = sp.getU1();
        float v0 = sp.getV0(), v1 = sp.getV1();
        // Counter-clockwise seen from +Z; the other side draws because the layer does not cull.
        vertex(m, vc, -r, -r, 0f, u0, v1, alpha, tint, overlay);
        vertex(m, vc, r, -r, 0f, u1, v1, alpha, tint, overlay);
        vertex(m, vc, r, r, 0f, u1, v0, alpha, tint, overlay);
        vertex(m, vc, -r, r, 0f, u0, v0, alpha, tint, overlay);
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float z, float u, float v,
                              int alpha, int tint, int overlay) {
        // Full-bright deliberately: the fade is alpha, so a fish 15 blocks down is faint but not black.
        // The normal is world UP rather than the quad's own facing — the entity shader mixes directional
        // light by the normal, and a face whose normal turns with the fish would brighten and darken as
        // it swam round. Up is constant and is the brighter of the two vanilla light directions.
        // §1.20.1: the old builder chain — same six attributes, different names, and it must be closed
        // with endVertex() or the buffer is left mid-vertex and the whole batch draws as garbage.
        vc.vertex(m, x, y, z)
                .color((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, alpha)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0f, 1f, 0f)
                .endVertex();
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
