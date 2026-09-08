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
 * §fly-4: the fly rod's only piece of screen — the cast.
 *
 * <p>The rod swings on its own while use is held and the needle is where the rod is. The right end is the
 * forward stop: the green band is the clean turnover, the amber either side of it still flies. The metres
 * climb by three every full swing. Nothing is clicked; letting go is the whole skill.
 *
 * <p>Once the fly is on the water the screen goes quiet: the drift, the fish coming up and the take are
 * all on the river, not in a status line. The class stays awake only to pass the left button along — the
 * flick while the fly drifts, the lift when a fish has taken.
 *
 * <p>The winter rod's jig (mode 1, §ice-rhythm) shares the same frame.
 */
public final class FlyCastClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static int maxBeats;
    private static boolean attackWas;
    private static int mode;   // 0 the cast, 1 the jig
    private static long hitNanos = -1L;
    private static int lastStroke = -1;

    private static final net.minecraft.resources.ResourceLocation BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private FlyCastClient() {}

    public static void accept(FlyCastPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean hit = p.active && p.mode == 1 && p.beats > beats && mode == p.mode;
        if (!active && p.active) { hitNanos = -1L; lastStroke = -1; }
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        maxBeats = p.maxBeats;
        mode = p.mode;
        if (hit) {
            hitNanos = System.nanoTime();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.9f + 0.09f * Math.min(beats, 12));
            }
        }
    }

    /** The cast or the jig gauge is up — the charge bar yields to it. */
    public static boolean isActive() {
        return active;
    }

    /** §fly-5: the rod is swinging a cast right now — the hand pose and the airborne loop read this. */
    public static boolean isCasting() {
        return active && mode == 0;
    }

    /** 0 at the forward stop, 1 at the back stop: how far behind the angler the rod is loaded. */
    public static float loadFraction(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!isCasting() || mc.level == null) return 0f;
        return 1f - marker((mc.level.getGameTime() - startTick) + partialTick);
    }

    /** Metres of line in the air this instant — the length the loop is drawn at. */
    public static double airMetres() {
        Minecraft mc = Minecraft.getInstance();
        if (!isCasting() || mc.level == null) return 0;
        return FlyCast.lineOut(mc.level.getGameTime() - startTick, maxBeats);
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
     * Client tick. While a fly line is on the water the left button is the flick, and the lift when a fish
     * has taken; on the jig it is the accent. During a cast it does nothing, so nothing is sent.
     */
    public static void tick(Minecraft mc) {
        if (!active) {
            // no gauge, but a fly line on the water still listens for the left button
            boolean drifting = !heldFlyRod(mc).isEmpty() && ClientLineState.active();
            if (drifting) {
                boolean d = mc.options.keyAttack.isDown();
                if (d && !attackWas) ModNetwork.toServer(new FlyBeatPacket());
                attackWas = d;
                while (mc.options.keyAttack.consumeClick()) { /* drained: no arm swing, no block hit */ }
            } else {
                attackWas = false;
            }
            return;
        }
        if (heldRhythmRod(mc).isEmpty() || !mc.player.isUsingItem()) {
            active = false;   // the hold ended without a release (a slot switch): the gauge comes down
            return;
        }
        boolean down = mc.options.keyAttack.isDown();
        if (down && !attackWas && mode != 0) ModNetwork.toServer(new FlyBeatPacket());
        attackWas = down;
        while (mc.options.keyAttack.consumeClick()) { /* drained */ }
        // the swish at each end of the swing, on the client's own clock
        if (mc.level != null) {
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
        if (mode == 0) return;   // §fly-5: the cast is drawn in the world, not here
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

        if (mode == 0) {
            // the metres, on the plaque: the one number the cast needs
            int px = x + (FW - 48) / 2, py = y - 30;
            String label = String.format(java.util.Locale.ROOT, "%.0f m", metres);
            int lw = mc.font.width(label);
            g.blit(BAR, px, py, 48, 16, 0f, 32f, 48, 16, 128, 48);
            g.drawString(mc.font, label, px + (48 - lw) / 2, py + 4, 0xFF3A2A18, false);
            String cap = String.format(java.util.Locale.ROOT, "/ %d", maxBeats);
            g.drawString(mc.font, cap, px + 50, py + 4, 0xFFB08D3C, true);
        }
    }
}
