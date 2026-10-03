package com.riverfishing.alife;

import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.engine.Weather;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Runs a pond for fifty game days outside the game and checks that the behaviour the design promises
 * actually emerges from {@link Lake} — nothing here is scripted into the fish.
 *
 * <pre>
 *   gradlew :common:compileJava
 *   java -cp common/build/classes/java/main com.riverfishing.alife.AlifeSim
 * </pre>
 *
 * <p>Exits non-zero on the first broken promise. A tuning change is an edit and a five-second run.
 */
public final class AlifeSim {
    private AlifeSim() {}

    // ponytail: five species copied by hand from their profiles; the game adapter reads FishProfile instead.
    static final Species ROACH = sp("roach", "peaceful", 120, 1000, "mid",
            seasons(1.0, 1.0, 1.0, 0.7), times(1.1, 1.0, 1.1, 0.6), Map.of("maggot", 1.0, "bread", 0.5, "corn", 0.2));
    static final Species BREAM = sp("bream", "peaceful", 900, 4000, "bottom",
            seasons(1.2, 1.2, 0.8, 0.3), times(1.2, 0.8, 1.2, 0.6), Map.of("worm", 0.9, "corn", 0.6, "boilie", 0.3));
    static final Species CARP = sp("carp", "peaceful", 3500, 15000, "bottom",
            seasons(0.8, 1.4, 0.9, 0.05), times(1.2, 0.9, 1.2, 1.0), Map.of("boilie", 1.0, "corn", 0.8, "pea", 0.6));
    static final Species PERCH = sp("perch", "predator", 250, 2000, "mid",
            seasons(1.1, 0.9, 1.3, 0.8), times(1.3, 1.0, 1.2, 0.5), Map.of("worm", 0.6, "livebait", 0.9));
    static final Species PIKE = sp("pike", "predator", 2000, 10000, "mid",
            seasons(1.1, 0.7, 1.5, 0.9), times(1.3, 0.9, 1.3, 0.5), Map.of("livebait", 0.9, "wobbler", 1.0));

    static final int REEDS = 0, OPEN = 1, WEED = 2, DROP = 3, HOLE = 4, SNAGS = 5;

