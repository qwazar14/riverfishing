package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * §finder-screen: the sounding, drawn.
 *
 * <p>All of this was already computed and then printed into chat as six lines of names — which is the
 * complaint. Nothing new is measured here; it is the same sounding with a shape.
 *
 * <p>The left half is the water in SECTION, because that is the one thing a list cannot say: a fish
 * sits at the depth it actually lives at, against the real depth of this spot, so "too shallow for a
 * pike-perch" is something you SEE rather than read. The right half is the species, and clicking one
 * opens what the mod knows about it here and now — including, for a fish that cannot bite, the gate
 * that stops it. That diagnosis existed already and was shown to nobody: it was written for the
 * creative-only admin probe.
 */
public class FinderScreen extends Screen {
    private static final ResourceLocation SLOT = RiverFishing.id("textures/gui/slot.png");

    private static final int W = 440, H = 252;
    /** The sonar window inside the panel — a dark instrument face let into the parchment. */
    private static final int VIEW_X = 10, VIEW_Y = 30, VIEW_W = 236, VIEW_H = 150;
    private static final int LIST_X = 254, LIST_W = 176;
    private static final int ROW = 13;

    // The instrument face. Deep water blue-green, the way every sounder ever made has looked.
    private static final int FACE = 0xFF0B1E22, GRID = 0x2240E0B0, SURFACE = 0xFF7FE9D0;
    private static final int FLOOR = 0xFF6B5A38, FLOOR_TOP = 0xFF8A7448;
    private static final int TRACE = 0xFF40E0B0;

    private final CompoundTag data;
    private final List<CompoundTag> here = new ArrayList<>();
    private final List<CompoundTag> gone = new ArrayList<>();

    private int left, top;
    private int scroll;
    /** The species whose page is open, or null for the list. */
    private String detail;
    /**
     * §sounding: the instrument face shows the section, or the BED FROM ABOVE once you have sounded
     * some. Two views of one water — the section says how deep, the map says where.
     */
    private boolean mapView;
    /** What the cursor is over on the section this frame — drawn last, above the whole panel. */
    private List<net.minecraft.util.FormattedCharSequence> hover;
    private boolean detailBlocked;

