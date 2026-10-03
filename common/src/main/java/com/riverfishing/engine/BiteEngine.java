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
    /** §hybrid-rare: a hybrid's share of its own weight in wild water. */
    private static final double HYBRID_WILD = 0.04;
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
            best = Math.max(best, "boilie".equals(bait) ? boilieBaitScore(p, c) : p.baitScore(bait));
        }
        // §tying: a tied lure fishes as its template says for this fish's family — an ant is food to
        // a roach and a curiosity to a pike; a streamer the other way round.
        if (c.tied != null) {
            // §tying: a profile may rate a tied TEMPLATE by name (fly_nymph, fly_streamer, fly_pellet, ...) the
            // way it rates any bait; a species that says nothing takes the family's generic affinity for it.
            Double own = p.baitScores.get("fly_" + c.tied.template().key);
            if (own != null) best = own;
            // §species-table: by what it eats. A rig with nothing else on it (a fly on a tippet) starts
            // from 1, not 0 — scaling nothing by the affinity left the tied fly scoring zero everywhere.
            else best = (best > 0 ? best : 1.0) * c.tied.affinity(p.diet, p.group);
        }
        return best;
    }

    /**
     * §boilies: how much this species rates the boilie on the hook as a bait. Too big for its mouth, nothing;
     * a small sweet one is what the roach and the bream strip off the hook — they take it like their dough.
     */
    private static double boilieBaitScore(FishProfile p, BiteContext c) {
        com.riverfishing.fish.Boilie b = c.boilie;
        if (b == null) return p.baitScore("boilie");
        if (b.tooBigFor(p.weightMean)) return 0.0;
        double own = p.baitScore("boilie");
        if (own <= 0 && b.nuisanceBait()) {
            own = 0.6 * Math.max(p.baitScore("dough"), Math.max(p.baitScore("bread"), p.baitScore("maggot")));
        }
        return own;
    }

    /** §boilies: the moment the boilie is judged in — season, hour, sky, glass, water, bottom, and who is eating. */
    public static com.riverfishing.fish.Boilie.Scene boilieScene(FishProfile p, BiteContext c) {
        double cover = c.lake != null && c.lakeZone >= 0 && c.lakeZone < c.lake.zones.size() ? c.lake.zones.get(c.lakeZone).cover : 0.3;
        return new com.riverfishing.fish.Boilie.Scene(
                c.season == null ? com.riverfishing.engine.Season.SUMMER : c.season, c.time, c.weather,
                Math.abs(c.pressureTrend) > 3.0, c.clarity, c.bed, cover,
                c.water == com.riverfishing.water.WaterType.RIVER || c.biomeRiver,
                p == null || p.diet == null ? "" : p.diet, p == null || p.group == null ? "" : p.group,
                p == null ? 1000 : p.weightMean);
    }

    /** Best-fitting hook among those loaded. Lure rigs carry no separate hook — the lure's treble counts. */
    private static double hookScore(FishProfile p, BiteContext c) {
        if (c.hookSizes.isEmpty()) {
            // A predator lure's treble and a winter mormyshka carry their own hook — no separate hook slot.
            // §fly-take: a tied fly on a tippet carries its hook the same way — without this line every
            // species was "no_hook" on the fly rig and no fish ever took.
            return (c.rig == com.riverfishing.component.RigType.PREDATOR
                    || c.rig == com.riverfishing.component.RigType.WINTER
                    || c.rig == com.riverfishing.component.RigType.FLY) ? 0.85 : 0.0;
        }
        double best = 0.0;
        for (int size : c.hookSizes) {
            best = Math.max(best, gradient(size, p.hookIdeal, p.hookTolerance));
        }
        // §hook-mouth: a species whose biggest specimen cannot get the smallest hook on the rig into its
        // mouth does not take it, whatever the size gradient says
        if (p.weightMax < mouthG(c.hookSizes)) return 0.0;
        return best;
    }

    /**
     * §hook-mouth: the smallest fish that can take a hook of this size, in grams — 40 g at #8, halving
     * every two sizes down (#16: 2.5 g) and doubling every two up (#2: 320 g). The smallest hook on the
     * rig sets it; nothing is asked of an empty rig.
     */
    public static double mouthG(java.util.Collection<Integer> hookSizes) {
        if (hookSizes.isEmpty()) return 0.0;
        int smallest = java.util.Collections.max(hookSizes);   // bigger number, smaller hook
        return 40.0 * Math.pow(2.0, (8 - smallest) / 2.0);
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
        // §species-table: the rod, the rig and the reel left the match — a species asks for bait, feed,
        // line and hook, and how you deliver them is your business
        double sLine = lineScore(p, c);
        double sHook = hookScore(p, c);

        return 0.45 * sBait
                + 0.20 * sGround
                + 0.20 * sLine
                + 0.15 * sHook;
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

    // ---- Environmental suitability E (§1.2) ----

    public static double environmentScore(FishProfile p, BiteContext c) {
        return environmentScore(p, c, true);
    }

    /**
     * §alife: whether and how well this species LIVES here — every gate and the water, biome, community
     * and bed factors, but not the season, the hour or the weather. Those decide how active the fish is,
     * which the living water plays out itself; this decides who is in it at all.
     */
    public static double habitatScore(FishProfile p, BiteContext c) {
        return environmentScore(p, c, false);
    }

    private static double environmentScore(FishProfile p, BiteContext c, boolean clock) {
        // §livebait-4: the mouth rule is not a habitat gate the stocking floor may lift — a stocked
        // species whose biggest specimen is under five times the bait still cannot take it. A 505 g
        // pollock took a 2.5 kg bait through this floor. First, before anything is scored.
        if (c.livebaitG > 0 && p.weightMax < c.livebaitG * PREY_RATIO) return 0.0;
        double natural = naturalScore(p, c, clock);
        double presence = c.stockedPresence != null ? c.stockedPresence.applyAsDouble(p.id) : 0.0;
        // §hybrid-rare: a hybrid is a fish of the breeding tank, not of the river — wild water holds it one
        // time in twenty-five; stocked and settled it fishes like anything else (the presence rule below)
        if (!p.hybridOf.isEmpty() && presence <= 0) natural *= HYBRID_WILD;
        // §stocked-survival (0.5.1): a STOCKED species lives on even in water that fails its natural
        // gates — at a quarter of full activity, scaled by how much of it is actually there. This is
        // what makes "нестандартное" зарыбление real: the settled shark in the river is catchable,
        // just never comfortable.
        return presence > 0 ? Math.max(natural, 0.25 * presence) : natural;
    }

    /** §livebait-4: the taker is at least this many times the baitfish — the top of the 10–20 % prey band. */
    public static final double PREY_RATIO = 5.0;

    private static double naturalScore(FishProfile p, BiteContext c, boolean clock) {
        double fWater = p.waterFactor(c.water);
        if (fWater <= 0) return 0.0; // the fish does not live in this water body

        // Habitat hard gates (§ecology): wrong depth or wrong-sized water = the fish simply isn't here.
        // §pond: not in a private pond — a 2x5x2 pit holds whatever its owner put in it, and the water type,
        // climate and biome gates below still say whether the fish can live there at all.
        if (!c.privatePond) {
            if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return 0.0;
            if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return 0.0;
        }

        // §provinces: half a planet, the one gate a biome cannot express. A species that names
        // provinces is absent from every other one — no factor, no half rate, absent. A private pond is
        // exempt (its owner put the fish there), and §stocked-survival above already lets a settled
        // species live outside its range at a quarter of full activity: travel to find it, or bring it
        // home and breed it. Those are the two ways, and both of them are the point.
        if (!c.privatePond && !p.provinces.isEmpty()
                && !c.province.isEmpty() && !p.provinces.contains(c.province)) {
            return 0.0;
        }
        // §biomes-require: every group in the list, not the best of them. A specialist says what it
        // needs all at once — cold AND a river AND mountains — and a water missing any of it has none.
        if (!c.privatePond && !p.biomesRequire.isEmpty() && !c.biomeGroups.containsAll(p.biomesRequire)) {
            return 0.0;
        }

        double fBiome = biomeGroupFactor(p, c);
        // §pond-biome: a private pond is out of every fish's range by definition — the owner put the
        // fish there. The gate stays for wild water; in a pond a fish out of its climate lives and
        // bites at half rate, which is what a koi in a forest pit deserves.
        if (fBiome <= 0) { if (!c.privatePond) return 0.0; fBiome = 0.5; }

        // §community (0.5.0): THIS water's own species set — the seed decides which species a given
        // lake/river patch actually holds (0 = simply not here, 1.8 = the water's signature fish).
        double fCommunity = c.communityFactor != null ? c.communityFactor.applyAsDouble(p.id) : 1.0;
        if (fCommunity <= 0) return 0.0;

        // §deep-conditions: season, time and biome are amplified so the daily/yearly rhythm and the
        // regional identity of each fish are strongly felt (see the *_POW constants).
        double fSeason = clock ? Math.pow(p.seasonFactor(c.season), SEASON_POW) : 1.0;
        double fTime = clock ? Math.pow(p.timeFactor(c.time), TIME_POW) : 1.0;
        double fWeather = clock ? p.weatherFactor(c.weather) : 1.0;
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
        // §species-table: the species' own distance band is gone — where the fish holds is the water's
        // business (depth, width, bed), not a number per profile
        return 1.0;
    }

    // ---- Species attractiveness W (§1.4) ----

    public static double speciesWeight(FishProfile p, BiteContext c) {
        double e = environmentScore(p, c);
        if (e <= 0) return 0.0;
        double t = tackleWeight(p, c);
        if (t <= 0) return 0.0;
        // §population: THIS species' local stock. Fishing out the bream slows only the bream — the rest
        // of the water keeps biting, and the spot recovers over time (faster in spring, §spawn-recovery).
        double pop = c.speciesFactor != null ? c.speciesFactor.applyAsDouble(p.id) : 1.0;
        // §weather-pressure: a uniform feeding-activity multiplier — a falling glass feeds the whole
        // water, a bluebird high slows it. Same for every species, so it scales the time-to-bite.
        // §skills NATURALIST: a flat overall bite-chance bonus (you know where the fish are).
        return p.base * e * feedBonus(c) * pop * c.pressureFactor * (1.0 + c.skillBiteBonus) * t;
    }

    /**
     * §alife: what the rig is worth to this species — every part of the old weight that is about the bait,
     * the hook, the line, the lure and the angler, and none of it about the water. The living water asks
     * this of each fish near the bait; the old engine multiplies it by the water's own terms above.
     */
    public static double tackleWeight(FishProfile p, BiteContext c) {
        // Hard gates (realism): the wrong bait or a wrong-sized hook means the fish simply won't take.
        double sBait = baitScore(p, c);
        if (sBait <= 0.0) return 0.0;          // no bait the fish wants is on the rig (#7)
        double sHook = hookScore(p, c);
        if (sHook < HOOK_GATE) return 0.0;     // hook too big for a small fish / too small for a big one (#6)

        double m = matchScore(p, c);

        // Bigger fish demand a near-perfect setup and approach slowly: W falls off as M^sizeExp,
        // so a heavy fish only takes when the whole kit is close to ideal, and then T is long.
        double meanKg = p.weightMean / 1000.0;
        double sizeExp = 1.0 + Math.min(3.0, meanKg / 2.0);
        double w = Math.pow(Math.max(0.0, m), sizeExp);
        // §boilies: the right boilie for the day, the water and the fish — when the boilie IS what it takes
        if (c.boilie != null && c.baits.contains("boilie") && boilieBaitScore(p, c) >= sBait - 1e-9) {
            w *= c.boilie.factor(boilieScene(p, c));
        }

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
        // §species-table: the ladder runs 0-50 now and the level never forbids — short of the rung the bite
        // thins in proportion, to a floor of 15 %, so a novice CAN fluke the fish and a veteran fishes it steadily
        if (p.minAnglerLevel > 0 && c.anglerLevel < p.minAnglerLevel) {
            double deficit = (p.minAnglerLevel - c.anglerLevel) / (double) p.minAnglerLevel;
            w *= Math.max(0.15, 1.0 - 0.85 * deficit);
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
        if (c.lake != null) return evaluateAlife(profiles, c, random);
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
            return new Outcome(weights, 0.0, -1L, null);
        }
        double t = T_MIN_TICKS / effectiveWeight(total);
        double u = random.nextDouble();
        long ticks = (long) (-t * Math.log(1.0 - u));
        // §honest-tail (0.5.0): no upper clamp any more — a barely-viable setup now really IS a long
        // wait (the caller warns the player) instead of silently gifting a fish every two minutes.
        ticks = Math.max(40L, ticks);
        return new Outcome(weights, total, ticks, null);
    }

    /**
     * §alife: the bite from the living water. Every fish near the bait is asked how much it wants THIS rig
     * ({@link #tackleWeight}, behind the habitat gates of the bait's own column — a fish does not take a
     * bait lying in water it cannot be in); the lake answers with bites per game hour from the agents that
     * are actually there, hungry and not wary. Summed per species it is the same Outcome as ever, scaled so
     * that {@link #T_MIN_TICKS} / weight is the wait: one game hour is 1000 ticks.
     */
    /** §alife: what this cast's rig is worth to a species of the living water — behind the habitat gates of the bait's own column. */
    public static java.util.function.ToDoubleFunction<com.riverfishing.alife.Species> alifeAppeal(BiteContext c) {
        return sp -> {
            FishProfile p = com.riverfishing.fish.FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(sp.id()));
            if (p == null || habitatScore(p, c) <= 0) return 0.0;
            return tackleWeight(p, c) * (1.0 + c.skillBiteBonus);
        };
    }

    /**
     * §alife: this cast as the living water sees it. A lure worked back through the water on a spinning rod
     * passes more fish than a bait lying still, so an active rod is asked of the fish up to ~20 blocks off.
     */
    public static com.riverfishing.alife.Lake.Offer alifeOffer(BiteContext c) {
        boolean active = c.rod != null && c.rod.rodClass() == com.riverfishing.component.RodClass.ACTIVE;
        java.util.List<String> keys = c.baits;
        double reach = active ? 20.0 : 12.0;
        if (c.boilie != null) {
            // §boilies: its flavours are what a prebaited fish knows it by; a dip or a strong smell carries further
            keys = new java.util.ArrayList<>(c.baits);
            for (com.riverfishing.fish.Flavour f : c.boilie.flavours()) keys.add("flavour:" + f.id());
            if (c.boilie.dip() != null) keys.add("flavour:" + c.boilie.dip().id());
            reach += c.boilie.reachBonus(boilieScene(null, c));
        }
        return new com.riverfishing.alife.Lake.Offer(c.lakeZone, alifeAppeal(c), keys, 0.0, reach);
    }

    private static Outcome evaluateAlife(Collection<FishProfile> profiles, BiteContext c, RandomSource random) {
        Map<String, FishProfile> byPath = new java.util.HashMap<>();
        for (FishProfile p : profiles) byPath.put(p.id.getPath(), p);
        com.riverfishing.alife.Lake.Interest in = c.lake.offer(alifeOffer(c), c.lakeNow);
        Map<ResourceLocation, Double> weights = new LinkedHashMap<>();
        for (int i = 0; i < in.agents().size(); i++) {
            FishProfile p = byPath.get(in.agents().get(i).sp.id());
            if (p != null) weights.merge(p.id, in.rates()[i] * T_MIN_TICKS / 1000.0, Double::sum);
        }
        double total = in.total() * T_MIN_TICKS / 1000.0;
        if (total <= 1e-6) return new Outcome(weights, 0.0, -1L, in);
        double t = T_MIN_TICKS / effectiveWeight(total);
        long ticks = Math.max(40L, (long) (-t * Math.log(1.0 - random.nextDouble())));
        return new Outcome(weights, total, ticks, in);
    }

    /** Result of an evaluation: per-species weights, total, and a sampled time-to-bite. */
    public static final class Outcome {
        private final Map<ResourceLocation, Double> weights;
        public final double totalWeight;
        /** Ticks until the bite; -1 means nothing is biting here. */
        public final long ticksToBite;
        /** §alife: who in the water is interested, so the landed fish comes out of the right shoal; null = old engine. */
        public final com.riverfishing.alife.Lake.Interest interest;

        Outcome(Map<ResourceLocation, Double> weights, double totalWeight, long ticksToBite,
                com.riverfishing.alife.Lake.Interest interest) {
            this.weights = weights;
            this.totalWeight = totalWeight;
            this.ticksToBite = ticksToBite;
            this.interest = interest;
        }

        /** §alife: the agent of this species that bites, weighted by how keen each of them is; null = old engine. */
        public com.riverfishing.alife.Lake.Agent pickAgent(ResourceLocation species, RandomSource random) {
            if (interest == null) return null;
            double sum = 0;
            for (int i = 0; i < interest.agents().size(); i++) {
                if (interest.agents().get(i).sp.id().equals(species.getPath())) sum += interest.rates()[i];
            }
            double roll = random.nextDouble() * sum;
            for (int i = 0; i < interest.agents().size(); i++) {
                if (!interest.agents().get(i).sp.id().equals(species.getPath())) continue;
                if ((roll -= interest.rates()[i]) <= 0) return interest.agents().get(i);
            }
            return null;
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
