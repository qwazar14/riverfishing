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

    /** Most spoons of one component. Five keeps the arithmetic in the head and the UI on one row. */
    public static final int MAX_SPOONS = 5;
    /** Most distinct components in one mix. */
    public static final int MAX_COMPONENTS = 5;


    /**
     * What one component brings. {@code pull} is the category it drags the mix towards — null for an
     * inert filler, which is the entire point of soil: volume and cloud, no calories and no identity.
     */
    public record Component(String id, double nutrition, double fraction, String pull) {}

    /**
     * The pantry. Every entry but soil and bran is an item the mod already has.
     *
     * <p>Numbers come from the real thing: breadcrumb is a cloud that feeds almost nothing, boilies are
     * dense bottom food, oil cake is the smell-and-oil middle. Soil is the dial the whole system needs
     * — on a hammered water half the mix is inert, so it pulls fish in without filling them.
     */
    public static final Map<String, Component> PANTRY = new LinkedHashMap<>();

    private static void put(String id, double nutrition, double fraction, String pull) {
        PANTRY.put(id, new Component(id, nutrition, fraction, pull));
    }

    static {
        put("bread", 0.25, 0.10, "powder");
        put("bran", 0.15, 0.05, "powder");
        put("dough", 0.60, 0.30, "powder");
        put("oil_cake", 0.55, 0.25, "cake");
        put("corn", 0.85, 0.90, "grain");
        put("pea", 0.80, 0.75, "grain");
        put("pearl_barley", 0.70, 0.70, "grain");
        put("boilie", 0.95, 1.00, "pellet");
        put("maggot", 0.65, 0.55, "pellet");
        put("soil", 0.00, 0.35, null);      // inert: the ballast that separates attracting from feeding
    }

    /** The four ready-made items, as mixes. A preset is just a mix nobody had to stir. */
    public static final Map<String, GroundbaitMix> PRESETS = Map.of(
            "powder", preset("powder", 0.25, 0.10),
            "grain", preset("grain", 0.80, 0.85),
            "pellet", preset("pellet", 0.90, 0.95),
            "cake", preset("cake", 0.55, 0.25));

    private static GroundbaitMix preset(String category, double nutrition, double fraction) {
        return new GroundbaitMix(List.of(), category, nutrition, fraction);
    }

    private final List<Part> parts;
    private final String category;
    private final double nutrition;
    private final double fraction;

    public record Part(String id, int spoons) {}

    private GroundbaitMix(List<Part> parts, String category, double nutrition, double fraction) {
        this.parts = List.copyOf(parts);
        this.category = category;
        this.nutrition = nutrition;
        this.fraction = fraction;
    }

    public List<Part> parts() { return parts; }
    public String category() { return category; }
    public double nutrition() { return nutrition; }
    public double fraction() { return fraction; }
    public boolean isPreset() { return parts.isEmpty(); }

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
        double totalSpoons = 0, nutrition = 0, fraction = 0;
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
        return new GroundbaitMix(kept, category, nutrition / totalSpoons, fraction / totalSpoons);
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
        }

        // Soil is the whole trick: half a mix of it halves the nutrition without changing the identity.
        GroundbaitMix pure = of(List.of(new Part("corn", 2)));
        GroundbaitMix half = of(List.of(new Part("corn", 2), new Part("soil", 2)));
        require(pure != null && half != null, "corn mixes must stir");
        require(pure.category().equals("grain") && half.category().equals("grain"),
                "soil must not change what a mix is");
        require(Math.abs(half.nutrition() - pure.nutrition() / 2) < 1e-9,
                "half soil must halve the nutrition, got " + half.nutrition());

        // A tie goes to the richer category, so a cloud with grain in it still fishes as grain.
        GroundbaitMix tie = of(List.of(new Part("corn", 2), new Part("bread", 2)));
        require(tie != null && tie.category().equals("grain"), "tie must go to the richer category");

        // Nothing but ballast is not groundbait, and must not reach the bite engine as a category.
        require(of(List.of(new Part("soil", 5))) == null, "a jar of mud is not groundbait");
        require(of(List.of()) == null, "an empty recipe is not groundbait");
        require(of(List.of(new Part("gravel", 3))) == null, "an unknown component is not groundbait");

        // Spoons are clamped rather than trusted: the UI limits them, the NBT does not.
        GroundbaitMix over = of(List.of(new Part("corn", 99)));
        require(over != null && over.parts().get(0).spoons() == MAX_SPOONS, "spoons must clamp");

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

    private GroundbaitMix() { this(List.of(), "powder", 0, 0); }
}
