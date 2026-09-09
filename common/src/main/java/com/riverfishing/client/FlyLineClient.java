package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riverfishing.fishing.Rope;
import com.riverfishing.item.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * §rope prototype: a {@link Rope} hung off the held rod's real tip, the world as its medium. Client
 * only, no fish, no server — the point is to find out whether the mouse is a casting arm. Toggled
 * with {@code /rfrod rope on|off}. Inputs, none of them timed: mouse = arm, LEFT held = open line
 * hand (line runs out only as fast as the rope pulls it), RIGHT click = one strip.
 */
public final class FlyLineClient {
    private FlyLineClient() {}

    public static boolean ENABLED = true;
    /** Points in the chain; /rfrod rope segments <n> rebuilds the rope. */
    public static int SEGMENTS = Rope.DEFAULT_N;
    /** The rod's load off the rope, smoothed per tick — what the blank bends to (§rod-load). */
    private static float load;
    private static final double STRIP_M = 0.6;
    private static Rope rope;
    private static Vec3 tipPrev, tipNow;
    /** The tip the physics hung from on the last tick — the picture must start from THIS one. */
    private static Vec3 tipUsed;
    /** A fly rod is 2.7 m of reach: the eye-to-tip line, at the rod's real length, is where the physics tip goes. */
    private static final double ROD_REACH = 2.7;
    private static boolean stripWas, feedWas;

    private static final Rope.Medium WORLD = new Rope.Medium() {
        @Override public double surfaceY(double x, double y, double z) {
            var level = Minecraft.getInstance().level;
            BlockPos pos = BlockPos.containing(x, y, z);
            var fs = level.getFluidState(pos);
            if (fs.isEmpty()) return Double.NaN;
            // Vanilla water tops out at 8/9 of the block; a full column keeps the next block's height.
            BlockPos up = pos;
            while (!level.getFluidState(up.above()).isEmpty()) up = up.above();
            return up.getY() + level.getFluidState(up).getOwnHeight();
        }
        @Override public boolean solid(double x, double y, double z) {
            var level = Minecraft.getInstance().level;
            BlockPos pos = BlockPos.containing(x, y, z);
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }
        @Override public void flow(double x, double y, double z, double[] out) {
            com.riverfishing.fishing.Flow.at(Minecraft.getInstance().level, x, y, z, out);
        }
    };

    public static boolean active() { return ENABLED && rope != null; }

    /** 0..1 blank load from the rope's pull on the tip; 0 when the rope is not out. */
    public static float load() { return active() ? load : 0f; }

    /** Running line stripped into the hand, metres — the loop between reel and stripping guide. */
    private static double slack;
    private static final double REEL_PAY = 0.08;   // off the reel, against its click: a quarter of the shoot

    /**
     * The rope is out on a fly rod carrying a fly line, or on any rod with /rfrod rope on. A fly
     * rod is fished this way and no other: RopeInputMixin keeps vanilla's use off it.
     */
    private static boolean holdsRod(Minecraft mc) {
        if (mc.player == null) return false;
        var main = mc.player.getMainHandItem();
        var stack = main.getItem() instanceof RodItem ? main : mc.player.getOffhandItem();
        if (!(stack.getItem() instanceof RodItem rod)) return false;
        // Only ever a fly rod; /rfrod rope off is the way to fish it without the rope.
        return ENABLED && rod.rodType() == com.riverfishing.component.RodType.FLY
                && com.riverfishing.item.RodData.get(stack, com.riverfishing.component.ComponentSlot.LINE)
                        .getItem() instanceof com.riverfishing.item.LineItem li
                && li.lineType() == com.riverfishing.component.LineType.FLY;
    }

    /** §one-rope: the hand pass tells the rope where the drawn tip really is, so physics and picture agree. */
    static void handTip(Vec3 world, Vec3 eye) {
        // The hand pass tip sits well under a block from the eye (a near-plane construct), so a swing
        // moved it a third as far as a real tip and the line never loaded. Same direction, real reach.
        Vec3 d = world.subtract(eye);
        double len = d.length();
        tipNow = len < 1e-4 ? world : eye.add(d.scale(ROD_REACH / len));
    }

    /** The rope this frame, world space, index 0 = the tip; null when no rope is out. */
    public static Vec3[] renderPoints(float pt) {
        if (rope == null || tipNow == null) return null;
        Vec3[] pts = new Vec3[rope.n];
        pts[0] = tipUsed != null ? tipUsed : tipNow;
        for (int i = 1; i < rope.n; i++) {
            pts[i] = new Vec3(Mth.lerp(pt, rope.px[i], rope.x[i]),
                    Mth.lerp(pt, rope.py[i], rope.y[i]),
                    Mth.lerp(pt, rope.pz[i], rope.z[i]));
        }
        return pts;
    }

