package com.riverfishing.client;

import com.riverfishing.fish.FishGroup;
import com.riverfishing.network.CullListPacket;
import com.riverfishing.network.CullPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * §cull (0.7.0), §stock-tool + §fish-groups (0.8.0): what lives in this water, and what could.
 *
 * <p>Two clicks, never one. The first selects and turns the row into a question; only the second does
 * anything. A creative tool that empties a lake on a mis-click is a tool people lose worlds to, and the
 * confirmation costs a quarter of a second.
 *
 * <p>The screen used to list only what lived here, which made it exactly one page long and needed no
 * organising. It now lists all seventy-nine species, so it has a column of FAMILIES down the left and one
 * family's fish on the right. A flat seventy-nine-row list would have been six pages of alphabet — the
 * family is the axis a person actually thinks along.
 *
 * <p>The first category is not a family: it is this water. It holds what was living here when the screen
 * opened and it keeps holding it — a fish you cull stays in the list, struck through, so the undo is
 * where you last saw the fish rather than somewhere you have to go looking for it.
 */
public class CullScreen extends Screen {
    private static final int ROW = 14;
    /** Widest the list may get; the real count also has to fit the window (see {@link #perPage}). */
    private static final int MAX_ROWS = 14;
    private static final int BTN_W = 22;
    private static final int CAT_W = 108;
    private static final int LIST_W = 216;
    private static final int GAP = 6;

    /** Row tint per state, in {@link CullListPacket}'s order: absent, here, culled. */
    private static final int[] STRIP = {0xFF6A6A6A, 0xFF4CAF50, 0xFFC0392B};
    private static final int[] TEXT = {0xFFA8A8A8, 0xFFFFFFFF, 0xFF8A8A8A};

    private final BlockPos water;
    private final List<Identifier> species;
    private final byte[] state;
    /** Category keys: {@code null} for "this water", otherwise a {@link FishGroup} id. */
    private final List<String> cats = new ArrayList<>();
    /** Per category, the indices into {@link #species} it shows — fixed once, so nothing moves under the cursor. */
    private final List<List<Integer>> rows = new ArrayList<>();

    private int cat;
    private int page;
    private int selected = -1;

    private CullScreen(CullListPacket p) {
        super(Component.translatable("gui.riverfishing.cull_title"));
        this.water = p.water;
        this.species = p.species;
        this.state = p.state.clone();

        // Category 0 — this water: what lives here AND what an operator took out of here. Culled has to
        // be in it. Building this from HERE alone meant a fish you removed was missing from the first
        // thing the screen shows you the next time you opened it, which is exactly the "I removed it and
        // now it is gone" the whole change was supposed to end — the row was only findable by knowing
        // which family to click.
        List<Integer> here = new ArrayList<>();
        for (int i = 0; i < species.size(); i++) {
            if (state[i] == CullListPacket.HERE || state[i] == CullListPacket.CULLED) here.add(i);
        }
        cats.add(null);
        rows.add(here);

        for (String g : FishGroup.ORDER) {
            List<Integer> mine = new ArrayList<>();
            for (int i = 0; i < species.size(); i++) {
                if (g.equals(p.group.get(i))) mine.add(i);
            }
            if (mine.isEmpty()) continue;   // a group no species is filed under is furniture
            mine.sort((a, b) -> name(a).getString().compareToIgnoreCase(name(b).getString()));
            cats.add(g);
            rows.add(mine);
        }
    }

    public static void open(CullListPacket p) {
        //? if <26.2 {
        Minecraft.getInstance().setScreen(new CullScreen(p));
        //?} else {
        /*Minecraft.getInstance().setScreenAndShow(new CullScreen(p));
        *///?}
    }

    private Component name(int i) {
        return Component.translatable("item.riverfishing." + species.get(i).getPath());
    }

    private List<Integer> view() {
        return rows.get(cat);
    }

    /** How many of a category's fish are in this water right now — recounted, so culling shows up live. */
    private int liveCount(int c) {
        int n = 0;
        for (int i : rows.get(c)) {
            if (state[i] == CullListPacket.HERE) n++;
        }
        return n;
    }

    private int listTop() {
        return 40;
    }

    private int leftX() {
        return width / 2 - (CAT_W + GAP + LIST_W) / 2;
    }

    private int listX() {
        return leftX() + CAT_W + GAP;
    }

    /**
     * How many rows this window can hold. A big lake can list thirty species and the first cut showed a
     * fixed twelve with a scroll nobody found — the rest of the water simply looked absent.
     */
    private int perPage() {
        int room = (height - listTop() - 60) / ROW;
        return Math.max(4, Math.min(MAX_ROWS, room));
    }

    private int pages() {
        return Math.max(1, (view().size() + perPage() - 1) / perPage());
    }

    private int first() {
        return page * perPage();
    }

    private int rowsShown() {
        return Math.max(0, Math.min(perPage(), view().size() - first()));
    }

    private int pagerY() {
        return listTop() + perPage() * ROW + 6;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        g.centeredText(font, title, cx, 14, 0xFFE8E4D0);
        g.centeredText(font, Component.translatable("gui.riverfishing.cull_where",
                water.getX(), water.getY(), water.getZ()).withStyle(ChatFormatting.DARK_GRAY), cx, 26, 0xFF808080);

        renderCategories(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);

        g.centeredText(font, Component.translatable("gui.riverfishing.cull_scope")
                .withStyle(ChatFormatting.GRAY), cx, height - 26, 0xFF9A9A9A);
    }