    public FinderScreen(CompoundTag data) {
        super(Component.translatable("screen.riverfishing.finder"));
        this.data = data == null ? new CompoundTag() : data;
        ListTag h = this.data.getList("here", 10);
        for (int i = 0; i < h.size(); i++) here.add(h.getCompound(i));
        ListTag g = this.data.getList("gone", 10);
        for (int i = 0; i < g.size(); i++) gone.add(g.getCompound(i));
        // Best first, so the top of the list is the answer to "what do I put on".
        here.sort((a, b) -> Float.compare(b.getFloat("e"), a.getFloat("e")));
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private CompoundTag water() {
        return data.getCompound("water");
    }

    // ---- the section ---------------------------------------------------------------------------

    /**
     * The water column, to scale. The floor sits at the measured depth and the ruler counts real
     * blocks, so a fish drawn below the floor line is a fish that cannot be here — which is the whole
     * argument the picture makes.
     */
    private int depthScale() {
        // Never squash the column flat in a puddle: six blocks of scale is the floor of the ruler.
        return Math.max(6, water().getInt("depth"));
    }

    private int yForDepth(double metres) {
        return VIEW_Y + top + (int) Math.round(metres / depthScale() * (VIEW_H - 16)) + 8;
    }

    private void renderSection(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        g.fill(x0, y0, x0 + VIEW_W, y0 + VIEW_H, FACE);
        g.fill(x0, y0, x0 + VIEW_W, y0 + 1, 0xFF2B4A44);
        g.fill(x0, y0 + VIEW_H - 1, x0 + VIEW_W, y0 + VIEW_H, 0xFF2B4A44);

        int scale = depthScale();
        // Depth ruler: a line and a number every metre while they fit, every other one when they do not.
        int step = scale <= 8 ? 1 : (scale + 7) / 8;
        for (int d = 0; d <= scale; d += step) {
            int y = yForDepth(d);
            g.fill(x0 + 1, y, x0 + VIEW_W - 1, y + 1, GRID);
            g.drawString(this.font, String.valueOf(d), x0 + 3, y - 4, 0x8840E0B0, false);
        }

        // Surface and floor.
        g.fill(x0 + 1, yForDepth(0), x0 + VIEW_W - 1, yForDepth(0) + 1, SURFACE);
        int floorY = yForDepth(water().getInt("depth"));
        g.fill(x0 + 1, floorY, x0 + VIEW_W - 1, floorY + 2, FLOOR_TOP);
        g.fill(x0 + 1, floorY + 2, x0 + VIEW_W - 1, y0 + VIEW_H - 1, FLOOR);

        // The fish, at the depth they live at. Spread across the face so they do not stack into one
        // column; the horizontal axis carries no meaning and is not pretended to.
        int n = Math.max(1, here.size());
        for (int i = 0; i < here.size(); i++) {
            CompoundTag t = here.get(i);
            double band = bandMid(t);
            int fx = x0 + 26 + (int) ((VIEW_W - 56) * ((i + 0.5) / n));
            int fy = Mth.clamp(yForDepth(band), y0 + 6, floorY - 10);
            drawFish(g, t.getString("sp"), fx, fy - 8);
            if (t.getBoolean("sig")) {
                // A signature species of this water wears a mark: this is a tench lake.
                g.drawString(this.font, "★", fx + 12, fy - 12, 0xFFE8B430, false);
            }
            if (mouseX >= fx && mouseX < fx + 16 && mouseY >= fy - 8 && mouseY < fy + 8) {
                hover = List.of(fishName(t.getString("sp")).getVisualOrderText(),
                        Component.translatable("finder.riverfishing.band",
                                t.getInt("dmin"), t.getInt("dmax")).getVisualOrderText());
            }
        }

        // A ping line down the middle: the device is on.
        g.fill(x0 + VIEW_W / 2, y0 + 1, x0 + VIEW_W / 2 + 1, y0 + VIEW_H - 1, 0x1840E0B0);
        g.drawString(this.font, Component.translatable("finder.riverfishing.section"),
                x0 + 4, y0 + VIEW_H - 11, 0x9940E0B0, false);
    }

    /**
     * §sounding: the bed you have measured, from above. Deeper reads darker, unmeasured reads as
     * nothing at all — an honest map has holes in it until somebody casts across them, and the holes
     * are the reason to keep casting.
     */
    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        g.fill(x0, y0, x0 + VIEW_W, y0 + VIEW_H, FACE);
        ListTag map = data.getList("map", 10);
        if (map.isEmpty()) {
            for (var seq : this.font.split(
                    Component.translatable("finder.riverfishing.unsounded"), VIEW_W - 20)) {
                g.drawString(this.font, seq, x0 + 10, y0 + VIEW_H / 2 - 6, 0x9940E0B0, false);
                y0 += 11;
            }
            return;
        }
        final int REACH = 24, CELL = 3;
        int cx = x0 + VIEW_W / 2, cy = y0 + VIEW_H / 2;
        int deepest = 1;
        for (int i = 0; i < map.size(); i++) deepest = Math.max(deepest, map.getCompound(i).getInt("d"));

        for (int i = 0; i < map.size(); i++) {
            CompoundTag t = map.getCompound(i);
            int px = cx + t.getInt("x") * CELL, py = cy + t.getInt("z") * CELL;
            if (px < x0 || px >= x0 + VIEW_W - CELL || py < y0 || py >= y0 + VIEW_H - CELL) continue;
            // One ramp from shallow to deepest measured, so the shape of the bed is what you read
            // rather than an absolute depth nobody can eyeball anyway.
            float f = t.getInt("d") / (float) deepest;
            int shade = 0xFF000000
                    | ((int) (0x20 + 0x10 * (1 - f)) << 16)
                    | ((int) (0x50 + 0x80 * (1 - f)) << 8)
                    | (int) (0x60 + 0x70 * (1 - f));
            g.fill(px, py, px + CELL, py + CELL, shade);
            if (t.contains("s")) {
                g.fill(px - 1, py - 1, px + CELL + 1, py + CELL + 1, 0xFFE8B430);
                if (mouseX >= px - 2 && mouseX < px + CELL + 2 && mouseY >= py - 2 && mouseY < py + CELL + 2) {
                    hover = List.of(Component.translatable("spot.riverfishing." + t.getString("s"))
                                    .getVisualOrderText(),
                            Component.translatable("finder.riverfishing.metres", t.getInt("d"))
                                    .getVisualOrderText());
                }
            }
        }
        // Where you are standing, so the map has a you-are-here.
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF6060);
        g.drawString(this.font, Component.translatable("finder.riverfishing.map"),
                x0 + 4, y0 + VIEW_H - 11, 0x9940E0B0, false);
    }

    /** The one control on the face: which of the two views it is showing. */
    private void renderViewTab(GuiGraphics g, int mouseX, int mouseY) {
        Component label = Component.translatable(mapView
                ? "finder.riverfishing.to_section" : "finder.riverfishing.to_map");
        int w = this.font.width(label) + 10;
        int x = left + VIEW_X + VIEW_W - w, y = top + VIEW_Y - 13;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
        g.fill(x, y, x + w, y + 12, hov ? 0xFF8A7038 : 0xFF63512F);
        g.drawString(this.font, label, x + 5, y + 2, 0xFFEDE2C6, false);
    }

    private boolean clickedViewTab(double mx, double my) {
        Component label = Component.translatable(mapView
                ? "finder.riverfishing.to_section" : "finder.riverfishing.to_map");
        int w = this.font.width(label) + 10;
        int x = left + VIEW_X + VIEW_W - w, y = top + VIEW_Y - 13;
        return mx >= x && mx < x + w && my >= y && my < y + 12;
    }

    /** The middle of a species' depth band, which is where it is drawn. */
    private static double bandMid(CompoundTag t) {
        return (t.getInt("dmin") + t.getInt("dmax")) / 2.0;
    }

    private static Component fishName(String sp) {
        return Component.translatable("fish.riverfishing." + sp);
    }

    /** Fish are builtin/entity items the GUI shades dark; blit the texture, the way the journal does. */
    private void drawFish(GuiGraphics g, String sp, int x, int y) {
        g.blit(RiverFishing.id("textures/item/fish/" + sp + ".png"), x, y, 16, 16, 0f, 0f, 16, 16, 16, 16);
    }

    // ---- the list ------------------------------------------------------------------------------

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + LIST_X, y = top + VIEW_Y;
        g.drawString(this.font, Component.translatable("finder.riverfishing.biting", here.size()),
                x, y, 0xFFB0842C, false);
        y += 13;
        int bottom = top + VIEW_Y + VIEW_H;
        int rows = (bottom - y) / ROW;
        List<CompoundTag> all = new ArrayList<>(here);
        all.addAll(gone);
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - rows));

        for (int i = scroll; i < all.size() && i < scroll + rows; i++) {
            CompoundTag t = all.get(i);
            boolean blocked = i >= here.size();
            boolean hov = mouseX >= x - 2 && mouseX < x + LIST_W && mouseY >= y - 2 && mouseY < y + 11;
            if (hov) g.fill(x - 2, y - 2, x + LIST_W, y + 11, 0x22000000);
            // A bar for how well the water suits it — the number itself is engine noise.
            if (!blocked) {
                int wBar = (int) (18 * Mth.clamp(t.getFloat("e"), 0f, 1f));
                g.fill(x, y + 2, x + 18, y + 8, 0x33000000);
                g.fill(x, y + 2, x + wBar, y + 8, 0xFF3FA34A);
            } else {
                g.drawString(this.font, "×", x + 6, y, 0xFF9A4A3C, false);
            }
            String label = this.font.plainSubstrByWidth(fishName(t.getString("sp")).getString(), LIST_W - 30);
            g.drawString(this.font, label, x + 24, y, blocked ? GuiStyle.GHOST : GuiStyle.TEXT, false);
            y += ROW;
        }
        if (all.size() > rows) {
            g.drawString(this.font, Component.translatable("finder.riverfishing.more", all.size() - rows),
                    x, bottom - 10, GuiStyle.GHOST, false);
        }
    }

    // ---- one species ---------------------------------------------------------------------------

    private void renderDetail(GuiGraphics g) {
        CompoundTag t = find(detail);
        if (t == null) { detail = null; return; }
        int x = left + LIST_X, y = top + VIEW_Y;
        drawFish(g, detail, x, y - 2);
        g.drawString(this.font, fishName(detail), x + 20, y + 2, GuiStyle.TEXT, false);
        y += 20;

        y = line(g, x, y, "finder.riverfishing.depth_band",
                Component.literal(t.getInt("dmin") + "–" + t.getInt("dmax") + " m"));
        y = line(g, x, y, "finder.riverfishing.level", Component.literal(String.valueOf(t.getInt("lvl"))));

        if (!detailBlocked) {
            y = line(g, x, y, "finder.riverfishing.bait",
                    Component.translatable("item.riverfishing." + t.getString("bait")));
            y = line(g, x, y, "finder.riverfishing.stock",
                    Component.literal(t.getInt("stock") + "%")
                            .append(t.getBoolean("res") ? Component.empty()
                                    : Component.translatable("finder.riverfishing.temp")));
            if (t.getBoolean("sig")) {
                g.drawString(this.font, Component.translatable("finder.riverfishing.is_signature"),
                        x, y, 0xFFB05A00, false);
                y += 12;
            }
        } else {
            // The one thing this tool can say that nothing else does.
            y += 4;
            Component why = Component.translatable("finder.riverfishing.gate."
                    + t.getString("why").replaceAll("\\(.*\\)", ""));
            for (var seq : this.font.split(
                    Component.translatable("finder.riverfishing.blocked", why), LIST_W)) {
                g.drawString(this.font, seq, x, y, 0xFF9A4A3C, false);
                y += 11;
            }
        }
        g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                x, top + VIEW_Y + VIEW_H - 10, GuiStyle.GHOST, false);
    }

    private int line(GuiGraphics g, int x, int y, String key, Component value) {
        g.drawString(this.font, Component.translatable(key), x, y, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, value, x + 74, y, GuiStyle.TEXT, false);
        return y + 12;
    }

    private CompoundTag find(String sp) {
        for (CompoundTag t : here) if (t.getString("sp").equals(sp)) return t;
        for (CompoundTag t : gone) if (t.getString("sp").equals(sp)) return t;
        return null;
    }

    // ---- the instrument bar --------------------------------------------------------------------

    private void renderBar(GuiGraphics g) {
        CompoundTag w = water();
        int x = left + 10, y = top + VIEW_Y + VIEW_H + 8;
        g.fill(x, y - 4, left + W - 10, y - 3, 0x33000000);

        String outlook = w.getString("outlook");
        int colour = switch (outlook) {
            case "great" -> 0xFF2E7D32;
            case "good" -> 0xFF3FA34A;
            case "fair" -> 0xFFB0842C;
            case "poor" -> 0xFF9A4A3C;
            default -> 0xFF7A2A22;
        };
        int trend = w.getInt("trend");
        String arrow = trend < 0 ? "↓" : trend > 0 ? "↑" : "→";

        y = pair(g, x, y, "finder.riverfishing.water",
                Component.translatable("water.riverfishing." + w.getString("type")));
        y = pair(g, x, y, "finder.riverfishing.depth",
                Component.translatable("finder.riverfishing.metres", w.getInt("depth")));
        y = pair(g, x, y, "finder.riverfishing.width",
                Component.translatable("finder.riverfishing.metres", Math.round(w.getFloat("width"))));

        int x2 = left + 230;
        int y2 = top + VIEW_Y + VIEW_H + 8;
        if (!w.getString("season").isEmpty()) {
            y2 = pair2(g, x2, y2, "finder.riverfishing.season",
                    Component.translatable("season.riverfishing." + w.getString("season")));
        }
        y2 = pair2(g, x2, y2, "finder.riverfishing.weather",
                Component.translatable("weather.riverfishing." + w.getString("weather")));
        g.drawString(this.font, Component.translatable("finder.riverfishing.pressure",
                        w.getInt("hpa"), arrow), x2, y2, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, Component.translatable("finder.riverfishing.outlook." + outlook),
                x2 + 96, y2, colour, false);

        if (w.getBoolean("frenzy")) {
            g.drawString(this.font, Component.translatable("finder.riverfishing.frenzy"),
                    x, top + H - 16, 0xFFB05A00, false);
        }
    }

    private int pair(GuiGraphics g, int x, int y, String key, Component value) {
        g.drawString(this.font, Component.translatable(key), x, y, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, value, x + 62, y, GuiStyle.TEXT, false);
        return y + 12;
    }

    private int pair2(GuiGraphics g, int x, int y, String key, Component value) {
        g.drawString(this.font, Component.translatable(key), x, y, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, value, x + 96, y, GuiStyle.TEXT, false);
        return y + 12;
    }

    // ---- frame ---------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        GuiStyle.panel(g, left, top, W, H);
        g.drawString(this.font, Component.translatable("screen.riverfishing.finder"),
                left + 10, top + 6, GuiStyle.TEXT, false);

        if (water().isEmpty()) {
            g.drawString(this.font, Component.translatable("message.riverfishing.no_water"),
                    left + 10, top + 30, 0xFF9A4A3C, false);
            return;
        }
        hover = null;
        if (mapView) renderMap(g, mouseX, mouseY);
        else renderSection(g, mouseX, mouseY);
        renderViewTab(g, mouseX, mouseY);
        if (detail == null) renderList(g, mouseX, mouseY);
        else renderDetail(g);
        renderBar(g);
        if (hover != null) g.renderTooltip(this.font, hover, mouseX, mouseY);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // §journal-blur: the panel is opaque, and 1.21's gaussian pass reads as a washed-out page.
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (clickedViewTab(mx, my)) {
            mapView = !mapView;
            return true;
        }
        if (detail != null) {
            detail = null;
            return true;
        }
        int x = left + LIST_X, y = top + VIEW_Y + 13;
        int bottom = top + VIEW_Y + VIEW_H;
        int rows = (bottom - y) / ROW;
        List<CompoundTag> all = new ArrayList<>(here);
        all.addAll(gone);
        for (int i = scroll; i < all.size() && i < scroll + rows; i++) {
            if (mx >= x - 2 && mx < x + LIST_W && my >= y - 2 && my < y + 11) {
                detail = all.get(i).getString("sp");
                detailBlocked = i >= here.size();
                return true;
            }
            y += ROW;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy);
        return true;
    }
}
