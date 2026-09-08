package com.riverfishing.client;

import com.riverfishing.component.RodType;
import com.riverfishing.fishing.FlyCast;
import com.riverfishing.item.RodItem;
import com.riverfishing.network.FlyBeatPacket;
import com.riverfishing.network.FlyCastPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * §fly-2: the fly rod's HUD, three states on one packet.
 *
 * <p><b>The cast</b> (mode 0): the cast bar's frame with the needle sweeping from the backcast stop (left)
 * to the forward stop (right) — the rod false-casting on its own while use is held. The right end is green:
 * release there. A whoosh on every stop, the metres on the plaque growing two at a time, a left-click on
 * a stop hauling two more with a zip and a punch of the whole gauge. Nothing here can be failed by
 * clicking; the release is the only judgement.
 *
 * <p><b>The drift</b> (mode 2): a line under the crosshair that says what the fly is doing — dead drift,
 * dragging (mend!), the line straight below you — and, when the fly is over a rising fish, that the take
 * is coming. The controls sit under it in small print until they have been used.
 *
 * <p><b>The jig</b> (mode 1, §ice-rhythm) keeps its two-stop rhythm on the same frame.
 */
public final class FlyCastClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static int maxBeats;
    private static boolean openLoop;
    private static boolean attackWas;
    private static int lastEnd = -1;
    private static int mode;   // 0 the fly cast, 1 the jig, 2 the drift
    private static long hitNanos = -1L, missNanos = -1L;
    private static int lastStroke = -1;
    private static int mendsSeen;

    private static final net.minecraft.resources.ResourceLocation BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private FlyCastClient() {}

    public static void accept(FlyCastPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean hit = p.active && p.mode != 2 && p.beats > beats && mode == p.mode;
        boolean miss = p.active && p.mode == 1 && p.openLoop && (!openLoop || p.beats < beats);
        if (!active && p.active) { hitNanos = -1L; missNanos = -1L; lastStroke = -1; }
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        maxBeats = p.maxBeats;
        openLoop = p.openLoop;
        lastEnd = p.lastEnd;
        mode = p.mode;
        if (hit) {
            hitNanos = System.nanoTime();
            if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, mode == 0 ? 1.4f : 0.9f + 0.09f * Math.min(beats, 12));
        } else if (miss) {
            missNanos = System.nanoTime();
            if (mc.player != null) mc.player.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.5f, 0.7f);
        }
    }

    /** The cast or the jig gauge is up (the drift's line is not a gauge: the charge bar may not yield to it). */
    public static boolean isActive() {
        return active && mode != 2;
    }

    /** A fly rod is in the hand — the strike bar labels itself for it. */
    public static boolean flyHeld() {
        return !heldFlyRod(Minecraft.getInstance()).isEmpty();
    }

    /** A fly line is on the water and the drift status is showing. */
    public static boolean isDrifting() {
        return active && mode == 2;
    }

    private static float marker(float t) {
        if (period <= 0) return 0.5f;
        float phase = (t % period) / period;
        if (phase < 0) phase += 1f;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    private static ItemStack heldFlyRod(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY) return main;
        ItemStack off = mc.player.getOffhandItem();
        return off.getItem() instanceof RodItem ri2 && ri2.rodType() == RodType.FLY ? off : ItemStack.EMPTY;
    }

    /** §jig-2: the rod whose rhythm this is — the fly rod on the cast, the winter rod on the jig. */
    private static ItemStack heldRhythmRod(Minecraft mc) {
        if (mode == 1) {
            if (mc.player == null) return ItemStack.EMPTY;
            ItemStack main = mc.player.getMainHandItem();
            if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.WINTER) return main;
            ItemStack off = mc.player.getOffhandItem();
            return off.getItem() instanceof RodItem ri2 && ri2.rodType() == RodType.WINTER ? off : ItemStack.EMPTY;
        }
        return heldFlyRod(mc);
    }

    /** Client tick: the attack button's down-edge is a haul while casting and a mend while drifting. */
    public static void tick(Minecraft mc) {
        if (!active) {
            attackWas = false;
            return;
        }
        if (heldRhythmRod(mc).isEmpty() || (mode != 2 && !mc.player.isUsingItem())) {
            active = false;   // the hold ended without a release (a slot switch): the gauge comes down here
            return;
        }
        boolean down = mc.options.keyAttack.isDown();
        if (down && !attackWas) {
            ModNetwork.toServer(new FlyBeatPacket());
            if (mode == 2) mendsSeen++;
        }
        attackWas = down;
        while (mc.options.keyAttack.consumeClick()) { /* drained: no arm swing, no block hit */ }
        // the whoosh: the rod reaching a stop, on the client's own clock (the jig ticks instead of whooshing)
        if (mode != 2 && mc.level != null) {
            int st = (int) Math.floorDiv(mc.level.getGameTime() - startTick, (long) Math.max(1, period / 2));
            if (st != lastStroke) {
                if (lastStroke >= 0) {
                    if (mode == 0) mc.player.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.45f, st % 2 == 0 ? 1.1f : 1.35f);
                }
                lastStroke = st;
            }
        }
    }

    private static float since(long nanos) {
        return nanos < 0 ? 99f : (System.nanoTime() - nanos) / 1.0e9f;
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    public static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        if (mode == 2) { renderDrift(g, mc, screenW, screenH); return; }
        ItemStack rod = heldRhythmRod(mc);
        if (rod.isEmpty()) return;
        float t = (mc.level.getGameTime() - startTick) + partialTick;

        float dh = since(hitNanos), dm = since(missNanos);
        float punch = (float) Math.exp(-dh * 6.0);
        float crash = (float) Math.exp(-dm * 5.0);
        double metres = mode == 0 ? FlyCast.lineOut(mc.level.getGameTime() - startTick, beats, maxBeats) : 0;
        float combo = mode == 1 ? (maxBeats > 0 ? Mth.clamp(beats / (float) maxBeats, 0f, 1f) : 0f)
                : (float) Mth.clamp((metres - FlyCast.PICKUP) / Math.max(1.0, maxBeats - FlyCast.PICKUP), 0.0, 1.0);
        float scale = (1f + 0.05f * Math.min(beats, 8)) * (1f + 0.35f * punch) * (1f - 0.12f * crash);
        float shake = punch * 2.5f * Mth.sin(dh * 90f) + crash * 6f * Mth.sin(dm * 70f);
        int stopRgb = lerpRgb(0x5FA84E, 0xFFF4C0, combo * 0.9f);
        int haloRgb = lerpRgb(0x5FA84E, 0xFFD34A, combo);

        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int cx = x + FW / 2, cy = y + FH / 2;

        g.pose().pushPose();
        g.pose().translate(cx + shake, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0);

        int haloA = (int) (40 + 120 * combo + 80 * punch);
        int spread = 3 + (int) (4 * combo + 10 * punch);
        g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, (Math.min(255, haloA) << 24) | haloRgb);
        if (crash > 0.05f) g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, ((int) (140 * crash) << 24) | 0xE05A4A);

        int tx = x + 4, ty = y + 4;
        g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 128, 48);

        int zw = (int) (zoneHalf * TW);
        int stopA = (int) (200 + 55 * punch);
        if (mode == 1) {   // §jig-2: both stops take an accent — the lift and the drop
            g.fill(tx, ty, tx + zw, ty + TH, (stopA << 24) | stopRgb);
            g.fill(tx + TW - zw, ty, tx + TW, ty + TH, (stopA << 24) | stopRgb);
            g.drawCenteredString(mc.font, Component.literal("▲"), tx + zw / 2, ty, 0xFF1C1814);
            g.drawCenteredString(mc.font, Component.literal("▼"), tx + TW - zw / 2, ty, 0xFF1C1814);
        } else {
            // the forward stop is the green; the open band before it amber; the backcast end a dim mark
            int ow = (int) (FlyCast.OPEN_HALF * TW);
            g.fill(tx + TW - ow, ty, tx + TW - zw, ty + TH, 0xB0C8862E);
            g.fill(tx + TW - zw, ty, tx + TW, ty + TH, (stopA << 24) | stopRgb);
            g.fill(tx, ty, tx + zw / 2, ty + TH, 0x60231A10);
        }
        if (punch > 0.05f) g.fill(tx, ty, tx + TW, ty + TH, ((int) (110 * punch) << 24) | 0xFFFFFF);

        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, openLoop ? 0xFFE05A4A : 0xFFFFE8A8);

        if (mode == 1) {
            int px0 = x + (FW - maxBeats * 6) / 2, py0 = y - 7;
            for (int i = 0; i < maxBeats; i++) {
                int px = px0 + i * 6;
                boolean lit = i < beats;
                int grow = lit && i == beats - 1 ? (int) (2 * punch) : 0;
                int c = lit ? (openLoop ? 0xFFE05A4A : lerpRgb(0xFFC83C, 0xFFFFFF, punch) | 0xFF000000) : 0x60231A10;
                g.fill(px - grow, py0 - grow, px + 4 + grow, py0 + 4 + grow, c);
            }
        }
        g.pose().popPose();

        int px = x + (FW - 48) / 2, py = y - 30;
        if (mode == 0) {
            // the metres on the plaque — the line in the air, growing on every stop and every haul
            String label = String.format(java.util.Locale.ROOT, "%.0f m", metres);
            int lw = mc.font.width(label);
            g.blit(BAR, px, py, 48, 16, 0f, 32f, 48, 16, 128, 48);
            g.drawString(mc.font, label, px + (48 - lw) / 2, py + 4, 0xFF3A2A18, false);
            String cap = String.format(java.util.Locale.ROOT, "/ %d", maxBeats);
            g.drawString(mc.font, cap, px + 50, py + 4, 0xFFB08D3C, true);
            boolean full = metres >= maxBeats - 1e-6;
            g.drawCenteredString(mc.font, Component.translatable(full ? "gui.riverfishing.fly_full" : "gui.riverfishing.fly_hint"),
                    screenW / 2, y + FH + 8, full ? 0xFFB8E8A0 : 0xFFB8AE9A);
            if (!full) g.drawCenteredString(mc.font, Component.translatable("gui.riverfishing.fly_haul"), screenW / 2, y + FH + 18, 0xFF8E8676);
            if (beats > 0) {
                String haul = Component.translatable("gui.riverfishing.fly_hauls", beats).getString();
                float cs = 1f + 0.4f * punch;
                g.pose().pushPose();
                g.pose().translate(screenW / 2f, py - 8, 0);
                g.pose().scale(cs, cs, 1f);
                g.drawCenteredString(mc.font, haul, 0, -4, lerpRgb(0xF0E6CD, 0xFFE070, punch) | 0xFF000000);
                g.pose().popPose();
            }
        } else {
            if (beats == 0 && hitNanos < 0) {
                g.drawCenteredString(mc.font, Component.translatable("gui.riverfishing.jig_hint"), screenW / 2, y + FH + 8, 0xFFB8AE9A);
            }
            g.drawCenteredString(mc.font, Component.translatable("gui.riverfishing.jig_beats", beats),
                    screenW / 2, py - 24, openLoop ? 0xFFE05A4A : 0xFFF0E6CD);
        }
    }

    /** The drift: one line that says what the fly is doing, the controls under it in small print. */
    private static void renderDrift(GuiGraphics g, Minecraft mc, int screenW, int screenH) {
        if (heldFlyRod(mc).isEmpty()) return;
        long t = mc.level.getGameTime();
        String key;
        int col;
        if (openLoop) {            // on a rising fish: the take is coming
            key = "hud.riverfishing.fly_on_fish";
            col = ((t / 6) % 2 == 0) ? 0xFF9CF08C : 0xFF5FD070;
        } else if (beats == 2) {
            key = "hud.riverfishing.fly_straight"; col = 0xFFB8AE9A;
        } else if (beats == 1) {
            key = "hud.riverfishing.fly_drag"; col = 0xFFFFC850;
        } else {
            key = "hud.riverfishing.fly_dead_drift"; col = 0xFF7CE07C;
        }
        String text = Component.translatable(key).getString();
        int cx = screenW / 2, y = screenH / 2 + 14;
        int w = mc.font.width(text);
        g.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 11, 0x66000000);
        g.drawCenteredString(mc.font, text, cx, y, col);
        String m = maxBeats + " m";
        g.drawString(mc.font, m, cx + w / 2 + 8, y, 0xFFB08D3C, true);
        if (mendsSeen < 3 || beats == 2) {
            g.drawCenteredString(mc.font, Component.translatable("hud.riverfishing.fly_controls"), cx, y + 13, 0xFF8E8676);
        }
    }
}
