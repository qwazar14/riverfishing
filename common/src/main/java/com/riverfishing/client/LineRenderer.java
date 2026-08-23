package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws every visible fishing line (§line-multiplayer): from each angler's rod tip out to where their
 * cast landed, hanging by the fight's own taut/slack state (§line-taut-eased) and reeling toward the
 * bank as the fight progresses (§immersion). Loader-neutral — the platform world-render hook just
 * hands us the pose stack, the camera position and the partial-tick.
 */
public final class LineRenderer {
    private LineRenderer() {}

    /**
     * §rod-bend-tip: per-bucket line-anchor offset {dx, dy} in near-plane units — the SPRITE path's
     * anchor nudges. The 3D chain replaces this with the captured tip (§rod-tip-3d).
     */
    public static final float[][] TIP_BEND_OFFSET = new float[com.riverfishing.item.RodData.BEND_BUCKETS + 1][2];
    static {
        float[][] tuned = {{0.043f, -0.060f}, {0.045f, -0.100f}, {0.047f, -0.130f},
                           {0.045f, -0.200f}, {0.060f, -0.250f}, {0.060f, -0.300f}};
        for (int b = 1; b <= com.riverfishing.item.RodData.BEND_BUCKETS; b++) {
            TIP_BEND_OFFSET[b] = tuned[b - 1];
        }
    }

    /** §rod-tip-3d: constant on-screen trim for the captured FP tip anchor, near-plane units
     *  ({@code /rfrod tip3d dx dy}) — the fallback for frames the hand line did not draw. */
    public static final float[] TIP3D_OFFSET = {0f, 0f};

    /** §rod-bend debug: {@code /rfrod bend N} pins the offset lookup to a bucket (-1 = read the rod). */
    public static int FORCE_BEND = -1;

    /**
     * §rod-bend: the bucket the local player's held rod is BENT to — the SPRITE path's read. On 26.x
     * the bend lives in the stack (RodData FLOAT 0, written server-side).
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
        // Rod-tip anchor: the captured 3D tip where fresh, vanilla FishingHookRenderer math otherwise.
        Vec3 tip = rodTipAnchor(mc, player, pt);
        Vec3 end = lineEnd(mc, player, state, pt);

        int color = state.color;
        int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;

        // §line-strand: material and diameter come off the angler's HELD ROD — vanilla already syncs
        // held items to every client, so nothing needed adding to the packet. Alpha and width follow
        // the fitted line (fluoro nearly vanishes, thick carp mono draws heavy); the synced colour
        // keeps carrying the hue. No rod readable → vanilla-thin opaque, as before.
        float[] style = null;
        var held = player.getMainHandItem().getItem() instanceof com.riverfishing.item.RodItem
                ? player.getMainHandItem() : player.getOffhandItem();
        if (held.getItem() instanceof com.riverfishing.item.RodItem) {
            style = RodChain.lineStyle(held);
        }
        int alpha = style == null ? 255 : (int) style[3];
        float width = style == null ? 2.0f : style[4];

        // §hand-line: the LOCAL first-person string is drawn by the hand pass, welded to the tip's
        // own matrix — drawing it here too would double it, one frame behind. Everyone else's lines
        // (and our own in third person) still belong to this pass.
        boolean handDrawn = player == mc.player && mc.options.getCameraType().isFirstPerson()
                && RodChain.handLineFresh();
        if (!handDrawn) {
            // Vanilla string SHAPE (FishingHookRenderer.stringVertex), hang replaced by §line-taut —
            // tension straightens the string, a fish running at the angler bellies it.
            double time = mc.level.getGameTime() + pt;
            double dx = tip.x - end.x, dy = tip.y - end.y, dz = tip.z - end.z;
            Vec3 prev = end.add(0, hangOffset(state, dy, 0.0, time), 0);
            for (int k = 1; k <= 16; k++) {
                double f = k / 16.0;
                Vec3 p = new Vec3(end.x + dx * f,
                        end.y + hangOffset(state, dy, f, time),
                        end.z + dz * f);
                line(vc, m, nrm, prev, p, cr, cg, cb, alpha, width);
                prev = p;
            }
        }

        // The bobber (§bobber-render): only float rigs show one — a red antenna over a white body.
        // Spinning lures and bottom rigs have nothing on the surface. Wave motion is already in
        // `end`, so the float and the line move as one.
        if (state.floatKind != 0) {
            drawFloat(vc, m, nrm, end, state.floatKind == 2);
        }
    }

    /**
     * §hand-line: where this angler's line meets the water THIS frame — the cast target riding the
     * waves, pulled toward the angler as reel-in progress rises. Shared by the world pass and the
     * first-person hand pass ({@link RodChain}), so the two can never disagree on the far end.
     */
    static Vec3 lineEnd(Minecraft mc, Player player, ClientLineState.Line state, float pt) {
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
        return water.lerp(bank, Mth.clamp(state.smoothProgress * 0.85f, 0f, 0.9f));
    }