    public static void main(String[] args) {
        Lake lake = pond();
        Random weatherRng = new Random(7);
        Weather[] weather = new Weather[24 * 60 / 6 + 1];
        for (int i = 0; i < weather.length; i++) {
            double r = weatherRng.nextDouble();
            weather[i] = r < 0.7 ? Weather.CLEAR : r < 0.95 ? Weather.RAIN : Weather.THUNDER;
        }
        java.util.function.DoubleFunction<Species.Conditions> at = h -> conditions(h, weather);
        Lake.Agent carpShoal = find(lake, CARP, false), bigCarp = find(lake, CARP, true);
        Random angler = new Random(3);

        int[] carpShallowByHour = new int[24], carpSamplesByHour = new int[24];
        double carpBeforeRate = 0, carpAfterRate = 0;
        int pikeWithRoach = 0, pikeSamples = 0;
        Map<String, Integer> summerCatch = new TreeMap<>(), winterCatch = new TreeMap<>();
        double cornFam = 0, waryAfter = 0, waryLater = 0;
        double trophyRateBefore = 0, trophyRateAfter = 0, trophyRateLater = 0;
        int roachBefore = 0, roachFishedOut = Integer.MAX_VALUE, roachRecovered = 0;

        for (int day = 0; day < 50; day++) {
            for (int hr = 0; hr < 24; hr++) {
                double h = day * 24.0 + hr;
                lake.advance(h, at);
                Species.Conditions c = at.apply(h);

                if (day < 20 && carpShoal.alive()) {
                    carpSamplesByHour[hr]++;
                    if (lake.zones.get(carpShoal.zone).depth <= 2.5) carpShallowByHour[hr]++;
                }
                // how keen the carp are on corn IN THE BAITED SWIM at dawn and dusk — the promise to the player
                boolean edge = c.time() == TimeOfDay.DAWN || c.time() == TimeOfDay.DUSK;
                if (edge && day < 3) carpBeforeRate += carpRate(lake, c, OPEN) / 18;
                if (edge && day >= 10 && day < 13) carpAfterRate += carpRate(lake, c, OPEN) / 18;
                for (Lake.Agent p : lake.agents) {
                    if (p.sp != PIKE || !p.alive()) continue;
                    pikeSamples++;
                    for (Lake.Agent r : lake.agents) if (PIKE.preysOn(r.sp) && r.alive() && r.zone == p.zone) { pikeWithRoach++; break; }
                }

                if (System.getenv("ALIFE_DEBUG") != null && day == 10) {
                    double fed = lake.zones.get(OPEN).feeds.stream().mapToDouble(f -> f.amount).sum();
                    StringBuilder d = new StringBuilder(String.format("d%d %02d feed=%.1f", day, hr, fed));
                    for (Lake.Agent a : lake.agents) if (a.sp == CARP || a.sp == PIKE || a.sp == BREAM)
                        d.append(String.format(" | %s%s z%d h%.2f", a.sp.id(), a.trophy ? "*" : "", a.zone, a.hunger));
                    for (Lake.Agent a : lake.agents) if (a.sp == ROACH) d.append(" r").append(a.zone);
                    System.out.println(d);
                }
                // days 3-9: the angler prebaits the open shallows with corn at seven every morning, then stops
                if (day >= 3 && day < 10 && hr == 7) lake.feed(OPEN, 6, Map.of("corn", 1.0));

                // an hour of corn in the open shallows at dawn and dusk; summer days 21-28 and winter 40-49
                boolean summerSession = day >= 21 && day < 29, winterSession = day >= 40;
                if ((summerSession || winterSession) && (hr == 6 || hr == 19)) {
                    Map<String, Integer> tally = summerSession ? summerCatch : winterCatch;
                    fishHour(lake, c, OPEN, "corn", angler, tally, bigCarp);
                }

                // the released trophy: how keen is it on corn the day before, the day after and a week after
                // (a day's average, so one full belly at noon does not decide it)
                if (day == 10 && hr == 0) cornFam = carpShoal.fam.getOrDefault("corn", 0.0);
                if (day == 13) trophyRateBefore += rateOf(lake, c, bigCarp) / 24;
                if (hr == 0 && day == 14) lake.spooked(bigCarp, true);
                if (day == 14) trophyRateAfter += rateOf(lake, c, bigCarp) / 24;
                if (day == 14 && hr == 23) waryAfter = bigCarp.wary;
                if (day == 20 && hr == 23) waryLater = bigCarp.wary;
                if (day == 20) trophyRateLater += rateOf(lake, c, bigCarp) / 24;

                // the reed shallows are hammered for roach on days 20-24, then left alone
                if (hr == 12 && day == 20) roachBefore = roachIn(lake);
                if (day >= 20 && day < 25 && hr >= 8 && hr < 18) {
                    for (Lake.Agent r : lake.agents) {
                        if (r.sp == ROACH && r.count > 3 && angler.nextDouble() < 0.6) lake.take(r);
                    }
                }
                if (hr == 12 && day == 25) roachFishedOut = roachIn(lake);
                if (hr == 12 && day == 39) roachRecovered = roachIn(lake);
            }
        }

        System.out.println("carp shoal in the shallows, % of summer days, by hour:");
        StringBuilder sb = new StringBuilder();
        for (int hr = 0; hr < 24; hr++) sb.append(String.format("%02d:%3d%% ", hr, pct(carpShallowByHour[hr], carpSamplesByHour[hr])));
        System.out.println("  " + sb);
        System.out.printf("carp bites/h on corn in the baited swim at dawn/dusk: days 0-2 %.3f, days 10-12 (prebaited 3-9) %.3f%n",
                carpBeforeRate, carpAfterRate);
        System.out.printf("carp familiarity with corn at the end of the campaign: %.2f%n", cornFam);
        System.out.printf("pike sharing a zone with something it eats: %d%% of hours%n", pct(pikeWithRoach, pikeSamples));
        System.out.println("summer sessions: " + summerCatch);
        System.out.println("winter sessions: " + winterCatch);
        System.out.printf("released trophy, corn bites/h: before %.3f, day after %.3f, six days on %.3f; wariness %.2f -> %.2f%n",
                trophyRateBefore, trophyRateAfter, trophyRateLater, waryAfter, waryLater);
        System.out.printf("roach: %d before the hammering, %d after, %d two weeks later%n",
                roachBefore, roachFishedOut, roachRecovered);

        int dawn = pct(carpShallowByHour[6], carpSamplesByHour[6]) + pct(carpShallowByHour[19], carpSamplesByHour[19]);
        int noon = pct(carpShallowByHour[13], carpSamplesByHour[13]) + pct(carpShallowByHour[14], carpSamplesByHour[14]);
        require(dawn > noon, "carp must be in the shallows more at dawn/dusk than at midday");
        require(carpAfterRate > carpBeforeRate * 1.3, "a week of prebaiting must make the carp bite in the swim once the feeding stops");
        require(cornFam > 0.2, "fish that ate the corn must be used to it");
        require(pct(pikeWithRoach, pikeSamples) > 60, "pike must follow its prey, not wander");
        int summer = summerCatch.values().stream().mapToInt(Integer::intValue).sum();
        int winter = winterCatch.values().stream().mapToInt(Integer::intValue).sum();
        require(winter * 3 < summer, "winter must fish far slower than summer");
        require(trophyRateAfter < trophyRateBefore * 0.6, "a released trophy must be shy the next day");
        // the rate a week on also carries the corn habit fading, so the recovery is asked of the wariness itself
        require(waryLater < waryAfter * 0.5, "...and come round again within a week");
        require(roachFishedOut < roachBefore * 0.7, "hammering the roach must thin them out for real");
        require(roachRecovered > roachFishedOut * 1.15, "...and the water must refill once left alone");
        // a pond nobody visited for forty days is brought up to date in one call, cheaply and sanely
        Lake away = pond();
        away.feed(OPEN, 20, Map.of("corn", 1.0));
        find(away, CARP, true).wary = 0.9;
        long t0 = System.nanoTime();
        away.advance(24.0 * 40, at);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("forty days away caught up in %d ms%n", ms);
        require(ms < 50, "offline catch-up must stay cheap");
        require(away.zones.get(OPEN).feeds.isEmpty(), "feed left for forty days must be gone");
        require(find(away, CARP, true).wary < 0.01, "a trophy left alone for forty days has forgotten");
        require(away.agents.stream().allMatch(a -> a.hunger >= 0 && a.hunger <= 1 && a.count <= a.capacity),
                "state must stay in range across a long gap");
        ponds();
        // a full sea region: 64 chunk zones and the agent cap — three days of catch-up must stay a blink
        Lake sea = new Lake(9, 0);
        for (int i = 0; i < 64; i++) sea.zones.add(new Lake.Zone("s" + i, (i % 8) * 16 + 8, (i / 8) * 16 + 8, 6, 1, 0.2));
        Species[] kinds = {ROACH, BREAM, CARP, PERCH, PIKE, WHITE_BREAM};
        for (int i = 0; i < 384; i++) sea.agents.add(new Lake.Agent(kinds[i % kinds.length], 20, kinds[i % kinds.length].meanG(), i % 64, false));
        sea.advance(24, at);   // warm the JIT first: a server has long been warm when a player walks up
        long s0 = System.nanoTime();
        sea.advance(24 + 72, at);
        long seaMs = (System.nanoTime() - s0) / 1_000_000;
        System.out.printf("a full sea region (64 zones, 384 shoals), 72 h caught up in %d ms%n", seaMs);
        require(seaMs < 100, "a full region must catch up cheaply");
        System.out.println("AlifeSim: all checks pass");
    }

