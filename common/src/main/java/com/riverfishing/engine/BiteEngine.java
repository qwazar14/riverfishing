package com.riverfishing.engine;

import com.riverfishing.fish.FishProfile;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bite model (§1). Turns a {@link BiteContext} into an expected time-to-bite and,
 * when a bite fires, picks which species took the bait — weighted by how well the whole
 * setup matches each fish under the current conditions.
 */
public final class BiteEngine {
    /** Time-to-bite at "ideal" attractiveness, in ticks (§1.4, T_min ≈ 8 s). */
    public static final double T_MIN_TICKS = 160.0;
    private static final double GRADIENT_K = 0.25;        // §1.1
    private static final double BAIT_HARD_FILTER = 0.15;  // §1.5
    private static final double HOOK_GATE = 0.34;         // below this, the hook is the wrong size band (#6)
    private static final double SWARM_KNEE = 1.5;         // §swarm-cap: W_total below this is untouched
    private static final double SWARM_DAMP = 0.3;         // …above it, only 30% of the excess counts toward speed
    // §deep-conditions: amplify the season / time-of-day / biome swings the profiles already describe, so
    // WHEN and WHERE you fish is strongly felt (a >1 factor grows, a <1 factor shrinks). Kept moderate so
    // off-peak water still has the ungated commons biting.
    private static final double SEASON_POW = 1.5;
    private static final double TIME_POW = 1.4;
    private static final double BIOME_POW = 1.3;

    private BiteEngine() {}

    /** Gradient sub-score: 1.0 at the ideal, falling off by tolerance "steps" (§1.1). */
    public static double gradient(double actual, double ideal, double tolerance) {
        if (tolerance <= 0) return actual == ideal ? 1.0 : 0.0;
        return Math.max(0.0, 1.0 - GRADIENT_K * Math.abs(actual - ideal) / tolerance);
    }

    // ---- Match coefficient M (§1.1) ----

    /** Best bait score among everything loaded on the rig (Module 4): rewards loading several baits. */
    public static double baitScore(FishProfile p, BiteContext c) {
        double best = 0.0;
        for (String bait : c.baits) {
            best = Math.max(best, p.baitScore(bait));
        }
        return best;
    }

    /** Best-fitting hook among those loaded. Lure rigs carry no separate hook — the lure's treble counts. */
    private static double hookScore(FishProfile p, BiteContext c) {
        if (c.hookSizes.isEmpty()) {
            // A predator lure's treble and a winter mormyshka carry their own hook — no separate hook slot.
            return (c.rig == com.riverfishing.component.RigType.PREDATOR
                    || c.rig == com.riverfishing.component.RigType.WINTER) ? 0.85 : 0.0;
        }
        double best = 0.0;
        for (int size : c.hookSizes) {
            best = Math.max(best, gradient(size, p.hookIdeal, p.hookTolerance));
        }
        return best;
    }

    /**
     * Why this species will not take right now, or null if it will.
     *
     * <p>Exactly the gates {@link #speciesWeight} enforces, asked one at a time and in the same order.
     * Public and living HERE rather than in the message code on purpose: the hint a player reads and the
     * rule that actually stopped them have to come from one place, or the mod ends up teaching something
     * that is no longer true.
     *
     * @return {@code absent} the fish does not live here or is not feeding, {@code no_bait} nothing is on
     *         the hook at all, {@code bait} what is on the hook is not something it eats, {@code hook} the
     *         hook is the wrong size band for its mouth, {@code no_hook} there is no hook on the rig at
     *         all, or null
     */
    public static String blockReason(FishProfile p, BiteContext c) {
        if (environmentScore(p, c) <= 0) return "absent";
        if (baitScore(p, c) <= 0) return c.baits.isEmpty() ? "no_bait" : "bait";
        if (hookScore(p, c) < HOOK_GATE) return hookScore(p, c) <= 0 && c.hookSizes.isEmpty() ? "no_hook" : "hook";
        return null;
    }

