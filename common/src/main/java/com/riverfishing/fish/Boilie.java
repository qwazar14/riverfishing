package com.riverfishing.fish;

import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.engine.Weather;

import java.util.List;
import java.util.Locale;
import java.util.function.ObjDoubleConsumer;

/**
 * §boilies: one boilie — up to two flavours, how it floats, how big it is, whether fish meal went into it, and
 * the dip it was soaked in — and the rules that decide how a fish takes it here and now.
 *
 * <p>The rules are the author's carp-angling notes turned into points: cold water wants protein and spice,
 * warm water fruit, sweet and sour; overcast and rain want heavy, nourishing smells, a bright hot noon light
 * sweet and sour ones and puts fish off meat; a sharp change of the glass kills the appetite, and only a sour,
 * bright pop-up wakes it; murky water wants a strong smell and a dip, clear water a natural one (a big fish
 * most of all); a muddy bottom swallows a mild smell, a clean one suits fish and shellfish; dawn likes a small
 * bright pop-up and fruit with spice, the day sour and vanilla-quiet baits, the evening fruit and fish, the
 * night heavy protein and spice on a big sinker. A bright colour goes with fruit, a dark one with protein;
 * at night nobody sees the colour. A predator wants meat and fish; small fish cannot take a big boilie.
 *
 * <p>Each rule adds points; the factor is {@code exp(0.85 × points)}, held to 0.3..2.0 — a wide corridor, as asked:
 * the right boilie for the day is worth six of the wrong one. Pure: the whole rulebook runs in a check.
 */
public record Boilie(List<Flavour> flavours, Buoyancy buoyancy, int sizeMm, boolean meal, Flavour dip) {

    public enum Buoyancy { SINKER, WAFTER, POPUP, SNOWMAN }

    /** The standard sizes, by how many measures of base went into the paste. */
    public static final int[] SIZES = {10, 15, 20, 24};

    /** A plain boilie off the shelf: no flavour, sinks, 20 mm. It has no opinion and gets none. */
    public static final Boilie PLAIN = new Boilie(List.of(), Buoyancy.SINKER, 20, false, null);

    /**
     * What the water and the fish are like at the moment of the cast. {@code shift}: the glass has moved
     * sharply. {@code clarity}: 1 untouched, lower is murkier. {@code bed}: FishingManager.bedType codes (4 mud,
     * 1..3 sand/gravel/clay). {@code cover}: weed around the bait. {@code current}: running water.
     */
    public record Scene(Season season, TimeOfDay time, Weather weather, boolean shift, double clarity,
                        int bed, double cover, boolean current, String diet, String group, double meanG) {}

    private static final double MIN = 0.3, MAX = 2.0;

    /** How this fish takes this boilie here and now: 0.3..2.0 around the boilie as it is. */
    public double factor(Scene s) {
        return factor(s, null);
    }

    /**
     * §boilie-why (1.1.0): the same sum, with every rule that moved it told to {@code why} as (rule, points) —
     * the journal's boilie builder shows the angler why. A flavour's rules come as "flavour/rule", already
     * divided by how many flavours share the boilie, so the reported points add up to the sum exactly.
     */
    public double factor(Scene s, ObjDoubleConsumer<String> why) {
        double p = 0;
        if (!flavours.isEmpty()) {
            double sum = 0;
            int n = flavours.size();
            for (Flavour f : flavours) sum += points(f, s, why == null ? null : (r, v) -> why.accept(f.id() + "/" + r, v / n));
            p += sum / flavours.size();
            if (flavours.size() == 2) p += note(why, "combo", combo(flavours.get(0), flavours.get(1), s));
        }
        if (dip != null) p += dipPoints(dip, s, why);
        p += buoyancyPoints(s, why);
        p += sizePoints(s, why);
        if (meal && (cold(s) || s.time() == TimeOfDay.NIGHT)) p += note(why, "meal", 0.15);   // fish meal: the nourishing base cold and night want
        return Math.max(MIN, Math.min(MAX, Math.exp(0.85 * p)));   // 0.85: the good ones stay apart under the ceiling
    }

