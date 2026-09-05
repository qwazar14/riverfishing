# -*- coding: utf-8 -*-
"""§chart-server: the sounder's chart lives in the world save, so handing over the sounder hands over the bed.

    py -X utf8 tools/patches/p_chartserver.py <root> [1211|1201|26]

§chart-item minted the id and filed the chart under it — on the CLIENT, which meant a traded sounder
carried its name and nothing behind it. This is the other half: the accumulated chart now lives in the
world's own data storage under that id, and the first time a player takes a sounding with a sounder they
have not been handed this session, the server streams them everything that sounder knows.

Three decisions worth writing down.

WHAT IS STORED is the chart the client draws, column for column — LAND, WATER, or a depth — and not the
soundings that produced it. The bed itself is already in SoundingData, world-wide and shared; what makes
a chart is which columns THIS sounder has actually seen, and the water mask around them, which cannot be
recomputed later because the chunks may be gone.

STREAMED, NOT SYNCED. The chart goes out in packets of {CHUNK} columns, once per player per sounder per
session, on the sounding that used it. No version numbers, no client request, no delta: a sounding is the
only way to open the chart, so it is the only moment worth spending traffic on.

MERGED, NOT REPLACED. What arrives is folded into whatever that client already had, by the same rule the
client has always used — a depth beats water, water beats land, and nothing ever goes backwards. That is
what lets an existing per-world chart and a fresh server-side one coexist while the two converge, and it
means a lost packet costs detail rather than correctness.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")
CHUNK = 8192

GS = (lambda t, k, d: '%s.getStringOr("%s", "%s")' % (t, k, d)) if D == "26" \
    else (lambda t, k, d: '%s.getString("%s")' % (t, k))
GI = (lambda t, k: '%s.getIntOr("%s", 0)' % (t, k)) if D == "26" else (lambda t, k: '%s.getInt("%s")' % (t, k))
GL = (lambda t, k: '%s.getListOrEmpty("%s")' % (t, k)) if D == "26" else (lambda t, k: '%s.getList("%s", 10)' % (t, k))
GC = (lambda t, i: '%s.getCompoundOrEmpty(%s)' % (t, i)) if D == "26" else (lambda t, i: '%s.getCompound(%s)' % (t, i))
GBA = (lambda t, k: '%s.getByteArray("%s").orElse(new byte[0])' % (t, k)) if D == "26" \
    else (lambda t, k: '%s.getByteArray("%s")' % (t, k))
GLA = (lambda t, k: '%s.getLongArray("%s").orElse(new long[0])' % (t, k)) if D == "26" \
    else (lambda t, k: '%s.getLongArray("%s")' % (t, k))
HAS = (lambda t, k: '%s.getBooleanOr("%s", false)' % (t, k)) if D == "26" \
    else (lambda t, k: '%s.getBoolean("%s")' % (t, k))

if D == "26":
    STORAGE = '''    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now.
    private static final net.minecraft.world.level.saveddata.SavedDataType<ChartData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("riverfishing", "charts"),
                    ChartData::new,
                    CompoundTag.CODEC.xmap(t -> ChartData.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);

    public static ChartData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }'''
    LOAD_SIG = "public static ChartData load(CompoundTag tag, HolderLookup.Provider registries) {"
    SAVE_SIG = "public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {"
elif D == "1201":
    STORAGE = '''    public static ChartData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ChartData::load, ChartData::new, NAME);
    }'''
    LOAD_SIG = "public static ChartData load(CompoundTag tag) {"
    SAVE_SIG = "@Override\n    public CompoundTag save(CompoundTag tag) {"
else:
    STORAGE = '''    public static ChartData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChartData::new, ChartData::load,
                        (net.minecraft.util.datafix.DataFixTypes) null), NAME);
    }'''
    LOAD_SIG = "public static ChartData load(CompoundTag tag, HolderLookup.Provider registries) {"
    SAVE_SIG = "@Override\n    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {"

CHART_DATA = '''package com.riverfishing.fishing;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * §chart-server: the depth chart a sounder has built, kept in the world save under the sounder's own id.
 *
 * <p>§chart-item gave every fish finder twelve hex characters of identity so a chart could belong to an
 * OBJECT rather than to an account. This is where the chart then lives. A sounding is folded in here as
 * well as drawn, and the first time a player sounds with a finder they have not been handed this session
 * the whole of that finder's chart is streamed to them. Buy a surveyed sounder off another player and you
 * get the survey; drop it in the lava and the survey goes with it. That is the point of the exercise.
 *
 * <p>What is stored is the chart AS DRAWN — one byte a column, land, water or a depth — and not the
 * soundings that produced it. The bed itself is already in {@link SoundingData}, world-wide and shared by
 * everyone; a chart is the subset one sounder has actually seen, plus the water mask around it, and that
 * mask cannot be recomputed later because the chunks it was read from may be long gone.
 *
 * <p>ponytail: a chart is uncapped, like the client's own file. A hundred thousand columns is under a
 * megabyte and a busy angler is nowhere near that; if a public server ever collects hundreds of surveyed
 * sounders, bucket this by chunk the way SoundingData already is, or age the unused ones out.
 */
public final class ChartData extends SavedData {
    private static final String NAME = "riverfishing_charts";

    /**
     * The three values a column can hold. THE SAME NUMBERS the client's chart draws by
     * ({@code ClientSoundings}) — they go out on the wire as they are, so the two must agree, and
     * tools/check_chart_sync.py holds them to it. Named here because a dedicated server must not touch
     * a client class to find out what a byte means.
     */
    public static final byte LAND = 1, WATER = 2, DEPTH0 = 3;

    /** How many columns go in one packet. Eight thousand is about seventy kilobytes — a safe parcel. */
    private static final int CHUNK = %(CHUNK)d;

    /** chart id -> column key -> LAND, WATER or DEPTH0 + depth. */
    private final Map<String, Map<Long, Byte>> charts = new HashMap<>();
    /** chart id -> column key -> 0 a hole, 1 a drop-off. */
    private final Map<String, Map<Long, Byte>> marks = new HashMap<>();

    /**
     * Who has already been handed which chart, this run of the server. Not saved and not meant to be:
     * a fresh session re-streaming a chart costs one parcel of traffic, and remembering it across a
     * restart would cost a player their chart the one time it mattered.
     */
    private static final Map<UUID, Set<String>> SENT = new HashMap<>();

%(STORAGE)s

    /** One column, x and z packed — the same key the client files by. */
    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /**
     * Fold one sounding into the sounder's chart, and hand over what that sounder already knew if this
     * player has not been given it yet. Takes the payload the client is about to be sent, so the two
     * sides are looking at exactly the same window and there is no second place to keep in step.
     */
    public static void record(ServerPlayer sp, ServerLevel level, CompoundTag payload) {
        CompoundTag w = %(WATER)s;
        String id = %(CHART)s;
        if (id.isEmpty()) return;
        ChartData d = get(level);
        d.push(sp, id);
        d.merge(id, %(WX)s, %(WZ)s, %(WET)s, %(MAP)s);
    }

    /** Everything a player owns a copy of again — call it when they leave, so the set does not grow. */
    public static void forget(UUID player) {
        SENT.remove(player);
    }

    private void merge(String id, int cx, int cz, byte[] wet, ListTag map) {
        Map<Long, Byte> cells = charts.computeIfAbsent(id, k -> new HashMap<>());
        boolean changed = false;
        int r = FishingManager.MAP_REACH, n = 2 * r + 1;
        if (wet.length == n * n) {
            for (int dz = 0; dz < n; dz++) {
                for (int dx = 0; dx < n; dx++) {
                    long k = key(cx + dx - r, cz + dz - r);
                    byte v = wet[dz * n + dx] != 0 ? WATER : LAND;
                    Byte old = cells.get(k);
                    // A mask never overwrites a measured depth: it knows less than the cast that took it.
                    if (old == null || (old < DEPTH0 && old != v)) {
                        cells.put(k, v);
                        changed = true;
                    }
                }
            }
        }
        for (int i = 0; i < map.size(); i++) {
            CompoundTag t = %(MAPI)s;
            long k = key(cx + %(TX)s, cz + %(TZ)s);
            cells.put(k, (byte) Math.min(127, DEPTH0 + %(TD)s));
            if (%(HASS)s) {
                marks.computeIfAbsent(id, x -> new HashMap<>())
                        .put(k, (byte) ("hole".equals(%(TS)s) ? 0 : 1));
            }
            changed = true;
        }
        if (changed) setDirty();
    }

    /**
     * Stream a chart to a player who has not been given it this session. Silent when the sounder is new
     * — there is nothing to hand over — and silent the second time, which is the common case.
     */
    private void push(ServerPlayer sp, String id) {
        if (!SENT.computeIfAbsent(sp.getUUID(), u -> new HashSet<>()).add(id)) return;
        Map<Long, Byte> cells = charts.get(id);
        if (cells == null || cells.isEmpty()) return;
        long[] ks = new long[Math.min(CHUNK, cells.size())];
        byte[] vs = new byte[ks.length];
        int i = 0;
        for (Map.Entry<Long, Byte> e : cells.entrySet()) {
            ks[i] = e.getKey();
            vs[i++] = e.getValue();
            if (i == ks.length) {
                send(sp, id, ks, vs, i, false);
                i = 0;
            }
        }
        // The last parcel carries the marks, so a chart is never half-marked on the client.
        send(sp, id, ks, vs, i, true);
    }

    private void send(ServerPlayer sp, String id, long[] ks, byte[] vs, int n, boolean last) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("chartsync", true);
        t.putString("chart", id);
        // Copied, always: an NBT array tag keeps the REFERENCE, the packet is encoded on the netty
        // thread, and the loop above is about to refill this buffer for the next parcel.
        t.putLongArray("k", java.util.Arrays.copyOf(ks, n));
        t.putByteArray("v", java.util.Arrays.copyOf(vs, n));
        t.putBoolean("last", last);
        if (last) {
            Map<Long, Byte> m = marks.get(id);
            if (m != null && !m.isEmpty()) {
                long[] sk = new long[m.size()];
                byte[] sv = new byte[m.size()];
                int j = 0;
                for (Map.Entry<Long, Byte> e : m.entrySet()) {
                    sk[j] = e.getKey();
                    sv[j++] = e.getValue();
                }
                t.putLongArray("sk", sk);
                t.putByteArray("sv", sv);
            }
        }
        com.riverfishing.network.ModNetwork.toPlayer(sp,
                new com.riverfishing.network.FinderPacket(t, true));
    }

    // ---- the world save ----------------------------------------------------------------------

    %(LOAD_SIG)s
        ChartData d = new ChartData();
        ListTag list = %(LOADLIST)s;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = %(LOADI)s;
            String id = %(LOADID)s;
            if (id.isEmpty()) continue;
            long[] ks = %(LOADK)s;
            byte[] vs = %(LOADV)s;
            Map<Long, Byte> cells = new HashMap<>();
            for (int j = 0; j < ks.length && j < vs.length; j++) cells.put(ks[j], vs[j]);
            d.charts.put(id, cells);
            long[] sk = %(LOADSK)s;
            byte[] sv = %(LOADSV)s;
            if (sk.length > 0) {
                Map<Long, Byte> m = new HashMap<>();
                for (int j = 0; j < sk.length && j < sv.length; j++) m.put(sk[j], sv[j]);
                d.marks.put(id, m);
            }
        }
        return d;
    }

    %(SAVE_SIG)s
        ListTag list = new ListTag();
        for (Map.Entry<String, Map<Long, Byte>> e : charts.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.getKey());
            long[] ks = new long[e.getValue().size()];
            byte[] vs = new byte[ks.length];
            int i = 0;
            for (Map.Entry<Long, Byte> c : e.getValue().entrySet()) {
                ks[i] = c.getKey();
                vs[i++] = c.getValue();
            }
            t.putLongArray("k", ks);
            t.putByteArray("v", vs);
            Map<Long, Byte> m = marks.get(e.getKey());
            if (m != null && !m.isEmpty()) {
                long[] sk = new long[m.size()];
                byte[] sv = new byte[sk.length];
                i = 0;
                for (Map.Entry<Long, Byte> c : m.entrySet()) {
                    sk[i] = c.getKey();
                    sv[i++] = c.getValue();
                }
                t.putLongArray("sk", sk);
                t.putByteArray("sv", sv);
            }
            list.add(t);
        }
        tag.put("charts", list);
        return tag;
    }
}
''' % {
    "CHUNK": CHUNK,
    "STORAGE": STORAGE,
    "LOAD_SIG": LOAD_SIG,
    "SAVE_SIG": SAVE_SIG,
    "WATER": GC("payload", '"water"') if D == "26" else 'payload.getCompound("water")',
    "CHART": GS("w", "chart", ""),
    "WX": GI("w", "x"),
    "WZ": GI("w", "z"),
    "WET": GBA("payload", "wet"),
    "MAP": GL("payload", "map"),
    "MAPI": GC("map", "i"),
    "TX": GI("t", "x"),
    "TZ": GI("t", "z"),
    "TD": GI("t", "d"),
    "HASS": 't.getString("s").isPresent()' if D == "26" else 't.contains("s")',
    "TS": GS("t", "s", ""),
    "LOADLIST": GL("tag", "charts"),
    "LOADI": GC("list", "i"),
    "LOADID": GS("t", "id", ""),
    "LOADK": GLA("t", "k"),
    "LOADV": GBA("t", "v"),
    "LOADSK": GLA("t", "sk"),
    "LOADSV": GBA("t", "sv"),
}

# 26.x reads a compound out of a compound by name with getCompoundOrEmpty(String)
if D == "26":
    CHART_DATA = CHART_DATA.replace('payload.getCompoundOrEmpty("water")', 'payload.getCompoundOrEmpty("water")')


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    assert old in s, "%s moved" % what
    return s.replace(old, new, 1)


p = J + "fishing/ChartData.java"
if not os.path.exists(p):
    wr(p, CHART_DATA)
    print("  fishing/ChartData.java")

# ---- the two places a sounding is taken with a finder ---------------------------------------------
p = J + "fishing/FishingManager.java"
s = rd(p)
if "chart-server" not in s:
    s = sub(s, """        BlockPos centre = com.riverfishing.item.WaterProbeItem.findWater(level, sp);
        if (centre != null) {
            com.riverfishing.network.ModNetwork.toPlayer(sp, new com.riverfishing.network.FinderPacket(
                    finderPayload(sp, level, centre, true), true));
        }""",
            """        BlockPos centre = com.riverfishing.item.WaterProbeItem.findWater(level, sp);
        if (centre != null) {
            CompoundTag payload = finderPayload(sp, level, centre, true);
            // §chart-server: into the sounder's own chart before it goes to the screen, and out of it
            // first if this player has not been handed this sounder's work yet.
            ChartData.record(sp, level, payload);
            com.riverfishing.network.ModNetwork.toPlayer(sp,
                    new com.riverfishing.network.FinderPacket(payload, true));
        }""", "the marker cast's payload")
    wr(p, s)
    print("  FishingManager: the marker cast records to the chart")

p = J + "item/WaterProbeItem.java"
s = rd(p)
if "chart-server" not in s:
    s = sub(s, """                    com.riverfishing.network.ModNetwork.toPlayer(sp, new com.riverfishing.network.FinderPacket(
                            FishingManager.finderPayload(sp, sl, waterPos), false));""",
            """                    net.minecraft.nbt.CompoundTag payload =
                            FishingManager.finderPayload(sp, sl, waterPos);
                    // §chart-server: the chart belongs to this sounder and lives in the world save.
                    com.riverfishing.fishing.ChartData.record(sp, sl, payload);
                    com.riverfishing.network.ModNetwork.toPlayer(sp,
                            new com.riverfishing.network.FinderPacket(payload, false));""",
            "the finder's own payload")
    wr(p, s)
    print("  WaterProbeItem: the reading records to the chart")

# ---- the client takes delivery ---------------------------------------------------------------------
p = J + "client/FinderState.java"
s = rd(p)
if "chart-server" not in s:
    s = sub(s, """    public static void accept(CompoundTag data, boolean hud) {
        last = data == null ? new CompoundTag() : data;""",
            """    public static void accept(CompoundTag data, boolean hud) {
        // §chart-server: a parcel of somebody's chart, not a sounding — it is not the last reading, it
        // does not belong on the trace and it must not open a screen.
        if (data != null && %s) {
            ClientSoundings.absorb(data);
            return;
        }
        last = data == null ? new CompoundTag() : data;""" % HAS("data", "chartsync"), "accept()")
    wr(p, s)
    print("  FinderState: chart parcels routed away from the screen")

p = J + "client/ClientSoundings.java"
s = rd(p)
if "chart-server" not in s:
    s = sub(s, """    // ---- disk ----------------------------------------------------------------------------------""",
            """    /**
     * §chart-server: a parcel of this sounder's chart, as the world save has it. Folded in by the same
     * rules a sounding is — a depth beats water, water beats land, nothing goes backwards — so an
     * older local chart and the server's copy converge instead of one wiping the other, and a parcel
     * that never arrives costs detail rather than correctness.
     */
    public static void absorb(CompoundTag parcel) {
        select(%s);
        ensureLoaded();
        if (loadedFor == null) return;
        long[] ks = %s;
        byte[] vs = %s;
        boolean changed = false;
        for (int i = 0; i < ks.length && i < vs.length; i++) {
            byte v = vs[i];
            Byte old = cells.get(ks[i]);
            if (v >= DEPTH0) {
                if (old == null || old < DEPTH0) sounded++;
                deepest = Math.max(deepest, v - DEPTH0);
            } else if (old != null && (old >= DEPTH0 || old == v)) {
                continue;
            }
            cells.put(ks[i], v);
            changed = true;
        }
        long[] sk = %s;
        byte[] sv = %s;
        for (int i = 0; i < sk.length && i < sv.length; i++) {
            if (spots.put(sk[i], sv[i]) == null) changed = true;
        }
        if (changed) version++;
        // One write per chart, not one per parcel: a big chart is twenty-five of these, and saving
        // the whole file each time would be twenty-five writes of a growing megabyte.
        if (%s) save();
    }

    // ---- disk ----------------------------------------------------------------------------------""" % (
                GS("parcel", "chart", ""), GLA("parcel", "k"), GBA("parcel", "v"),
                GLA("parcel", "sk"), GBA("parcel", "sv"), HAS("parcel", "last")), "the disk section")
    wr(p, s)
    print("  ClientSoundings: absorb a parcel")

# ---- and forget a player who leaves -----------------------------------------------------------------
p = J + "fishing/FishingManager.java"
s = rd(p)
if "ChartData.forget" not in s:
    s = sub(s, """    public static void clear(UUID uuid) {""",
            """    public static void clear(UUID uuid) {
        ChartData.forget(uuid);   // §chart-server: they take their copy with them""", "clear()")
    wr(p, s)
    print("  FishingManager: a leaving player is forgotten")
print("done (%s)" % D)
