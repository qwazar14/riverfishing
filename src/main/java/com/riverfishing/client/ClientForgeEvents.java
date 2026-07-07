package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riverfishing.RiverFishing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Client forge-bus events: float-timing HUD (#5) and the in-hand fishing-line renderer (§immersion). */
@Mod.EventBusSubscriber(modid = RiverFishing.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLineState.clear(); // never carry a line into another world
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (FloatTimingClient.isActive()) {
            FloatTimingClient.render(event.getGuiGraphics(),
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    event.getPartialTick());
        }
        renderCastPower(event, mc);
    }

    /** Cast power bar (§cast-minigame): shown while charging a cast (holding RMB with no line out). */
    private static void renderCastPower(RenderGuiEvent.Post event, Minecraft mc) {
        Player player = mc.player;
        if (player == null || !player.isUsingItem()) return;
        if (!(player.getUseItem().getItem() instanceof com.riverfishing.item.RodItem rodItem)) return;
        // §spin-charge (2.3): lure rods now charge-and-cast too, so they show the bar — but only while
        // charging. Once a line is out, holding is a RETRIEVE, not a charge, so hide it (next line).
        if (ClientLineState.active()) return;

        int used = player.getUseItem().getUseDuration() - player.getUseItemRemainingTicks();
        float power = com.riverfishing.item.RodItem.castPower(used);

        // §cast-bar-cut: mirror the server's rod-test lower bound — an under-loaded blank can't throw
        // as far, so the far end of the bar is a dead zone the fill can't enter. Computed client-side
        // from the installed rig's mass vs the rod's minimum test (both pure, NBT-readable here).
        float usable = 1.0f;
        if (player.getUseItem().getItem() instanceof com.riverfishing.item.RodItem) {
            var rodType = rodItem.rodType();
            var rig = com.riverfishing.item.RodData.get(player.getUseItem(), com.riverfishing.component.ComponentSlot.RIG);
            if (rig.getItem() instanceof com.riverfishing.item.RigItem ri
                    && rodType.castWeightMin() > 0
                    && ri.rigType().massGrams() < rodType.castWeightMin()) {
                usable = 0.55f; // matches maxRange *= 0.55 server-side
            }
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = 102, h = 7;
        int x = (sw - w) / 2, y = sh - 64;
        var g = event.getGuiGraphics();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xCC2A1E12);           // wood frame
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF5C4A34);
        g.fill(x, y, x + w, y + h, 0xFF1E1610);                            // track
        int cut = (int) (usable * w);
        // the unreachable far zone (under-loaded rod): a dim red dead band
        if (usable < 1.0f) {
            g.fill(x + cut, y, x + w, y + h, 0x55B03030);
            g.fill(x + cut, y, x + cut + 1, y + h, 0xFFE05A4A);           // hard cut-off marker
        }
        int fill = (int) (Math.min(power, usable) * w);
        int color = power < 0.5f ? 0xFF7CB342 : (power < 0.85f ? 0xFFF4C542 : 0xFFE05A4A);
        g.fill(x, y, x + fill, y + h, color);
        // tick marks at 50% / 85% so the timing is readable (only within the usable zone)
        if (0.5f < usable) g.fill(x + w / 2, y, x + w / 2 + 1, y + h, 0x66FFFFFF);
        if (0.85f < usable) g.fill(x + (int) (w * 0.85f), y, x + (int) (w * 0.85f) + 1, y + h, 0x66FFFFFF);
    }

    /**
     * Renders every visible fishing line (§line-multiplayer): from each angler's rod tip out to
     * where their cast landed, sagging under gravity, and visually reeling in toward the bank as the
     * fight progresses (§immersion). Our own line and other players' lines use the same math — the
     * anchor hangs off THAT player's head yaw and main hand.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientLineState.lines().isEmpty()) return;

        float pt = event.getPartialTick();
        float frameSeconds = mc.getDeltaFrameTime() / 20f;
        long now = mc.level.getGameTime();

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        Matrix4f m = pose.last().pose();
        Matrix3f nrm = pose.last().normal();

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

        if (drew) {
            buffers.endBatch(RenderType.lines());
        }
        pose.popPose();
    }

    private static void renderLine(Minecraft mc, VertexConsumer vc, Matrix4f m, Matrix3f nrm,
                                   Player player, ClientLineState.Line state, float pt) {
        // Rod-tip anchor: exact vanilla FishingHookRenderer math (§vanilla-line).
        Vec3 tip = rodTipAnchor(mc, player, pt);

        // Line end: the cast target riding the waves, pulled toward the angler as reel-in progress
        // rises. The float and the line END move together — like the vanilla bobber on water.
        float bobT = mc.level.getGameTime() + pt;
        double bob;
        if (!state.bobber) {
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
        if (state.bobber) {
            Vec3 fb = end;
            line(vc, m, nrm, fb.add(0, 0.24, 0), fb.add(0, 0.10, 0), 224, 58, 48);   // antenna
            line(vc, m, nrm, fb.add(0.018, 0.24, 0), fb.add(0.018, 0.10, 0), 224, 58, 48);
            line(vc, m, nrm, fb.add(0, 0.10, 0), fb.add(0, -0.06, 0), 240, 238, 228); // body
            line(vc, m, nrm, fb.add(0.018, 0.10, 0), fb.add(0.018, -0.06, 0), 240, 238, 228);
            line(vc, m, nrm, fb.add(-0.018, 0.10, 0), fb.add(-0.018, -0.06, 0), 240, 238, 228);
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
            double fovScale = 960.0 / mc.options.fov().get();
            Vec3 v = mc.gameRenderer.getMainCamera().getNearPlane()
                    .getPointOnPlane(arm * 0.525f, -0.1f)
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
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, 255).normal(nrm, dx, dy, dz).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, 255).normal(nrm, dx, dy, dz).endVertex();
    }
}
