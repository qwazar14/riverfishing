package com.riverfishing.fishing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-level, per-chunk fishing pressure (Module 7, §анти-макро). Every cast adds pressure to the
 * chunk; pressure regenerates slowly when the spot is left alone. High pressure multiplies the
 * spot's attractiveness (W_total) down toward {@link #FLOOR}, so botting one pond stops paying and
 * the player has to move on.
 *
 * <p>Implemented as level {@link SavedData} keyed by chunk (functionally a per-chunk capability, but
 * far less boilerplate and just as persistent).
 */
public class FishingPressureData extends SavedData {
    public static final String NAME = "riverfishing_pressure";

    private static final long REGEN_HALFLIFE = 24000L; // pressure halves ~every 20 min idle
    private static final double CAST_PRESSURE = 0.015;  // ~ an hour+ of steady casting to deplete
    // §population: a KEPT fish actually leaves the water — 4x the pressure of merely disturbing it.
    private static final double CATCH_PRESSURE = 0.06;
    private static final double MAX_PRESSURE = 1.5;
    private static final double FLOOR = 0.1;            // W_total never drops below 10%

    /** Per-species catch pressure lives under this key-prefix; casts use the plain chunk entry. */
    private static final String GLOBAL = "";

    private final Map<Long, Map<String, Entry>> chunks = new HashMap<>();

    public static FishingPressureData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FishingPressureData::load, FishingPressureData::new, NAME);
    }

    /** Register a cast on a chunk (scaled by the difficulty depletion multiplier, §14): disturbance only. */
    public void addCast(long chunkKey, long gameTime) {
        add(chunkKey, GLOBAL, gameTime, CAST_PRESSURE);
    }

    /**
     * Register a LANDED fish (§population): real removal — pressure lands on THAT SPECIES only, so
     * fishing out the bream leaves the perch biting as before.
     */
    public void addCatch(long chunkKey, String species, long gameTime) {
        add(chunkKey, species, gameTime, CATCH_PRESSURE);
    }

    private void add(long chunkKey, String key, long gameTime, double base) {
        double current = currentPressure(chunkKey, key, gameTime, 1.0);
        double amount = base * com.riverfishing.config.RiverFishingConfig.depletionMultiplier();
        chunks.computeIfAbsent(chunkKey, k -> new HashMap<>())
                .put(key, new Entry(Math.min(MAX_PRESSURE, current + amount), gameTime));
        setDirty();
    }

    /** Attractiveness multiplier for a chunk: 1.0 fresh, down to {@link #FLOOR} when fished out. */
    public double attractiveness(long chunkKey, long gameTime) {
        return attractiveness(chunkKey, gameTime, 1.0);
    }

    /**
     * §spawn-recovery: {@code regenScale} speeds the recovery clock — in SPRING (spawning season, нерест)
     * the caller passes ~2.5, so a fished-out water restocks in a fraction of the usual time.
     */
    public double attractiveness(long chunkKey, long gameTime, double regenScale) {
        double current = currentPressure(chunkKey, GLOBAL, gameTime, regenScale);
        return Math.max(FLOOR, Math.min(1.0, 1.0 - current));
    }

    /** §population: how depleted THIS species is here (1.0 plenty … {@link #FLOOR} fished out). */
    public double speciesAttractiveness(long chunkKey, String species, long gameTime, double regenScale) {
        double current = currentPressure(chunkKey, species, gameTime, regenScale);
        return Math.max(FLOOR, Math.min(1.0, 1.0 - current));
    }

    private double currentPressure(long chunkKey, String key, long gameTime, double regenScale) {
        Map<String, Entry> perSpecies = chunks.get(chunkKey);
        Entry e = perSpecies == null ? null : perSpecies.get(key);
        if (e == null) return 0.0;
        double elapsed = Math.max(0, gameTime - e.tick) * Math.max(0.1, regenScale);
        return e.pressure * Math.pow(0.5, elapsed / REGEN_HALFLIFE);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Map<String, Entry>> chunk : chunks.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Chunk", chunk.getKey());
            ListTag entries = new ListTag();
            for (Map.Entry<String, Entry> e : chunk.getValue().entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putString("S", e.getKey());
                t.putDouble("P", e.getValue().pressure);
                t.putLong("T", e.getValue().tick);
                entries.add(t);
            }
            c.put("E", entries);
            list.add(c);
        }
        tag.put("Chunks", list);
        return tag;
    }

    public static FishingPressureData load(CompoundTag tag) {
        FishingPressureData data = new FishingPressureData();
        ListTag list = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Map<String, Entry> perSpecies = new HashMap<>();
            if (c.contains("E")) {
                ListTag entries = c.getList("E", Tag.TAG_COMPOUND);
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag t = entries.getCompound(j);
                    perSpecies.put(t.getString("S"), new Entry(t.getDouble("P"), t.getLong("T")));
                }
            } else if (c.contains("P")) {
                perSpecies.put(GLOBAL, new Entry(c.getDouble("P"), c.getLong("T"))); // old single-value format
            }
            data.chunks.put(c.getLong("Chunk"), perSpecies);
        }
        return data;
    }

    private record Entry(double pressure, long tick) {}
}