    static final Species WHITE_BREAM = sp("white_bream", "peaceful", 150, 1200, "bottom",
            seasons(1.1, 1.1, 0.9, 0.4), times(1.2, 0.9, 1.2, 0.6), Map.of("maggot", 1.0, "worm", 0.9, "corn", 0.5));

    /** §alife-life: a private pond holds exactly its fish, breeds them in season, and only as far as it has room. */
    static void ponds() {
        // the bug report: one male in, and the net brings up exactly one fish — then nothing, for good
        Lake one = pondLake(600, 0.2, 1);
        Lake.Agent bream = Life.join(one, WHITE_BREAM, head(1, 1, 180), 0);
        one.advance(24 * 30, h -> summer(h, false));
        int netted = 0;
        for (int i = 0; i < 10 && bream.alive(); i++) { one.take(bream); netted++; }
        System.out.printf("pond: one male released, a month later the net lifts %d, then %s%n", netted, bream.alive() ? "more" : "nothing");
        require(netted == 1 && !bream.alive(), "a pond must hold exactly what went in");

        // a trophy (and a legend) put back comes out as the same fish, badges and all
        Lake back = pondLake(600, 0.2, 2);
        Life.Head t = head(77, 0, 9000);
        t.trophy = true; t.legend = true; t.returned = true;
        Lake.Agent carp = Life.join(back, CARP, t, 0);
        back.advance(24 * 5, h -> summer(h, false));
        Life.Head caught = back.pickHead(carp);
        back.takeHead(carp, caught);
        require(caught.uid == 77 && caught.trophy && caught.legend && caught.returned && !carp.alive(),
                "a released trophy must be caught again as itself");

        // spawning: survivors from one pair, and what predators, weed and a full water do to them
        double open = juveniles(false, 0.1, 600), weed = juveniles(false, 0.9, 600), pike = juveniles(true, 0.1, 600);
        System.out.printf("pond 600 blocks, one carp pair, a spring: %.1f young (weed %.1f, with a pike %.1f)%n", open, weed, pike);
        require(open >= 5 && open <= 20, "a pair in a pond with room must leave 5..20 young");
        require(weed > open, "weed must hide the fry");
        require(pike < open, "a pike must eat fry");
        // a pond's fish grow on what they are given: fed young put on weight, a starved pond's stay fry
        double fedYoung = youngWeight(true), starvedYoung = youngWeight(false);
        System.out.printf("young carp after a summer: fed %.0f g, never fed %.0f g%n", fedYoung, starvedYoung);
        require(fedYoung > 20 && starvedYoung < 5, "fed young must grow and starved ones must not");
        whyNoBite();
        wildSpawn();
        Lake puddle = pondLake(12, 0.0, 3);
        Life.join(puddle, CARP, head(10, 0, 4000), 0);
        Life.join(puddle, CARP, head(11, 1, 3500), 0);
        puddle.advance(24 * 60, h -> summer(h, h / 24 >= 5 && h / 24 < 13));
        System.out.printf("a 12-block puddle after a spawn: %d fish, room %.0f%n", Life.heads(puddle), Life.headRoom(puddle));
        require(Life.heads(puddle) <= Math.max(2, Math.ceil(Life.headRoom(puddle))), "a puddle must never fill past its room");
    }

