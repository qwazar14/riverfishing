package com.riverfishing.config;

/**
 * Difficulty config (§14, §multiloader). A {@code preset} (arcade / realism / hardcore) picks the
 * multipliers for the "frustrating" mechanics; set it to {@code custom} to use the individual values.
 *
 * <p>The Forge {@code ForgeConfigSpec} was dropped for the multi-loader split — Architectury has no
 * config API. These are plain static fields defaulted to the old {@code realism} values; a per-platform
 * config-file loader (JSON) can populate them in a later pass. The public accessor API is unchanged, so
 * no call sites needed touching.
 */
public final class RiverFishingConfig {
    // preset: arcade | realism | hardcore | custom
    public static String preset = "realism";

    // [custom] values (only used when preset == "custom")
    public static double phantom = 1.0;
    public static double breakSensitivity = 1.0;
    public static double depletion = 1.0;
    public static double leaderBiteoff = 0.75;
    public static double lineWear = 1.0;
    public static double hookWear = 1.0;
    public static double snag = 1.0;
    public static double foul = 1.0;

    // Gameplay events (§polish): read directly, not preset-driven.
    public static double trophyChance = 0.04;
    public static double frenzySpeed = 3.0;
    public static boolean consumeBait = true;
    public static boolean consumeGroundbait = true;
    public static double bycatchJunk = 0.045;
    public static double bycatchTreasure = 0.013;

    private RiverFishingConfig() {}

    private static double byPreset(double arcade, double realism, double hardcore, double custom) {
        return switch (preset.toLowerCase()) {
            case "arcade" -> arcade;
            case "hardcore" -> hardcore;
            case "custom" -> custom;
            default -> realism;
        };
    }

    public static double phantomMultiplier() { return byPreset(0.2, 1.0, 1.6, phantom); }
    public static double breakSensitivity() { return byPreset(0.3, 1.0, 1.7, breakSensitivity); }
    public static double depletionMultiplier() { return byPreset(0.3, 1.0, 1.6, depletion); }
    public static double leaderBiteoffChance() { return byPreset(0.3, 0.75, 0.95, leaderBiteoff); }
    public static double lineWearRate() { return byPreset(0.3, 1.0, 1.7, lineWear); }
    public static double hookWearRate() { return byPreset(0.3, 1.0, 1.7, hookWear); }
    public static double snagChance() { return byPreset(0.3, 1.0, 1.6, snag); }
    public static double foulHookChance() { return byPreset(0.4, 1.0, 1.6, foul); }

    public static double trophyChance() { return trophyChance; }
    public static double frenzySpeed() { return frenzySpeed; }
    public static boolean consumeBait() { return consumeBait; }
    public static boolean consumeGroundbait() { return consumeGroundbait; }
    public static double bycatchJunkChance() { return bycatchJunk; }
    public static double bycatchTreasureChance() { return bycatchTreasure; }
}
