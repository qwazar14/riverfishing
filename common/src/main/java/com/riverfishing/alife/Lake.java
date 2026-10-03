package com.riverfishing.alife;

import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

/**
 * §alife: one body of water that lives whether anyone fishes it or not.
 *
 * <p>The water is a handful of {@link Zone}s (reed edge, open shallows, weed, drop-off, hole…) and the fish
 * in it are {@link Agent}s — a shoal of forty roach is ONE agent with a count, a trophy carp is an agent of
 * its own. Every step each agent gets hungrier at the rate its activity allows, eats what its zone offers,
 * and moves to the zone that best serves what it wants right now: to feed, to rest, to get away from a pike.
 * The angler does not create fish. A cast asks the agents that are near the bait whether they want it
 * ({@link #offer}); feed changes where they go and what they are used to; a catch takes a fish out of the
 * water for real, and a fish that got away remembers.
 *
 * <p>Pure Java, zero Minecraft imports, on purpose: {@code AlifeSim} runs a lake for weeks in a second, and
 * that run is how every number here is tuned.
 */
public final class Lake {
    /** Hours of game time an online step covers. */
    public static final double STEP_H = 0.25;
    /** A gap longer than this is not stepped through: memory decays in closed form, the last day is lived. */
    public static final double CATCH_UP_H = 72.0;

    // Tuning. Every one of these is exercised by AlifeSim's checks.
    private static final double HUNGER_RATE = 0.12;      // per hour at activity 1
    private static final double EAT_RATE = 0.8;          // hunger an hour of good food takes away at activity 1
    private static final double NATURAL_REGROW = 0.04;   // natural food per hour back toward 1
    private static final double NATURAL_PER_KG = 3.0;    // a zone regrows ~1 a day: a third of a kilo of meals — the water only feeds so much
    private static final double MEAL_SHARE = 0.02;
    private static final double CROWD_KG = 8.0;          // other fish's biomass at which a zone's food is halved per head
    private static final double WARY_HALF_H = 48.0, WARY_HALF_TROPHY_H = 96.0;
    private static final double FAM_HALF_H = 150.0;
    private static final double FEED_HALF_H = 18.0;      // groundbait on the bottom rots / washes out
    private static final double FEAR_HALF_H = 0.03;      // ~2 minutes: the short fright, SpookData's job in-game
    private static final double TROPHY_RESPAWN_H = 24.0 * 20;
    private static final double BITE_K = 8.0;            // bites per game hour from one ideal, hungry fish ×√count (tuned on a real river: a good rig on a shoal waits ~10 s)
    private static final double SCENT_BLOCKS = 12.0;     // the next chunk (16 blocks) still smells a quarter of it
    /** How far a fish looks for a better zone in one step — keeps a 64-zone sea region cheap. */
    private static final double MOVE_RANGE = 20.0;   // the next chunk over: a journey is a few steps, and a sea region stays cheap

    public final List<Zone> zones = new ArrayList<>();
    public final List<Agent> agents = new ArrayList<>();
    public double hour;
    /** Something the angler did happened here — this water is saved; an untouched one is re-grown from the seed. */
    public boolean touched;
    /**
     * §alife-save: this touched water changed since it was last written — it lived a step, or something was done
     * to it. The save is rebuilt only when some water has; a touched water nobody visits costs the autosave nothing.
     */
    public boolean changed;
    /** Eggs on the bottom (§alife-life) — public so they can be drawn one day. */
    public final List<Life.Roe> roe = new ArrayList<>();
    /** The game's genetics; the default makes plausible fish when nobody is asked. */
    public Life.Nursery nursery = Life.Nursery.DEFAULT;
    /** Longest gap lived step by step. A pond is lived through a whole year: that is where its fish grow and spawn. */
    public double catchUpH = CATCH_UP_H;
    /** §alife-age: a player's pond — its fish die of nothing but a hook, a net or a pike. Wild water ages its fish. */
    public boolean pond;
    /** §alife-years: who was born and who died of what, for the long runs; null in the game. */
    public java.util.function.ObjIntConsumer<String> ledger;

    /** Something the angler (or a remembered fish's life) did: this water is kept, and written at the next save. */
    public void touch() {
        touched = true;
        changed = true;
    }

