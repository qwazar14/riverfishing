package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.RiverFishing;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.StackNbt;
import com.riverfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * §hooked-fish (0.9.1): the fish on the end of the line, drawn from the moment it is hooked.
 *
 * <p>The fight used to be read off the rod and the bar alone; the fish itself was a course, a run
 * timer and a number. Now the number has a body: the same item drawing the shoal and the tank use
 * (the extruded sprite, true length, tail beating on its own phase), posed where
 * {@link ClientLineState.Line#tickFish} carried it this frame — out along its course on a run, deep
 * on a sounding, in an arc over the water on a breach, shuddering on a head-shake. The line ends on
 * its mouth, because {@link LineRenderer#lineEnd} reads the same offset.
 *
 * <p>Client-side entirely: the server sends what the fish is and what it is doing, in the packet it
 * already sent for the bar. Nothing here is simulated twice.
 */
public final class HookedFishRenderer {
    private HookedFishRenderer() {}

    public static void draw(Minecraft mc, PoseStack pose, MultiBufferSource buffers,
                            ClientLineState.Line state, Vec3 at, float pt) {
        if (!state.fighting || state.species.isEmpty() || mc.level == null) return;
        ItemStack stack = stackFor(state);
        if (stack == null) return;
        float time = mc.level.getGameTime() + pt;

        // the splash: once when the body leaves the water, once when it comes back
        double surfaceY = state.target.getY() + 0.95;
        boolean inAir = at.y > surfaceY + 0.15;
        if (inAir != state.wasInAir) {
            for (int i = 0; i < 10 + state.lengthCm / 10; i++) {
                mc.level.addParticle(ParticleTypes.SPLASH, at.x + (mc.level.random.nextDouble() - 0.5) * 0.8,
                        surfaceY, at.z + (mc.level.random.nextDouble() - 0.5) * 0.8, 0, 0.15, 0);
            }
            state.wasInAir = inAir;
        }

        pose.pushPose();
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
        FishItemRenderer.gridScale = Mth.clamp(state.lengthCm / 100f, 0.12f, 4.5f);   // true length, one block a metre
        pose.translate(FishItemRenderer.gridScale * 0.5, 0, 0);   // §hooked-mouth: the head is on -X; the line ends at the mouth
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, depthLight(surfaceY - at.y),
                OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        FishItemRenderer.gridScale = 0f;
        pose.popPose();
    }

    /**
     * §hooked-dim: a fish under water is lit by the water above it. Full bright at the surface, down to
     * a quarter six blocks under — a sounding fish goes dark, a breaching one comes up into the light.
     */
    static int depthLight(double depth) {
        int l = Math.round(15f * Mth.clamp(1f - (float) Math.max(0.0, depth) / 6f, 0.25f, 1f));
        return LightTexture.pack(l, l);
    }

    /** The item the fish is drawn as — rebuilt only when the species on the line changes. */
    private static ItemStack stackFor(ClientLineState.Line state) {
        if (state.stack != null && state.species.equals(state.stackSpecies)) return state.stack;
        ResourceLocation id = RiverFishing.id(state.species);
        var item = ModItems.fishItem(id);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item);
        int w = state.weightG, l = state.lengthCm;
        StackNbt.mutate(stack, tag -> {
            tag.putString(FishItem.TAG_SPECIES, id.toString());
            tag.putInt(FishItem.TAG_WEIGHT, w);
            tag.putInt(FishItem.TAG_LENGTH, l);
        });
        state.stack = stack;
        state.stackSpecies = state.species;
        return stack;
    }
}
