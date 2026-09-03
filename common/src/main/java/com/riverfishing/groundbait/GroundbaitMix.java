package com.riverfishing.groundbait;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §groundbait-one-jar (0.8.0): what a groundbait IS.
 *
 * <p>There used to be four jars — powder, grain, pellet, cake — and every fish named the ones it liked.
 * That was a lookup table wearing a mechanic's clothes: four answers, fifty questions, and nothing to
 * decide once you had read the page. All four are gone. <b>There is one jar</b>, the loose mix, and it is
 * the BASE the way an angler means it: the thing you build on, useful by itself and never the answer on
 * its own.
 *
 * <p>Everything about a groundbait now falls out of what you put in the bowl:
 *
 * <ul>
 *   <li>{@code nutrition} — how much food is in it. Food is what gathers fish, so this is how hard the
 *       spot pulls. It is also a preference: a bleak wants a thin cloud, a carp wants a table.</li>
 *   <li>{@code fraction} — 0 is dust that clouds and disperses, 1 is grain that lies on the bottom.
 *       <b>Big fraction calls big fish.</b> This is the honest answer to "why do I only catch tiddlers".</li>
 *   <li>{@code diets} — WHAT is in it, by name. If worm is in the fish's diet, worm in the feed works.
 *       Read straight off the species' own bait preferences, which every profile already carries.</li>
 *   <li>{@code variety} — how many different things are in it. A varied mix gathers a bigger and more
 *       mixed shoal than a single ingredient ever can.</li>
 * </ul>
 *
 * <p>Composition is counted in ITEMS: one slot of the grid is one item of that component, and its share
 * is that over the total. The field is still called {@code spoons} internally — renaming it would touch
 * the NBT key and twenty call sites for no player-visible gain — but nothing a player reads says "spoon"
 * or "jar" any more. Those words were invented here and they sent people looking for a measuring spoon.
 *
 * <p>Nothing here touches the world OR Minecraft. Feeding and decay live in FeedZoneData, reading and
 * writing a stack lives in {@link GroundbaitNbt}, and this class only answers "given these spoons, what
 * is the mix". That is deliberate: {@link #selfCheck()} is the thing that guards the balance of the whole
 * feature, and a check that needs a mapped game jar on the classpath is a check nobody ever runs again.
 * This one runs with plain `java`.
 */
public final class GroundbaitMix {

    /**
     * Most of one component, and most components — both NINE, which is the size of a crafting grid: the
     * BASE plus up to EIGHT additives. The grid is the real limit, and inventing a smaller one caused a
     * duplication bug.
     *
     * <p>At five the recorded composition disagreed with what the player actually put in (eight corn was
     * written down as five), so the payout had to be counted somewhere else, and the payout and the
     * composition then drifted apart — twice. At nine a vanilla grid cannot exceed the cap, the kept
     * parts ARE the grid, and {@link #jars} can be read off the same list that produced every other
     * number. A bigger modded grid still clamps; it under-counts rather than over-pays, which is the
     * safe direction.
     */
    public static final int MAX_SPOONS = 9;
    public static final int MAX_COMPONENTS = 9;
    /** The base takes one of the nine, so eight is what is left for what you add to it. */
    public static final int MAX_ADDITIVES = 8;

    /**
     * The one craftable groundbait, and the thing every mix is built on.
     *
     * <p>Crafted from <b>wheat seeds + bread</b>. Neither of those is the base itself, so no mix can ever
     * collide with the recipe that makes it — which is what lets {@link #qualifiesAsMix} be as simple as
     * "does it contain the base".
     */
    public static final String BASE_ID = "groundbait_powder";

    /**
     * What one component brings.
     *
     * <p>{@code diet} is the bait this reads as when the bite engine asks whether the fish eats this. It
     * is null for the BASE and for ballast, and null means <b>does not vote</b> — the base is cereal
     * filler that says nothing about what is on the menu, and soil says even less. That null is the whole
     * shape of the feature: the base decides nothing, everything you add to it decides.
     *
     * <p>{@code rgb} is what it stains the mix. Real groundbait is coloured on purpose — dark on clear
     * pressured water so a fed patch does not look alarming, bright in murk so fish can find it — and
     * beetroot really is one of the dyes people use. In the game it feeds {@link
     * com.riverfishing.engine.LureColor}, the same classifier a painted lure goes through, so the rule
     * a player learned on lures holds here too.
     */
    public record Component(String id, double nutrition, double fraction, String diet, int rgb) {}