    /** Pass a rule's points on to whoever asked why, and back into the sum. */
    private static double note(ObjDoubleConsumer<String> why, String rule, double v) {
        if (why != null && v != 0) why.accept(rule, v);
        return v;
    }

    /** §boilies: a dipped or strong-smelling boilie is found from further off in murky water. */
    public double reachBonus(Scene s) {
        boolean strong = dip != null || flavours.stream().anyMatch(Flavour::strong);
        return strong && murky(s) ? 4.0 : dip != null ? 2.0 : 0.0;
    }

    /** §boilies: can a small fish take this at all? A 20 mm boilie is more than a roach's mouth. */
    public boolean tooBigFor(double meanG) {
        return meanG < 300 && sizeMm >= 20;
    }

    /** §boilies: a small, sweet boilie is stripped by the small fish — what they take instead of the carp. */
    public boolean nuisanceBait() {
        return sizeMm <= 15 && (flavours.isEmpty() || flavours.stream().anyMatch(f -> f.sweet() || f.family == Flavour.Family.NUT));
    }

    // ---- the rules ----

    static boolean cold(Scene s) {
        return s.season() == Season.WINTER || s.season() == Season.SPRING;
    }

    static boolean warm(Scene s) {
        return s.season() == Season.SUMMER;
    }

    static boolean overcast(Scene s) {
        return s.weather() == Weather.RAIN || s.weather() == Weather.THUNDER;
    }

    static boolean heat(Scene s) {
        return warm(s) && s.time() == TimeOfDay.DAY && s.weather() == Weather.CLEAR;
    }

    static boolean murky(Scene s) {
        return s.clarity() < 0.85 || overcast(s);
    }

    static boolean clear(Scene s) {
        return s.clarity() >= 1.0 && s.weather() == Weather.CLEAR;
    }

    static boolean predatorDiet(Scene s) {
        return "predator".equals(s.diet()) || "catfish".equals(s.group());
    }

    static double points(Flavour f, Scene s) {
        return points(f, s, null);
    }

    static double points(Flavour f, Scene s, ObjDoubleConsumer<String> why) {
        double p = 0;
        // the water's temperature
        if (cold(s)) p += note(why, "cold", f.protein() || f.spice() ? 0.4 : f.sweet() ? -0.3 : 0);
        if (warm(s)) p += note(why, "warm", f.sweet() || f.sour() ? 0.35 : f.protein() ? -0.15 : 0);
        // the sky
        if (overcast(s)) p += note(why, "overcast", f.protein() || f.spice() ? 0.3 : 0);
        if (heat(s)) p += note(why, "heat", f.protein() ? -0.45 : f.sweet() || f.sour() ? 0.25 : 0);
        // the glass jumped: appetite is gone but for a sharp, sour smell
        if (s.shift()) p += note(why, "shift", f.sour() ? 0.35 : -0.1);
        // how far the water carries the smell, and how much it lets a fish see
        if (murky(s)) p += note(why, "murky", f.strong() ? 0.3 : -0.1);
        if (clear(s)) p += note(why, "clear", f.strong() ? -0.25 * (s.meanG() > 2000 ? 1.5 : 1.0) : f.natural() ? 0.2 : 0);
        // the bottom it lies on
        if (s.bed() == 4) p += note(why, "mud", f.punchy() ? 0.3 : -0.2);
        else if (s.bed() >= 1 && s.bed() <= 3) p += note(why, "hard_bed", f.natural() && f.protein() ? 0.25 : 0);
        // the hour
        p += note(why, "hour_" + s.time().name().toLowerCase(Locale.ROOT), switch (s.time()) {
            case DAWN -> f.sweet() || f.spice() ? 0.2 : 0;
            case DAY -> f.sour() || f.family == Flavour.Family.SWEET || f.family == Flavour.Family.NUT ? 0.2 : f.strong() ? -0.1 : 0;
            case DUSK -> f.sweet() || f.protein() ? 0.15 : 0;
            case NIGHT -> f.protein() || f.spice() ? 0.35 : f.fruit() ? -0.25 : 0;
        });
        // who is eating
        if (predatorDiet(s)) p += note(why, "predator", f.protein() ? 0.4 : f.sweet() || f.fruit() ? -0.9 : 0);
        else if ("peaceful".equals(s.diet())) p += note(why, "peaceful", f.sweet() || f.family == Flavour.Family.NUT ? 0.15 : 0);
        // colour: bright with fruit, dark with protein — at night nobody sees it
        if (s.time() != TimeOfDay.NIGHT) p += note(why, "colour", f.bright == f.fruit() ? 0.05 : -0.1);
        return p;
    }

