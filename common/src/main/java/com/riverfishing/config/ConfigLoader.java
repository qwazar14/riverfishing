package com.riverfishing.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riverfishing.RiverFishing;

import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * §config (0.7.0): fills {@link RiverFishingConfig} from {@code config/riverfishing.json}.
 *
 * <p>Until now those fields were plain statics that nothing ever assigned, so every server ran the
 * {@code realism} preset and the {@code arcade} / {@code hardcore} branches were unreachable code. A
 * modpack could not soften line breaks, snags or depletion for its audience without patching the jar.
 *
 * <p>Deliberately small: Gson is already a dependency and Architectury already knows the config folder,
 * so this needs no new platform seam. The file is WRITTEN on first run with every value at its default,
 * because a config nobody can discover is barely better than no config — and the accessor API in
 * {@link RiverFishingConfig} is untouched, so no call site moved.
 *
 * <p>Nothing here may throw into mod init: a malformed config falls back to defaults with one warning.
 * Numbers are clamped, so a typo cannot produce a negative chance or a multiplier that breaks the
 * fight maths.
 */
public final class ConfigLoader {
    private static final String FILE = "riverfishing.json";

    private ConfigLoader() {}

    public static void load() {
        Path file;
        try {
            file = dev.architectury.platform.Platform.getConfigFolder().resolve(FILE);
        } catch (Throwable t) {                       // no config folder (datagen, tests) — defaults stand
            return;
        }
        if (!Files.exists(file)) {
            write(file);
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            RiverFishingConfig.preset = str(o, "preset", RiverFishingConfig.preset);

            RiverFishingConfig.phantom = num(o, "phantom", RiverFishingConfig.phantom, 0.0, 5.0);
            RiverFishingConfig.breakSensitivity = num(o, "break_sensitivity", RiverFishingConfig.breakSensitivity, 0.0, 5.0);
            RiverFishingConfig.depletion = num(o, "depletion", RiverFishingConfig.depletion, 0.0, 5.0);
            RiverFishingConfig.leaderBiteoff = num(o, "leader_biteoff", RiverFishingConfig.leaderBiteoff, 0.0, 1.0);
            RiverFishingConfig.lineWear = num(o, "line_wear", RiverFishingConfig.lineWear, 0.0, 5.0);
            RiverFishingConfig.hookWear = num(o, "hook_wear", RiverFishingConfig.hookWear, 0.0, 5.0);
            RiverFishingConfig.snag = num(o, "snag", RiverFishingConfig.snag, 0.0, 5.0);
            RiverFishingConfig.foul = num(o, "foul", RiverFishingConfig.foul, 0.0, 5.0);

            RiverFishingConfig.trophyChance = num(o, "trophy_chance", RiverFishingConfig.trophyChance, 0.0, 1.0);
            RiverFishingConfig.frenzySpeed = num(o, "frenzy_speed", RiverFishingConfig.frenzySpeed, 1.0, 20.0);
            RiverFishingConfig.bycatchJunk = num(o, "bycatch_junk", RiverFishingConfig.bycatchJunk, 0.0, 1.0);
            RiverFishingConfig.bycatchTreasure = num(o, "bycatch_treasure", RiverFishingConfig.bycatchTreasure, 0.0, 1.0);

            RiverFishingConfig.consumeBait = bool(o, "consume_bait", RiverFishingConfig.consumeBait);
            RiverFishingConfig.consumeGroundbait = bool(o, "consume_groundbait", RiverFishingConfig.consumeGroundbait);
            RiverFishingConfig.updateCheck = bool(o, "update_check", RiverFishingConfig.updateCheck);

            String p = RiverFishingConfig.preset.toLowerCase();
            if (!p.equals("arcade") && !p.equals("realism") && !p.equals("hardcore") && !p.equals("custom")) {
                RiverFishing.LOGGER.warn("River Fishing: unknown preset '{}' — using realism. "
                        + "Valid: arcade, realism, hardcore, custom.", RiverFishingConfig.preset);
                RiverFishingConfig.preset = "realism";
            }
            RiverFishing.LOGGER.info("River Fishing: config loaded, preset = {}", RiverFishingConfig.preset);
        } catch (Exception e) {
            RiverFishing.LOGGER.warn("River Fishing: {} is unreadable ({}) — running on defaults. "
                    + "Delete it to have a fresh one written.", FILE, e.toString());
        }
    }

    /** First run: put every knob on disk at its default, so the file itself is the documentation. */
    private static void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(DEFAULTS);
            }
            RiverFishing.LOGGER.info("River Fishing: wrote a default {}", FILE);
        } catch (Exception e) {
            RiverFishing.LOGGER.warn("River Fishing: could not write {} ({}) — defaults still apply.",
                    FILE, e.toString());
        }
    }

    private static String str(JsonObject o, String k, String def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
    }

    /** Clamped on purpose: a stray minus sign must not turn a chance negative or a multiplier absurd. */
    private static double num(JsonObject o, String k, double def, double min, double max) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive()) return def;
        double v;
        try {
            v = e.getAsDouble();
        } catch (Exception ignored) {
            return def;
        }
        if (Double.isNaN(v)) return def;
        double c = Math.max(min, Math.min(max, v));
        if (c != v) {
            RiverFishing.LOGGER.warn("River Fishing: {} = {} is outside {}..{} — clamped to {}.",
                    k, v, min, max, c);
        }
        return c;
    }

    private static final String DEFAULTS = """
            {
              "_comment": [
                "River Fishing config. Delete this file to regenerate it with the defaults.",
                "'preset' picks the multipliers for the frustrating mechanics and is the only knob most",
                "packs need: arcade | realism | hardcore | custom. The eight values below it are used",
                "ONLY when preset is 'custom'. Everything from 'trophy_chance' down is read directly and",
                "applies whatever the preset is.",
                "Higher is harsher for: phantom, break_sensitivity, depletion, leader_biteoff, line_wear,",
                "hook_wear, snag, foul."
              ],

              "preset": "realism",

              "phantom": 1.0,
              "break_sensitivity": 1.0,
              "depletion": 1.0,
              "leader_biteoff": 0.75,
              "line_wear": 1.0,
              "hook_wear": 1.0,
              "snag": 1.0,
              "foul": 1.0,

              "trophy_chance": 0.04,
              "frenzy_speed": 3.0,
              "bycatch_junk": 0.045,
              "bycatch_treasure": 0.013,

              "consume_bait": true,
              "consume_groundbait": true,
              "update_check": true
            }
            """;
}
