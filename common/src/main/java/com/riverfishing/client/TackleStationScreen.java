package com.riverfishing.client;

import com.riverfishing.menu.TackleStationMenu;
import com.riverfishing.registry.ModItems;
import com.riverfishing.tackle.TackleForm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * §tackle-station (0.6.0, playtest round 4): two tabs, a 3x3 form grid with hover names, a weight
 * stepper, a labeled fine-tuning drawer (draggable hook-link slider for rigs / balance buttons for
 * lures), ghost-hinted material slots with live requirement counts, and a stonecutter-style result.
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
    private static final int TRACK_X = 116, TRACK_W = 70;
    /** §hook-pick: the well the hook SLOT used to occupy, now the size picker. */
    private static final int HOOK_X = 38;
    /** The two arrow buttons either side of it. The material wells moved right to make room. */
    private static final int HOOK_DOWN_X = 22, HOOK_UP_X = 59, HOOK_BTN_W = 12;
    private static final int HOOK_Y = 149, HOOK_BTN_H = 18;
    /** Where the right-hand column starts, and how much room it has — both derived, never guessed. */
    private static final int TEXT_X = GRID_X + COLS * CELL + 10;
    private boolean predatorTab;
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
                : font.split(net.minecraft.network.chat.Component.literal(text), imageWidth - TEXT_X - 8)) {
            g.drawString(font, line, x, y, colour, false);
            y += 11;
        }
        return y + 3;
    }

    private int shownLeader() {
        return pendingLeader >= 0 ? pendingLeader : menu.leaderCm();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0242018);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF3a3227);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF57493a);

        drawTab(g, x + 10, y + 8, !predatorTab, I18n.get("screen.riverfishing.tackle_station.tab_peaceful"));
        drawTab(g, x + 84, y + 8, predatorTab, I18n.get("screen.riverfishing.tackle_station.tab_predator"));

        // Form grid.
        List<TackleForm> forms = tabForms();
        TackleForm sel = menu.form();
        for (int i = 0; i < forms.size(); i++) {
            int cx = x + GRID_X + (i % COLS) * CELL;
            int cy = y + GRID_Y + (i / COLS) * CELL;
            boolean isSel = forms.get(i) == sel;
            g.fill(cx, cy, cx + 20, cy + 20, isSel ? 0xFFC8A050 : 0xFF2a241c);
            g.fill(cx + 1, cy + 1, cx + 19, cy + 19, isSel ? 0xFF6e5a3a : 0xFF463b2d);
            g.renderItem(new ItemStack(forms.get(i).item()), cx + 2, cy + 2);
        }

        // Right column: name, weight stepper, cast hint, cost.
        int rx = x + TEXT_X;
        int avail = imageWidth - TEXT_X - 8;
        // The name is clipped rather than wrapped: the two lines under it are at fixed offsets because
        // the weight stepper is clickable, and a name that pushed them down would move its hit box.
        g.drawString(font, font.plainSubstrByWidth(
                new ItemStack(sel.item()).getHoverName().getString(), avail), rx, y + GRID_Y, 0xFFEDE4D0, false);
        int grams = menu.weightGrams();
        g.drawString(font, "< " + I18n.get("screen.riverfishing.tackle_station.weight", grams) + " >",
                rx, y + GRID_Y + 16, 0xFFFFD97A, false);
        int ly = y + GRID_Y + 32;
        ly = flow(g, I18n.get("screen.riverfishing.tackle_station.cast_hint",
                TackleForm.castHintBlocks(grams)), rx, ly, 0xFFB8AE9A);
        ly = flow(g, I18n.get("screen.riverfishing.tackle_station.cost",
                menu.ironNeeded(), sel.stringNeeded()), rx, ly, 0xFFB8AE9A);
        if (sel.dyeable) {
            flow(g, I18n.get("screen.riverfishing.tackle_station.dye_hint"), rx, ly, 0xFF8FB08A);
        }

        // §tackle-adv drawer: its own labeled section, nothing overlaps.
        g.drawString(font, (advanced ? "▼ " : "► ")
                        + I18n.get("screen.riverfishing.tackle_station.advanced"),
                x + GRID_X, y + advY(), 0xFFB8AE9A, false);
        if (advanced) {
            if (sel.rig) {
                // Hook link (distance hook → anchor point) — rigs only.
                g.drawString(font, I18n.get("screen.riverfishing.tackle_station.hook_link_label"),
                        x + GRID_X, y + advY() + 15, 0xFF9a8d78, false);
                int tx = x + TRACK_X, ty = y + advY() + 13;
                g.fill(tx, ty + 3, tx + TRACK_W, ty + 6, 0xFF2a241c);
                int hx = tx + (int) ((shownLeader() - 5) / 95.0 * TRACK_W);
                g.fill(hx - 2, ty, hx + 3, ty + 9, 0xFFFFD97A);
                g.drawString(font, shownLeader() + " " + I18n.get("screen.riverfishing.tackle_station.cm"),
                        tx + TRACK_W + 6, ty, 0xFFEDE4D0, false);
            } else {
                // Balance — lures only.
                g.drawString(font, I18n.get("screen.riverfishing.tackle_station.balance_label"),
                        x + GRID_X, y + advY() + 15, 0xFF9a8d78, false);
                String[] keys = {"balance_nose", "balance_center", "balance_tail"};
                for (int i = 0; i < 3; i++) {
                    int bx = x + TRACK_X + i * 38;
                    boolean on = menu.balancePos() == i;
                    g.fill(bx, y + advY() + 12, bx + 36, y + advY() + 23, on ? 0xFF6e5a3a : 0xFF2a241c);
                    g.drawCenteredString(font, I18n.get("screen.riverfishing.tackle_station." + keys[i]),
                            bx + 18, y + advY() + 14, on ? 0xFFFFE6B0 : 0xFF9a8d78);
                }
            }
        }

        // Material wells + ghost hints + live requirement counts (red when short).
        int[][] wells = {{HOOK_X, 150}, {76, 150}, {100, 150}, {124, 150}, {176, 150}};
        for (int[] w : wells) {
            g.fill(x + w[0] - 1, y + w[1] - 1, x + w[0] + 17, y + w[1] + 17, 0xFF2a241c);
        }
        ItemStack[] ghosts = {
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
                new ItemStack(net.minecraft.world.item.Items.STRING),
                new ItemStack(net.minecraft.world.item.Items.RED_DYE)};
        int[] need = {menu.ironNeeded(), sel.stringNeeded(), 0};
        // §hook-pick: the first well is no longer a slot — it is the hook PICKER, with an arrow button
        // either side of it. The iron cost below already includes whatever size it is showing.
        g.fill(x + HOOK_X - 1, y + 149, x + HOOK_X + 17, y + 167, 0xFF463b2d);
        g.renderItem(new ItemStack(ModItems.HOOKS.get(menu.hookIdx()).get()), x + HOOK_X, y + 150);
        // Dim at the ends of the ladder: a button that cannot do anything should not look
        // like one that can — #16 is the smallest hook there is and #1 the biggest.
        drawHookArrow(g, x + HOOK_DOWN_X, y + HOOK_Y, "◄", menu.hookIdx() > 0);
        drawHookArrow(g, x + HOOK_UP_X, y + HOOK_Y, "►",
                menu.hookIdx() < TackleForm.HOOK_SIZES.length - 1);
        g.drawCenteredString(font, "#" + menu.hookSize(), x + HOOK_X + 8, y + 169, 0xFFFFD97A);

        for (int i = 0; i < ghosts.length; i++) {
            ItemStack in = menu.getSlot(i).getItem();
            int w0 = wells[i + 1][0];        // well 0 is the picker, not a slot
            if (in.isEmpty()) {
                g.renderFakeItem(ghosts[i], x + w0, y + wells[i + 1][1]);
                g.fill(net.minecraft.client.renderer.RenderType.guiGhostRecipeOverlay(),
                        x + w0, y + wells[i + 1][1], x + w0 + 16, y + wells[i + 1][1] + 16, 0x8857493a);
            }
            if (need[i] > 0) {
                boolean short_ = in.getCount() < need[i];
                g.drawCenteredString(font, "×" + need[i], x + w0 + 8, y + 169,
                        short_ ? 0xFFE06050 : 0xFF9a8d78);
            }
        }
        g.drawString(font, "→", x + 158, y + 154, 0xFFB8AE9A, false);

        // Player inventory wells.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                g.fill(x + 42 + col * 18, y + 179 + row * 18, x + 60 + col * 18, y + 197 + row * 18, 0xFF2a241c);
            }
        }
        for (int col = 0; col < 9; col++) {
            g.fill(x + 42 + col * 18, y + 239, x + 60 + col * 18, y + 257, 0xFF2a241c);
        }
    }

    /** One of the two hook-size buttons: same look as the balance buttons, dim when stuck. */
    private void drawHookArrow(GuiGraphics g, int bx, int by, String glyph, boolean live) {
        g.fill(bx, by, bx + HOOK_BTN_W, by + HOOK_BTN_H, live ? 0xFF6e5a3a : 0xFF2a241c);
        g.drawCenteredString(font, glyph, bx + HOOK_BTN_W / 2, by + 5, live ? 0xFFFFE6B0 : 0xFF6b6257);
    }

    private void drawTab(GuiGraphics g, int x, int y, boolean active, String label) {
        int w = 64;
        g.fill(x, y, x + w, y + 14, active ? 0xFF6e5a3a : 0xFF2a241c);
        g.drawCenteredString(font, label, x + w / 2, y + 3, active ? 0xFFFFE6B0 : 0xFF9a8d78);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        if (my >= y + 8 && my < y + 22) {
            if (mx >= x + 10 && mx < x + 74) { predatorTab = false; return true; }
            if (mx >= x + 84 && mx < x + 148) { predatorTab = true; return true; }
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
            if (menu.form().rig && my >= y + advY() + 9 && my < y + advY() + 26
                    && mx >= x + TRACK_X - 4 && mx < x + TRACK_X + TRACK_W + 5) {
                draggingLeader = true;
                pendingLeader = leaderAt(mx);
                return true;
            }
            if (!menu.form().rig && my >= y + advY() + 12 && my < y + advY() + 23) {
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
        if (draggingLeader) {
            pendingLeader = leaderAt(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
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
