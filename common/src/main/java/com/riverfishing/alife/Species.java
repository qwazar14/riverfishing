package com.riverfishing.alife;

import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.engine.Weather;

import java.util.Map;

/**
 * §alife: what the living water needs to know about a species — a plain copy of the few profile fields the
 * simulation reads, so the core has no Minecraft on its classpath. The game builds these from FishProfile;
 * the headless runner writes them by hand.
 *
 * <p>The season / time / weather maps are the profile's own. In the old engine they multiplied the bite; here
 * they are how ACTIVE the fish is, which drives how fast it gets hungry and how far it will go to feed.
 */
public record Species(String id, String diet, double meanG, double maxG,
                      Map<String, Double> season, Map<String, Double> time, Map<String, Double> weather,
                      String depthPref, Map<String, Double> baits) {

    public boolean predator() { return "predator".equals(diet); }

    /**
     * §alife-age: how many game years this species lives — read off how big it grows, because the profiles
     * carry no age and size says it well enough: a bleak ~4, a roach ~10, a carp ~17, a sturgeon ~30+.
     */
    public double lifespanYears() {
        return Math.max(4.0, Math.min(40.0, 4.0 + 6.0 * Math.log10(Math.max(100.0, maxG) / 100.0)));
    }

    /** How much of this species' own bait list a thing is — 0 = it does not eat that. */
    public double eats(String key) { return baits.getOrDefault(key, 0.0); }

    /** A predator eats fish at most a fifth of its own weight (BiteEngine.PREY_RATIO). */
    public boolean preysOn(Species other) { return predator() && other.meanG * 5.0 <= meanG; }

    /** Activity 0..~2 under these conditions — the same amplification the old engine gave the bite. */
    public double activity(Conditions c) {
        double s = Math.pow(season.getOrDefault(c.season().jsonKey(), 1.0), 1.5);
        double t = Math.pow(time.getOrDefault(c.time().jsonKey(), 1.0), 1.4);
        double w = weather.getOrDefault(c.weather().jsonKey(), 1.0);
        return s * t * w * c.pressureFactor();
    }

    /** Conditions of the moment. waterTemp 0 = ice water, 1 = the hottest summer afternoon. */
    public record Conditions(Season season, TimeOfDay time, Weather weather, double waterTemp, double pressureFactor,
                             java.util.function.Predicate<String> spawning) {
        public Conditions(Season season, TimeOfDay time, Weather weather, double waterTemp, double pressureFactor) {
            this(season, time, weather, waterTemp, pressureFactor, id -> false);
        }
    }
}
