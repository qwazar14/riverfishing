package com.riverfishing.client;

import com.riverfishing.menu.TackleStationMenu;
import com.riverfishing.registry.ModItems;
import com.riverfishing.tackle.TackleForm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * §tackle-station (0.6.0, playtest round 4): two tabs, a 3x3 form grid with hover names, a weight
 * stepper, a labeled fine-tuning drawer (draggable hook-link slider for rigs / balance buttons for
 * lures), ghost-hinted material slots with live requirement counts, and a stonecutter-style result.
 * §bench-look (1.1.0): drawn as a tackle-maker's bench — see the section of that name below.
 */
public class TackleStationScreen extends AbstractContainerScreen<TackleStationMenu> {
    private static final int GRID_X = 14, GRID_Y = 30, CELL = 22;
    /**
     * Five across. Four fitted the ten predator forms in three rows; fourteen would need four
     * rows, and the fourth row pushes the advanced drawer straight down into the material
     * wells at y=149. Five keeps every tab at three rows and the text column still fits the
     * longest Russian cost line in two lines.
     */
    private static final int COLS = 5;
    /** §bench-look: the drawer's controls sit under its label, left of the paper tag — not on top of it. */
    private static final int TRACK_X = GRID_X, TRACK_W = 70;
    /** §hook-pick: the well the hook SLOT used to occupy, now the size picker. */
    private static final int HOOK_X = 38;
    /** The two arrow buttons either side of it. The material wells moved right to make room. */
    private static final int HOOK_DOWN_X = 22, HOOK_UP_X = 59, HOOK_BTN_W = 12;
    private static final int HOOK_Y = 149, HOOK_BTN_H = 18;
    /** Where the right-hand column starts, and how much room it has — both derived, never guessed. */
    private static final int TEXT_X = GRID_X + COLS * CELL + 10;
    /** §bench-look: the paper tag the column is written on, and the room the ink has on it. */
    private static final int TAG_X = 128, TAG_Y = 25, TEXT_W = TAG_X + 112 - TEXT_X - 6;
    private boolean predatorTab;
    /** §tying: the third page — the hook in the vise and the canvas over it. */
    private boolean tying;
    private final TyingCanvas canvas = new TyingCanvas();
    private boolean advanced;
    private boolean draggingLeader;
    private int pendingLeader = -1;                 // local value while dragging; -1 = use menu's

    public TackleStationScreen(TackleStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 264;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    private List<TackleForm> tabForms() {
        List<TackleForm> out = new ArrayList<>();
        for (TackleForm f : TackleForm.values()) {
            if (f.predatorTab == predatorTab) out.add(f);
        }
        return out;
    }

    /**
     * The y of the "advanced" toggle: under the grid, wherever the grid ends. The predator tab has fourteen
     * forms and the peaceful six, so a literal here is a literal that is wrong on one of the two tabs.
     */
    private int advY() {
        int rows = (tabForms().size() + COLS - 1) / COLS;
        return GRID_Y + rows * CELL + 10;
    }

    /** Draw wrapped, advance past what was drawn. The only honest way to place text in nine languages. */
    private int flow(GuiGraphics g, String text, int x, int y, int colour) {
        for (net.minecraft.util.FormattedCharSequence line
                : font.split(net.minecraft.network.chat.Component.literal(text), TEXT_W)) {
            g.drawString(font, line, x, y, colour, false);
            y += 11;
        }
        return y + 3;
    }

    private int shownLeader() {
        return pendingLeader >= 0 ? pendingLeader : menu.leaderCm();
    }

    // ---- §bench-look (1.1.0): the station is a tackle-maker's bench — a walnut worktop in a dark-oak frame,
    // brass-framed labels for the three pages, the forms in the compartments of a tray, the item's name and
    // bill on a paper tag, the materials in wells cut into a rail and the inventory in a drawer. One sheet,
    // drawn by tools/gen_tackle_bench.py; the rectangles below are where that script puts each part. ----

    private static final ResourceLocation BENCH = com.riverfishing.RiverFishing.id("textures/gui/tackle_station/bench.png");
    private static final int TEX_W = 256, TEX_H = 400;
    /** {u, v, w, h} on the sheet. */
    private static final int[] TAG = {0, 264, 112, 117}, TAB_ON = {120, 264, 64, 14}, TAB_OFF = {120, 280, 64, 14},
            CELL_UV = {188, 264, 20, 20}, CELL_SEL = {210, 264, 20, 20}, WELL = {188, 286, 18, 18},
            CUP = {210, 286, 26, 26}, PLATE = {232, 264, 18, 18}, KNOB = {120, 296, 7, 11};
    /** Ink on the tag, and the brass-and-parchment text of the labels. */
    private static final int INK = 0xFF3A2A18, INK_SOFT = 0xFF5E4A30, INK_RED = 0xFF8A3A10, INK_GREEN = 0xFF3E6A2A;
    private static final int[] TAB_X = {10, 84, 158};
    private static final String[] TAB_KEYS = {"tab_peaceful", "tab_predator", "tab_tie"};

    static void sprite(GuiGraphics g, int[] s, int x, int y) {
        g.blit(BENCH, x, y, s[0], s[1], s[2], s[3], TEX_W, TEX_H);
    }

    /**
     * A key on the bench: a raised wooden button, brass when it is the one set, sunk dark and flat when it
     * cannot do anything — the tying page's buttons are these too.
     */
    static void benchButton(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, int h, String label,
                            boolean on, boolean live, boolean hov) {
        if (!live) {
            g.fill(x, y, x + w, y + h, 0xFF2E2016);
            g.fill(x, y, x + w, y + 1, 0xFF20150D);
        } else {
            int face = on ? 0xFFB88A3E : hov ? 0xFF8A603E : 0xFF72492E;
            g.fill(x, y, x + w, y + h, on ? 0xFF6E4E1E : 0xFF3A2416);
            g.fill(x, y, x + w - 1, y + h - 1, on ? 0xFFEAC678 : 0xFF9A7050);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        }
        int colour = !live ? 0xFF6B5A48 : on ? 0xFF2A1C0C : 0xFFF0E2C4;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1, colour, false);
    }