    void note(String what, int n) {
        if (ledger != null && n != 0) ledger.accept(what, n);
    }

    /** Game hours in a year (96 days), what lifespans are counted in. */
    public static final double YEAR_H = 96 * 24;
    private final Random rng;
    /** The agents in each zone, rebuilt at the top of every step — a step costs agents × zones, not agents² × zones. */
    private List<List<Agent>> byZone = new ArrayList<>();

    public Lake(long seed, double hour) {
        this.rng = new Random(seed);
        this.hour = hour;
    }

    // ---- the water ----

    /** A part of the water with one character. {@code bed}: FishingManager.bedType's codes (4 = mud). */
    public static final class Zone {
        public final String name;
        public final double x, z, depth, baseCover;
        /** Weed and snags, 0..1: the surveyed cover plus what the upgrade blocks add. */
        public double cover;
        public final int bed;
        public double natural = 1.0;
        public double fear;
        public final List<Feed> feeds = new ArrayList<>();
        /** Blocks of water. Food, room and fry all scale with it; 1024 is a chunk four deep. */
        public double volume = 1024;
        /** Food and room multiplier the zone's upgrades give (a feeding station, an aerator); 1 = none. */
        public double richness = 1.0;
        /** §shoal-live: the zone's deepest surveyed water surface, where its fish are drawn; ay = MIN_VALUE = unknown. */
        public int ax, ay = Integer.MIN_VALUE, az, adepth;
        /** Species that can live in this zone at all; empty = any. A sea fish does not swim up the river. */
        public final java.util.Set<String> lives = new java.util.HashSet<>();

        public Zone(String name, double x, double z, double depth, int bed, double cover) {
            this.name = name; this.x = x; this.z = z; this.depth = depth; this.bed = bed;
            this.cover = cover; this.baseCover = cover;
        }

        /** Plain sqrt: Math.hypot is exact to the last ulp and ten times slower, and it was half the step. */
        double dist(Zone o) { double dx = x - o.x, dz = z - o.z; return Math.sqrt(dx * dx + dz * dz); }
    }

    /** Feed on the bottom: how much is left (portions) and what it is made of (bait/diet key → share). */
    public static final class Feed {
        public double amount;
        public final Map<String, Double> keys;
        /** §shoal-bait: where it landed (block x/z), NaN when nobody said — what a feeding shoal gathers over. */
        public double x = Double.NaN, z = Double.NaN;
        public Feed(double amount, Map<String, Double> keys) { this.amount = amount; this.keys = keys; }
    }

    /** A shoal (count > 1) or one fish (trophy). count 0 on a trophy = an empty slot waiting to refill. */
    public static final class Agent {
        public final Species sp;
        public final boolean trophy;
        public int capacity;   // grows when a newly surveyed zone adds its fish to a shoal already here
        public int count;
        public double weightG;
        public int zone;
        public double hunger = 0.5;
        public double wary;
        public final Map<String, Double> fam = new HashMap<>();
        public double respawnAt;
        /** §alife-life: the fish themselves, when this water remembers them; null = a counted shoal. */
        public List<Life.Head> heads;
        /** A batch of fry: born when, from whom. They become heads at Life.JUVENILE_H. */
        public boolean fry;
        public double bornHour;
        /** §alife-spawn: when this counted shoal last spawned (a remembered fish keeps its own). */
        public double lastSpawnHour = -1e9;
        public Life.Head mother, father;
        /** §event-fish: put here by an organiser (/rffish spawn), so /rffish clear can take it out again. */
        public boolean event;

        public Agent(Species sp, int count, double weightG, int zone, boolean trophy) {
            this.sp = sp; this.count = count; this.capacity = count; this.weightG = weightG;
            this.zone = zone; this.trophy = trophy;
        }

        public boolean alive() { return count > 0; }

        /** A roster's count, room and weight are its heads'. */
        public void sync() {
            if (heads == null) return;
            count = heads.size();
            capacity = count;
            double w = 0;
            for (Life.Head h : heads) w += h.weightG;
            if (count > 0) weightG = w / count;
        }
        public double familiarity(Iterable<String> keys) {
            double f = 0;
            for (String k : keys) f = Math.max(f, fam.getOrDefault(k, 0.0));
            return f;
        }
    }

    // ---- what the angler does to the water ----

