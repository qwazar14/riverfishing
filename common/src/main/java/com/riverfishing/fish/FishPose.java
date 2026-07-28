package com.riverfishing.fish;

import java.util.Set;

/**
 * §fish-pose (0.7.0): which fish lie flat.
 *
 * <p>Almost every fish in the mod is drawn side-on, so a sprite standing upright in the water is the right
 * picture. A flatfish is not: a flounder, a halibut and a ray are drawn from ABOVE — the sprite is the
 * broad, eyed face of a fish whose whole body is horizontal. Rendered upright they read as a bream
 * standing on its edge, which is the one thing a flatfish never does.
 *
 * <p>Flat means FLAT — parallel to the bottom they travel along. The first cut leaned them towards the
 * viewer so they could never go edge-on, and it read as exactly what it was: a fish at a wrong angle. A
 * flounder seen from the side genuinely is almost nothing to look at, and that is the correct picture —
 * you look DOWN at a flatfish, which is also where it lives.
 */
public final class FishPose {
    /**
     * The flatfish. Add a species here and it lies down everywhere at once — in open water, in the
     * aquarium and on the ground where you dropped it.
     */
    private static final Set<String> FLAT = Set.of("flounder", "halibut", "ray");

    private FishPose() {}

    public static boolean isFlat(String speciesPath) {
        return FLAT.contains(speciesPath);
    }

    /**
     * The pitch about the sprite's own X axis, applied after it has been turned to face the viewer, that
     * lays it flat and face up.
     */
    public static float lay() {
        return -90f;
    }
}
