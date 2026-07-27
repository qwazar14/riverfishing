package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws every visible fishing line (§line-multiplayer): from each angler's rod tip out to where their
 * cast landed, sagging under gravity and reeling toward the bank as the fight progresses (§immersion).
 * Loader-neutral — the platform world-render hook (Forge {@code RenderLevelStageEvent} /
 * Fabric {@code WorldRenderEvents.AFTER_TRANSLUCENT}) just hands us the pose stack, the camera position
 * and the partial-tick (see {@code ClientPlatform.registerLevelRenderer}).
 */
public final class LineRenderer {
    private LineRenderer() {}

    /**
     * §rod-bend-tip: per-bucket line-anchor offset {dx, dy} in near-plane units. A bent blank's tip
     * sits lower and closer than the straight sprite's, so the first-person anchor has to follow the
     * bucket or the line leaves the rod in mid-air. Index 0 stays {0,0} — a straight rod needs none.
     * Hand-tuned in game with {@code /rfrod bend N} + {@code /rfrod tip N dx dy} (2026-07-22 playtest).
     */
    public static final float[][] TIP_BEND_OFFSET = new float[com.riverfishing.item.RodData.BEND_BUCKETS + 1][2];
    static {
        float[][] tuned = {{0.043f, -0.060f}, {0.045f, -0.100f}, {0.047f, -0.130f},
                           {0.045f, -0.200f}, {0.060f, -0.250f}, {0.060f, -0.300f}};
        for (int b = 1; b <= com.riverfishing.item.RodData.BEND_BUCKETS; b++) {
            TIP_BEND_OFFSET[b] = tuned[b - 1];
        }
    }

    /** §rod-bend debug: {@code /rfrod bend N} pins the offset lookup to a bucket (-1 = read the rod). */
    public static int FORCE_BEND = -1;

