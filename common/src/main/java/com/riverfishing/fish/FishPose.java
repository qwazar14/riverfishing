package com.riverfishing.fish;

import net.minecraft.util.Mth;

import java.util.Set;

/**
 * §fish-pose (0.7.0): which fish lie flat, and how far to lean them so you can still see one.
 *
 * <p>Almost every fish in the mod is drawn side-on, so a sprite standing upright in the water is the right
 * picture. A flatfish is not: a flounder, a halibut and a ray are drawn from ABOVE — the sprite is the
 * broad, eyed face of a fish whose whole body is horizontal. Rendered upright they read as a bream
 * standing on its edge, which is the one thing a flatfish never does.
 *
 * <p>So they are laid flat. The catch is that a perfectly flat sprite seen from the bank is a line one
 * pixel high, and "the flounder is invisible" would be a fair report even though the anatomy is right, so
 * the lay leans towards the viewer by however much the viewing angle needs. Look down on one from a boat
 * and it is flat; crouch at the water's edge and it tips up enough to be a fish. That is a courtesy of the
 * renderer, not a claim about the animal.
 */
public final class FishPose {
    /**
     * The flatfish. Add a species here and it lies down everywhere at once — in open water, in the
     * aquarium and on the ground where you dropped it.
     */
    private static final Set<String> FLAT = Set.of("flounder", "halibut", "ray");

    /** How far a flat fish may lean towards a viewer who is not above it, in degrees. */
    private static final float MAX_LEAN = 55f;

    private FishPose() {}

    public static boolean isFlat(String speciesPath) {
        return FLAT.contains(speciesPath);
    }

    /**
     * The pitch to apply about the sprite's own X axis after it has been turned to face the viewer, in
     * degrees. −90 lays the sprite perfectly flat, face up; 0 leaves it upright.
     *
     * @param elevationDeg how high the viewer sits above the fish, 0 (level with it) to 90 (straight over)
     */
    public static float lay(float elevationDeg) {
        return -90f + Mth.clamp(90f - elevationDeg, 0f, MAX_LEAN);
    }

    /** The lay for a fixed viewing angle — the aquarium, where the viewer is always out in front. */
    public static float layFixed() {
        return lay(0f);
    }
}
