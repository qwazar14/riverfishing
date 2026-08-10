package com.riverfishing.groundbait;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §groundbait-mix (0.8.0): what a groundbait actually IS, once the player mixes it.
 *
 * <p>Until now a groundbait was one string — {@code powder}, {@code grain}, {@code pellet},
 * {@code cake} — and the bite engine asked one question: is that string in this fish's list. A mix
 * keeps that string, because <b>50 fish profiles reference it</b> and rewriting them would break every
 * save, and adds two numbers underneath that the string could never carry:
 *
 * <ul>
 *   <li>{@code nutrition} — how much the fish actually EAT. This is the axis the whole feature turns
 *       on: feed is not only an attractant, it is food, and a full fish stops biting.</li>
 *   <li>{@code fraction} — 0 is dust that clouds and disperses, 1 is grain that lies on the bottom.
 *       Small fish answer the cloud, big fish answer the grain.</li>
 * </ul>
 *
 * <p>Composition is in SPOONS, not fractions: each component gets a whole 1-5, and the share is that
 * over the total. Whole numbers read at a glance, survive three translations, and cannot drift into
 * "0.3333". The four ready-made items stay craftable as presets, so a player who does not want to mix
 * plays exactly as before.
 *
 * <p>Nothing here touches the world OR Minecraft. Feeding, decay and satiety live in FeedZoneData,
 * reading and writing a stack lives in {@link GroundbaitNbt}, and this class only answers "given these
 * spoons, what is the mix". That is deliberate: {@link #selfCheck()} is the thing that guards the
 * balance of the whole feature, and a check that needs a mapped game jar on the classpath is a check
 * nobody ever runs again. This one runs with plain `java`.
 */
public final class GroundbaitMix {

    /**
     * Most spoons of one component, and most components — both NINE, which is the size of a crafting
     * grid, because the grid is the real limit and inventing a smaller one caused a duplication bug.
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


    /**
     * What one component brings.
     *
     * <p>{@code pull} is the category it drags the mix towards — null for an inert filler, which is the
     * entire point of soil: volume and cloud, no calories and no identity.
     *
     * <p>{@code rgb} is what it stains the mix. Real groundbait is coloured on purpose — dark on clear
     * pressured water so a fed patch does not look alarming, bright in murk so fish can find it — and
     * beetroot really is one of the dyes people use. In the game it feeds {@link
     * com.riverfishing.engine.LureColor}, the same classifier a painted lure goes through, so the rule
     * a player learned on lures holds here too.
     */
    public record Component(String id, double nutrition, double fraction, String pull, int rgb) {}

    /**
     * The pantry. Every entry but soil and bran is an item the mod already has.
     *
     * <p>Numbers come from the real thing: breadcrumb is a cloud that feeds almost nothing, boilies are
     * dense bottom food, oil cake is the smell-and-oil middle. Soil is the dial the whole system needs
     * — on a hammered water half the mix is inert, so it pulls fish in without filling them.
     */
    public static final Map<String, Component> PANTRY = new LinkedHashMap<>();

    private static void put(String id, double nutrition, double fraction, String pull, int rgb) {
        PANTRY.put(id, new Component(id, nutrition, fraction, pull, rgb));
    }