    /** Groundbait (or a scatter of boilies) goes into a zone. */
    public void feed(int zone, double portions, Map<String, Double> keys) {
        feed(zone, portions, keys, Double.NaN, Double.NaN);
    }

    /** The same, at a spot: the latest throw is where the feeding shoal gathers. */
    public void feed(int zone, double portions, Map<String, Double> keys, double x, double z) {
        touch();
        for (Feed f : zones.get(zone).feeds) {
            if (f.keys.equals(keys)) {
                f.amount += portions;
                if (!Double.isNaN(x)) { f.x = x; f.z = z; }
                return;
            }
        }
        Feed f = new Feed(portions, keys);
        f.x = x;
        f.z = z;
        zones.get(zone).feeds.add(f);
    }

    /** A cast on their heads, a splash, a man running along the bank. */
    public void disturb(int zone, double amount) {
        Zone z = zones.get(zone);
        z.fear = Math.min(1.0, z.fear + amount);
    }

    // ---- time ----

    /**
     * Bring the water up to {@code to}. Up to {@link #CATCH_UP_H} is lived step by step; beyond that the slow
     * memories (wariness, familiarity, feed, numbers) are decayed in closed form across the gap and only the
     * last day is lived, so a lake nobody visited for a month costs the same as one left for three days.
     */
    public void advance(double to, DoubleFunction<Species.Conditions> conditionsAt) {
        double gap = to - hour;
        // §alife-steps: whole steps only. Every look used to run one, however little time had passed, and the move
        // roll does not scale with the step — so the more players watched a water, the more its fish shuttled.
        if (gap < STEP_H) return;
        if (touched) changed = true;   // §alife-save: it lives on, so it is written again
        // offline catch-up in hours, online in quarter-hours — and never more than ~500 steps, whatever the
        // calendar: a long season_days made a returning pond cost hundreds of thousands in one tick
        double stepH = gap > 6 ? Math.max(1.0, gap / 500) : STEP_H;
        if (gap > catchUpH) {
            double skip = gap - 24.0;
            decay(skip);
            regrow(skip, conditionsAt.apply(hour + skip).season(), false);   // a long absence: births and deaths assumed even
            for (Agent a : agents) a.hunger = 0.5;
            hour += skip;
        }
        while (to - hour >= STEP_H - 1e-9) {
            double dt = Math.min(stepH, Math.floor((to - hour) / STEP_H + 1e-9) * STEP_H);
            step(dt, conditionsAt.apply(hour));
            hour += dt;
        }
    }

    void step(double dt, Species.Conditions c) {
        index();
        decay(dt);
        regrow(dt, c.season(), true);
        for (Zone z : zones) z.natural = Math.min(1.0, z.natural + NATURAL_REGROW * dt);
        for (Agent a : agents) {
            if (!a.alive()) continue;
            double act = a.sp.activity(c);
            a.hunger = Math.min(1.0, a.hunger + HUNGER_RATE * act * dt);
            eat(a, act, dt);
            move(a, act, c);
        }
        Life.step(this, dt, c, rng);
    }

    long newUid() { return rng.nextLong(); }

    private void index() {
        byZone = new ArrayList<>(zones.size());
        for (int i = 0; i < zones.size(); i++) byZone.add(new ArrayList<>());
        for (Agent a : agents) if (a.alive() && a.zone < zones.size()) byZone.get(a.zone).add(a);
        grazerKg = new double[zones.size()];
        for (Agent a : agents) {
            if (a.alive() && !a.sp.predator() && a.zone < zones.size()) grazerKg[a.zone] += a.count * a.weightG / 1000.0;
        }
    }

    /** The zones within MOVE_RANGE of each zone — the water does not move, so this is worked out once per survey. */
    private int[][] near = new int[0][];

