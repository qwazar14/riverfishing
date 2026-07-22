package com.riverfishing.client;

import com.riverfishing.menu.TackleStationMenu;
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
    private static final int ADV_Y = 100;           // toggle row; drawer occupies ADV_Y+12..+34
    private static final int TRACK_X = 78, TRACK_W = 70;
    private boolean predatorTab;
    private boolean advanced;
    private boolean draggingLeader;
    private int pendingLeader = -1;                 // local value while dragging; -1 = use menu's

    public TackleStationScreen(TackleStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 252;
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
            int cx = x + GRID_X + (i % 3) * CELL;
            int cy = y + GRID_Y + (i / 3) * CELL;
            boolean isSel = forms.get(i) == sel;
            g.fill(cx, cy, cx + 20, cy + 20, isSel ? 0xFFC8A050 : 0xFF2a241c);
            g.fill(cx + 1, cy + 1, cx + 19, cy + 19, isSel ? 0xFF6e5a3a : 0xFF463b2d);
            g.renderItem(new ItemStack(forms.get(i).item()), cx + 2, cy + 2);
        }

        // Right column: name, weight stepper, cast hint, cost.
        int rx = x + GRID_X + 3 * CELL + 10;
        g.drawString(font, new ItemStack(sel.item()).getHoverName(), rx, y + GRID_Y, 0xFFEDE4D0, false);
        int grams = menu.weightGrams();
        g.drawString(font, "< " + I18n.get("screen.riverfishing.tackle_station.weight", grams) + " >",
                rx, y + GRID_Y + 16, 0xFFFFD97A, false);
        g.drawString(font, I18n.get("screen.riverfishing.tackle_station.cast_hint",
                TackleForm.castHintBlocks(grams)), rx, y + GRID_Y + 30, 0xFFB8AE9A, false);
        g.drawString(font, I18n.get("screen.riverfishing.tackle_station.cost",
                sel.ironFor(grams), sel.stringNeeded()), rx, y + GRID_Y + 44, 0xFFB8AE9A, false);
        if (sel.dyeable) {
            g.drawString(font, I18n.get("screen.riverfishing.tackle_station.dye_hint"),
                    rx, y + GRID_Y + 58, 0xFF8FB08A, false);
        }

        // §tackle-adv drawer: its own labeled section, nothing overlaps.
        g.drawString(font, (advanced ? "▼ " : "► ")
                        + I18n.get("screen.riverfishing.tackle_station.advanced"),
                x + GRID_X, y + ADV_Y, 0xFFB8AE9A, false);
        if (advanced) {
            if (sel.rig) {
                // Hook link (distance hook → anchor point) — rigs only.
                g.drawString(font, I18n.get("screen.riverfishing.tackle_station.hook_link_label"),
                        x + GRID_X, y + ADV_Y + 15, 0xFF9a8d78, false);
                int tx = x + TRACK_X, ty = y + ADV_Y + 13;
                g.fill(tx, ty + 3, tx + TRACK_W, ty + 6, 0xFF2a241c);
                int hx = tx + (int) ((shownLeader() - 5) / 95.0 * TRACK_W);
                g.fill(hx - 2, ty, hx + 3, ty + 9, 0xFFFFD97A);
                g.drawString(font, shownLeader() + " " + I18n.get("screen.riverfishing.tackle_station.cm"),
                        tx + TRACK_W + 6, ty, 0xFFEDE4D0, false);
            } else {
                // Balance — lures only.
                g.drawString(font, I18n.get("screen.riverfishing.tackle_station.balance_label"),
                        x + GRID_X, y + ADV_Y + 15, 0xFF9a8d78, false);
                String[] keys = {"balance_nose", "balance_center", "balance_tail"};
                for (int i = 0; i < 3; i++) {
                    int bx = x + TRACK_X + i * 38;
                    boolean on = menu.balancePos() == i;
                    g.fill(bx, y + ADV_Y + 12, bx + 36, y + ADV_Y + 23, on ? 0xFF6e5a3a : 0xFF2a241c);
                    g.drawCenteredString(font, I18n.get("screen.riverfishing.tackle_station." + keys[i]),
                            bx + 18, y + ADV_Y + 14, on ? 0xFFFFE6B0 : 0xFF9a8d78);
                }
            }
        }

        // Material wells + ghost hints + live requirement counts (red when short).
        int[][] wells = {{14, 138}, {38, 138}, {62, 138}, {86, 138}, {152, 138}};
        for (int[] w : wells) {
            g.fill(x + w[0] - 1, y + w[1] - 1, x + w[0] + 17, y + w[1] + 17, 0xFF2a241c);
        }
        ItemStack[] ghosts = {
                new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(com.riverfishing.RiverFishing.id("hook_10"))),
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
                new ItemStack(net.minecraft.world.item.Items.STRING),
                new ItemStack(net.minecraft.world.item.Items.RED_DYE)};
        int[] need = {sel.hooksNeeded(), sel.ironFor(grams), sel.stringNeeded(), 0};
        for (int i = 0; i < ghosts.length; i++) {
            ItemStack in = menu.getSlot(i).getItem();
            if (in.isEmpty()) {
                g.renderFakeItem(ghosts[i], x + wells[i][0], y + wells[i][1]);
                g.fill(net.minecraft.client.renderer.RenderType.guiGhostRecipeOverlay(),
                        x + wells[i][0], y + wells[i][1], x + wells[i][0] + 16, y + wells[i][1] + 16, 0x8857493a);
            }
            if (need[i] > 0) {
                boolean short_ = in.getCount() < need[i];
                g.drawCenteredString(font, "×" + need[i], x + wells[i][0] + 8, y + 157,
                        short_ ? 0xFFE06050 : 0xFF9a8d78);
            }
        }
        g.drawString(font, "→", x + 134, y + 142, 0xFFB8AE9A, false);

        // Player inventory wells.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                g.fill(x + 19 + col * 18, y + 167 + row * 18, x + 37 + col * 18, y + 185 + row * 18, 0xFF2a241c);
            }
        }
        for (int col = 0; col < 9; col++) {
            g.fill(x + 19 + col * 18, y + 227, x + 37 + col * 18, y + 245, 0xFF2a241c);
        }
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
            int cx = x + GRID_X + (i % 3) * CELL;
            int cy = y + GRID_Y + (i / 3) * CELL;
            if (mx >= cx && mx < cx + 20 && my >= cy && my < cy + 20) {
                clickButton(forms.get(i).ordinal());
                return true;
            }
        }
        // Advanced toggle.
        if (my >= y + ADV_Y - 2 && my < y + ADV_Y + 10 && mx >= x + GRID_X && mx < x + GRID_X + 110) {
            advanced = !advanced;
            return true;
        }
        if (advanced) {
            // Slider: press starts a DRAG (round-4 feedback: click-only was fiddly).
            if (menu.form().rig && my >= y + ADV_Y + 9 && my < y + ADV_Y + 26
                    && mx >= x + TRACK_X - 4 && mx < x + TRACK_X + TRACK_W + 5) {
                draggingLeader = true;
                pendingLeader = leaderAt(mx);
                return true;
            }
            if (!menu.form().rig && my >= y + ADV_Y + 12 && my < y + ADV_Y + 23) {
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
        int rx = x + GRID_X + 3 * CELL + 10;
        if (my >= y + GRID_Y + 14 && my < y + GRID_Y + 28 && mx >= rx && mx < rx + 90) {
            TackleForm f = menu.form();
            int cur = currentWeightIdx();
            int next = mx < rx + 45 ? Math.max(0, cur - 1) : Math.min(f.weights.length - 1, cur + 1);
            clickButton(100 + next);
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
            int cx = leftPos + GRID_X + (i % 3) * CELL;
            int cy = topPos + GRID_Y + (i / 3) * CELL;
            if (mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20) {
                g.renderTooltip(font, new ItemStack(forms.get(i).item()).getHoverName(), mouseX, mouseY);
            }
        }
    }
}
