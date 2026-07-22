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
 * §tackle-station (0.6.0): two tabs (peaceful / predator), a 3x3 form grid, a weight stepper, the
 * material slots and a stonecutter-style result. Code-drawn panels (journal style) — no texture.
 */
public class TackleStationScreen extends AbstractContainerScreen<TackleStationMenu> {
    private static final int GRID_X = 14, GRID_Y = 30, CELL = 22;
    private boolean predatorTab;

    public TackleStationScreen(TackleStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 230;
        this.inventoryLabelY = -1000; // labels drawn by hand
    }

    private List<TackleForm> tabForms() {
        List<TackleForm> out = new ArrayList<>();
        for (TackleForm f : TackleForm.values()) {
            if (f.predatorTab == predatorTab) out.add(f);
        }
        return out;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0242018);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF3a3227);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF57493a);

        // Tabs.
        drawTab(g, x + 10, y + 8, !predatorTab, I18n.get("screen.riverfishing.tackle_station.tab_peaceful"));
        drawTab(g, x + 84, y + 8, predatorTab, I18n.get("screen.riverfishing.tackle_station.tab_predator"));

        // Form grid 3x3: item icons, selected cell highlighted.
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

        // Right column: selected form name, weight stepper, cost + cast hint.
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

        // Slot wells (menu slot coords are menu-local).
        int[][] wells = {{14, 118}, {38, 118}, {62, 118}, {86, 118}, {148, 118}};
        for (int[] w : wells) {
            g.fill(x + w[0] - 1, y + w[1] - 1, x + w[0] + 17, y + w[1] + 17, 0xFF2a241c);
        }
        String[] labels = {"screen.riverfishing.tackle_station.hook", "screen.riverfishing.tackle_station.iron",
                "screen.riverfishing.tackle_station.string", "screen.riverfishing.tackle_station.dye"};
        for (int i = 0; i < labels.length; i++) {
            g.drawString(font, I18n.get(labels[i]), x + wells[i][0] - 1, y + 108, 0xFF9a8d78, false);
        }
        // Player inventory wells.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                g.fill(x + 19 + col * 18, y + 147 + row * 18, x + 37 + col * 18, y + 165 + row * 18, 0xFF2a241c);
            }
        }
        for (int col = 0; col < 9; col++) {
            g.fill(x + 19 + col * 18, y + 205, x + 37 + col * 18, y + 223, 0xFF2a241c);
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
        // Tabs.
        if (my >= y + 8 && my < y + 22) {
            if (mx >= x + 10 && mx < x + 74) { predatorTab = false; return true; }
            if (mx >= x + 84 && mx < x + 148) { predatorTab = true; return true; }
        }
        // Form grid.
        List<TackleForm> forms = tabForms();
        for (int i = 0; i < forms.size(); i++) {
            int cx = x + GRID_X + (i % 3) * CELL;
            int cy = y + GRID_Y + (i / 3) * CELL;
            if (mx >= cx && mx < cx + 20 && my >= cy && my < cy + 20) {
                clickButton(forms.get(i).ordinal());
                return true;
            }
        }
        // Weight stepper: click left half = previous, right half = next.
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
    }
}
