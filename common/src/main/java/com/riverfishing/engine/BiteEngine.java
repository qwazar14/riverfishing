package com.riverfishing.engine;

import com.riverfishing.fish.FishProfile;
import net.minecraft.resources.ResourceLocation;
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
    private static final long MAX_WAIT_TICKS = 2400;      // 2 min ceiling (§bite-pacing): waits stay humane
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

    private static double groundbaitScore(FishProfile p, BiteContext c) {
        if (!c.inFeedZone || c.feedFreshness <= 0 || c.feedCategory == null) {
            return 0.4; // fishing an un-fed spot is fine, just not ideal
        }
        return p.idealGroundbaits.contains(c.feedCategory) ? 1.0 : 0.3;
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
        double fWater = p.waterFactor(c.water);
        if (fWater <= 0) return 0.0; // the fish does not live in this water body

        // Habitat hard gates (§ecology): wrong depth or wrong-sized water = the fish simply isn't here.
        if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return 0.0;
        if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return 0.0;

        double fBiome = biomeGroupFactor(p, c);
        if (fBiome <= 0) return 0.0; // wrong climate/terrain — not this fish's range

        // §deep-conditions: season, time and biome are amplified so the daily/yearly rhythm and the
        // regional identity of each fish are strongly felt (see the *_POW constants).
        double fSeason = Math.pow(p.seasonFactor(c.season), SEASON_POW);
        double fTime = Math.pow(p.timeFactor(c.time), TIME_POW);
        double fWeather = p.weatherFactor(c.weather);
        double fDist = distanceFactor(p, c);

        return fWater * fSeason * fTime * fWeather * Math.pow(fBiome, BIOME_POW) * fDist;
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

        // §line-visibility: a thick, opaque line spooks fish and slows the bite — but a SMALL wary fish
        // fears a visible line far more than a big fish does. Fluoro (low visibility) and thin diameters
        // stay near-invisible; thick braid on a roach swim is a real handicap. Reference: 0.20 mm mono = 1.
        double visibility = c.lineType.visibilityFactor() * (c.lineDiameterMm / 0.20);
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

    public static Outcome evaluate(Collection<FishProfile> profiles, BiteContext c, RandomSource random) {
        Map<ResourceLocation, Double> weights = new LinkedHashMap<>();
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
        // §swarm-cap (§anti-macro): a big shoal of small fish stacks a huge W_total and floors the wait at
        // the rod-class minimum, turning a swim into a bite-per-few-seconds conveyor. Compress attractiveness
        // above a knee: normal single/few-target fishing (W_total below the knee) is untouched, but a swarm's
        // effective total grows only a fraction, so the pacing stays lively without becoming farmable.
        double eff = total <= SWARM_KNEE ? total : SWARM_KNEE + (total - SWARM_KNEE) * SWARM_DAMP;
        double t = T_MIN_TICKS / eff;
        double u = random.nextDouble();
        long ticks = (long) (-t * Math.log(1.0 - u));
        ticks = Math.max(40L, Math.min(MAX_WAIT_TICKS, ticks));
        return new Outcome(weights, total, ticks);
    }

    /** Result of an evaluation: per-species weights, total, and a sampled time-to-bite. */
    public static final class Outcome {
        private final Map<ResourceLocation, Double> weights;
        public final double totalWeight;
        /** Ticks until the bite; -1 means nothing is biting here. */
        public final long ticksToBite;

        Outcome(Map<ResourceLocation, Double> weights, double totalWeight, long ticksToBite) {
            this.weights = weights;
            this.totalWeight = totalWeight;
            this.ticksToBite = ticksToBite;
        }

        public boolean willBite() {
            return ticksToBite >= 0;
        }

        /** Picks which species bit, weighted by W (§1.4). */
        public ResourceLocation pickSpecies(RandomSource random) {
            double roll = random.nextDouble() * totalWeight;
            ResourceLocation last = null;
            for (Map.Entry<ResourceLocation, Double> e : weights.entrySet()) {
                last = e.getKey();
                roll -= e.getValue();
                if (roll <= 0) return e.getKey();
            }
            return last;
        }
    }
}
