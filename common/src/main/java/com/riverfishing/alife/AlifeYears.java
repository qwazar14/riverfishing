package com.riverfishing.alife;

import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.engine.Weather;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleFunction;

/**
 * §alife-years: a water left to itself for {@code days} game days (a year is 96, as the mod's calendar), to
 * see whether it balances — the shoals, the predators on them, the spawns, the fry. Prints one line a year.
 *
 * <pre>
 *   java -cp common/build/classes/java/main com.riverfishing.alife.AlifeYears [days]
 * </pre>
 */
public final class AlifeYears {
    private AlifeYears() {}

    private static final int YEAR = 96, SEASON = 24, SUB = 8;
    /** Print each year's births and deaths by cause under its line. */
    private static final boolean LEDGER = Boolean.getBoolean("alife.ledger");
    /** Where each species' spawning window sits in the year: season index, sub-season index. */
    private static final Map<String, int[]> WINDOW = Map.of(
            "roach", new int[]{0, 1}, "perch", new int[]{0, 0}, "pike", new int[]{0, 0},
            "bream", new int[]{0, 2}, "white_bream", new int[]{0, 2}, "carp", new int[]{1, 0});

    public static void main(String[] args) {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        Species[] all = {AlifeSim.ROACH, AlifeSim.BREAM, AlifeSim.CARP, AlifeSim.PERCH, AlifeSim.PIKE, AlifeSim.WHITE_BREAM};

        System.out.println("=== 1. a wild pond, nobody fishing ===");
        run(AlifeSim.pond(), all, days, 0, false);

        System.out.println("=== 2. the same pond, an angler there two hours every dawn, keeping what he catches ===");
        run(AlifeSim.pond(), all, days, days, false);

        System.out.println("=== 2b. fished like that for three years, then left alone ===");
        run(AlifeSim.pond(), all, days, YEAR * 3, false);

        System.out.println("=== 3a. a private pond of 600 blocks: 4 carp pairs, 2 white bream pairs and a pike, fed every day ===");
        run(privatePond(), all, days, 0, true);
        System.out.println("=== 3b. the same pond, never fed ===");
        run(privatePond(), all, days, 0, false);
    }

    static Lake privatePond() {
        Lake p = AlifeSim.pondLake(600, 0.3, 11);
        long uid = 1;
        for (int i = 0; i < 4; i++) {
            Life.join(p, AlifeSim.CARP, AlifeSim.head(uid++, 0, 3500 + i * 200), i % 2);
            Life.join(p, AlifeSim.CARP, AlifeSim.head(uid++, 1, 3200 + i * 150), i % 2);
        }
        for (int i = 0; i < 2; i++) {
            Life.join(p, AlifeSim.WHITE_BREAM, AlifeSim.head(uid++, 0, 220), 0);
            Life.join(p, AlifeSim.WHITE_BREAM, AlifeSim.head(uid++, 1, 200), 1);
        }
        Life.join(p, AlifeSim.PIKE, AlifeSim.head(uid++, 0, 2500), 1);
        return p;
    }

    private static void run(Lake lake, Species[] all, int days, int fishing, boolean feed) {
        Random weather = new Random(5), angler = new Random(9);
        Weather[] sky = new Weather[days * 4 + 8];
        for (int i = 0; i < sky.length; i++) {
            double r = weather.nextDouble();
            sky[i] = r < 0.7 ? Weather.CLEAR : r < 0.95 ? Weather.RAIN : Weather.THUNDER;
        }
        DoubleFunction<Species.Conditions> at = h -> conditions(h, sky);
        StringBuilder head = new StringBuilder(String.format("%5s", "year"));
        for (Species s : all) head.append(String.format(" %11s", s.id()));
        head.append(String.format(" %6s %7s %6s", "fry", "kg", "caught"));
        System.out.println(head);
        print(lake, all, 0, 0);
        Map<String, Integer> events = new java.util.TreeMap<>();
        lake.ledger = (what, n) -> events.merge(what, n, Integer::sum);
        int caught = 0;
        for (int d = 1; d <= days; d++) {
            if (feed) lake.feed(d % lake.zones.size(), 3, Map.of("corn", 1.0));
            lake.advance(24.0 * d, at);
            if (d <= fishing) caught += fishDay(lake, angler, at.apply(24.0 * d - 18));
            if (d % YEAR == 0 || d == days) {
                print(lake, all, d / (double) YEAR, caught);
                if (LEDGER) System.out.println("       " + events);
                events.clear();
                caught = 0;
            }
        }
    }

    /**
     * Two hours at dawn in the open shallows with worm and corn, bites as the living water gives them — so
     * the fewer fish there are, the fewer he catches, which is how a real water is fished down.
     */
    private static int fishDay(Lake lake, Random r, Species.Conditions dawn) {
        int n = 0;
        double t = 0;
        List<String> bait = List.of("worm", "corn", "maggot", "livebait");
        Lake.Offer o = new Lake.Offer(AlifeSim.OPEN, s -> {
            double b = 0;
            for (String k : bait) b = Math.max(b, s.eats(k));
            return b * 0.5;   // an ordinary rig, not a perfect one
        }, bait, 0.1);
        while (true) {
            Lake.Interest in = lake.offer(o, dawn);
            if (in.total() <= 0) break;
            t += -Math.log(1 - r.nextDouble()) / in.total() + 0.1;   // a tenth of an hour to land and rebait
            if (t > 2.0) break;
            Lake.Agent a = in.pick(r);
            if (a == null) break;
            lake.take(a);
            n++;
        }
        return n;
    }

    private static void print(Lake lake, Species[] all, double year, int caught) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (Species s : all) count.put(s.id(), 0);
        int fry = 0;
        for (Lake.Agent a : lake.agents) {
            if (!a.alive()) continue;
            if (a.fry) { fry += a.count; continue; }
            count.merge(a.sp.id(), a.count, Integer::sum);
        }
        for (Life.Roe r : lake.roe) fry += (int) r.eggs;
        StringBuilder line = new StringBuilder(String.format("%5.1f", year));
        for (Species s : all) line.append(String.format(" %11d", count.get(s.id())));
        line.append(String.format(" %6d %7.1f %6d", fry, Life.kg(lake), caught));
        System.out.println(line);
    }

    /** The mod's calendar: 96-day year, 24-day seasons, 8-day subs; a day and night; spawning by window. */
    static Species.Conditions conditions(double h, Weather[] sky) {
        int day = (int) (h / 24);
        int doy = day % YEAR, hr = (int) (h % 24);
        Season s = Season.values()[doy / SEASON];
        TimeOfDay t = hr >= 5 && hr < 8 ? TimeOfDay.DAWN : hr >= 8 && hr < 18 ? TimeOfDay.DAY
                : hr >= 18 && hr < 21 ? TimeOfDay.DUSK : TimeOfDay.NIGHT;
        double temp = (s == Season.SUMMER ? 0.7 : s == Season.SPRING ? 0.45 : s == Season.AUTUMN ? 0.4 : 0.05)
                + (t == TimeOfDay.DAY ? 0.15 : 0);
        return new Species.Conditions(s, t, sky[Math.min(sky.length - 1, (int) (h / 6))], temp, 1.0, id -> {
            int[] w = WINDOW.get(id);
            if (w == null) return false;
            int start = w[0] * SEASON + w[1] * SUB;
            return Math.floorMod(doy - start, YEAR) < SUB;
        });
    }
}
