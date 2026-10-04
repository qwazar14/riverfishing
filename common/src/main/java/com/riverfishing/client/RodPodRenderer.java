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

        // §pod-visual per tier, measured off the block models rather than eyeballed:
        //  - tier 1 (rod_pod_y): a forked branch, crotch at y 9.92u — one rod cradled at 25°.
        //  - tier 3 (the buzz-bar pod): front saddles top out at y 13.3u (z 3.2), rear at 14.3u
        //    (z 12.8) — the rod lies ACROSS both bars, so it is nearly flat: atan(1/9.6) ~ 6° up
        //    toward the water, passing y 13.5u at the sprite centre (z 7.2).
        boolean bars = n >= 3;
        float rodY = bars ? 0.845f : 0.62f;
        float rodPitch = bars ? 6f : 25f;

        // §pod-3d: a 3D blank lies THROUGH both saddles like a real rod — butt hanging in the air
        // behind the pod, blank clamped by the two bars, tip out over the water. Its own frame:
        // model -X (tip) turns onto +Z (the cast direction) and the whole rod pitches up by the
        // saddle slope. Numbers are solved from the block models, same as the sprite tier constants.
        float rod3dY = bars ? 0.79f : 0.72f;
        float rod3dPitch = bars ? 5f : 25f;
        float rod3dZ = 1.0f;
        float time = be.getLevel() != null ? be.getLevel().getGameTime() % 100000L + partialTick : partialTick;
        long now = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        // §pod-slot: looking at the pod with an empty hand shows which rod a click takes — the very rule the
        // server will apply (the client has the rods and their lines through the update tag)
        int aim = -1;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.player.getMainHandItem().isEmpty() && be.getLevel() != null
                && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && bh.getBlockPos().equals(be.getBlockPos())) {
            aim = be.takeSlot(now, bh.getLocation());
        }

        // 1) Rods lying ALONG the cast direction (§pod-visual): a sprite is turned edge-on to the
        // water so from the side you see a rod resting on the bar; a 3D blank replaces it whole.
        float[][] tips3d = new float[n][];
        for (int i = 0; i < n; i++) {
            ItemStack rod = rods.get(i);
            if (rod.isEmpty()) continue;
            float x = slotX(i, n);
            int vis = be.lineVisualAt(i);
            float[] mo = podMotion(vis, be.ticksToBite(i, now), time, i);
            // §pod-anim: a running fish rattles the rod in its rests
            float rattle = vis == 4 ? 0.006f * (float) Math.sin(time * 2.9f + i * 1.7f) : 0f;
            // ONE matrix serves both the drawn rod and the line anchor, so they can never disagree.
            // Order matters and bit us once: matrices apply right-to-left, so rotateY must sit LAST
            // in the chain (= applied to the model first, turning the blank onto +Z) and the pitch
            // before it (= applied in the turned frame, lifting the tip). The other way round the
            // pitch hit the model frame, where the rod lies ALONG x — it ROLLED the blank about its
            // own length and the rod stayed flat while the hand-derived tip climbed the slope.
            // The 0.03125 translate compensates the blank axis sitting off-centre in model z.
            org.joml.Matrix4f rodM = new org.joml.Matrix4f()
                    .translate(x - 0.03125f + rattle, rod3dY + Math.abs(rattle) + (i == aim ? 0.03f : 0f), rod3dZ)
                    .rotateX((float) Math.toRadians(-rod3dPitch))  // lift the tip by the saddle slope
                    .rotateY((float) Math.toRadians(90f));         // model -X (tip) -> +Z, guides down
            pose.pushPose();
            pose.mulPoseMatrix(rodM);
            org.joml.Matrix4f track = new org.joml.Matrix4f(rodM);
            boolean drew3d = RodItemRenderer.drawPodBlank(rod, pose, buffers, light, overlay, mo[0], track);
            pose.popPose();
            if (drew3d) {
                // the line must leave the REAL tip of this rod: the same matrix that drew the blank
                // transforms the same model-space tip the threaded line ends at
                Float tipX = rod.getItem() instanceof com.riverfishing.item.RodItem r
                        ? RodItemRenderer.blankTipX(r.rodType().jsonKey()) : null;
                if (tipX != null) {
                    org.joml.Vector3f tip = track.transformPosition(new org.joml.Vector3f(   // §pod-anim: the BENT tip
                            tipX / 16f - 0.5f, 10.5f / 16f - 0.5f, 8.5f / 16f - 0.5f));
                    tips3d[i] = new float[]{tip.x, tip.y, tip.z};
                }
                continue;
            }
            pose.pushPose();
            pose.translate(x, rodY + (i == aim ? 0.03f : 0f), 0.45);
            // FIXED context maps texture-right to local -X, so POSITIVE angles here point the
            // texture diagonal (handle -> tip) toward +Z, the cast direction.
            pose.mulPose(Axis.YP.rotationDegrees(90f));       // sprite plane runs along the cast axis (+Z)
            pose.mulPose(Axis.ZP.rotationDegrees(rodPitch));  // texture diagonal lifted above horizontal
            pose.scale(1.15f, 1.15f, 1.15f);
            itemRenderer.renderStatic(rod, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        // 2) Mounted bite alarms (§pod-alarms). On the buzz-bar pod an alarm head sits ON the front
        // bar under its rod (bar top y 13u, front face z 2.9u), not on a mid crossbar.
        float alarmY = bars ? 0.83f : 0.62f;
        float alarmZ = bars ? 0.17f : 0.44f;
        for (int i = 0; i < n; i++) {
            AlarmType alarm = be.alarmTypeAt(i);
            if (alarm == AlarmType.NONE) continue;
            var alarmItem = ModItems.alarmItem(alarm);
            if (alarmItem == null) continue;
            float x = slotX(i, n) + 0.09f;
            int vis = be.lineVisualAt(i);
            float[] mo = podMotion(vis, be.ticksToBite(i, now), time, i);
            pose.pushPose();
            pose.translate(x, alarmY, alarmZ);
            int alarmLight = light;
            float scale = 0.4f;
            if (alarm == AlarmType.BELL) {
                // §pod-anim: the bell swings from its clip: wildly on a run, steadily on a take, a
                // shiver on a nibble or a gust
                float amp = vis == 4 ? 30f : vis == 2 ? 22f : vis == 5 ? 12f : mo[1] * 20f;
                if (amp > 0f) {
                    pose.translate(0f, 0.12f, 0f);
                    pose.mulPose(Axis.ZP.rotationDegrees(amp * (float) Math.sin(time * 1.25f + i)));
                    pose.translate(0f, -0.12f, 0f);
                }
            } else if (vis == 2 || vis == 4 || vis == 5) {
                // §pod-anim: the digital alarm lights up and pops on every beep (the server beeps every 8 ticks)
                float ph = time % 8f;
                if (ph < 3f) {
                    alarmLight = 0xF000F0;
                    scale *= 1f + 0.12f * (float) Math.sin(ph / 3f * Math.PI);
                }
            }
            pose.scale(scale, scale, scale);
            itemRenderer.renderStatic(new ItemStack(alarmItem), ItemDisplayContext.FIXED,
                    alarmLight, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        // 3) Lines last (one buffer), from each raised tip (§pod-line): a WAITING line is TAUT (a
        // bottom rig is fished on a tight line); during a bite it twitches; a MISSED real bite goes
        // SLACK — the sagging curve is the "reel in and re-cast" cue.
        // §live-buffer: asked for per rod below, never cached — see LineRenderer.render.
        Matrix4f m = pose.last().pose();
        Matrix3f nrm = pose.last().normal();
        for (int i = 0; i < n; i++) {
            int state = be.lineVisualAt(i);   // §pod-anim: 4 = a self-hooked run, 5 = a false alarm
            if (state == 0) continue;
            float x = slotX(i, n);
            // §line-strand: the water line IS the line threaded along the blank — one material, one
            // look. Style off the podded rod's own LineItem: colour + alpha + diameter width; a rod
            // with no readable line keeps the old thin dark string.
            float[] style = i < rods.size() && !rods.get(i).isEmpty()
                    ? RodItemRenderer.lineStyle(rods.get(i)) : null;
            VertexConsumer lv = buffers.getBuffer(
                    style != null ? RodRenderTypes.lineStrand(style[4]) : RenderType.lines());
            int lr = style != null ? (int) style[0] : 25;
            int lg = style != null ? (int) style[1] : 25;
            int lb = style != null ? (int) style[2] : 25;
            int la = style != null ? (int) style[3] : 255;
            // 3D rods captured their true tip — start the line EXACTLY there; sprites keep the tier
            // constants. The water end stays on the slot axis: line converges from tip to cast.
            float tipX = tips3d[i] != null ? tips3d[i][0] : x;
            float tipY = tips3d[i] != null ? tips3d[i][1] : (bars ? 0.90f : 0.88f);
            float tipZ = tips3d[i] != null ? tips3d[i][2] : (bars ? 1.02f : 1.16f);
            // the water end sits ahead of the tip, wherever this rod's length put the tip
            float endY = 0.02f, endZ = Math.max(2.3f, tipZ + 0.5f);
            if (state == 3) {
                // Slack: the bait is gone and the line hangs limp, well below the straight pull.
                float midY = (tipY + endY) * 0.5f - 0.42f;
                float midZ = (tipZ + endZ) * 0.5f - 0.15f;
                drawLine(lv, m, nrm, tipX, tipY, tipZ, x, midY, midZ, lr, lg, lb, la);
                drawLine(lv, m, nrm, x, midY, midZ, x, endY, endZ - 0.35f, lr, lg, lb, la);
            } else if (state == 2 || state == 4) {
                // §pod-anim: a take PULLS the line in jerks, in time with the tip's nods; a run drags it
                // hard out and swings it about
                float pull = podMotion(state, -1, time, i)[1];
                float swing = state == 4 ? (float) Math.sin(time * 0.21f + i) * 0.18f : (float) Math.sin(time * 0.9 + i * 2) * 0.03f;
                drawLine(lv, m, nrm, tipX, tipY, tipZ, x + swing, endY - pull * 0.04f, endZ + pull * 0.4f, lr, lg, lb, la);
            } else {
                // Waiting: dead straight from tip to water, tugged by a nibble, shivering in a false alarm's gust
                float[] mo = podMotion(state, be.ticksToBite(i, now), time, i);
                float shiver = state == 5 ? (float) Math.sin(time * 2.3f + i) * 0.025f : 0f;
                drawLine(lv, m, nrm, tipX, tipY, tipZ, x + shiver, endY, endZ + mo[1] * 0.15f, lr, lg, lb, la);
            }
        }

        pose.popPose();
    }

    /**
     * §pod-anim: how a podded rod moves this instant, {bend load 0..1, line pull 0..1}, from its visual state.
     * Waiting on a tight line it keeps a slight bow, and in the last ten seconds before the take the fish
     * nibbles: small taps, more often as the take nears. A take nods the tip in jerks that come harder; a
     * self-hooked fish bends it over and keeps it throbbing; a false alarm only shivers it.
     */
    static float[] podMotion(int vis, long toBite, float t, int i) {
        float load = 0f, pull = 0f;
        switch (vis) {
            case 1 -> {
                load = 0.03f;
                if (toBite > 0 && toBite < 200) {
                    int bucket = (int) Math.floor(t / 5f);
                    if (hash(bucket * 31 + i * 7) < 0.35f * (1f - toBite / 200f)) {
                        float e = (float) Math.sin(Math.PI * (t / 5f - bucket));
                        load += 0.12f * e;
                        pull = 0.25f * e;
                    }
                }
            }
            case 2 -> {
                float a = (float) Math.pow(Math.max(0.0, Math.sin(t * 0.55f + i)), 6);
                float b = (float) Math.pow(Math.max(0.0, Math.sin(t * 1.9f + i * 2f)), 10);
                pull = Math.min(1f, 0.8f * a + 0.4f * b);
                load = 0.08f + 0.42f * a + 0.2f * b;
            }
            case 4 -> {
                float surge = (float) Math.pow(Math.sin(t * 0.33f + i), 2);
                load = 0.5f + 0.3f * surge + 0.05f * (float) Math.sin(t * 3.1f);
                pull = 0.7f + 0.3f * surge;
            }
            case 5 -> load = 0.02f + 0.025f * (float) Math.abs(Math.sin(t * 2.3f));
            default -> { }
        }
        return new float[]{load, pull};
    }

    private static float hash(int n) {
        float x = (float) Math.sin(n * 12.9898f) * 43758.547f;
        return x - (float) Math.floor(x);
    }

    private static float slotX(int i, int n) {
        return RodPodBlockEntity.slotX(i, n);   // §pod-slot: one table for the drawing and the click
    }

    private static void drawLine(VertexConsumer vc, Matrix4f m, Matrix3f nrm,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 int r, int g, int b, int a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).normal(nrm, dx, dy, dz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).normal(nrm, dx, dy, dz).endVertex();
    }
}
