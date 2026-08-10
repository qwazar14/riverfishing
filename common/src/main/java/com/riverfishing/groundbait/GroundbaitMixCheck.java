package com.riverfishing.groundbait;

import java.util.List;

/**
 * Runs {@link GroundbaitMix#selfCheck()} outside the game, and prints what the reference mixes from the
 * design come out as.
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

    /** The waters the design is built around, so a tuning change shows its effect immediately. */
    private static final List<Recipe> REFERENCE = List.of(
            new Recipe("shop jar, as bought", "the base alone: neutral, helps a little",
                    List.of(new GroundbaitMix.Part(GroundbaitMix.BASE_ID, 1))),
            new Recipe("hammered town water", "lean and clouding, half ballast",
                    List.of(new GroundbaitMix.Part(GroundbaitMix.BASE_ID, 3),
                            new GroundbaitMix.Part("bloodworm", 1),
                            new GroundbaitMix.Part("groundbait_soil", 4))),
            new Recipe("stocked pond", "imitates the pellets they are fed",
                    List.of(new GroundbaitMix.Part("boilie", 4),
                            new GroundbaitMix.Part("corn", 2))),
            new Recipe("bream, the classic", "grain and chopped worm over the base",
                    List.of(new GroundbaitMix.Part(GroundbaitMix.BASE_ID, 3),
                            new GroundbaitMix.Part("pearl_barley", 2),
                            new GroundbaitMix.Part("worm", 2),
                            new GroundbaitMix.Part("maggot", 1))),
            new Recipe("wild lake", "all particle, lies on the bottom",
                    List.of(new GroundbaitMix.Part("corn", 5),
                            new GroundbaitMix.Part("pea", 3),
                            new GroundbaitMix.Part("pearl_barley", 2))),
            new Recipe("first mix, vanilla only", "wheat and a beetroot, hour one",
                    List.of(new GroundbaitMix.Part("minecraft:wheat", 3),
                            new GroundbaitMix.Part("minecraft:beetroot", 2),
                            new GroundbaitMix.Part("minecraft:wheat_seeds", 1))));

    private record Recipe(String name, String intent, List<GroundbaitMix.Part> parts) {}

    public static void main(String[] args) {
        GroundbaitMix.selfCheck();

        // Colour prints as hex, not as a LureColor class: that classifier lives in the engine package
        // and drags Minecraft in with it, which would cost this runner the one property that makes it
        // worth having. Tuning wants the hex anyway — the class is one of three buckets it falls in.
        System.out.printf("%-24s %9s %9s %4s %8s %6s   %s%n",
                "mix", "nutrition", "fraction", "var", "colour", "jars", "menu");
        System.out.println("-".repeat(104));
        for (Recipe r : REFERENCE) {
            GroundbaitMix m = GroundbaitMix.of(r.parts());
            if (m == null) {
                throw new IllegalStateException("reference mix does not stir: " + r.name());
            }
            System.out.printf("%-24s %9.3f %9.3f %4d  #%06X %6d   %s%n",
                    r.name(), m.nutrition(), m.fraction(), m.variety(), m.rgb(),
                    GroundbaitMix.jars(m), m.diets().isEmpty() ? "(says nothing)" : m.diets());
        }
        System.out.println("-".repeat(104));
        System.out.printf("pantry: %d components, %d of them name a diet%n",
                GroundbaitMix.PANTRY.size(),
                GroundbaitMix.PANTRY.values().stream().filter(c -> c.diet() != null).count());
        System.out.println("self-check passed");
    }
}
