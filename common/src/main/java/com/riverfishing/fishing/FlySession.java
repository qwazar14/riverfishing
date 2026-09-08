package com.riverfishing.fishing;

import com.riverfishing.tackle.TiedDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * §fly-3 (0.10.0): everything one fly cast knows about itself — the state it is in, where the fly is,
 * which rise it was aimed at, and what kind of fly is on the tippet.
 *
 * <p>The loop is four states and nothing else:
 * <pre>
 *   IDLE → CASTING → DRIFTING → STRIKING → FIGHTING → IDLE
 * </pre>
 * and the one shortcut a drifting line has: pick up (or strip the fly home) and you are back at IDLE,
 * ready for the next cast. {@link FlyCast} owns CASTING, {@link FlyDrift} owns DRIFTING, {@link FlyStrike}
 * owns STRIKING, and the fight is the mod's own — no fly-only combat exists.
 */
public final class FlySession {
    /** How the player fishes this fly, in the only three kinds the game asks them to tell apart. */
    public enum Kind {
        /** A dry fly or an ant: it rides the surface and is fished by leaving it alone. */
        DRY,
        /** A nymph, a shrimp, anything that swims under: a dead drift with the odd twitch. */
        NYMPH,
        /** A streamer: a small fish, and it is fished by stripping it. */
        STREAMER;

        /** The button that sets the hook on this fly: a streamer is strip-set, everything else is lifted. */
        public boolean strikeIsRightClick() {
            return this == STREAMER;
        }

        public static Kind of(TiedDesign.Analysis tied) {
            if (tied == null) return NYMPH;
            return switch (tied.template()) {
                case DRY_FLY, ANT -> DRY;
                case STREAMER -> STREAMER;
                default -> NYMPH;
            };
        }
    }

    public enum State { CASTING, DRIFTING, STRIKING }

    public State state = State.DRIFTING;
    public Kind kind = Kind.NYMPH;

    // ---- where the fly is ----
    /** The water column the cast landed in; the current walks it downstream. */
    public BlockPos spot;
    /** 0..1 of the way home: what the strips have taken back. The client draws the end off this. */
    public double reel;

    // ---- the drift ----
    /** 0..100. Below {@link FlyDrift#DRAG_CRITICAL} the fly rides naturally; above it, it skates. */
    public int drag;
    /** The line has come tight across the current — this drift is over. */
    public boolean straight;
    /** How many times the line has been mended this drift; each one after the first is noisier. */
    public int mends;
    /** The last strip's tick and the one before it: the rhythm a streamer is fished by, and the double-tap. */
    public long lastStrip = Long.MIN_VALUE, prevStrip = Long.MIN_VALUE;
    /** The rise this cast is fishing to, once the fly has landed on one. */
    public boolean onRise;
    /** The last drift state sent to the HUD, so the status line is not re-sent every tick. */
    public int shownState = -1;

    // ---- the take ----
    /** The strike window closes here once the fish has taken. */
    public long strikeUntil;
    /** True once the fish has actually taken — before that a strike is early. */
    public boolean taken;
    /** 0 weak, 1 normal, 2 strong — what the strike earned; the fight reads it. */
    public int hookStrength = 1;

    public FlySession(BlockPos spot, Kind kind) {
        this.spot = spot;
        this.kind = kind;
    }

    /** Where the fly actually floats: the spot, pulled toward the angler by everything stripped back. */
    public Vec3 flyAt(ServerPlayer sp) {
        return new Vec3(Mth.lerp(reel, spot.getX() + 0.5, sp.getX()),
                spot.getY(), Mth.lerp(reel, spot.getZ() + 0.5, sp.getZ()));
    }

    /** Metres of line on the water, for the HUD. */
    public int metres(ServerPlayer sp) {
        Vec3 at = flyAt(sp);
        double dx = sp.getX() - at.x, dz = sp.getZ() - at.z;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