    /** §alife-spawn: in its window a wild shoal goes to the spawning grounds, spawns there, and its fry swell it. */
    static void wildSpawn() {
        Lake lake = pond();
        Lake.Agent carp = find(lake, CARP, false);
        int before = carp.count, onGrounds = 0, hours = 0;
        for (int h = 1; h <= 24 * 30; h++) {
            final boolean window = h < 24 * 8;
            lake.advance(h, t -> summer(t, window));
            if (window && carp.alive()) { hours++; if (Lake.spawningGround(lake.zones.get(carp.zone))) onGrounds++; }
        }
        System.out.printf("wild carp in their window: %d%% of hours on the spawning grounds; shoal %d -> %d after the fry grew%n",
                pct(onGrounds, hours), before, carp.count);
        require(pct(onGrounds, hours) > 60, "a spawning shoal must be on the spawning grounds");
        require(carp.count > before, "a wild spawn must swell the shoal");
    }

    /** §alife-why: the diagnosis names what really holds the fish back. */
    static void whyNoBite() {
        Species.Conditions noon = summer(12, false);
        Lake.Offer corn = new Lake.Offer(OPEN, s -> s.eats("corn"), List.of("corn"), 0.0);
        // nobody near the bait, the only fish lie in the hole
        Lake far = pond();
        far.agents.clear();
        far.agents.add(new Lake.Agent(CARP, 6, 3500, HOLE, false));
        String away = far.diagnose(corn, noon).reason();
        // a shoal on the bait that has just eaten its fill
        Lake fed = pond();
        fed.agents.clear();
        Lake.Agent full = new Lake.Agent(CARP, 6, 3500, OPEN, false);
        full.hunger = 0.02;
        fed.agents.add(full);
        String sated = fed.diagnose(corn, noon).reason();
        // the same shoal, hungry but hooked and lost twice today
        full.hunger = 0.6;
        full.wary = 0.8;
        String shy = fed.diagnose(corn, noon).reason();
        // fish on the bait that do not eat what is on the hook
        full.wary = 0;
        String rig = fed.diagnose(new Lake.Offer(OPEN, s -> s.eats("livebait"), List.of("livebait"), 0.0), noon).reason();
        // §alife-strike: a pike that has just fed still takes a lure on reflex — slower, but it takes
        Lake.Agent pike = new Lake.Agent(PIKE, 1, 4000, OPEN, true);
        pike.hunger = 0.02;
        fed.agents.add(pike);
        Lake.Offer spoon = new Lake.Offer(OPEN, s -> s.eats("wobbler"), List.of("wobbler"), 0.0, 20.0);
        double fullPike = fed.offer(spoon, noon).total();
        String pikeWhy = fed.diagnose(spoon, noon).reason();
        System.out.printf("a full pike on a wobbler: %.2f bites/h, the hint says %s%n", fullPike, pikeWhy);
        require(fullPike > 1.0 && !pikeWhy.equals("full"), "a fed pike must still strike a lure");
        System.out.printf("why no bite: %s, %s, %s, %s%n", away, sated, shy, rig);
        require(away.equals("away_deep") && sated.equals("full") && shy.equals("wary") && rig.equals("rig"),
                "the diagnosis must name the real reason");
    }