    /** The family column: name on the left, "in this water / in the family" on the right. */
    private void renderCategories(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = leftX(), top = listTop();
        g.fill(x - 2, top - 2, x + CAT_W + 2, top + cats.size() * ROW + 2, 0xC0000000);
        for (int c = 0; c < cats.size(); c++) {
            int y = top + c * ROW;
            boolean hover = mouseX >= x && mouseX < x + CAT_W && mouseY >= y && mouseY < y + ROW;
            if (c == cat) {
                g.fill(x, y, x + CAT_W, y + ROW, 0x80305070);
            } else if (hover) {
                g.fill(x, y, x + CAT_W, y + ROW, 0x40FFFFFF);
            }
            Component label = cats.get(c) == null
                    ? Component.translatable("gui.riverfishing.cull_cat_here")
                    : Component.translatable(FishGroup.nameKey(cats.get(c)));
            String count = liveCount(c) + "/" + rows.get(c).size();
            g.text(font, label, x + 5, y + 3, c == cat ? 0xFFFFFFFF : 0xFFC0C0C0, false);
            g.text(font, Component.literal(count).withStyle(ChatFormatting.DARK_GRAY),
                    x + CAT_W - font.width(count) - 5, y + 3, 0xFF707070, false);
        }
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = listX(), top = listTop();
        int shown = rowsShown();
        g.fill(x - 2, top - 2, x + LIST_W + 2, top + Math.max(1, shown) * ROW + 2, 0xC0000000);
        if (shown == 0) {
            g.text(font, Component.translatable("gui.riverfishing.cull_none_here")
                    .withStyle(ChatFormatting.DARK_GRAY), x + 8, top + 3, 0xFF707070, false);
            return;
        }
        for (int i = 0; i < shown; i++) {
            int idx = view().get(i + first());
            int y = top + i * ROW;
            byte st = state[idx];
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW;
            boolean sel = i + first() == selected;
            if (sel) {
                g.fill(x, y, x + LIST_W, y + ROW, st == CullListPacket.HERE ? 0x80C03020 : 0x8020A040);
            } else if (hover) {
                g.fill(x, y, x + LIST_W, y + ROW, 0x40FFFFFF);
            }
            g.fill(x, y, x + 3, y + ROW, STRIP[st]);
            Component line = st == CullListPacket.CULLED
                    ? name(idx).copy().withStyle(ChatFormatting.STRIKETHROUGH)
                    : name(idx).copy();
            g.text(font, line, x + 9, y + 3, TEXT[st], false);
            if (sel) {
                Component ask = Component.translatable(st == CullListPacket.HERE
                        ? "gui.riverfishing.cull_confirm" : "gui.riverfishing.cull_confirm_add");
                g.text(font, ask.copy().withStyle(ChatFormatting.YELLOW),
                        x + LIST_W - font.width(ask) - 6, y + 3, 0xFFFFFF55, false);
            }
        }
        // Pager: arrows either side of "page / of", greyed at the ends. Only drawn when there IS a
        // second page — one screenful of fish should not grow furniture it does not need.
        if (pages() > 1) {
            int py = pagerY();
            drawArrow(g, x, py, "<", page > 0, mouseX, mouseY);
            drawArrow(g, x + LIST_W - BTN_W, py, ">", page < pages() - 1, mouseX, mouseY);
            g.centeredText(font, Component.literal((first() + 1) + "–"
                    + (first() + shown) + "  /  " + view().size()), x + LIST_W / 2, py + 3, 0xFFB0B0B0);
        }
    }

    /** One pager arrow. Dead ones are drawn dark and do not answer a click. */
    private void drawArrow(GuiGraphicsExtractor g, int x, int y, String glyph, boolean live, int mouseX, int mouseY) {
        boolean hover = live && mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + 12;
        g.fill(x, y, x + BTN_W, y + 12, live ? (hover ? 0xFF4A5A6A : 0xFF2A3A4A) : 0xFF1A1A1A);
        g.centeredText(font, Component.literal(glyph), x + BTN_W / 2, y + 2,
                live ? 0xFFE8E4D0 : 0xFF555555);
    }

    private boolean hitArrow(double mx, double my, int x, int y) {
        return mx >= x && mx < x + BTN_W && my >= y && my < y + 12;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int top = listTop(), cxCat = leftX(), x = listX();
        for (int c = 0; c < cats.size(); c++) {
            int y = top + c * ROW;
            if (mx < cxCat || mx >= cxCat + CAT_W || my < y || my >= y + ROW) continue;
            cat = c;
            page = 0;
            selected = -1;
            return true;
        }
        if (pages() > 1) {
            int py = pagerY();
            if (hitArrow(mx, my, x, py) && page > 0) {
                page--;
                selected = -1;
                return true;
            }
            if (hitArrow(mx, my, x + LIST_W - BTN_W, py) && page < pages() - 1) {
                page++;
                selected = -1;
                return true;
            }
        }
        for (int i = 0; i < rowsShown(); i++) {
            int y = top + i * ROW;
            if (mx < x || mx >= x + LIST_W || my < y || my >= y + ROW) continue;
            int row = i + first();
            int idx = view().get(row);
            if (selected == row) {
                // Second click on the same row: do it.
                boolean remove = state[idx] == CullListPacket.HERE;
                ModNetwork.toServer(new CullPacket(water, species.get(idx), remove));
                state[idx] = remove ? CullListPacket.CULLED : CullListPacket.HERE;
                selected = -1;
            } else {
                selected = row;
            }
            return true;
        }
        selected = -1;   // clicking anywhere else cancels the pending confirmation
        return super.mouseClicked(event, doubleClick);
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
     * §journal-blur, again: the menu background runs a gaussian blur over the world behind a screen, which
     * reads as a washed-out mess behind an opaque panel. Same no-op the journal uses.
     */
    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor g) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
