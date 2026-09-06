package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.RiverFishing;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.StackNbt;
import com.riverfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * §hooked-fish (0.9.1): the fish on the end of the line, drawn from the moment it is hooked.
 *
 * <p>The fight used to be read off the rod and the bar alone; the fish itself was a course, a run
 * timer and a number. Now the number has a body: the fish item (the extruded sprite, true length,
 * tail beating on its own phase), posed where {@link ClientLineState.Line#tickFish} carried it this
 * frame — out along its course on a run, deep on a sounding, in an arc over the water on a breach,
 * shuddering on a head-shake. The line ends on its mouth, because {@link LineRenderer#lineEnd} reads
 * the same offset.
 *
 * <p>26.1 draws the item state straight into the frame's buffers; 26.2 submits it to the collector.
 * The pose is built once for both.
 */
public final class HookedFishRenderer {
    private HookedFishRenderer() {}

    /** The pose and the splash — everything but the draw call, which the two versions spell differently. */
    private static ItemStackRenderState pose(Minecraft mc, PoseStack pose, ClientLineState.Line state, Vec3 at, float pt) {
        if (!state.fighting || state.species.isEmpty() || mc.level == null) return null;
        ItemStack stack = stackFor(state);
        if (stack == null) return null;
        float time = mc.level.getGameTime() + pt;

        // the splash: once when the body leaves the water, once when it comes back
        double surfaceY = state.target.getY() + 0.95;
        boolean inAir = at.y > surfaceY + 0.15;
        if (inAir != state.wasInAir) {
            for (int i = 0; i < 10 + state.lengthCm / 10; i++) {
                mc.level.addParticle(ParticleTypes.SPLASH, at.x + (mc.level.getRandom().nextDouble() - 0.5) * 0.8,
                        surfaceY, at.z + (mc.level.getRandom().nextDouble() - 0.5) * 0.8, 0, 0.15, 0);
            }
            state.wasInAir = inAir;
        }
        state.depth = surfaceY - at.y;

        pose.translate(at.x, at.y, at.z);
        // Sprite head is on local −X (ShoalRenderer's derivation): a Y turn of 180 − heading sends it
        // along the heading. The tail beat swings the whole body, the nose swings with it.
        float beat = Mth.sin(state.tail) * (state.running ? 7f : 4f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - (float) Math.toDegrees(state.heading) + beat));
        if (com.riverfishing.fish.FishPose.isFlat(state.species)) {
            pose.mulPose(Axis.XP.rotationDegrees(com.riverfishing.fish.FishPose.lay()));
        }
        pose.mulPose(Axis.ZP.rotationDegrees(state.pitch + Mth.sin(time * 0.05f) * 2f));
        // the item's FIXED display turns the model 180° about Y; one more here puts the head back on −X
        pose.mulPose(Axis.YP.rotationDegrees(180f + Mth.sin(state.tail * 1.0f) * 6f));
        float s = Mth.clamp(state.lengthCm / 100f, 0.12f, 4.5f);   // true length, one block a metre
        pose.scale(s, s, s);
        ItemStackRenderState rs = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(rs, stack, ItemDisplayContext.FIXED, mc.level, null, 0);
        return rs;
    }

    //? if <26.2 {
    // 26.1: the world pass is immediate mode and an ItemStackRenderState only knows how to SUBMIT, so
    // the body is the flat sprite here — a double-sided quad off the item atlas, the way the shoal's
    // 26.1 path draws its fish. The pose is the same; only the last step differs.
    public static void render(Minecraft mc, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                              ClientLineState.Line state, Vec3 at, float pt) {
        pose.pushPose();
        if (pose(mc, pose, state, at, pt) != null) {
            net.minecraft.client.renderer.texture.TextureAtlas atlas =
                    mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.ITEMS);
            Identifier tex = RiverFishing.id("item/fish/" + state.species);
            net.minecraft.client.renderer.texture.TextureAtlasSprite sp = atlas.getSprite(tex);
            if (sp != null && tex.equals(sp.contents().name())) {
                var layer = net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(
                        net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_ITEMS);
                com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(layer);
                org.joml.Matrix4f m = pose.last().pose();
                light = depthLight(state.depth);
                float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
                // the item model is a unit sprite centred on the origin, head on −X after the 180 above
                for (int side = 0; side < 2; side++) {
                    float z = side == 0 ? 0.02f : -0.02f;
                    if (side == 0) { quad(m, vc, -0.5f, 0.5f, z, u1, u0, v0, v1); }
                    else           { quad(m, vc, 0.5f, -0.5f, z, u0, u1, v0, v1); }
                }
            }
        }
        pose.popPose();
    }

    private static void quad(org.joml.Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer vc,
                             float xa, float xb, float z, float ua, float ub, float v0, float v1) {
        vtx(m, vc, xa, -0.5f, z, ua, v1); vtx(m, vc, xb, -0.5f, z, ub, v1);
        vtx(m, vc, xb, 0.5f, z, ub, v0);  vtx(m, vc, xa, 0.5f, z, ua, v0);
    }

    private static int light;   // §hooked-dim: set per draw from the fish's depth

    private static void vtx(org.joml.Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer vc,
                            float x, float y, float z, float u, float v) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);
    }
    //?} else {
    /*public static void submit(Minecraft mc, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                              ClientLineState.Line state, Vec3 at, float pt) {
        pose.pushPose();
        ItemStackRenderState rs = pose(mc, pose, state, at, pt);
        if (rs != null) rs.submit(pose, collector, depthLight(state.depth), OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
    *///?}

    /**
     * §hooked-dim: a fish under water is lit by the water above it. Full bright at the surface, down to
     * a quarter six blocks under — a sounding fish goes dark, a breaching one comes up into the light.
     * Packed the way LightTexture.pack does it: sky in the high half, block in the low.
     */
    static int depthLight(double depth) {
        int l = Math.round(15f * Mth.clamp(1f - (float) Math.max(0.0, depth) / 6f, 0.25f, 1f));
        return (l << 20) | (l << 4);
    }

    /** The item the fish is drawn as — rebuilt only when the species on the line changes. */
    private static ItemStack stackFor(ClientLineState.Line state) {
        if (state.stack != null && state.species.equals(state.stackSpecies)) return state.stack;
        Identifier id = RiverFishing.id(state.species);
        var item = ModItems.fishItem(id);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item);
        int w = state.weightG, l = state.lengthCm;
        StackNbt.mutate(stack, tag -> {
            tag.putString(FishItem.TAG_SPECIES, id.toString());
            tag.putInt(FishItem.TAG_WEIGHT, w);
            tag.putInt(FishItem.TAG_LENGTH, l);
        });
        FishItem.stampIcon(stack);   // 26.x: the icon is stack-driven — no stamp, no fish
        state.stack = stack;
        state.stackSpecies = state.species;
        return stack;
    }
}