    public static double matchScore(FishProfile p, BiteContext c) {
        double sBait = baitScore(p, c);
        double sGround = groundbaitScore(p, c);
        double sRig = c.rig != null && p.idealRigs.contains(c.rig.jsonKey()) ? 1.0 : 0.15;
        double sRod = p.idealRods.contains(c.rod.jsonKey()) ? 1.0 : 0.35;
        double sLine = lineScore(p, c);
        double sHook = hookScore(p, c);
        double sReel = reelScore(p, c);

        return 0.30 * sBait
                + 0.15 * sGround
                + 0.13 * sRig
                + 0.12 * sRod
                + 0.12 * sLine
                + 0.10 * sHook
                + 0.08 * sReel;
    }

    /**
     * §groundbait-one-jar (0.8.0): four questions about the bed of feed, and not one of them is "which
     * of the four jars is it".
     *
     * <ul>
     *   <li><b>the menu</b> — is what is in the bowl something this fish eats? Asked of the species' own
     *       bait list, because that list already IS its diet. Chop worm into the feed and the fish that
     *       take worm come; a swim of sweetcorn says nothing to an eel.</li>
     *   <li><b>fraction against size</b> — a cloud of dust calls up bleak and roach; whole grain on the
     *       bottom is what a carp is looking for. <b>Big fraction calls big fish.</b> This is also the
     *       honest answer to "why do I only catch small stuff": it is a choice, not a bug.</li>
     *   <li><b>nutrition against appetite</b> — a carp wants a table laid; a bleak wants a cloud, and a
     *       table put down for the carp is not what it came for.</li>
     *   <li><b>variety</b> — the more different things are down there, the broader the crowd. A small
     *       term, because it is a nudge in the real thing too.</li>
     * </ul>
     *
     * <p>Nothing here punishes feeding. Overfeeding a spot is impossible by design (see FeedZoneData);
     * the worst a mix can do is say nothing this fish is interested in, and that lands it back where an
     * unfed swim already was.
     */
    private static double groundbaitScore(FishProfile p, BiteContext c) {
        if (!c.inFeedZone || c.feedFreshness <= 0 || c.feedMix == null) {
            return 0.4; // fishing an un-fed spot is fine, just not ideal
        }
        com.riverfishing.groundbait.GroundbaitMix mix = c.feedMix;
        // Never below half on either axis alone: the wrong grind should cost you the edge, not the fish.
        double fracFit = 1.0 - Math.min(1.0, Math.abs(mix.fraction() - p.gbFraction));
        double nutFit = 1.0 - Math.min(1.0, Math.abs(mix.nutrition() - p.gbNutrition));
        double variety = 0.90 + 0.10 * Math.min(1.0, (mix.variety() - 1) / 4.0);
        return Math.min(1.0, menuScore(p, mix)
                * (0.45 + 0.55 * fracFit)
                * (0.60 + 0.40 * nutFit)
                * variety);
    }

    /**
     * Does this species eat what is actually in the bowl?
     *
     * <p>Spoon-weighted over everything in the mix that NAMES a diet. The base and the ballast are not in
     * that list at all, which is the difference between "there is nothing here a bream wants" and "this
     * mix does not say" — the first should fish worse than a plain jar, the second exactly like one.
     *
     * <p>A perfect menu can go slightly over 1.0 on purpose: getting the fish's own food into the feed is
     * allowed to buy back a fraction or two of the wrong grind, because in the real thing it does.
     */
    private static double menuScore(FishProfile p, com.riverfishing.groundbait.GroundbaitMix mix) {
        int spoons = mix.additiveSpoons();
        if (spoons == 0) return 0.75;   // a plain jar of base: neither the right food nor the wrong food
        double sum = 0;
        for (java.util.Map.Entry<String, Integer> e : mix.diets().entrySet()) {
            sum += Math.max(0.0, Math.min(1.0, p.baitScore(e.getKey()))) * e.getValue();
        }
        return 0.45 + 0.75 * (sum / spoons);
    }


    private static double lineScore(FishProfile p, BiteContext c) {
        double typeMatch = c.lineType.jsonKey().equals(p.lineType) ? 1.0 : 0.6;
        double diaGrad = gradient(c.lineDiameterMm, p.lineDiameter, p.lineTolerance);
        return Math.max(0.0, Math.min(1.0, typeMatch * diaGrad));
    }

