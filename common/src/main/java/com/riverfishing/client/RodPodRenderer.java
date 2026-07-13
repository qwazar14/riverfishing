package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.riverfishing.block.RodPodBlock;
import com.riverfishing.block.RodPodBlockEntity;
import com.riverfishing.item.AlarmType;
import com.riverfishing.registry.ModItems;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a rod-pod's contents: docked rods resting butt-down on the crossbar with tips out over the
 * water, a sagging line from each tip down to the water (§immersion), and any mounted bite alarms.
 * §26.1: two-phase render-state model; the lines go through {@code submitCustomGeometry}.
 */
public class RodPodRenderer implements BlockEntityRenderer<RodPodBlockEntity, RodPodRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public RodPodRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
    }

    static class Docked {
        final ItemStackRenderState item = new ItemStackRenderState();
        float x;
        boolean alarm; // alarms draw smaller and offset on the crossbar
    }

    /** One line segment (start/end in block-local coords, already facing-rotated by the pose). */
    record Seg(float x1, float y1, float z1, float x2, float y2, float z2) {}

    public static class State extends BlockEntityRenderState {
        final List<Docked> items = new ArrayList<>();
        final List<Seg> lines = new ArrayList<>();
        float yRot;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RodPodBlockEntity be, State s, float partialTick, net.minecraft.world.phys.Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, overlay);
        s.items.clear();
        s.lines.clear();

        List<ItemStack> rods = be.getRodsForDrop();
        int n = rods.size();
        Direction facing = be.getBlockState().hasProperty(RodPodBlock.FACING)
                ? be.getBlockState().getValue(RodPodBlock.FACING) : Direction.NORTH;
        s.yRot = -facing.toYRot();

        for (int i = 0; i < n; i++) {
            ItemStack rod = rods.get(i);
            if (rod.isEmpty()) continue;
            Docked d = new Docked();
            d.x = slotX(i, n);
            itemModelResolver.updateForTopItem(d.item, rod, ItemDisplayContext.FIXED, be.getLevel(), null, i);
            s.items.add(d);
        }
        for (int i = 0; i < n; i++) {
            AlarmType alarm = be.alarmTypeAt(i);
            if (alarm == AlarmType.NONE) continue;
            var alarmItem = ModItems.alarmItem(alarm);
            if (alarmItem == null) continue;
            Docked d = new Docked();
            d.x = slotX(i, n) + 0.09f;
            d.alarm = true;
            itemModelResolver.updateForTopItem(d.item, new ItemStack(alarmItem), ItemDisplayContext.FIXED,
                    be.getLevel(), null, 100 + i);
            s.items.add(d);
        }

        // Lines (§pod-line): a WAITING line is TAUT (a bottom rig is fished on a tight line); during a
        // bite it twitches; a MISSED real bite goes SLACK — the sag is the "reel in and re-cast" cue.
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
                s.lines.add(new Seg(x, tipY, tipZ, x, midY, midZ));
                s.lines.add(new Seg(x, midY, midZ, x, endY, endZ - 0.35f));
            } else if (state == 2) {
                // Bite: taut line yanked about at the water end.
                float twY = (float) Math.sin(time * 1.4 + i) * 0.07f;
                float twX = (float) Math.sin(time * 0.9 + i * 2) * 0.05f;
                s.lines.add(new Seg(x, tipY, tipZ, x + twX, endY + twY, endZ));
            } else {
                // Waiting: dead straight from tip to water.
                s.lines.add(new Seg(x, tipY, tipZ, x, endY, endZ));
            }
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.items.isEmpty() && s.lines.isEmpty()) return;

        // Orient everything toward the block's facing (the water side). Base content points +Z.
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(s.yRot));
        pose.translate(-0.5, 0.0, -0.5);

        // 1) Rods lying ALONG the cast direction (§pod-visual) + 2) mounted alarms on the crossbar.
        for (Docked d : s.items) {
            pose.pushPose();
            if (d.alarm) {
                pose.translate(d.x, 0.62, 0.44);
                pose.scale(0.4f, 0.4f, 0.4f);
            } else {
                pose.translate(d.x, 0.62, 0.45);
                // FIXED context maps texture-right to local -X, so POSITIVE angles here point the
                // texture diagonal (handle -> tip) toward +Z, the cast direction.
                pose.mulPose(Axis.YP.rotationDegrees(90f));  // sprite plane runs along the cast axis (+Z)
                pose.mulPose(Axis.ZP.rotationDegrees(25f));  // texture diagonal: 45° -> ~20° above horizontal
                pose.scale(1.15f, 1.15f, 1.15f);
            }
            d.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        // 3) Lines last, as one custom-geometry node on the lines render type.
        if (!s.lines.isEmpty()) {
            List<Seg> segs = List.copyOf(s.lines);
            collector.submitCustomGeometry(pose, RenderTypes.lines(), (posePose, vc) -> {
                for (Seg seg : segs) {
                    drawLine(posePose, vc, seg);
                }
            });
        }

        pose.popPose();
    }

    private static float slotX(int i, int n) {
        float t = n <= 1 ? 0.5f : (float) i / (n - 1);
        return 0.25f + t * 0.5f;
    }

    private static void drawLine(PoseStack.Pose pose, VertexConsumer vc, Seg s) {
        float dx = s.x2() - s.x1(), dy = s.y2() - s.y1(), dz = s.z2() - s.z1();
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(pose, s.x1(), s.y1(), s.z1()).setColor(25, 25, 25, 255).setNormal(dx, dy, dz);
        vc.addVertex(pose, s.x2(), s.y2(), s.z2()).setColor(25, 25, 25, 255).setNormal(dx, dy, dz);
    }
}
