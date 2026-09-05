package com.riverfishing.client;

import com.riverfishing.fishing.FishingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * §depth-map: the bed of the world, as far as THIS PLAYER has seen it, kept on this player's disk.
 *
 * <p>The server owns the truth (§sounding, SoundingData) and hands out windows of it: the map around
 * a sounding, the swath a marker cast just wrote. This keeps every window it was ever handed, per
 * world, and that accumulation IS the depth map — a lake you sounded in week one is still on the
 * chart in week ten, and the chart is the only place the whole of it can be seen at once. The same
 * model as a minimap mod: you map what you have been near, and nothing is fetched for a place you
 * have not.
 *
 * <p>One byte a column: unknown, bank, water nobody has sounded, or a depth. A cell never goes back
 * from a depth to plain water — a later window that has not sounded that column knows less than the
 * cast that did, not more. Written straight after each merge; merges happen on a screen open or a
 * marker cast, which is rare enough that a debounce would be a second thing to get wrong.
 */
public final class ClientSoundings {
    public static final byte LAND = 1, WATER = 2, DEPTH0 = 3;

    private static final Map<Long, Byte> cells = new HashMap<>();
    /** column key -> 0 hole, 1 drop-off. */
    private static final Map<Long, Byte> spots = new HashMap<>();
    private static String loadedFor;
    /**
     * §chart-item: the sounder whose chart is loaded, or empty for the old per-world pile. Set by the
     * sounding itself — the server names the finder that took it — so putting one sounder away and
     * drawing another swaps charts on the next reading rather than on a guess about which hand.
     */
    private static String chart = "";
    /** The world the loaded chart belongs to, so joining another server does not carry an id over. */
    private static String world;
    private static int deepest = 1;
    /**
     * §arrow-target: the mark the strip's needle points at, chosen on the chart, or null for "the
     * nearest one". Kept with the chart, so a hole you picked on Tuesday is still the hole on Friday.
     */
    private static Long target;
    /**
     * §finder2: bumped on every change to the cells or the marks, so a reader can keep what it built
     * from them (the chart's cell grid, the visible marks) until the data actually moves. And the
     * sounded-column count kept as it changes, because the chart printed it by walking every cell a frame.
     */
    private static int version;
    private static int sounded;
    /**
     * §chart-far: the seed the chart draws the faunal provinces off — NOT the world seed, a one-way
     * scramble of it ({@link com.riverfishing.water.Provinces#mapSeed}). 0 until a sounding arrives,
     * which reads as "no regions on the chart yet".
     */
    private static long mapSeed;
    /** §chart-far: the bed folded down to a zoom's cell size, and which zoom and which data it is. */
    private static final Map<Long, Byte> coarse = new HashMap<>();
    private static int coarseStep = -1, coarseVersion = -1;

    private ClientSoundings() {}

    public static int version() {
        ensureLoaded();
        return version;
    }

    /** How many columns hold a depth — the chart's "Sounded: N m²". */
    public static int sounded() {
        ensureLoaded();
        return sounded;
    }

    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    public static Map<Long, Byte> cells() {
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
    }

    public static Map<Long, Byte> spots() {
        ensureLoaded();
        return spots;
    }

    public static int deepest() {
        return deepest;
    }

    public static Long target() {
        ensureLoaded();
        return target;
    }

    /** Pick this mark, or clear it if it was the pick. */
    public static void toggleTarget(long key) {
        ensureLoaded();
        target = (target != null && target == key) ? null : key;
        save();
    }

    /** Which sounder's chart is on screen, or empty. */
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
        select(data.getCompound("water").getString("chart"));             // §chart-item: before the load, or it lands in the last chart
        ensureLoaded();
        if (loadedFor == null) return;
        CompoundTag w = data.getCompound("water");
        if (w.isEmpty()) return;
        int cx = w.getInt("x"), cz = w.getInt("z");
        boolean changed = false;
        long seed = w.getLong("seed");                        // §chart-far
        if (seed != 0 && seed != mapSeed) {
            mapSeed = seed;
            changed = true;
        }

