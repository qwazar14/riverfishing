package com.riverfishing.fish;

import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.engine.Weather;

import java.util.List;

/**
 * §boilies: the author's flavour rules, asked scene by scene — which boilie a carp (or a pike, or a roach)
 * takes best — and a failure if the answer contradicts the notes. Pure, no Minecraft:
 *
 * <pre>
 *   java -cp common/build/classes/java/main com.riverfishing.fish.BoilieCheck
 * </pre>
 */
public final class BoilieCheck {
    private BoilieCheck() {}

    static Boilie b(Boilie.Buoyancy by, int mm, Flavour... f) {
        return new Boilie(List.of(f), by, mm, false, null);
    }

    static Boilie.Scene carp(Season s, TimeOfDay t, Weather w, boolean shift, double clarity, int bed) {
        return new Boilie.Scene(s, t, w, shift, clarity, bed, 0.2, false, "peaceful", "cyprinid", 3500);
    }

    public static void main(String[] args) {
        Boilie strawberry = b(Boilie.Buoyancy.SINKER, 20, Flavour.STRAWBERRY);
        Boilie squid = b(Boilie.Buoyancy.SINKER, 20, Flavour.SQUID);
        Boilie garlic = b(Boilie.Buoyancy.SINKER, 20, Flavour.GARLIC);
        Boilie liver = b(Boilie.Buoyancy.SINKER, 24, Flavour.LIVER);
        Boilie fish = b(Boilie.Buoyancy.SINKER, 20, Flavour.FISH);
        Boilie tutti = b(Boilie.Buoyancy.SINKER, 20, Flavour.TUTTI_FRUTTI);
        Boilie pineapple = b(Boilie.Buoyancy.SINKER, 20, Flavour.PINEAPPLE);
        Boilie caramel = b(Boilie.Buoyancy.SINKER, 20, Flavour.CARAMEL);
        Boilie citrusPop = b(Boilie.Buoyancy.POPUP, 15, Flavour.CITRUS);

        Boilie.Scene coldSpring = carp(Season.SPRING, TimeOfDay.DAY, Weather.CLEAR, false, 0.9, 1);
        Boilie.Scene summerNoon = carp(Season.SUMMER, TimeOfDay.DAY, Weather.CLEAR, false, 0.9, 1);
        Boilie.Scene night = carp(Season.SUMMER, TimeOfDay.NIGHT, Weather.CLEAR, false, 0.9, 1);
        Boilie.Scene mud = carp(Season.AUTUMN, TimeOfDay.DUSK, Weather.CLEAR, false, 0.9, 4);
        Boilie.Scene clearWater = carp(Season.AUTUMN, TimeOfDay.DAY, Weather.CLEAR, false, 1.0, 1);
        Boilie.Scene storm = carp(Season.AUTUMN, TimeOfDay.DAY, Weather.CLEAR, true, 0.9, 1);
        Boilie.Scene pike = new Boilie.Scene(Season.AUTUMN, TimeOfDay.DUSK, Weather.CLEAR, false, 0.9, 1, 0.3, false,
                "predator", "predator", 2000);

        row("cold spring day (carp)", coldSpring, strawberry, squid, garlic);
        row("summer noon, clear, hot", summerNoon, strawberry, pineapple, liver);
        row("summer night", night, tutti, liver, garlic);
        row("autumn dusk, muddy bottom", mud, caramel, pineapple, garlic);
        row("autumn, crystal-clear water", clearWater, tutti, fish, caramel);
        row("the glass jumped", storm, liver, citrusPop, strawberry);
        row("a pike", pike, strawberry, liver, fish);

        require(squid.factor(coldSpring) > strawberry.factor(coldSpring) && garlic.factor(coldSpring) > strawberry.factor(coldSpring),
                "cold water wants protein and spice, not fruit");
        require(strawberry.factor(summerNoon) > liver.factor(summerNoon) && pineapple.factor(summerNoon) > liver.factor(summerNoon),
                "the heat of a summer noon wants sweet and sour, and puts fish off meat");
        require(liver.factor(night) > tutti.factor(night) && garlic.factor(night) > tutti.factor(night),
                "night wants heavy protein and spice");
        require(pineapple.factor(mud) > caramel.factor(mud) && garlic.factor(mud) > caramel.factor(mud),
                "mud swallows a mild smell; a punchy one cuts through");
        require(fish.factor(clearWater) > tutti.factor(clearWater), "clear water wants a natural smell");
        require(citrusPop.factor(storm) > liver.factor(storm), "a sharp change: only a sour, bright pop-up wakes them");
        require(liver.factor(pike) > strawberry.factor(pike) * 2, "a predator wants meat");
        require(b(Boilie.Buoyancy.SINKER, 24, Flavour.FISH).tooBigFor(120), "a roach cannot take a 24 mm boilie");
        require(b(Boilie.Buoyancy.SINKER, 10, Flavour.STRAWBERRY).nuisanceBait(), "a small sweet boilie is for the small fish");
        // §boilie-why: the journal's builder lists the rules; they have to add up to the number it shows
        Boilie snow = new Boilie(List.of(Flavour.STRAWBERRY, Flavour.GARLIC), Boilie.Buoyancy.SNOWMAN, 24, true, Flavour.KRILL);
        for (Boilie.Scene s : List.of(coldSpring, summerNoon, night, mud, clearWater, storm)) {
            double[] sum = {0};
            double f = snow.factor(s, (rule, v) -> sum[0] += v);
            require(Math.abs(Math.max(0.3, Math.min(2.0, Math.exp(0.85 * sum[0]))) - f) < 1e-9, "the builder's why adds up");
        }

        double lo = 9, hi = 0;
        for (Boilie x : List.of(strawberry, squid, garlic, liver, fish, tutti, pineapple, caramel, citrusPop)) {
            for (Boilie.Scene s : List.of(coldSpring, summerNoon, night, mud, clearWater, storm, pike)) {
                lo = Math.min(lo, x.factor(s));
                hi = Math.max(hi, x.factor(s));
            }
        }
        System.out.printf("corridor across the scenes: x%.2f .. x%.2f%n", lo, hi);
        require(lo >= 0.3 && hi <= 2.0 && hi / lo >= 4, "the corridor must be wide and bounded");
        System.out.println("BoilieCheck: all checks pass");
    }

    static void row(String what, Boilie.Scene s, Boilie... bs) {
        StringBuilder sb = new StringBuilder(String.format("%-30s", what));
        for (Boilie x : bs) {
            sb.append(String.format("  %s%s x%.2f", x.flavours().get(0).id(),
                    x.buoyancy() == Boilie.Buoyancy.POPUP ? "(pop)" : "", x.factor(s)));
        }
        System.out.println(sb);
    }

    static void require(boolean ok, String what) {
        if (!ok) {
            System.out.println("BoilieCheck FAILED: " + what);
            System.exit(1);
        }
    }
}
