package com.riverfishing.client;

import com.riverfishing.component.RodType;
import com.riverfishing.fishing.FlyCast;
import com.riverfishing.fishing.FlyDrift;
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
 * §fly-3: the fly rod's screen, three things on one packet.
 *
 * <p><b>The cast</b> (mode 0): the rod swings on its own and the needle is the rod. The right end is the
 * forward stop: the green band is the clean turnover, the amber either side of it still flies. The metres
 * on the plaque climb by three every full swing. Nothing here is clicked — letting go is the whole skill.
 *
 * <p><b>The line</b> (mode 2): one sentence under the crosshair that says what the fly is doing, and, when
 * a fish comes up, which button to press. Never a number.
 *
 * <p><b>The jig</b> (mode 1, §ice-rhythm) keeps its two stops on the same frame.
 */
public final class FlyCastClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static int maxBeats;
    private static boolean onRise;
    private static boolean attackWas;
    private static int mode;   // 0 the cast, 1 the jig, 2 the line
    private static long hitNanos = -1L;
    private static int lastStroke = -1;
    private static int seenState = -1;
    private static long stateNanos = -1L;

    private static final net.minecraft.resources.ResourceLocation BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private FlyCastClient() {}

    public static void accept(FlyCastPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean hit = p.active && p.mode == 1 && p.beats > beats && mode == p.mode;
        if (!active && p.active) { hitNanos = -1L; lastStroke = -1; }
        if (p.mode == 2 && p.beats != seenState) {
            seenState = p.beats;
            stateNanos = System.nanoTime();
            if (mc.player != null && (p.beats == FlyDrift.HUD_STRIKE_LMB || p.beats == FlyDrift.HUD_STRIKE_RMB)) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.5f);
            }
        }
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        maxBeats = p.maxBeats;
        onRise = p.openLoop;
        mode = p.mode;
        if (hit) {
            hitNanos = System.nanoTime();
            if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.9f + 0.09f * Math.min(beats, 12));
        }
    }

    /** The cast or the jig gauge is up — the charge bar yields to it; the status line is not a gauge. */
    public static boolean isActive() {
        return active && mode != 2;
    }

    /** A fly rod is in the hand. */
    public static boolean flyHeld() {
        return !heldFlyRod(Minecraft.getInstance()).isEmpty();
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

    /** The rod this rhythm belongs to: the fly rod on a cast, the winter rod on a jig. */
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

    /**
     * Client tick. The left button is a mend while the line drifts and the lift when a fish has taken; on
     * the jig it is the accent. During a cast it does nothing at all, so nothing is sent.
     */
    public static void tick(Minecraft mc) {
        if (!active) {
            attackWas = false;
            return;
        }
        if (heldRhythmRod(mc).isEmpty() || (mode != 2 && !mc.player.isUsingItem())) {
            active = false;   // the hold ended without a release (a slot switch): the gauge comes down
            return;
        }
        boolean down = mc.options.keyAttack.isDown();
        if (down && !attackWas && mode != 0) ModNetwork.toServer(new FlyBeatPacket());
        attackWas = down;
        while (mc.options.keyAttack.consumeClick()) { /* drained: no arm swing, no block hit */ }
        // the swish at each end of the swing, on the client's own clock
        if (mode != 2 && mc.level != null) {
            int st = (int) Math.floorDiv(mc.level.getGameTime() - startTick, (long) Math.max(1, period / 2));
            if (st != lastStroke) {
                if (lastStroke >= 0 && mode == 0) {
                    mc.player.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.4f, st % 2 == 0 ? 1.05f : 1.4f);
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
        if (mode == 2) { renderLine(g, mc, screenW, screenH); return; }
        ItemStack rod = heldRhythmRod(mc);
        if (rod.isEmpty()) return;
        float t = (mc.level.getGameTime() - startTick) + partialTick;

        float punch = (float) Math.exp(-since(hitNanos) * 6.0);
        double metres = mode == 0 ? FlyCast.lineOut(mc.level.getGameTime() - startTick, maxBeats) : 0;
        float fill = mode == 1 ? (maxBeats > 0 ? Mth.clamp(beats / (float) maxBeats, 0f, 1f) : 0f)
                : (float) Mth.clamp((metres - FlyCast.BASE_METERS) / Math.max(1.0, maxBeats - FlyCast.BASE_METERS), 0.0, 1.0);
        float scale = (1f + 0.05f * Math.min(beats, 8)) * (1f + 0.3f * punch);
        int stopRgb = lerpRgb(0x5FA84E, 0xFFF4C0, fill * 0.9f);
        int haloRgb = lerpRgb(0x5FA84E, 0xFFD34A, fill);

        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int cx = x + FW / 2, cy = y + FH / 2;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0);

        int haloA = (int) (40 + 110 * fill + 70 * punch);
        int spread = 3 + (int) (4 * fill + 8 * punch);
        g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, (Math.min(255, haloA) << 24) | haloRgb);

        int tx = x + 4, ty = y + 4;
        g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 128, 48);

        int zw = Math.max(2, (int) (zoneHalf * TW));
        if (mode == 1) {
            g.fill(tx, ty, tx + zw, ty + TH, 0xC8000000 | stopRgb);
            g.fill(tx + TW - zw, ty, tx + TW, ty + TH, 0xC8000000 | stopRgb);
            g.drawCenteredString(mc.font, Component.literal("▲"), tx + zw / 2, ty, 0xFF1C1814);
            g.drawCenteredString(mc.font, Component.literal("▼"), tx + TW - zw / 2, ty, 0xFF1C1814);
        } else {
            // the forward stop is the right end: green is the clean turnover, amber still flies
            int ow = (int) (FlyCast.NORMAL_WINDOW / (float) (FlyCast.SWING_PERIOD / 2) * TW);
            g.fill(tx + TW - ow, ty, tx + TW - zw, ty + TH, 0xB0C8862E);
            g.fill(tx + TW - zw, ty, tx + TW, ty + TH, 0xE0000000 | stopRgb);
            g.fill(tx, ty, tx + zw / 2, ty + TH, 0x60231A10);
        }
        if (punch > 0.05f) g.fill(tx, ty, tx + TW, ty + TH, ((int) (110 * punch) << 24) | 0xFFFFFF);

        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, 0xFFFFE8A8);

        if (mode == 1) {
            int px0 = x + (FW - maxBeats * 6) / 2, py0 = y - 7;
            for (int i = 0; i < maxBeats; i++) {
                int px = px0 + i * 6;
                boolean lit = i < beats;
                int grow = lit && i == beats - 1 ? (int) (2 * punch) : 0;
                int c = lit ? lerpRgb(0xFFC83C, 0xFFFFFF, punch) | 0xFF000000 : 0x60231A10;
                g.fill(px - grow, py0 - grow, px + 4 + grow, py0 + 4 + grow, c);
            }
        }
        g.pose().popPose();

        int px = x + (FW - 48) / 2, py = y - 30;
        if (mode == 0) {
            String label = String.format(java.util.Locale.ROOT, "%.0f m", metres);
            int lw = mc.font.width(label);
            g.blit(BAR, px, py, 48, 16, 0f, 32f, 48, 16, 128, 48);
            g.drawString(mc.font, label, px + (48 - lw) / 2, py + 4, 0xFF3A2A18, false);
            String cap = String.format(java.util.Locale.ROOT, "/ %d", maxBeats);
            g.drawString(mc.font, cap, px + 50, py + 4, 0xFFB08D3C, true);
            boolean full = metres >= maxBeats - 1e-6;
            g.drawCenteredString(mc.font, Component.translatable(full ? "gui.riverfishing.fly_full" : "gui.riverfishing.fly_hint"),
                    screenW / 2, y + FH + 8, full ? 0xFFB8E8A0 : 0xFFB8AE9A);
        } else {
            g.drawCenteredString(mc.font, Component.translatable("gui.riverfishing.jig_hint"), screenW / 2, y + FH + 8, 0xFFB8AE9A);
            g.drawCenteredString(mc.font, Component.translatable("gui.riverfishing.jig_beats", beats),
                    screenW / 2, py - 24, 0xFFF0E6CD);
        }
    }

    /** The line's state: one sentence under the crosshair, and the button when a fish is on it. */
    private static void renderLine(GuiGraphics g, Minecraft mc, int screenW, int screenH) {
        if (heldFlyRod(mc).isEmpty()) return;
        long t = mc.level.getGameTime();
        boolean strike = beats == FlyDrift.HUD_STRIKE_LMB || beats == FlyDrift.HUD_STRIKE_RMB;
        String key;
        int col;
        switch (beats) {
            case FlyDrift.HUD_DRAG -> { key = "hud.riverfishing.fly_drag"; col = 0xFFFFC850; }
            case FlyDrift.HUD_STRAIGHT -> { key = "hud.riverfishing.fly_straight"; col = 0xFFB8AE9A; }
            case FlyDrift.HUD_APPROACH -> { key = "hud.riverfishing.fly_approach"; col = ((t / 4) % 2 == 0) ? 0xFFFFF0A0 : 0xFFD8C070; }
            case FlyDrift.HUD_STRIKE_LMB -> { key = "hud.riverfishing.fly_strike_lmb"; col = 0xFFFF6040; }
            case FlyDrift.HUD_STRIKE_RMB -> { key = "hud.riverfishing.fly_strike_rmb"; col = 0xFFFF6040; }
            case FlyDrift.HUD_ON_FISH -> { key = "hud.riverfishing.fly_on_fish"; col = ((t / 6) % 2 == 0) ? 0xFF9CF08C : 0xFF5FD070; }
            default -> { key = "hud.riverfishing.fly_dead_drift"; col = 0xFF7CE07C; }
        }
        String text = Component.translatable(key).getString();
        int cx = screenW / 2, y = screenH / 2 + 14;
        float pop = strike ? (float) Math.exp(-since(stateNanos) * 5.0) : 0f;
        int w = mc.font.width(text);
        g.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 11, 0x66000000);
        if (pop > 0.02f) {
            g.pose().pushPose();
            g.pose().translate(cx, y + 4, 0);
            g.pose().scale(1f + 0.7f * pop, 1f + 0.7f * pop, 1f);
            g.drawCenteredString(mc.font, text, 0, -4, col);
            g.pose().popPose();
        } else {
            g.drawCenteredString(mc.font, text, cx, y, col);
        }
        if (!strike) {
            String m = maxBeats + " m";
            g.drawString(mc.font, m, cx + w / 2 + 8, y, 0xFFB08D3C, true);
            g.drawCenteredString(mc.font, Component.translatable("hud.riverfishing.fly_controls"), cx, y + 13, 0xFF8E8676);
        }
    }
}