    /** Two flavours that work together: fruit and spice at dawn, fish and fruit at dusk (the evening sandwich). */
    static double combo(Flavour a, Flavour b, Scene s) {
        boolean fruitSpice = (a.fruit() && b.spice()) || (a.spice() && b.fruit());
        boolean fishFruit = (a.protein() && b.fruit()) || (a.fruit() && b.protein());
        if (fruitSpice && s.time() == TimeOfDay.DAWN) return 0.15;
        if (fishFruit && s.time() == TimeOfDay.DUSK) return 0.15;
        return 0;
    }

    /** A dip carries a smell a long way through murky water; in clear water a big fish is warier of it. */
    static double dipPoints(Flavour d, Scene s, ObjDoubleConsumer<String> why) {
        double p = note(why, "dip_murky", murky(s) ? 0.25 : 0);
        if (clear(s) && s.meanG() > 2000) p += note(why, "dip_wary", -0.1);
        return p + note(why, "dip_smell", 0.25 * points(d, s));   // and a quarter of the dip's own flavour rules
    }

    double buoyancyPoints(Scene s, ObjDoubleConsumer<String> why) {
        double p = 0;
        boolean fluoWithProtein = flavours.stream().anyMatch(f -> f.protein() && !f.bright);   // a fluo ball tasting of meat
        switch (buoyancy) {
            case POPUP -> {
                if (s.bed() == 4) p += note(why, "popup_mud", 0.25);                 // above the silt, not in it
                if (s.cover() >= 0.5) p += note(why, "popup_weed", 0.2);              // over the weed
                if (s.current()) p += note(why, "popup_current", -0.3);                   // swung about by the current
                if (s.time() == TimeOfDay.NIGHT) p += note(why, "popup_night", -0.2);   // nobody sees it at night
                if (s.time() == TimeOfDay.DAWN && sizeMm <= 15) p += note(why, "popup_dawn", 0.15);
                if (s.shift()) p += note(why, "popup_shift", 0.2);                     // a bright pop-up wakes a sulking fish
                if (fluoWithProtein && s.time() != TimeOfDay.NIGHT) p += note(why, "popup_fluo", -0.1);
            }
            case SINKER -> {
                if (s.current()) p += note(why, "sinker_current", 0.15);
                if (s.time() == TimeOfDay.NIGHT && sizeMm >= 20) p += note(why, "sinker_night", 0.15);
                if (s.bed() == 4) p += note(why, "sinker_mud", -0.1);
            }
            case WAFTER -> {
                if (s.time() == TimeOfDay.DAY) p += note(why, "wafter_day", 0.1);
                if (s.meanG() > 2000) p += note(why, "wafter_big", 0.1);              // a wary big fish takes what behaves like a free offering
            }
            case SNOWMAN -> {
                if (s.time() == TimeOfDay.DUSK) p += note(why, "snowman_dusk", 0.2);
                if (s.bed() == 4) p += note(why, "snowman_mud", 0.1);
            }
        }
        return p;
    }

    double sizePoints(Scene s, ObjDoubleConsumer<String> why) {
        if (s.meanG() < 300) return note(why, "size_small", sizeMm <= 10 ? 0.2 : sizeMm <= 15 ? 0 : -0.6);
        if (s.meanG() > 2000) return note(why, "size_big", sizeMm >= 24 ? 0.2 : sizeMm <= 10 ? -0.2 : 0);
        return 0;
    }
}