    private int[] neighbours(int zi) {
        if (near.length != zones.size()) {
            near = new int[zones.size()][];
            for (int i = 0; i < zones.size(); i++) {
                List<Integer> n = new ArrayList<>();
                for (int j = 0; j < zones.size(); j++) if (j != i && zones.get(j).dist(zones.get(i)) <= MOVE_RANGE) n.add(j);
                near[i] = n.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        return near[zi];
    }

    /** Grazers' biomass per zone at the top of the step — asked once per candidate zone per agent. */
    private double[] grazerKg = new double[0];

    private List<Agent> in(int zi) {
        if (byZone.size() != zones.size()) index();
        return byZone.get(zi);
    }

    private void decay(double dt) {
        for (Zone z : zones) {
            z.fear *= half(dt, FEAR_HALF_H);
            for (Feed f : z.feeds) f.amount *= half(dt, FEED_HALF_H);
            z.feeds.removeIf(f -> f.amount < 0.05);
        }
        for (Agent a : agents) {
            a.wary *= half(dt, a.trophy ? WARY_HALF_TROPHY_H : WARY_HALF_H);
            a.fam.replaceAll((k, v) -> v * half(dt, FAM_HALF_H));
            a.fam.values().removeIf(v -> v < 0.01);
        }
    }

    /** Logistic regrowth of the shoals toward what the water holds, faster in spring; trophies refill slots. */
    /**
     * §alife-age: the wild water's deaths and its trickle of newcomers — the births are the spawn's (Life).
     * A counted shoal loses a lifespan's share of its fish a year to age, and more when it starves; a
     * remembered fish in wild water dies when it outlives its own span. A player's pond ages nobody.
     * A shoal down to its last tenth gets a stray from the next water now and then, so a fished-out
     * reach comes back, slowly, rather than never. {@code lived}: false across a long absence.
     */
    private void regrow(double dt, Season season, boolean lived) {
        for (Iterator<Agent> it = agents.iterator(); it.hasNext(); ) {
            Agent a = it.next();
            if (a.fry) continue;
            if (a.trophy) {
                if (a.count == 0 && hour >= a.respawnAt) { a.count = 1; a.wary = 0; a.fam.clear(); }
                else if (lived && !pond && a.count > 0 && rng.nextDouble() < dt / (a.sp.lifespanYears() * YEAR_H * 0.5)
                        + (a.hunger > 0.95 ? dt * STARVE_H : 0)) {   // a lone fish ages and starves like any
                    a.count = 0;   // the old one is gone; another grows into its place
                    a.respawnAt = hour + TROPHY_RESPAWN_H;
                }
                continue;
            }
            if (!lived || pond) continue;
            if (a.heads != null) {
                if (a.heads.removeIf(h -> hour - h.bornHour > a.sp.lifespanYears() * YEAR_H * (0.8 + 0.4 * unit(h.uid)))) {
                    a.sync();
                    touch();
                }
                continue;
            }
            int aged = Math.min(a.count, round(a.count * dt / (a.sp.lifespanYears() * YEAR_H)));
            a.count -= aged;
            note(a.sp.id() + ":age", aged);
            if (a.hunger > 0.95) {
                int starved = Math.min(a.count, round(a.count * dt * STARVE_H));
                a.count -= starved;
                note(a.sp.id() + ":starved", starved);
            }
            if (a.count < Math.max(2, 0.25 * a.capacity) && rng.nextDouble() < dt / STRAY_H) { a.count++; note(a.sp.id() + ":stray", 1); }
        }
    }

    /** A wild shoal starving flat out loses this share of its fish an hour. */
    private static final double STARVE_H = 0.0002;
    /** How often a stray finds its way into a reach its kind has all but left, hours. */
    private static final double STRAY_H = 240;

    private int round(double x) {
        int f = (int) Math.floor(x);
        return f + (rng.nextDouble() < x - f ? 1 : 0);
    }

    private static double unit(long uid) {
        long h = uid * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    private static double half(double dt, double halfH) { return Math.pow(0.5, dt / halfH); }

    // ---- behaviour ----

    /** What there is to eat here for this agent, 0..~2. Predators eat the shoals they can swallow. */
    double food(Agent a, Zone z, int zi) {
        if (a.sp.predator()) {
            double prey = 0;
            for (Agent o : in(zi)) if (a.sp.preysOn(o.sp)) prey += o.count;
            // §alife-years: a perch lives on worms and larvae more than on fish — a small predator takes a
            // grazer's full share of the zone's own food, a big one a third, so none simply starves for want
            // of something to swallow
            double share = a.sp.meanG() < 1000 ? 1.0 : 0.3;
            return Math.min(2.0, prey / 20.0 + share * z.natural * naturalFit(a.sp, z));
        }
        double f = z.natural * naturalFit(a.sp, z);
        for (Feed fd : z.feeds) {
            double menu = 0;
            for (Map.Entry<String, Double> e : fd.keys.entrySet()) menu += a.sp.eats(e.getKey()) * e.getValue();
            double fam = a.familiarity(fd.keys.keySet());
            f += Math.min(1.5, fd.amount / 4.0) * menu * (1.0 + fam);
        }
        return f;
    }

    /** Bottom feeders like soft bottoms and weed; mid-water fish take what drifts past anywhere. */
    private static double naturalFit(Species sp, Zone z) {
        double fit = "bottom".equals(sp.depthPref()) ? (z.bed == 4 ? 0.6 : 0.35) : 0.45;
        return fit + 0.2 * z.cover;
    }

    private void eat(Agent a, double act, double dt) {
        Zone z = zones.get(a.zone);
        double avail = food(a, z, a.zone);
        double eaten = Math.min(a.hunger, EAT_RATE * act * Math.min(1.0, avail) * dt);
        if (eaten <= 0) return;
        a.hunger -= eaten;
        // hunger 1 → 0 is a day's ration, about 2 % of body weight
        double biomassKg = eaten * a.count * a.weightG / 1000.0 * MEAL_SHARE;
        if (a.sp.predator()) {
            // The shoal it fed on loses a fish now and then — which is what sends the roach to the weed.
            // §alife-years: it takes what it meets most of — prey chosen by the square of its numbers, so a
            // pike switches to the roach that are everywhere and the last few perch get a refuge
            Agent pickPrey = null;
            double w2 = 0;
            for (Agent o : in(a.zone)) {
                if (!o.alive() || o.trophy || o.fry || !a.sp.preysOn(o.sp)) continue;
                double w = (double) o.count * o.count;
                w2 += w;
                if (rng.nextDouble() * w2 < w) pickPrey = o;
            }
            for (Agent o : pickPrey == null ? List.<Agent>of() : List.of(pickPrey)) {
                if (rng.nextDouble() >= biomassKg / (o.weightG / 1000.0) / Math.max(1, o.count)) continue;
                if (o.heads == null) { o.count--; note(o.sp.id() + ":eaten", 1); break; }
                Life.Head smallest = null;
                for (Life.Head h : o.heads) if (h.weightG * 5 <= a.weightG && (smallest == null || h.weightG < smallest.weightG)) smallest = h;
                if (smallest != null) { o.heads.remove(smallest); o.sync(); touch(); }
                break;
            }
            return;
        }
        // Feed is eaten before the natural food, and eating it is how a fish learns what it tastes like.
        double take = biomassKg;   // one portion of feed is a kilogram
        for (Feed f : z.feeds) {
            double menu = 0;
            for (Map.Entry<String, Double> e : f.keys.entrySet()) menu += a.sp.eats(e.getKey()) * e.getValue();
            if (menu <= 0) continue;
            double bite = Math.min(f.amount, take);
            f.amount -= bite;
            take -= bite;
            // §boilies: it learns the whole bed it ate — the foods it knows and the flavour they came in
            for (Map.Entry<String, Double> e : f.keys.entrySet()) {
                double share = e.getValue() > 0 ? e.getValue() : 1.0;
                if (e.getValue() > 0 && a.sp.eats(e.getKey()) <= 0) continue;
                a.fam.merge(e.getKey(), eaten * 0.25 * share, (x, y) -> Math.min(1.0, x + y));
            }
        }
        z.natural = Math.max(0.0, z.natural - take * NATURAL_PER_KG * 1024 / Math.max(1, z.volume * z.richness));
    }

    /** How good zone {@code zi} is for this agent right now — feeding, resting and staying alive, weighed. */
    double utility(Agent a, int zi, double act, Species.Conditions c) {
        Zone z = zones.get(zi);
        double feedPull = drive(a, zi) * Math.min(1.5, act);
        double share = a.sp.predator() ? 1.0 : 1.0 / (1.0 + grazersKg(zi, a) / CROWD_KG);
        double u = feedPull * food(a, z, zi) * share * 2.0 + (1.0 - a.hunger) * (0.3 + 0.5 * z.cover);
        u += depthFit(a.sp, z, c);
        // §alife-spawn: in its window a shoal that can spawn goes to the spawning grounds — warm, shallow
        // water with weed or a soft bed — and that is where the fish are, visibly, for those days
        if (!a.fry && c.spawning().test(a.sp.id()) && spawningGround(z)) u += SPAWN_PULL;
        u -= z.fear * 2.0;
        if (!a.sp.predator()) {
            for (Agent o : in(zi)) if (o.sp.preysOn(a.sp)) u -= 0.4 * (1.0 - 0.6 * z.cover);
        }
        return u - 0.01 * z.dist(zones.get(a.zone));
    }

    /**
     * How keen on food this agent is in zone {@code zi}: its hunger, topped up by feed it KNOWS lying there.
     * A shoal on a bed of familiar feed keeps picking at it past hunger — competitive feeding, part of why
     * prebaiting works. Only part: a belly filled with free feed still bites less, so overfeeding costs.
     */
    double drive(Agent a, int zi) {
        double excite = 0;
        for (Feed f : zones.get(zi).feeds) {
            double menu = 0;
            for (Map.Entry<String, Double> e : f.keys.entrySet()) menu += a.sp.eats(e.getKey()) * e.getValue();
            excite += Math.min(1.0, f.amount / 4.0) * Math.min(1.0, menu) * a.familiarity(f.keys.keySet());
        }
        return a.hunger + (1.0 - a.hunger) * Math.min(0.4, 0.4 * excite);
    }

    private static final double SPAWN_PULL = 0.9;

    /** Where fish spawn: shallow water with weed or a soft bottom. */
    public static boolean spawningGround(Zone z) {
        return z.depth <= 3.0 && (z.cover >= 0.3 || z.bed == 4);
    }

    /** §shoal-bait: the spot of the freshest feed in a zone, or null. */
    public double[] baitSpot(int zi) {
        Feed best = null;
        for (Feed f : zones.get(zi).feeds) if (!Double.isNaN(f.x) && (best == null || f.amount > best.amount)) best = f;
        return best == null ? null : new double[]{best.x, best.z};
    }

    /** Biomass of the other non-predators in a zone — the fish this one would be sharing the food with. */
    private double grazersKg(int zi, Agent self) {
        if (grazerKg.length != zones.size()) index();
        double own = self.zone == zi && !self.sp.predator() ? self.count * self.weightG / 1000.0 : 0;
        return Math.max(0, grazerKg[zi] - own);
    }

    /**
     * The daily rhythm: shallows at dawn and dusk and at night for the big bottom feeders, the deep and the
     * shade in the heat of a summer day and all winter. Numbers are a nudge next to food, not a leash.
     */
    private static double depthFit(Species sp, Zone z, Species.Conditions c) {
        boolean shallow = z.depth <= 2.5, deep = z.depth >= 5;
        TimeOfDay t = c.time();
        double u = 0;
        if (c.season() == Season.WINTER) u += deep ? 0.6 : shallow ? -0.4 : 0;
        boolean heat = c.waterTemp() > 0.7 && t == TimeOfDay.DAY && c.weather() == com.riverfishing.engine.Weather.CLEAR;
        if (heat) u += deep ? 0.5 : shallow ? -0.5 : 0;
        if (t == TimeOfDay.DAWN || t == TimeOfDay.DUSK) u += shallow ? 0.3 : 0;
        if (t == TimeOfDay.NIGHT && sp.meanG() > 1500) u += shallow ? 0.25 : 0;
        if ("bottom".equals(sp.depthPref()) && z.depth < 1.5) u -= 0.2;
        return u;
    }

    private void move(Agent a, double act, Species.Conditions c) {
        int best = a.zone;
        double here = utility(a, a.zone, act, c), bestU = here;
        for (int i : neighbours(a.zone)) {
            Zone to = zones.get(i);
            if (!to.lives.isEmpty() && !to.lives.contains(a.sp.id())) continue;
            double u = utility(a, i, act, c) + rng.nextGaussian() * 0.08;
            if (u > bestU) { bestU = u; best = i; }
        }
        if (bestU > here + 0.1) a.zone = best;   // inertia: a shoal does not flicker between two equal zones
    }

    // ---- the cast ----

    /**
     * A bait in the water. {@code appeal} is how much a species wants the whole presentation (bait, feed,
     * line, hook — the old match score, 0..~1.3); {@code keys} are what it smells of, for familiarity;
     * {@code suspicion} is what looks wrong about it (a thick line, a heavy lead), 0..1.
     */
    public record Offer(int zone, ToDoubleFunction<Species> appeal, List<String> keys, double suspicion, double reach) {
        public Offer(int zone, ToDoubleFunction<Species> appeal, List<String> keys, double suspicion) {
            this(zone, appeal, keys, suspicion, SCENT_BLOCKS);
        }
    }

    /**
     * §alife-strike: a predator hits a lure or a live bait out of reflex and territory as much as hunger —
     * a pike that fed an hour ago still slams a spoon through its patch. Its keenness never drops below this.
     */
    private static final double STRIKE_FLOOR = 0.45;

    /** Keenness to take a bait here: its drive, and for a predator never less than the reflex floor. */
    private double keenness(Agent a, int zone) {
        double d = drive(a, zone);
        return a.sp.predator() ? Math.max(d, STRIKE_FLOOR) : d;
    }

    /** Who is interested, how much (bites per game hour each), and the total. */
    public record Interest(List<Agent> agents, double[] rates, double total) {
        public double meanWaitH() { return total > 0 ? 1.0 / total : Double.POSITIVE_INFINITY; }

        public Agent pick(Random r) {
            double roll = r.nextDouble() * total;
            for (int i = 0; i < rates.length; i++) if ((roll -= rates[i]) <= 0) return agents.get(i);
            return agents.isEmpty() ? null : agents.get(agents.size() - 1);
        }
    }

    public Interest offer(Offer o, Species.Conditions c) {
        List<Agent> who = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        double total = 0;
        Zone at = zones.get(o.zone());
        for (Agent a : agents) {
            if (!a.alive() || a.fry) continue;
            double near = Math.exp(-zones.get(a.zone).dist(at) / o.reach());
            if (near < 0.02) continue;
            double appeal = o.appeal().applyAsDouble(a.sp);
            if (appeal <= 0) continue;
            // a big fish is fussier about what looks wrong than a hungry little one
            double sens = Math.min(1.0, 0.4 + a.weightG / 5000.0);
            double r = BITE_K * Math.sqrt(a.count) * keenness(a, o.zone()) * a.sp.activity(c) * appeal * near
                    * (1.0 + a.familiarity(o.keys()))   // a fish used to it takes it twice as readily
                    * (1.0 - a.wary)
                    * Math.max(0.0, 1.0 - o.suspicion() * sens)
                    * (1.0 - at.fear);
            if (r > 1e-6) { who.add(a); rates.add(r); total += r; }
        }
        double[] arr = rates.stream().mapToDouble(Double::doubleValue).toArray();
        return new Interest(who, arr, total);
    }

    /**
     * §alife-why: why the water is not biting on this bait, read off the fish themselves. {@code reason}:
     * {@code away_deep / away_weed / away_shallow / away_open} — nobody near the bait, and the nearest fish are
     * over that kind of water; {@code empty} — no fish in this water at all; {@code rig} — fish are near and
     * refuse the rig itself ({@code species} says whose rules to ask); otherwise the factor holding back the
     * keenest fish near the bait: {@code full}, {@code season}, {@code time}, {@code weather}, {@code wary},
     * {@code spooked}, or {@code other} when nothing stands out and it is just a slow water.
     */
    public record Diagnosis(String reason, String species) {}

    /** Nearness at which a fish counts as being at the bait (~16 blocks off at the scent's reach). */
    private static final double AT_BAIT = 0.25;

    public Diagnosis diagnose(Offer o, Species.Conditions c) {
        Zone at = zones.get(o.zone());
        Agent best = null, nearest = null, refused = null;
        double bestPot = 0, nearestD = Double.MAX_VALUE;
        for (Agent a : agents) {
            if (!a.alive() || a.fry) continue;
            double d = zones.get(a.zone).dist(at);
            if (d < nearestD) { nearestD = d; nearest = a; }
            double near = Math.exp(-d / o.reach());
            if (near < 0.02) continue;
            if (o.appeal().applyAsDouble(a.sp) <= 0) {
                if (refused == null || a.count > refused.count) refused = a;
                continue;
            }
            double pot = Math.sqrt(a.count) * near;
            if (pot > bestPot) { bestPot = pot; best = a; }
        }
        if (best == null && refused != null) return new Diagnosis("rig", refused.sp.id());
        // a fish that only smells the bait from the next bay is not "at the bait": say where the fish are instead
        Agent far = best != null ? best : nearest;
        if (best == null || Math.exp(-zones.get(best.zone).dist(at) / o.reach()) < AT_BAIT) {
            if (far == null) return new Diagnosis("empty", null);
            Zone z = zones.get(far.zone);
            String where = z.depth >= 5 ? "away_deep" : z.cover >= 0.5 ? "away_weed" : z.depth <= 2.5 ? "away_shallow" : "away_open";
            return new Diagnosis(where, far.sp.id());
        }
        // each factor of the bite rate, scaled so that 1 is an ordinary day; the smallest is the answer
        Species sp = best.sp;
        double season = Math.pow(sp.season().getOrDefault(c.season().jsonKey(), 1.0), 1.5);
        double time = Math.pow(sp.time().getOrDefault(c.time().jsonKey(), 1.0), 1.4);
        double weather = sp.weather().getOrDefault(c.weather().jsonKey(), 1.0) * c.pressureFactor();
        String[] names = {"full", "season", "time", "weather", "wary", "spooked"};
        double[] f = {keenness(best, o.zone()) / 0.5, season, time, weather, 1 - best.wary, 1 - at.fear};
        int worst = 0;
        for (int i = 1; i < f.length; i++) if (f[i] < f[worst]) worst = i;
        return new Diagnosis(f[worst] < 0.6 ? names[worst] : "other", sp.id());
    }

    /** The fish is landed and kept: one fewer in the water, and the shoal saw it go. Returns its weight, g. */
    public double take(Agent a) {
        touch();
        if (a.heads != null) {
            Life.Head h = a.heads.get(rng.nextInt(a.heads.size()));
            takeHead(a, h);
            return h.weightG;
        }
        double w = a.trophy ? a.weightG
                : Math.min(a.sp.maxG(), a.weightG * Math.exp(rng.nextGaussian() * 0.25));
        a.count--;
        note(a.sp.id() + ":caught", 1);
        a.hunger = Math.max(0, a.hunger - 0.3);
        if (a.trophy) a.respawnAt = hour + TROPHY_RESPAWN_H * (0.7 + 0.6 * rng.nextDouble());
        else a.wary = Math.min(0.9, a.wary + 0.1);
        return w;
    }

    /** One remembered fish leaves the water — the one that was caught, netted or eaten, and no other. */
    public void takeHead(Agent a, Life.Head h) {
        touch();
        a.heads.remove(h);
        a.sync();
        a.wary = Math.min(0.9, a.wary + 0.1);
    }

    /** A remembered fish leaves the water, whichever shoal it swims with now; false if it is no longer here. */
    public boolean remove(Life.Head h) {
        for (Agent a : agents) {
            if (a.heads != null && a.heads.contains(h)) { takeHead(a, h); return true; }
        }
        return false;
    }

    /** §shoal-live: is this shoal feeding right now — hungry, and on a zone with food on the bottom? What bubbles and rings show. */
    public boolean feeding(Agent a) {
        if (!a.alive() || a.sp.predator()) return false;
        Zone z = zones.get(a.zone);
        return a.hunger > 0.3 && (z.natural > 0.3 || !z.feeds.isEmpty());
    }

    /** §shoal-live: is this predator hunting — hungry, with fish it can swallow in its zone? What a strike at the surface shows. */
    public boolean hunting(Agent a) {
        if (!a.alive() || !a.sp.predator() || a.hunger < 0.35) return false;
        for (Agent o : agents) if (o.zone == a.zone && o.alive() && a.sp.preysOn(o.sp)) return true;
        return false;
    }

    /** A remembered fish, picked at random from its shoal. */
    public Life.Head pickHead(Agent a) {
        return a.heads == null || a.heads.isEmpty() ? null : a.heads.get(rng.nextInt(a.heads.size()));
    }

    /** Hooked and lost, or landed and let go — either way it knows now. */
    public void spooked(Agent a, boolean released) {
        touch();
        a.wary = Math.min(0.95, a.wary + (released ? 0.6 : 0.4));
    }
}
