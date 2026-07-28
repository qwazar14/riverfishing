package com.riverfishing.fishing;

import com.riverfishing.network.FightInputPacket;
import net.minecraft.util.RandomSource;

/**
 * §fight-course (0.7.0): which way a fish is going, and what holding the rod against it means.
 *
 * <p>The fight was one-dimensional — tension up, progress up, and a forty-minute catfish differed from a
 * forty-second roach only in how long the numbers took. A run now has a DIRECTION, and the answer is to
 * put the rod the other way. That turns the same seven fight patterns into readable sentences without a
 * line of new content: a marlin that greyhounds is a fish that keeps coming UP, and a tuna that sounds is
 * one long pull DOWN.
 *
 * <p>The input is the movement keys, not the camera. Aim was tried first because the server already knows
 * it for free, and it was wrong on its own terms: countering a left-hand run meant turning thirty degrees
 * away from the water, so the mechanic asked the player to stop watching the fight — and the leaning rod
 * swung off screen with them. WASD costs one small packet and leaves the camera where it belongs.
 */
public enum FightCourse {
    /** No run in progress. */
    NONE,
    /** The fish tracks to the angler's left; hold the rod to the RIGHT. */
    LEFT,
    /** …and the mirror of it. */
    RIGHT,
    /** It has gone deep. LIFT — look up and get its head up. */
    DOWN,
    /** It is coming up to jump. Rod DOWN — a low rod is what stops a fish leaping off the hook. */
    UP;

    /**
     * Credit for standing there doing nothing. Not zero — a player who has not worked out the mechanic yet
     * still lands fish, just slowly — and not close to one, or the mechanic may as well not exist.
     */
    private static final float IDLE = 0.4f;

    public boolean isRun() {
        return this != NONE;
    }

    /** The lang key for the boss bar, so the player is told the course rather than left to infer it. */
    public String key() {
        return "message.riverfishing.course_" + name().toLowerCase();
    }

    /** The key that answers this course — {@link FightInputPacket}'s constants. */
    public byte counter() {
        return switch (this) {
            case LEFT -> FightInputPacket.PULL_RIGHT;  // it goes left, you lean on it from the right
            case RIGHT -> FightInputPacket.PULL_LEFT;
            case DOWN -> FightInputPacket.LIFT;        // S — pull back and get its head up
            case UP -> FightInputPacket.PUSH;          // W — rod down, or it throws the hook in the air
            default -> FightInputPacket.NONE;
        };
    }

    /**
     * How well the player is pulling against this course: 1 on the answering key, {@link #IDLE} for hands
     * off, 0 for any other key — including, deliberately, the one that pulls the same way the fish is
     * already going.
     */
    public float alignment(byte pull) {
        if (this == NONE) return 1f;
        if (pull == counter()) return 1f;
        return pull == FightInputPacket.NONE ? IDLE : 0f;
    }

    /**
     * The course of the next run, from the species' fight pattern. This is the whole point of doing it
     * this way: the seven patterns already in the profiles become direction scripts rather than being
     * replaced by anything.
     *
     * @param runIndex which run of the fight this is, counting from zero
     */
    public static FightCourse forPattern(String pattern, int runIndex, RandomSource rng) {
        String p = pattern == null ? "" : pattern;
        FightCourse side = rng.nextBoolean() ? LEFT : RIGHT;
        return switch (p) {
            // Straight down and stay there — the deep-water slog. Nothing but lifting will move it.
            case "sounding" -> runIndex % 4 == 3 ? side : DOWN;
            // Jump, jump, jump. A greyhounding fish is trying to throw the hook in the air.
            case "greyhounding" -> runIndex % 3 == 2 ? side : UP;
            // Never stops, never the same way twice: alternating sides with no rest between.
            case "relentless" -> runIndex % 2 == 0 ? LEFT : RIGHT;
            // All fire early, then it sulks deep once it is beaten.
            case "active_then_passive" -> runIndex < 2 ? side : DOWN;
            // Short, sharp, unpredictable.
            case "burst" -> runIndex % 3 == 2 ? DOWN : side;
            // A hard fish that mixes a dive into its sidework.
            case "aggressive" -> runIndex % 4 == 2 ? DOWN : side;
            // Long, heavy, sideways — the fish that just leans on you.
            case "steady" -> side;
            default -> side;
        };
    }
}
