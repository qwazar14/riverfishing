package com.riverfishing.client;

import com.riverfishing.fishing.FishingManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    // §finder-chart: a chart's palette. Land tan, a darker shoreline, water in six steps from pale
    // shallows to deep blue, white contours, a red boat, gold marks.
    private static final int CHART_LAND = 0xFFD8C79A, CHART_SHORE = 0xFF9E8A5A, CHART_GRID = 0x30FFFFFF;
    private static final int CHART_CONTOUR = 0x88FFFFFF, CHART_YOU = 0xFFE03030, CHART_MARK = 0xFFFFC83C;
    private static final int[] CHART_BANDS = {
            0xFF9FD3E6,   // unsounded water: pale, flat, honest
            0xFFB9E4F0, 0xFF86C4E0, 0xFF5AA3CF, 0xFF3A7FB8, 0xFF275C97, 0xFF163B6E};

    private final CompoundTag data;
    private final List<CompoundTag> here = new ArrayList<>();
    private final List<CompoundTag> gone = new ArrayList<>();

    private int left, top;
    private int scroll;
    /** The species whose page is open, or null for the list. */
    private String detail;
    private boolean detailBlocked;
    /** §sounding: the face shows the section, or the bed from above. */
    private boolean mapView;
    /** What the cursor is over on the face this frame — drawn last, above the whole panel. */
    private List<net.minecraft.util.FormattedCharSequence> hover;

    public FinderScreen(CompoundTag data) {
        super(Component.translatable("screen.riverfishing.finder"));
        this.data = data == null ? new CompoundTag() : data;
        ListTag h = this.data.getListOrEmpty("here");
        for (int i = 0; i < h.size(); i++) here.add(h.getCompoundOrEmpty(i));
        ListTag g = this.data.getListOrEmpty("gone");
        for (int i = 0; i < g.size(); i++) gone.add(g.getCompoundOrEmpty(i));
        // Best first, so the top of the list is the answer to "what do I put on".
        here.sort((a, b) -> Float.compare(b.getFloatOr("e", 0f), a.getFloatOr("e", 0f)));
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
        return data.getCompoundOrEmpty("water");
    }

    // ---- the section ---------------------------------------------------------------------------

    /** The bed along the aim, one metre a column; a negative is bank. Empty when the server sent none. */
    private byte[] profileDepth() {
        return data.getCompoundOrEmpty("profile").getByteArray("d").orElse(new byte[0]);
    }

    private byte[] profileBed() {
        return data.getCompoundOrEmpty("profile").getByteArray("b").orElse(new byte[0]);
    }

    /** The scale of the ruler: the deepest thing on the face, never less than six blocks. */
    private int depthScale() {
        int scale = Math.max(6, water().getIntOr("depth", 0));
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

    private void renderSection(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        g.fill(x0, y0, x0 + VIEW_W, y0 + VIEW_H, FACE);

        int scale = depthScale();
        int step = scale <= 8 ? 1 : (scale + 7) / 8;
        for (int d = 0; d <= scale; d += step) {
            int y = yForDepth(d);
            g.fill(x0 + 1, y, x0 + VIEW_W - 1, y + 1, GRID);
            g.text(this.font, String.valueOf(d), x0 + 3, y - 4, 0x8840E0B0, false);
        }

        byte[] pd = profileDepth(), pb = profileBed();
        int floorBottom = y0 + VIEW_H - 1;
        int surfaceY = yForDepth(0);
        int mw = metreWidth();
        if (pd.length == 0) {
            // No profile (an old server): the flat floor at the one depth we have.
            int floorY = yForDepth(water().getIntOr("depth", 0));
            g.fill(x0 + 22, floorY, x0 + VIEW_W - 1, floorBottom, bedColour(water().getByteOr("bed", (byte) 0)));
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
        int shown = 0;
        for (CompoundTag t : here) {
            if (shown >= MAX_FISH) break;
            int[] at = placeFish(t, pd, taken);
            if (at == null) continue;
            taken.add(at);
            shown++;
            drawFish(g, t.getStringOr("sp", ""), at[0], at[1]);
            if (t.getBooleanOr("sig", false)) {
                // A signature species of this water wears a mark: this is a tench lake.
                g.text(this.font, "★", at[0] + ICON - 6, at[1] - 4, 0xFFE8B430, false);
            }
            if (mouseX >= at[0] && mouseX < at[0] + ICON && mouseY >= at[1] && mouseY < at[1] + ICON) {
                hover = List.of(fishName(t.getStringOr("sp", "")).getVisualOrderText(),
                        Component.translatable("finder.riverfishing.band",
                                t.getIntOr("dmin", 0), t.getIntOr("dmax", 0)).getVisualOrderText());
            }
        }
        if (here.size() > shown) {
            g.text(this.font, Component.translatable("finder.riverfishing.more_fish", here.size() - shown),
                    x0 + VIEW_W - 60, y0 + 4, 0x9940E0B0, false);
        }

        // What the bed is made of, read off the profile — the legend a real sounder prints.
        g.text(this.font, bedLegend(pd, pb), x0 + 4, y0 + VIEW_H - 11, 0x9940E0B0, false);
    }

    /**
     * Where to draw this species: the first metre out where the bed is deep enough for it, at the
     * depth it holds — clamped to the bed, so a bottom feeder sits ON the bottom rather than in it.
     * Then the nearest free cell around that, because two fish on one pixel are no fish at all.
     *
     * @return {x, y} on the face, or null if there is no room left on the face for it
     */
    private int[] placeFish(CompoundTag t, byte[] pd, List<int[]> taken) {
        int dmin = t.getIntOr("dmin", 0), dmax = t.getIntOr("dmax", 0);
        double mid = (dmin + dmax) / 2.0;
        int col = -1, deepest = -1, deepestD = -1;
        for (int i = 0; i < pd.length; i++) {
            if (pd[i] < 0) continue;
            if (pd[i] > deepestD) { deepestD = pd[i]; deepest = i; }
            if (col < 0 && pd[i] >= dmin) col = i;
        }
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        int wantX, bedHere;
        if (pd.length == 0) {
            wantX = x0 + 30 + (taken.size() * (ICON + 6)) % (VIEW_W - 60);
            bedHere = water().getIntOr("depth", 0);
        } else if (col >= 0) {
            wantX = xForMetre(col);
            bedHere = pd[col];
        } else {
            // Nowhere along this line is deep enough: it holds where the line is deepest, which is
            // the honest place to say "this fish wants more water than there is here".
            wantX = deepest < 0 ? x0 + 30 : xForMetre(deepest);
            bedHere = Math.max(0, deepestD);
        }
        double depth = Mth.clamp(mid, 0.4, Math.max(0.4, bedHere - 0.4));
        int wantY = yForDepth(depth) - ICON / 2;

        // Nearest free cell: same spot, then along the bed, then a lane up or down.
        int[][] tries = {{0, 0}, {ICON + 2, 0}, {-(ICON + 2), 0}, {0, ICON + 2}, {0, -(ICON + 2)},
                {2 * (ICON + 2), 0}, {-2 * (ICON + 2), 0}, {ICON + 2, ICON + 2}, {-(ICON + 2), -(ICON + 2)}};
        for (int[] d : tries) {
            int x = Mth.clamp(wantX + d[0], x0 + 24, x0 + VIEW_W - ICON - 2);
            int y = Mth.clamp(wantY + d[1], y0 + 2, y0 + VIEW_H - ICON - 12);
            boolean free = true;
            for (int[] o : taken) {
                if (Math.abs(o[0] - x) < ICON && Math.abs(o[1] - y) < ICON) { free = false; break; }
            }
            if (free) return new int[]{x, y};
        }
        return null;
    }

    private Component bedLegend(byte[] pd, byte[] pb) {
        java.util.LinkedHashSet<Integer> kinds = new java.util.LinkedHashSet<>();
        for (int i = 0; i < pb.length; i++) if (pd[i] >= 0 && pb[i] > 0) kinds.add((int) pb[i]);
        if (kinds.isEmpty()) kinds.add((int) water().getByteOr("bed", (byte) 0));
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
    private void drawFish(GuiGraphicsExtractor g, String sp, int x, int y) {
        FishIcon.draw(g, sp, x, y, ICON, 0xFFFFFFFF);
    }

    // ---- the map -------------------------------------------------------------------------------

    /**
     * §finder-chart: the bed from above, drawn the way a chart draws it.
     *
     * <p>The first two cuts scattered three-pixel dots on a black face and called it a map; it read
     * as noise, and a map that reads as noise is worse than the number it replaced. A chart works
     * because it is BANDED: depth in steps, each step one flat colour, a line where the colour
     * changes. So: water in six depth bands from pale shallows to deep blue, a contour line on every
     * band edge, the bank in tan with a shoreline, a grid every five metres, a scale bar, and you as
     * a boat with a heading line. Sounded cells spread two cells out so a swath reads as a band of
     * bed and not as a row of stitches; water nobody has sounded is plain water.
     */
    private void renderMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        final int R = FishingManager.MAP_REACH, N = 2 * R + 1, CELL = 4;
        int cx = x0 + VIEW_W / 2, cy = y0 + VIEW_H / 2;
        int ox = cx - R * CELL - CELL / 2, oy = cy - R * CELL - CELL / 2;
        g.fill(x0, y0, x0 + VIEW_W, y0 + VIEW_H, CHART_LAND);

        byte[] wet = data.getByteArray("wet").orElse(new byte[0]);
        boolean[] water = new boolean[N * N];
        if (wet.length == N * N) for (int i = 0; i < N * N; i++) water[i] = wet[i] != 0;

        // Sounded depth per cell, spread two cells out from each reading — nearest reading wins.
        int[] depth = new int[N * N];
        int[] dist = new int[N * N];
        java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        int deepest = 1;
        ListTag map = data.getListOrEmpty("map");
        java.util.List<int[]> marks = new java.util.ArrayList<>();
        for (int i = 0; i < map.size(); i++) {
            CompoundTag t = map.getCompoundOrEmpty(i);
            int mx = t.getIntOr("x", 0) + R, mz = t.getIntOr("z", 0) + R, d = t.getIntOr("d", 0);
            deepest = Math.max(deepest, d);
            if (t.contains("s")) marks.add(new int[]{mx, mz, "hole".equals(t.getStringOr("s", "")) ? 0 : 1, d});
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int x = mx + dx, z = mz + dz;
                    if (x < 0 || z < 0 || x >= N || z >= N) continue;
                    int dd = dx * dx + dz * dz;
                    if (dd < dist[z * N + x]) { dist[z * N + x] = dd; depth[z * N + x] = d; }
                }
            }
        }

        // Bands: index 0 is unsounded water, 1..6 are shallow to deep.
        int[] band = new int[N * N];
        for (int i = 0; i < N * N; i++) {
            if (!water[i]) { band[i] = -1; continue; }
            band[i] = dist[i] == Integer.MAX_VALUE ? 0 : 1 + Math.min(5, depth[i] * 6 / (deepest + 1));
        }

        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                int px = ox + x * CELL, py = oy + z * CELL;
                if (px < x0 || px + CELL > x0 + VIEW_W || py < y0 || py + CELL > y0 + VIEW_H) continue;
                int b = band[z * N + x];
                if (b < 0) {
                    // Bank. A shoreline where it meets water, so the edge of the lake is a line.
                    boolean shore = false;
                    for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = x + o[0], nz = z + o[1];
                        if (nx >= 0 && nz >= 0 && nx < N && nz < N && band[nz * N + nx] >= 0) shore = true;
                    }
                    g.fill(px, py, px + CELL, py + CELL, shore ? CHART_SHORE : CHART_LAND);
                    continue;
                }
                g.fill(px, py, px + CELL, py + CELL, CHART_BANDS[b]);
                // Contour: a lighter edge wherever the band changes toward the deeper neighbour.
                boolean edge = false;
                for (int[] o : new int[][]{{1, 0}, {0, 1}}) {
                    int nx = x + o[0], nz = z + o[1];
                    if (nx < N && nz < N && band[nz * N + nx] > b && band[nz * N + nx] > 0 && b > 0) edge = true;
                }
                if (edge) g.fill(px, py + CELL - 1, px + CELL, py + CELL, CHART_CONTOUR);
            }
        }

        // Grid every five metres, faint, so distance can be read off the chart.
        for (int k = -R; k <= R; k += 5) {
            int gx = cx + k * CELL, gy = cy + k * CELL;
            if (gx > x0 && gx < x0 + VIEW_W) g.fill(gx, y0 + 1, gx + 1, y0 + VIEW_H - 1, CHART_GRID);
            if (gy > y0 && gy < y0 + VIEW_H) g.fill(x0 + 1, gy, x0 + VIEW_W - 1, gy + 1, CHART_GRID);
        }

        // Features: a ring for a hole, a diamond for a drop-off.
        for (int[] m : marks) {
            int px = ox + m[0] * CELL + CELL / 2, py = oy + m[1] * CELL + CELL / 2;
            if (px < x0 + 4 || px > x0 + VIEW_W - 4 || py < y0 + 4 || py > y0 + VIEW_H - 4) continue;
            if (m[2] == 0) {
                g.fill(px - 4, py - 4, px + 4, py + 4, CHART_MARK);
                g.fill(px - 2, py - 2, px + 2, py + 2, CHART_BANDS[Math.max(1, band[m[1] * N + m[0]])]);
            } else {
                for (int k = -3; k <= 3; k++) {
                    int hw = 3 - Math.abs(k);
                    g.fill(px - hw, py + k, px + hw + 1, py + k + 1, CHART_MARK);
                }
            }
            if (mouseX >= px - 5 && mouseX < px + 5 && mouseY >= py - 5 && mouseY < py + 5) {
                hover = List.of(Component.translatable("spot.riverfishing." + (m[2] == 0 ? "hole" : "ledge")).getVisualOrderText(),
                        Component.translatable("finder.riverfishing.metres", m[3]).getVisualOrderText());
            }
        }

        // You: a boat, and a heading line out to where the sounding was aimed.
        double yaw = Math.toRadians(data.getIntOr("yaw", 0));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);     // Minecraft: yaw 0 faces +z, 90 faces -x
        for (int k = 0; k < 18; k++) {
            int ax = (int) Math.round(cx + fx * k), ay = (int) Math.round(cy + fz * k);
            if (ax > x0 && ax < x0 + VIEW_W && ay > y0 && ay < y0 + VIEW_H) g.fill(ax, ay, ax + 1, ay + 1, CHART_YOU);
        }
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFFFFFFFF);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, CHART_YOU);

        // Scale bar: ten metres, bottom left, over the chart.
        int sx = x0 + 6, sy = y0 + VIEW_H - 8;
        g.fill(sx, sy, sx + 10 * CELL, sy + 2, 0xFFFFFFFF);
        g.fill(sx, sy - 2, sx + 1, sy + 3, 0xFFFFFFFF);
        g.fill(sx + 10 * CELL - 1, sy - 2, sx + 10 * CELL, sy + 3, 0xFFFFFFFF);
        g.text(this.font, Component.translatable("finder.riverfishing.metres", 10), sx + 2, sy - 12, 0xFFFFFFFF, false);

        if (map.isEmpty()) {
            int ty = y0 + 6;
            for (var seq : this.font.split(Component.translatable("finder.riverfishing.unsounded"), VIEW_W - 20)) {
                g.text(this.font, seq, x0 + 10, ty, 0xFFFFFFFF, true);
                ty += 11;
            }
        }
    }

    /** The one control on the face: which of the two views it is showing. */
    private void renderViewTab(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Component label = Component.translatable(mapView
                ? "finder.riverfishing.to_section" : "finder.riverfishing.to_map");
        int w = this.font.width(label) + 10;
        int x = left + VIEW_X + VIEW_W - w, y = top + VIEW_Y - 13;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
        g.fill(x, y, x + w, y + 12, hov ? 0xFF8A7038 : 0xFF63512F);
        g.text(this.font, label, x + 5, y + 2, 0xFFEDE2C6, false);
    }

    private boolean clickedViewTab(double mx, double my) {
        Component label = Component.translatable(mapView
                ? "finder.riverfishing.to_section" : "finder.riverfishing.to_map");
        int w = this.font.width(label) + 10;
        int x = left + VIEW_X + VIEW_W - w, y = top + VIEW_Y - 13;
        return mx >= x && mx < x + w && my >= y && my < y + 12;
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
        out.add(new Row(null, false, Component.translatable("finder.riverfishing.biting", here.size())));
        for (CompoundTag t : here) out.add(new Row(t.getStringOr("sp", ""), false, null));
        if (!gone.isEmpty()) {
            out.add(new Row(null, true, Component.translatable("finder.riverfishing.not_here", gone.size())));
            for (CompoundTag t : gone) out.add(new Row(t.getStringOr("sp", ""), true, null));
        }
        return out;
    }

    private int visibleRows() {
        return (VIEW_H) / ROW;
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = left + LIST_X, y = top + VIEW_Y;
        List<Row> rows = rows();
        int vis = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - vis));
        for (int i = scroll; i < rows.size() && i < scroll + vis; i++) {
            Row r = rows.get(i);
            if (r.sp() == null) {
                g.text(this.font, r.heading(), x, y, r.blocked() ? 0xFF9A4A3C : 0xFFB0842C, false);
                y += ROW;
                continue;
            }
            boolean hov = mouseX >= x - 2 && mouseX < x + LIST_W && mouseY >= y - 2 && mouseY < y + 11;
            if (hov) g.fill(x - 2, y - 2, x + LIST_W, y + 11, 0x22000000);
            CompoundTag t = find(r.sp());
            if (!r.blocked() && t != null) {
                // A bar for how well the water suits it — the number itself is engine noise.
                int wBar = (int) (18 * Mth.clamp(t.getFloatOr("e", 0f), 0f, 1f));
                g.fill(x, y + 2, x + 18, y + 8, 0x33000000);
                g.fill(x, y + 2, x + wBar, y + 8, 0xFF3FA34A);
            } else {
                g.text(this.font, "×", x + 6, y, 0xFF9A4A3C, false);
            }
            String label = this.font.plainSubstrByWidth(fishName(r.sp()).getString(), LIST_W - 30);
            g.text(this.font, label, x + 24, y, r.blocked() ? GuiStyle.GHOST : GuiStyle.TEXT, false);
            y += ROW;
        }
        if (rows.size() > scroll + vis) {
            g.text(this.font, Component.translatable("finder.riverfishing.more", rows.size() - scroll - vis),
                    x, top + VIEW_Y + VIEW_H - 2, GuiStyle.GHOST, false);
        }
    }

    // ---- one species ---------------------------------------------------------------------------

    private void renderDetail(GuiGraphicsExtractor g) {
        CompoundTag t = find(detail);
        if (t == null) { detail = null; return; }
        int x = left + LIST_X, y = top + VIEW_Y;
        drawFish(g, detail, x, y - 4);
        g.text(this.font, fishName(detail), x + ICON + 4, y + 6, GuiStyle.TEXT, false);
        y += ICON + 4;

        y = line(g, x, y, "finder.riverfishing.depth_band",
                Component.literal(t.getIntOr("dmin", 0) + "–" + t.getIntOr("dmax", 0) + " m"));
        y = line(g, x, y, "finder.riverfishing.level", Component.literal(String.valueOf(t.getIntOr("lvl", 0))));

        if (!detailBlocked) {
            y = line(g, x, y, "finder.riverfishing.bait",
                    Component.translatable("item.riverfishing." + t.getStringOr("bait", "")));
            y = line(g, x, y, "finder.riverfishing.stock",
                    Component.literal(t.getIntOr("stock", 0) + "%")
                            .append(t.getBooleanOr("res", false) ? Component.empty()
                                    : Component.translatable("finder.riverfishing.temp")));
            if (t.getBooleanOr("sig", false)) {
                g.text(this.font, Component.translatable("finder.riverfishing.is_signature"),
                        x, y, 0xFFB05A00, false);
                y += 12;
            }
        } else {
            // The one thing this tool can say that nothing else does.
            y += 4;
            Component why = Component.translatable("finder.riverfishing.gate."
                    + t.getStringOr("why", "other").replaceAll("\\(.*\\)", ""));
            for (var seq : this.font.split(
                    Component.translatable("finder.riverfishing.blocked", why), LIST_W)) {
                g.text(this.font, seq, x, y, 0xFF9A4A3C, false);
                y += 11;
            }
        }
        g.text(this.font, Component.translatable("guide.riverfishing.back"),
                x, top + VIEW_Y + VIEW_H - 10, GuiStyle.GHOST, false);
    }

    private int line(GuiGraphicsExtractor g, int x, int y, String key, Component value) {
        g.text(this.font, Component.translatable(key), x, y, GuiStyle.TEXT_HINT, false);
        g.text(this.font, value, x + 74, y, GuiStyle.TEXT, false);
        return y + 12;
    }

    private CompoundTag find(String sp) {
        for (CompoundTag t : here) if (t.getStringOr("sp", "").equals(sp)) return t;
        for (CompoundTag t : gone) if (t.getStringOr("sp", "").equals(sp)) return t;
        return null;
    }

    // ---- the instrument bar --------------------------------------------------------------------

    private void renderBar(GuiGraphicsExtractor g) {
        CompoundTag w = water();
        int x = left + 10, y = top + VIEW_Y + VIEW_H + 8;
        g.fill(x, y - 4, left + W - 10, y - 3, 0x33000000);

        String outlook = w.getStringOr("outlook", "fair");
        int colour = switch (outlook) {
            case "great" -> 0xFF2E7D32;
            case "good" -> 0xFF3FA34A;
            case "fair" -> 0xFFB0842C;
            case "poor" -> 0xFF9A4A3C;
            default -> 0xFF7A2A22;
        };
        int trend = w.getIntOr("trend", 0);
        String arrow = trend < 0 ? "↓" : trend > 0 ? "↑" : "→";

        y = pair(g, x, y, "finder.riverfishing.water",
                Component.translatable("water.riverfishing." + w.getStringOr("type", "")));
        y = pair(g, x, y, "finder.riverfishing.depth",
                Component.translatable("finder.riverfishing.metres", w.getIntOr("depth", 0)));
        y = pair(g, x, y, "finder.riverfishing.width",
                Component.translatable("finder.riverfishing.metres", Math.round(w.getFloatOr("width", 0f))));

        int x2 = left + 230;
        int y2 = top + VIEW_Y + VIEW_H + 8;
        if (!w.getStringOr("season", "").isEmpty()) {
            y2 = pair2(g, x2, y2, "finder.riverfishing.season",
                    Component.translatable("season.riverfishing." + w.getStringOr("season", "")));
        }
        y2 = pair2(g, x2, y2, "finder.riverfishing.weather",
                Component.translatable("weather.riverfishing." + w.getStringOr("weather", "clear")));
        // Pressure and outlook on their own line each — the first cut printed them over each other.
        g.text(this.font, Component.translatable("finder.riverfishing.pressure_short",
                w.getIntOr("hpa", 1013), arrow), x2, y2, GuiStyle.TEXT_HINT, false);
        g.text(this.font, Component.translatable("finder.riverfishing.outlook." + outlook),
                x2 + 96, y2, colour, false);

        if (w.getBooleanOr("frenzy", false)) {
            g.text(this.font, Component.translatable("finder.riverfishing.frenzy"),
                    x, top + H - 16, 0xFFB05A00, false);
        }
    }

    private int pair(GuiGraphicsExtractor g, int x, int y, String key, Component value) {
        g.text(this.font, Component.translatable(key), x, y, GuiStyle.TEXT_HINT, false);
        g.text(this.font, value, x + 62, y, GuiStyle.TEXT, false);
        return y + 12;
    }

    private int pair2(GuiGraphicsExtractor g, int x, int y, String key, Component value) {
        g.text(this.font, Component.translatable(key), x, y, GuiStyle.TEXT_HINT, false);
        g.text(this.font, value, x + 96, y, GuiStyle.TEXT, false);
        return y + 12;
    }

    // ---- frame ---------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        GuiStyle.panel(g, left, top, W, H);
        g.text(this.font, Component.translatable("screen.riverfishing.finder"),
                left + 10, top + 6, GuiStyle.TEXT, false);

        if (water().isEmpty()) {
            g.text(this.font, Component.translatable("message.riverfishing.no_water"),
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
        if (hover != null) g.setTooltipForNextFrame(this.font, hover, mouseX, mouseY);
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor g) {
        // §journal-blur: the panel is opaque, and the gaussian pass reads as a washed-out page.
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (clickedViewTab(mx, my)) {
            mapView = !mapView;
            return true;
        }
        if (detail != null) {
            detail = null;
            return true;
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double dy) {
        scroll -= (int) Math.signum(dy);
        return true;
    }
}
