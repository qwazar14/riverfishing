package com.riverfishing.alife;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * §alife-life: births, growth and room — the part of the living water that makes MORE fish, and so the part
 * that has to be honest. A water that holds {@link Head}s (a player's pond, a fish someone put back) holds
 * exactly the fish that went in plus the ones really born there: no head count runs ahead of the records,
 * because there is no head count — a shoal of heads is as big as its list.
 *
 * <p>The year: in its spawning window a mature female with a mature male of her kind in the same water lays
 * {@link Roe}; the roe is eaten and dies off for a few days and hatches into a batch of fry (an {@link Lake.Agent}
 * with {@code fry = true}); the fry are eaten, starve in a crowded water, and after {@link #JUVENILE_H} the
 * survivors become heads — as many as the water has room for. Heads grow when they are fed and the water is not
 * full. Room is the water's volume: {@link #HEADS_PER_BLOCK} fish and {@link #KG_PER_BLOCK} kilograms a block,
 * more among weed and snags. Room never kills a grown fish (a player owns it); it stops growth and starves the
 * fry — which is where a real pond's ceiling is too.
 *
 * <p>Pure Java like the rest of the core. What a fish IS (its genes, its card) the game keeps in
 * {@link Head#tag}; how genes cross and how big a clutch is, the game answers through {@link Nursery}.
 */
public final class Life {
    private Life() {}

    public static final double HEADS_PER_BLOCK = 0.25;
    public static final double KG_PER_BLOCK = 0.4;
    /** Weed and snags: room and shelter. A zone of full cover holds half again as much and hides the fry. */
    private static final double COVER_ROOM = 0.5;
    public static final double HATCH_H = 72;
    public static final double JUVENILE_H = 24 * 16;
    /** A female spawns once a window: this long since her last clutch before the next. */
    private static final double SPAWN_GAP_H = 24 * 50;
    private static final double ROE_LOSS_H = 0.004, ROE_EATEN_H = 0.006;
    private static final double FRY_LOSS_H = 0.0012, FRY_EATEN_H = 0.003, FRY_CROWD_H = 0.02;
    /** Fry in a zone with nothing to eat: most of a batch is gone in a fortnight (§alife-years). */
    private static final double FRY_STARVE_H = 0.003;
    /** In a player's pond the fry live on what they are given: unfed, a batch is mostly gone in a fortnight. */
    private static final double POND_FRY_STARVE_H = 0.01;
    /** Predator kilos in a zone at which fry are eaten at the full rate — a pike eats what it can, not per head. */
    private static final double FRY_PREDATOR_KG = 5.0;
    /** A shoal hungrier than this is in no condition to spawn — a starved pond does not breed (§alife-years). */
    private static final double SPAWN_HUNGER = 0.75;
    /**
     * von Bertalanffy's K, a day: length closes this share of the gap to the fish's own ceiling each fed
     * day, weight is length cubed. A well-fed carp matures in about a game year and a quarter and is an
     * ordinary fish in two — a fry to a fish is years, as it is.
     */
    private static final double GROWTH_PER_DAY = 0.006;
    private static final double FRY_G = 0.5;
    /** A fish is mature at this share of its species' ordinary weight. */
    public static final double MATURE_SHARE = 0.4;
    /**
     * §wild-heads: the most fish a WILD water remembers one by one. It hatches them with no card (its nursery is
     * the default), so past this a released pair's young grow up as ordinary fish of the water — the same fish,
     * counted — instead of a record each that was saved and stepped for years: six carp pairs left in a river
     * bred some fifty records a year, bounded only by a pond-sized room of thousands. 256 is a released stock's
     * first four or five years exactly as before — the fun of it, young growing into big fish one by one. A pond
     * is not capped.
     */
    public static final int WILD_HEADS = 256;

    /** One fish of a water that remembers its fish. */
    public static final class Head {
        public final long uid;
        /** 0 female, 1 male. */
        public int sex;
        public double weightG;
        public double bornHour;
        public boolean trophy, legend;
        /** Put back by a player — a legend caught again earns no achievement. */
        public boolean returned;
        public double lastSpawnHour = -1e9;
        /** The game's record of this fish (card, genes, name, morph). The core never reads it. */
        public Object tag;

        public Head(long uid, int sex, double weightG, double bornHour) {
            this.uid = uid; this.sex = sex; this.weightG = weightG; this.bornHour = bornHour;
        }

        /** Where this fish's growth levels off: 45..100 % of the species' biggest, fixed by the fish itself. */
        double ceilingG(Species sp) {
            long h = uid * 0x9E3779B97F4A7C15L;
            h ^= h >>> 31;
            double u = (h >>> 11) / (double) (1L << 53);
            return sp.maxG() * (0.45 + 0.55 * u);
        }

        public boolean mature(Species sp) { return weightG >= MATURE_SHARE * sp.meanG(); }
    }

    /** Eggs on the bottom. Public so the renderer can draw them one day: zone, species, how many, when. */
    public static final class Roe {
        public final Species sp;
        public final int zone;
        public double eggs;
        public final double laidHour;
        public final Head mother, father;

        public Roe(Species sp, int zone, double eggs, double laidHour, Head mother, Head father) {
            this.sp = sp; this.zone = zone; this.eggs = eggs; this.laidHour = laidHour;
            this.mother = mother; this.father = father;
        }

        public double hatchHour() { return laidHour + HATCH_H; }
    }

    /** What only the game knows: genes. The defaults make a plausible fish when nobody is asked. */
    public interface Nursery {
        /** Eggs in one clutch of this mother. */
        default int clutch(Species sp, Head mother) {
            return (int) Math.round(10 + 15 * Math.min(2.0, mother.weightG / Math.max(1, sp.meanG())));
        }

        /** A fry of these parents grown into a head — or null if the egg never developed (a lethal genotype). */
        default Head hatch(Species sp, Head mother, Head father, long uid, double hour, Random r) {
            return new Head(uid, r.nextInt(2), FRY_G, hour);
        }

        /** Can this species spawn without a male of its own kind (gynogenesis)? */
        default boolean selfing(Species sp) { return false; }

        Nursery DEFAULT = new Nursery() {};
    }

    // ---- room ----

    public static double headRoom(Lake lake) {
        double r = 0;
        for (Lake.Zone z : lake.zones) r += z.volume * HEADS_PER_BLOCK * (1 + COVER_ROOM * z.cover);
        return r;
    }

    public static double kgRoom(Lake lake) {
        double r = 0;
        for (Lake.Zone z : lake.zones) r += z.volume * KG_PER_BLOCK * z.richness * (1 + COVER_ROOM * z.cover);
        return r;
    }

    public static int heads(Lake lake) {
        int n = 0;
        for (Lake.Agent a : lake.agents) if (a.heads != null) n += a.heads.size();
        return n;
    }

    public static double kg(Lake lake) {
        double kg = 0;
        for (Lake.Agent a : lake.agents) {
            if (a.heads != null) for (Head h : a.heads) kg += h.weightG / 1000.0;
            else kg += a.count * a.weightG / 1000.0;
        }
        return kg;
    }

    // ---- a step ----

    static void step(Lake lake, double dt, Species.Conditions c, Random rng) {
        spawn(lake, c, rng);   // wild shoals spawn too
        boolean anyLife = !lake.roe.isEmpty();
        for (Lake.Agent a : lake.agents) anyLife |= a.heads != null || a.fry;
        if (!anyLife) return;   // nothing remembered, nothing hatching: nothing more to do
        boolean full = kg(lake) >= kgRoom(lake);
        grow(lake, dt, full);
        roe(lake, dt, rng);
        fry(lake, dt, rng);
    }

    /** Fed fish in a water with room put on weight toward their own ceiling; a full or hungry water, none. */
    private static void grow(Lake lake, double dt, boolean full) {
        if (full) return;
        for (Lake.Agent a : lake.agents) {
            double k = GROWTH_PER_DAY * Math.max(0.0, 1.0 - a.hunger / 0.6) * dt / 24.0;
            if (k <= 0) continue;
            if (a.fry) { a.weightG = grown(a.weightG, a.sp.maxG() * 0.7, k); continue; }
            if (a.heads == null) continue;
            for (Head h : a.heads) h.weightG = grown(h.weightG, h.ceilingG(a.sp), k);
        }
    }

    /** One fed stretch of von Bertalanffy growth: length toward the ceiling's, weight its cube. A fish over its ceiling keeps its weight. */
    private static double grown(double w, double ceiling, double k) {
        if (w >= ceiling) return w;
        double l = Math.cbrt(w / ceiling);
        l += k * (1.0 - l);
        return ceiling * l * l * l;
    }

    private static void spawn(Lake lake, Species.Conditions c, Random rng) {
        for (int i = 0; i < lake.agents.size(); i++) {
            Lake.Agent a = lake.agents.get(i);
            // §alife-spawn: a wild shoal spawns as a shoal — on the spawning grounds, once a window — and
            // its fry join it in time. Not a touch: a wild water nobody fished is still grown from the seed.
            if (a.hunger > SPAWN_HUNGER) continue;
            if (a.heads == null && !a.fry && !a.trophy && a.count >= 2 && lake.hour - a.lastSpawnHour >= SPAWN_GAP_H
                    && Lake.spawningGround(lake.zones.get(a.zone)) && c.spawning().test(a.sp.id())) {
                a.lastSpawnHour = lake.hour;
                lake.roe.add(new Roe(a.sp, a.zone, Math.min(600, a.count * 10.0), lake.hour, null, null));
                continue;
            }
            if (a.heads == null || a.heads.isEmpty() || !c.spawning().test(a.sp.id())) continue;
            for (Head mother : a.heads) {
                if (mother.sex != 0 || !mother.mature(a.sp) || lake.hour - mother.lastSpawnHour < SPAWN_GAP_H) continue;
                Head father = lake.nursery.selfing(a.sp) ? mother : male(lake, a.sp, rng);
                if (father == null) continue;
                mother.lastSpawnHour = lake.hour;
                lake.roe.add(new Roe(a.sp, a.zone, lake.nursery.clutch(a.sp, mother), lake.hour, mother, father));
                lake.touch();
            }
        }
    }

    private static Head male(Lake lake, Species sp, Random rng) {
        List<Head> males = new ArrayList<>();
        for (Lake.Agent a : lake.agents) {
            if (a.heads == null || !a.sp.id().equals(sp.id())) continue;
            for (Head h : a.heads) if (h.sex == 1 && h.mature(sp)) males.add(h);
        }
        return males.isEmpty() ? null : males.get(rng.nextInt(males.size()));
    }

    /** Eggs die off and are eaten by whatever grazes the zone, less so among weed; then they hatch. */
    private static void roe(Lake lake, double dt, Random rng) {
        for (Iterator<Roe> it = lake.roe.iterator(); it.hasNext(); ) {
            Roe r = it.next();
            Lake.Zone z = lake.zones.get(r.zone);
            double grazers = 0;
            for (Lake.Agent a : lake.agents) if (a.zone == r.zone && a.alive() && !a.fry) grazers += a.count;
            double loss = ROE_LOSS_H + ROE_EATEN_H * Math.min(1.0, grazers / 20.0) * (1 - z.cover);
            r.eggs *= Math.pow(1 - loss, dt);
            if (lake.hour >= r.hatchHour()) {
                it.remove();
                int n = round(r.eggs, rng);
                if (n <= 0) continue;
                Lake.Agent fry = new Lake.Agent(r.sp, n, FRY_G, r.zone, false);
                fry.fry = true;
                fry.bornHour = lake.hour;
                fry.mother = r.mother;
                fry.father = r.father;
                lake.agents.add(fry);
            }
        }
    }

    /**
     * Fry die off, are eaten by every predator in the zone (weed hides them), and in a water already at its
     * head room they starve; the ones left at {@link #JUVENILE_H} become heads, no more than there is room for.
     */
    private static void fry(Lake lake, double dt, Random rng) {
        double room = headRoom(lake);
        int heads = heads(lake);
        List<Lake.Agent> grown = new ArrayList<>();
        for (Lake.Agent a : lake.agents) {
            if (!a.fry || !a.alive()) continue;
            Lake.Zone z = lake.zones.get(a.zone);
            double predatorKg = 0;
            for (Lake.Agent o : lake.agents) {
                if (o.zone == a.zone && o.alive() && o.sp.predator() && !o.fry) predatorKg += o.count * o.weightG / 1000.0;
            }
            double food = z.feeds.isEmpty() ? z.natural : 1.0;
            double loss = FRY_LOSS_H + FRY_EATEN_H * Math.min(1.0, predatorKg / FRY_PREDATOR_KG) * (1 - z.cover)
                    + (heads >= room ? FRY_CROWD_H : 0)
                    + (lake.pond ? POND_FRY_STARVE_H : FRY_STARVE_H) * Math.max(0.0, 1.0 - food);
            a.count = round(a.count * Math.pow(1 - Math.min(0.5, loss), dt), rng);
            if (lake.hour - a.bornHour >= JUVENILE_H) grown.add(a);
        }
        for (Lake.Agent a : grown) {
            lake.agents.remove(a);
            if (a.mother == null) { joinShoal(lake, a); continue; }   // a wild batch grows into its shoal
            int free = Math.max(0, (int) Math.floor(room) - heads);
            int n = Math.min(a.count, lake.pond ? free : Math.min(free, Math.max(0, WILD_HEADS - heads)));   // §wild-heads
            for (int i = 0; i < n; i++) {
                Head h = lake.nursery.hatch(a.sp, a.mother, a.father, lake.newUid(), a.bornHour, rng);
                if (h == null) continue;
                h.weightG = Math.max(h.weightG, a.weightG);
                join(lake, a.sp, h, a.zone);
                heads++;
            }
            // §wild-heads: the ones the room had space for and the cap did not — ordinary fish of the water
            if (!lake.pond && Math.min(a.count, free) > n) {
                a.count = Math.min(a.count, free) - n;
                joinShoal(lake, a);
            }
        }
        lake.agents.removeIf(a -> a.fry && !a.alive());
    }

    /** §alife-spawn: a wild batch's survivors swell the nearest shoal of their kind, never past half again its size. */
    private static void joinShoal(Lake lake, Lake.Agent batch) {
        Lake.Agent best = null;
        double bestD = Double.MAX_VALUE;
        Lake.Zone at = lake.zones.get(batch.zone);
        for (Lake.Agent a : lake.agents) {
            if (a.heads != null || a.fry || a.trophy || !a.sp.id().equals(batch.sp.id())) continue;
            double d = lake.zones.get(a.zone).dist(at);
            if (d < bestD) { bestD = d; best = a; }
        }
        if (best == null) {
            Lake.Agent shoal = new Lake.Agent(batch.sp, batch.count, batch.sp.meanG() * 0.3, batch.zone, false);
            lake.agents.add(shoal);
            return;
        }
        // what holds a wild shoal now is food, predators and age; the ceiling is only a guard against a runaway
        int was = best.count;
        best.count = Math.min(best.count + batch.count, (int) Math.ceil(best.capacity * 3.0));
        lake.note(batch.sp.id() + ":born", best.count - was);
    }

    /** A head joins the shoal of its kind that keeps a roster (the nearest), or starts one where it is. */
    public static Lake.Agent join(Lake lake, Species sp, Head h, int zone) {
        Lake.Agent best = null;
        double bestD = Double.MAX_VALUE;
        Lake.Zone at = lake.zones.get(zone);
        for (Lake.Agent a : lake.agents) {
            if (a.heads == null || a.fry || !a.sp.id().equals(sp.id())) continue;
            double d = lake.zones.get(a.zone).dist(at);
            if (d < bestD) { bestD = d; best = a; }
        }
        if (best == null) {
            best = new Lake.Agent(sp, 0, sp.meanG(), zone, false);
            best.heads = new ArrayList<>();
            lake.agents.add(best);
        }
        best.heads.add(h);
        best.sync();
        lake.touch();
        return best;
    }

    private static int round(double x, Random rng) {
        int f = (int) Math.floor(x);
        return f + (rng.nextDouble() < x - f ? 1 : 0);
    }
}