    private static boolean in(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(BENCH, x, y, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);
        drawTabs(g, x, y, mouseX, mouseY);
        if (tying) {   // §tying: the page is the canvas; the hook picker below it and the store wells stay
            canvas.draw(g, font, x, y, mouseX, mouseY, menu);
            canvas.markBrush(g, x, y);
            drawHookPicker(g, x, y, mouseX, mouseY);
            drawWells(g, x, y, true);
            return;
        }

        // The tray: one compartment a form, the chosen one in brass.
        List<TackleForm> forms = tabForms();
        TackleForm sel = menu.form();
        for (int i = 0; i < forms.size(); i++) {
            int cx = x + GRID_X + (i % COLS) * CELL;
            int cy = y + GRID_Y + (i / COLS) * CELL;
            sprite(g, forms.get(i) == sel ? CELL_SEL : CELL_UV, cx, cy);
            if (forms.get(i) != sel && in(mouseX, mouseY, cx, cy, cx + 20, cy + 20)) g.fill(cx + 2, cy + 2, cx + 18, cy + 18, 0x24FFE6B0);
            g.renderItem(new ItemStack(forms.get(i).item()), cx + 2, cy + 2);
        }

        // The tag: name, weight stepper, cast hint, cost — in ink.
        g.fill(x + TAG_X + 2, y + TAG_Y + 2, x + TAG_X + TAG[2] + 2, y + TAG_Y + TAG[3] + 2, 0x44000000);
        sprite(g, TAG, x + TAG_X, y + TAG_Y);
        int rx = x + TEXT_X;
        int avail = TEXT_W;
        // The name is clipped rather than wrapped: the two lines under it are at fixed offsets because
        // the weight stepper is clickable, and a name that pushed them down would move its hit box.
        g.drawString(font, font.plainSubstrByWidth(
                new ItemStack(sel.item()).getHoverName().getString(), avail), rx, y + GRID_Y, INK, false);
        int grams = menu.weightGrams();
        boolean stepHov = in(mouseX, mouseY, rx, y + GRID_Y + 14, rx + 90, y + GRID_Y + 28);
        g.drawString(font, "◄ " + I18n.get("screen.riverfishing.tackle_station.weight", grams) + " ►",
                rx, y + GRID_Y + 16, stepHov ? 0xFFB85A20 : INK_RED, false);
        int ly = y + GRID_Y + 32;
        ly = flow(g, I18n.get("screen.riverfishing.tackle_station.cast_hint",
                TackleForm.castHintBlocks(grams)), rx, ly, INK_SOFT);
        ly = flow(g, I18n.get("screen.riverfishing.tackle_station.cost",
                menu.ironNeeded(), sel.stringNeeded()), rx, ly, INK_SOFT);
        if (sel.dyeable) {
            flow(g, I18n.get("screen.riverfishing.tackle_station.dye_hint"), rx, ly, INK_GREEN);
        }

        // §tackle-adv drawer: its own labeled section, nothing overlaps.
        boolean advHov = in(mouseX, mouseY, x + GRID_X, y + advY() - 2, x + GRID_X + 110, y + advY() + 10);
        g.drawString(font, (advanced ? "▼ " : "► ")
                        + I18n.get("screen.riverfishing.tackle_station.advanced"),
                x + GRID_X, y + advY(), advHov ? 0xFFFFE6B0 : 0xFFD8C8A8, false);
        if (advanced) {
            if (sel.rig) {
                // Hook link (distance hook → anchor point) — rigs only: a groove in the worktop, a brass slide.
                g.drawString(font, I18n.get("screen.riverfishing.tackle_station.hook_link_label"),
                        x + GRID_X, y + advY() + 12, 0xFFC8B89A, false);
                int tx = x + TRACK_X, ty = y + advY() + 23;
                g.fill(tx, ty + 3, tx + TRACK_W, ty + 6, 0xFF24170E);
                g.fill(tx, ty + 6, tx + TRACK_W, ty + 7, 0x40FFE6B0);
                int hx = tx + (int) ((shownLeader() - 5) / 95.0 * TRACK_W);
                sprite(g, KNOB, hx - 3, ty - 1);
                g.drawString(font, shownLeader() + " " + I18n.get("screen.riverfishing.tackle_station.cm"),
                        tx + TRACK_W + 6, ty, 0xFFEDE4D0, false);
            } else {
                // Balance — lures only.
                g.drawString(font, I18n.get("screen.riverfishing.tackle_station.balance_label"),
                        x + GRID_X, y + advY() + 12, 0xFFC8B89A, false);
                String[] keys = {"balance_nose", "balance_center", "balance_tail"};
                for (int i = 0; i < 3; i++) {
                    int bx = x + TRACK_X + i * 38, by = y + advY() + 23;
                    benchButton(g, font, bx, by, 36, 11, I18n.get("screen.riverfishing.tackle_station." + keys[i]),
                            menu.balancePos() == i, true, in(mouseX, mouseY, bx, by, bx + 36, by + 11));
                }
            }
        }

        // The rail: the hook picker, the material wells with ghost hints and live counts (red when short),
        // and the result cup.
        drawHookPicker(g, x, y, mouseX, mouseY);
        drawWells(g, x, y, false);
        ItemStack[] ghosts = {
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
                new ItemStack(net.minecraft.world.item.Items.STRING),
                new ItemStack(net.minecraft.world.item.Items.RED_DYE)};
        int[] need = {menu.ironNeeded(), sel.stringNeeded(), 0};
        int[] wellX = {76, 100, 124};
        for (int i = 0; i < ghosts.length; i++) {
            ItemStack in = menu.getSlot(i).getItem();
            int wx = x + wellX[i], wy = y + 150;
            if (in.isEmpty()) {
                g.renderFakeItem(ghosts[i], wx, wy);
                g.fill(net.minecraft.client.renderer.RenderType.guiGhostRecipeOverlay(),
                        wx, wy, wx + 16, wy + 16, 0x9946301E);
            }
            if (need[i] > 0) {
                boolean short_ = in.getCount() < need[i];
                g.drawCenteredString(font, "×" + need[i], wx + 8, y + 169, short_ ? 0xFFE06050 : 0xFFD8C8A8);
            }
        }
        g.drawString(font, "→", x + 158, y + 154, 0xFFE8DCC0, false);
    }

