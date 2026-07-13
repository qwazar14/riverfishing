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

        int barW = 160;
        int barH = 12;
        int x = (screenW - barW) / 2;
        int y = screenH - 72;

        g.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0xD0202020);
        g.fill(x, y, x + barW, y + barH, 0xFF3A3A3A);

        // Orange band first (25% hook), then the green zone (100%) on top — both at a random spot.
        int os = x + (int) (orangeStart * barW);
        int oe = x + (int) (orangeEnd * barW);
        g.fill(os, y, oe, y + barH, 0xD0D08030);                 // orange partial zone
        int zs = x + (int) (greenStart * barW);
        int ze = x + (int) (greenEnd * barW);
        g.fill(zs, y, ze, y + barH, 0xD050C050);                 // green target zone

        float m = marker(t);
        int mx = x + (int) (m * barW);
        g.fill(mx - 1, y - 3, mx + 2, y + barH + 3, 0xFFFFE040); // moving marker

        Component label = Component.translatable("hud.riverfishing.strike_timing");
        g.centeredText(mc.font, label, screenW / 2, y - 12, 0xFFFFFFFF);
    }
}
