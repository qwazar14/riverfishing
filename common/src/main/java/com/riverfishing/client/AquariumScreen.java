package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.engine.Calendar;
import com.riverfishing.engine.Season;
import com.riverfishing.menu.AquariumMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * §aquarium-window (0.9.0): the tank's window. Everything it says comes from the ten synced ints of the
 * contract (docs/design/breeding-api.md, Layer 4) — the screen never looks at the block entity, so it
 * reads the same on a server as in singleplayer.
 */
public class AquariumScreen extends AbstractContainerScreen<AquariumMenu> {
    private static final ResourceLocation BG = RiverFishing.id("textures/gui/aquarium.png");
    private static final String K = "screen.riverfishing.aquarium.";
    /**
     * Ordinals of {@code AquariumBreeding.Status} (stream B), spelled out because that class is
     * package-private to the block package; the contract fixes this order.
     */
    private static final String[] STATUS = { "empty", "no_pair", "not_mature", "out_of_season", "hungry",
            "bad_water", "spawning", "roe_ready", "incubating", "fry_ready", "busy" };
    private static final int SPAWNING = 6, ROE_READY = 7, INCUBATING = 8, FRY_READY = 9;
    /** §scale-genes: the carp varieties data(10) indexes, spelled out for the same reason STATUS is. */
    // §koi-metal: the window used to keep its own four-name list, so a koi pair in the tank showed
    // nothing at all. One list now, the breeding rules' own, and it cannot drift again.
    private static final String[] VARIETIES = com.riverfishing.block.AquariumBreeding.VARIETIES;
    private static final String[] SLOT_LABEL = { "fish", "fish", "fish", "fish", "fish", "fish",
            "food", "groundbait", "water", "result", "modules", "modules" };

    // Geometry shared with tools/gen_aquarium_gui.py — change both or the frames drift off the slots.
    private static final int ARROW_X = 102, ARROW_Y = 34, ARROW_W = 20, ARROW_H = 8;
    private static final int BAR_X = 55, BAR_Y = 70, BAR_W = 112, BAR_H = 10;
    private static final int TEXT_W = 160;
    private static final int COL_TEXT = 0xFFD6E8E6, COL_DIM = 0xFF8FB2AE, COL_WARN = 0xFFF0C070;

    public AquariumScreen(AquariumMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.titleLabelY = -1000;      // the title row is composed below (water, fish count)
        this.inventoryLabelY = -1000;  // the status text needs that room
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);   // §1.20.1: the dim overlay is the screen's own call here, not super.render's
        super.render(g, mouseX, mouseY, pt);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight);

        int status = Math.floorMod(menu.data(0), STATUS.length);
        // Progress arrow: spawn day/3, incubation day/total, full when something is waiting in the result.
        float progress = status == SPAWNING ? menu.data(1) / 3f
                : status == INCUBATING ? menu.data(2) / (float) Math.max(1, menu.data(3))
                : status == ROE_READY || status == FRY_READY ? 1f : 0f;
        int lit = Math.round(Math.min(1f, progress) * ARROW_W);
        if (lit > 0) g.blit(BG, x + ARROW_X, y + ARROW_Y, 176, 0, lit, ARROW_H);

        // Feed bar: how much of the current day of feeding is left (the contract clamps it to one day).
        int ticks = Math.max(0, Math.min(24000, menu.data(4)));
        int fill = ticks * BAR_W / 24000;
        if (fill > 0) g.blit(BG, x + BAR_X, y + BAR_Y, 176, ARROW_H, fill, BAR_H);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int status = Math.floorMod(menu.data(0), STATUS.length);

        // Title row: "Aquarium · water: 80% · fish: 4".
        String head = title.getString() + " · " + I18n.get(K + "label.water") + ": " + menu.data(5) + "% · "
                + I18n.get(K + "label.fish") + ": " + menu.data(8);
        g.drawString(font, font.plainSubstrByWidth(head, TEXT_W), 8, 6, COL_TEXT, false);

        // Feed countdown, centred in the bar, as mm:ss of game ticks.
        int secs = Math.max(0, menu.data(4)) / 20;
        String feed = I18n.get(K + "feed", String.format("%d:%02d", secs / 60, secs % 60));
        feed = font.plainSubstrByWidth(feed, BAR_W - 4);
        g.drawString(font, feed, BAR_X + (BAR_W - font.width(feed)) / 2, BAR_Y + 1,
                menu.data(4) <= 0 ? COL_WARN : COL_TEXT, false);

        // Under the bar: the run in progress, else the clutch the current pair would give.
        String run = status == SPAWNING ? I18n.get(K + "spawning", menu.data(1))
                : status == INCUBATING ? I18n.get(K + "incubating", menu.data(2), menu.data(3))
                : menu.data(9) > 0 ? I18n.get(K + "clutch", menu.data(9)) : "";
        // §scale-genes: whose clutch it is — "Leather × Leather" is the whole explanation of a clutch a
        // quarter short, and of why the mirrors keep breeding true.
        String pair = variety(menu.data(10) & 255), sire = variety(menu.data(10) >> 8);
        if (!pair.isEmpty()) run = (run.isEmpty() ? "" : run + " · ") + pair + " × " + sire;
        if (!run.isEmpty()) g.drawString(font, font.plainSubstrByWidth(run, TEXT_W), 8, 88, COL_DIM, false);

        // Status line, at most two lines; a third would run into the window line.
        int ly = 100, lines = 0;
        // §aq-fix: out_of_season names the window — the one status with an argument.
        Component statusText = status == 3 && menu.data(6) >= 0
                ? Component.translatable(K + "status." + STATUS[status], Calendar.name(
                        Season.values()[Math.floorMod(menu.data(6), Season.values().length)],
                        menu.data(7) < 0 ? null : Calendar.Sub.values()[Math.floorMod(menu.data(7), Calendar.Sub.values().length)]))
                : Component.translatable(K + "status." + STATUS[status]);
        for (FormattedCharSequence line : font.split(statusText, TEXT_W)) {
            if (lines++ == 2) break;
            g.drawString(font, line, 8, ly, COL_TEXT, false);
            ly += 11;
        }

        // The spawning window of the fish in the tank — meaningless with no fish in it.
        if (menu.data(8) > 0) {
            Season season = Season.values()[Math.floorMod(menu.data(6), Season.values().length)];
            Calendar.Sub sub = menu.data(7) < 0 ? null
                    : Calendar.Sub.values()[Math.floorMod(menu.data(7), Calendar.Sub.values().length)];
            String window = I18n.get(K + "window", Calendar.name(season, sub).getString());
            g.drawString(font, font.plainSubstrByWidth(window, TEXT_W), 8, 123,
                    status == 3 ? COL_WARN : COL_DIM, false); // 3 = OUT_OF_SEASON
        }
    }

    /** §scale-genes: one byte of data(10) as the variety's own name, or "" when the fish is no carp. */
    private static String variety(int nibble) {
        return nibble < 1 || nibble > VARIETIES.length ? ""
                : I18n.get("variety.riverfishing." + VARIETIES[nibble - 1]);
    }

    /** An empty tank slot names itself on hover — the window has no room for labels beside 3×2 fish. */
    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < AquariumMenu.INV_START) {
            g.renderTooltip(font, Component.translatable(K + "label." + SLOT_LABEL[hoveredSlot.index]), mouseX, mouseY);
        }
    }
}