    /**
     * §line-taut: the vertical hang of the string at parameter {@code f} (0 = water end, 1 = tip),
     * replacing the fixed vanilla catenary. The line is a physical readout of the fight:
     * <ul>
     *   <li>idle / waiting — the vanilla hang, lazy and heavy;</li>
     *   <li>under tension — pulled STRAIGHT like a string, with a fine tremble once it is loaded;</li>
     *   <li>fish coming AT the angler — the pull is gone and the line bellies well below the chord,
     *       which is the classic "reel, reel, it is running at you" read.</li>
     * </ul>
     * Shared by the world pass and the first-person hand pass, so every observer sees one line.
     */
    static double hangOffset(ClientLineState.Line state, double dy, double f, double time) {
        // §line-taut-eased: the hang reads the DISPLAYED taut/slack, which tickSmoothing chases
        // asymmetrically (snaps tight, relaxes at cable speed) over a wide tension band — so between
        // the dead string and the deep belly lives a continuous scale of partial droop, and every
        // transition is a movement, not a switch.
        float taut = state != null ? state.dispTaut : 0f;
        float slack = state != null ? state.dispSlack : 0f;
        double sag = dy * (f * f + f) * 0.5 + 0.25 * (1.0 - f);   // the vanilla hang, lift included
        double straight = dy * f;
        // even a string under full load keeps a few percent of catenary — a laser line reads fake
        double y = straight + (sag - straight) * (1.0 - taut * 0.96);
        if (slack > 0f) {
            y -= slack * 0.6 * 4.0 * f * (1.0 - f);               // deepest mid-span, zero at both ends
        }
        if (taut > 0.7f) {
            // a loaded string trembles — fading in with taut, growing with real nearness to breaking
            double gate = (taut - 0.7) / 0.3;
            double amp = (0.008 + 0.025 * (state != null ? state.smoothTension : 0f)) * gate * gate;
            y += Math.sin(time * 2.1 + f * 31.0) * amp * Math.sin(Math.PI * f);
        }
        return y;
    }

    /**
     * §float-kind geometry, matched to the float item's own 16×16 icon so the thing on the water and the
     * thing in the inventory are recognisably one object.
     *
     * @param proper a float item is rigged: antenna, bulbous red-over-white body, dark keel — the icon.
     *               Otherwise a single red-over-white line: a goose quill, and honestly nothing more,
     *               because nothing more is on the rig.
     */
    private static void drawFloat(VertexConsumer vc, Matrix4f m, Matrix3f nrm, Vec3 end, boolean proper) {
        final int rR = 192, rG = 57, rB = 43;        // #C0392B — the icon's red
        final int wR = 236, wG = 236, wB = 226;      // #ECECE2 — the icon's white
        final int dR = 46, dG = 28, dB = 16;         // #2E1C10 — the icon's dark outline / keel

        if (!proper) {
            // A goose quill: ONE column, red above the water, white at the surface.
            line(vc, m, nrm, end.add(0, 0.22, 0), end.add(0, 0.07, 0), rR, rG, rB);
            line(vc, m, nrm, end.add(0, 0.07, 0), end.add(0, -0.03, 0), wR, wG, wB);
            return;
        }

        // Antenna — one thin red stalk, cross-hatched so it survives being viewed edge-on.
        line(vc, m, nrm, end.add(0, 0.26, 0), end.add(0, 0.12, 0), rR, rG, rB);
        line(vc, m, nrm, end.add(0.005, 0.26, 0), end.add(0.005, 0.12, 0), rR, rG, rB);
        line(vc, m, nrm, end.add(0, 0.26, 0.005), end.add(0, 0.12, 0.005), rR, rG, rB);

        // Body — widest at the red/white waterline, tapering to both ends, exactly like the icon's bulge.
        // Nine columns at 0.006 leave no gap to read as a stripe.
        for (int i = -4; i <= 4; i++) {
            double off = i * 0.006;
            double taper = 1.0 - Math.abs(i) / 5.5;          // 1.0 at the centre, ~0.27 at the rim
            double top = 0.05 + 0.07 * taper;                // red cap height
            double bot = 0.05 - 0.07 * taper;                // white belly depth
            line(vc, m, nrm, end.add(off, top, 0), end.add(off, 0.05, 0), rR, rG, rB);
            line(vc, m, nrm, end.add(off, 0.05, 0), end.add(off, bot, 0), wR, wG, wB);
            line(vc, m, nrm, end.add(0, top, off), end.add(0, 0.05, off), rR, rG, rB);
            line(vc, m, nrm, end.add(0, 0.05, off), end.add(0, bot, off), wR, wG, wB);
        }

        // Keel below the waterline — gives the body a bottom instead of a cut-off edge.
        line(vc, m, nrm, end.add(0, -0.02, 0), end.add(0, -0.10, 0), dR, dG, dB);
        line(vc, m, nrm, end.add(0.005, -0.02, 0), end.add(0.005, -0.10, 0), dR, dG, dB);
        line(vc, m, nrm, end.add(0, -0.02, 0.005), end.add(0, -0.10, 0.005), dR, dG, dB);
    }