    /**
     * The pantry.
     *
     * <p>Numbers come from the real thing: breadcrumb is a cloud that feeds almost nothing, boilies are
     * dense bottom food, chopped worm is meat that sinks and stays. Ballast is the dial the whole system
     * needs — half a mix of soil pulls fish in without laying much of a table.
     */
    public static final Map<String, Component> PANTRY = new LinkedHashMap<>();

    private static void put(String id, double nutrition, double fraction, String diet, int rgb) {
        PANTRY.put(id, new Component(id, nutrition, fraction, diet, rgb));
    }

    static {
        // ---- the base, and the two ballasts ----
        // §groundbait-one-jar: dead centre on BOTH axes, and that is not laziness — "you raise and lower
        // it from here" only means something if here is the middle. A base pinned at fraction 0.10 can
        // only ever be made coarser, which is half a mechanic.
        put(BASE_ID, 0.50, 0.50, null, 0xE0DBC4);
        put("groundbait_soil", 0.00, 0.35, null, 0x6B5236);   // inert ballast: no calories, no identity, all volume
        put("minecraft:clay_ball", 0.00, 0.55, null, 0x9AA7B4); // the second ballast: binds, sinks, feeds nothing

        // ---- the mod's own baits, chopped into the feed the way an angler really does ----
        // Each one names itself as a diet, so "the fish eats worm, so worm in the feed works" needs no
        // new table anywhere: it is the species' own bait preference, asked a second time.
        put("bread", 0.25, 0.10, "bread", 0xC9A46A);
        put("dough", 0.60, 0.30, "dough", 0xE0D2A8);
        put("corn", 0.85, 0.90, "corn", 0xE8C23A);
        put("pea", 0.80, 0.75, "pea", 0x7FA24A);
        put("pearl_barley", 0.70, 0.70, "pearl_barley", 0xD8CBA4);
        put("boilie", 0.95, 1.00, "boilie", 0x9A4B2A);
        put("maggot", 0.65, 0.55, "maggot", 0xEDE4C8);
        put("worm", 0.70, 0.65, "worm", 0x8F463E);            // chopped worm: meat, sinks, stays
        put("bloodworm", 0.30, 0.20, "bloodworm", 0x8D2124);  // joker: the fine LIVE additive
        put("chicken_liver", 0.80, 0.65, "chicken_liver", 0x732728);
        put("fish_strip", 0.75, 0.80, "fish_strip", 0x976661); // chum: ground fish lies on the deck
        // §j: what the grinder and the furnace make of a fish. Both read as "livebait" — the one diet
        // word the freshwater predators (pike, zander, perch, catfish) score, and the sturgeon and the big
        // game with them; "fish_strip" is the SEA word and pike does not answer it at all. Meal is dense
        // protein that lies on the bottom; oil is fraction 0 on purpose — a slick, all scent and no grain.
        put("fish_meal", 0.90, 0.30, "livebait", 0xC8A870);
        put("fish_oil", 0.60, 0.00, "livebait", 0xD89A30);

        // ---- vanilla, so the first mix does not wait on a farm ----
        // Reach matters more than variety here: wheat and a potato are hour-one items, corn and boilies
        // are not. Without these the whole system only opens up late, which is the wrong shape for the
        // thing that is supposed to teach you how feeding works.
        //
        // A vanilla item borrows the diet of the mod bait it stands in for, and takes null when it stands
        // in for nothing. Null is not a penalty — it still changes the nutrition, the fraction, the colour
        // and the variety of the mix. It only means this ingredient makes no claim about the menu.
        put("minecraft:wheat", 0.55, 0.45, "pearl_barley", 0xC8A83C);
        put("minecraft:wheat_seeds", 0.30, 0.15, null, 0x93A857);
        put("minecraft:bread", 0.45, 0.20, "bread", 0xB07840);
        put("minecraft:potato", 0.60, 0.50, "dough", 0xD8B45C);
        put("minecraft:carrot", 0.45, 0.55, "corn", 0xE8801C);
        put("minecraft:beetroot", 0.40, 0.45, "corn", 0x8C1F35);      // the classic red dye, and it is food
        put("minecraft:sweet_berries", 0.35, 0.40, "boilie", 0xA02030); // the fruit flavour a boilie is
        put("minecraft:sugar", 0.20, 0.05, null, 0xF2F2F2);            // sweetener: all flavour, no menu
        put("minecraft:cocoa_beans", 0.35, 0.25, "boilie", 0x6B3A1E);
        // Makuha. It lost its press recipe and kept its job — it is the carp plant-food, same as corn.
        put("minecraft:sunflower", 0.60, 0.25, "corn", 0xB8912E);
        put("minecraft:pumpkin_seeds", 0.55, 0.35, "pea", 0xD8CE8C);
        put("minecraft:melon_seeds", 0.50, 0.30, "pea", 0xC8D8A0);
        put("minecraft:dried_kelp", 0.40, 0.30, "fish_strip", 0x2E5E36); // sea-side green, and it smells of sea
    }

