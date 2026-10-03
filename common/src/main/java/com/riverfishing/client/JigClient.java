package com.riverfishing.client;

import com.riverfishing.component.RodType;
import com.riverfishing.item.RodItem;
import com.riverfishing.network.JigBeatPacket;
import com.riverfishing.network.JigGaugePacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * §ice-rhythm: the winter rod's jig gauge — the one bar this mod draws while you fish.
 *
 * <p>The rod works the mormyshka on its own while use is held: the needle sweeps between ▲ the lift and
 * ▼ the drop, and a left click that lands on a stop is an accent — the bar flashes and the take comes
 * sooner. §jig-3: the run has no ceiling, so what the bar carries is an arcade combo — 12x, and a
 * bigger, hotter word the longer it holds. A missed click drops it to nothing in one flash of red.
 */
public final class JigClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static boolean attackWas;
    private static long hitNanos = -1L;
    private static long missNanos = -1L;
    /** The combo the miss threw away — what the MISS flash counts down from. */
    private static int lostCombo;

    private static final net.minecraft.resources.ResourceLocation BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    /**
     * §jig-3: the arcade ladder — the combo each word needs, longest run first.
     *
     * <p>§jig-4: retuned down from {30, 20, 12, 7, 3}. A take now lands four seconds into a clean
     * rhythm, and tools/check_ice_bite_clock.py simulates the longest combo one take can hold: eleven,
     * on the slowest hole, played without a miss. The old top two rungs were dead text on a bar nobody
     * could fill; that check now fails if a rung climbs back out of reach.
     */
    private static final int[] TIER_AT = {11, 9, 7, 5, 3};
    private static final String[] TIER_KEY = {"legend", "unreal", "fire", "great", "good"};
    private static final int[] TIER_RGB = {0xFF4FD8, 0xFF3B30, 0xFF9A16, 0xFFD447, 0xEFEFE0};

    private JigClient() {}

    public static void accept(JigGaugePacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean hit = p.active && p.beats > beats;
        boolean miss = p.active && p.beats == 0 && beats > 0;
        if (!active && p.active) { hitNanos = -1L; missNanos = -1L; }
        if (miss) lostCombo = beats;
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        if (mc.player == null) return;
        if (hit) {
            hitNanos = System.nanoTime();
            // the pitch climbs with the run, the way an arcade counter does
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.9f + 0.06f * Math.min(beats, 16));
        } else if (miss) {
            missNanos = System.nanoTime();
            mc.player.playSound(SoundEvents.ITEM_BREAK, 0.5f, 0.7f);
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

    /** Client tick: while the rod jigs, the left button is the accent and never a block hit. */
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
        // §jig-swing: vanilla's own swing stays drained (it would punch the block in front of you); the
        // server swings the arm for the accent instead, so the jerk is a rod stroke you can see.
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

    /** The rung of the ladder this combo is on, or -1 below the first word. */
    private static int tier(int combo) {
        for (int i = 0; i < TIER_AT.length; i++) if (combo >= TIER_AT[i]) return i;
        return -1;
    }

    public static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        if (heldWinterRod(mc).isEmpty()) return;
        float t = (mc.level.getGameTime() - startTick) + partialTick;

        float punch = (float) Math.exp(-since(hitNanos) * 6.0);
        float missFade = (float) Math.exp(-since(missNanos) * 1.6);
        // the run's own heat, 0..1 — everything on the bar grows and warms with it
        float heat = Mth.clamp(beats / 20f, 0f, 1f);
        float scale = (1f + 0.04f * Math.min(beats, 10)) * (1f + 0.3f * punch);
        int stopRgb = lerpRgb(0x5FA84E, 0xFFF4C0, heat * 0.9f);
        int haloRgb = lerpRgb(0x5FA84E, 0xFFD34A, heat);
        if (missFade > 0.05f) haloRgb = lerpRgb(haloRgb, 0xE03020, missFade);

        // §gui-art: the drawn frame — 256x34, its trough 230x14 at (13,10).
        final int FW = 256, FH = 34, TW = 230, TH = 14;
        int x = (screenW - FW) / 2, y = screenH - 88;
        int cx = x + FW / 2, cy = y + FH / 2;
        // §gui-180: the drawn frame is 180 wide; the accent punch rides on top of that one factor.
        final float S = 180f / FW;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale * S, scale * S, 1f);
        g.pose().translate(-cx, -cy, 0);

        int haloA = (int) (40 + 110 * heat + 70 * punch + 90 * missFade);
        int spread = 3 + (int) (4 * heat + 8 * punch + 6 * missFade);
        g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, (Math.min(255, haloA) << 24) | haloRgb);

        int tx = x + 13, ty = y + 10;   // §gui-art: the drawn trough
        g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 256, 96);

        int zw = Math.max(2, (int) (zoneHalf * TW));
        g.fill(tx, ty, tx + zw, ty + TH, 0xC8000000 | stopRgb);
        g.fill(tx + TW - zw, ty, tx + TW, ty + TH, 0xC8000000 | stopRgb);
        g.drawCenteredString(mc.font, Component.literal("▲"), tx + zw / 2, ty, 0xFF1C1814);
        g.drawCenteredString(mc.font, Component.literal("▼"), tx + TW - zw / 2, ty, 0xFF1C1814);
        if (punch > 0.05f) g.fill(tx, ty, tx + TW, ty + TH, ((int) (110 * punch) << 24) | 0xFFFFFF);

        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, 0xFFFFE8A8);
        g.pose().popPose();

        // §gui-180: where the scale actually left the frame's top — the words sit on that, not on the
        // unscaled rect the numbers above are written in.
        int top = (int) (cy - FH * S / 2f);
        // §jig-3: the counter. Above the bar, at its own scale, so a long run is genuinely big on the
        // screen — the arcade read the eight pips never were.
        if (beats > 0) {
            int rung = tier(beats);
            int rgb = rung < 0 ? 0xEFEFE0 : TIER_RGB[rung];
            // from ON FIRE up the number flickers, like a machine that cannot hold the colour
            if (rung >= 0 && rung <= 2) rgb = lerpRgb(rgb, 0xFFFFFF, 0.35f + 0.35f * Mth.sin(t * 0.9f));
            float cs = 1.6f + 0.06f * Math.min(beats, 12) + 0.5f * punch;
            drawBig(g, mc, Component.literal(beats + "x"), cx, top - 20, cs, rgb);
            if (rung >= 0) {
                drawBig(g, mc, Component.translatable("hud.riverfishing.jig." + TIER_KEY[rung]),
                        cx, top - 30, 1.0f + 0.1f * (TIER_AT.length - rung), rgb);
            }
        } else if (missFade > 0.05f) {
            drawBig(g, mc, Component.translatable("hud.riverfishing.jig.miss", lostCombo),
                    cx, top - 20, 1.4f, lerpRgb(0x603020, 0xFF5040, missFade));
        }
    }

    /** One centred line of text at its own scale, drawn about (cx, y). */
    private static void drawBig(GuiGraphics g, Minecraft mc, Component text, int cx, int y, float scale, int rgb) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(mc.font, text, 0, 0, 0xFF000000 | rgb);
        g.pose().popPose();
    }
}
