package com.riverfishing.client;

import com.riverfishing.network.TieLurePacket;
import com.riverfishing.tackle.TiedDesign;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * §tying: the canvas, as a page of the Tackle Station. The hook is already there — drawn from the
 * side the way a vise holds it, eye on the left, bend on the right — and you tie ON it: thread along
 * the shank, a bead at the eye, hackle at the head. A row of PATTERNS under the canvas are stencils:
 * pick one and it ghosts over the hook; Stamp lays it down in the material you hold, and you edit
 * from there. The readout says what the engine will read it as, live, with the same code the server
 * reads it with.
 *
 * <p>State and input live here so the station screen stays a station screen; the three versions'
 * screens differ only in how they spell a click and a fill.
 */
public final class TyingCanvas {
    public static final int CANVAS_X = 14, CANVAS_Y = 30, CELL = 6, SIDE = TiedDesign.SIZE * CELL;
    public static final int PAL_X = 118, PAL_Y = 30, PAL_CELL = 18;
    public static final int MAT_Y = PAL_Y + 4 * PAL_CELL + 2;      // the six materials, one row
    public static final int PAT_Y = CANVAS_Y + SIDE + 4, PAT_W = 12;   // the eight stencils, one row
    public static final int BTN_Y = MAT_Y + PAL_CELL + 4, BTN_W = 36;
    public static final int READ_Y = PAT_Y + 14;
    public static final int TIE_X = 196, TIE_Y = 128, TIE_W = 46, TIE_H = 18;
    private static final int[] MATERIALS = {TiedDesign.HACKLE, TiedDesign.FUR, TiedDesign.BEAD_IRON, TiedDesign.BEAD_GOLD, TiedDesign.TINSEL, TiedDesign.EYE};
    private static final TiedDesign.Template[] STENCILS = {
            TiedDesign.Template.PELLET, TiedDesign.Template.DROP, TiedDesign.Template.DEVIL, TiedDesign.Template.ANT,
            TiedDesign.Template.NYMPH, TiedDesign.Template.STREAMER, TiedDesign.Template.SHRIMP, TiedDesign.Template.DRY_FLY};

    public final byte[] design = new byte[TiedDesign.SIZE * TiedDesign.SIZE];
    private int brush = TiedDesign.THREAD0 + 14;   // red thread
    private int stencil = -1;
    private int painting = -1;                      // -1 idle, 0 paint, 1 erase

    private int cellAt(int left, int top, double mx, double my) {
        int x = (int) ((mx - left - CANVAS_X) / CELL), y = (int) ((my - top - CANVAS_Y) / CELL);
        if (mx < left + CANVAS_X || my < top + CANVAS_Y || x < 0 || y < 0 || x >= TiedDesign.SIZE || y >= TiedDesign.SIZE) return -1;
        return y * TiedDesign.SIZE + x;
    }

    private int paletteAt(int left, int top, double mx, double my) {
        int x = (int) ((mx - left - PAL_X) / PAL_CELL);
        if (mx < left + PAL_X || x < 0) return -1;
        if (my >= top + PAL_Y && my < top + PAL_Y + 4 * PAL_CELL && x < 4) {
            return TiedDesign.THREAD0 + (int) ((my - top - PAL_Y) / PAL_CELL) * 4 + x;
        }
        if (my >= top + MAT_Y && my < top + MAT_Y + PAL_CELL && x < MATERIALS.length) return MATERIALS[x];
        return -1;
    }