    /** The wells cut into the rail: three and a cup on the tackle pages, the nine store wells on the tying page. */
    private void drawWells(GuiGraphics g, int x, int y, boolean store) {
        if (store) {
            for (int i = 0; i < TackleStationMenu.STORE_SLOTS; i++) sprite(g, WELL, x + 75 + i * 18, y + 149);
            return;
        }
        for (int wx : new int[]{75, 99, 123}) sprite(g, WELL, x + wx, y + 149);
        sprite(g, CUP, x + 171, y + 145);
    }

    /** §hook-pick: the hook on its brass plate between two keys — the Tie page keeps it, the hook is what you tie on. */
    private void drawHookPicker(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        sprite(g, PLATE, x + HOOK_X - 1, y + 149);
        g.renderItem(new ItemStack(ModItems.HOOKS.get(menu.hookIdx()).get()), x + HOOK_X, y + 150);
        // Dim at the ends of the ladder: a button that cannot do anything should not look
        // like one that can — #16 is the smallest hook there is and #1 the biggest.
        boolean down = menu.hookIdx() > 0, up = menu.hookIdx() < TackleForm.HOOK_SIZES.length - 1;
        benchButton(g, font, x + HOOK_DOWN_X, y + HOOK_Y, HOOK_BTN_W, HOOK_BTN_H, "◄", false, down,
                in(mouseX, mouseY, x + HOOK_DOWN_X, y + HOOK_Y, x + HOOK_DOWN_X + HOOK_BTN_W, y + HOOK_Y + HOOK_BTN_H));
        benchButton(g, font, x + HOOK_UP_X, y + HOOK_Y, HOOK_BTN_W, HOOK_BTN_H, "►", false, up,
                in(mouseX, mouseY, x + HOOK_UP_X, y + HOOK_Y, x + HOOK_UP_X + HOOK_BTN_W, y + HOOK_Y + HOOK_BTN_H));
        g.drawCenteredString(font, "#" + menu.hookSize(), x + HOOK_X + 8, y + 169, 0xFFFFD97A);
    }

