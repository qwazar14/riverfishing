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

    /**
     * §fish-groups (0.8.0): which family this species is filed under — {@code cyprinid}, {@code predator},
     * {@code salmonid}, {@code sturgeon}, {@code koi}, {@code sea}, {@code big_game}, or {@code other}.
     *
     * <p>Seventy-nine species in one flat list is a list nobody reads, and the electrofisher now offers
     * every one of them. The group is the axis a person actually thinks along ("where are the carp") —
     * so it is written in the profile rather than guessed from weight and water, which would file a koi
     * under carp and an asp under predator and be wrong about both.
     *
     * <p>Unknown or missing lands in {@code other} on purpose: a datapack species with no group is still
     * listed and still reachable, just not silently mis-filed.
     */
    public final String group;

    // Presence / size
    public final Map<String, Double> waterBodies;
    public final double weightMin, weightMax, weightMean;
    /** §weight-curve: true when the profile explicitly sets weight_g.mean — it then becomes the roll's median. */
    public final boolean weightMeanSet;
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
    /**
     * §groundbait-one-jar: the grind this species answers to. 0 is a cloud of dust, 1 is whole grain
     * lying on the bottom. <b>A big fraction calls big fish</b>, so this mostly tracks the fish's own
     * size — and where it does not, that is the profile saying something about the fish.
     */
    public final double gbFraction;
    /**
     * §groundbait-one-jar: how rich a table this species wants. A carp is looking for a meal; a bleak
     * is looking for a cloud, and a spread laid out for the carp puts it off. Seeded from the species'
     * own bait list — a fish that eats boilies wants a rich mix, one that eats bloodworm does not.
     */
    public final double gbNutrition;
    public final Map<String, Double> baitScores;
    public final int hookIdeal, hookTolerance;
    public final boolean requiresLeader;

    // Environment tables
    public final Map<String, Double> season;
    public final Map<String, Double> time;
    public final Map<String, Double> weather;
    public final Map<String, Double> bed;
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
        this.group = b.group;
        this.waterBodies = b.waterBodies;
        this.weightMin = b.weightMin;
        this.weightMax = b.weightMax;
        this.weightMean = b.weightMean;
        this.weightMeanSet = b.weightMeanSet;
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
        this.gbFraction = b.gbFraction;
        this.gbNutrition = b.gbNutrition;
        this.baitScores = b.baitScores;
        this.hookIdeal = b.hookIdeal;
        this.hookTolerance = b.hookTolerance;
        this.requiresLeader = b.requiresLeader;
        this.season = b.season;
        this.time = b.time;
        this.weather = b.weather;
        this.bed = b.bed;
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

    /** The bed codes FishingManager.bedType() hands out, by name, for the profile's own "bed" map. */
    private static final String[] BED_KEYS = {"", "sand", "gravel", "clay", "mud", "rock", "other"};

    /**
     * §bed-bite: how much this species likes the bottom it is over. A profile may say so itself with a
     * {@code "bed"} map; the ninety-odd that do not get their FAMILY's habit, which is the same trick
     * the groundbait grind uses — the fallback seeds sensible numbers into every species at once
     * instead of asking someone to hand-write ninety files.
     *
     * <p>A nudge, never a gate: 0.85 to 1.2. The bed decides where a carp is COMFORTABLE, not whether
     * a carp exists — depth and water type already do that, and a second hard gate on top of them would
     * empty half the swims in the game.
     */
    public double bedFactor(int bedCode) {
        if (bedCode <= 0 || bedCode >= BED_KEYS.length) return 1.0;
        String key = BED_KEYS[bedCode];
        if (!bed.isEmpty()) return bed.getOrDefault(key, 1.0);
        switch (group == null ? "" : group) {
            case "cyprinid":                       // roots in the soft stuff
                return switch (key) { case "mud", "clay" -> 1.15; case "rock" -> 0.85; default -> 1.0; };
            case "predator":                       // ambushes off hard structure
                return switch (key) { case "rock", "gravel" -> 1.12; case "mud" -> 0.9; default -> 1.0; };
            case "salmonid":                       // gravel is where they spawn and feed
                return switch (key) { case "gravel" -> 1.2; case "sand" -> 1.05; case "mud" -> 0.85; default -> 1.0; };
            case "sturgeon":                       // grubs the soft bottom
                return switch (key) { case "sand", "mud" -> 1.15; case "rock" -> 0.85; default -> 1.0; };
            case "sea", "big_game":                // sand and rock both work; mud is a harbour
                return switch (key) { case "sand", "rock" -> 1.1; case "mud" -> 0.9; default -> 1.0; };
            default:
                return 1.0;
        }
    }

    public double baitScore(String baitId) {
        if (baitId == null) return 0.0;
        return baitScores.getOrDefault(baitId, 0.0);
    }

    /**
     * §groundbait-one-jar: the grind a species wants when its profile does not say, straight off its own
     * weight. A 20 g bleak wants dust, a 1 kg bream the middle, a 20 kg carp whole grain.
     *
     * <p>This is also what SEEDED the number into all 79 shipped profiles, so the fallback and the data
     * agree by construction rather than by anybody remembering to keep them in step.
     */
    public static double defaultGbFraction(double weightMeanG, double weightMaxG) {
        double kg = (weightMeanG > 0 ? weightMeanG : weightMaxG) / 1000.0;
        // log10 over three decades of fish: 0.02 kg -> ~0.06, 1 kg -> ~0.57, 20 kg -> ~0.95.
        return Math.max(0.0, Math.min(1.0, (Math.log10(Math.max(0.01, kg)) + 2.0) / 3.5));
    }

    /**
     * §groundbait-one-jar: how rich a table a species wants, read off the baits it already likes.
     *
     * <p>A fish that eats boilies and corn is asking for a meal; one that eats bloodworm and breadcrumb
     * is not. Nobody had to invent a second table for this — the bait list IS the fish's diet, and the
     * pantry already knows what each of those is worth in calories.
     */
    public static double defaultGbNutrition(Map<String, Double> baitScores) {
        double weight = 0, sum = 0;
        for (Map.Entry<String, Double> e : baitScores.entrySet()) {
            com.riverfishing.groundbait.GroundbaitMix.Component c =
                    com.riverfishing.groundbait.GroundbaitMix.PANTRY.get(e.getKey());
            if (c == null || e.getValue() == null || e.getValue() <= 0) continue;
            weight += e.getValue();
            sum += c.nutrition() * e.getValue();
        }
        // A predator whose whole list is lures has no pantry entry at all: it never fishes over a bed of
        // feed on purpose, so the neutral middle is exactly the right thing to say about it.
        return weight > 0 ? sum / weight : 0.5;
    }

    // ---- JSON parsing (§13 schema) ----

    public static FishProfile fromJson(ResourceLocation id, JsonObject json) {
        Builder b = new Builder(id);

        b.group = GsonHelper.getAsString(json, "group", FishGroup.OTHER);
        b.waterBodies = readDoubleMap(GsonHelper.getAsJsonObject(json, "water_bodies", new JsonObject()));

        JsonObject w = GsonHelper.getAsJsonObject(json, "weight_g", new JsonObject());
        b.weightMin = GsonHelper.getAsDouble(w, "min", 50);
        b.weightMax = GsonHelper.getAsDouble(w, "max", 1000);
        b.weightMean = GsonHelper.getAsDouble(w, "mean", (b.weightMin + b.weightMax) / 2.0);
        b.weightMeanSet = w.has("mean");

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
        b.baitScores = readDoubleMap(GsonHelper.getAsJsonObject(ideal, "bait", new JsonObject()));
        // §groundbait-one-jar: "groundbait" used to be a LIST of jar names and is now a pair of numbers.
        // The isJsonObject guard is not politeness — a modpack still shipping the old array would other-
        // wise crash the profile load, and a third-party profile deserves to fall back, not to explode.
        JsonObject gb = ideal.has("groundbait") && ideal.get("groundbait").isJsonObject()
                ? GsonHelper.getAsJsonObject(ideal, "groundbait") : new JsonObject();
        b.gbFraction = GsonHelper.getAsDouble(gb, "fraction", defaultGbFraction(b.weightMean, b.weightMax));
        b.gbNutrition = GsonHelper.getAsDouble(gb, "nutrition", defaultGbNutrition(b.baitScores));
        JsonObject hook = GsonHelper.getAsJsonObject(ideal, "hook", new JsonObject());
        b.hookIdeal = GsonHelper.getAsInt(hook, "ideal", 12);
        b.hookTolerance = Math.max(1, GsonHelper.getAsInt(hook, "tolerance", 2));
        b.requiresLeader = GsonHelper.getAsBoolean(ideal, "requires_leader", false);

        b.season = readDoubleMap(GsonHelper.getAsJsonObject(json, "season", new JsonObject()));
        b.time = readDoubleMap(GsonHelper.getAsJsonObject(json, "time", new JsonObject()));
        b.weather = readDoubleMap(GsonHelper.getAsJsonObject(json, "weather", new JsonObject()));
        // §bed-bite: optional. Absent means "my family's habit" — see bedFactor().
        b.bed = readDoubleMap(GsonHelper.getAsJsonObject(json, "bed", new JsonObject()));
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
        String group = FishGroup.OTHER;
        Map<String, Double> waterBodies = new HashMap<>();
        double weightMin, weightMax, weightMean;
        boolean weightMeanSet;
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
        double gbFraction, gbNutrition;
        Map<String, Double> baitScores = new HashMap<>();
        int hookIdeal, hookTolerance;
        boolean requiresLeader;
        Map<String, Double> season = new HashMap<>();
        Map<String, Double> time = new HashMap<>();
        Map<String, Double> weather = new HashMap<>();
        Map<String, Double> bed = new java.util.HashMap<>();
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
