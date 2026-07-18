package com.riverfishing.fish;

import com.google.gson.JsonObject;
import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.engine.Weather;
import com.riverfishing.water.WaterType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A data-driven fish profile (§13). The same object feeds the bite engine (§1) and,
 * later, the journal hints (§15) — so balance and tips can never drift apart.
 */
public final class FishProfile {
    public final ResourceLocation id;

    // Presence / size
    public final Map<String, Double> waterBodies;
    public final double weightMin, weightMax, weightMean, weightSpread;
    public final double lengthMin, lengthMax;

    // Fight (used by the vyvazhivanie mini-game)
    public final double fightStrength, fightStamina;
    public final int fightRuns;
    /** Behaviour pattern: "steady" | "active_then_passive" | "aggressive" | "burst" | "relentless". */
    public final String fightPattern;
    public final double fightAggression;

    // Ideal tackle
    public final Set<String> idealRods;
    public final int reelSize, reelTolerance;
    public final String lineType;
    public final double lineDiameter, lineTolerance;
    public final Set<String> idealRigs;
    public final Set<String> idealGroundbaits;
    public final Map<String, Double> baitScores;
    public final int hookIdeal, hookTolerance;
    public final boolean requiresLeader;

    // Environment tables
    public final Map<String, Double> season;
    public final Map<String, Double> time;
    public final Map<String, Double> weather;
    public final String depthPref;
    public final double distMin, distMax;

    // Habitat hard gates (§ecology): the fish only lives in water of this depth/size…
    public final int depthMin, depthMax;
    public final double widthMin, widthMax;
    // …and only in these biome groups (group -> factor; empty = anywhere; no match = 0).
    public final Map<String, Double> biomes;

    // §legendary (0.5.0): the species hides ONE named specimen per server (0 = none).
    public final int legendaryWeightG;
    public final double legendaryChance;

    // Base attractiveness / relative density (§1.4)
    public final double base;

    // Progression gate: the fish won't take until the angler reaches this journal level (0 = ungated)
    public final int minAnglerLevel;

    private FishProfile(Builder b) {
        this.id = b.id;
        this.waterBodies = b.waterBodies;
        this.weightMin = b.weightMin;
        this.weightMax = b.weightMax;
        this.weightMean = b.weightMean;
        this.weightSpread = b.weightSpread;
        this.lengthMin = b.lengthMin;
        this.lengthMax = b.lengthMax;
        this.fightStrength = b.fightStrength;
        this.fightStamina = b.fightStamina;
        this.fightRuns = b.fightRuns;
        this.fightPattern = b.fightPattern;
        this.fightAggression = b.fightAggression;
        this.idealRods = b.idealRods;
        this.reelSize = b.reelSize;
        this.reelTolerance = b.reelTolerance;
        this.lineType = b.lineType;
        this.lineDiameter = b.lineDiameter;
        this.lineTolerance = b.lineTolerance;
        this.idealRigs = b.idealRigs;
        this.idealGroundbaits = b.idealGroundbaits;
        this.baitScores = b.baitScores;
        this.hookIdeal = b.hookIdeal;
        this.hookTolerance = b.hookTolerance;
        this.requiresLeader = b.requiresLeader;
        this.season = b.season;
        this.time = b.time;
        this.weather = b.weather;
        this.depthPref = b.depthPref;
        this.distMin = b.distMin;
        this.distMax = b.distMax;
        this.legendaryWeightG = b.legendaryWeightG;
        this.legendaryChance = b.legendaryChance;
        this.base = b.base;
        this.minAnglerLevel = b.minAnglerLevel;
        this.depthMin = b.depthMin;
        this.depthMax = b.depthMax;
        this.widthMin = b.widthMin;
        this.widthMax = b.widthMax;
        this.biomes = b.biomes;
    }

    // ---- Lookups used by the engine ----

    public double waterFactor(WaterType type) {
        return waterBodies.getOrDefault(type.key(), 0.0);
    }

    public double seasonFactor(Season s) {
        return s == null ? 1.0 : season.getOrDefault(s.jsonKey(), 1.0);
    }

    public double timeFactor(TimeOfDay t) {
        return t == null ? 1.0 : time.getOrDefault(t.jsonKey(), 1.0);
    }

    public double weatherFactor(Weather w) {
        return w == null ? 1.0 : weather.getOrDefault(w.jsonKey(), 1.0);
    }

    public double baitScore(String baitId) {
        if (baitId == null) return 0.0;
        return baitScores.getOrDefault(baitId, 0.0);
    }

    // ---- JSON parsing (§13 schema) ----

