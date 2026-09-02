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
    private static final int LAND = 0xFF2E3A22, LAND_TOP = 0xFF4A6034, WATER = 0xFF163A44;

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
        int shown = 0;
        for (CompoundTag t : here) {
            if (shown >= MAX_FISH) break;
            int[] at = placeFish(t, pd, taken);
            if (at == null) continue;
            taken.add(at);
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
            g.drawString(this.font, Component.translatable("finder.riverfishing.more_fish", here.size() - shown),
                    x0 + VIEW_W - 60, y0 + 4, 0x9940E0B0, false);
        }

        // What the bed is made of, read off the profile — the legend a real sounder prints.
        g.drawString(this.font, bedLegend(pd, pb), x0 + 4, y0 + VIEW_H - 11, 0x9940E0B0, false);
    }

    /**
     * Where to draw this species: the first metre out where the bed is deep enough for it, at the
     * depth it holds — clamped to the bed, so a bottom feeder sits ON the bottom rather than in it.
     * Then the nearest free cell around that, because two fish on one pixel are no fish at all.
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
        int wantX, bedHere;
        if (pd.length == 0) {
            wantX = x0 + 30 + (taken.size() * (ICON + 6)) % (VIEW_W - 60);
            bedHere = water().getInt("depth");
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
     * §sounding: the bed you have measured, from above, on the shape of the water itself. Water is
     * water-dark, bank is bank, a sounded cell reads deeper the darker, and you are the arrow —
     * pointing where you point. Unmeasured water is just water: an honest map has holes in it until
     * somebody casts across them, and the holes are the reason to keep casting.
     */
    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left + VIEW_X, y0 = top + VIEW_Y;
        g.fill(x0, y0, x0 + VIEW_W, y0 + VIEW_H, FACE);
        final int R = FishingManager.MAP_REACH, N = 2 * R + 1, CELL = 3;
        int cx = x0 + VIEW_W / 2, cy = y0 + VIEW_H / 2;
        int ox = cx - R * CELL, oy = cy - R * CELL;

        byte[] wet = data.getByteArray("wet");
        if (wet.length == N * N) {
            for (int dz = 0; dz < N; dz++) {
                for (int dx = 0; dx < N; dx++) {
                    int px = ox + dx * CELL, py = oy + dz * CELL;
                    if (py < y0 + 1 || py + CELL > y0 + VIEW_H - 1) continue;
                    g.fill(px, py, px + CELL, py + CELL, wet[dz * N + dx] != 0 ? WATER : LAND);
                }
            }
        }

        ListTag map = data.getList("map", 10);
        int deepest = 1;
        for (int i = 0; i < map.size(); i++) deepest = Math.max(deepest, map.getCompound(i).getInt("d"));
        for (int i = 0; i < map.size(); i++) {
            CompoundTag t = map.getCompound(i);
            int px = cx + t.getInt("x") * CELL, py = cy + t.getInt("z") * CELL;
            if (px < x0 + 1 || px + CELL > x0 + VIEW_W - 1 || py < y0 + 1 || py + CELL > y0 + VIEW_H - 1) continue;
            // One ramp from shallow to the deepest measured, so the SHAPE of the bed is what you
            // read rather than an absolute depth nobody can eyeball anyway.
            float f = t.getInt("d") / (float) deepest;
            int shade = 0xFF000000
                    | ((int) (0x20 + 0x18 * (1 - f)) << 16)
                    | ((int) (0x60 + 0x80 * (1 - f)) << 8)
                    | (int) (0x70 + 0x70 * (1 - f));
            g.fill(px, py, px + CELL, py + CELL, shade);
            if (t.contains("s")) {
                g.fill(px - 1, py - 1, px + CELL + 1, py + CELL + 1, 0xFFE8B430);
                g.fill(px, py, px + CELL, py + CELL, shade);
                if (mouseX >= px - 2 && mouseX < px + CELL + 2 && mouseY >= py - 2 && mouseY < py + CELL + 2) {
                    hover = List.of(Component.translatable("spot.riverfishing." + t.getString("s")).getVisualOrderText(),
                            Component.translatable("finder.riverfishing.metres", t.getInt("d")).getVisualOrderText());
                }
            }
        }

        // You: an arrow, pointing the way you were facing when you took the sounding.
        double yaw = Math.toRadians(data.getInt("yaw"));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);     // Minecraft: yaw 0 faces +z, 90 faces -x
        for (int k = 0; k < 7; k++) {
            int ax = (int) Math.round(cx + 1 + fx * k), ay = (int) Math.round(cy + 1 + fz * k);
            g.fill(ax - 1, ay - 1, ax + 1, ay + 1, 0xFFFF6060);
        }
        g.fill(cx - 2, cy - 2, cx + 4, cy + 4, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 3, cy + 3, 0xFFFF6060);

        if (map.isEmpty()) {
            for (var seq : this.font.split(Component.translatable("finder.riverfishing.unsounded"), VIEW_W - 20)) {
                g.drawString(this.font, seq, x0 + 10, y0 + 6, 0xCC40E0B0, false);
                y0 += 11;
            }
        }
        g.drawString(this.font, Component.translatable("finder.riverfishing.map"),
                left + VIEW_X + 4, top + VIEW_Y + VIEW_H - 11, 0x9940E0B0, false);
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
        this.renderBackground(g);
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
    public boolean mouseClicked(double mx, double my, int button) {
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
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dy) {
        scroll -= (int) Math.signum(dy);
        return true;
    }
}