    static {
        // ---- the four ready-made groundbaits, as INGREDIENTS ----
        // The shop base mix is a real thing an angler builds on rather than replaces, and letting the
        // old items into the pantry means 0.8.0 does not obsolete them: "base plus my own particles plus
        // ballast" is both the realistic workflow and the reason a player who already has these keeps
        // using them. Numbers are their preset numbers, so a jar of pure base fishes exactly as before.
        put("groundbait_powder", 0.25, 0.10, "powder", 0xE0DBC4);
        put("groundbait_grain", 0.80, 0.85, "grain", 0xEACF52);
        put("groundbait_pellet", 0.90, 0.95, "pellet", 0x8C6640);
        put("groundbait_cake", 0.55, 0.25, "cake", 0x8A7A3C);   // makuha: the smell-and-oil middle

        // ---- the mod's own, the ones a committed angler farms ----
        put("bread", 0.25, 0.10, "powder", 0xC9A46A);
        put("dough", 0.60, 0.30, "powder", 0xE0D2A8);
        put("corn", 0.85, 0.90, "grain", 0xE8C23A);
        put("pea", 0.80, 0.75, "grain", 0x7FA24A);
        put("pearl_barley", 0.70, 0.70, "grain", 0xD8CBA4);
        put("boilie", 0.95, 1.00, "pellet", 0x9A4B2A);
        put("maggot", 0.65, 0.55, "pellet", 0xEDE4C8);
        put("groundbait_soil", 0.00, 0.35, null, 0x6B5236);   // inert ballast: no calories, no identity, all volume

        // ---- the natural baits, chopped into the feed the way an angler really does ----
        // Categories were the one thing every reviewer agreed on; the numbers had to clear the
        // coarse-lean rule asserted in selfCheck, which is what kept the sea strip honest.
        put("worm", 0.70, 0.65, "pellet", 0x8F463E);          // chopped worm: meat, sinks, stays — the first pellet in the band pellet fish live in
        put("bloodworm", 0.30, 0.20, "powder", 0x8D2124);     // joker: the fine LIVE additive, and lean is the whole point of it on cold water
        put("chicken_liver", 0.80, 0.65, "cake", 0x732728);   // offal: cake had four fine components and no coarse one, and this is the fish liver is for
        put("fish_strip", 0.75, 0.80, "pellet", 0x976661);    // chum: a pellet IS ground fish, and cut fish lies on the deck rather than smearing away

        // ---- vanilla, so the first mix does not wait on a farm ----
        // Reach matters more than variety here: wheat and a potato are hour-one items, corn and boilies
        // are not. Without these the whole system only opens up late, which is the wrong shape for the
        // thing that is supposed to teach you how feeding works.
        put("minecraft:wheat", 0.55, 0.45, "grain", 0xC8A83C);
        put("minecraft:wheat_seeds", 0.30, 0.15, "powder", 0x93A857);
        put("minecraft:bread", 0.45, 0.20, "powder", 0xB07840);
        put("minecraft:potato", 0.60, 0.50, "grain", 0xD8B45C);
        put("minecraft:carrot", 0.45, 0.55, "grain", 0xE8801C);
        put("minecraft:beetroot", 0.40, 0.45, "grain", 0x8C1F35);   // the classic red dye, and it is food
        put("minecraft:sweet_berries", 0.35, 0.40, "grain", 0xA02030);
        put("minecraft:sugar", 0.20, 0.05, "powder", 0xF2F2F2);
        put("minecraft:cocoa_beans", 0.35, 0.25, "cake", 0x6B3A1E);
        // §groundbait-base: the sunflower lost its press recipe and kept its job — makuha is
        // now something you ADD to a mix. The rich, fine end of cake, and the reason cake exists.
        put("minecraft:sunflower", 0.60, 0.25, "cake", 0xB8912E);
        put("minecraft:pumpkin_seeds", 0.55, 0.35, "cake", 0xD8CE8C);
        put("minecraft:melon_seeds", 0.50, 0.30, "cake", 0xC8D8A0);
        put("minecraft:dried_kelp", 0.40, 0.30, "powder", 0x2E5E36); // sea-side green, and it smells of sea
        put("minecraft:clay_ball", 0.00, 0.55, null, 0x9AA7B4);      // the second ballast: binds, sinks, feeds nothing
    }

    /** The four ready-made items, as mixes. A preset is just a mix nobody had to stir. */
    public static final Map<String, GroundbaitMix> PRESETS = Map.of(
            "powder", preset("powder", 0.25, 0.10, 0xE0DBC4),
            "grain", preset("grain", 0.80, 0.85, 0xEACF52),
            "pellet", preset("pellet", 0.90, 0.95, 0x8C6640),
            "cake", preset("cake", 0.55, 0.25, 0xA8994D));

    private static GroundbaitMix preset(String category, double nutrition, double fraction, int rgb) {
        return new GroundbaitMix(List.of(), category, nutrition, fraction, rgb);
    }

    private final List<Part> parts;
    private final String category;
    private final double nutrition;
    private final double fraction;
    private final int rgb;

    public record Part(String id, int spoons) {}

    private GroundbaitMix(List<Part> parts, String category, double nutrition, double fraction, int rgb) {
        this.parts = List.copyOf(parts);
        this.category = category;
        this.nutrition = nutrition;
        this.fraction = fraction;
        this.rgb = rgb;
    }

    public List<Part> parts() { return parts; }
    public String category() { return category; }
    public double nutrition() { return nutrition; }
    public double fraction() { return fraction; }
    /** The mix's own colour, before any dye. */
    public int rgb() { return rgb; }
    public boolean isPreset() { return parts.isEmpty(); }

