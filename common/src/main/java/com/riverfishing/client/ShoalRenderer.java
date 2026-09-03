package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.riverfishing.RiverFishing;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.StackNbt;
import com.riverfishing.network.ShoalPacket;
import com.riverfishing.registry.ModItems;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
 * <p>§fish-item (0.9.0): by default each fish is now the dropped ITEM — the baked {@code item/generated}
 * model through the item renderer, sized to its length — see {@link #FISH_ITEM}. What follows describes
 * the flat path that {@code /rfrod fishitem off} brings back.
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
        // §shoal-layer: entityTranslucent, NOT entityTranslucentCull, and the difference is three
        // separate things — I only found them after turning the fish by its heading made the first
        // one visible.
        //  1. cull discards back faces. A billboard never showed one, so it cost nothing for a year;
        //     the moment the fish faced its own heading it hid every fish whose far flank was toward
        //     the camera, which is half the shoal at any instant.
        //  2. rendertype_entity_translucent_cull.fsh multiplies the vertex colour in BEFORE its
        //     `a < 0.1` discard, so our fade alpha is inside the cutoff and a fish pops out of
        //     existence at 10% instead of reaching zero. The uncalled one discards on texture alpha
        //     alone and lets the fade finish.
        //  3. its shader JSON declares no Sampler1 at all, so setOverlay() has been writing into
        //     nothing — §morph's whitening has never once been drawn on these two versions.
        RenderType layer = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
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
                // §shoal-far: a loner too small to see at this range is skipped; a fish in a school is
                // drawn, because the school is what you see.
                if (!e.shoaling() && dist > 40 && e.lengthCm() < 90) continue;
                if (!e.shoaling() && dist > 26 && e.lengthCm() < 35) continue;
                TextureAtlasSprite sprite = FISH_ITEM ? null : spriteFor(atlas, e.species());
                if (!FISH_ITEM && sprite == null) continue;

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
                        + Mth.sin(time * 0.22f + f.phase) * (3f + 4f * f.kick);   // §shoal-kick: the wave does the rest

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
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05f + f.phase) * 3f + f.pitch));   // §shoal-jump
                if (FISH_ITEM) {
                    // §fish-item: the fish is the ITEM — the same baked item/generated model as the one
                    // you drop on the bank, a sixteenth thick with coloured edges — posed where the flat
                    // quad was. The item model cannot bend, so the swimming wave becomes a beat: the
                    // whole body swings ±6° at the wave's own rhythm, on top of the §fish-beat above.
                    // The FIXED display turns the model 180° about Y (head to +X); one more 180° here
                    // puts the head back on −X so the yaw derivation, the lay and the pitch sign all
                    // hold unchanged.
                    pose.mulPose(Axis.YP.rotationDegrees(180f
                            + Mth.sin(time * (0.35f + 0.45f * f.kick) + f.phase * 5f) * 6f));
                    if (f.stack == null) f.stack = stackFor(e);
                    // Size rides the length, which the server derived from the weight it rolled — so
                    // the picture is the weight, and the fish keeps it between packets.
                    FishItemRenderer.gridScale = itemSize(e.lengthCm());
                    ALPHA.alpha = alpha;
                    ALPHA.vc = vc;
                    mc.getItemRenderer().renderStatic(f.stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, pose, ALPHA_SOURCE, mc.level, 0);
                    FishItemRenderer.gridScale = 0f;
                    pose.popPose();
                    drew = true;
                    continue;
                }
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
                int tintNow = com.riverfishing.fish.FishMorph.tint(path, age, "");
                int overlayNow = FishTint.whiten(com.riverfishing.fish.FishMorph.pale(path, age, ""));
                // §fish-3d first; a species whose sprite could not be read is drawn flat, as before.
                // §fish-3d-fins: the slab is the body; the flat sprite still draws — down the centreline,
                // without its bulge — and that is where the fins, the fork and every thin thing come
                // from. Inside the body the flanks cover it; outside, it is the fin.
                boolean slab = FISH_3D && FishMesh.emit(m, vc, sprite, e.species(), size, f, time, alpha, tintNow, overlayNow);
                body(m, vc, sprite, size, slab ? 0f : (plusNear ? 1f : -1f), f, time, alpha, tintNow, overlayNow);
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
     * §fish-wave: the fish as a ribbon of strips with a swimming wave running down it.
     *
     * <p>The lens was one crease at mid-body — thickness that could not ghost, and it still cannot,
     * but a crease is a crease: from the bank every fish looked folded. So the same single surface is
     * cut into {@link #STRIPS} along the body, and each strip is displaced sideways by a wave that
     * starts at nothing behind the head and grows toward the tail, travelling nose-to-tail at a rate
     * set by the tail's own beat (§shoal-kick). That is what a swimming fish's outline does, and it is
     * eight quads instead of two.
     *
     * <p>The thickness rides the same strips: a body profile that peaks a third of the way back and
     * tapers to nothing at nose and tail, bulging toward the viewer as before. Every strip's uv is a
     * linear slice of the sprite, so nothing is stretched.
     */
    private static final int STRIPS = 8;

    /**
     * §fish-3d: bodies pulled out of the sprites (FishMesh) instead of the flat wave. OFF by default
     * after testing — the flat wave is the shipped look; {@code /rfrod fish3d on} tries the bodies,
     * persisted, no rebuild.
     */
    public static boolean FISH_3D = false;

    /**
     * §fish-item: the fish in the water drawn as the dropped ITEM would be — the baked
     * {@code item/generated} model, a sixteenth thick, edges and all. ON by default; {@code /rfrod
     * fishitem off} is the flat wave (and FISH_3D under it) for a live comparison.
     *
     * <p>The header's cost argument still stands: a 256px sprite bakes to on the order of a thousand
     * quads, and the fish list can hold seventy. ponytail: it is drawn through the item renderer and
     * measured by eye, not profiled; if a full shoal shows in the frame time, bake one distance-capped
     * model per species (a 64px downsample of the sprite) and draw that past ten blocks.
     */
    public static boolean FISH_ITEM = true;

    /**
     * The item renderer writes every vertex at alpha 1 and with the quad's own normal. The shoal's
     * whole visibility model is alpha — clarity, distance, spook, the arrival fade — and its lighting
     * is a constant up-normal so a fish does not brighten and darken as it turns (see {@link #vertex}).
     * This sits between the item renderer and the shoal's translucent buffer and imposes both.
     */
    private static final class AlphaConsumer implements VertexConsumer {
        VertexConsumer vc;
        int alpha;

        @Override public VertexConsumer addVertex(float x, float y, float z) { vc.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { vc.setColor(r, g, b, alpha); return this; }
        @Override public VertexConsumer setUv(float u, float v) { vc.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { vc.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { vc.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { vc.setNormal(0f, 1f, 0f); return this; }
    }

    private static final AlphaConsumer ALPHA = new AlphaConsumer();
    /** Whatever layer the item renderer asks for, it gets the shoal's own (see §shoal-layer). */
    private static final MultiBufferSource ALPHA_SOURCE = type -> ALPHA;

    /**
     * §fish-item: the stack the item renderer paints. Species, weight, length and age are what the
     * packet knows, and they are all FishItemRenderer and FishTint read — so a young roach in the
     * water is as pale as the one in your hand, by the same code. No morph: the packet does not carry one.
     */
    private static ItemStack stackFor(ShoalPacket.Entry e) {
        ItemStack stack = new ItemStack(ModItems.fishItem(e.species()));
        StackNbt.mutate(stack, tag -> {
            tag.putString(FishItem.TAG_SPECIES, e.species().toString());
            tag.putInt(FishItem.TAG_WEIGHT, e.weightG());
            tag.putInt(FishItem.TAG_LENGTH, e.lengthCm());
            tag.putByte(FishItem.TAG_AGE, e.age());
        });
        return stack;
    }

    /**
     * §fish-item: true length, one block per metre — the icons are drawn full-length across the sprite,
     * so the model's width IS the fish. A 15 cm bleak is 0.15 of a block and a metre of pike a block;
     * the floor keeps a fry visible at all. Shared with the aquarium, which caps it to its tank.
     */
    public static float itemSize(int lengthCm) {
        return Mth.clamp(lengthCm / 100f, 0.12f, 4.5f);
    }

    private static void body(Matrix4f m, VertexConsumer vc, TextureAtlasSprite sp, float size, float side,
                             ShoalSim.Fish f, float time, int alpha, int tint, int overlay) {
        float r = size / 2f;
        float u0 = sp.getU0(), u1 = sp.getU1();
        float v0 = sp.getV0(), v1 = sp.getV1();
        // Amplitude at the tail, and the wave's phase speed — faster on a beat, idle on a glide.
        float amp = size * (0.05f + 0.09f * f.kick);
        float phase = time * (0.35f + 0.45f * f.kick) + f.phase * 5f;
        float[] zs = new float[STRIPS + 1];
        float[] xs = new float[STRIPS + 1];
        for (int i = 0; i <= STRIPS; i++) {
            float t = i / (float) STRIPS;               // 0 nose .. 1 tail
            xs[i] = -r + t * size;
            float body = (float) Math.sin(Math.PI * Math.pow(t, 0.75));   // peaks at about a third back
            float thick = side * size * THICK * body;
            float wave = amp * t * t * Mth.sin(phase - t * 6.5f);       // grows toward the tail
            zs[i] = thick + wave;
        }
        for (int i = 0; i < STRIPS; i++) {
            float ua = u0 + (u1 - u0) * (i / (float) STRIPS), ub = u0 + (u1 - u0) * ((i + 1) / (float) STRIPS);
            vertex(m, vc, xs[i], -r, zs[i], ua, v1, alpha, tint, overlay);
            vertex(m, vc, xs[i + 1], -r, zs[i + 1], ub, v1, alpha, tint, overlay);
            vertex(m, vc, xs[i + 1], r, zs[i + 1], ub, v0, alpha, tint, overlay);
            vertex(m, vc, xs[i], r, zs[i], ua, v0, alpha, tint, overlay);
        }
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