    /**
     * A plain jar off the shelf, with nothing stirred into it: the base's own numbers.
     *
     * <p>Neutral on both axes, no diet, one component. It helps — a fed spot always beats an unfed one —
     * and it is beaten by anything the player thought about, which is the point of it.
     */
    public static final GroundbaitMix BASE =
            new GroundbaitMix(List.of(new Part(BASE_ID, 1)), 0.50, 0.50, 0xE0DBC4);

    private final List<Part> parts;
    private final double nutrition;
    private final double fraction;
    private final int rgb;

    public record Part(String id, int spoons) {}

    private GroundbaitMix(List<Part> parts, double nutrition, double fraction, int rgb) {
        this.parts = List.copyOf(parts);
        this.nutrition = nutrition;
        this.fraction = fraction;
        this.rgb = rgb;
    }

    public List<Part> parts() { return parts; }
    public double nutrition() { return nutrition; }
    public double fraction() { return fraction; }
    /** The mix's own colour, before any dye. */
    public int rgb() { return rgb; }

    /** How many DIFFERENT things are in it. A varied table gathers a bigger and more mixed shoal. */
    public int variety() { return parts.size(); }

    /**
     * What is on the menu, in spoons: diet id -> spoons of it.
     *
     * <p>The base and the ballast are not in here at all. That is the difference between "this mix has
     * nothing a bream eats in it" and "this mix does not say" — the first should fish worse than a plain
     * jar, the second should fish exactly like one.
     */
    public Map<String, Integer> diets() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Part p : parts) {
            Component c = PANTRY.get(p.id());
            if (c != null && c.diet() != null) out.merge(c.diet(), p.spoons(), Integer::sum);
        }
        return out;
    }

    /** Spoons of anything that names a diet — the denominator for how well the menu matches. */
    public int additiveSpoons() {
        int n = 0;
        for (Map.Entry<String, Integer> e : diets().entrySet()) n += e.getValue();
        return n;
    }

    /**
     * The signature of what this mix IS, for the fed spot.
     *
     * <p>Two jars with the same signature are the same groundbait and add up in the water; two with
     * different signatures do not, and the later one takes the swim over. Colour is deliberately NOT in
     * here: a dyed jar and an undyed one of the same recipe are the same food, and telling a player their
     * two identical mixes fight each other because one was pink would be a lie about the fishing.
     */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        List<Part> sorted = new ArrayList<>(parts);
        sorted.sort((a, b) -> a.id().compareTo(b.id()));
        for (Part p : sorted) sb.append(p.id()).append(':').append(p.spoons()).append(';');
        return sb.toString();
    }

    public boolean isBase() { return parts.size() == 1 && BASE_ID.equals(parts.get(0).id()); }

    /** The same mix stained by a dye — what the grid does when the player drops a dye in. */
    public GroundbaitMix dyed(int dyeRgb) {
        return new GroundbaitMix(parts, nutrition, fraction, blend(rgb, dyeRgb, 0.6));
    }

    /**
     * The same mix wearing a colour it would not have computed for itself.
     *
     * <p>Only {@link GroundbaitNbt} needs this, to put a stored dye back on a mix that has just been
     * stirred from its parts. A dye is the one thing about a jar that cannot be re-derived from what is
     * in it, so it is the one thing that has to be written down.
     */
    public GroundbaitMix recoloured(int rgb) {
        return new GroundbaitMix(parts, nutrition, fraction, rgb & 0xFFFFFF);
    }

    /** Weighted RGB blend. Straight per-channel: mixing powders is not light, it is paint. */
    private static int blend(int a, int b, double towardsB) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - towardsB) + ((b >> 16) & 0xFF) * towardsB);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - towardsB) + ((b >> 8) & 0xFF) * towardsB);
        int bl = (int) Math.round((a & 0xFF) * (1 - towardsB) + (b & 0xFF) * towardsB);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Stir the components into a mix.
     *
     * <p>Nutrition and fraction are the spoon-weighted mean over EVERY component, ballast included — that
     * is how soil dilutes: it contributes zeros to the average, so a mix that is half soil lays half the
     * table. It still counts towards fraction, because mud on the bottom really is coarse.
     *
     * @return null if the recipe is empty or every spoon in it is inert — a jar of wet mud is not
     *     groundbait, and a spot fed with it would gather nothing.
     */
    public static GroundbaitMix of(List<Part> recipe) {
        double totalSpoons = 0, nutrition = 0, fraction = 0, red = 0, green = 0, blue = 0;
        boolean anyFood = false;
        List<Part> kept = new ArrayList<>();

        for (Part p : recipe) {
            Component c = PANTRY.get(p.id());
            if (c == null || p.spoons() <= 0) continue;
            int spoons = Math.min(p.spoons(), MAX_SPOONS);
            kept.add(new Part(p.id(), spoons));
            totalSpoons += spoons;
            nutrition += c.nutrition() * spoons;
            fraction += c.fraction() * spoons;
            if (c.nutrition() > 0) anyFood = true;
            // Colour blends by spoon like everything else, and ballast DOES tint: a mix half soil really
            // does come out the colour of soil, which is exactly why a lean town mix looks unalarming.
            red += ((c.rgb() >> 16) & 0xFF) * spoons;
            green += ((c.rgb() >> 8) & 0xFF) * spoons;
            blue += (c.rgb() & 0xFF) * spoons;
            if (kept.size() >= MAX_COMPONENTS) break;
        }
        if (totalSpoons == 0 || !anyFood) {
            return null;
        }

        int rgb = ((int) Math.round(red / totalSpoons) << 16)
                | ((int) Math.round(green / totalSpoons) << 8)
                | (int) Math.round(blue / totalSpoons);
        return new GroundbaitMix(kept, nutrition / totalSpoons, fraction / totalSpoons, rgb);
    }

    /**
     * How many jars this mix pays out: ONE PER SPOON OF FOOD.
     *
     * <p>Ballast is what a mix is diluted WITH, not what it is made OF, so soil and clay pay nothing.
     * They still change the nutrition, the fraction and the colour of every jar; they do not change how
     * many jars there are. Counting slots instead is how eight soil and one jar of feed became nine jars
     * for two dirt.
     *
     * <p>Read off the KEPT parts — the same list that decided the nutrition, the fraction and the colour.
     * One list, one read: there is no second number here that could disagree with a first one, which is
     * the whole point, because that disagreement is this feature's recurring bug.
     */
    public static int jars(GroundbaitMix mix) {
        int jars = 0;
        for (Part p : mix.parts()) {
            Component c = PANTRY.get(p.id());
            if (c != null && c.nutrition() > 0) jars += p.spoons();
        }
        return jars;
    }

    /**
     * Is this a MIX?
     *
     * <p><b>A mix is THE BASE plus something.</b> Not "any two things from the pantry" — the base is what
     * you build on, and requiring it is what makes everything else an ADDITIVE rather than a second way
     * of making groundbait from scratch. Up to eight additives fit beside it, which is what a crafting
     * grid holds anyway once the base has taken its slot.
     *
     * <p>The base's own recipe is wheat seeds + bread, and neither of those is the base, so a mix can
     * never swallow it. The old "three or more components" rule existed only to dodge that collision;
     * with the base required, the collision cannot happen at all.
     */
    public static boolean qualifiesAsMix(List<Part> recipe, boolean dyed) {
        boolean hasBase = false;
        int additives = 0;
        for (Part p : recipe) {
            if (!PANTRY.containsKey(p.id()) || p.spoons() <= 0) continue;
            if (BASE_ID.equals(p.id())) hasBase = true;
            else additives += p.spoons();
        }
        // A dye counts as something added: staining a base is a legal, if pointless, mix.
        return hasBase && (additives > 0 || dyed);
    }

    /**
     * Self-check for the arithmetic that decides what a mix is. Runs from {@code RiverFishing.init()},
     * so a bad edit to the pantry fails on load rather than three hours into someone's session.
     *
     * <p>It also runs standalone, which is the reason this class has no Minecraft in it:
     * <pre>
     *   gradlew :common:compileJava
     *   java -cp common/build/classes/java/main GroundbaitMixCheck
     * </pre>
     */
    public static void selfCheck() {
        // Every pantry entry has to be inside the ranges the rest of the system assumes.
        for (Component c : PANTRY.values()) {
            require(c.nutrition() >= 0 && c.nutrition() <= 1, c.id() + " nutrition out of range");
            require(c.fraction() >= 0 && c.fraction() <= 1, c.id() + " fraction out of range");
            require((c.rgb() & ~0xFFFFFF) == 0, c.id() + " colour is not a plain 24-bit rgb");
        }

        // THE COARSE-LEAN RULE. A component that is coarse AND lean at once hands the player a big
        // fraction without paying for it in food, which deletes ballast as the dial the whole feature
        // turns on. Ballast is exempt: being coarse and free is what ballast IS. Tightest real margin is
        // minecraft:carrot at +0.05, and it held before anybody wrote the rule down.
        for (Component c : PANTRY.values()) {
            if (c.nutrition() <= 0) continue;                  // ballast is meant to be coarse and free
            require(c.nutrition() >= c.fraction() - 0.15,
                    c.id() + " is coarse and lean at once (" + c.nutrition() + " / " + c.fraction()
                            + ") — that is a big fraction without paying for it, which is ballast's job");
        }

        // §groundbait-one-jar: the four jars are GONE, and nothing may quietly put them back.
        for (String dead : new String[]{"groundbait_grain", "groundbait_pellet", "groundbait_cake",
                "groundbait_base"}) {
            require(!PANTRY.containsKey(dead), dead + " is gone — one jar is the whole point");
        }

        // The base is the middle of both axes, or "raise it and lower it from here" is only half true.
        Component base = PANTRY.get(BASE_ID);
        require(base != null && base.diet() == null, "the base must exist and must not vote on the menu");
        require(Math.abs(base.nutrition() - 0.5) < 1e-9 && Math.abs(base.fraction() - 0.5) < 1e-9,
                "the base has to sit dead centre, or it can only be pushed one way");
        require(BASE.isBase() && BASE.variety() == 1, "a plain jar is one component, and it is the base");
        require(BASE.diets().isEmpty(), "a plain jar makes no claim about what is on the menu");

        // A diet has to be a bait the fish profiles can actually be asked about. Anything else silently
        // scores neutral for every species, which looks exactly like a working mapping and is not one.
        for (Component c : PANTRY.values()) {
            if (c.diet() == null) continue;
            require(PANTRY.containsKey(c.diet()) && PANTRY.get(c.diet()).diet() != null,
                    c.id() + " borrows the diet '" + c.diet() + "', which is not a bait in its own right");
        }

        // Ballast is the whole trick: half a mix of it halves the nutrition without changing the menu.
        GroundbaitMix pure = of(List.of(new Part("corn", 2)));
        GroundbaitMix half = of(List.of(new Part("corn", 2), new Part("groundbait_soil", 2)));
        require(pure != null && half != null, "corn mixes must stir");
        require(Math.abs(half.nutrition() - pure.nutrition() / 2) < 1e-9,
                "half soil must halve the nutrition, got " + half.nutrition());
        require(half.diets().equals(pure.diets()), "ballast must not change what is on the menu");
        require(half.variety() == 2, "ballast is still a different thing in the bowl");

        // Nothing but ballast is not groundbait, and must not reach the water as a fed spot.
        require(of(List.of(new Part("groundbait_soil", 5))) == null, "a jar of mud is not groundbait");
        require(of(List.of(new Part("groundbait_soil", 5), new Part("minecraft:clay_ball", 4))) == null,
                "mud and clay is still mud");
        require(of(List.of()) == null, "an empty recipe is not groundbait");
        require(of(List.of(new Part("gravel", 3))) == null, "an unknown component is not groundbait");

        // The base ALONE is groundbait — that is the difference between it and the old base item, and it
        // is the thing the player buys first.
        GroundbaitMix plain = of(List.of(new Part(BASE_ID, 1)));
        require(plain != null && plain.isBase(), "a plain jar of base must stir and must read as the base");

        // What you add decides what it becomes, on both axes and on the menu.
        GroundbaitMix fed = of(List.of(new Part(BASE_ID, 4), new Part("boilie", 4)));
        require(fed != null, "base + boilie must stir");
        require(fed.nutrition() > plain.nutrition(), "adding food must raise the nutrition");
        require(fed.fraction() > plain.fraction(), "adding boilies must coarsen the mix");
        require(fed.diets().containsKey("boilie"), "what you add is what is on the menu");
        GroundbaitMix lean = of(List.of(new Part(BASE_ID, 2), new Part("groundbait_soil", 6)));
        require(lean != null && lean.nutrition() < plain.nutrition() && lean.fraction() < plain.fraction(),
                "ballast must be able to push the base DOWN on both axes, or the middle is pointless");

        // Colour blends towards what there is most of, and a dye overrides most of the way.
        GroundbaitMix red = of(List.of(new Part("minecraft:beetroot", 4), new Part("bread", 1)));
        require(red != null, "beetroot mix must stir");
        require(((red.rgb() >> 16) & 0xFF) > (red.rgb() & 0xFF),
                "a beetroot mix must come out red, got " + Integer.toHexString(red.rgb()));
        require(red.dyed(0x1D1D21).rgb() != red.rgb(), "dye must change the colour");
        require(red.dyed(0x1D1D21).nutrition() == red.nutrition(), "dye must not change the food");
        require(red.dyed(0x1D1D21).signature().equals(red.signature()),
                "a dyed jar is the same groundbait — it must not fight its own undyed twin in the water");

        // Vanilla components have to be reachable by their full id, or the first mix waits on a farm.
        require(of(List.of(new Part("minecraft:wheat", 3))) != null, "vanilla wheat must stir");

        // A MIX IS THE BASE PLUS SOMETHING. Everything below is that one rule seen from every side.
        require(qualifiesAsMix(List.of(new Part(BASE_ID, 3), new Part("corn", 2)), false),
                "base + corn is a mix");
        require(qualifiesAsMix(List.of(new Part(BASE_ID, 1), new Part("groundbait_soil", 4)), false),
                "base + ballast is a mix — that is how you make it leaner");
        require(!qualifiesAsMix(List.of(new Part("corn", 3), new Part("pea", 2)), false),
                "corn and peas without the base is NOT groundbait — the base is what you build on");
        require(!qualifiesAsMix(List.of(new Part(BASE_ID, 5)), false),
                "the base alone is already groundbait, so stirring it with itself makes nothing new");
        require(!qualifiesAsMix(List.of(new Part("minecraft:wheat_seeds", 1), new Part("minecraft:bread", 1)), false),
                "seeds + bread is the recipe that MAKES the base, and must never read as a mix");
        require(qualifiesAsMix(List.of(new Part(BASE_ID, 1)), true),
                "a dye on the base is a mix — pointless, but legal");
        require(!qualifiesAsMix(List.of(new Part("corn", 3)), true),
                "a dye does not turn corn into groundbait either");
        require(MAX_COMPONENTS == MAX_ADDITIVES + 1,
                "the base takes one of the nine slots, so eight is what is left to add");

        // Two jars of the same recipe are the same groundbait; a different recipe is a different one.
        require(of(List.of(new Part("corn", 2), new Part("bread", 1))).signature()
                        .equals(of(List.of(new Part("bread", 1), new Part("corn", 2))).signature()),
                "the same spoons in a different order are the same mix");
        require(!of(List.of(new Part("corn", 2))).signature().equals(of(List.of(new Part("corn", 3))).signature()),
                "more corn is a different mix");

        // Spoons are clamped rather than trusted: the UI limits them, the NBT does not.
        GroundbaitMix over = of(List.of(new Part("corn", 99)));
        require(over != null && over.parts().get(0).spoons() == MAX_SPOONS, "spoons must clamp");
        // ...and the cap has to be at least a crafting grid, or the kept parts stop being what the
        // player put in and the payout has to be counted somewhere else. That is §groundbait-value.
        require(MAX_SPOONS >= 9 && MAX_COMPONENTS >= 9, "the cap must not be smaller than a 3x3 grid");

        // §groundbait-value: ballast dilutes, it does not multiply.
        require(jars(of(List.of(new Part("groundbait_soil", 8), new Part(BASE_ID, 1)))) == 1,
                "8 soil + 1 base is ONE jar, not nine");
        require(jars(of(List.of(new Part("corn", 3), new Part("groundbait_soil", 3)))) == 3,
                "food pays a jar a spoon, ballast only makes each jar leaner");
        require(jars(of(List.of(new Part("corn", 7), new Part("pea", 1), new Part("pearl_barley", 1)))) == 9,
                "an honest all-food grid still pays every spoon");
        require(jars(of(List.of(new Part(BASE_ID, 5), new Part("corn", 2)))) == 7,
                "the base is food you paid for: stirring it must not destroy it");
        // Anything that stirs at all has food in it, so it can never pay zero.
        require(jars(of(List.of(new Part("minecraft:wheat", 1), new Part("groundbait_soil", 8)))) >= 1,
                "a mix that stirs must pay at least one jar");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("GroundbaitMix self-check: " + message);
    }

}