    /** Young carp a pair leaves in one spring, averaged over ten waters. */
    static double juveniles(boolean pike, double cover, double volume) {
        double sum = 0;
        for (int s = 0; s < 10; s++) {
            Lake p = pondLake(volume, cover, 100 + s);
            Life.join(p, CARP, head(1, 0, 4000), 0);
            Life.join(p, CARP, head(2, 1, 3500), 1);
            if (pike) Life.join(p, PIKE, head(3, 0, 3000), 0);
            for (int d = 0; d < 40; d++) {
                p.feed(0, 2, Map.of("corn", 1.0));   // a fed pond, so the parents are fit
                p.advance(24.0 * (d + 1), h -> summer(h, h / 24 >= 5 && h / 24 < 13));
            }
            for (Lake.Agent a : p.agents) if (a.heads != null && a.sp == CARP) sum += a.heads.size() - 2;
        }
        return sum / 10;
    }

    /** Mean weight of the young a carp pair leaves, sixty days after the spawn, with or without daily feed. */
    static double youngWeight(boolean fed) {
        Lake p = pondLake(600, 0.2, 7);
        Life.join(p, CARP, head(1, 0, 4000), 0);
        Life.join(p, CARP, head(2, 1, 3500), 1);
        for (int d = 0; d < 80; d++) {
            if (fed) p.feed(d % 2, 3, Map.of("corn", 1.0));
            p.advance(24.0 * (d + 1), h -> summer(h, h / 24 >= 5 && h / 24 < 13));
        }
        double w = 0;
        int n = 0;
        for (Lake.Agent a : p.agents) {
            if (a.heads == null) continue;
            for (Life.Head h : a.heads) if (h.uid != 1 && h.uid != 2) { w += h.weightG; n++; }
        }
        return n == 0 ? 0 : w / n;
    }

    static Lake pondLake(double volume, double cover, long seed) {
        Lake lake = new Lake(seed, 0);
        lake.catchUpH = 24 * 96;
        lake.pond = true;
        for (int i = 0; i < 2; i++) {
            Lake.Zone z = new Lake.Zone("pond" + i, i * 10, 0, 2.5, 4, cover);
            z.volume = volume / 2;
            lake.zones.add(z);
        }
        return lake;
    }

    static Life.Head head(long uid, int sex, double g) {
        Life.Head h = new Life.Head(uid, sex, g, 0);
        h.lastSpawnHour = -1e9;
        return h;
    }

    static Species.Conditions summer(double h, boolean spawning) {
        int hr = (int) (h % 24);
        TimeOfDay t = hr >= 5 && hr < 8 ? TimeOfDay.DAWN : hr >= 8 && hr < 18 ? TimeOfDay.DAY
                : hr >= 18 && hr < 21 ? TimeOfDay.DUSK : TimeOfDay.NIGHT;
        return new Species.Conditions(Season.SUMMER, t, Weather.CLEAR, 0.7, 1.0, id -> spawning);
    }

    /** An hour of fishing: bites arrive as the interest says; every carp but the trophy is kept. */
    private static void fishHour(Lake lake, Species.Conditions c, int zone, String bait, Random r,
                                 Map<String, Integer> tally, Lake.Agent trophy) {
        double t = 0;
        while (true) {
            Lake.Interest in = lake.offer(new Lake.Offer(zone, s -> s.eats(bait), List.of(bait), 0.1), c);
            if (in.total() <= 0) return;
            t += -Math.log(1 - r.nextDouble()) / in.total();
            if (t > 1.0) return;
            Lake.Agent a = in.pick(r);
            tally.merge(a.sp.id(), 1, Integer::sum);
            if (a == trophy) lake.spooked(a, true); else lake.take(a);
            lake.disturb(zone, 0.15);
        }
    }

    private static double carpRate(Lake lake, Species.Conditions c, int zone) {
        Lake.Interest in = lake.offer(new Lake.Offer(zone, s -> s.eats("corn"), List.of("corn"), 0.1), c);
        double r = 0;
        for (int i = 0; i < in.agents().size(); i++) if (in.agents().get(i).sp == CARP) r += in.rates()[i];
        return r;
    }

