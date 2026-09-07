package com.riverfishing.client;

import com.riverfishing.component.RodType;
import com.riverfishing.fishing.FlyCast;
import com.riverfishing.item.RodItem;
import com.riverfishing.network.FlyBeatPacket;
import com.riverfishing.network.FlyCastPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * §fly: the rhythm gauge — the cast bar's brass frame with a green stop at BOTH ends, the needle
 * sweeping between them, a pip above the bar for every false cast in the air, and the metres the line
 * will land at on the plaque. The sneak key's down-edge is the beat; the server judges it.
 *
 * <p>§fly-juice: a hit has to be FELT. Every good beat punches the whole gauge up in size and it
 * settles back over half a second, the frame shakes for the first few frames, the stops glow from
 * green toward white the longer the combo runs, a halo grows around the frame with it, and a note
 * climbs in pitch with each beat. A bad beat drops the size back, shakes harder and flashes red.
 */
public final class FlyCastClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static int maxBeats;
    private static boolean openLoop;
    private static boolean shiftWas;
    /** Wall-clock of the last good beat / the last collapse, for the punch and the shake. */
    private static long hitNanos = -1L, missNanos = -1L;

    private static final net.minecraft.resources.Identifier BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private FlyCastClient() {}

    public static void accept(FlyCastPacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean hit = p.active && p.beats > beats;
        boolean miss = p.active && p.openLoop && (!openLoop || p.beats < beats);
        if (!active && p.active) { hitNanos = -1L; missNanos = -1L; }
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        maxBeats = p.maxBeats;
        openLoop = p.openLoop;
        if (hit) {
            hitNanos = System.nanoTime();
            if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55f, 0.9f + 0.09f * Math.min(beats, 12));
        } else if (miss) {
            missNanos = System.nanoTime();
            if (mc.player != null) mc.player.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.5f, 0.7f);
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** Triangle wave 0..1 with the given period; matches the server's marker. */
    private static float marker(float t) {
        if (period <= 0) return 0.5f;
        float phase = (t % period) / period;
        if (phase < 0) phase += 1f;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    /** The fly rod being held on the charge, or EMPTY when the hold is over (slot switch, hand change). */
    private static ItemStack heldFlyRod(Minecraft mc) {
        if (mc.player == null || !mc.player.isUsingItem()) return ItemStack.EMPTY;
        ItemStack use = mc.player.getUseItem();
        return use.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY ? use : ItemStack.EMPTY;
    }

    /** Client tick: the sneak key's down-edge while the gauge is up is a beat. */
    public static void tick(Minecraft mc) {
        if (!active) {
            shiftWas = false;
            return;
        }
        if (heldFlyRod(mc).isEmpty()) {
            // The hold ended without a release (a slot switch): the server never hears a release, so
            // the gauge comes down here — the next use begins a fresh rhythm anyway.
            active = false;
            return;
        }
        boolean down = mc.options.keyShift.isDown();
        if (down && !shiftWas) ModNetwork.toServer(new FlyBeatPacket());
        shiftWas = down;
    }

    /** Seconds since a wall-clock stamp, or a large number when there is none. */
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
        if (mc.level == null) return;
        ItemStack rod = heldFlyRod(mc);
        if (rod.isEmpty()) return;
        float t = (mc.level.getGameTime() - startTick) + partialTick;

        // §fly-juice: the punch (a hit), the collapse (a miss), and the combo's steady growth
        float dh = since(hitNanos), dm = since(missNanos);
        float punch = (float) Math.exp(-dh * 6.0);          // 1 at the hit, gone in half a second
        float crash = (float) Math.exp(-dm * 5.0);
        float combo = maxBeats > 0 ? Mth.clamp(beats / (float) maxBeats, 0f, 1f) : 0f;
        float scale = (1f + 0.06f * Math.min(beats, 10)) * (1f + 0.45f * punch) * (1f - 0.12f * crash);
        float shake = punch * 3f * Mth.sin(dh * 90f) + crash * 6f * Mth.sin(dm * 70f);
        int stopRgb = lerpRgb(0x5FA84E, 0xFFF4C0, combo * 0.9f);        // green → warm white with the combo
        int haloRgb = lerpRgb(0x5FA84E, 0xFFD34A, combo);

        // The cast gauge's own geometry — frame 120x16, with a 112x8 recess at (4,4).
        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int cx = x + FW / 2, cy = y + FH / 2;

        g.pose().pushMatrix();
        g.pose().translate(cx + shake, cy);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -cy);

        // the halo: wider and brighter as the combo runs, and a burst of it on every hit
        int haloA = (int) (40 + 120 * combo + 80 * punch);
        int spread = 3 + (int) (4 * combo + 10 * punch);
        g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, (Math.min(255, haloA) << 24) | haloRgb);
        if (crash > 0.05f) g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, ((int) (140 * crash) << 24) | 0xE05A4A);

        int tx = x + 4, ty = y + 4;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, x, y, 0f, 0f, FW, FH, 128, 48);

        // The two stops: as wide as the server's zone, glowing with the combo, flaring on the hit.
        int zw = (int) (zoneHalf * TW);
        int stopA = (int) (200 + 55 * punch);
        g.fill(tx, ty, tx + zw, ty + TH, (stopA << 24) | stopRgb);
        g.fill(tx + TW - zw, ty, tx + TW, ty + TH, (stopA << 24) | stopRgb);
        if (punch > 0.05f) {   // the hit's flash across the whole tube
            g.fill(tx, ty, tx + TW, ty + TH, ((int) (110 * punch) << 24) | 0xFFFFFF);
        }

        // The needle, through the whole frame and a little past it.
        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, openLoop ? 0xFFE05A4A : 0xFFFFE8A8);

        // One pip per false cast in the air — the newest one big and bright — red when the loop has
        // collapsed, hollow up to the rod's max.
        int px0 = x + (FW - maxBeats * 6) / 2, py0 = y - 7;
        for (int i = 0; i < maxBeats; i++) {
            int px = px0 + i * 6;
            boolean lit = i < beats;
            int grow = lit && i == beats - 1 ? (int) (2 * punch) : 0;
            int c = lit ? (openLoop ? 0xFFE05A4A : lerpRgb(0xFFC83C, 0xFFFFFF, punch) | 0xFF000000) : 0x60231A10;
            g.fill(px - grow, py0 - grow, px + 4 + grow, py0 + 4 + grow, c);
        }
        g.pose().popMatrix();

        // The metres on the plaque: the pickup plus the false casts, capped at what the rod carries.
        double metres = Math.min(FlyCast.PICKUP + beats, com.riverfishing.fishing.FishingManager.castRangeMax(rod));
        String label = String.format(java.util.Locale.ROOT, "%.1f m", metres);
        int lw = mc.font.width(label);
        int px = x + (FW - 48) / 2, py = y - 30;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, px, py, 0f, 32f, 48, 16, 128, 48);
        g.text(mc.font, label, px + (48 - lw) / 2, py + 4, 0xFF3A2A18, false);
        // the combo, big, growing with the beats and jumping on each
        if (beats > 0) {
            String comboText = "×" + beats;
            float cs = 1f + 0.08f * Math.min(beats, 10) + 0.5f * punch;
            g.pose().pushMatrix();
            g.pose().translate(cx, py - 10);
            g.pose().scale(cs, cs);
            g.centeredText(mc.font, comboText, 0, -4, openLoop ? 0xFFE05A4A : lerpRgb(0xF0E6CD, 0xFFE070, combo) | 0xFF000000);
            g.pose().popMatrix();
        }
        g.centeredText(mc.font, Component.translatable("gui.riverfishing.fly_beats", beats),
                screenW / 2, py - 24, openLoop ? 0xFFE05A4A : 0xFFF0E6CD);
    }
}