    /**
     * Rod-tip anchor: in first person the captured 3D tip's screen point (§rod-tip-3d) where fresh,
     * else the sprite near-plane constant; in third person the local player's tip captured in view
     * space, rotated back to world; for everyone else the vanilla body-model guess.
     */
    private static Vec3 rodTipAnchor(Minecraft mc, Player player, float pt) {
        int arm = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
        if (!(player.getMainHandItem().getItem() instanceof com.riverfishing.item.RodItem)) {
            arm = -arm; // the rod is in the off hand
        }
        float swingProgress = player.getAttackAnim(pt);
        float swing = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);

        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            // §zoom-anchor: the FOV the frame is ACTUALLY drawn at, not the one in the settings menu.
            // The camera knows the answer; ask it.
            //? if <26.2 {
            double fov = mc.gameRenderer.getMainCamera().getFov();
            //?} else {
            /*double fov = mc.gameRenderer.mainCamera().getFov();
            *///?}
            double fovScale = 960.0 / fov;
            float px, py;
            if (RodChain.tipNdcFresh()) {
                // §rod-tip-3d: the tip the renderer actually drew, projected to screen with the HAND
                // pass's fov. The near plane here is built from the WORLD fov, so the coordinate is
                // rescaled by the tangent ratio before it lands on it — after that both projections
                // agree on the pixel. getPointOnPlane's first argument scales the LEFT vector, so
                // screen-right has to be negated.
                float ratio = RodChain.HAND_FOV <= 0f ? 1f
                        : (float) (Math.tan(Math.toRadians(RodChain.HAND_FOV) / 2.0)
                                / Math.tan(Math.toRadians(fov) / 2.0));
                px = -RodChain.TIP_NDC[0] * ratio + TIP3D_OFFSET[0];
                py = RodChain.TIP_NDC[1] * ratio + TIP3D_OFFSET[1];
            } else {
                // Sprite blanks: one 16-unit icon for every rod, so the tip is a constant found in
                // game, nudged per bend bucket (/rfrod tip) and per lean (§fight-course).
                int bend = heldBend(player);
                float[] lean = ClientLineState.ownLean();
                px = arm * (0.525f + TIP_BEND_OFFSET[bend][0]) + lean[0] * RodHandTransform.COURSE_TIP_X;
                py = -0.1f + TIP_BEND_OFFSET[bend][1] + lean[1] * RodHandTransform.COURSE_TIP_Y;
            }
            // §26.1: getNearPlane now takes the fov in degrees instead of reading it itself.
            //? if <26.2 {
            Vec3 v = mc.gameRenderer.getMainCamera().getNearPlane((float) fov)
            //?} else {
            /*Vec3 v = mc.gameRenderer.mainCamera().getNearPlane((float) fov)
            *///?}
                    .getPointOnPlane(px, py)
                    .scale(fovScale)
                    .yRot(swing * 0.5f)
                    .xRot(-swing * 0.7f);
            return new Vec3(
                    Mth.lerp(pt, player.xo, player.getX()) + v.x,
                    Mth.lerp(pt, player.yo, player.getY()) + v.y + player.getEyeHeight(),
                    Mth.lerp(pt, player.zo, player.getZ()) + v.z);
        }

        // §rod-tip-3d third person: the local player's tip was captured in view space while the rod
        // drew — rotate it back to world with the camera quaternion and the line starts ON the bent
        // 3D tip instead of at the body-model shoulder guess below.
        if (player == mc.player && RodChain.tipViewFresh()) {
            org.joml.Vector3f w = RodChain.cameraRot(mc).transform(new org.joml.Vector3f(
                    RodChain.TIP_VIEW[0], RodChain.TIP_VIEW[1], RodChain.TIP_VIEW[2]));
            Vec3 cp = RodChain.cameraPos(mc);
            return new Vec3(cp.x + w.x(), cp.y + w.y(), cp.z + w.z());
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
        line(vc, m, nrm, a, b, r, g, bl, 255, 2.0f);
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f nrm, Vec3 a, Vec3 b,
                             int r, int g, int bl, int alpha, float width) {
        float dx = (float) (b.x - a.x), dy = (float) (b.y - a.y), dz = (float) (b.z - a.z);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 1e-4f) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha).setNormal(dx, dy, dz).setLineWidth(width);
        vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha).setNormal(dx, dy, dz).setLineWidth(width);
    }
}
