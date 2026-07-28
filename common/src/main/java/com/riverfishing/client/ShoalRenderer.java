package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.network.ShoalPacket;
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
        List<ItemStack> stacks = ShoalState.stacks();
        if (fish.isEmpty() || stacks.size() != fish.size()) return;

        BlockPos c = ShoalState.centre();
        Vec3 centre = new Vec3(c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5);
        if (centre.distanceToSqr(cam) > MAX_DIST * MAX_DIST) return;

        float time = mc.level.getGameTime() + partialTick;

        // Lanes are laid out across the water body, not at a fixed spacing: the outermost circuit of a
        // ditch has to stay in the ditch (§shoal-spread), or fish visibly swim over the bank.
        int lanes = 1;
        for (ShoalPacket.Entry e : fish) lanes = Math.max(lanes, e.lane() + 1);
        double rOuter = Mth.clamp(ShoalState.spread() * 0.9, 1.1, 5.0);

        for (int i = 0; i < fish.size(); i++) {
            ShoalPacket.Entry e = fish.get(i);
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

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

            // The circuits are drawn around one surface block, which near a bank means part of a lap can
            // leave the water. The client has the blocks, so it just checks: a fish is only drawn where
            // there IS water. Without this you get fish over the grass and fish buried in the bottom.
            if (mc.level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) continue;

            // Depth dims a fish, it does not delete it: at the bottom of a lake you should still make
            // out that something is down there. The first cut faded to 0.12 and deep water read as empty.
            float depthFade = Mth.clamp(1.0f - e.depth() * 0.055f, 0.45f, 1.0f);
            float alpha = fade * depthFade;
            if (alpha <= 0.02f) continue;

            // The sprite has no alpha channel of its own here, so fading is done by shrinking the light
            // it is drawn with — a dim fish in dark water, which is what depth actually looks like.
            int light = packedLight(alpha);

            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y, z - cam.z);
            // §shoal-side: the fish is a flat sprite, so it must stay BROADSIDE to whoever is looking or
            // it turns edge-on and disappears — the aquarium's rule, generalised from a fixed viewing
            // face to a camera that moves. Billboard around Y towards the camera, then flip 180° when the
            // fish is travelling to the viewer's left, so the head always leads (never tail-first).
            double toCamX = cam.x - x, toCamZ = cam.z - z;
            float faceCam = (float) Math.toDegrees(Math.atan2(-toCamX, toCamZ));
            // Velocity along the circuit, and the viewer's right-hand axis: forward (x,z) → right (−z,x).
            // The flip is decided against the camera's BLOCK, not its exact position: at the moment the
            // fish swims straight at you the sign sits on zero, and head-bob alone would make it mirror
            // back and forth every frame. Rounding to a block makes the turn happen once, cleanly.
            double vx = -Mth.sin(t), vz = Mth.cos(t) * 0.75;
            double rightX = Math.floor(cam.z) + 0.5 - z, rightZ = -(Math.floor(cam.x) + 0.5 - x);
            float flip = (vx * rightX + vz * rightZ) < 0 ? 180f : 0f;
            pose.mulPose(Axis.YP.rotationDegrees(-faceCam + flip));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05f + i) * 3f));   // a slight roll
            float scale = spriteScale(e.lengthCm());
            pose.scale(scale, scale, scale);
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    pose, buffers, mc.level, 0);
            pose.popPose();
        }
        // The item renderer batches; without this the shoal would not appear until something else flushed.
        buffers.endBatch();
    }

    /** Fade by light level: 15 is a fully lit fish, 6 is a shape you are not sure about. */
    private static int packedLight(float alpha) {
        int block = 6 + (int) (alpha * 9f);
        return (Math.min(15, block) << 4) | (Math.min(15, block) << 20);
    }

    /**
     * The mod already scales a fish by its length — {@link com.riverfishing.item.FishItem#getIconScale}
     * drives the journal, the hand and the aquarium — so the shoal uses the same figure and a gudgeon
     * cannot come out the size of a pike. FIXED caps that at 2, which is right in a slot but flattens
     * every sea monster into the same silhouette, so anything over a metre gets its length back on top.
     */
    private static float spriteScale(int lengthCm) {
        float big = lengthCm > 100 ? Mth.clamp(1f + (lengthCm - 100) / 260f, 1f, 2.2f) : 1f;
        return 0.55f * big;
    }
}