    private int stencilAt(int left, int top, double mx, double my) {
        if (my < top + PAT_Y || my >= top + PAT_Y + PAT_W || mx < left + CANVAS_X) return -1;
        int i = (int) ((mx - left - CANVAS_X) / PAT_W);
        return i < STENCILS.length ? i : -1;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The station wells and the inventory together pay — the same walk the server does. */
    public boolean canTie(AbstractContainerMenu menu) {
        return TiedDesign.valid(design) && TieLurePacket.affordable(menu, design)
                && TieLurePacket.count(menu, TieLurePacket.HOOK) >= 1 + TiedDesign.cost(design)[TiedDesign.BEAD_IRON];
    }

    /** A click on the page. Returns true when it was ours. {@code button}: 0 paint, 1 erase. */
    public boolean click(int left, int top, double mx, double my, int button, Runnable tie) {
        int c = cellAt(left, top, mx, my);
        if (c >= 0) { painting = button == 1 ? 1 : 0; design[c] = (byte) (painting == 1 ? 0 : brush); return true; }
        int p = paletteAt(left, top, mx, my);
        if (p > 0) { brush = p; return true; }
        int s = stencilAt(left, top, mx, my);
        if (s >= 0) { stencil = stencil == s ? -1 : s; return true; }
        int bx = left + PAL_X, by = top + BTN_Y;
        if (in(mx, my, bx, by, BTN_W, 12)) { stamp(); return true; }
        if (in(mx, my, bx + BTN_W + 4, by, BTN_W, 12)) { java.util.Arrays.fill(design, (byte) 0); return true; }
        if (in(mx, my, bx + 2 * (BTN_W + 4), by, BTN_W, 12)) { mirror(); return true; }
        if (in(mx, my, left + TIE_X, top + TIE_Y, TIE_W, TIE_H)) { tie.run(); return true; }
        return false;
    }

    public boolean drag(int left, int top, double mx, double my) {
        if (painting < 0) return false;
        int c = cellAt(left, top, mx, my);
        if (c >= 0) design[c] = (byte) (painting == 1 ? 0 : brush);
        return true;
    }

    public void release() { painting = -1; }

    /** The stencil laid down in the material you hold — then you edit. */
    private void stamp() {
        if (stencil < 0) return;
        boolean[][] m = STENCILS[stencil].mask();
        for (int y = 0; y < TiedDesign.SIZE; y++)
            for (int x = 0; x < TiedDesign.SIZE; x++) if (m[y][x]) design[y * TiedDesign.SIZE + x] = (byte) brush;
    }

    private void mirror() {
        for (int y = 0; y < TiedDesign.SIZE / 2; y++)
            for (int x = 0; x < TiedDesign.SIZE; x++)
                design[(TiedDesign.SIZE - 1 - y) * TiedDesign.SIZE + x] = design[y * TiedDesign.SIZE + x];
    }

    public void draw(GuiGraphicsExtractor g, Font font, int left, int top, int mouseX, int mouseY, AbstractContainerMenu menu) {
        int cx = left + CANVAS_X, cy = top + CANVAS_Y;
        // the board and the hook in the vise — the hook is not yours to paint, it is what you tie on
        g.fill(cx - 2, cy - 2, cx + SIDE + 2, cy + SIDE + 2, 0xFF1C1814);
        g.fill(cx, cy, cx + SIDE, cy + SIDE, 0xFF2E3A46);
        boolean[][] hook = TiedDesign.HOOK;
        boolean[][] ghost = stencil >= 0 ? STENCILS[stencil].mask() : null;
        for (int y = 0; y < TiedDesign.SIZE; y++) {
            for (int x = 0; x < TiedDesign.SIZE; x++) {
                int x0 = cx + x * CELL, y0 = cy + y * CELL;
                if ((x + y) % 2 == 0) g.fill(x0, y0, x0 + CELL, y0 + CELL, 0xFF33404C);
                if (hook[y][x]) g.fill(x0, y0, x0 + CELL, y0 + CELL, 0xFF9AA3AA);
                int px = design[y * TiedDesign.SIZE + x];
                if (px != 0) g.fill(x0, y0, x0 + CELL, y0 + CELL, 0xFF000000 | TiedDesign.rgb(px));
                else if (ghost != null && ghost[y][x]) g.fill(x0, y0, x0 + CELL, y0 + CELL, 0x50FFFFFF);
            }
        }
        int hover = cellAt(left, top, mouseX, mouseY);
        if (hover >= 0) {
            int x0 = cx + (hover % TiedDesign.SIZE) * CELL, y0 = cy + (hover / TiedDesign.SIZE) * CELL;
            g.fill(x0, y0, x0 + CELL, y0 + CELL, 0x60FFFFFF);
        }
        // the stencils
        for (int i = 0; i < STENCILS.length; i++) {
            int x0 = cx + i * PAT_W, y0 = top + PAT_Y;
            g.fill(x0, y0, x0 + PAT_W - 1, y0 + PAT_W - 1, stencil == i ? 0xFFC8A050 : 0xFF2a241c);
            boolean[][] m = STENCILS[i].mask();
            for (int y = 0; y < TiedDesign.SIZE; y += 2)
                for (int x = 0; x < TiedDesign.SIZE; x += 2)
                    if (m[y][x] || m[y + 1][x] || m[y][x + 1]) g.fill(x0 + 1 + x / 2 * 5 / 8, y0 + 1 + y / 2 * 5 / 8, x0 + 2 + x / 2 * 5 / 8, y0 + 2 + y / 2 * 5 / 8, 0xFFE8DCC0);
        }
        // the palette: sixteen threads, then the six materials; greyed red when the inventory cannot pay
        int[] cost = TiedDesign.cost(design);
        for (int i = 0; i < 16; i++) paletteCell(g, left + PAL_X + (i % 4) * PAL_CELL, top + PAL_Y + (i / 4) * PAL_CELL, TiedDesign.THREAD0 + i, cost, menu);
        for (int i = 0; i < MATERIALS.length; i++) paletteCell(g, left + PAL_X + i * PAL_CELL, top + MAT_Y, MATERIALS[i], cost, menu);
        // stamp / clear / mirror
        int bx = left + PAL_X, by = top + BTN_Y;
        button(g, font, bx, by, BTN_W, I18n("gui.riverfishing.tie_stamp"), stencil >= 0);
        button(g, font, bx + BTN_W + 4, by, BTN_W, I18n("gui.riverfishing.tie_clear"), true);
        button(g, font, bx + 2 * (BTN_W + 4), by, BTN_W, I18n("gui.riverfishing.tie_mirror"), true);
        // the readout, and the tie button by the result well
        TiedDesign.Analysis a = TiedDesign.analyse(design);
        String read = I18n("tied.riverfishing." + a.template().key)
                + (a.template() != TiedDesign.Template.NONE ? " " + Math.round(a.match() * 100) + "%" : "")
                + "  ·  " + Component.translatable("tooltip.riverfishing.tied_size", a.sizeMm(),
                String.format(java.util.Locale.ROOT, "%.1f", a.weightG())).getString();
        g.text(font, read, cx, top + READ_Y, 0xFFE8DCC0, false);
        button(g, font, left + TIE_X, top + TIE_Y, TIE_W, I18n("gui.riverfishing.tie"), canTie(menu), TIE_H);
        // the hook is a nugget of iron — say so beside the result well, red when there is none to take
        int nuggets = 1 + cost[TiedDesign.BEAD_IRON], have = TieLurePacket.count(menu, TieLurePacket.HOOK);
        g.fakeItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_NUGGET), left + TIE_X, top + 150);
        g.text(font, "×" + nuggets, left + TIE_X + 18, top + 154, have >= nuggets ? 0xFF9a8d78 : 0xFFE06050, false);
    }

    private static void paletteCell(GuiGraphicsExtractor g, int x0, int y0, int px, int[] cost, AbstractContainerMenu menu) {
        g.fill(x0, y0, x0 + PAL_CELL - 1, y0 + PAL_CELL - 1, 0xFF2a241c);
        g.fill(x0 + 3, y0 + 3, x0 + PAL_CELL - 4, y0 + PAL_CELL - 4, 0xFF000000 | TiedDesign.rgb(px));
        if (px == TiedDesign.EYE) g.fill(x0 + 7, y0 + 7, x0 + 11, y0 + 11, 0xFFFFFFFF);
        if (px == TiedDesign.HACKLE) for (int k = 0; k < 4; k++) g.fill(x0 + 4 + k * 3, y0 + 4, x0 + 5 + k * 3, y0 + 14, 0xFF3B2A18);
        boolean afford = cost[px] == 0 || (TieLurePacket.count(menu, TieLurePacket.ingredient(px)) >= cost[px]
                && (TieLurePacket.dyeFor(px) == null || TieLurePacket.count(menu, TieLurePacket.dyeFor(px)) >= cost[px]));
        if (!afford) g.fill(x0 + 1, y0 + 1, x0 + PAL_CELL - 2, y0 + PAL_CELL - 2, 0x90B02020);
    }

    public void paletteTooltip(GuiGraphicsExtractor g, Font font, int left, int top, int mouseX, int mouseY) {
        int p = paletteAt(left, top, mouseX, mouseY);
        if (p <= 0) {
            int s = stencilAt(left, top, mouseX, mouseY);
            if (s >= 0) g.setTooltipForNextFrame(font, Component.translatable("tied.riverfishing." + STENCILS[s].key), mouseX, mouseY);
            return;
        }
        int[] cost = TiedDesign.cost(design);
        String name = I18n("material.riverfishing." + TiedDesign.materialKey(p));
        g.setTooltipForNextFrame(font, Component.literal(cost[p] > 0 ? name + "  ×" + cost[p] : name), mouseX, mouseY);
    }

    public void markBrush(GuiGraphicsExtractor g, int left, int top) {
        int i = brush - TiedDesign.THREAD0;
        int x0, y0;
        if (i >= 0 && i < 16) { x0 = left + PAL_X + (i % 4) * PAL_CELL; y0 = top + PAL_Y + (i / 4) * PAL_CELL; }
        else { int k = 0; while (k < MATERIALS.length && MATERIALS[k] != brush) k++; x0 = left + PAL_X + k * PAL_CELL; y0 = top + MAT_Y; }
        g.fill(x0, y0, x0 + PAL_CELL - 1, y0 + 1, 0xFFF0C040); g.fill(x0, y0 + PAL_CELL - 2, x0 + PAL_CELL - 1, y0 + PAL_CELL - 1, 0xFFF0C040);
        g.fill(x0, y0, x0 + 1, y0 + PAL_CELL - 1, 0xFFF0C040); g.fill(x0 + PAL_CELL - 2, y0, x0 + PAL_CELL - 1, y0 + PAL_CELL - 1, 0xFFF0C040);
    }

    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, boolean on) { button(g, font, x, y, w, label, on, 12); }

    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, boolean on, int h) {
        g.fill(x, y, x + w, y + h, on ? 0xFF6e5a3a : 0xFF2a241c);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? 0xFF8a7248 : 0xFF3a3227);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, on ? 0xFFFFE6B0 : 0xFF6E5A3C, false);
    }

    private static String I18n(String key) { return Component.translatable(key).getString(); }
}
