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
    private static int deepest = 1;
    /**
     * §arrow-target: the mark the strip's needle points at, chosen on the chart, or null for "the
     * nearest one". Kept with the chart, so a hole you picked on Tuesday is still the hole on Friday.
     */
    private static Long target;

    private ClientSoundings() {}

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

    /** Fold one sounding's windows in: the water mask and the sounded cells, both around its centre. */
    public static void merge(CompoundTag data) {
        ensureLoaded();
        if (loadedFor == null) return;
        CompoundTag w = data.getCompound("water");
        if (w.isEmpty()) return;
        int cx = w.getInt("x"), cz = w.getInt("z");
        boolean changed = false;

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
            cells.put(k, (byte) Math.min(127, DEPTH0 + d));
            deepest = Math.max(deepest, d);
            if (t.contains("s")) spots.put(k, (byte) ("hole".equals(t.getString("s")) ? 0 : 1));
            changed = true;
        }
        if (changed) save();
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
        String k = worldKey();
        if (k == null || k.equals(loadedFor)) return;
        cells.clear();
        spots.clear();
        deepest = 1;
        target = null;
        loadedFor = k;
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            CompoundTag t = NbtIo.readCompressed(p.toFile());
            long[] ks = t.getLongArray("k");
            byte[] vs = t.getByteArray("v");
            for (int i = 0; i < ks.length && i < vs.length; i++) {
                cells.put(ks[i], vs[i]);
                if (vs[i] >= DEPTH0) deepest = Math.max(deepest, vs[i] - DEPTH0);
            }
            long[] sk = t.getLongArray("sk");
            byte[] sv = t.getByteArray("sv");
            for (int i = 0; i < sk.length && i < sv.length; i++) spots.put(sk[i], sv[i]);
            if (t.getBoolean("ht")) target = t.getLong("t");
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
            Files.createDirectories(file().getParent());
            NbtIo.writeCompressed(t, file().toFile());
        } catch (Exception e) {
            com.riverfishing.RiverFishing.LOGGER.warn("§depth-map: could not write {}: {}", loadedFor, e.toString());
        }
    }
}