    /** §tying: both sides must agree which wells are live, so the flip goes to the menu and the server. */
    private void setTying(boolean on) {
        if (tying == on) return;
        tying = on;
        menu.setTying(on);
        clickButton(on ? 601 : 600);
    }

    private boolean hookPickerClick(double mx, double my) {
        int x = leftPos, y = topPos;
        if (my >= y + HOOK_Y && my < y + HOOK_Y + HOOK_BTN_H && mx >= x + HOOK_DOWN_X && mx < x + HOOK_UP_X + HOOK_BTN_W) {
            int cur = menu.hookIdx();
            clickButton(500 + (mx < x + HOOK_X + 8 ? Math.max(0, cur - 1) : Math.min(TackleForm.HOOK_SIZES.length - 1, cur + 1)));
            return true;
        }
        return false;
    }

    /** The three labels in their brass frames, each with its page's own tackle drawn small beside the word. */
    private void drawTabs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int open = tying ? 2 : predatorTab ? 1 : 0;
        ItemStack[] icons = {new ItemStack(ModItems.FLOAT.get()), new ItemStack(TackleForm.SPINNER.item()),
                new ItemStack(net.minecraft.world.item.Items.FEATHER)};
        for (int i = 0; i < 3; i++) {
            int tx = x + TAB_X[i], ty = y + 8;
            sprite(g, i == open ? TAB_ON : TAB_OFF, tx, ty);
            if (i != open && in(mouseX, mouseY, tx, ty, tx + 64, ty + 14)) g.fill(tx + 2, ty + 2, tx + 62, ty + 12, 0x28FFF0C8);
            g.pose().pushPose();
            g.pose().translate(tx + 3, ty + 2, 0);
            g.pose().scale(0.625f, 0.625f, 1f);
            g.renderItem(icons[i], 0, 0);
            g.pose().popPose();
            String label = font.plainSubstrByWidth(I18n.get("screen.riverfishing.tackle_station." + TAB_KEYS[i]), 48);
            g.drawString(font, label, tx + 14 + (48 - font.width(label)) / 2, ty + 3, i == open ? INK : 0xFF4A3622, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        if (my >= y + 8 && my < y + 22) {
            if (mx >= x + 10 && mx < x + 74) { predatorTab = false; setTying(false); return true; }
            if (mx >= x + 84 && mx < x + 148) { predatorTab = true; setTying(false); return true; }
            if (mx >= x + 158 && mx < x + 222) { setTying(true); return true; }
        }
        if (tying) {   // §tying: the canvas takes the click; the hook picker below it still works
            if (canvas.click(x, y, mx, my, button, () -> {
                if (canvas.canTie(menu))
                    com.riverfishing.network.ModNetwork.toServer(new com.riverfishing.network.TieLurePacket(canvas.design.clone()));
            })) return true;
            if (hookPickerClick(mx, my)) return true;
            return super.mouseClicked(mx, my, button);
        }
        List<TackleForm> forms = tabForms();
        for (int i = 0; i < forms.size(); i++) {
            int cx = x + GRID_X + (i % COLS) * CELL;
            int cy = y + GRID_Y + (i / COLS) * CELL;
            if (mx >= cx && mx < cx + 20 && my >= cy && my < cy + 20) {
                clickButton(forms.get(i).ordinal());
                return true;
            }
        }
        // Advanced toggle.
        if (my >= y + advY() - 2 && my < y + advY() + 10 && mx >= x + GRID_X && mx < x + GRID_X + 110) {
            advanced = !advanced;
            return true;
        }
        if (advanced) {
            // Slider: press starts a DRAG (round-4 feedback: click-only was fiddly).
            if (menu.form().rig && my >= y + advY() + 19 && my < y + advY() + 36
                    && mx >= x + TRACK_X - 4 && mx < x + TRACK_X + TRACK_W + 5) {
                draggingLeader = true;
                pendingLeader = leaderAt(mx);
                return true;
            }
            if (!menu.form().rig && my >= y + advY() + 23 && my < y + advY() + 34) {
                for (int i = 0; i < 3; i++) {
                    int bx = x + TRACK_X + i * 38;
                    if (mx >= bx && mx < bx + 36) {
                        clickButton(400 + i);
                        return true;
                    }
                }
            }
        }
        // Weight stepper.
        int rx = x + TEXT_X;
        if (my >= y + GRID_Y + 14 && my < y + GRID_Y + 28 && mx >= rx && mx < rx + 90) {
            TackleForm f = menu.form();
            int cur = currentWeightIdx();
            int next = mx < rx + 45 ? Math.max(0, cur - 1) : Math.min(f.weights.length - 1, cur + 1);
            clickButton(100 + next);
            return true;
        }
        // Hook picker (§hook-pick): ONE hit box over both arrows and the icon between them, split down
        // the middle. The arrows say which side does what; aiming at the icon itself still works, which
        // is what a player does when the thing they want to change is the thing they are looking at.
        if (my >= y + HOOK_Y && my < y + HOOK_Y + HOOK_BTN_H
                && mx >= x + HOOK_DOWN_X && mx < x + HOOK_UP_X + HOOK_BTN_W) {
            int cur = menu.hookIdx();
            int next = mx < x + HOOK_X + 8 ? Math.max(0, cur - 1)
                    : Math.min(TackleForm.HOOK_SIZES.length - 1, cur + 1);
            clickButton(500 + next);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tying && canvas.drag(leftPos, topPos, mx, my)) return true;
        if (draggingLeader) {
            pendingLeader = leaderAt(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        canvas.release();
        if (draggingLeader) {
            draggingLeader = false;
            clickButton(200 + pendingLeader);
            pendingLeader = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private int leaderAt(double mx) {
        int cm = (int) Math.round(5 + (mx - (leftPos + TRACK_X)) / (double) TRACK_W * 95);
        return Math.max(5, Math.min(100, cm));
    }

    private int currentWeightIdx() {
        TackleForm f = menu.form();
        int grams = menu.weightGrams();
        for (int i = 0; i < f.weights.length; i++) {
            if (f.weights[i] == grams) return i;
        }
        return 0;
    }

    private void clickButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        renderTooltip(g, mouseX, mouseY);
        if (tying) { canvas.paletteTooltip(g, font, leftPos, topPos, mouseX, mouseY, menu); return; }
        // Hover names for the form grid — the icon alone shouldn't be a guessing game.
        List<TackleForm> forms = tabForms();
        for (int i = 0; i < forms.size(); i++) {
            int cx = leftPos + GRID_X + (i % COLS) * CELL;
            int cy = topPos + GRID_Y + (i / COLS) * CELL;
            if (mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20) {
                g.renderTooltip(font, new ItemStack(forms.get(i).item()).getHoverName(), mouseX, mouseY);
            }
        }
    }
}