    /** First point of the leader — the last metres that do not float and draw near-invisible. */
    public static int leaderFrom() {
        return rope == null ? 0 : Math.max(1, rope.n - 1 - (int) Math.ceil(rope.leaderLen / rope.segLen()));
    }

    /** Sag of the reel-to-guide loop for the hand pass, model units (16 = one block); 0 = no loop. */
    public static float handLoopUnits(String rodKey) {
        if (!active() || !"fly".equals(rodKey)) return 0f;
        return (float) Math.min(14.0, slack * 8.0);
    }

    /** Client tick: one physics step from the tip the renderer drew last frame. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || !holdsRod(mc) || mc.screen != null) {
            rope = null;
            stripWas = false;
            feedWas = false;
            load = 0f;
            slack = 0;
            return;
        }
        if (tipNow == null) return;   // no frame drawn yet
        if (rope == null || rope.n != SEGMENTS) rope = new Rope(SEGMENTS, tipNow.x, tipNow.y, tipNow.z);

        // LEFT held = open line hand, RIGHT click = one strip. RopeInputMixin keeps both buttons
        // from vanilla (its swing jerked the tip, and so the whole line, on every click).
        boolean handOpen = mc.options.keyAttack.isDown();
        // A LEFT press feeds out exactly what a RIGHT press takes in; holding it keeps the hand open
        // so a loaded line can shoot on top of that.
        if (handOpen && !feedWas) {
            rope.feed(STRIP_M);
            slack = Math.max(0, slack - STRIP_M);
        }
        feedWas = handOpen;
        boolean strip = mc.options.keyUse.isDown() && !mc.player.isShiftKeyDown();   // shift+use opens the rod
        if (strip && !stripWas) {
            double before = rope.length();
            rope.strip(STRIP_M);
            slack += before - rope.length();   // what came in is in the hand now
        }
        stripWas = strip;
        while (mc.options.keyAttack.consumeClick()) { }
        while (mc.options.keyUse.consumeClick()) { }

        // The shoot spends the hand loop first; past that the line comes off the reel, slowly.
        rope.payPerStep = slack > 0 ? Rope.MAX_PAY_PER_STEP : REEL_PAY;
        tipUsed = tipNow;
        rope.step(0.05, tipNow.x, tipNow.y, tipNow.z, handOpen, WORLD);
        slack = Math.max(0, slack - rope.paidOut);
        if (Double.isNaN(rope.x[rope.n - 1] + rope.y[rope.n - 1] + rope.z[rope.n - 1])) {
            rope = new Rope(SEGMENTS, tipNow.x, tipNow.y, tipNow.z);   // blew up: start again, not vanish
        }
        load += ((float) rope.load01() - load) * 0.5f;
    }

    /** World pass: called by {@link LineRenderer#render} every frame, before its own early-outs. */
    static void render(PoseStack pose, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !holdsRod(mc)) return;
        Vec3 tip = LineRenderer.rodTipAnchor(mc, mc.player, pt);
        // §one-rope: in first person the hand pass owns the tip (handTip); two anchors fighting over
        // tipNow put a step under the tip — the rope hung from one and was drawn from the other.
        if (!(mc.options.getCameraType().isFirstPerson() && RodItemRenderer.handLineFresh())) {
            tipPrev = tipNow;
            tipNow = tip;
        }
        if (rope == null) return;
        // §one-rope: in first person the hand pass draws the whole line off the tip it just drew;
        // this pass only draws it for third person (and while the hand pass is not yet running).
        if (mc.options.getCameraType().isFirstPerson() && RodItemRenderer.handLineFresh()) return;

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float[] style = RodRenderTypes.strandStyle(com.riverfishing.component.LineType.FLY, 1.0);
        VertexConsumer vc = buffers.getBuffer(RodRenderTypes.lineStrand(style[4]));
        var m = pose.last().pose();
        var nrm = pose.last().normal();
        int leaderFrom = leaderFrom();
        Vec3[] pts = renderPoints(pt);
        Vec3 prev = tip;
        for (int i = 1; i < rope.n; i++) {
            Vec3 p = pts[i];
            // Fly line in its strand colour, leader near-invisible, the fly itself a dark dot.
            boolean leader = i >= leaderFrom;
            LineRenderer.line(vc, m, nrm, prev, p, leader ? 90 : (int) style[0], leader ? 90 : (int) style[1],
                    leader ? 90 : (int) style[2], leader ? 120 : (int) style[3]);
            prev = p;
        }
        Vec3 fly = prev;
        LineRenderer.line(vc, m, nrm, fly.add(0, 0.03, 0), fly.add(0, -0.03, 0), 30, 30, 30, 255);
        LineRenderer.line(vc, m, nrm, fly.add(0.03, 0, 0), fly.add(-0.03, 0, 0), 30, 30, 30, 255);
        buffers.endBatch();
        pose.popPose();
    }
}
