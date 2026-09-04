# -*- coding: utf-8 -*-
"""§chart-item: the depth chart belongs to a SOUNDER, not to a player.

    py -X utf8 tools/patches/p_chartitem.py <root> [1211|1201|26]

Until now every sounding a player ever took went into one pile per world, kept on that player's disk
and shown by whatever finder they happened to be holding. That is fine for one person fishing alone and
wrong for a server: survey work is WORK, and work you cannot lose, hand over or sell is not work, it is
a wiki page.

So a fish finder mints an id the first time it is used, keeps it in its own stack data, and the chart
is filed under it. One sounder, one chart. Buy a second and it starts blank. Lose the sounder in lava
and the chart goes with it — which is exactly what makes a surveyed one worth something.

Groundwork, and deliberately only that: the chart still LIVES on the client, so handing a sounder to
another player hands over the id but not yet the bed behind it. Moving the accumulated chart to the
server, under the same id, is the next step and needs nothing here to change — which is the point of
minting the id now.

The first sounder used in a world inherits the chart that was already kept per-world, and the old file
is renamed as it is taken, so a second sounder does not inherit it too.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")
GETS = (lambda k: 'getStringOr("%s", "")' % k) if D == "26" else (lambda k: 'getString("%s")' % k)
ADD = "tooltip.accept" if D == "26" else "tooltip.add"


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    assert old in s, "%s moved" % what
    return s.replace(old, new, 1)


# ---- 1. the id itself ---------------------------------------------------------------------------
CHART = '''package com.riverfishing.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * §chart-item: which sounder a depth chart belongs to.
 *
 * <p>The chart used to be filed per player per world, which made a surveyed lake a property of the
 * ACCOUNT — it could not be lost, lent, inherited or sold, and on a server that is the difference
 * between a map being work and a map being a wiki page. A finder mints an id the first time it is
 * used and carries it in its own stack data; the chart is filed under that id. One sounder, one
 * chart. A second finder starts blank, and a finder that goes in the lava takes its survey with it.
 *
 * <p>The id is twelve hex characters — short enough to read off a tooltip and tell two sounders
 * apart, long enough that no server will ever collide two.
 */
public final class FinderChart {

    /** The stack-data key. Capitalised like every other key the mod writes. */
    private static final String KEY = "Chart";

    private FinderChart() {}

    /** The id already on this stack, or empty. Never mints — safe on the client. */
    public static String of(ItemStack stack) {
        return StackNbt.get(stack).%s;
    }

    /**
     * …and the server's way in: the id, minting one if this sounder has never been used. Called where
     * a sounding is actually taken, so a finder that has never seen water carries nothing.
     */
    public static String mint(ItemStack stack) {
        String id = of(stack);
        if (!id.isEmpty()) return id;
        String fresh = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        StackNbt.mutate(stack, t -> t.putString(KEY, fresh));
        return fresh;
    }

    /** The player-facing finder in this player's hands, or EMPTY. The admin probe is not one. */
    public static ItemStack held(Player p) {
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof WaterProbeItem w && !w.admin()) return main;
        ItemStack off = p.getOffhandItem();
        if (off.getItem() instanceof WaterProbeItem w && !w.admin()) return off;
        return ItemStack.EMPTY;
    }
}
''' % GETS("Chart")

p = J + "item/FinderChart.java"
if not os.path.exists(p):
    wr(p, CHART)
    print("  item/FinderChart.java")

# ---- 2. the sounding says whose chart it is ------------------------------------------------------
p = J + "fishing/FishingManager.java"
s = rd(p)
if "chart-item" not in s:
    s = sub(s, '            w.putLong("seed", com.riverfishing.water.Provinces.mapSeed(level.getSeed()));',
            '''            w.putLong("seed", com.riverfishing.water.Provinces.mapSeed(level.getSeed()));
            // §chart-item: and WHOSE chart this sounding belongs on. Only a full sounding carries it —
            // the strip a rod puts on the HUD must never switch the chart under a player who is fishing
            // with a finder in the other hand. Minting here means a finder that has never seen water
            // has no id, so an unused one off the shelf is blank rather than pre-registered.
            ItemStack finder = com.riverfishing.item.FinderChart.held(sp);
            if (!finder.isEmpty()) {
                w.putString("chart", com.riverfishing.item.FinderChart.mint(finder));
            }''', "the finder payload's seed line")
    wr(p, s)
    print("  FishingManager: the payload names the sounder")

# ---- 3. the client files it under that id --------------------------------------------------------
p = J + "client/ClientSoundings.java"
s = rd(p)
if "chart-item" not in s:
    s = sub(s, "    private static String loadedFor;",
            """    private static String loadedFor;
    /**
     * §chart-item: the sounder whose chart is loaded, or empty for the old per-world pile. Set by the
     * sounding itself — the server names the finder that took it — so putting one sounder away and
     * drawing another swaps charts on the next reading rather than on a guess about which hand.
     */
    private static String chart = "";
    /** The world the loaded chart belongs to, so joining another server does not carry an id over. */
    private static String world;""", "loadedFor")

    s = sub(s, """    /** Fold one sounding's windows in: the water mask and the sounded cells, both around its centre. */
    public static void merge(CompoundTag data) {
        ensureLoaded();""",
            """    /** Which sounder's chart is on screen, or empty. */
    public static String chart() {
        ensureLoaded();
        return chart;
    }

    /**
     * §chart-item: move to this sounder's chart, banking the one we were on. Empty means "the server
     * said nothing", which happens for every payload that did not come from a finder — those must
     * leave the chart alone rather than reset it.
     */
    private static void select(String id) {
        if (id.isEmpty() || id.equals(chart)) return;
        if (loadedFor != null) save();
        chart = id;
        loadedFor = null;               // …so ensureLoaded swaps the store instead of skipping
    }

    /** Fold one sounding's windows in: the water mask and the sounded cells, both around its centre. */
    public static void merge(CompoundTag data) {
        select(data.%s.%s);             // §chart-item: before the load, or it lands in the last chart
        ensureLoaded();""" % ('getCompoundOrEmpty("water")' if D == "26" else 'getCompound("water")',
                              GETS("chart")), "merge()")

    s = sub(s, """    private static void ensureLoaded() {
        String k = worldKey();
        if (k == null || k.equals(loadedFor)) return;""",
            """    private static void ensureLoaded() {
        String wk = worldKey();
        // §chart-item: a different world is a different chart, whatever sounder is in the bag.
        if (wk != null && !wk.equals(world)) {
            world = wk;
            chart = "";
        }
        String k = wk == null || chart.isEmpty() ? wk : wk + "_" + chart;
        if (k == null || k.equals(loadedFor)) return;""", "ensureLoaded")

    s = sub(s, """        try {
            Path p = file();
            if (!Files.exists(p)) return;""",
            """        try {
            Path p = file();
            // §chart-item: the first sounder used in a world inherits the pile that was kept per world,
            // and TAKES it — renamed as it goes, so the second sounder starts blank the way it should.
            if (!Files.exists(p) && !chart.isEmpty()) {
                Path old = p.getParent().resolve(world + ".nbt");
                if (Files.exists(old)) {
                    try {
                        Files.move(old, p);
                    } catch (Exception ignored) {
                        // an unreadable legacy chart is not worth failing a world join over
                    }
                }
            }
            if (!Files.exists(p)) return;""", "the load")
    wr(p, s)
    print("  ClientSoundings: one chart per sounder, the old pile inherited once")

# ---- 4. the sounder says so on its tooltip -------------------------------------------------------
p = J + "item/WaterProbeItem.java"
s = rd(p)
if "chart-item" not in s:
    old = """                ? "tooltip.riverfishing.hydro_probe" : "tooltip.riverfishing.fish_finder")
                .withStyle(ChatFormatting.DARK_GRAY));"""
    s = sub(s, old, old + """
        // §chart-item: a surveyed sounder is worth more than a new one, so it has to be possible to
        // tell them apart in a chest without plugging each one in.
        String chart = FinderChart.of(stack);
        if (!admin && !chart.isEmpty()) {
            %s(Component.translatable("tooltip.riverfishing.chart", chart)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }""" % ADD, "the finder tooltip")
    wr(p, s)
    print("  WaterProbeItem: the chart id on the tooltip")

# ---- 5. lang -------------------------------------------------------------------------------------
NAMES = {"en_us": "Chart %s", "ru_ru": "Карта дна %s", "uk_ua": "Карта дна %s"}
for loc, text in NAMES.items():
    p = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang", loc + ".json")
    s = rd(p)
    if '"tooltip.riverfishing.chart"' in s:
        continue
    i = s.index('"tooltip.riverfishing.fish_finder":')
    wr(p, s[:i] + '"tooltip.riverfishing.chart": "%s",\n  ' % text + s[i:])
    print("  lang %s" % loc)
print("done (%s)" % D)
