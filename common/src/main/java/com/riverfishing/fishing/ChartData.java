package com.riverfishing.fishing;

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
    private static final int CHUNK = 8192;

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

    public static ChartData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChartData::new, ChartData::load,
                        (net.minecraft.util.datafix.DataFixTypes) null), NAME);
    }

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
        CompoundTag w = payload.getCompound("water");
        String id = w.getString("chart");
        if (id.isEmpty()) return;
        ChartData d = get(level);
        d.push(sp, id);
        d.merge(id, w.getInt("x"), w.getInt("z"), payload.getByteArray("wet"), payload.getList("map", 10));
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
            CompoundTag t = map.getCompound(i);
            long k = key(cx + t.getInt("x"), cz + t.getInt("z"));
            cells.put(k, (byte) Math.min(127, DEPTH0 + t.getInt("d")));
            if (t.contains("s")) {
                marks.computeIfAbsent(id, x -> new HashMap<>())
                        .put(k, (byte) ("hole".equals(t.getString("s")) ? 0 : 1));
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

    public static ChartData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChartData d = new ChartData();
        ListTag list = tag.getList("charts", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String id = t.getString("id");
            if (id.isEmpty()) continue;
            long[] ks = t.getLongArray("k");
            byte[] vs = t.getByteArray("v");
            Map<Long, Byte> cells = new HashMap<>();
            for (int j = 0; j < ks.length && j < vs.length; j++) cells.put(ks[j], vs[j]);
            d.charts.put(id, cells);
            long[] sk = t.getLongArray("sk");
            byte[] sv = t.getByteArray("sv");
            if (sk.length > 0) {
                Map<Long, Byte> m = new HashMap<>();
                for (int j = 0; j < sk.length && j < sv.length; j++) m.put(sk[j], sv[j]);
                d.marks.put(id, m);
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