    public static FishProfile fromJson(ResourceLocation id, JsonObject json) {
        Builder b = new Builder(id);

        b.waterBodies = readDoubleMap(GsonHelper.getAsJsonObject(json, "water_bodies", new JsonObject()));

        JsonObject w = GsonHelper.getAsJsonObject(json, "weight_g", new JsonObject());
        b.weightMin = GsonHelper.getAsDouble(w, "min", 50);
        b.weightMax = GsonHelper.getAsDouble(w, "max", 1000);
        b.weightMean = GsonHelper.getAsDouble(w, "mean", (b.weightMin + b.weightMax) / 2.0);
        b.weightSpread = GsonHelper.getAsDouble(w, "spread", 0.6);

        JsonObject len = GsonHelper.getAsJsonObject(json, "length_cm", new JsonObject());
        b.lengthMin = GsonHelper.getAsDouble(len, "min", 8);
        b.lengthMax = GsonHelper.getAsDouble(len, "max", 40);

        JsonObject fight = GsonHelper.getAsJsonObject(json, "fight", new JsonObject());
        b.fightStrength = GsonHelper.getAsDouble(fight, "strength", 0.3);
        b.fightStamina = GsonHelper.getAsDouble(fight, "stamina", 0.4);
        b.fightRuns = GsonHelper.getAsInt(fight, "runs", 1);
        b.fightPattern = GsonHelper.getAsString(fight, "pattern", "steady");
        b.fightAggression = GsonHelper.getAsDouble(fight, "aggression", 0.5);

        JsonObject ideal = GsonHelper.getAsJsonObject(json, "ideal", new JsonObject());
        b.idealRods = readStringSet(ideal, "rod");
        b.reelSize = GsonHelper.getAsInt(ideal, "reel_size", 0);
        b.reelTolerance = GsonHelper.getAsInt(ideal, "reel_tolerance", 1000);
        JsonObject line = GsonHelper.getAsJsonObject(ideal, "line", new JsonObject());
        b.lineType = GsonHelper.getAsString(line, "type", "mono");
        b.lineDiameter = GsonHelper.getAsDouble(line, "diameter_mm", 0.20);
        b.lineTolerance = GsonHelper.getAsDouble(line, "tolerance_mm", 0.06);
        b.idealRigs = readStringSet(ideal, "rig");
        b.idealGroundbaits = readStringSet(ideal, "groundbait");
        b.baitScores = readDoubleMap(GsonHelper.getAsJsonObject(ideal, "bait", new JsonObject()));
        JsonObject hook = GsonHelper.getAsJsonObject(ideal, "hook", new JsonObject());
        b.hookIdeal = GsonHelper.getAsInt(hook, "ideal", 12);
        b.hookTolerance = Math.max(1, GsonHelper.getAsInt(hook, "tolerance", 2));
        b.requiresLeader = GsonHelper.getAsBoolean(ideal, "requires_leader", false);

        b.season = readDoubleMap(GsonHelper.getAsJsonObject(json, "season", new JsonObject()));
        b.time = readDoubleMap(GsonHelper.getAsJsonObject(json, "time", new JsonObject()));
        b.weather = readDoubleMap(GsonHelper.getAsJsonObject(json, "weather", new JsonObject()));
        b.depthPref = GsonHelper.getAsString(json, "depth_pref", "bottom");

        JsonObject dist = GsonHelper.getAsJsonObject(json, "distance_pref", new JsonObject());
        b.distMin = GsonHelper.getAsDouble(dist, "min", 2);
        b.distMax = GsonHelper.getAsDouble(dist, "max", 40);

        b.base = GsonHelper.getAsDouble(json, "base", 1.0);
        b.minAnglerLevel = GsonHelper.getAsInt(json, "min_angler_level", 0);

        // §legendary (0.5.0): optional one-per-server named specimen.
        if (json.has("legendary")) {
            JsonObject leg = GsonHelper.getAsJsonObject(json, "legendary");
            b.legendaryWeightG = GsonHelper.getAsInt(leg, "weight_g", 0);
            b.legendaryChance = GsonHelper.getAsDouble(leg, "chance", 0.005);
        }

        // Habitat gates (§ecology): depth/size of the water body + biome groups.
        JsonObject hab = GsonHelper.getAsJsonObject(json, "habitat", new JsonObject());
        b.depthMin = GsonHelper.getAsInt(hab, "depth_min", 0);
        b.depthMax = GsonHelper.getAsInt(hab, "depth_max", 999);
        b.widthMin = GsonHelper.getAsDouble(hab, "width_min", 0);
        b.widthMax = GsonHelper.getAsDouble(hab, "width_max", 99999);
        b.biomes = readDoubleMap(GsonHelper.getAsJsonObject(json, "biomes", new JsonObject()));
        return new FishProfile(b);
    }

    private static Map<String, Double> readDoubleMap(JsonObject obj) {
        Map<String, Double> map = new HashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
            map.put(e.getKey(), e.getValue().getAsDouble());
        }
        return map;
    }

    private static Set<String> readStringSet(JsonObject parent, String key) {
        Set<String> set = new HashSet<>();
        if (parent.has(key) && parent.get(key).isJsonArray()) {
            parent.getAsJsonArray(key).forEach(e -> set.add(e.getAsString()));
        }
        return set;
    }

    private static final class Builder {
        final ResourceLocation id;
        Map<String, Double> waterBodies = new HashMap<>();
        double weightMin, weightMax, weightMean, weightSpread;
        double lengthMin, lengthMax;
        double fightStrength, fightStamina;
        int fightRuns;
        String fightPattern = "steady";
        double fightAggression = 0.5;
        Set<String> idealRods = new HashSet<>();
        int reelSize, reelTolerance;
        String lineType = "mono";
        double lineDiameter, lineTolerance;
        Set<String> idealRigs = new HashSet<>();
        Set<String> idealGroundbaits = new HashSet<>();
        Map<String, Double> baitScores = new HashMap<>();
        int hookIdeal, hookTolerance;
        boolean requiresLeader;
        Map<String, Double> season = new HashMap<>();
        Map<String, Double> time = new HashMap<>();
        Map<String, Double> weather = new HashMap<>();
        String depthPref = "bottom";
        double distMin, distMax;
        double base = 1.0;
        int minAnglerLevel = 0;
        int legendaryWeightG = 0;
        double legendaryChance = 0.005;
        int depthMin = 0, depthMax = 999;
        double widthMin = 0, widthMax = 99999;
        Map<String, Double> biomes = new HashMap<>();

        Builder(ResourceLocation id) { this.id = id; }
    }
}
