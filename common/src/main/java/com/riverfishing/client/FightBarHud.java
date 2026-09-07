package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * §fight-bar: the fight's own boss bar — a carved oak frame with the water in its window, the fish riding
 * the edge of what you have won back, the angler's name on the plate above and the cue on the stone sign
 * below. One per angler fighting in sight (the line sync already tells every nearby client about every
 * fight), your own on top, the neighbours' stacked under it. Drawn off textures/gui/fight_bar.png; the
 * sheet's regions are listed in tools/gen_fight_bar.py, and any of them can be repainted in place.
 */
public final class FightBarHud {
    private static final Identifier TEX = RiverFishing.id("textures/gui/fight_bar.png");
    private static final int SW = 256, SH = 96;                    // the sheet
    private static final int FW = 256, FH = 56;                    // the frame
    private static final int WX = 28, WY = 20, WW = 200, WH = 20;  // the window
    private static final int SIGN_W = 88, SIGN_H = 20;
    private static final int GAP = 6;

    private FightBarHud() {}

    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        // §26.2: Options.hideGui moved onto the Hud itself (mc.gui.hud.isHidden()).
        //? if <26.2 {
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        //?} else {
        /*if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        *///?}
        List<Map.Entry<Integer, ClientLineState.Line>> fights = new ArrayList<>();
        for (Map.Entry<Integer, ClientLineState.Line> e : ClientLineState.lines().entrySet()) {
            if (e.getValue().fighting) fights.add(e);
        }
        if (fights.isEmpty()) return;
        final int me = mc.player.getId();
        fights.sort((a, b) -> a.getKey() == me ? -1 : b.getKey() == me ? 1 : Integer.compare(a.getKey(), b.getKey()));
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int limit = mc.getWindow().getGuiScaledHeight() / 2;
        long t = mc.level.getGameTime();
        int y = 4;
        for (Map.Entry<Integer, ClientLineState.Line> e : fights) {
            drawOne(g, mc, e.getKey(), e.getValue(), cx - FW / 2, y, t);
            y += FH + SIGN_H + GAP;
            if (y > limit) break;   // the top half of the screen is as much as the fights may take
        }
    }

    private static void drawOne(GuiGraphicsExtractor g, Minecraft mc, int playerId, ClientLineState.Line l, int x, int y, long t) {
        int wx = x + WX, wy = y + WY;
        int fill = Mth.clamp(Math.round(l.smoothProgress * WW), 0, WW);
        // the water: the wave scrolls a pixel a tick under everything, the deep stands from the fill's edge on
        g.enableScissor(wx, wy, wx + WW, wy + WH);
        int scroll = (int) (t % 40);
        for (int px = wx - 40 - scroll; px < wx + WW; px += 40) blit(g, px, wy, 0, 56, 40, 20);
        for (int px = wx + fill; px < wx + WW; px += 40) blit(g, px, wy, 40, 56, Math.min(40, wx + WW - px), 20);
        float ten = l.smoothTension;
        if (ten > 0.5f) {   // the line's break-risk, as a red that rises through the water you hold
            int a = (int) (Math.min(1f, (ten - 0.5f) * 2f) * 150);
            g.fill(wx, wy, wx + Math.max(1, fill), wy + WH, (a << 24) | 0xE03A2A);
        }
        g.disableScissor();
        blit(g, x, y, 0, 0, FW, FH);
        // the fish rides the fill's edge, and beats its tail while it runs
        int frame = l.running && (t / 4) % 2 == 0 ? 104 : 80;
        int fx = wx + Mth.clamp(fill - 12, 0, WW - 24);
        int fy = wy + 2 + (int) Math.round(Math.sin(t * 0.35) * (l.running ? 1.5 : 0.5));
        blit(g, fx, fy, frame, 56, 24, 16);
        // the name plate
        Entity ent = mc.level.getEntity(playerId);
        String name = ent != null ? ent.getName().getString() : "?";
        if (l.fatigue > 0.7f) name = name + " · " + I18n.get("hud.riverfishing.tired");
        centred(g, mc, name, x + FW / 2, y + 3, 0xFFEAD9B0, true);
        // the sign: the cue
        int sx = x + (FW - SIGN_W) / 2, sy = y + FH;
        blit(g, sx, sy, 0, 76, SIGN_W, SIGN_H);
        String key; int col;
        if (ten > 0.85f) { key = "hud.riverfishing.drag_now"; col = 0xFF8E1A10; }
        else if (l.running) { key = "hud.riverfishing.ease"; col = 0xFF7A4E0A; }
        else { key = "hud.riverfishing.reel"; col = 0xFF1E4A1A; }
        centred(g, mc, I18n.get(key), x + FW / 2, sy + 6, col, false);
    }

    private static void centred(GuiGraphicsExtractor g, Minecraft mc, String s, int cx, int y, int color, boolean shadow) {
        g.text(mc.font, s, cx - mc.font.width(s) / 2, y, color, shadow);
    }

    private static void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEX, x, y, (float) u, (float) v, w, h, SW, SH);
    }
}
