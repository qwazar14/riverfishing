package com.riverfishing.groundbait;

import java.util.List;

/**
 * Runs {@link GroundbaitMix#selfCheck()} outside the game, and prints what the three reference mixes
 * from the design come out as.
 *
 * <pre>
 *   gradlew :common:compileJava
 *   java -cp common/build/classes/java/main com.riverfishing.groundbait.GroundbaitMixCheck
 * </pre>
 *
 * <p>No Minecraft on that classpath, and that is the point: the pantry numbers decide the balance of
 * the whole feature, so checking them has to be cheaper than launching a world. Tuning a component is
 * an edit and a five-second run.
 */
public final class GroundbaitMixCheck {

    private GroundbaitMixCheck() {}

    /** The three waters the design is built around, so a tuning change shows its effect immediately. */
    private static final List<Recipe> REFERENCE = List.of(
            new Recipe("hammered town water", "lean, clouding, half ballast",
                    List.of(new GroundbaitMix.Part("corn", 3),
                            new GroundbaitMix.Part("bread", 1),
                            new GroundbaitMix.Part("soil", 4))),
            new Recipe("stocked pond", "imitates the pellets they are fed",
                    List.of(new GroundbaitMix.Part("boilie", 4),
                            new GroundbaitMix.Part("corn", 2))),
            new Recipe("wild lake", "all particle, lies on the bottom",
                    List.of(new GroundbaitMix.Part("corn", 5),
                            new GroundbaitMix.Part("pea", 3),
                            new GroundbaitMix.Part("pearl_barley", 2))));

    private record Recipe(String name, String intent, List<GroundbaitMix.Part> parts) {}

    public static void main(String[] args) {
        GroundbaitMix.selfCheck();

        System.out.printf("%-22s %-8s %9s %9s   %s%n",
                "mix", "category", "nutrition", "fraction", "intent");
        System.out.println("-".repeat(78));
        for (Recipe r : REFERENCE) {
            GroundbaitMix m = GroundbaitMix.of(r.parts());
            if (m == null) {
                throw new IllegalStateException("reference mix does not stir: " + r.name());
            }
            System.out.printf("%-22s %-8s %9.3f %9.3f   %s%n",
                    r.name(), m.category(), m.nutrition(), m.fraction(), r.intent());
        }
        System.out.println("-".repeat(78));
        System.out.printf("presets: %s%n", GroundbaitMix.PRESETS.keySet());
        System.out.println("self-check passed");
    }
}
