package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
 * <p>The input is the player's own aim, which the server already knows to the degree — no new keys, no new
 * packets, and it is what an angler actually does: you lead a running fish with the rod tip, and you lift
 * the rod on one that has gone deep. Alignment is a slope rather than a switch, so half-right is
 * half-rewarded and the fight never feels like a quiz.
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

    /** How far off the line the rod has to be held for full credit, in degrees. */
    private static final float FULL_YAW = 45f;
    private static final float FULL_PITCH = 40f;

    public boolean isRun() {
        return this != NONE;
    }

    /** The lang key for the boss bar, so the player is told the course rather than left to infer it. */
    public String key() {
        return "message.riverfishing.course_" + name().toLowerCase();
    }

    /**
     * How well the player is holding against this course, 0..1.
     *
     * <p>Measured from where they are AIMING relative to the line: yaw for a fish tracking sideways, pitch
     * for one that has sounded or is about to jump. Straight down the line is zero credit for a sideways
     * run, which is right — a rod pointed at a running fish is a rod doing nothing.
     */
    public float alignment(ServerPlayer sp, BlockPos target) {
        if (this == NONE) return 1f;
        if (this == DOWN) return Mth.clamp(-sp.getXRot() / FULL_PITCH, 0f, 1f);
        if (this == UP) return Mth.clamp(sp.getXRot() / FULL_PITCH, 0f, 1f);
        // Yaw from the player to the fish, then how far off it they are holding. Positive delta is to the
        // player's right, because Minecraft's yaw grows clockwise from above.
        double dx = target.getX() + 0.5 - sp.getX();
        double dz = target.getZ() + 0.5 - sp.getZ();
        float bearing = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
        float delta = Mth.wrapDegrees(sp.getYRot() - bearing);
        return Mth.clamp((this == LEFT ? delta : -delta) / FULL_YAW, 0f, 1f);
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
