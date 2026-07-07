package com.riverfishing.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Difficulty config (§14). A {@code preset} (arcade / realism / hardcore) picks a set of multipliers
 * for the "frustrating" mechanics; set it to {@code custom} to use the individual values instead.
 * "Low floor, high ceiling": Arcade barely punishes, Hardcore turns everything up.
 */
public final class RiverFishingConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> PRESET;
    public static final ForgeConfigSpec.DoubleValue PHANTOM;
    public static final ForgeConfigSpec.DoubleValue BREAK_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue DEPLETION;
    public static final ForgeConfigSpec.DoubleValue LEADER_BITEOFF;
    public static final ForgeConfigSpec.DoubleValue LINE_WEAR;
    public static final ForgeConfigSpec.DoubleValue HOOK_WEAR;
    public static final ForgeConfigSpec.DoubleValue SNAG;
    public static final ForgeConfigSpec.DoubleValue FOUL;

    // ---- Gameplay events (§polish 4): always read directly, not preset-driven ----
    public static final ForgeConfigSpec.DoubleValue TROPHY_CHANCE;
    public static final ForgeConfigSpec.DoubleValue FRENZY_SPEED;
    public static final ForgeConfigSpec.BooleanValue CONSUME_BAIT;
    public static final ForgeConfigSpec.BooleanValue CONSUME_GROUNDBAIT;
    public static final ForgeConfigSpec.DoubleValue BYCATCH_JUNK;
    public static final ForgeConfigSpec.DoubleValue BYCATCH_TREASURE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("River Fishing difficulty (§14). 'low floor, high ceiling'.");
        b.push("difficulty");
        PRESET = b.comment("Difficulty preset: arcade, realism, hardcore, or custom (use the values below).")
                .define("preset", "realism");
        PHANTOM = b.comment("[custom] Phantom (false-alarm) rate multiplier.")
                .defineInRange("phantom_multiplier", 1.0, 0.0, 10.0);
        BREAK_SENSITIVITY = b.comment("[custom] How readily the line snaps on over-tension (higher = snaps sooner).")
                .defineInRange("break_sensitivity", 1.0, 0.1, 10.0);
        DEPLETION = b.comment("[custom] How fast a chunk's fish get depleted by repeated casts.")
                .defineInRange("depletion_multiplier", 1.0, 0.0, 10.0);
        LEADER_BITEOFF = b.comment("[custom] Chance a leaderless line is bitten through by pike/zander.")
                .defineInRange("leader_biteoff_chance", 0.75, 0.0, 1.0);
        LINE_WEAR = b.comment("[custom] Line wear rate multiplier (0 = lines never wear).")
                .defineInRange("line_wear_multiplier", 1.0, 0.0, 10.0);
        HOOK_WEAR = b.comment("[custom] Hook dulling rate multiplier (0 = hooks never blunt).")
                .defineInRange("hook_wear_multiplier", 1.0, 0.0, 10.0);
        SNAG = b.comment("[custom] Spinning snag-near-shore rate multiplier (0 = no snags).")
                .defineInRange("snag_multiplier", 1.0, 0.0, 10.0);
        FOUL = b.comment("[custom] Foul-hooking (snagging a fish by the body) rate multiplier.")
                .defineInRange("foulhook_multiplier", 1.0, 0.0, 10.0);
        b.pop();

        b.comment("Gameplay events (§polish): trophies, feeding frenzy, consumables, bycatch.");
        b.push("events");
        TROPHY_CHANCE = b.comment("Chance a hooked fish is a trophy-class specimen.")
                .defineInRange("trophy_chance", 0.04, 0.0, 1.0);
        FRENZY_SPEED = b.comment("How much faster fish bite during a feeding frenzy (1 = frenzy off).")
                .defineInRange("frenzy_bite_speed", 3.0, 1.0, 10.0);
        CONSUME_BAIT = b.comment("Fish eat one natural bait from the rig on every strike.")
                .define("consume_bait", true);
        CONSUME_GROUNDBAIT = b.comment("Feeder casts consume one groundbait and feed the landing spot.")
                .define("consume_groundbait", true);
        BYCATCH_JUNK = b.comment("Chance a float/bottom bite is junk (boot, kelp...).")
                .defineInRange("bycatch_junk_chance", 0.045, 0.0, 1.0);
        BYCATCH_TREASURE = b.comment("Chance a float/bottom bite is a small treasure.")
                .defineInRange("bycatch_treasure_chance", 0.013, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private RiverFishingConfig() {}

    private static String preset() {
        return PRESET.get().toLowerCase();
    }

    private static double byPreset(double arcade, double realism, double hardcore, ForgeConfigSpec.DoubleValue custom) {
        return switch (preset()) {
            case "arcade" -> arcade;
            case "hardcore" -> hardcore;
            case "custom" -> custom.get();
            default -> realism;
        };
    }

    public static double phantomMultiplier() {
        return byPreset(0.2, 1.0, 1.6, PHANTOM);
    }

    public static double breakSensitivity() {
        return byPreset(0.3, 1.0, 1.7, BREAK_SENSITIVITY);
    }

    public static double depletionMultiplier() {
        return byPreset(0.3, 1.0, 1.6, DEPLETION);
    }

    public static double leaderBiteoffChance() {
        return byPreset(0.3, 0.75, 0.95, LEADER_BITEOFF);
    }

    public static double lineWearRate() {
        return byPreset(0.3, 1.0, 1.7, LINE_WEAR);
    }

    public static double hookWearRate() {
        return byPreset(0.3, 1.0, 1.7, HOOK_WEAR);
    }

    public static double snagChance() {
        return byPreset(0.3, 1.0, 1.6, SNAG);
    }

    public static double foulHookChance() {
        return byPreset(0.4, 1.0, 1.6, FOUL);
    }

    public static double trophyChance() {
        return TROPHY_CHANCE.get();
    }

    public static double frenzySpeed() {
        return FRENZY_SPEED.get();
    }

    public static boolean consumeBait() {
        return CONSUME_BAIT.get();
    }

    public static boolean consumeGroundbait() {
        return CONSUME_GROUNDBAIT.get();
    }

    public static double bycatchJunkChance() {
        return BYCATCH_JUNK.get();
    }

    public static double bycatchTreasureChance() {
        return BYCATCH_TREASURE.get();
    }
}

