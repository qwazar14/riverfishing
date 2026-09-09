package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riverfishing.fishing.Rope;
import com.riverfishing.item.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * §rope prototype: a {@link Rope} hung off the held rod's real tip, the world as its medium. Client
 * only, no fish, no server — the point is to find out whether the mouse is a casting arm. Toggled
 * with {@code /rfrod rope on|off}. Inputs, none of them timed: mouse = arm, RIGHT held = open line
 * hand (line runs out only as fast as the rope pulls it), LEFT click = one strip.
 */
public final class FlyLineClient {
    private FlyLineClient() {}

    public static boolean ENABLED = false;
    /** Points in the chain; /rfrod rope segments <n> rebuilds the rope. */
    public static int SEGMENTS = Rope.DEFAULT_N;
    /** The rod's load off the rope, smoothed per tick — what the blank bends to (§rod-load). */
    private static float load;
    private static final double STRIP_M = 0.6;
    private static Rope rope;
    private static Vec3 tipPrev, tipNow;
    private static boolean attackWas;

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
        // ponytail: still water everywhere — the river flow field replaces this one method.
        @Override public void flow(double x, double y, double z, double[] out) { out[0] = out[1] = out[2] = 0; }
    };

    public static boolean active() { return ENABLED && rope != null; }

    /** 0..1 blank load from the rope's pull on the tip; 0 when the rope is not out. */
    public static float load() { return active() ? load : 0f; }

    private static boolean holdsRod(Minecraft mc) {
        return mc.player != null && (mc.player.getMainHandItem().getItem() instanceof RodItem
                || mc.player.getOffhandItem().getItem() instanceof RodItem);
    }

    /** Client tick: one physics step from the tip the renderer drew last frame. */
    public static void tick(Minecraft mc) {
        if (!ENABLED || mc.level == null || !holdsRod(mc) || mc.screen != null) {
            rope = null;
            attackWas = false;
            load = 0f;
            return;
        }
        if (tipNow == null) return;   // no frame drawn yet
        if (rope == null || rope.n != SEGMENTS) rope = new Rope(SEGMENTS, tipNow.x, tipNow.y, tipNow.z);

        boolean handOpen = mc.options.keyUse.isDown();
        boolean attack = mc.options.keyAttack.isDown();
        if (attack && !attackWas) rope.strip(STRIP_M);
        attackWas = attack;
        // Drained: neither button reaches vanilla (no block hit, no rod cast) while the rope is out.
        while (mc.options.keyAttack.consumeClick()) { }
        while (mc.options.keyUse.consumeClick()) { }

        rope.step(0.05, tipNow.x, tipNow.y, tipNow.z, handOpen, WORLD);
        load += ((float) rope.load01() - load) * 0.5f;
    }

    /** World pass: called by {@link LineRenderer#render} every frame, before its own early-outs. */
    static void render(PoseStack pose, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null || mc.level == null || !holdsRod(mc)) return;
        Vec3 tip = LineRenderer.rodTipAnchor(mc, mc.player, pt);
        tipPrev = tipNow;
        tipNow = tip;
        if (rope == null) return;

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        var m = pose.last().pose();
        var nrm = pose.last().normal();
        int leaderFrom = Math.max(1, rope.n - 1 - (int) Math.ceil(rope.leaderLen / rope.segLen()));
        Vec3 prev = tip;
        for (int i = 1; i < rope.n; i++) {
            Vec3 p = new Vec3(Mth.lerp(pt, rope.px[i], rope.x[i]),
                    Mth.lerp(pt, rope.py[i], rope.y[i]),
                    Mth.lerp(pt, rope.pz[i], rope.z[i]));
            // Fly line pale and heavy, leader near-invisible, the fly itself a dark dot.
            boolean leader = i >= leaderFrom;
            LineRenderer.line(vc, m, nrm, prev, p, leader ? 90 : 235, leader ? 90 : 225, leader ? 90 : 170,
                    leader ? 120 : 255);
            prev = p;
        }
        Vec3 fly = prev;
        LineRenderer.line(vc, m, nrm, fly.add(0, 0.03, 0), fly.add(0, -0.03, 0), 30, 30, 30, 255);
        LineRenderer.line(vc, m, nrm, fly.add(0.03, 0, 0), fly.add(-0.03, 0, 0), 30, 30, 30, 255);
        buffers.endBatch();
        pose.popPose();
    }
}