    /** The same mix stained by a dye — what the bench does when the player drops a dye in. */
    public GroundbaitMix dyed(int dyeRgb) {
        return new GroundbaitMix(parts, category, nutrition, fraction, blend(rgb, dyeRgb, 0.6));
    }

    /**
     * The same mix wearing a colour it would not have computed for itself.
     *
     * <p>Only {@link GroundbaitNbt} needs this, to put a stored dye back on a mix that has just been
     * stirred from its parts. A dye is the one thing about a jar that cannot be re-derived from what is
     * in it, so it is the one thing that has to be written down.
     */
    public GroundbaitMix recoloured(int rgb) {
        return new GroundbaitMix(parts, category, nutrition, fraction, rgb & 0xFFFFFF);
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
     * <p>Nutrition and fraction are the spoon-weighted mean over EVERY component, soil included — that
     * is how soil dilutes: it contributes zeros to the average, so a mix that is half soil is half as
     * nourishing. The category is the pull with the most spoons behind it; soil never votes, because
     * ballast should not decide what the mix smells of.
     *
     * @return null if the recipe is empty or every spoon in it is inert — a jar of wet mud is not
     *     groundbait, and letting it through would give the bite engine a category it cannot match.
     */
    public static GroundbaitMix of(List<Part> recipe) {
        double totalSpoons = 0, nutrition = 0, fraction = 0, red = 0, green = 0, blue = 0;
        Map<String, Integer> pulls = new LinkedHashMap<>();
        List<Part> kept = new ArrayList<>();

        for (Part p : recipe) {
            Component c = PANTRY.get(p.id());
            if (c == null || p.spoons() <= 0) continue;
            int spoons = Math.min(p.spoons(), MAX_SPOONS);
            kept.add(new Part(p.id(), spoons));
            totalSpoons += spoons;
            nutrition += c.nutrition() * spoons;
            fraction += c.fraction() * spoons;
            // Colour blends by spoon like everything else, and ballast DOES tint: a mix half soil really
            // does come out the colour of soil, which is exactly why a lean town mix looks unalarming.
            red += ((c.rgb() >> 16) & 0xFF) * spoons;
            green += ((c.rgb() >> 8) & 0xFF) * spoons;
            blue += (c.rgb() & 0xFF) * spoons;
            if (c.pull() != null) {
                pulls.merge(c.pull(), spoons, Integer::sum);
            }
            if (kept.size() >= MAX_COMPONENTS) break;
        }
        if (totalSpoons == 0 || pulls.isEmpty()) {
            return null;
        }

        // Most spoons wins. On a tie the richer category takes it: two spoons of corn against two of
        // breadcrumb is a grain mix with a cloud, not a powder mix with grain in it.
        String category = null;
        int best = -1;
        for (Map.Entry<String, Integer> e : pulls.entrySet()) {
            boolean richer = category != null
                    && PRESETS.get(e.getKey()).nutrition() > PRESETS.get(category).nutrition();
            if (e.getValue() > best || (e.getValue() == best && richer)) {
                best = e.getValue();
                category = e.getKey();
            }
        }
        int rgb = ((int) Math.round(red / totalSpoons) << 16)
                | ((int) Math.round(green / totalSpoons) << 8)
                | (int) Math.round(blue / totalSpoons);
        return new GroundbaitMix(kept, category, nutrition / totalSpoons, fraction / totalSpoons, rgb);
    }

    /**
     * How many jars this mix pays out: ONE PER SPOON OF FOOD.
     *
     * <p>Ballast is what a mix is diluted WITH, not what it is made OF, so soil and clay pay nothing.
     * They still change the nutrition, the fraction and the colour of every jar; they no longer change
     * how many jars there are. Counting slots instead is how eight soil and one jar of pellet became
     * nine jars of pellet for two dirt.
     *
     * <p>Read off the KEPT parts — the same list that decided the category, the nutrition, the fraction
     * and the colour. One list, one read: there is no second number here that could disagree with a
     * first one, which is the whole point, because that disagreement is this feature's recurring bug.
     *
     * <p>The four ready-made groundbaits are food, so a jar stirred into a mix pays for exactly one jar
     * out. That is necessary but NOT sufficient to stop the recipe printing its own ingredient — see the
     * stamped-jar guard in GroundbaitMixRecipe, which is what actually closes the loop.
     */
    public static int jars(GroundbaitMix mix) {
        int jars = 0;
        for (Part p : mix.parts()) {
            Component c = PANTRY.get(p.id());
            if (c != null && c.pull() != null) jars += p.spoons();
        }
        return jars;
    }

    /**
     * Is this a MIX, or is it the one plain recipe?
     *
     * <p>§groundbait-base left exactly one craftable groundbait — powder, from bread and wheat — so a
     * grid is a mix unless it IS that pair. It used to take three components to qualify, because three
     * basic recipes could be stolen by a smaller grid; with two of them gone that bar only stood between
     * the player and the pantry, and mixing is now the only road to grain, pellet and cake.
     *
     * <p>Two spoons each of bread and wheat is a mix, and correctly so: the vanilla shapeless recipe
     * matches one of each and nothing else, so there is nothing left to collide with.
     */
    public static boolean qualifiesAsMix(List<Part> recipe, boolean dyed) {
        if (dyed) return true;
        int distinct = 0, breads = 0, wheats = 0;
        for (Part p : recipe) {
            if (!PANTRY.containsKey(p.id()) || p.spoons() <= 0) continue;
            distinct++;
            if ("minecraft:bread".equals(p.id())) breads = p.spoons();
            else if ("minecraft:wheat".equals(p.id())) wheats = p.spoons();
        }
        if (distinct == 2 && breads == 1 && wheats == 1) return false;   // that is the powder recipe
        return distinct >= 2;
    }

    /** "groundbait_grain" -> "grain"; anything else unchanged. */
    private static String stripJar(String id) {
        return id.startsWith("groundbait_") ? id.substring("groundbait_".length()) : id;
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
            require(c.pull() == null || PRESETS.containsKey(c.pull()), c.id() + " pulls to no category");
            require((c.rgb() & ~0xFFFFFF) == 0, c.id() + " colour is not a plain 24-bit rgb");
        }

        // §groundbait-baits: THE COARSE-LEAN RULE. Nutrition is pure cost, so a component that is coarse
        // AND lean at the same time hands the player leanness for free and deletes soil and clay as the
        // dial the whole feature turns on. The pantry obeyed this before anyone wrote it down; the
        // tightest margin is minecraft:carrot at +0.05. A proposed sea strip at 0.45/0.85 broke it and
        // fed a catfish a perfect swim at under half the satiety of anything buildable today.
        for (Component c : PANTRY.values()) {
            if (c.pull() == null) continue;                    // ballast is meant to be coarse and free
            require(c.nutrition() >= c.fraction() - 0.15,
                    c.id() + " is coarse and lean at once (" + c.nutrition() + " / " + c.fraction()
                            + ") — that is leanness without paying for it, which is what ballast is for");
        }

        // Soil is the whole trick: half a mix of it halves the nutrition without changing the identity.
        GroundbaitMix pure = of(List.of(new Part("corn", 2)));
        GroundbaitMix half = of(List.of(new Part("corn", 2), new Part("groundbait_soil", 2)));
        require(pure != null && half != null, "corn mixes must stir");
        require(pure.category().equals("grain") && half.category().equals("grain"),
                "soil must not change what a mix is");
        require(Math.abs(half.nutrition() - pure.nutrition() / 2) < 1e-9,
                "half soil must halve the nutrition, got " + half.nutrition());

        // A tie goes to the richer category, so a cloud with grain in it still fishes as grain.
        GroundbaitMix tie = of(List.of(new Part("corn", 2), new Part("bread", 2)));
        require(tie != null && tie.category().equals("grain"), "tie must go to the richer category");

        // Nothing but ballast is not groundbait, and must not reach the bite engine as a category.
        require(of(List.of(new Part("groundbait_soil", 5))) == null, "a jar of mud is not groundbait");
        require(of(List.of()) == null, "an empty recipe is not groundbait");
        require(of(List.of(new Part("gravel", 3))) == null, "an unknown component is not groundbait");

        // Colour blends towards what there is most of, and a dye overrides most of the way.
        GroundbaitMix red = of(List.of(new Part("minecraft:beetroot", 4), new Part("bread", 1)));
        require(red != null, "beetroot mix must stir");
        require(((red.rgb() >> 16) & 0xFF) > (red.rgb() & 0xFF),
                "a beetroot mix must come out red, got " + Integer.toHexString(red.rgb()));
        require(red.dyed(0x1D1D21).rgb() != red.rgb(), "dye must change the colour");
        require(red.dyed(0x1D1D21).nutrition() == red.nutrition(), "dye must not change the food");
        require(red.dyed(0x1D1D21).category().equals(red.category()), "dye must not change the category");

        // Vanilla components have to be reachable by their full id, or the first mix waits on a farm.
        require(of(List.of(new Part("minecraft:wheat", 3))) != null, "vanilla wheat must stir");

        // §groundbait-base: ONE craftable groundbait is left, so the mix rule has exactly one recipe to
        // avoid swallowing — and everything else with two or more components is fair game.
        require(!qualifiesAsMix(List.of(new Part("minecraft:bread", 1), new Part("minecraft:wheat", 1)), false),
                "bread + wheat is the powder recipe, not a mix");
        require(qualifiesAsMix(List.of(new Part("minecraft:bread", 2), new Part("minecraft:wheat", 2)), false),
                "TWO each is a mix — the vanilla recipe only matches one of each");
        require(qualifiesAsMix(List.of(new Part("minecraft:wheat", 1), new Part("minecraft:wheat_seeds", 1)), false),
                "wheat + seeds is a MIX now that the grain recipe is gone — and it still makes grain");
        require("grain".equals(of(List.of(new Part("minecraft:wheat", 1), new Part("minecraft:wheat_seeds", 1))).category()),
                "the old grain pair must still read as grain, or the muscle memory lies");
        require(qualifiesAsMix(List.of(new Part("corn", 5), new Part("pea", 3), new Part("pearl_barley", 2)), false),
                "three components is a mix");
        // The sunflower kept its job when its press recipe went: it is how you reach cake by hand.
        require("cake".equals(of(List.of(new Part("minecraft:sunflower", 3), new Part("bread", 1))).category()),
                "a sunflower-led mix must read as cake");
        require(qualifiesAsMix(List.of(new Part("corn", 3), new Part("groundbait_soil", 4)), false),
                "anything with ballast is a mix");
        require(qualifiesAsMix(List.of(new Part("groundbait_grain", 2), new Part("corn", 3)), false),
                "a ready-made jar as a base is a mix");
        require(qualifiesAsMix(List.of(new Part("minecraft:bread", 1), new Part("minecraft:wheat", 1)), true),
                "a dye makes anything a mix");

        // Spoons are clamped rather than trusted: the UI limits them, the NBT does not.
        GroundbaitMix over = of(List.of(new Part("corn", 99)));
        require(over != null && over.parts().get(0).spoons() == MAX_SPOONS, "spoons must clamp");
        // ...and the cap has to be at least a crafting grid, or the kept parts stop being what the
        // player put in and the payout has to be counted somewhere else. That is §groundbait-value.
        require(MAX_SPOONS >= 9 && MAX_COMPONENTS >= 9, "the cap must not be smaller than a 3x3 grid");

        // §groundbait-value: ballast dilutes, it does not multiply.
        require(jars(of(List.of(new Part("groundbait_soil", 8), new Part("groundbait_pellet", 1)))) == 1,
                "8 soil + 1 pellet is ONE jar, not nine");
        require(jars(of(List.of(new Part("corn", 3), new Part("groundbait_soil", 3)))) == 3,
                "food pays a jar a spoon, ballast only makes each jar leaner");
        require(jars(of(List.of(new Part("corn", 7), new Part("pea", 1), new Part("pearl_barley", 1)))) == 9,
                "an honest all-food grid still pays every spoon");
        require(jars(of(List.of(new Part("corn", 8), new Part("groundbait_soil", 1)))) == 8,
                "eight spoons of corn must be recorded as eight and paid as eight");
        // Anything that stirs at all has a pulling component in it, so it can never pay zero.
        require(jars(of(List.of(new Part("minecraft:wheat", 1), new Part("groundbait_soil", 8)))) >= 1,
                "a mix that stirs must pay at least one jar");
        // A ready-made jar must count as food, or stirring one in would pay out more than it cost.
        for (String cat : PRESETS.keySet()) {
            require(PANTRY.get("groundbait_" + cat).pull() != null,
                    cat + " jar must count as food, or a jar in the grid pays for nothing");
        }

        // The presets have to stay inside the same ranges, or the compatibility path lies.
        for (Map.Entry<String, GroundbaitMix> e : PRESETS.entrySet()) {
            GroundbaitMix m = e.getValue();
            require(m.isPreset() && m.category().equals(e.getKey()), e.getKey() + " preset mislabelled");
            require(m.nutrition() >= 0 && m.nutrition() <= 1 && m.fraction() >= 0 && m.fraction() <= 1,
                    e.getKey() + " preset out of range");
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("GroundbaitMix self-check: " + message);
    }

}
