package com.riverfishing.client;

import com.riverfishing.component.RodType;
import com.riverfishing.item.RodItem;
import com.riverfishing.network.JigBeatPacket;
import com.riverfishing.network.JigGaugePacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * §ice-rhythm: the winter rod's jig gauge — the one bar this mod draws while you fish.
 *
 * <p>The rod works the mormyshka on its own while use is held: the needle sweeps between ▲ the lift and
 * ▼ the drop, and a left click that lands on a stop is an accent — the bar flashes, the pips above it
 * fill, and the take comes sooner.
 */
public final class JigClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static int maxBeats;
    private static boolean attackWas;
    private static long hitNanos = -1L;

    private static final net.minecraft.resources.Identifier BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private JigClient() {}

    public static void accept(JigGaugePacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean hit = p.active && p.beats > beats;
        if (!active && p.active) hitNanos = -1L;
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        maxBeats = p.maxBeats;
        if (hit) {
            hitNanos = System.nanoTime();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.9f + 0.09f * Math.min(beats, 12));
            }
        }
    }

    /** The jig gauge is up — the charge bar yields to it. */
    public static boolean isActive() {
        return active;
    }

    private static float marker(float t) {
        if (period <= 0) return 0.5f;
        float phase = (t % period) / period;
        if (phase < 0) phase += 1f;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    private static ItemStack heldWinterRod(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.WINTER) return main;
        ItemStack off = mc.player.getOffhandItem();
        return off.getItem() instanceof RodItem ri2 && ri2.rodType() == RodType.WINTER ? off : ItemStack.EMPTY;
    }

    /** Client tick: while the rod jigs, the left button is the accent and never an arm swing. */
    public static void tick(Minecraft mc) {
        if (!active) {
            attackWas = false;
            return;
        }
        if (heldWinterRod(mc).isEmpty() || !mc.player.isUsingItem()) {
            active = false;   // the hold ended without a release (a slot switch): the gauge comes down
            return;
        }
        boolean down = mc.options.keyAttack.isDown();
        if (down && !attackWas) ModNetwork.toServer(new JigBeatPacket());
        attackWas = down;
        while (mc.options.keyAttack.consumeClick()) { /* drained: no arm swing, no block hit */ }
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

    public static void render(GuiGraphicsExtractor g, int screenW, int screenH, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        //? if <26.2 {
        if (mc.level == null || mc.options.hideGui) return;
        //?} else {
        /*if (mc.level == null || mc.gui.hud.isHidden()) return;
        *///?}
        if (heldWinterRod(mc).isEmpty()) return;
        float t = (mc.level.getGameTime() - startTick) + partialTick;

        float punch = (float) Math.exp(-since(hitNanos) * 6.0);
        float fill = maxBeats > 0 ? Mth.clamp(beats / (float) maxBeats, 0f, 1f) : 0f;
        float scale = (1f + 0.05f * Math.min(beats, 8)) * (1f + 0.3f * punch);
        int stopRgb = lerpRgb(0x5FA84E, 0xFFF4C0, fill * 0.9f);
        int haloRgb = lerpRgb(0x5FA84E, 0xFFD34A, fill);

        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int cx = x + FW / 2, cy = y + FH / 2;

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);

        int haloA = (int) (40 + 110 * fill + 70 * punch);
        int spread = 3 + (int) (4 * fill + 8 * punch);
        g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, (Math.min(255, haloA) << 24) | haloRgb);

        int tx = x + 4, ty = y + 4;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, x, y, 0f, 0f, FW, FH, 128, 48);

        int zw = Math.max(2, (int) (zoneHalf * TW));
        g.fill(tx, ty, tx + zw, ty + TH, 0xC8000000 | stopRgb);
        g.fill(tx + TW - zw, ty, tx + TW, ty + TH, 0xC8000000 | stopRgb);
        g.centeredText(mc.font, Component.literal("▲"), tx + zw / 2, ty, 0xFF1C1814);
        g.centeredText(mc.font, Component.literal("▼"), tx + TW - zw / 2, ty, 0xFF1C1814);
        if (punch > 0.05f) g.fill(tx, ty, tx + TW, ty + TH, ((int) (110 * punch) << 24) | 0xFFFFFF);

        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, 0xFFFFE8A8);

        int px0 = x + (FW - maxBeats * 6) / 2, py0 = y - 7;
        for (int i = 0; i < maxBeats; i++) {
            int px = px0 + i * 6;
            boolean lit = i < beats;
            int grow = lit && i == beats - 1 ? (int) (2 * punch) : 0;
            int c = lit ? lerpRgb(0xFFC83C, 0xFFFFFF, punch) | 0xFF000000 : 0x60231A10;
            g.fill(px - grow, py0 - grow, px + 4 + grow, py0 + 4 + grow, c);
        }
        g.pose().popMatrix();
    }
}
