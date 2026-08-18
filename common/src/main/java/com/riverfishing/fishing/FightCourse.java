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
 * <p>The input is four dedicated keys (§fight-keys, 0.7.1), arrows by default and rebindable in the
 * vanilla Controls screen. It is <em>not</em> the camera, and it is no longer the movement keys.
 *
 * <h2>Two rejected inputs, so nobody re-derives them</h2>
 *
 * <p><b>Camera aim (rejected 0.7.0).</b> Tried first because the server already knows it for free, and
 * wrong on its own terms: countering a left-hand run meant turning thirty degrees away from the water, so
 * the mechanic asked the player to stop watching the fight — and the leaning rod swung off screen with
 * them.
 *
 * <p><b>The movement keys (rejected 0.7.1).</b> They worked, and they cost one small packet, but WASD
 * ended up carrying three meanings at once: answer the course, work {@code §fight-footwork}, and
 * physically relocate you. The third was a side effect this mechanic never wanted — it only ever read key
 * STATE — and it put players in the water off piers. It also made the fight argue with itself, since S
 * both counters a DOWN run and backs you off the hook (a leg-pump, paid twice) while W answers an UP run
 * by walking you at the fish (slack, punished).
 *
 * <p><b>An analogue mouse pull, Fishing Planet style (designed and shelved, 0.7.1).</b> Asked for by
 * Foxsy1798. Designed properly before being turned down; the reasons are all about the platform, not the
 * idea:
 *
 * <ul>
 *   <li>There is <em>no portable raw-mouse read</em>. {@code MouseHandler.getXVelocity()/getYVelocity()}
 *       are Forge/NeoForge patches and absent from vanilla; {@code accumulatedDX/DY} are private on all
 *       four lines. {@code turnPlayer} is public and no-arg on 1.20.1 but private and driven per FRAME
 *       from {@code handleAccumulatedMovement()} on 1.21.1 and 26.x — so it would be the mod's first
 *       client-input mixin, in two bodies, on the class Mojang churns hardest.</li>
 *   <li>A {@code Screen} is not an option: it replaces the HUD render path, which kills the boss bar and
 *       the §pump-reel cue at the exact moment they matter, and it zeroes movement input, which kills
 *       §fight-footwork outright.</li>
 *   <li>{@code releaseMouse()} frees the cursor, but vanilla re-grabs on the next click and swallows it —
 *       and that click is the reel pulse.</li>
 *   <li>Right-drag collides with {@code RodItem} use → {@code reelPulse}, and telling a drag from a click
 *       is a client-side judgement the server cannot verify.</li>
 *   <li>An anchored camera is a MODAL state, and every exit has to release it — key up, fight end, screen
 *       open, death, dimension change, disconnect, plus a timeout. Miss one and the player cannot look
 *       around any more.</li>
 * </ul>
 *
 * <p>If an analogue fight is ever built, two things are already known. The portable read is <b>not</b> the
 * mouse but the ROTATION DELTA — {@code player.getYRot()/getXRot()} minus a stored anchor — which is the
 * same number on every line, needs no mixin, and picks up controller right-sticks for free. And the fight
 * MATH needs no change at all: {@code session.courseAlign} is already a float, and its four consumers
 * (footwork's line gain, the wrong-way load, the fatigue bonus and the stamina drain) all read it as a
 * continuous 0..1. The direction model was never the defect; the input device was.
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

    /**
     * The lang key for the boss bar, so the player is told the course rather than left to infer it.
     *
     * <p>1.21.1 deleted this with §rod-load — there the blank bends toward the fish and loads with
     * the pull, so naming the course in words only repeated the tackle. 26.x has no 3D blank, so the
     * bar is still the only instrument, and the words stay until there is something to read instead.
     */
    public String key() {
        return "message.riverfishing.course_" + name().toLowerCase();
    }

    public boolean isRun() {
        return this != NONE;
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
