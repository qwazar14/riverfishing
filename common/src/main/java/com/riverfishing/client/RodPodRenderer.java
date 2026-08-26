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
 * Renders a rod-pod's contents: docked rods resting on the bars with tips out over the water, a line
 * from each tip down to the water (§immersion), and any mounted bite alarms.
 *
 * <p>§pod-3d: a 3D blank lies THROUGH both saddles like a real rod — butt hanging in the air behind
 * the pod, blank clamped by the two bars, tip out over the water — drawn straight by
 * {@link RodChain#submitPod}. The sprite path stays as the fallback for {@code /rfrod blank off}.
 * §26.1: two-phase render-state model; the rod stacks come across in the state so the 3D path (and
 * the line style read off the fitted LineItem) can run at submit time.
 */
public class RodPodRenderer implements BlockEntityRenderer<RodPodBlockEntity, RodPodRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public RodPodRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
    }

    static class Docked {
        final ItemStackRenderState item = new ItemStackRenderState();
        ItemStack stack = ItemStack.EMPTY;   // the rod itself — the 3D path draws from this
        float x;
        boolean alarm; // alarms draw smaller and offset on the bar
    }

    public static class State extends BlockEntityRenderState {
        final List<Docked> items = new ArrayList<>();
        final int[] lineStates = new int[4];
        final ItemStack[] lineRods = new ItemStack[4];
        final float[] lineX = new float[4];
        int slots;
        float time;
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

        List<ItemStack> rods = be.getRodsForDrop();
        int n = rods.size();
        s.slots = Math.min(n, s.lineStates.length);
        Direction facing = be.getBlockState().hasProperty(RodPodBlock.FACING)
                ? be.getBlockState().getValue(RodPodBlock.FACING) : Direction.NORTH;
        s.yRot = -facing.toYRot();
        s.time = be.getLevel() != null ? be.getLevel().getGameTime() + partialTick : partialTick;

        for (int i = 0; i < n; i++) {
            ItemStack rod = rods.get(i);
            if (rod.isEmpty()) continue;
            Docked d = new Docked();
            d.x = slotX(i, n);
            d.stack = rod;
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

        for (int i = 0; i < s.slots; i++) {
            s.lineStates[i] = be.lineStateAt(i);
            s.lineRods[i] = i < rods.size() ? rods.get(i) : ItemStack.EMPTY;
            s.lineX[i] = slotX(i, n);
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.items.isEmpty() && s.slots == 0) return;

        // Orient everything toward the block's facing (the water side). Base content points +Z.
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(s.yRot));
        pose.translate(-0.5, 0.0, -0.5);

        // §pod-visual per tier, measured off the block models rather than eyeballed:
        //  - tier 1 (rod_pod_y): a forked branch, crotch at y 9.92u — one rod cradled at 25°.
        //  - tier 3 (the buzz-bar pod): front saddles top out at y 13.3u (z 3.2), rear at 14.3u
        //    (z 12.8) — the rod lies ACROSS both bars, nearly flat, ~6° up toward the water.
        boolean bars = s.slots >= 3;
        float rodY = bars ? 0.845f : 0.62f;
        float rodPitch = bars ? 6f : 25f;
        float rod3dY = bars ? 0.79f : 0.72f;
        float rod3dPitch = bars ? 5f : 25f;
        float rod3dZ = 1.0f;

        // 1) Rods lying ALONG the cast direction (§pod-visual) + 2) mounted alarms.
        float[][] tips3d = new float[s.lineX.length][];
        int slot = 0;
        for (Docked d : s.items) {
            if (d.alarm) {
                pose.pushPose();
                pose.translate(d.x, bars ? 0.83f : 0.62f, bars ? 0.17f : 0.44f);
                pose.scale(0.4f, 0.4f, 0.4f);
                d.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
                continue;
            }
            int i = slot++;
            // ONE matrix serves both the drawn rod and the line anchor, so they can never disagree.
            // Order matters and bit us once: matrices apply right-to-left, so rotateY must sit LAST
            // in the chain (= applied to the model first, turning the blank onto +Z) and the pitch
            // before it (= applied in the turned frame, lifting the tip). The other way round the
            // pitch hit the model frame, where the rod lies ALONG x — it ROLLED the blank about its
            // own length. The 0.03125 translate compensates the blank axis sitting off-centre in z.
            org.joml.Matrix4f rodM = new org.joml.Matrix4f()
                    .translate(d.x - 0.03125f, rod3dY, rod3dZ)
                    .rotateX((float) Math.toRadians(-rod3dPitch))  // lift the tip by the saddle slope
                    .rotateY((float) Math.toRadians(90f));         // model -X (tip) -> +Z, guides down
            pose.pushPose();
            pose.mulPose(rodM);
            boolean drew3d = RodChain.submitPod(d.stack, pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            if (drew3d) {
                // the line must leave the REAL tip of this rod: the same matrix that drew the blank
                // transforms the same model-space tip the threaded line ends at
                Float tipX = d.stack.getItem() instanceof com.riverfishing.item.RodItem r
                        ? RodChain.blankTipX(r.rodType().jsonKey()) : null;
                if (tipX != null && i < tips3d.length) {
                    org.joml.Vector3f tip = rodM.transformPosition(new org.joml.Vector3f(
                            tipX / 16f - 0.5f, 10.5f / 16f - 0.5f, 8.5f / 16f - 0.5f));
                    tips3d[i] = new float[]{tip.x, tip.y, tip.z};
                }
                continue;
            }
            pose.pushPose();
            pose.translate(d.x, rodY, 0.45);
            // FIXED context maps texture-right to local -X, so POSITIVE angles here point the
            // texture diagonal (handle -> tip) toward +Z, the cast direction.
            pose.mulPose(Axis.YP.rotationDegrees(90f));       // sprite plane runs along the cast axis (+Z)
            pose.mulPose(Axis.ZP.rotationDegrees(rodPitch));  // texture diagonal lifted above horizontal
            pose.scale(1.15f, 1.15f, 1.15f);
            d.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        // 3) Lines last, one custom-geometry node (§pod-line): a WAITING line is TAUT (a bottom rig
        // is fished on a tight line); during a bite it twitches; a MISSED real bite goes SLACK — the
        // sagging curve is the "reel in and re-cast" cue. §line-strand: the water line IS the line
        // threaded along the blank — style off the podded rod's own LineItem.
        int slots = s.slots;
        float time = s.time;
        boolean any = false;
        for (int i = 0; i < slots; i++) if (s.lineStates[i] != 0) any = true;
        if (any) {
            float[][] tips = tips3d;
            int[] states = s.lineStates.clone();
            ItemStack[] lineRods = s.lineRods.clone();
            float[] xs = s.lineX.clone();
            collector.submitCustomGeometry(pose, RenderTypes.lines(), (posePose, vc) -> {
                for (int i = 0; i < slots; i++) {
                    int state = states[i];
                    if (state == 0) continue;
                    float x = xs[i];
                    float[] style = lineRods[i] != null && !lineRods[i].isEmpty()
                            ? RodChain.lineStyle(lineRods[i]) : null;
                    int lr = style != null ? (int) style[0] : 25;
                    int lg = style != null ? (int) style[1] : 25;
                    int lb = style != null ? (int) style[2] : 25;
                    int la = style != null ? (int) style[3] : 255;
                    float lw = style != null ? style[4] : 2.0f;
                    // 3D rods captured their true tip — start the line EXACTLY there; sprites keep
                    // the tier constants. The water end stays on the slot axis.
                    float tipX = tips[i] != null ? tips[i][0] : x;
                    float tipY = tips[i] != null ? tips[i][1] : (bars ? 0.90f : 0.88f);
                    float tipZ = tips[i] != null ? tips[i][2] : (bars ? 1.02f : 1.16f);
                    float endY = 0.02f, endZ = Math.max(2.3f, tipZ + 0.5f);
                    if (state == 3) {
                        // Slack: the bait is gone and the line hangs limp, well below the straight pull.
                        float midY = (tipY + endY) * 0.5f - 0.42f;
                        float midZ = (tipZ + endZ) * 0.5f - 0.15f;
                        drawLine(posePose, vc, tipX, tipY, tipZ, x, midY, midZ, lr, lg, lb, la, lw);
                        drawLine(posePose, vc, x, midY, midZ, x, endY, endZ - 0.35f, lr, lg, lb, la, lw);
                    } else if (state == 2) {
                        // Bite: taut line yanked about at the water end.
                        float twY = (float) Math.sin(time * 1.4 + i) * 0.07f;
                        float twX = (float) Math.sin(time * 0.9 + i * 2) * 0.05f;
                        drawLine(posePose, vc, tipX, tipY, tipZ, x + twX, endY + twY, endZ, lr, lg, lb, la, lw);
                    } else {
                        // Waiting: dead straight from tip to water.
                        drawLine(posePose, vc, tipX, tipY, tipZ, x, endY, endZ, lr, lg, lb, la, lw);
                    }
                }
            });
        }

        pose.popPose();
    }

    private static float slotX(int i, int n) {
        float t = n <= 1 ? 0.5f : (float) i / (n - 1);
        // the buzz-bar pod's saddles sit at x 4.475 / 8 / 11.525 in its model — 0.28..0.72 in blocks;
        // the old 0.25..0.75 spread parked the outer rods half a unit outside their rests
        return n >= 3 ? 0.2797f + t * 0.4406f : 0.25f + t * 0.5f;
    }

    private static void drawLine(PoseStack.Pose pose, VertexConsumer vc,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 int r, int g, int b, int a, float w) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(w);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(w);
    }
}