    private static double reelScore(FishProfile p, BiteContext c) {
        if (c.reelSize == 0) {
            return p.reelSize == 0 ? 1.0 : 0.25;
        }
        if (p.reelSize == 0) {
            return 0.6;
        }
        return gradient(c.reelSize, p.reelSize, p.reelTolerance);
    }

    // ---- Environmental suitability E (§1.2) ----

    public static double environmentScore(FishProfile p, BiteContext c) {
        double natural = naturalScore(p, c);
        // §stocked-survival (0.5.1): a STOCKED species lives on even in water that fails its natural
        // gates — at a quarter of full activity, scaled by how much of it is actually there. This is
        // what makes "нестандартное" зарыбление real: the settled shark in the river is catchable,
        // just never comfortable.
        double presence = c.stockedPresence != null ? c.stockedPresence.applyAsDouble(p.id) : 0.0;
        return presence > 0 ? Math.max(natural, 0.25 * presence) : natural;
    }

    private static double naturalScore(FishProfile p, BiteContext c) {
        double fWater = p.waterFactor(c.water);
        if (fWater <= 0) return 0.0; // the fish does not live in this water body

        // Habitat hard gates (§ecology): wrong depth or wrong-sized water = the fish simply isn't here.
        if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return 0.0;
        if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return 0.0;

        double fBiome = biomeGroupFactor(p, c);
        if (fBiome <= 0) return 0.0; // wrong climate/terrain — not this fish's range

        // §community (0.5.0): THIS water's own species set — the seed decides which species a given
        // lake/river patch actually holds (0 = simply not here, 1.8 = the water's signature fish).
        double fCommunity = c.communityFactor != null ? c.communityFactor.applyAsDouble(p.id) : 1.0;
        if (fCommunity <= 0) return 0.0;

        // §deep-conditions: season, time and biome are amplified so the daily/yearly rhythm and the
        // regional identity of each fish are strongly felt (see the *_POW constants).
        double fSeason = Math.pow(p.seasonFactor(c.season), SEASON_POW);
        double fTime = Math.pow(p.timeFactor(c.time), TIME_POW);
        double fWeather = p.weatherFactor(c.weather);
        double fDist = distanceFactor(p, c);

        // §bed-bite: the bottom under the cast, a nudge of 0.85..1.2 — see FishProfile.bedFactor.
        double fBed = p.bedFactor(c.bed);
        return fWater * fSeason * fTime * fWeather * Math.pow(fBiome, BIOME_POW) * fDist * fCommunity * fBed;
    }

    /**
     * Biome-range factor (§ecology): the profile's {@code biomes} map lists the groups the species
     * lives in (cold/temperate/warm, taiga, mountain, swamp, jungle…) with a factor each. The best
     * matching group wins; an empty map means "anywhere"; no match at all means the fish is absent.
     */
    private static double biomeGroupFactor(FishProfile p, BiteContext c) {
        if (p.biomes.isEmpty()) return 1.0;
        double best = 0.0;
        for (Map.Entry<String, Double> e : p.biomes.entrySet()) {
            if (c.biomeGroups.contains(e.getKey())) {
                best = Math.max(best, e.getValue());
            }
        }
        return best;
    }

    private static double distanceFactor(FishProfile p, BiteContext c) {
        if (c.rod == null) return 1.0; // environment-only view (fish finder / probe) — no tackle
        // Narrow water blocks long-range methods (§4.1).
        if (c.rod.longRange() && c.waterWidth < 12) {
            return 0.4;
        }
        double d = c.castDistance;
        if (d < p.distMin) {
            double t = p.distMin <= 0 ? 1.0 : d / p.distMin;
            return 0.6 + 0.4 * Math.max(0.0, Math.min(1.0, t));
        }
        if (d > p.distMax) {
            return 0.85;
        }
        return 1.1;
    }

    // ---- Species attractiveness W (§1.4) ----

