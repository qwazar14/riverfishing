# -*- coding: utf-8 -*-
"""§chart-far: the chart pulls back far enough to see the world, and the world has regions on it.

    py -X utf8 tools/patches/p_chartfar.py <root> [1211|1201|26]

The chart stopped at two pixels a block, which is a lake and a bit — fine for the water in front of
you and useless for the question §provinces invented: WHERE AM I, and where is the fish I cannot find.

So a zoom step is now {blocks per cell, pixels per cell} instead of a pixel count. Below the middle of
the table a cell is one block drawn big, exactly as before; above it a cell is many blocks drawn one
pixel, and the far end puts thirteen thousand blocks on the face — four faunal provinces across. The
soundings are folded down to the cell size once per zoom (deepest wins, so a lake stays a lake when a
pixel is thirty-two blocks of bank) rather than once per frame of a drag.

And the provinces are painted underneath: four darks with a gold line where two meet, the sounded bed
on top of them, and the name of the one under the middle of the chart in the corner. That needs a seed
on the client, which is the one thing worth being careful about — the WORLD seed is every structure and
every ore vein in it, so the server sends {@code Provinces.mapSeed}, a one-way scramble that draws the
same map and is not the same number.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")
GG = "GuiGraphicsExtractor" if D == "26" else "GuiGraphics"
TXT = "text" if D == "26" else "drawString"
GETLONG = (lambda k: 'getLongOr("%s", 0L)' % k) if D == "26" else (lambda k: 'getLong("%s")' % k)


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    assert old in s, "%s moved" % what
    return s.replace(old, new, 1)


# ================================================================================================
# 1. Provinces: a seed a client may be given
# ================================================================================================
p = J + "water/Provinces.java"
s = rd(p)
if "mapSeed" not in s:
    s = sub(s, """    /** The province of a block, for this world's seed. */
    public static String at(long seed, int x, int z) {
        return ALL[index(seed, x, z)];
    }

    /** 0..ALL.length-1, the same answer {@link #at} names. */
    public static int index(long seed, int x, int z) {""",
            """    /** The province of a block, for this world's seed. */
    public static String at(long worldSeed, int x, int z) {
        return ALL[index(mapSeed(worldSeed), x, z)];
    }

    /**
     * §chart-far: what a CLIENT is handed so its chart can draw the regions — a one-way scramble of the
     * world seed, and deliberately not the seed itself. The chart has to compute provinces at any
     * coordinate, which needs a seed; handing out the world's own would hand out every structure and
     * every ore vein in it to anybody who joins. This draws the same map and is not the same number.
     */
    public static long mapSeed(long worldSeed) {
        return mix(worldSeed ^ 0x50524F56494E4345L);
    }

    /** 0..ALL.length-1, the same answer {@link #at} names, off the seed {@link #mapSeed} derives. */
    public static int index(long seed, int x, int z) {""", "Provinces.at")
    wr(p, s)
    print("  Provinces: mapSeed")

# ================================================================================================
# 2. the server sends it with the sounding
# ================================================================================================
p = J + "fishing/FishingManager.java"
s = rd(p)
if "mapSeed" not in s:
    s = sub(s, '            w.putString("prov", env.province);',
            '''            w.putString("prov", env.province);
            // §chart-far: and the seed the chart paints the regions off. A scramble of the world's, on
            // purpose: the client needs the map, it does not need the world.
            w.putLong("seed", com.riverfishing.water.Provinces.mapSeed(level.getSeed()));''',
            "the finder payload's province line")
    wr(p, s)
    print("  FishingManager: the map seed goes out with the sounding")

# ================================================================================================
# 3. the client keeps it, and folds the bed down for the far zooms
# ================================================================================================
p = J + "client/ClientSoundings.java"
s = rd(p)
if "chart-far" not in s:
    s = sub(s, """    private static int version;
    private static int sounded;""",
            """    private static int version;
    private static int sounded;
    /**
     * §chart-far: the seed the chart draws the faunal provinces off — NOT the world seed, a one-way
     * scramble of it ({@link com.riverfishing.water.Provinces#mapSeed}). 0 until a sounding arrives,
     * which reads as "no regions on the chart yet".
     */
    private static long mapSeed;
    /** §chart-far: the bed folded down to a zoom's cell size, and which zoom and which data it is. */
    private static final Map<Long, Byte> coarse = new HashMap<>();
    private static int coarseStep = -1, coarseVersion = -1;""", "the version fields")

    s = sub(s, """    public static Map<Long, Byte> cells() {
        ensureLoaded();
        return cells;
    }""",
            """    public static Map<Long, Byte> cells() {
        ensureLoaded();
        return cells;
    }

    /** §chart-far: the seed for the province layer, or 0 if the server has not said yet. */
    public static long mapSeed() {
        ensureLoaded();
        return mapSeed;
    }

    /**
     * §chart-far: the same bed at {@code step} blocks a cell, DEEPEST WINS — the byte order is
     * LAND &lt; WATER &lt; a depth, so a lake stays a lake when one pixel is thirty-two blocks of bank.
     * Built once per zoom and kept until the soundings change: a drag at the far end would otherwise
     * fold every column a player has ever sounded, every frame.
     */
    public static Map<Long, Byte> cells(int step) {
        ensureLoaded();
        if (step <= 1) return cells;
        if (step == coarseStep && version == coarseVersion) return coarse;
        coarse.clear();
        for (Map.Entry<Long, Byte> e : cells.entrySet()) {
            long k = key(Math.floorDiv(keyX(e.getKey()), step), Math.floorDiv(keyZ(e.getKey()), step));
            Byte had = coarse.get(k);
            if (had == null || e.getValue() > had) coarse.put(k, e.getValue());
        }
        coarseStep = step;
        coarseVersion = version;
        return coarse;
    }""", "cells()")

    s = sub(s, """        int cx = w.%s, cz = w.%s;
        boolean changed = false;""" % (
                'getIntOr("x", 0)' if D == "26" else 'getInt("x")',
                'getIntOr("z", 0)' if D == "26" else 'getInt("z")'),
            """        int cx = w.%s, cz = w.%s;
        boolean changed = false;
        long seed = w.%s;                        // §chart-far
        if (seed != 0 && seed != mapSeed) {
            mapSeed = seed;
            changed = true;
        }""" % ('getIntOr("x", 0)' if D == "26" else 'getInt("x")',
                'getIntOr("z", 0)' if D == "26" else 'getInt("z")',
                GETLONG("seed")), "merge()'s centre")

    s = sub(s, """        deepest = 1;
        target = null;
        sounded = 0;""", """        deepest = 1;
        target = null;
        sounded = 0;
        mapSeed = 0;
        coarseStep = -1;""", "ensureLoaded's reset")
    HT = ('getBooleanOr("ht", false)', 'getLongOr("t", 0L)') if D == "26" \
        else ('getBoolean("ht")', 'getLong("t")')
    s = sub(s, '            if (t.%s) target = t.%s;' % HT,
            '            if (t.%s) target = t.%s;\n            mapSeed = t.%s;   // §chart-far'
            % (HT[0], HT[1], GETLONG("seed")), "the target load")
    s = sub(s, '            if (target != null) t.putLong("t", target);',
            '            if (target != null) t.putLong("t", target);\n            t.putLong("seed", mapSeed);',
            "the target save")
    wr(p, s)
    print("  ClientSoundings: the map seed, kept; the bed, folded")

# ================================================================================================
# 4. the chart itself
# ================================================================================================
p = J + "client/FinderScreen.java"
s = rd(p)
if "chart-far" in s:
    print("  FinderScreen already patched")
    print("done (%s)" % D)
    sys.exit(0)

s = sub(s, """    /** §depth-map: the chart's centre in world blocks, and pixels per block. */
    private double mapCx, mapCz;
    private int zoom = 4;""",
        """    /** §depth-map: the chart's centre in world blocks. */
    private double mapCx, mapCz;
    /**
     * §chart-far: one zoom step, as {blocks per cell, pixels per cell}. It used to be a pixel count,
     * which put a floor of about a lake on how far back the chart could stand — and §provinces then
     * asked a question a lake cannot answer. Below the middle of this table a cell is one block drawn
     * big, exactly as it always was; above it a cell is many blocks drawn one pixel. The far end is
     * thirteen thousand blocks across the face, which is four faunal provinces.
     */
    private static final int[][] STEPS = {
            {64, 1}, {32, 1}, {16, 1}, {8, 1}, {4, 1}, {2, 1}, {1, 1}, {1, 2}, {1, 3}, {1, 4}, {1, 6}, {1, 8}};
    /** Index into {@link #STEPS}: {1, 4}, four pixels a block, where the chart has always opened. */
    private int zoom = 9;
    /**
     * §chart-far: the faunal provinces, painted under the bed — in {@link com.riverfishing.water.Provinces#ALL}
     * order. Four darks the water sits on top of, so a sounded lake still reads as a lake, and a gold
     * line where two meet, because a region map is mostly its borders.
     */
    private static final int[] PROV = {0xFF101C2C, 0xFF101F14, 0xFF241609, 0xFF1E132A};
    private static final int PROV_EDGE = 0xAAE8B430;
    /** Cells between province samples. A Voronoi is smooth and a border is three thousand blocks long. */
    private static final int PROV_STRIDE = 2;

    private int step() {
        return STEPS[zoom][0];
    }

    private int cellPx() {
        return STEPS[zoom][1];
    }

    /** Pixels per world block — the one number the marks, the grid and you are all placed by. */
    private double ppb() {
        return (double) STEPS[zoom][1] / STEPS[zoom][0];
    }""", "the zoom field")

s = sub(s, """    private int chartStartX, chartStartZ, chartCols, chartRows, chartZoom = -1, chartVersion = -1;""",
        """    private int chartStartX, chartStartZ, chartCols, chartRows, chartZoom = -1, chartVersion = -1;
    /** §chart-far: the province under the middle of the chart, plus one; 0 when the layer is off. */
    private int chartProv;""", "the chart cache fields")

# ---- renderMap: the window ----------------------------------------------------------------------
s = sub(s, """        int x0 = left + VIEW_X, y0 = top + VIEW_Y, w = W - 2 * VIEW_X, h = VIEW_H;
        g.fill(x0, y0, x0 + w, y0 + h, FACE);
        java.util.Map<Long, Byte> cells = ClientSoundings.cells();
        int z = zoom;
        int cols = w / z + 2, rows = h / z + 2;
        int startX = (int) Math.floor(mapCx - (w / 2.0) / z), startZ = (int) Math.floor(mapCz - (h / 2.0) / z);
        if (chartCells == null || chartStartX != startX || chartStartZ != startZ || chartCols != cols
                || chartRows != rows || chartZoom != z || chartVersion != ClientSoundings.version()) {
            buildChart(startX, startZ, cols, rows, z);
        }""",
        """        int x0 = left + VIEW_X, y0 = top + VIEW_Y, w = W - 2 * VIEW_X, h = VIEW_H;
        g.fill(x0, y0, x0 + w, y0 + h, FACE);
        java.util.Map<Long, Byte> cells = ClientSoundings.cells();
        int step = step(), cp = cellPx();
        double ppb = ppb();
        // §chart-far: where a world block lands on the face — ox + x * ppb, and that is the whole of the
        // projection. A cell is `step` blocks, so cell c starts at ox + (startX + c) * cp exactly.
        double ox = x0 + w / 2.0 - mapCx * ppb, oz = y0 + h / 2.0 - mapCz * ppb;
        int cols = w / cp + 2, rows = h / cp + 2;
        int startX = (int) Math.floor(mapCx / step - (w / 2.0) / cp);
        int startZ = (int) Math.floor(mapCz / step - (h / 2.0) / cp);
        if (chartCells == null || chartStartX != startX || chartStartZ != startZ || chartCols != cols
                || chartRows != rows || chartZoom != zoom || chartVersion != ClientSoundings.version()) {
            buildChart(startX, startZ, cols, rows, zoom);
        }""", "the chart window")

# ---- renderMap: the cells and the edges ----------------------------------------------------------
s = sub(s, """        for (int r = 0; r < rows; r++) {
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
        }""",
        """        for (int r = 0; r < rows; r++) {
            int py = (int) Math.floor(oz + (startZ + r) * (double) cp);
            int cy1 = Math.max(py, y0), py2 = Math.min(py + cp, y0 + h);
            if (cy1 >= py2) continue;
            for (int c = 0; c < cols; ) {
                int colour = chartCells[r * cols + c];
                if (colour == 0) { c++; continue; }
                int c2 = c;
                while (c2 + 1 < cols && chartCells[r * cols + c2 + 1] == colour) c2++;
                int px = (int) Math.floor(ox + (startX + c) * (double) cp);
                int px2 = Math.min(px + (c2 - c + 1) * cp, x0 + w);
                if (px < px2) g.fill(Math.max(px, x0), cy1, px2, py2, colour);
                c = c2 + 1;
            }
            for (int c = 0; c < cols; c++) {
                byte e = chartEdges[r * cols + c];
                if (e == 0) continue;
                int px = (int) Math.floor(ox + (startX + c) * (double) cp);
                int px2 = Math.min(px + cp, x0 + w), cx1 = Math.max(px, x0);
                if (cx1 >= px2) continue;
                // §chart-far: a province border draws at every zoom — at the far end it is the only
                // thing on the face. A depth contour needs a cell big enough to put a line inside.
                if ((e & 4) != 0) g.fill(px2 - 1, cy1, px2, py2, PROV_EDGE);
                if ((e & 8) != 0) g.fill(cx1, py2 - 1, px2, py2, PROV_EDGE);
                if (cp < 3) continue;
                // Contour: a lighter edge wherever the next cell over is a deeper band.
                if ((e & 1) != 0) g.fill(px2 - 1, cy1, px2, py2, CHART_CONTOUR);
                if ((e & 2) != 0) g.fill(cx1, py2 - 1, px2, py2, CHART_CONTOUR);
            }
        }""", "the cell loop")

# ---- renderMap: the grid -------------------------------------------------------------------------
s = sub(s, """        // Chunk grid, faint — the face's own teal, the way the section rules its depths.
        for (int gx = (startX / 16) * 16; gx < startX + cols; gx += 16) {
            int px = x0 + (int) Math.floor((gx - mapCx) * z + w / 2.0);
            if (px > x0 && px < x0 + w) g.fill(px, y0 + 1, px + 1, y0 + h - 1, GRID);
        }
        for (int gz = (startZ / 16) * 16; gz < startZ + rows; gz += 16) {
            int py = y0 + (int) Math.floor((gz - mapCz) * z + h / 2.0);
            if (py > y0 && py < y0 + h) g.fill(x0 + 1, py, x0 + w - 1, py + 1, GRID);
        }""",
        """        // Chunk grid, faint — the face's own teal, the way the section rules its depths. §chart-far:
        // it coarsens with the zoom, sixteen blocks on a swim and a thousand on a continent, or the
        // whole face would be grid.
        int grid = 16 * step;
        for (int gx = Math.floorDiv(startX * step, grid) * grid; gx < (startX + cols) * step; gx += grid) {
            int px = (int) Math.floor(ox + gx * ppb);
            if (px > x0 && px < x0 + w) g.fill(px, y0 + 1, px + 1, y0 + h - 1, GRID);
        }
        for (int gz = Math.floorDiv(startZ * step, grid) * grid; gz < (startZ + rows) * step; gz += grid) {
            int py = (int) Math.floor(oz + gz * ppb);
            if (py > y0 && py < y0 + h) g.fill(x0 + 1, py, x0 + w - 1, py + 1, GRID);
        }""", "the chunk grid")

# ---- renderMap: the marks and you ------------------------------------------------------------------
s = sub(s, """            int px = x0 + (int) Math.floor((m[0] - mapCx) * z + w / 2.0) + z / 2;
            int py = y0 + (int) Math.floor((m[1] - mapCz) * z + h / 2.0) + z / 2;""",
        """            int px = (int) Math.floor(ox + m[0] * ppb) + cp / 2;
            int py = (int) Math.floor(oz + m[1] * ppb) + cp / 2;""", "the mark placement")

s = sub(s, """            int px = x0 + (int) Math.floor((mc.player.getX() - mapCx) * z + w / 2.0);
            int py = y0 + (int) Math.floor((mc.player.getZ() - mapCz) * z + h / 2.0);""",
        """            int px = (int) Math.floor(ox + mc.player.getX() * ppb);
            int py = (int) Math.floor(oz + mc.player.getZ() * ppb);""", "the player marker")
s = sub(s, "                for (int k = 0; k < 4 * z; k++) {",
        "                for (int k = 0; k < Math.max(8, 4 * cp); k++) {", "the heading whisker")

# ---- renderMap: the scale bar and the region's name ------------------------------------------------
s = sub(s, """        // Scale bar, ten metres at this zoom; and how much of the world is on the chart.
        int sx = x0 + 6, sy = y0 + h - 8;
        g.fill(sx, sy, sx + 10 * z, sy + 2, SURFACE);
        g.fill(sx, sy - 2, sx + 1, sy + 3, SURFACE);
        g.fill(sx + 10 * z - 1, sy - 2, sx + 10 * z, sy + 3, SURFACE);
        g.%s(this.font, Component.translatable("finder.riverfishing.metres", 10), sx + 2, sy - 12, 0x9940E0B0, false);""" % TXT,
        """        // Scale bar; and how much of the world is on the chart. §chart-far: the distance is chosen
        // to come out between forty and a couple of hundred pixels, so the ruler stays a ruler whether
        // the face holds a swim or four provinces.
        int metres = 10;
        for (int m : new int[]{10, 25, 50, 100, 250, 500, 1000, 2500, 5000}) {
            metres = m;
            if (m * ppb >= 40) break;
        }
        int bar = Math.max(10, (int) Math.round(metres * ppb));
        int sx = x0 + 6, sy = y0 + h - 8;
        g.fill(sx, sy, sx + bar, sy + 2, SURFACE);
        g.fill(sx, sy - 2, sx + 1, sy + 3, SURFACE);
        g.fill(sx + bar - 1, sy - 2, sx + bar, sy + 3, SURFACE);
        g.%s(this.font, Component.translatable("finder.riverfishing.metres", metres), sx + 2, sy - 12, 0x9940E0B0, false);
        // §chart-far: and what part of the world the middle of it is. The colours say where the borders
        // are; this says what they are, which is the half a player can act on.
        if (chartProv != 0) {
            Component region = Component.translatable(
                    "province.riverfishing." + com.riverfishing.water.Provinces.ALL[chartProv - 1]);
            g.%s(this.font, region, x0 + w - this.font.width(region) - 6, y0 + 5, 0xCCE8B430, false);
        }""" % (TXT, TXT), "the scale bar")

# ---- buildChart -------------------------------------------------------------------------------------
s = sub(s, """    private void buildChart(int startX, int startZ, int cols, int rows, int z) {
        chartStartX = startX;
        chartStartZ = startZ;
        chartCols = cols;
        chartRows = rows;
        chartZoom = z;
        chartVersion = ClientSoundings.version();
        java.util.Map<Long, Byte> cells = ClientSoundings.cells();
        int deepest = Math.max(1, ClientSoundings.deepest());
        chartCells = new int[cols * rows];
        chartEdges = new byte[cols * rows];""",
        """    private void buildChart(int startX, int startZ, int cols, int rows, int zoomLevel) {
        chartStartX = startX;
        chartStartZ = startZ;
        chartCols = cols;
        chartRows = rows;
        chartZoom = zoomLevel;
        chartVersion = ClientSoundings.version();
        int step = STEPS[zoomLevel][0];
        java.util.Map<Long, Byte> cells = ClientSoundings.cells(step);
        int deepest = Math.max(1, ClientSoundings.deepest());
        chartCells = new int[cols * rows];
        chartEdges = new byte[cols * rows];

        // §chart-far: the provinces first, as the ground everything else is drawn on. Only when the face
        // is wide enough for a border to be on it — under a thousand blocks the layer is one flat colour
        // and the sample view already names it — and only once the server has sent the map's seed.
        long seed = ClientSoundings.mapSeed();
        chartProv = 0;
        if (seed != 0 && (long) cols * step >= 1024) {
            int[] pr = new int[cols * rows];
            for (int r = 0; r < rows; r += PROV_STRIDE) {
                for (int c = 0; c < cols; c += PROV_STRIDE) {
                    int v = com.riverfishing.water.Provinces.index(
                            seed, (startX + c) * step, (startZ + r) * step) + 1;
                    for (int rr = r; rr < Math.min(rows, r + PROV_STRIDE); rr++) {
                        for (int cc = c; cc < Math.min(cols, c + PROV_STRIDE); cc++) pr[rr * cols + cc] = v;
                    }
                }
            }
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int v = pr[r * cols + c];
                    chartCells[r * cols + c] = PROV[(v - 1) % PROV.length];
                    int e = 0;
                    if (c + 1 < cols && pr[r * cols + c + 1] != v) e |= 4;
                    if (r + 1 < rows && pr[(r + 1) * cols + c] != v) e |= 8;
                    chartEdges[r * cols + c] = (byte) e;
                }
            }
            chartProv = pr[(rows / 2) * cols + cols / 2];
        }""", "buildChart's head")

s = sub(s, """                    chartEdges[r * cols + c] = (byte) e;""",
        """                    chartEdges[r * cols + c] |= (byte) e;   // §chart-far: the province border keeps its bits""",
        "the contour bits")

s = sub(s, """            int gx = ClientSoundings.keyX(e.getKey()), gz = ClientSoundings.keyZ(e.getKey());
            if (gx < startX || gx >= startX + cols || gz < startZ || gz >= startZ + rows) continue;""",
        """            int gx = ClientSoundings.keyX(e.getKey()), gz = ClientSoundings.keyZ(e.getKey());
            // §chart-far: the mark is a world column; the window is in cells.
            int mcx = Math.floorDiv(gx, step), mcz = Math.floorDiv(gz, step);
            if (mcx < startX || mcx >= startX + cols || mcz < startZ || mcz >= startZ + rows) continue;""",
        "the mark window")

# ---- pan and zoom -------------------------------------------------------------------------------
s = sub(s, """            mapCx -= dx / zoom;
            mapCz -= dy / zoom;""",
        """            mapCx -= dx / ppb();
            mapCz -= dy / ppb();""", "the drag")

s = sub(s, """            // Five steps of zoom. Two pixels a block shows a whole lake; eight shows a swim.
            int[] steps = {2, 3, 4, 6, 8};
            int i = 0;
            for (int k = 0; k < steps.length; k++) if (steps[k] == zoom) i = k;
            i = Mth.clamp(i + (int) Math.signum(dy), 0, steps.length - 1);
            zoom = steps[i];
            return true;""",
        """            // §chart-far: twelve steps, from eight pixels a block to sixty-four blocks a pixel — a
            // swim at one end and four faunal provinces at the other.
            zoom = Mth.clamp(zoom + (int) Math.signum(dy), 0, STEPS.length - 1);
            return true;""", "the zoom wheel")

wr(p, s)
print("  FinderScreen: %d zoom steps, the province layer, an adaptive grid and ruler" % 12)
print("done (%s)" % D)
