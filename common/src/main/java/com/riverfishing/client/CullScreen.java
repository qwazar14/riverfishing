package com.riverfishing.client;

import com.riverfishing.network.CullListPacket;
import com.riverfishing.network.CullPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * §cull (0.7.0): pick the species to take out of this water.
 *
 * <p>Two clicks, never one. The first selects and turns the row into a question; only the second does
 * anything. A creative tool that empties a lake on a mis-click is a tool people lose worlds to, and the
 * confirmation costs a quarter of a second.
 *
 * <p>Rows for species already removed are shown struck through and put them BACK when confirmed, so the
 * screen is the undo as well as the do.
 */
public class CullScreen extends Screen {
    private static final int ROW = 14;
    /** Widest the list may get; the real count also has to fit the window (see {@link #perPage}). */
    private static final int MAX_ROWS = 14;
    private static final int BTN_W = 22;

    private final BlockPos water;
    private final List<ResourceLocation> species;
    private final List<Boolean> culled;

    private int page;
    private int selected = -1;

    private CullScreen(CullListPacket p) {
        super(Component.translatable("gui.riverfishing.cull_title"));
        this.water = p.water;
        this.species = p.species;
        this.culled = new java.util.ArrayList<>(p.culled);
    }

    public static void open(CullListPacket p) {
        Minecraft.getInstance().setScreen(new CullScreen(p));
    }

    private int listTop() {
        return 40;
    }

    /**
     * How many rows this window can hold. A big lake can list thirty species and the first cut showed a
     * fixed twelve with a scroll nobody found — the rest of the water simply looked absent.
     */
    private int perPage() {
        int room = (height - listTop() - 46) / ROW;
        return Math.max(4, Math.min(MAX_ROWS, room));
    }

    private int pages() {
        return Math.max(1, (species.size() + perPage() - 1) / perPage());
    }

    private int first() {
        return page * perPage();
    }

    private int rowsShown() {
        return Math.min(perPage(), species.size() - first());
    }

    private int pagerY() {
        return listTop() + perPage() * ROW + 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        g.drawCenteredString(font, title, cx, 14, 0xFFE8E4D0);
        g.drawCenteredString(font, Component.translatable("gui.riverfishing.cull_where",
                water.getX(), water.getY(), water.getZ()).withStyle(ChatFormatting.DARK_GRAY), cx, 26, 0xFF808080);

        int top = listTop();
        int w = 220;
        g.fill(cx - w / 2 - 2, top - 2, cx + w / 2 + 2, top + rowsShown() * ROW + 2, 0xC0000000);
        for (int i = 0; i < rowsShown(); i++) {
            int idx = i + first();
            if (idx >= species.size()) break;
            int y = top + i * ROW;
            boolean hover = mouseX >= cx - w / 2 && mouseX <= cx + w / 2 && mouseY >= y && mouseY < y + ROW;
            boolean sel = idx == selected;
            if (sel) {
                g.fill(cx - w / 2, y, cx + w / 2, y + ROW, 0x80C03020);
            } else if (hover) {
                g.fill(cx - w / 2, y, cx + w / 2, y + ROW, 0x40FFFFFF);
            }
            Component name = Component.translatable("item.riverfishing." + species.get(idx).getPath());
            Component line = culled.get(idx)
                    ? name.copy().withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_GRAY)
                    : name.copy().withStyle(ChatFormatting.WHITE);
            g.drawString(font, line, cx - w / 2 + 6, y + 3, 0xFFFFFFFF, false);
            if (sel) {
                Component ask = Component.translatable(culled.get(idx)
                        ? "gui.riverfishing.cull_confirm_back" : "gui.riverfishing.cull_confirm");
                g.drawString(font, ask.copy().withStyle(ChatFormatting.YELLOW),
                        cx + w / 2 - font.width(ask) - 6, y + 3, 0xFFFFFF55, false);
            }
        }
        // Pager: arrows either side of "page / of", greyed at the ends. Only drawn when there IS a
        // second page — one screenful of fish should not grow furniture it does not need.
        if (pages() > 1) {
            int py = pagerY();
            drawArrow(g, cx - w / 2, py, "<", page > 0, mouseX, mouseY);
            drawArrow(g, cx + w / 2 - BTN_W, py, ">", page < pages() - 1, mouseX, mouseY);
            g.drawCenteredString(font, Component.literal((first() + 1) + "–"
                    + (first() + rowsShown()) + "  /  " + species.size()), cx, py + 3, 0xFFB0B0B0);
        }
        g.drawCenteredString(font, Component.translatable("gui.riverfishing.cull_scope")
                .withStyle(ChatFormatting.GRAY), cx, height - 28, 0xFF9A9A9A);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** One pager arrow. Dead ones are drawn dark and do not answer a click. */
    private void drawArrow(GuiGraphics g, int x, int y, String glyph, boolean live, int mouseX, int mouseY) {
        boolean hover = live && mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + 12;
        g.fill(x, y, x + BTN_W, y + 12, live ? (hover ? 0xFF4A5A6A : 0xFF2A3A4A) : 0xFF1A1A1A);
        g.drawCenteredString(font, Component.literal(glyph), x + BTN_W / 2, y + 2,
                live ? 0xFFE8E4D0 : 0xFF555555);
    }

    private boolean hitArrow(double mx, double my, int x, int y) {
        return mx >= x && mx < x + BTN_W && my >= y && my < y + 12;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = width / 2, w = 220, top = listTop();
        if (pages() > 1) {
            int py = pagerY();
            if (hitArrow(mx, my, cx - w / 2, py) && page > 0) {
                page--;
                selected = -1;
                return true;
            }
            if (hitArrow(mx, my, cx + w / 2 - BTN_W, py) && page < pages() - 1) {
                page++;
                selected = -1;
                return true;
            }
        }
        for (int i = 0; i < rowsShown(); i++) {
            int idx = i + first();
            if (idx >= species.size()) break;
            int y = top + i * ROW;
            if (mx < cx - w / 2.0 || mx > cx + w / 2.0 || my < y || my >= y + ROW) continue;
            if (selected == idx) {
                // Second click on the same row: do it.
                boolean remove = !culled.get(idx);
                ModNetwork.toServer(new CullPacket(water, species.get(idx), remove));
                culled.set(idx, remove);
                selected = -1;
            } else {
                selected = idx;
            }
            return true;
        }
        selected = -1;   // clicking anywhere else cancels the pending confirmation
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // The wheel turns pages too — the scroll it used to do went a row at a time and made the pager
        // and the wheel disagree about where you were.
        if (pages() > 1) {
            page = Math.max(0, Math.min(pages() - 1, page - (int) Math.signum(dy)));
            selected = -1;
        }
        return true;
    }

    /**
     * §journal-blur, again: 1.21's {@code renderBackground} runs a gaussian blur over the world behind a
     * screen, which reads as a washed-out mess behind an opaque panel. Same no-op the journal uses.
     */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