    private static double rateOf(Lake lake, Species.Conditions c, Lake.Agent who) {
        // asked at the fish's own zone so the question is "does it want it", not "is it here"
        Lake.Interest in = lake.offer(new Lake.Offer(who.zone, s -> s.eats("corn"), List.of("corn"), 0.1), c);
        for (int i = 0; i < in.agents().size(); i++) if (in.agents().get(i) == who) return in.rates()[i];
        return 0;
    }

    static Lake pond() {
        Lake lake = new Lake(42, 0);
        lake.zones.add(new Lake.Zone("reeds", 0, 0, 1.0, 4, 0.7));
        lake.zones.add(new Lake.Zone("open shallows", 14, 0, 2.0, 1, 0.1));
        lake.zones.add(new Lake.Zone("weed bed", 10, 14, 2.0, 4, 0.9));
        lake.zones.add(new Lake.Zone("drop-off", 28, 6, 4.0, 2, 0.3));
        lake.zones.add(new Lake.Zone("hole", 40, 10, 8.0, 4, 0.2));
        lake.zones.add(new Lake.Zone("snags", 34, 24, 5.0, 3, 0.8));
        for (int i = 0; i < 4; i++) lake.agents.add(new Lake.Agent(ROACH, 30, 120, i % 3, false));
        lake.agents.add(new Lake.Agent(BREAM, 15, 900, HOLE, false));
        lake.agents.add(new Lake.Agent(BREAM, 12, 1100, DROP, false));
        lake.agents.add(new Lake.Agent(CARP, 6, 3500, SNAGS, false));
        lake.agents.add(new Lake.Agent(CARP, 1, 11000, HOLE, true));
        lake.agents.add(new Lake.Agent(PERCH, 12, 250, DROP, false));
        lake.agents.add(new Lake.Agent(PERCH, 10, 220, WEED, false));
        lake.agents.add(new Lake.Agent(PIKE, 1, 3000, WEED, true));
        lake.agents.add(new Lake.Agent(PIKE, 1, 7000, SNAGS, true));
        return lake;
    }

    /** Days 0-29 summer, 30-39 autumn, 40-49 winter; a day is 24 game hours. */
    static Species.Conditions conditions(double h, Weather[] weather) {
        int day = (int) (h / 24), hr = (int) (h % 24);
        Season s = day < 30 ? Season.SUMMER : day < 40 ? Season.AUTUMN : Season.WINTER;
        TimeOfDay t = hr >= 5 && hr < 8 ? TimeOfDay.DAWN : hr >= 8 && hr < 18 ? TimeOfDay.DAY
                : hr >= 18 && hr < 21 ? TimeOfDay.DUSK : TimeOfDay.NIGHT;
        double temp = (s == Season.SUMMER ? 0.7 : s == Season.AUTUMN ? 0.4 : 0.05) + (t == TimeOfDay.DAY ? 0.15 : 0);
        return new Species.Conditions(s, t, weather[Math.min(weather.length - 1, (int) (h / 6))], temp, 1.0);
    }

    private static int roachIn(Lake lake) {
        return lake.agents.stream().filter(a -> a.sp == ROACH).mapToInt(a -> a.count).sum();
    }

    private static Lake.Agent find(Lake lake, Species sp, boolean trophy) {
        return lake.agents.stream().filter(a -> a.sp == sp && a.trophy == trophy).findFirst().orElseThrow();
    }

    private static int pct(int n, int of) { return of == 0 ? 0 : 100 * n / of; }

    private static Species sp(String id, String diet, double meanG, double maxG, String depth,
                              Map<String, Double> season, Map<String, Double> time, Map<String, Double> baits) {
        return new Species(id, diet, meanG, maxG, season, time,
                Map.of("clear", 1.0, "rain", 1.15, "thunder", 0.9), depth, baits);
    }

    /** spring, summer, autumn, winter — then dawn, day, dusk, night. */
    private static Map<String, Double> seasons(double a, double b, double c, double d) {
        return Map.of("spring", a, "summer", b, "autumn", c, "winter", d);
    }

    private static Map<String, Double> times(double a, double b, double c, double d) {
        return Map.of("dawn", a, "day", b, "dusk", c, "night", d);
    }

    private static void require(boolean ok, String what) {
        if (!ok) {
            System.out.println("AlifeSim FAILED: " + what);
            System.exit(1);
        }
    }
}
