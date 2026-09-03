package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.engine.Calendar;
import com.riverfishing.engine.Season;
import com.riverfishing.menu.AquariumMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * §aquarium-window (0.9.0): the tank's window. Everything it says comes from the ten synced ints of the
 * contract (docs/design/breeding-api.md, Layer 4) — the screen never looks at the block entity, so it
 * reads the same on a server as in singleplayer.
 *
 * <p>§26.1: drawn through the extract/submit model like TackleStationScreen — renderBg + renderLabels
 * are one extractBackground in absolute coordinates (nothing here overlaps a slot), and the empty-slot
 * hover name goes through extractRenderState's setTooltipForNextFrame.
 */
public class AquariumScreen extends AbstractContainerScreen<AquariumMenu> {
    private static final Identifier BG = RiverFishing.id("textures/gui/aquarium.png");
    private static final int TEX = 256;
    private static final String K = "screen.riverfishing.aquarium.";
    /**
     * Ordinals of {@code AquariumBreeding.Status} (stream B), spelled out because that class is
     * package-private to the block package; the contract fixes this order.
     */
    private static final String[] STATUS = { "empty", "no_pair", "not_mature", "out_of_season", "hungry",
            "bad_water", "spawning", "roe_ready", "incubating", "fry_ready", "busy" };
    private static final int SPAWNING = 6, ROE_READY = 7, INCUBATING = 8, FRY_READY = 9;
    private static final String[] SLOT_LABEL = { "fish", "fish", "fish", "fish", "fish", "fish",
            "food", "groundbait", "water", "result", "modules", "modules" };

    // Geometry shared with tools/gen_aquarium_gui.py — change both or the frames drift off the slots.
    private static final int ARROW_X = 102, ARROW_Y = 34, ARROW_W = 20, ARROW_H = 8;
    private static final int BAR_X = 55, BAR_Y = 70, BAR_W = 112, BAR_H = 10;
    private static final int TEXT_W = 160;
    private static final int COL_TEXT = 0xFFD6E8E6, COL_DIM = 0xFF8FB2AE, COL_WARN = 0xFFF0C070;

    public AquariumScreen(AquariumMenu menu, Inventory inv, Component title) {
        // §26.1: imageWidth/imageHeight are final now — the size goes through the super ctor.
        super(menu, inv, title, 176, 222);
        this.titleLabelY = -1000;      // the title row is composed below (water, fish count)
        this.inventoryLabelY = -1000;  // the status text needs that room
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);
        int x = leftPos, y = topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, BG, x, y, 0f, 0f, imageWidth, imageHeight, TEX, TEX);

        int status = Math.floorMod(menu.data(0), STATUS.length);
        // Progress arrow: spawn day/3, incubation day/total, full when something is waiting in the result.
        float progress = status == SPAWNING ? menu.data(1) / 3f
                : status == INCUBATING ? menu.data(2) / (float) Math.max(1, menu.data(3))
                : status == ROE_READY || status == FRY_READY ? 1f : 0f;
        int lit = Math.round(Math.min(1f, progress) * ARROW_W);
        if (lit > 0) g.blit(RenderPipelines.GUI_TEXTURED, BG, x + ARROW_X, y + ARROW_Y, 176f, 0f, lit, ARROW_H, TEX, TEX);

        // Feed bar: how much of the current day of feeding is left (the contract clamps it to one day).
        int ticks = Math.max(0, Math.min(24000, menu.data(4)));
        int fill = ticks * BAR_W / 24000;
        if (fill > 0) g.blit(RenderPipelines.GUI_TEXTURED, BG, x + BAR_X, y + BAR_Y, 176f, (float) ARROW_H, fill, BAR_H, TEX, TEX);

        // Title row: "Aquarium · water: 80% · fish: 4".
        String head = title.getString() + " · " + I18n.get(K + "label.water") + ": " + menu.data(5) + "% · "
                + I18n.get(K + "label.fish") + ": " + menu.data(8);
        g.text(font, font.plainSubstrByWidth(head, TEXT_W), x + 8, y + 6, COL_TEXT, false);

        // Feed countdown, centred in the bar, as mm:ss of game ticks.
        int secs = Math.max(0, menu.data(4)) / 20;
        String feed = I18n.get(K + "feed", String.format("%d:%02d", secs / 60, secs % 60));
        feed = font.plainSubstrByWidth(feed, BAR_W - 4);
        g.text(font, feed, x + BAR_X + (BAR_W - font.width(feed)) / 2, y + BAR_Y + 1,
                menu.data(4) <= 0 ? COL_WARN : COL_TEXT, false);

        // Under the bar: the run in progress, else the clutch the current pair would give.
        String run = status == SPAWNING ? I18n.get(K + "spawning", menu.data(1))
                : status == INCUBATING ? I18n.get(K + "incubating", menu.data(2), menu.data(3))
                : menu.data(9) > 0 ? I18n.get(K + "clutch", menu.data(9)) : "";
        if (!run.isEmpty()) g.text(font, font.plainSubstrByWidth(run, TEXT_W), x + 8, y + 88, COL_DIM, false);

        // Status line, at most two lines; a third would run into the window line.
        int ly = y + 100, lines = 0;
        // §aq-fix: out_of_season names the window — the one status with an argument.
        Component statusText = status == 3 && menu.data(6) >= 0
                ? Component.translatable(K + "status." + STATUS[status], Calendar.name(
                        Season.values()[Math.floorMod(menu.data(6), Season.values().length)],
                        menu.data(7) < 0 ? null : Calendar.Sub.values()[Math.floorMod(menu.data(7), Calendar.Sub.values().length)]))
                : Component.translatable(K + "status." + STATUS[status]);
        for (FormattedCharSequence line : font.split(statusText, TEXT_W)) {
            if (lines++ == 2) break;
            g.text(font, line, x + 8, ly, COL_TEXT, false);
            ly += 11;
        }

        // The spawning window of the fish in the tank — meaningless with no fish in it.
        if (menu.data(8) > 0) {
            Season season = Season.values()[Math.floorMod(menu.data(6), Season.values().length)];
            Calendar.Sub sub = menu.data(7) < 0 ? null
                    : Calendar.Sub.values()[Math.floorMod(menu.data(7), Calendar.Sub.values().length)];
            String window = I18n.get(K + "window", Calendar.name(season, sub).getString());
            g.text(font, font.plainSubstrByWidth(window, TEXT_W), x + 8, y + 123,
                    status == 3 ? COL_WARN : COL_DIM, false); // 3 = OUT_OF_SEASON
        }
    }

    /** An empty tank slot names itself on hover — the window has no room for labels beside 3×2 fish. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractRenderState(g, mouseX, mouseY, pt);
        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            int i = menu.slots.indexOf(hoveredSlot);
            if (i >= 0 && i < AquariumMenu.INV_START) {
                g.setTooltipForNextFrame(font, Component.translatable(K + "label." + SLOT_LABEL[i]), mouseX, mouseY);
            }
        }
    }
}

// §ported26