    /**
     * §rod-bend: the bucket the local player's held rod is BENT to. On 26.x the bend lives in the
     * stack (RodData FLOAT 0, written server-side), so the client just reads back whatever the model
     * is already rendering instead of re-deriving it from tension — the two can never disagree.
     */
    public static int heldBend(Player player) {
        if (FORCE_BEND >= 0) return Math.min(FORCE_BEND, com.riverfishing.item.RodData.BEND_BUCKETS);
        for (net.minecraft.world.item.ItemStack stack
                : new net.minecraft.world.item.ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof com.riverfishing.item.RodItem) {
                return Mth.clamp(com.riverfishing.item.RodData.getBend(stack), 0,
                        com.riverfishing.item.RodData.BEND_BUCKETS);
            }
        }
        return 0;
    }

    //? if <26.2 {
    // 26.1: immediate mode — pull the shared buffer source and flush the lines batch ourselves.
    public static void render(PoseStack pose, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientLineState.lines().isEmpty()) return;
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
        boolean drew = drawAll(mc, vc, pose.last().pose(), pose.last().normal(), pt);
        if (drew) {
            buffers.endBatch(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
        }
        pose.popPose();
    }
    //?} else {
    /*// 26.2: MultiBufferSource is gone — geometry rides the frame's SubmitNodeCollector, exactly
    // like the BlockEntity renderers (see RodPodRenderer.submitCustomGeometry).
    public static void submit(PoseStack pose, Vec3 cam, float pt,
                              net.minecraft.client.renderer.SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientLineState.lines().isEmpty()) return;
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.lines(),
                (posePose, vc) -> drawAll(mc, vc, posePose.pose(), posePose.normal(), pt));
        pose.popPose();
    }
    *///?}

    /** The shared per-frame loop: expire stale lines, smooth, draw. Returns true when anything drew. */
    private static boolean drawAll(Minecraft mc, VertexConsumer vc, Matrix4f m, Matrix3f nrm, float pt) {
        float frameSeconds = mc.getDeltaTracker().getGameTimeDeltaTicks() / 20f;
        long now = mc.level.getGameTime();
        var it = ClientLineState.lines().entrySet().iterator();
        boolean drew = false;
        while (it.hasNext()) {
            var entry = it.next();
            ClientLineState.Line state = entry.getValue();
            // The server refreshes lines every ~2 s; a line whose owner vanished expires here.
            if (now - state.lastUpdate > ClientLineState.STALE_TICKS) {
                it.remove();
                continue;
            }
            if (!(mc.level.getEntity(entry.getKey()) instanceof Player player)) continue;
            state.tickSmoothing(frameSeconds);
            renderLine(mc, vc, m, nrm, player, state, pt);
            drew = true;
        }
        return drew;
    }

    private static void renderLine(Minecraft mc, VertexConsumer vc, Matrix4f m, Matrix3f nrm,
                                   Player player, ClientLineState.Line state, float pt) {
        // Rod-tip anchor: exact vanilla FishingHookRenderer math (§vanilla-line).
        Vec3 tip = rodTipAnchor(mc, player, pt);

        // Line end: the cast target riding the waves, pulled toward the angler as reel-in progress
        // rises. The float and the line END move together — like the vanilla bobber on water.
        float bobT = mc.level.getGameTime() + pt;
        double bob;
        if (state.floatKind == 0) {
            bob = 0.0;
        } else if (state.biting) {
            // §bite-visual: the float PLUNGES under and twitches hard — the classic "подсекай!" cue.
            bob = -0.22 + Math.sin(bobT * 1.6) * 0.08 + Math.sin(bobT * 0.53) * 0.04;
        } else {
            // Idle wave imitation: two slow overlapping swells, like the vanilla hook on water.
            bob = Math.sin(bobT * 0.13) * 0.05 + Math.sin(bobT * 0.047) * 0.03;
        }
        BlockPos t = state.target;
        Vec3 water = new Vec3(t.getX() + 0.5, t.getY() + 0.95 + bob, t.getZ() + 0.5);
        Vec3 bank = player.position().add(player.getViewVector(pt).scale(1.2)).add(0, 0.1, 0);
        Vec3 end = water.lerp(bank, Mth.clamp(state.smoothProgress * 0.85f, 0f, 0.9f));

        int color = state.color;
        int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;

        // Vanilla string shape (FishingHookRenderer.stringVertex): 16 segments from the water end
        // up to the rod tip, y following (f² + f)/2 — the line hangs toward the water exactly like
        // the vanilla rod's, plus the classic 0.25 lift where it leaves the float.
        double dx = tip.x - end.x, dy = tip.y - end.y, dz = tip.z - end.z;
        Vec3 prev = end.add(0, 0.25, 0);
        for (int k = 1; k <= 16; k++) {
            double f = k / 16.0;
            Vec3 p = new Vec3(end.x + dx * f,
                    end.y + dy * (f * f + f) * 0.5 + 0.25 * (1.0 - f),
                    end.z + dz * f);
            line(vc, m, nrm, prev, p, cr, cg, cb);
            prev = p;
        }

        // The bobber (§bobber-render): only float rigs show one — a red antenna over a white body.
        // Spinning lures and bottom rigs have nothing on the surface. Wave motion is already in
        // `end`, so the float and the line move as one.
        if (state.floatKind != 0) {
            drawFloat(vc, m, nrm, end, state.floatKind == 2);
        }
    }

    /**
     * §float-kind geometry. The old float was three vertical lines 0.018 apart, and the visible gaps
     * between them are exactly why players read it as holed sticks — so the proper float is a tapered
     * bundle at 0.008 spacing, which reads as one solid body at any distance.
     *
     * @param proper a float item is rigged: red-tipped, bodied, with a keel. Otherwise a plain peg —
     *               honest about the fact that there is no float on this rig.
     */
    private static void drawFloat(VertexConsumer vc, Matrix4f m, Matrix3f nrm, Vec3 end, boolean proper) {
        if (!proper) {
            // A bare stick riding the surface. Deliberately plain: it should look like something is
            // missing, because something is.
            for (int i = -1; i <= 1; i++) {
                double dx = i * 0.008;
                line(vc, m, nrm, end.add(dx, 0.11, 0), end.add(dx, -0.02, 0), 196, 190, 176);
            }
            return;
        }
        // Antenna: thin, tall, red — the part a player actually watches for the plunge.
        for (int i = -1; i <= 1; i++) {
            double dx = i * 0.008;
            line(vc, m, nrm, end.add(dx, 0.26, 0), end.add(dx, 0.11, 0), 226, 58, 46);
            line(vc, m, nrm, end.add(0, 0.26, dx), end.add(0, 0.11, dx), 226, 58, 46);
        }
        // Body: seven columns, tapered at the edges so the silhouette is rounded rather than a slab.
        for (int i = -3; i <= 3; i++) {
            double dx = i * 0.008;
            double taper = 0.10 - Math.abs(i) * 0.018;          // shorter towards the rim
            line(vc, m, nrm, end.add(dx, 0.11, 0), end.add(dx, 0.11 - 0.13 - taper, 0), 242, 240, 230);
            line(vc, m, nrm, end.add(0, 0.11, dx), end.add(0, 0.11 - 0.13 - taper, dx), 242, 240, 230);
        }
        // Keel below the waterline, darker — gives the float a bottom instead of a cut-off edge.
        for (int i = -1; i <= 1; i++) {
            double dx = i * 0.008;
            line(vc, m, nrm, end.add(dx, -0.12, 0), end.add(dx, -0.19, 0), 120, 116, 108);
        }
    }

    /**
     * Rod-tip anchor, replicating vanilla {@code FishingHookRenderer} (§vanilla-line): in first
     * person the line leaves the ON-SCREEN rod tip (a near-plane point on the rod-hand side, scaled
     * by FOV and swinging with the arm); in third person / for other players it hangs off the rod
     * hand of the body model — so every line starts at a rod, not in the air.
     */
    private static Vec3 rodTipAnchor(Minecraft mc, Player player, float pt) {
        int arm = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
        if (!(player.getMainHandItem().getItem() instanceof com.riverfishing.item.RodItem)) {
            arm = -arm; // the rod is in the off hand
        }
        float swingProgress = player.getAttackAnim(pt);
        float swing = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);

        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            double fov = mc.options.fov().get();
            double fovScale = 960.0 / fov;
            // §rod-bend-tip: shift the anchor with the blank's current bend so the line stays ON the
            // bent tip instead of hanging off where the straight sprite used to end (/rfrod tip tunes).
            int bend = heldBend(player);
            float tipDx = TIP_BEND_OFFSET[bend][0];
            float tipDy = TIP_BEND_OFFSET[bend][1];
            // §26.1: getNearPlane now takes the fov in degrees instead of reading it itself.
            //? if <26.2 {
            Vec3 v = mc.gameRenderer.getMainCamera().getNearPlane((float) fov)
            //?} else {
            /*Vec3 v = mc.gameRenderer.mainCamera().getNearPlane((float) fov)
            *///?}
                    .getPointOnPlane(arm * (0.525f + tipDx), -0.1f + tipDy)
                    .scale(fovScale)
                    .yRot(swing * 0.5f)
                    .xRot(-swing * 0.7f);
            return new Vec3(
                    Mth.lerp(pt, player.xo, player.getX()) + v.x,
                    Mth.lerp(pt, player.yo, player.getY()) + v.y + player.getEyeHeight(),
                    Mth.lerp(pt, player.zo, player.getZ()) + v.z);
        }

        float bodyYaw = Mth.lerp(pt, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180f);
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        double side = arm * 0.35;
        return new Vec3(
                Mth.lerp(pt, player.xo, player.getX()) - cos * side - sin * 0.8,
                player.yo + player.getEyeHeight() + (player.getY() - player.yo) * pt - 0.45
                        + (player.isCrouching() ? -0.1875 : 0.0),
                Mth.lerp(pt, player.zo, player.getZ()) - sin * side + cos * 0.8);
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f nrm, Vec3 a, Vec3 b,
                             int r, int g, int bl) {
        float dx = (float) (b.x - a.x), dy = (float) (b.y - a.y), dz = (float) (b.z - a.z);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 1e-4f) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 255).setNormal(dx, dy, dz).setLineWidth(2.0f);
        vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 255).setNormal(dx, dy, dz).setLineWidth(2.0f);
    }
}
