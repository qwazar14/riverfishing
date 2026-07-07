package com.riverfishing.engine;

import com.riverfishing.component.LineType;
import com.riverfishing.component.RigType;
import com.riverfishing.component.RodType;
import com.riverfishing.water.WaterType;

import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot of one cast: the assembled tackle plus the conditions at the spot.
 * Consumed by {@link BiteEngine}. Built server-side when the player casts.
 *
 * <p>Module 4: hooks and baits come from the rig's internal inventory, so they are lists — the
 * engine picks the best fit per fish, and empty (no hooks / no baits) drags the bite chance down.
 */
public class BiteContext {
    // ---- Tackle ----
    public RodType rod;
    public int reelSize;            // 0 = no reel (pole/stick/bamboo)
    public LineType lineType = LineType.MONO;
    public double lineDiameterMm = 0.20;
    public RigType rig;
    public List<Integer> hookSizes = new ArrayList<>(); // from the rig's hook slots
    public List<String> baits = new ArrayList<>();        // from the rig's bait / lure slots
    public boolean hasLeader;
    public double leaderProtection; // bite-through resistance of the fitted leader (0..1)
    public double leaderStealth;    // invisibility of the fitted leader (0..1)
    public double castWeightG;      // rig mass

    // ---- Angler (progression gate) ----
    public int anglerLevel;         // the caster's journal level; species can require a minimum

    /** Float depth setting ("спуск", §fishing-depth): surface/mid/bottom, or null when not float fishing. */
    public String floatDepth;

    // ---- Environment ----
    public WaterType water = WaterType.NONE;
    public Season season;           // null when Serene Seasons is absent -> factor 1.0
    public TimeOfDay time = TimeOfDay.DAY;
    public Weather weather = Weather.CLEAR;
    /** §weather-pressure: barometric bite multiplier (~0.7..1.35), uniform across species. 1.0 = neutral. */
    public double pressureFactor = 1.0;
    /** §skills NATURALIST: flat bite-chance bonus (0.0 = none, +0.05/rank), uniform across species. */
    public double skillBiteBonus = 0.0;
    public double biomeTemperature = 0.7;
    public boolean biomeRiver;
    public boolean biomeSwamp;
    public boolean biomeOcean;
    public double waterWidth = 32;  // max horizontal span of the water body (§4.1)
    public int waterDepth = 3;      // water-column depth (blocks) at the cast point — habitat gate
    /** Biome groups at the spot (climate + terrain: cold/temperate/warm, taiga, jungle, swamp, mountain…). */
    public java.util.Set<String> biomeGroups = new java.util.HashSet<>();
    public double castDistance = 8;

    // ---- Fed spot (§5) ----
    public boolean inFeedZone;
    public double feedFreshness;    // 0..1
    public String feedCategory;     // powder / grain / pellet / cake

    /** §ice-fishing: the cast is through a hole in an ice sheet — the engine treats it as winter. */
    public boolean iceHole;

    /** §population: per-species depletion at this spot (1.0 plenty … 0.1 fished out), or null = neutral. */
    public java.util.function.ToDoubleFunction<net.minecraft.resources.ResourceLocation> speciesFactor;
}
