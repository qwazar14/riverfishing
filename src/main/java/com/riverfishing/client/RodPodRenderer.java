package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.riverfishing.block.RodPodBlock;
import com.riverfishing.block.RodPodBlockEntity;
import com.riverfishing.item.AlarmType;
import com.riverfishing.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders a rod-pod's contents: docked rods resting butt-down on the crossbar with tips out over the
 * water, a sagging line from each tip down to the water (§immersion), and any mounted bite alarms.
 */
public class RodPodRenderer implements BlockEntityRenderer<RodPodBlockEntity> {
    private final ItemRenderer itemRenderer;

    public RodPodRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(RodPodBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        List<ItemStack> rods = be.getRodsForDrop();
        int n = rods.size();
        Direction facing = be.getBlockState().hasProperty(RodPodBlock.FACING)
                ? be.getBlockState().getValue(RodPodBlock.FACING) : Direction.NORTH;

        // Orient everything toward the block's facing (the water side). Base content points +Z.
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, 0.0, -0.5);

        // 1) Rods lying ALONG the cast direction (§pod-visual): the sprite plane is turned edge-on to
        // the water so from the side you see a rod resting on the bar — butt down behind it, tip out
        // over the water at a shallow angle, like a real rod on a pod.
        for (int i = 0; i < n; i++) {
            ItemStack rod = rods.get(i);
            if (rod.isEmpty()) continue;
            float x = slotX(i, n);
            pose.pushPose();
            pose.translate(x, 0.62, 0.45);
            // FIXED context maps texture-right to local -X, so POSITIVE angles here point the
            // texture diagonal (handle -> tip) toward +Z, the cast direction.
            pose.mulPose(Axis.YP.rotationDegrees(90f));  // sprite plane runs along the cast axis (+Z)
            pose.mulPose(Axis.ZP.rotationDegrees(25f));  // texture diagonal: 45° -> ~20° above horizontal
            pose.scale(1.15f, 1.15f, 1.15f);
            itemRenderer.renderStatic(rod, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        // 2) Mounted bite alarms, visible on the crossbar (§pod-alarms).
        for (int i = 0; i < n; i++) {
            AlarmType alarm = be.alarmTypeAt(i);
            if (alarm == AlarmType.NONE) continue;
            var alarmItem = ModItems.alarmItem(alarm);
            if (alarmItem == null) continue;
            float x = slotX(i, n) + 0.09f;
            pose.pushPose();
            pose.translate(x, 0.62, 0.44);
            pose.scale(0.4f, 0.4f, 0.4f);
            itemRenderer.renderStatic(new ItemStack(alarmItem), ItemDisplayContext.FIXED,
                    light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        // 3) Lines last (one buffer), from each raised tip (§pod-line): a WAITING line is TAUT (a
        // bottom rig is fished on a tight line); during a bite it twitches; a MISSED real bite goes
        // SLACK — the sagging curve is the "reel in and re-cast" cue.
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        Matrix4f m = pose.last().pose();
        Matrix3f nrm = pose.last().normal();
        float time = be.getLevel() != null ? be.getLevel().getGameTime() + partialTick : partialTick;
        for (int i = 0; i < n; i++) {
            int state = be.lineStateAt(i);
            if (state == 0) continue;
            float x = slotX(i, n);
            float tipY = 0.88f, tipZ = 1.16f;
            float endY = 0.02f, endZ = 2.3f;
            if (state == 3) {
                // Slack: the bait is gone and the line hangs limp, well below the straight pull.
                float midY = (tipY + endY) * 0.5f - 0.42f;
                float midZ = (tipZ + endZ) * 0.5f - 0.15f;
                drawLine(vc, m, nrm, x, tipY, tipZ, x, midY, midZ);
                drawLine(vc, m, nrm, x, midY, midZ, x, endY, endZ - 0.35f);
            } else if (state == 2) {
                // Bite: taut line yanked about at the water end.
                float twY = (float) Math.sin(time * 1.4 + i) * 0.07f;
                float twX = (float) Math.sin(time * 0.9 + i * 2) * 0.05f;
                drawLine(vc, m, nrm, x, tipY, tipZ, x + twX, endY + twY, endZ);
            } else {
                // Waiting: dead straight from tip to water.
                drawLine(vc, m, nrm, x, tipY, tipZ, x, endY, endZ);
            }
        }

        pose.popPose();
    }

    private static float slotX(int i, int n) {
        float t = n <= 1 ? 0.5f : (float) i / (n - 1);
        return 0.25f + t * 0.5f;
    }

    private static void drawLine(VertexConsumer vc, Matrix4f m, Matrix3f nrm,
                                 float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.vertex(m, x1, y1, z1).color(25, 25, 25, 255).normal(nrm, dx, dy, dz).endVertex();
        vc.vertex(m, x2, y2, z2).color(25, 25, 25, 255).normal(nrm, dx, dy, dz).endVertex();
    }
}
