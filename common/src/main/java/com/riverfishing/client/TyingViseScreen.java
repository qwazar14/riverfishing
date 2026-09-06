package com.riverfishing.client;

import com.riverfishing.item.HookItem;
import com.riverfishing.menu.TyingViseMenu;
import com.riverfishing.network.ModNetwork;
import com.riverfishing.network.TieLurePacket;
import com.riverfishing.tackle.TiedDesign;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * §tying: the vise. A 16×16 canvas, a palette of MATERIALS down the right, the hook and the result
 * under it. Left-drag paints the chosen material, right-drag erases; the readout under the canvas is
 * {@link TiedDesign#analyse} run on what you have drawn so far — the same code the server will read
 * it with — and the palette greys out what your inventory cannot pay for.
 */
public class TyingViseScreen extends AbstractContainerScreen<TyingViseMenu> {
    private static final int CANVAS_X = 14, CANVAS_Y = 24, CELL = 8;
    private static final int PAL_X = 150, PAL_Y = 24, PAL_CELL = 18, PAL_COLS = 4;
    private static final int[] PALETTE;
    static {
        PALETTE = new int[TiedDesign.LAST];
        for (int i = 0; i < PALETTE.length; i++) PALETTE[i] = i + 1;
    }

    private final byte[] design = new byte[TiedDesign.SIZE * TiedDesign.SIZE];
    private int brush = TiedDesign.THREAD0 + 14;   // red thread to start
    private int painting = -1;                      // -1 idle, 0 paint, 1 erase

    public TyingViseScreen(TyingViseMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 254;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    private int cellAt(double mx, double my) {
        int x = (int) ((mx - leftPos - CANVAS_X) / CELL), y = (int) ((my - topPos - CANVAS_Y) / CELL);
        if (mx < leftPos + CANVAS_X || my < topPos + CANVAS_Y || x < 0 || y < 0 || x >= TiedDesign.SIZE || y >= TiedDesign.SIZE) return -1;
        return y * TiedDesign.SIZE + x;
    }

    private int paletteAt(double mx, double my) {
        int x = (int) ((mx - leftPos - PAL_X) / PAL_CELL), y = (int) ((my - topPos - PAL_Y) / PAL_CELL);
        if (mx < leftPos + PAL_X || my < topPos + PAL_Y || x < 0 || y < 0 || x >= PAL_COLS) return -1;
        int i = y * PAL_COLS + x;
        return i < PALETTE.length ? PALETTE[i] : -1;
    }

    private boolean tieHit(double mx, double my) {
        int bx = leftPos + PAL_X + 24, by = topPos + TyingViseMenu.HOOK_Y - 2;
        return mx >= bx && mx < bx + 36 && my >= by && my < by + 20;
    }

    private boolean clearHit(double mx, double my) {
        int bx = leftPos + CANVAS_X, by = topPos + CANVAS_Y + TiedDesign.SIZE * CELL + 4;
        return mx >= bx && mx < bx + 40 && my >= by && my < by + 12;
    }

    private boolean mirrorHit(double mx, double my) {
        int bx = leftPos + CANVAS_X + 46, by = topPos + CANVAS_Y + TiedDesign.SIZE * CELL + 4;
        return mx >= bx && mx < bx + 40 && my >= by && my < by + 12;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int c = cellAt(mx, my);
        if (c >= 0) {
            painting = button == 1 ? 1 : 0;
            design[c] = (byte) (painting == 1 ? 0 : brush);
            return true;
        }
        int p = paletteAt(mx, my);
        if (p > 0) { brush = p; return true; }
        if (clearHit(mx, my)) { java.util.Arrays.fill(design, (byte) 0); return true; }
        if (mirrorHit(mx, my)) {
            for (int y = 0; y < TiedDesign.SIZE; y++)
                for (int x = 0; x < TiedDesign.SIZE / 2; x++) design[y * TiedDesign.SIZE + (TiedDesign.SIZE - 1 - x)] = design[y * TiedDesign.SIZE + x];
            return true;
        }
        if (tieHit(mx, my) && canTie()) {
            ModNetwork.toServer(new TieLurePacket(design.clone()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (painting >= 0) {
            int c = cellAt(mx, my);
            if (c >= 0) design[c] = (byte) (painting == 1 ? 0 : brush);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        painting = -1;
        return super.mouseReleased(mx, my, button);
    }

    private boolean canTie() {
        return TiedDesign.valid(design) && menu.hook().getItem() instanceof HookItem && menu.result().isEmpty()
                && TieLurePacket.affordable(minecraft.player.getInventory(), design);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        GuiStyle.panel(g, leftPos, topPos, imageWidth, imageHeight);
        g.drawString(font, title, leftPos + 10, topPos + 8, GuiStyle.TEXT, false);

        // the canvas: a dark board, the drawing, a faint grid
        int cx = leftPos + CANVAS_X, cy = topPos + CANVAS_Y, side = TiedDesign.SIZE * CELL;
        g.fill(cx - 2, cy - 2, cx + side + 2, cy + side + 2, GuiStyle.SLOT_DARK);
        g.fill(cx, cy, cx + side, cy + side, 0xFF4E6E86);
        for (int y = 0; y < TiedDesign.SIZE; y++) {
            for (int x = 0; x < TiedDesign.SIZE; x++) {
                int px = design[y * TiedDesign.SIZE + x];
                int x0 = cx + x * CELL, y0 = cy + y * CELL;
                if (px != 0) g.fill(x0, y0, x0 + CELL, y0 + CELL, 0xFF000000 | TiedDesign.rgb(px));
                else if ((x + y) % 2 == 0) g.fill(x0, y0, x0 + CELL, y0 + CELL, 0xFF52738C);
            }
        }
        int hover = cellAt(mouseX, mouseY);
        if (hover >= 0) {
            int x0 = cx + (hover % TiedDesign.SIZE) * CELL, y0 = cy + (hover / TiedDesign.SIZE) * CELL;
            g.fill(x0, y0, x0 + CELL, y0 + CELL, 0x60FFFFFF);
        }
        // clear / mirror
        int by = cy + side + 4;
        button(g, cx, by, 40, Component.translatable("gui.riverfishing.tie_clear").getString(), true);
        button(g, cx + 46, by, 40, Component.translatable("gui.riverfishing.tie_mirror").getString(), true);

        // the palette: one cell a material, greyed when the inventory cannot pay for what is drawn
        int[] cost = TiedDesign.cost(design);
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < PALETTE.length; i++) {
            int px = PALETTE[i];
            int x0 = leftPos + PAL_X + (i % PAL_COLS) * PAL_CELL, y0 = topPos + PAL_Y + (i / PAL_COLS) * PAL_CELL;
            GuiStyle.slot(g, x0, y0);
            int rgb = TiedDesign.rgb(px);
            g.fill(x0 + 4, y0 + 4, x0 + 14, y0 + 14, 0xFF000000 | rgb);
            if (px == TiedDesign.EYE) g.fill(x0 + 7, y0 + 7, x0 + 11, y0 + 11, 0xFFFFFFFF);
            boolean afford = cost[px] == 0 || (TieLurePacket.count(inv, TieLurePacket.ingredient(px)) >= cost[px]
                    && (TieLurePacket.dyeFor(px) == null || TieLurePacket.count(inv, TieLurePacket.dyeFor(px)) >= cost[px]));
            if (!afford) g.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, 0x90B02020);
            if (px == brush) GuiStyle.accentFrame(g, x0, y0, 0xFFF0C040);
        }
        // hook / result wells, the tie button between them
        GuiStyle.slot(g, leftPos + TyingViseMenu.HOOK_X - 1, topPos + TyingViseMenu.HOOK_Y - 1);
        GuiStyle.slot(g, leftPos + TyingViseMenu.RESULT_X - 1, topPos + TyingViseMenu.RESULT_Y - 1);
        button(g, leftPos + PAL_X + 24, topPos + TyingViseMenu.HOOK_Y - 2, 36,
                Component.translatable("gui.riverfishing.tie").getString(), canTie());

        // the readout: what the engine will read off this drawing
        TiedDesign.Analysis a = TiedDesign.analyse(design);
        int ty = cy + side + 20;
        String line1 = Component.translatable("tied.riverfishing." + a.template().key).getString()
                + (a.template() != TiedDesign.Template.NONE ? "  " + Math.round(a.match() * 100) + "%" : "");
        g.drawString(font, line1, cx, ty, GuiStyle.TEXT, false);
        g.drawString(font, Component.translatable("tooltip.riverfishing.tied_size", a.sizeMm(),
                String.format(java.util.Locale.ROOT, "%.1f", a.weightG())).getString(), cx, ty + 11, GuiStyle.TEXT_HINT, false);
        // inventory wells
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) GuiStyle.slot(g, leftPos + 42 + col * 18, topPos + TyingViseMenu.INV_Y - 1 + row * 18);
        for (int col = 0; col < 9; col++) GuiStyle.slot(g, leftPos + 42 + col * 18, topPos + TyingViseMenu.INV_Y + 57);
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, boolean on) {
        g.fill(x, y, x + w, y + 12, on ? GuiStyle.SLOT_BG : GuiStyle.GHOST);
        g.fill(x + 1, y + 1, x + w - 1, y + 11, on ? GuiStyle.SLOT_HI : GuiStyle.GHOST);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + 2, on ? 0xFFF4E8C8 : 0xFF6E5A3C, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        int p = paletteAt(mouseX, mouseY);
        if (p > 0) {
            int[] cost = TiedDesign.cost(design);
            String name = Component.translatable("material.riverfishing." + TiedDesign.materialKey(p)).getString();
            String need = cost[p] > 0 ? "  ×" + cost[p] : "";
            g.renderTooltip(font, Component.literal(name + need), mouseX, mouseY);
        }
        renderTooltip(g, mouseX, mouseY);
    }
}