        byte[] wet = data.getByteArray("wet");
        int r = FishingManager.MAP_REACH, n = 2 * r + 1;
        if (wet.length == n * n) {
            for (int dz = 0; dz < n; dz++) {
                for (int dx = 0; dx < n; dx++) {
                    long k = key(cx + dx - r, cz + dz - r);
                    byte v = wet[dz * n + dx] != 0 ? WATER : LAND;
                    Byte old = cells.get(k);
                    if (old == null || (old < DEPTH0 && old != v)) {
                        cells.put(k, v);
                        changed = true;
                    }
                }
            }
        }
        ListTag map = data.getList("map", 10);
        for (int i = 0; i < map.size(); i++) {
            CompoundTag t = map.getCompound(i);
            long k = key(cx + t.getInt("x"), cz + t.getInt("z"));
            int d = t.getInt("d");
            Byte old = cells.put(k, (byte) Math.min(127, DEPTH0 + d));
            if (old == null || old < DEPTH0) sounded++;
            deepest = Math.max(deepest, d);
            if (t.contains("s")) spots.put(k, (byte) ("hole".equals(t.getString("s")) ? 0 : 1));
            changed = true;
        }
        if (changed) {
            version++;
            save();
        }
    }

    /**
     * §chart-server: a parcel of this sounder's chart, as the world save has it. Folded in by the same
     * rules a sounding is — a depth beats water, water beats land, nothing goes backwards — so an
     * older local chart and the server's copy converge instead of one wiping the other, and a parcel
     * that never arrives costs detail rather than correctness.
     */
    public static void absorb(CompoundTag parcel) {
        select(parcel.getString("chart"));
        ensureLoaded();
        if (loadedFor == null) return;
        long[] ks = parcel.getLongArray("k");
        byte[] vs = parcel.getByteArray("v");
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
        long[] sk = parcel.getLongArray("sk");
        byte[] sv = parcel.getByteArray("sv");
        for (int i = 0; i < sk.length && i < sv.length; i++) {
            if (spots.put(sk[i], sv[i]) == null) changed = true;
        }
        if (changed) version++;
        // One write per chart, not one per parcel: a big chart is twenty-five of these, and saving
        // the whole file each time would be twenty-five writes of a growing megabyte.
        if (parcel.getBoolean("last")) save();
    }

    // ---- disk ----------------------------------------------------------------------------------

    /** This world's chart, by the level name in singleplayer and the address on a server. */
    private static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return "sp_" + safe(mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        return mc.getCurrentServer() == null ? null : "mp_" + safe(mc.getCurrentServer().ip);
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("riverfishing").resolve("soundings").resolve(loadedFor + ".nbt");
    }

    private static void ensureLoaded() {
        String wk = worldKey();
        // §chart-item: a different world is a different chart, whatever sounder is in the bag.
        if (wk != null && !wk.equals(world)) {
            world = wk;
            chart = "";
        }
        String k = wk == null || chart.isEmpty() ? wk : wk + "_" + chart;
        if (k == null || k.equals(loadedFor)) return;
        cells.clear();
        spots.clear();
        deepest = 1;
        target = null;
        sounded = 0;
        mapSeed = 0;
        coarseStep = -1;
        version++;
        loadedFor = k;
        try {
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
            if (!Files.exists(p)) return;
            CompoundTag t = NbtIo.readCompressed(p, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            long[] ks = t.getLongArray("k");
            byte[] vs = t.getByteArray("v");
            for (int i = 0; i < ks.length && i < vs.length; i++) {
                cells.put(ks[i], vs[i]);
                if (vs[i] >= DEPTH0) {
                    deepest = Math.max(deepest, vs[i] - DEPTH0);
                    sounded++;
                }
            }
            long[] sk = t.getLongArray("sk");
            byte[] sv = t.getByteArray("sv");
            for (int i = 0; i < sk.length && i < sv.length; i++) spots.put(sk[i], sv[i]);
            if (t.getBoolean("ht")) target = t.getLong("t");
            mapSeed = t.getLong("seed");   // §chart-far
        } catch (Exception e) {
            com.riverfishing.RiverFishing.LOGGER.warn("§depth-map: could not read {}: {}", loadedFor, e.toString());
        }
    }

    private static void save() {
        try {
            CompoundTag t = new CompoundTag();
            long[] ks = new long[cells.size()];
            byte[] vs = new byte[cells.size()];
            int i = 0;
            for (Map.Entry<Long, Byte> e : cells.entrySet()) {
                ks[i] = e.getKey();
                vs[i++] = e.getValue();
            }
            t.putLongArray("k", ks);
            t.putByteArray("v", vs);
            long[] sk = new long[spots.size()];
            byte[] sv = new byte[spots.size()];
            i = 0;
            for (Map.Entry<Long, Byte> e : spots.entrySet()) {
                sk[i] = e.getKey();
                sv[i++] = e.getValue();
            }
            t.putLongArray("sk", sk);
            t.putByteArray("sv", sv);
            t.putBoolean("ht", target != null);
            if (target != null) t.putLong("t", target);
            t.putLong("seed", mapSeed);
            Files.createDirectories(file().getParent());
            NbtIo.writeCompressed(t, file());
        } catch (Exception e) {
            com.riverfishing.RiverFishing.LOGGER.warn("§depth-map: could not write {}: {}", loadedFor, e.toString());
        }
    }
}
