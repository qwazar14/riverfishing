package com.riverfishing.engine;

import net.minecraft.util.Mth;

/**
 * §lure-color: the three effective "colour classes" a painted predator lure falls into, and the
 * light/clarity conditions each works best in. A dyed lure's (dye-mixed) RGB is classified by HSV
 * brightness + saturation; the bite engine rewards a colour that suits the current water and penalises a
 * mismatch. The condition axis blends time-of-day, weather, depth, water type and season (§8 choice).
 */
public enum LureColor {
    // idealLight: the condition-light score (0 dark/murky … 1 bright/clear) this class is tuned for.
    NATURAL(0.85),  // white / silver / pale blue — imitates baitfish; clear, bright water
    BRIGHT(0.40),   // chartreuse / orange / red — high-contrast; murky water, low light, overcast
    DARK(0.12);     // black / dark green — a strong silhouette; night, deep or very murky water

    private final double idealLight;

    LureColor(double idealLight) {
        this.idealLight = idealLight;
    }

    public double idealLight() {
        return idealLight;
    }

    /** Classify a (possibly dye-mixed) RGB into a colour class by HSV brightness + saturation. */
    public static LureColor fromRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double max = Math.max(r, Math.max(g, b)) / 255.0;
        double min = Math.min(r, Math.min(g, b)) / 255.0;
        double sat = max <= 0 ? 0 : (max - min) / max;
        if (max < 0.35) return DARK;
        if (sat > 0.5) return BRIGHT;
        return NATURAL;
    }

    /** 0 (dark / murky) … 1 (bright / clear): how well the fish can see a natural, baitfish-like colour. */
    public static double conditionLight(BiteContext c) {
        double v = 0.5;
        switch (c.time) {
            case DAY -> v += 0.28;
            case DAWN, DUSK -> v -= 0.08;
            case NIGHT -> v -= 0.32;
        }
        switch (c.weather) {
            case CLEAR -> v += 0.10;
            case RAIN -> v -= 0.14;
            case THUNDER -> v -= 0.20;
        }
        v += (3 - Math.min(10, c.waterDepth)) * 0.02; // shallow water is brighter, deep is darker
        if (c.biomeSwamp) v -= 0.12;                   // tannin-stained murk
        if (c.biomeRiver) v += 0.04;                   // flowing, clearer
        if (c.season != null) switch (c.season) {
            case SUMMER -> v += 0.05;
            case AUTUMN -> v -= 0.03;
            case WINTER -> v -= 0.06;
            default -> { }
        }
        return Mth.clamp(v, 0.0, 1.0);
    }

    /** Bite multiplier for THIS colour under the given conditions: up to +35% on a match, −25% on a miss. */
    public double conditionMultiplier(BiteContext c) {
        double closeness = 1.0 - Math.min(1.0, Math.abs(conditionLight(c) - idealLight) * 2.0);
        return 0.75 + 0.60 * closeness;
    }
}