    public static double speciesWeight(FishProfile p, BiteContext c) {
        double e = environmentScore(p, c);
        if (e <= 0) return 0.0;

        // Hard gates (realism): the wrong bait or a wrong-sized hook means the fish simply won't take.
        double sBait = baitScore(p, c);
        if (sBait <= 0.0) return 0.0;          // no bait the fish wants is on the rig (#7)
        double sHook = hookScore(p, c);
        if (sHook < HOOK_GATE) return 0.0;     // hook too big for a small fish / too small for a big one (#6)

        double m = matchScore(p, c);
        double g = feedBonus(c);
        // §population: THIS species' local stock. Fishing out the bream slows only the bream — the rest
        // of the water keeps biting, and the spot recovers over time (faster in spring, §spawn-recovery).
        double pop = c.speciesFactor != null ? c.speciesFactor.applyAsDouble(p.id) : 1.0;

        // Bigger fish demand a near-perfect setup and approach slowly: W falls off as M^sizeExp,
        // so a heavy fish only takes when the whole kit is close to ideal, and then T is long.
        double meanKg = p.weightMean / 1000.0;
        double sizeExp = 1.0 + Math.min(3.0, meanKg / 2.0);
        // §weather-pressure: a uniform feeding-activity multiplier — a falling glass feeds the whole
        // water, a bluebird high slows it. Same for every species, so it scales the time-to-bite.
        // §skills NATURALIST: a flat overall bite-chance bonus (you know where the fish are).
        double w = p.base * Math.pow(Math.max(0.0, m), sizeExp) * e * g * pop * c.pressureFactor
                * (1.0 + c.skillBiteBonus);

        // §bait-first (0.5.1): the bait is THE selector — bite speed scales directly with how much
        // this species rates what's on the hook. The right bait singles a species out of the swim;
        // a bait it barely tolerates slows it to a crawl (use that to keep the trash off), and a
        // favourite (score > 1) earns a small extra pull. Peaceful commons no longer swarm anything.
        w *= 0.30 + 0.70 * Math.min(1.3, sBait);

        // §line-visibility: a thick, opaque line spooks fish and slows the bite — but a SMALL wary fish
        // fears a visible line far more than a big fish does. Fluoro (low visibility) and thin diameters
        // stay near-invisible; thick braid on a roach swim is a real handicap. Reference: 0.20 mm mono = 1.
        double visibility = c.lineType.visibility(c.lineDiameterMm);
        if (visibility > 1.0) {
            double sensitivity = Math.max(0.1, Math.min(1.5, 1.5 - meanKg * 0.5)); // small fish = fussy
            w *= Math.max(0.4, 1.0 - 0.25 * (visibility - 1.0) * sensitivity);
        }

        if (sBait < BAIT_HARD_FILTER) w *= 0.1;            // acceptable-but-poor bait still drags it down
        if (p.requiresLeader && !c.hasLeader) w *= 0.15;   // pike/zander wary of a leaderless line
        // §leader-visibility (2.2): a fitted leader's visibility cuts both ways — a glinting steel trace
        // spooks a wary predator (~-13% bites), an invisible fluoro trace earns them (~+12%). Only when
        // a leader is actually on the rig, so leaderless float/bottom fishing is never penalised here.
        if (c.hasLeader) w *= 0.85 + c.leaderStealth * 0.30;

        // Float depth setting (§fishing-depth): presenting the bait at the species' depth pays off;
        // the wrong horizon costs bites but never turns the water dead (§bite-pacing rebalance).
        if (c.floatDepth != null) {
            w *= c.floatDepth.equals(p.depthPref) ? 1.3 : 0.55;
        }

        // §ultralight-finesse (§7): the two lure rods split the water instead of one dominating. An
        // ultralight presents tiny lures delicately for small/wary predators (a bite bonus that fades as
        // fish get bigger), while a spinning rod is crude for tiddlers but shines on size. Crossover ~1 kg,
        // so the ultralight finally has a domain the (otherwise strictly-better) spinning rod can't take.
        if (c.rod == com.riverfishing.component.RodType.ULTRALIGHT) {
            w *= Math.max(0.4, Math.min(1.6, 1.6 - meanKg * 0.6));
        } else if (c.rod == com.riverfishing.component.RodType.SPINNING) {
            w *= Math.min(1.2, 0.85 + meanKg * 0.15);
        }

        // §lure-size (0.6.0): the lure's bench mass IS its size — the optimum predator weighs about
        // 0.15 kg per gram of lure (a 20 g spoon calls ~3 kg pike). Off-size fish don't vanish, they
        // just bite far less — and since the TOTAL weight drives the wait, a big lure also means
        // slower, rarer, bigger takes. Untied (no TackleWeightG) lures skip the filter entirely.
        if (c.lureWeightG > 0) {
            // §round-6: SUBLINEAR optimum (0.5·√g) — a 200 g pilker hunts ~7 kg fish, not a mythical
            // 30 kg; and the off-size floor drops to 0.05 so a big lure truly silences the tiddlers.
            double opt = 0.5 * Math.sqrt(c.lureWeightG);
            double ratio = Math.max(0.05, meanKg / Math.max(0.1, opt));
            w *= Math.max(0.05, 2.0 / (ratio + 1.0 / ratio));
        }

        // §lure-color (§8): a painted lure whose colour suits the light/water pulls more takes; the wrong
        // colour for the conditions puts predators off. Only when a dyed lure is actually on the rig.
        if (c.lureColor != null) {
            w *= c.lureColor.conditionMultiplier(c);
        }

        // §skill-gate (§progression): min_angler_level is a real gate now — each level you're short of a
        // species' recommendation roughly halves its bite weight (×0.6 per level, floored at 3%). A novice
        // CAN still fluke a trophy on the right gear in the right place, just rarely; the seasoned angler
        // catches it steadily. Capability (tackle/bait/hook/leader) + location still gate on top of this.
        if (p.minAnglerLevel > 0 && c.anglerLevel < p.minAnglerLevel) {
            int deficit = p.minAnglerLevel - c.anglerLevel;
            w *= Math.max(0.03, Math.pow(0.6, deficit));
        }
        return Math.max(0.0, w);
    }

