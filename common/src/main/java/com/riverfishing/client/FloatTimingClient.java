package com.riverfishing.client;

import com.riverfishing.network.FloatTimingPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Client-side state + HUD for the float strike-timing mini-game (#5). */
public final class FloatTimingClient {
    private static boolean active;
    private static long startTick;
    private static int window;
    private static int period;
    private static float greenStart;
    private static float greenEnd;
    private static float orangeStart;
    private static float orangeEnd;

    /**
     * §strike-tune: the cast gauge's sheet. The same brass frame, because this is the same instrument
     * one moment later — the rod was aimed on it, and now the strike window is read on it.
     */
    private static final net.minecraft.resources.Identifier BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private FloatTimingClient() {}

    public static void accept(FloatTimingPacket p) {
        active = p.active;
        startTick = p.startTick;
        window = p.windowTicks;
        period = p.periodTicks;
        greenStart = p.greenStart;
        greenEnd = p.greenEnd;
        orangeStart = p.orangeStart;
        orangeEnd = p.orangeEnd;
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

    public static void render(GuiGraphicsExtractor g, int screenW, int screenH, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            active = false;
            return;
        }
        float t = (mc.level.getGameTime() - startTick) + partialTick;
        if (t > window + 5) {
            active = false;
            return;
        }

        // §strike-tune: the cast gauge's own geometry — frame 120x16, with a 112x8 recess at (4,4).
        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int tx = x + 4, ty = y + 4;

        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, x, y, 0f, 0f, FW, FH, 128, 48);

        // The orange band is the 25% hook, the green is the whole fish; green goes on top because
        // where they overlap the better answer is the one the player should be aiming at.
        int os = tx + (int) (orangeStart * TW), oe = tx + (int) (orangeEnd * TW);
        g.fill(os, ty, oe, ty + TH, 0xE0C8862E);
        int zs = tx + (int) (greenStart * TW), ze = tx + (int) (greenEnd * TW);
        g.fill(zs, ty, ze, ty + TH, 0xE05FA84E);

        // The needle, through the whole frame and a little past it, so it reads over either zone.
        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, 0xFFFFE8A8);

        Component label = Component.translatable("hud.riverfishing.strike_timing");
        int ly = y - 12;
        g.text(mc.font, label, screenW / 2 - mc.font.width(label) / 2, ly, 0xFFF0E6CD, true);
    }
}
