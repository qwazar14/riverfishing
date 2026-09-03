package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.fishing.FishingManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * §finder-screen: the sounding, drawn.
 *
 * <p>All of this was already computed and then printed into chat as six lines of names — which is the
 * complaint. Nothing new is measured here; it is the same sounding with a shape.
 *
 * <p>The left half is the water in SECTION along the line you are aiming down: the real bed, metre
 * by metre, coloured by what it is made of, with the surface over it. A fish is drawn where along
 * that bed its depth is actually met — a bream out where it gets deep, a bleak under the surface —
 * so "too shallow for a pike-perch" is something you SEE rather than read. The right half is the
 * species, and clicking one opens what the mod knows about it here and now, including, for a fish
 * that cannot bite, the gate that stops it. That diagnosis existed already and was shown to nobody:
 * it was written for the creative-only admin probe.
 *
 * <p>The face has a second view, the bed FROM ABOVE: the lake's shape, the bank, where you stand and
 * face, and whatever has been sounded — with holes in it until somebody casts across them.
 *
 * <p>§finder2: and a third, the WATER SAMPLE — everything measured that is not a fish (clarity, climate,
 * the season's third, oxygen, cover, the ecosystem, the farm ledger), and for fry, roe or a fish held
 * in the other hand, the release's gates with their numbers. The section's list is fish only again.
 */
public class FinderScreen extends Screen {
    private static final int W = 440, H = 252;
    /** The sonar window inside the panel — a dark instrument face let into the parchment. */
    private static final int VIEW_X = 10, VIEW_Y = 30, VIEW_W = 236, VIEW_H = 150;
    private static final int LIST_X = 254, LIST_W = 176;
    private static final int ROW = 13;

    /**
     * §fish-icons: drawn at 24 px, and at most ten of them. The textures are 256 px; at 16 px a
     * nearest-neighbour blit keeps one texel in sixteen and twenty-eight of them on one face were
     * mush. Bigger and fewer is the whole fix — the rest are in the list, where they belong.
     */
    private static final int ICON = 24, MAX_FISH = 10;

    // The instrument face. Deep water blue-green, the way every sounder ever made has looked.
    private static final int FACE = 0xFF0B1E22, GRID = 0x2240E0B0, SURFACE = 0xFF7FE9D0;
    private static final int LAND = 0xFF2E3A22, LAND_TOP = 0xFF4A6034;
    // §depth-map: the chart wears the section's own colours — one face, turned to look down. Water
    // from the face's dark through teal to deep navy, the bank the section's bank, gold for a mark.
    private static final int CHART_CONTOUR = 0x8840E0B0, CHART_YOU = 0xFFFF6060, CHART_MARK = 0xFFE8B430;
    private static final int[] CHART_BANDS = {
            0xFF163A44,   // unsounded water: the face's own dark
            0xFF3FB0A4, 0xFF2F8F8E, 0xFF246E78, 0xFF1B5264, 0xFF143C50, 0xFF0F2A3C};

    private final CompoundTag data;
    private final List<CompoundTag> here = new ArrayList<>();
    private final List<CompoundTag> gone = new ArrayList<>();

    private int left, top;
    private int scroll;
    /** The species whose page is open, or null for the list. */
    private String detail;
    private boolean detailBlocked;
    /** §finder2: the face shows the section, the bed from above, or the water sample. */
    private static final int SECTION = 0, CHART = 1, SAMPLE = 2;
    private int view = SECTION;
    /** §depth-map: the chart's centre in world blocks, and pixels per block. */
    private double mapCx, mapCz;
    private int zoom = 4;
    /**
     * §finder2: the chart's cells and marks are built when the block window, the zoom or the data
     * changes — not every frame. Dragging a sub-block keeps the grid; the pixels move, the colours do not.
     */
    private int chartStartX, chartStartZ, chartCols, chartRows, chartZoom = -1, chartVersion = -1;
    private int[] chartCells;
    private byte[] chartEdges;
    /** Marks inside the block window as {gx, gz, kind}, parallel to {@link #chartMarkKeys}. */
    private final List<int[]> chartMarks = new ArrayList<>();
    private final List<Long> chartMarkKeys = new ArrayList<>();
    private int sampleScroll;
    /** What the cursor is over on the face this frame — drawn last, above the whole panel. */
    private List<net.minecraft.util.FormattedCharSequence> hover;
    /** §section-click: where each fish was drawn this frame, {x, y, index into here}. */
    private final List<int[]> fishRects = new ArrayList<>();
    /** §arrow-target: where each mark was drawn this frame, {x, y}, parallel to {@link #markKeys}. */
    private final List<int[]> markRects = new ArrayList<>();
    private final List<Long> markKeys = new ArrayList<>();

    public FinderScreen(CompoundTag data) {
        super(Component.translatable("screen.riverfishing.finder"));
        this.data = data == null ? new CompoundTag() : data;
        ListTag h = this.data.getList("here", 10);
        for (int i = 0; i < h.size(); i++) here.add(h.getCompound(i));
        ListTag g = this.data.getList("gone", 10);
        for (int i = 0; i < g.size(); i++) gone.add(g.getCompound(i));
        // Best first, so the top of the list is the answer to "what do I put on".
        here.sort((a, b) -> Float.compare(b.getFloat("e"), a.getFloat("e")));
        // The chart opens on the water you just sounded.
        mapCx = water().getInt("x") + 0.5;
        mapCz = water().getInt("z") + 0.5;
        // §finder2: fry in the other hand means the question is "will they live here" — open on the answer.
        if (this.data.contains("suit")) view = SAMPLE;
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

    /** The bed along the aim, one metre a column; a negative is bank. Empty when the server sent none. */
    private byte[] profileDepth() {
        return data.getCompound("profile").getByteArray("d");
    }

    private byte[] profileBed() {
        return data.getCompound("profile").getByteArray("b");
    }

    /** The scale of the ruler: the deepest thing on the face, never less than six blocks. */
    private int depthScale() {
        int scale = Math.max(6, water().getInt("depth"));
        for (byte d : profileDepth()) scale = Math.max(scale, d);
        return scale;
    }

    private int yForDepth(double metres) {
        return VIEW_Y + top + (int) Math.round(metres / depthScale() * (VIEW_H - 18)) + 8;
    }

    /** Where metre {@code i} of the profile sits on the face. The ruler takes the first 22 px. */
    private int xForMetre(int i) {
        return left + VIEW_X + 22 + (int) Math.round((VIEW_W - 30) * (i / (double) (FishingManager.PROFILE_N - 1)));
    }

    private int metreWidth() {
        return Math.max(2, (VIEW_W - 30) / (FishingManager.PROFILE_N - 1) + 1);
    }

    private static int bedColour(int t) {
        return switch (t) {
            case 1 -> 0xFFC9B37A;   // sand
            case 2 -> 0xFF8C8C86;   // gravel
            case 3 -> 0xFF9AA3AD;   // clay
            case 4 -> 0xFF6B5A38;   // mud
            case 5 -> 0xFF55555A;   // rock
            default -> 0xFF6B5A38;
        };
    }

    private void renderSection(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        g.fill(x0, y0, x0 + VIEW_W, y0 + VIEW_H, FACE);

        int scale = depthScale();
        int step = scale <= 8 ? 1 : (scale + 7) / 8;
        for (int d = 0; d <= scale; d += step) {
            int y = yForDepth(d);
            g.fill(x0 + 1, y, x0 + VIEW_W - 1, y + 1, GRID);
            g.drawString(this.font, String.valueOf(d), x0 + 3, y - 4, 0x8840E0B0, false);
        }

        byte[] pd = profileDepth(), pb = profileBed();
        int floorBottom = y0 + VIEW_H - 1;
        int surfaceY = yForDepth(0);
        int mw = metreWidth();
        if (pd.length == 0) {
            // No profile (an old server): the flat floor at the one depth we have.
            int floorY = yForDepth(water().getInt("depth"));
            g.fill(x0 + 22, floorY, x0 + VIEW_W - 1, floorBottom, bedColour(water().getByte("bed")));
            g.fill(x0 + 22, surfaceY, x0 + VIEW_W - 1, surfaceY + 1, SURFACE);
        } else {
            for (int i = 0; i < pd.length; i++) {
                int x = xForMetre(i);
                if (pd[i] < 0) {
                    // Bank: solid ground from the surface line down, so the shore is a shore.
                    g.fill(x, surfaceY - 3, x + mw, floorBottom, LAND);
                    g.fill(x, surfaceY - 3, x + mw, surfaceY - 1, LAND_TOP);
                    continue;
                }
                int floorY = yForDepth(pd[i]);
                int c = bedColour(pb.length > i ? pb[i] : 0);
                g.fill(x, floorY, x + mw, floorBottom, c);
                g.fill(x, floorY, x + mw, floorY + 1, lighten(c));
                g.fill(x, surfaceY, x + mw, surfaceY + 1, SURFACE);
            }
        }

        // The fish, where along the bed their depth is met. Ten at most, at a size that reads.
        List<int[]> taken = new ArrayList<>();
        fishRects.clear();
        int shown = 0;
        for (int fi = 0; fi < here.size(); fi++) {
            CompoundTag t = here.get(fi);
            if (shown >= MAX_FISH) break;
            int[] at = placeFish(t, pd, taken);
            if (at == null) continue;
            taken.add(at);
            fishRects.add(new int[]{at[0], at[1], fi});
            shown++;
            drawFish(g, t.getString("sp"), at[0], at[1]);
            if (t.getBoolean("sig")) {
                // A signature species of this water wears a mark: this is a tench lake.
                g.drawString(this.font, "★", at[0] + ICON - 6, at[1] - 4, 0xFFE8B430, false);
            }
            if (mouseX >= at[0] && mouseX < at[0] + ICON && mouseY >= at[1] && mouseY < at[1] + ICON) {
                hover = List.of(fishName(t.getString("sp")).getVisualOrderText(),
                        Component.translatable("finder.riverfishing.band",
                                t.getInt("dmin"), t.getInt("dmax")).getVisualOrderText());
            }
        }
        if (here.size() > shown) {
            Component more = Component.translatable("finder.riverfishing.more_fish", here.size() - shown);
            g.drawString(this.font, more, x0 + VIEW_W - this.font.width(more) - 6, y0 + VIEW_H - 11, 0x9940E0B0, false);
        }

        // What the bed is made of, read off the profile — the legend a real sounder prints.
        g.drawString(this.font, bedLegend(pd, pb), x0 + 4, y0 + VIEW_H - 11, 0x9940E0B0, false);
    }

    /**
     * Where to draw this species: the first metre out where the bed is deep enough for it, at the
     * depth it holds — and then the icon is a 24 px RECTANGLE, and the whole rectangle has to sit in
     * the water. The first cut centred the icon on the depth and kept 0.4 m of clearance to the bed,
     * which at ten metres of scale is five pixels under a twelve-pixel half-icon: every bottom fish
     * was drawn standing in the mud, and a nudge sideways to dodge a neighbour put it into a
     * shallower column's bank. Now every candidate cell is checked against the bed under ITS OWN
     * width and lifted clear, and the surface caps it from above.
     *
     * @return {x, y} on the face, or null if there is no room left on the face for it
     */
    private int[] placeFish(CompoundTag t, byte[] pd, List<int[]> taken) {
        int dmin = t.getInt("dmin"), dmax = t.getInt("dmax");
        double mid = (dmin + dmax) / 2.0;
        int col = -1, deepest = -1, deepestD = -1;
        for (int i = 0; i < pd.length; i++) {
            if (pd[i] < 0) continue;
            if (pd[i] > deepestD) { deepestD = pd[i]; deepest = i; }
            if (col < 0 && pd[i] >= dmin) col = i;
        }
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        int wantX;
        if (pd.length == 0) {
            wantX = x0 + 30 + (taken.size() * (ICON + 6)) % (VIEW_W - 60);
        } else if (col >= 0) {
            wantX = xForMetre(col);
        } else {
            // Nowhere along this line is deep enough: it holds where the line is deepest, which is
            // the honest place to say "this fish wants more water than there is here".
            wantX = deepest < 0 ? x0 + 30 : xForMetre(deepest);
        }
        int wantY = yForDepth(Math.max(0.4, mid)) - ICON / 2;
        int surfaceY = yForDepth(0);

        // Nearest free cell: same spot, then along the bed, then a lane up or down.
        int[][] tries = {{0, 0}, {ICON + 2, 0}, {-(ICON + 2), 0}, {0, -(ICON + 2)}, {0, ICON + 2},
                {2 * (ICON + 2), 0}, {-2 * (ICON + 2), 0}, {ICON + 2, -(ICON + 2)}, {-(ICON + 2), -(ICON + 2)}};
        for (int[] d : tries) {
            int x = Mth.clamp(wantX + d[0], x0 + 24, x0 + VIEW_W - ICON - 2);
            // The bed under this icon's own width, and the icon lifted clear of it.
            int bedY = bedPixelUnder(x, x + ICON, pd);
            int y = Math.min(wantY + d[1], bedY - ICON - 1);
            y = Mth.clamp(y, surfaceY + 1, y0 + VIEW_H - ICON - 12);
            if (y + ICON > bedY) continue;          // no water column tall enough here
            boolean free = true;
            for (int[] o : taken) {
                if (Math.abs(o[0] - x) < ICON && Math.abs(o[1] - y) < ICON) { free = false; break; }
            }
            if (free) return new int[]{x, y};
        }
        return null;
    }

    /** The highest bed pixel across a horizontal span of the face — the bank counts as bed. */
    private int bedPixelUnder(int xa, int xb, byte[] pd) {
        if (pd.length == 0) return yForDepth(water().getInt("depth"));
        int best = top + VIEW_Y + VIEW_H;
        int mw = metreWidth();
        for (int i = 0; i < pd.length; i++) {
            int mx = xForMetre(i);
            if (mx + mw <= xa || mx >= xb) continue;
            int floorY = pd[i] < 0 ? yForDepth(0) - 3 : yForDepth(pd[i]);
            best = Math.min(best, floorY);
        }
        return best;
    }

    private Component bedLegend(byte[] pd, byte[] pb) {
        java.util.LinkedHashSet<Integer> kinds = new java.util.LinkedHashSet<>();
        for (int i = 0; i < pb.length; i++) if (pd[i] >= 0 && pb[i] > 0) kinds.add((int) pb[i]);
        if (kinds.isEmpty()) kinds.add((int) water().getByte("bed"));
        net.minecraft.network.chat.MutableComponent out = Component.translatable("finder.riverfishing.bed_is");
        boolean first = true;
        for (int k : kinds) {
            if (k <= 0) continue;
            out.append(Component.literal(first ? " " : ", "));
            out.append(Component.translatable("bed.riverfishing." + bedKey(k)));
            first = false;
        }
        return out;
    }

    private static String bedKey(int t) {
        return switch (t) {
            case 1 -> "sand";
            case 2 -> "gravel";
            case 3 -> "clay";
            case 4 -> "mud";
            case 5 -> "rock";
            default -> "other";
        };
    }

    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 40);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 40);
        int b = Math.min(255, (argb & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static Component fishName(String sp) {
        return Component.translatable("fish.riverfishing." + sp);
    }

    /**
     * The fish texture at {@link #ICON} px, the WHOLE 256 px sheet declared as what it is. Declaring it
     * 16 px would draw one corner of it; drawing it at 16 px keeps one texel in sixteen.
     */
    private void drawFish(GuiGraphics g, String sp, int x, int y) {
        g.blit(RiverFishing.id("textures/item/fish/" + sp + ".png"), x, y, ICON, ICON, 0f, 0f, 256, 256, 256, 256);
    }

    // ---- the map -------------------------------------------------------------------------------

    /**
     * §depth-map: the chart. Everything this player has ever been handed of the bed, drawn to pan and
     * zoom — the lake in front of you and the one you sounded last week, on one face, because the
     * point of measuring a bed is to still have it.
     *
     * <p>It is drawn in the section's own colours. The first chart was a paper chart's — pale water,
     * tan bank — and sat next to the dark instrument face like a page from another book. A sounder
     * has ONE face, and the bed from above is that face turned to look down: the same teal grid,
     * the same bank, water that gets darker as it gets deeper, gold for a mark.
     *
     * <p>Banded, because that is what makes a chart readable where a gradient is not: six depth
     * steps, one flat colour each, a contour where the step changes. Water nobody has sounded is the
     * face's own dark; an honest chart has holes in it until somebody casts across them.
     */
    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y, w = W - 2 * VIEW_X, h = VIEW_H;
        g.fill(x0, y0, x0 + w, y0 + h, FACE);
        java.util.Map<Long, Byte> cells = ClientSoundings.cells();
        int z = zoom;
        int cols = w / z + 2, rows = h / z + 2;
        int startX = (int) Math.floor(mapCx - (w / 2.0) / z), startZ = (int) Math.floor(mapCz - (h / 2.0) / z);
        if (chartCells == null || chartStartX != startX || chartStartZ != startZ || chartCols != cols
                || chartRows != rows || chartZoom != z || chartVersion != ClientSoundings.version()) {
            buildChart(startX, startZ, cols, rows, z);
        }

        // The cells, a row at a time with runs of one colour merged into one fill: a whole lake at two
        // pixels a block was seventeen thousand fills a frame, most of them the same teal as the last.
        for (int r = 0; r < rows; r++) {
            int py = y0 + (int) Math.floor((startZ + r - mapCz) * z + h / 2.0);
            int cy1 = Math.max(py, y0), py2 = Math.min(py + z, y0 + h);
            if (cy1 >= py2) continue;
            for (int c = 0; c < cols; ) {
                int colour = chartCells[r * cols + c];
                if (colour == 0) { c++; continue; }
                int c2 = c;
                while (c2 + 1 < cols && chartCells[r * cols + c2 + 1] == colour) c2++;
                int px = x0 + (int) Math.floor((startX + c - mapCx) * z + w / 2.0);
                int px2 = Math.min(px + (c2 - c + 1) * z, x0 + w);
                if (px < px2) g.fill(Math.max(px, x0), cy1, px2, py2, colour);
                c = c2 + 1;
            }
            if (z < 3) continue;
            for (int c = 0; c < cols; c++) {
                byte e = chartEdges[r * cols + c];
                if (e == 0) continue;
                int px = x0 + (int) Math.floor((startX + c - mapCx) * z + w / 2.0);
                int px2 = Math.min(px + z, x0 + w), cx1 = Math.max(px, x0);
                if (cx1 >= px2) continue;
                // Contour: a lighter edge wherever the next cell over is a deeper band.
                if ((e & 1) != 0) g.fill(px2 - 1, cy1, px2, py2, CHART_CONTOUR);
                if ((e & 2) != 0) g.fill(cx1, py2 - 1, px2, py2, CHART_CONTOUR);
            }
        }

        // Chunk grid, faint — the face's own teal, the way the section rules its depths.
        for (int gx = (startX / 16) * 16; gx < startX + cols; gx += 16) {
            int px = x0 + (int) Math.floor((gx - mapCx) * z + w / 2.0);
            if (px > x0 && px < x0 + w) g.fill(px, y0 + 1, px + 1, y0 + h - 1, GRID);
        }
        for (int gz = (startZ / 16) * 16; gz < startZ + rows; gz += 16) {
            int py = y0 + (int) Math.floor((gz - mapCz) * z + h / 2.0);
            if (py > y0 && py < y0 + h) g.fill(x0 + 1, py, x0 + w - 1, py + 1, GRID);
        }

        // Marks: a ring for a hole, a diamond for a drop-off — and a white halo on the one the
        // strip's needle is set to. Clicking a mark sets it; clicking it again lets it go.
        markRects.clear();
        markKeys.clear();
        Long target = ClientSoundings.target();
        for (int i = 0; i < chartMarks.size(); i++) {
            int[] m = chartMarks.get(i);
            Long key = chartMarkKeys.get(i);
            int px = x0 + (int) Math.floor((m[0] - mapCx) * z + w / 2.0) + z / 2;
            int py = y0 + (int) Math.floor((m[1] - mapCz) * z + h / 2.0) + z / 2;
            if (px < x0 + 5 || px > x0 + w - 5 || py < y0 + 5 || py > y0 + h - 5) continue;
            markRects.add(new int[]{px, py});
            markKeys.add(key);
            boolean picked = target != null && target.equals(key);   // boxed: == is identity
            if (picked) g.fill(px - 6, py - 6, px + 7, py + 7, 0xFFFFFFFF);
            if (m[2] == 0) {
                g.fill(px - 4, py - 4, px + 4, py + 4, CHART_MARK);
                g.fill(px - 2, py - 2, px + 2, py + 2, FACE);
            } else {
                for (int k = -3; k <= 3; k++) {
                    int hw = 3 - Math.abs(k);
                    g.fill(px - hw, py + k, px + hw + 1, py + k + 1, CHART_MARK);
                }
            }
            if (mouseX >= px - 5 && mouseX < px + 5 && mouseY >= py - 5 && mouseY < py + 5) {
                Byte v = cells.get(key);
                int d = v == null || v < ClientSoundings.DEPTH0 ? 0 : v - ClientSoundings.DEPTH0;
                hover = List.of(Component.translatable("spot.riverfishing." + (m[2] == 0 ? "hole" : "ledge")).getVisualOrderText(),
                        Component.translatable("finder.riverfishing.metres", d).getVisualOrderText(),
                        Component.translatable(picked ? "finder.riverfishing.mark_picked" : "finder.riverfishing.mark_hint").getVisualOrderText());
            }
        }

        // You, live: where you stand now and which way you face, not where the sounding was taken.
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            int px = x0 + (int) Math.floor((mc.player.getX() - mapCx) * z + w / 2.0);
            int py = y0 + (int) Math.floor((mc.player.getZ() - mapCz) * z + h / 2.0);
            if (px > x0 && px < x0 + w && py > y0 && py < y0 + h) {
                double yaw = Math.toRadians(mc.player.getYRot());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);
                for (int k = 0; k < 4 * z; k++) {
                    int ax = (int) Math.round(px + fx * k), ay = (int) Math.round(py + fz * k);
                    if (ax > x0 && ax < x0 + w && ay > y0 && ay < y0 + h) g.fill(ax, ay, ax + 1, ay + 1, CHART_YOU);
                }
                g.fill(px - 3, py - 3, px + 4, py + 4, 0xFFFFFFFF);
                g.fill(px - 2, py - 2, px + 3, py + 3, CHART_YOU);
            }
        }

        // Scale bar, ten metres at this zoom; and how much of the world is on the chart.
        int sx = x0 + 6, sy = y0 + h - 8;
        g.fill(sx, sy, sx + 10 * z, sy + 2, SURFACE);
        g.fill(sx, sy - 2, sx + 1, sy + 3, SURFACE);
        g.fill(sx + 10 * z - 1, sy - 2, sx + 10 * z, sy + 3, SURFACE);
        g.drawString(this.font, Component.translatable("finder.riverfishing.metres", 10), sx + 2, sy - 12, 0x9940E0B0, false);
        int sounded = ClientSoundings.sounded();
        Component mapped = Component.translatable("finder.riverfishing.mapped", sounded);
        g.drawString(this.font, mapped, x0 + w - this.font.width(mapped) - 6, y0 + h - 11, 0x9940E0B0, false);
        if (sounded == 0) {
            int ty = y0 + 6;
            for (var seq : this.font.split(Component.translatable("finder.riverfishing.unsounded"), w - 20)) {
                g.drawString(this.font, seq, x0 + 10, ty, 0xCC40E0B0, false);
                ty += 11;
            }
        }
    }

    /** -1 bank, 0 unsounded water, 1..6 shallow to deep. */
    private static int bandOf(byte v, int deepest) {
        if (v == ClientSoundings.LAND) return -1;
        if (v < ClientSoundings.DEPTH0) return 0;
        return 1 + Math.min(5, (v - ClientSoundings.DEPTH0) * 6 / (deepest + 1));
    }

    /**
     * §finder2: the controls on the face — the two views you are NOT on, and on the chart a way back to
     * yourself. Right-aligned against the face's edge, left to right in {@link #tabKeys} order.
     */
    private void renderViewTab(GuiGraphics g, int mouseX, int mouseY) {
        List<String> keys = tabKeys();
        int[] xs = tabXs(keys);
        for (int i = 0; i < keys.size(); i++) tab(g, mouseX, mouseY, xs[i], keys.get(i));
    }

    private List<String> tabKeys() {
        List<String> k = new ArrayList<>();
        if (view == CHART) k.add("finder.riverfishing.to_me");
        if (view != SECTION) k.add("finder.riverfishing.to_section");
        if (view != CHART) k.add("finder.riverfishing.to_map");
        if (view != SAMPLE) k.add("finder.riverfishing.to_sample");
        return k;
    }

    private int faceWidth() {
        return view == SECTION ? VIEW_W : W - 2 * VIEW_X;
    }

    private int[] tabXs(List<String> keys) {
        int[] xs = new int[keys.size()];
        int x = left + VIEW_X + faceWidth();
        for (int i = keys.size() - 1; i >= 0; i--) {
            x -= this.font.width(Component.translatable(keys.get(i))) + 10;
            xs[i] = x;
            x -= 4;
        }
        return xs;
    }

    private void tab(GuiGraphics g, int mouseX, int mouseY, int x, String key) {
        Component label = Component.translatable(key);
        int w = this.font.width(label) + 10, y = top + VIEW_Y - 13;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
        g.fill(x, y, x + w, y + 12, hov ? 0xFF8A7038 : 0xFF63512F);
        g.drawString(this.font, label, x + 5, y + 2, 0xFFEDE2C6, false);
    }

    /** The lang key of the tab under the cursor, or null. */
    private String clickedTab(double mx, double my) {
        int y = top + VIEW_Y - 13;
        if (my < y || my >= y + 12) return null;
        List<String> keys = tabKeys();
        int[] xs = tabXs(keys);
        for (int i = 0; i < keys.size(); i++) {
            int w = this.font.width(Component.translatable(keys.get(i))) + 10;
            if (mx >= xs[i] && mx < xs[i] + w) return keys.get(i);
        }
        return null;
    }

    // ---- the list ------------------------------------------------------------------------------

    /** One row of the list: a species, or a heading with {@code sp == null}. */
    private record Row(String sp, boolean blocked, Component heading) {}

    /**
     * Two sections under two headings — what bites and what cannot — rather than one list that
     * quietly changes meaning halfway down. The first version did that, and "biting here (28)" over
     * ninety-three rows read as ninety-three fish biting.
     */
    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        // §finder2: fish only. The ecosystem, the farm ledger and the bank moved to the water sample —
        // eight lines of pond-keeping before the first fish was the list about the wrong thing.
        out.add(new Row(null, false, Component.translatable("finder.riverfishing.biting", here.size())));
        for (CompoundTag t : here) out.add(new Row(t.getString("sp"), false, null));
        if (!gone.isEmpty()) {
            out.add(new Row(null, true, Component.translatable("finder.riverfishing.not_here", gone.size())));
            for (CompoundTag t : gone) out.add(new Row(t.getString("sp"), true, null));
        }
        return out;
    }

    private int visibleRows() {
        return (VIEW_H) / ROW;
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + LIST_X, y = top + VIEW_Y;
        List<Row> rows = rows();
        int vis = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - vis));
        for (int i = scroll; i < rows.size() && i < scroll + vis; i++) {
            Row r = rows.get(i);
            if (r.sp() == null) {
                g.drawString(this.font, r.heading(), x, y, r.blocked() ? 0xFF9A4A3C : 0xFFB0842C, false);
                y += ROW;
                continue;
            }
            boolean hov = mouseX >= x - 2 && mouseX < x + LIST_W && mouseY >= y - 2 && mouseY < y + 11;
            if (hov) g.fill(x - 2, y - 2, x + LIST_W, y + 11, 0x22000000);
            CompoundTag t = find(r.sp());
            if (!r.blocked() && t != null) {
                // A bar for how well the water suits it — the number itself is engine noise.
                int wBar = (int) (18 * Mth.clamp(t.getFloat("e"), 0f, 1f));
                g.fill(x, y + 2, x + 18, y + 8, 0x33000000);
                g.fill(x, y + 2, x + wBar, y + 8, 0xFF3FA34A);
            } else {
                g.drawString(this.font, "×", x + 6, y, 0xFF9A4A3C, false);
            }
            String label = this.font.plainSubstrByWidth(fishName(r.sp()).getString(), LIST_W - 30);
            g.drawString(this.font, label, x + 24, y, r.blocked() ? GuiStyle.GHOST : GuiStyle.TEXT, false);
            y += ROW;
        }
        if (rows.size() > scroll + vis) {
            g.drawString(this.font, Component.translatable("finder.riverfishing.more", rows.size() - scroll - vis),
                    x, top + VIEW_Y + VIEW_H - 2, GuiStyle.GHOST, false);
        }
    }

    // ---- one species ---------------------------------------------------------------------------

    private void renderDetail(GuiGraphics g) {
        CompoundTag t = find(detail);
        if (t == null) { detail = null; return; }
        int x = left + LIST_X, y = top + VIEW_Y;
        drawFish(g, detail, x, y - 4);
        g.drawString(this.font, fishName(detail), x + ICON + 4, y + 6, GuiStyle.TEXT, false);
        y += ICON + 4;

        y = line(g, x, y, "finder.riverfishing.depth_band",
                Component.literal(t.getInt("dmin") + "–" + t.getInt("dmax") + " m"));
        y = line(g, x, y, "finder.riverfishing.level", Component.literal(String.valueOf(t.getInt("lvl"))));

        if (!detailBlocked) {
            y = line(g, x, y, "finder.riverfishing.bait",
                    Component.translatable("item.riverfishing." + t.getString("bait")));
            y = line(g, x, y, "finder.riverfishing.stock_label",
                    Component.literal(t.getInt("stock") + "%")
                            .append(t.getBoolean("res") ? Component.empty()
                                    : Component.translatable("finder.riverfishing.temp")));
            if (t.getBoolean("sig")) {
                g.drawString(this.font, Component.translatable("finder.riverfishing.is_signature"),
                        x, y, 0xFFB05A00, false);
                y += 12;
            }
            // §bed-bite: the bottom here, and whether this fish would rather it were something else.
            float bf = t.contains("bf") ? t.getFloat("bf") : 1f;
            Component bedName = Component.translatable("bed.riverfishing." + bedKey(water().getByte("bed")));
            String bedKeyLine = bf > 1.02f ? "finder.riverfishing.bed_likes"
                    : bf < 0.98f ? "finder.riverfishing.bed_dislikes" : "finder.riverfishing.bed_neutral";
            g.drawString(this.font, Component.translatable(bedKeyLine, bedName), x, y,
                    bf > 1.02f ? 0xFF2E7D32 : bf < 0.98f ? 0xFF9A4A3C : GuiStyle.TEXT_HINT, false);
            y += 12;
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
        // §finder2: the release's gates with their numbers — the same block the sample view shows for
        // what is in the other hand, here for the fish you clicked.
        if (t.contains("fit")) {
            List<Component> lines = suitLines(t);
            for (int i = 0; i < lines.size(); i++) {
                int colour = i < lines.size() - 1 ? GuiStyle.TEXT_HINT : t.getFloat("fit") > 0 ? 0xFF2E7D32 : 0xFF9A4A3C;
                g.drawString(this.font, this.font.plainSubstrByWidth(lines.get(i).getString(), LIST_W), x, y, colour, false);
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

    // ---- the chart cache -----------------------------------------------------------------------

    /**
     * §finder2: the block window's colours, contour edges and marks, built once and kept until the
     * window, the zoom or the data moves. Cells: 0 nothing, else an ARGB. Edges: bit 0 a deeper band
     * to the right, bit 1 below.
     */
    private void buildChart(int startX, int startZ, int cols, int rows, int z) {
        chartStartX = startX;
        chartStartZ = startZ;
        chartCols = cols;
        chartRows = rows;
        chartZoom = z;
        chartVersion = ClientSoundings.version();
        java.util.Map<Long, Byte> cells = ClientSoundings.cells();
        int deepest = Math.max(1, ClientSoundings.deepest());
        chartCells = new int[cols * rows];
        chartEdges = new byte[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int gx = startX + c, gz = startZ + r;
                Byte v = cells.get(ClientSoundings.key(gx, gz));
                if (v == null) continue;
                int b = bandOf(v, deepest);
                if (b < 0) {
                    boolean shore = false;
                    for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        Byte nb = cells.get(ClientSoundings.key(gx + o[0], gz + o[1]));
                        if (nb != null && nb >= ClientSoundings.WATER) shore = true;
                    }
                    chartCells[r * cols + c] = shore ? LAND_TOP : LAND;
                    continue;
                }
                chartCells[r * cols + c] = CHART_BANDS[b];
                if (b > 0) {
                    Byte rt = cells.get(ClientSoundings.key(gx + 1, gz)), dn = cells.get(ClientSoundings.key(gx, gz + 1));
                    int e = 0;
                    if (rt != null && bandOf(rt, deepest) > b) e |= 1;
                    if (dn != null && bandOf(dn, deepest) > b) e |= 2;
                    chartEdges[r * cols + c] = (byte) e;
                }
            }
        }
        chartMarks.clear();
        chartMarkKeys.clear();
        for (java.util.Map.Entry<Long, Byte> e : ClientSoundings.spots().entrySet()) {
            int gx = ClientSoundings.keyX(e.getKey()), gz = ClientSoundings.keyZ(e.getKey());
            if (gx < startX || gx >= startX + cols || gz < startZ || gz >= startZ + rows) continue;
            chartMarks.add(new int[]{gx, gz, e.getValue()});
            chartMarkKeys.add(e.getKey());
        }
    }

    // ---- the water sample ----------------------------------------------------------------------

    /** One line of the sample: a label with a value, or a plain line with {@code label == null}. */
    private record Line(Component label, Component value, int colour) {}

    private static final int SAMPLE_LABEL = 0x9940E0B0, SAMPLE_TEXT = 0xFFB0E8D8, SAMPLE_HEAD = 0xFFE8B430;
    private static final int SAMPLE_OK = 0xFF7CE07C, SAMPLE_BAD = 0xFFFF6060;

    private Line head(String key, Object... args) {
        return new Line(null, Component.translatable(key, args), SAMPLE_HEAD);
    }

    private Line pairLine(String key, Component value) {
        return new Line(Component.translatable(key), value, SAMPLE_TEXT);
    }

    private Line plain(Component c) {
        return new Line(null, c, SAMPLE_TEXT);
    }

    /**
     * §finder2: the water as a sample — what the finder measured that is not a fish. Everything the
     * section's list used to open with, plus what the bite engine reads and never said: clarity, the
     * climate groups, the third of the season, oxygen and cover. For a fish, roe or fry in the other
     * hand, the suitability block comes first: that is the question being asked.
     */
    private List<Line> sampleLines() {
        CompoundTag w = water();
        List<Line> out = new ArrayList<>();
        CompoundTag suit = data.getCompound("suit");
        if (!suit.isEmpty()) {
            out.add(head("finder.riverfishing.suit", fishName(suit.getString("sp"))));
            List<Component> lines = suitLines(suit);
            for (int i = 0; i < lines.size(); i++) {
                boolean last = i == lines.size() - 1;
                out.add(new Line(null, lines.get(i), !last ? SAMPLE_TEXT : suit.getFloat("fit") > 0 ? SAMPLE_OK : SAMPLE_BAD));
            }
            out.add(plain(Component.empty()));
        }
        out.add(head("finder.riverfishing.sample"));
        out.add(pairLine("finder.riverfishing.water", Component.translatable("water.riverfishing." + w.getString("type"))));
        out.add(pairLine("finder.riverfishing.depth", Component.translatable("finder.riverfishing.metres", w.getInt("depth"))));
        out.add(pairLine("finder.riverfishing.width", Component.translatable("finder.riverfishing.metres", Math.round(w.getFloat("width")))));
        out.add(pairLine("finder.riverfishing.bed_label", Component.translatable("bed.riverfishing." + bedKey(w.getByte("bed")))));
        if (w.contains("clarity")) {
            out.add(pairLine("finder.riverfishing.clarity", Component.literal(Math.round(w.getFloat("clarity") * 100) + "%")));
        }
        List<Component> groups = new ArrayList<>();
        for (String k : w.getString("groups").split(";")) {
            if (!k.isEmpty()) groups.add(Component.translatable("biomegroup.riverfishing." + k));
        }
        net.minecraft.network.chat.MutableComponent climate = Component.empty();
        for (int i = 0; i < groups.size(); i++) climate.append(i == 0 ? "" : ", ").append(groups.get(i));
        out.add(pairLine("finder.riverfishing.climate", groups.isEmpty() ? Component.literal("—") : climate));
        if (!w.getString("season").isEmpty()) {
            out.add(pairLine("finder.riverfishing.season", windowName(w.getString("season"), w.getString("sub"))));
        }
        String up = data.getString("upgrades");
        boolean aerator = false, snags = false;
        List<String> upNames = new ArrayList<>();
        for (String k : up.split(";")) {
            if (k.isEmpty()) continue;
            aerator |= k.equals("aerator");
            snags |= k.equals("snags");
            String block = k.equals("snags") ? "snag_pile" : k.equals("gravel") ? "gravel_bed" : k;
            upNames.add(Component.translatable("block.riverfishing." + block).getString());
        }
        out.add(pairLine("finder.riverfishing.oxygen", Component.translatable(aerator ? "finder.riverfishing.oxygen_aerated" : "finder.riverfishing.oxygen_natural")));
        out.add(pairLine("finder.riverfishing.cover", Component.translatable(snags ? "finder.riverfishing.cover_snags" : "finder.riverfishing.cover_none")));
        // §f §ecosystem: what a settled species or a bank-side upgrade did to this water, one line each.
        for (String k : w.getString("eco").split(";")) {
            if (!k.isEmpty()) out.add(plain(Component.translatable("ecosystem.riverfishing." + k)));
        }
        if (!upNames.isEmpty()) {
            out.add(plain(Component.translatable("finder.riverfishing.farm_upgrades", String.join(", ", upNames))));
        }
        // §k §farm: the ledger for this water — one line a species, and when each spawns.
        CompoundTag farm = data.getCompound("farm");
        if (!farm.isEmpty()) {
            out.add(head("finder.riverfishing.farm"));
            for (String s : farm.getAllKeys()) {
                CompoundTag f = farm.getCompound(s);
                out.add(plain(Component.translatable(f.getBoolean("settled") ? "finder.riverfishing.farm_row" : "finder.riverfishing.farm_row_new",
                        fishName(s), f.getInt("stock"), f.getInt("f"), f.getInt("m"), f.getInt("fry"),
                        f.getString("genome"), Math.max(0, f.getInt("grow")))));
                if (f.contains("spawn")) {
                    int in = f.getInt("in");
                    out.add(plain(in <= 0
                            ? Component.translatable("finder.riverfishing.spawn_now", fishName(s), windowName(f.getString("spawn"), f.getString("ssub")))
                            : Component.translatable("finder.riverfishing.spawn_row", fishName(s), windowName(f.getString("spawn"), f.getString("ssub")), in)));
                }
            }
        }
        // §o: where the angler stands with the fishermen, and in the red what it takes to clear it.
        if (w.contains("rep")) out.add(pairLine("finder.riverfishing.rep", w.getInt("rep") < 0
                ? Component.translatable("finder.riverfishing.rep_debt", w.getInt("rep"),
                        com.riverfishing.fishing.Warden.kg(com.riverfishing.fishing.Warden.toClear(w.getInt("rep"), w.getInt("rep_grams"))))
                : Component.literal(String.valueOf(w.getInt("rep")))));
        CompoundTag ownerWater = data.getCompound("water");   // the owner rides in the water tag
        if (ownerWater.contains("owner")) {
            out.add(pairLine("finder.riverfishing.owner", Component.literal(ownerWater.getString("owner"))));
        }
        return out;
    }

    /** "late spring" from a season key and a sub key ("" for the whole season) — the calendar's own names. */
    private static Component windowName(String season, String sub) {
        return sub.isEmpty() ? Component.translatable("season.riverfishing." + season)
                : Component.translatable("calendar.riverfishing.name." + sub + "_" + season);
    }

    /**
     * §finder2: the habitat gates as three lines, from a species tag carrying the server's numbers —
     * the factors, the two hard bands with their pass marks, and the fit the release settles by. Used
     * on the sample for what is in the other hand and in the detail panel for a fish in the list.
     */
    private List<Component> suitLines(CompoundTag t) {
        CompoundTag w = water();
        int depth = w.getInt("depth");
        float width = w.getFloat("width");
        boolean dOk = depth >= t.getInt("dmin") && depth <= t.getInt("dmax");
        boolean wOk = width >= t.getFloat("wmin") && width <= t.getFloat("wmax");
        String grp = t.getString("bgrp");
        Component climate = !grp.isEmpty() ? Component.translatable("biomegroup.riverfishing." + grp)
                : Component.translatable(t.getFloat("bio") > 0 ? "finder.riverfishing.climate_any" : "finder.riverfishing.climate_none");
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("finder.riverfishing.suit_factors", fmt(t.getFloat("wf")), fmt(t.getFloat("sf")), climate, fmt(t.getFloat("bio"))));
        out.add(Component.translatable("finder.riverfishing.suit_gates", depth, dOk ? "✓" : "✗", t.getInt("dmin"), t.getInt("dmax"),
                Math.round(width), wOk ? "✓" : "✗", Math.round(t.getFloat("wmin")),
                t.getFloat("wmax") >= 1000 ? "∞" : String.valueOf(Math.round(t.getFloat("wmax")))));
        float fit = t.getFloat("fit");
        net.minecraft.network.chat.MutableComponent last = fit <= 0 ? Component.translatable("finder.riverfishing.suit_fit_none")
                : Component.translatable("finder.riverfishing.suit_fit", Math.round(fit * 100));
        if (t.contains("native")) {
            last.append(" · ").append(Component.translatable(t.getBoolean("native") ? "finder.riverfishing.suit_native"
                    : t.getBoolean("settled") ? "finder.riverfishing.suit_settled" : "finder.riverfishing.suit_new"));
        }
        out.add(last);
        return out;
    }

    private static String fmt(float f) {
        return String.format(java.util.Locale.ROOT, "%.2f", f);
    }

    private void renderSample(GuiGraphics g) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y, w = faceWidth(), h = VIEW_H;
        g.fill(x0, y0, x0 + w, y0 + h, FACE);
        List<Line> lines = sampleLines();
        sampleScroll = Mth.clamp(sampleScroll, 0, Math.max(0, lines.size() - 3));
        g.enableScissor(x0, y0, x0 + w, y0 + h);
        int x = x0 + 6, y = y0 + 5;
        for (int i = sampleScroll; i < lines.size() && y < y0 + h; i++) {
            Line l = lines.get(i);
            if (l.label() != null) {
                g.drawString(this.font, l.label(), x, y, SAMPLE_LABEL, false);
                g.drawString(this.font, l.value(), x + 96, y, l.colour(), false);
                y += 11;
                continue;
            }
            for (var seq : this.font.split(l.value(), w - 12)) {
                g.drawString(this.font, seq, x, y, l.colour(), false);
                y += 11;
            }
        }
        g.disableScissor();
        if (lines.size() > sampleScroll + 1 && y >= y0 + h) {
            g.drawString(this.font, Component.translatable("finder.riverfishing.more", lines.size() - sampleScroll),
                    x0 + w - 90, y0 + h - 11, 0x9940E0B0, false);
        }
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
        // §pond: a claimed pond says whose it is — and why the wild list is short.
        if (!w.getString("owner").isEmpty()) {
            g.drawString(this.font, Component.translatable("finder.riverfishing.owner", w.getString("owner")),
                    x, y, 0xFFB08A00, false);
            y += 12;
        }

        int x2 = left + 230;
        int y2 = top + VIEW_Y + VIEW_H + 8;
        if (!w.getString("season").isEmpty()) {
            y2 = pair2(g, x2, y2, "finder.riverfishing.season",
                    Component.translatable("season.riverfishing." + w.getString("season")));
        }
        y2 = pair2(g, x2, y2, "finder.riverfishing.weather",
                Component.translatable("weather.riverfishing." + w.getString("weather")));
        // Pressure and outlook on their own line each — the first cut printed them over each other.
        g.drawString(this.font, Component.translatable("finder.riverfishing.pressure_short",
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
        if (view == CHART) {
            // The chart takes the whole face: a species list beside a map of last week's lake is a
            // list about the wrong water.
            renderMap(g, mouseX, mouseY);
        } else if (view == SAMPLE) {
            renderSample(g);
        } else {
            renderSection(g, mouseX, mouseY);
            if (detail == null) renderList(g, mouseX, mouseY);
            else renderDetail(g);
        }
        renderViewTab(g, mouseX, mouseY);
        renderBar(g);
        if (hover != null) g.renderTooltip(this.font, hover, mouseX, mouseY);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // §journal-blur: the panel is opaque, and 1.21's gaussian pass reads as a washed-out page.
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        String tab = clickedTab(mx, my);
        if (tab != null) {
            switch (tab) {
                case "finder.riverfishing.to_me" -> centreOnMe();
                case "finder.riverfishing.to_section" -> view = SECTION;
                case "finder.riverfishing.to_map" -> view = CHART;
                default -> view = SAMPLE;
            }
            return true;
        }
        if (view == SAMPLE) return true;
        if (view == CHART) {
            // §arrow-target: a mark under the cursor is picked (or let go); anything else is the
            // start of a drag.
            for (int i = 0; i < markRects.size(); i++) {
                int[] r = markRects.get(i);
                if (mx >= r[0] - 5 && mx < r[0] + 5 && my >= r[1] - 5 && my < r[1] + 5) {
                    ClientSoundings.toggleTarget(markKeys.get(i));
                    return true;
                }
            }
            return true;
        }
        if (detail != null) {
            detail = null;
            return true;
        }
        // §section-click: a fish on the section is the same fish as in the list.
        for (int[] r : fishRects) {
            if (mx >= r[0] && mx < r[0] + ICON && my >= r[1] && my < r[1] + ICON) {
                detail = here.get(r[2]).getString("sp");
                detailBlocked = false;
                return true;
            }
        }
        int x = left + LIST_X, y = top + VIEW_Y;
        List<Row> rows = rows();
        int vis = visibleRows();
        for (int i = scroll; i < rows.size() && i < scroll + vis; i++) {
            Row r = rows.get(i);
            if (r.sp() != null && mx >= x - 2 && mx < x + LIST_W && my >= y - 2 && my < y + 11) {
                detail = r.sp();
                detailBlocked = r.blocked();
                return true;
            }
            y += ROW;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (view == CHART && button == 0) {
            mapCx -= dx / zoom;
            mapCz -= dy / zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (view == SAMPLE) {
            sampleScroll = Math.max(0, sampleScroll - (int) Math.signum(dy));
            return true;
        }
        if (view == CHART) {
            // Five steps of zoom. Two pixels a block shows a whole lake; eight shows a swim.
            int[] steps = {2, 3, 4, 6, 8};
            int i = 0;
            for (int k = 0; k < steps.length; k++) if (steps[k] == zoom) i = k;
            i = Mth.clamp(i + (int) Math.signum(dy), 0, steps.length - 1);
            zoom = steps[i];
            return true;
        }
        scroll -= (int) Math.signum(dy);
        return true;
    }

    private void centreOnMe() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mapCx = mc.player.getX();
        mapCz = mc.player.getZ();
    }
}
