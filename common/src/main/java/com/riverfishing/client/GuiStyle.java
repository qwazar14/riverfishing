package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Shared GUI styling for the mod's screens: a warm wood-and-brass panel over a parchment face, plus
 * sunken leather-wood slots, drawn from small 9-sliceable textures so every menu shares one look.
 */
public final class GuiStyle {
    private static final Identifier PANEL_TEX = RiverFishing.id("textures/gui/panel.png");
    private static final Identifier SLOT_TEX = RiverFishing.id("textures/gui/slot.png");
    private static final int PANEL_SIZE = 64;
    private static final int PANEL_BORDER = 8;

    private static final int BRASS = 0xFFB08D3C;

    // Legacy palette kept so existing screens compile; warm-tuned for the parchment face.
    public static final int PANEL_FACE = 0xFFE3D6B8;
    public static final int PANEL_EDGE = 0xFF34271A;
    public static final int PANEL_HI = 0xFFF0E6CD;
    public static final int TITLE_BAR = 0xFFB08D3C;
    public static final int SLOT_BG = 0xFF5C4A34;
    public static final int SLOT_DARK = 0xFF2B2016;
    public static final int SLOT_HI = 0xFF7A6446;
    public static final int TEXT = 0x3A2A18;
    public static final int TEXT_HINT = 0xFF6E5A3C;
    public static final int GHOST = 0xFF9C8968;

    private GuiStyle() {}

    /** Wood-and-brass panel over a parchment face, with a brass title separator near the top. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, PANEL_TEX, x, y, w, h, PANEL_BORDER, PANEL_SIZE);
        // title separator
        g.fill(x + 9, y + 18, x + w - 9, y + 19, BRASS);
        g.fill(x + 9, y + 19, x + w - 9, y + 20, 0x33000000);
    }

    /** A sunken leather-wood slot. {@code sx,sy} is the 16x16 content top-left (slot.x/slot.y). */
    public static void slot(GuiGraphics g, int sx, int sy) {
        g.blit(SLOT_TEX, sx - 1, sy - 1, 18, 18, 0f, 0f, 18, 18, 18, 18);
    }

    /** Blits a texture as a 9-slice so a small panel image can fill any panel size. */
    private static void nineSlice(GuiGraphics g, Identifier tex, int x, int y, int w, int h, int b, int ts) {
        int in = ts - 2 * b;      // inner (tileable) span in the source
        int cw = w - 2 * b;       // centre width in the destination
        int ch = h - 2 * b;
        // corners
        part(g, tex, x, y, b, b, 0, 0, b, b, ts);
        part(g, tex, x + w - b, y, b, b, ts - b, 0, b, b, ts);
        part(g, tex, x, y + h - b, b, b, 0, ts - b, b, b, ts);
        part(g, tex, x + w - b, y + h - b, b, b, ts - b, ts - b, b, b, ts);
        // edges (stretched)
        part(g, tex, x + b, y, cw, b, b, 0, in, b, ts);
        part(g, tex, x + b, y + h - b, cw, b, b, ts - b, in, b, ts);
        part(g, tex, x, y + b, b, ch, 0, b, b, in, ts);
        part(g, tex, x + w - b, y + b, b, ch, ts - b, b, b, in, ts);
        // centre
        part(g, tex, x + b, y + b, cw, ch, b, b, in, in, ts);
    }

    private static void part(GuiGraphics g, Identifier tex, int dx, int dy, int dw, int dh,
                             int su, int sv, int sw, int sh, int ts) {
        if (dw <= 0 || dh <= 0) return;
        g.blit(tex, dx, dy, dw, dh, (float) su, (float) sv, sw, sh, ts, ts);
    }

    /** A 1px coloured frame just outside a slot, marking its role. */
    public static void accentFrame(GuiGraphics g, int sx, int sy, int color) {
        g.fill(sx - 2, sy - 2, sx + 18, sy - 1, color);
        g.fill(sx - 2, sy + 17, sx + 18, sy + 18, color);
        g.fill(sx - 2, sy - 2, sx - 1, sy + 18, color);
        g.fill(sx + 17, sy - 2, sx + 18, sy + 18, color);
    }

    /** A faint centered glyph inside an empty slot, hinting what belongs there. */
    public static void ghostGlyph(GuiGraphics g, Font font, String glyph, int sx, int sy) {
        int gx = sx + (16 - font.width(glyph)) / 2;
        g.drawString(font, glyph, gx, sy + 4, GHOST, false);
    }

    /** A simple 2px line between two points (used to draw the rod blank under the assembly slots). */
    public static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            g.fill(x1, y1, x1 + 2, y1 + 2, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            g.fill(x, y, x + 3, y + 3, color);
        }
    }
}