    public static double feedBonus(BiteContext c) {
        if (!c.inFeedZone) return 1.0;
        return Math.max(1.0, Math.min(2.0, 1.0 + c.feedFreshness));
    }

    // ---- Scheduling ----

    /**
     * §swarm-cap (§anti-macro): a big shoal of small fish stacks a huge W_total and would floor the wait,
     * turning a swim into a bite-per-few-seconds conveyor. Compress attractiveness above a knee: normal
     * single/few-target fishing is untouched, a swarm's effective total grows only a fraction. Public so
     * live re-evaluation (§live-conditions) can rescale a waiting line's clock with the same curve.
     */
    public static double effectiveWeight(double total) {
        return total <= SWARM_KNEE ? total : SWARM_KNEE + (total - SWARM_KNEE) * SWARM_DAMP;
    }

    public static Outcome evaluate(Collection<FishProfile> profiles, BiteContext c, RandomSource random) {
        Map<Identifier, Double> weights = new LinkedHashMap<>();
        double total = 0.0;
        for (FishProfile p : profiles) {
            double w = speciesWeight(p, c);
            if (w > 1e-6) {
                weights.put(p.id, w);
                total += w;
            }
        }
        if (total <= 1e-6) {
            return new Outcome(weights, 0.0, -1L);
        }
        double t = T_MIN_TICKS / effectiveWeight(total);
        double u = random.nextDouble();
        long ticks = (long) (-t * Math.log(1.0 - u));
        // §honest-tail (0.5.0): no upper clamp any more — a barely-viable setup now really IS a long
        // wait (the caller warns the player) instead of silently gifting a fish every two minutes.
        ticks = Math.max(40L, ticks);
        return new Outcome(weights, total, ticks);
    }

    /** Result of an evaluation: per-species weights, total, and a sampled time-to-bite. */
    public static final class Outcome {
        private final Map<Identifier, Double> weights;
        public final double totalWeight;
        /** Ticks until the bite; -1 means nothing is biting here. */
        public final long ticksToBite;

        Outcome(Map<Identifier, Double> weights, double totalWeight, long ticksToBite) {
            this.weights = weights;
            this.totalWeight = totalWeight;
            this.ticksToBite = ticksToBite;
        }

        public boolean willBite() {
            return ticksToBite >= 0;
        }

        /** Picks which species bit, weighted by W (§1.4). */
        public Identifier pickSpecies(RandomSource random) {
            double roll = random.nextDouble() * totalWeight;
            Identifier last = null;
            for (Map.Entry<Identifier, Double> e : weights.entrySet()) {
                last = e.getKey();
                roll -= e.getValue();
                if (roll <= 0) return e.getKey();
            }
            return last;
        }
    }
}
