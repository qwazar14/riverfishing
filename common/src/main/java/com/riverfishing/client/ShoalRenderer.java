package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.network.ShoalPacket;
import com.riverfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * §shoal render: the fish the water actually holds, drifting under the surface.
 *
 * <p>Built on the aquarium's technique — the fish's own 256px item sprite, rendered broadside in world
 * space on a slow swim path. That is why this costs almost nothing: there is no model, no entity and no
 * animation rig, just the sprite the journal already draws, moved along a curve.
 *
 * <p>Visibility is the whole design. A shoal you can always see perfectly would flatten the mod's core
 * skill of reading water, so every fish fades with the server's clarity figure AND with its own depth:
 * a bottom-hugging fish in a swamp is a hint, not a readout. Nothing is labelled and nothing is
 * highlighted — you get a shape and a size, and identifying it is yours to do.
 */
public final class ShoalRenderer {
    /** Beyond this the sprites are a pixel wide; drawing them is just noise and cost. */
    private static final double MAX_DIST = 28.0;

    private ShoalRenderer() {}

    public static void render(PoseStack pose, Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        float fade = ShoalState.tickFade(partialTick);
        if (fade <= 0.01f) return;
        List<ShoalPacket.Entry> fish = ShoalState.fish();
        if (fish.isEmpty()) return;

        BlockPos c = ShoalState.centre();
        Vec3 centre = new Vec3(c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5);
        if (centre.distanceToSqr(cam) > MAX_DIST * MAX_DIST) return;

        float time = mc.level.getGameTime() + partialTick;

        for (int i = 0; i < fish.size(); i++) {
            ShoalPacket.Entry e = fish.get(i);
            ItemStack stack = stackFor(e);
            if (stack.isEmpty()) continue;

            // Each fish owns a slow circuit around the spot, offset by its lane and its packet phase so
            // the shoal never lines up. The radius grows with the lane so they do not share a lap.
            float phase = e.phase() / 64f * Mth.TWO_PI;
            float t = time * 0.012f + phase;
            double radius = 1.6 + e.lane() * 0.9;
            double x = centre.x + Mth.cos(t) * radius;
            double z = centre.z + Mth.sin(t) * radius * 0.75;
            // Depth from the server, plus a gentle rise and fall — fish do not hold a perfect line.
            double y = centre.y - 0.35 - e.depth() + Mth.sin(time * 0.03f + i) * 0.18;

            // Deeper fish are dimmer: the shoal has to stay a hint. Two blocks down is already vague.
            float depthFade = Mth.clamp(1.0f - e.depth() * 0.16f, 0.12f, 1.0f);
            float alpha = fade * depthFade;
            if (alpha <= 0.02f) continue;

            // The sprite has no alpha channel of its own here, so fading is done by shrinking the light
            // it is drawn with — a dim fish in dark water, which is what depth actually looks like.
            int light = packedLight(alpha);

            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y, z - cam.z);
            // Broadside to the direction of travel, head first — the aquarium's rule (§aquarium-side),
            // which is why a fish never shows edge-on and never swims backwards.
            float yaw = (float) Math.toDegrees(Math.atan2(Mth.cos(t), -Mth.sin(t)));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05f + i) * 3f));   // a slight roll
            float scale = spriteScale(e.weightG());
            pose.scale(scale, scale, scale);
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    pose, buffers, mc.level, 0);
            pose.popPose();
        }
        // The item renderer batches; without this the shoal would not appear until something else flushed.
        buffers.endBatch();
    }

    /** Fade by light level: 15 is a fully lit fish, 3 is a shape you are not sure about. */
    private static int packedLight(float alpha) {
        int block = (int) (alpha * 12f) + 3;
        return (Math.min(15, block) << 4) | (Math.min(15, block) << 20);
    }

    /**
     * Rendered length from mass. Deliberately compressed: a 30 kg catfish at true scale next to a 100 g
     * roach would be a wall, so the curve is cube-rooted and capped.
     */
    private static float spriteScale(int grams) {
        double kg = Math.max(0.02, grams / 1000.0);
        return (float) Mth.clamp(0.32 * Math.cbrt(kg) + 0.18, 0.18, 1.15);
    }

    private static ItemStack stackFor(ShoalPacket.Entry e) {
        var item = ModItems.fishItem(e.species());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
