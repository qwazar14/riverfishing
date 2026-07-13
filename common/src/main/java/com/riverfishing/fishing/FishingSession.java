package com.riverfishing.fishing;

import com.riverfishing.component.RigType;
import com.riverfishing.component.RodClass;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.InteractionHand;

/** One active line in the water for a player. Lives only on the server. */
public class FishingSession {
    public final InteractionHand hand;
    public final BlockPos target;
    public final Identifier species;
    public final RodClass rodClass;

    /** Engine's sampled time-to-bite, in ticks. */
    public final long biteDelay;
    /** Absolute tick the bite fires; -1 until the clock starts (set lazily for ACTIVE on first retrieve). */
    public long biteAtTick;

    // ---- ACTIVE (spinning) retrieve state ----
    public boolean retrieving;
    public int retrieveTicks;
    public int retrieveMax;
    /** This retrieve's snag fate, decided at cast: 0 none, 1 recoverable (tug free), 2 dead (lose rig). */
    public int snagOutcome;
    /** Retrieve tick the snag strikes at (second half of the retrieve, as the lure nears the bank). */
    public int snagAtTick;
    /** §foul-hook: this retrieve will foul-hook a passing fish (rolled once at cast, ~1%). */
    public boolean willFoul;
    /** Retrieve tick the foul-hook strikes at. */
    public int foulAtTick;

    // ---- Ice fishing (§ice-jig): jig the mormyshka in a steady rhythm to draw fish through the hole ----
    public boolean iceFishing;
    public long lastJigTick;

    // ---- bite window ----
    public boolean bitten;
    public long biteWindowEnd;

    // ---- tackle facts captured at cast, used by the fight (§7) ----
    public double lineStrainKg;
    public double dragKg;
    public boolean hasLeader;
    public double leaderProtection; // bite-through resistance of the fitted leader (#4)
    public RigType rigType;
    public int hookWear;          // dullness of the sharpest hook (§3.8)
    public boolean foulHooked;    // snagged by the body on a spinning retrieve (§7.1) -> legal=false
    public int reelSize;          // 0 = no reel (float/pole) — drives the fight feel (#2)
    public double overloadPenalty = 1.0; // <1 when the rig overloads the rod (#5)

    // Fight dynamics, precomputed at hook-up from line/reel/weight/pattern (#2, #3, #4)
    public String fightPattern = "steady";
    public double fightAggression = 0.5;
    public double runTensionPulse;
    public double calmTensionPulse;
    public double landPulse;
    public double relaxTick;
    public long fightTimeout = 900; // ticks; scaled up for big/burst fish so they stay winnable

    // Predator fight (2.1): a lure-caught fish (or any toothy predator) fights fast and sharp — harder
    // pulls, a tighter margin, and sudden head-shakes. Scaled by weight so an ultralight tiddler is fair.
    public boolean predator;
    public double headShakeChance; // per-tick chance of a sudden thrash during the fight

    // Float strike-timing mini-game (#5). The green (100%) zone is [center±zoneHalf]; a flanking orange
    // band out to [center±orangeHalf] gives a 25% hook chance. Center is RANDOM per attempt (§float-zones).
    public long floatStart;
    public int floatPeriod;
    public float floatZoneHalf;
    public float floatZoneCenter = 0.5f;
    public float floatOrangeHalf;

    // ---- fight state ----
    public boolean fighting;
    public ServerBossEvent bossBar;
    public double tension;        // 0..1; reaching breakTension snaps the line
    public double landProgress;   // 0..1; reaching 1 lands the fish
    public double breakTension;   // how much tension the tackle tolerates for THIS fish
    public int runsLeft;
    public int runTicksLeft;      // >0 while the fish is making a run (don't reel!)
    public long nextRunAt;
    public long fightStartTick;
    public int weightG;
    public int lengthCm;
    public boolean trophy;         // trophy-class specimen: top-of-range size, glint, 3x XP
    public int bycatch;            // §bycatch-intrigue: 0 = fish, 1 = junk, 2 = treasure (short heavy fight)
    public boolean finalSurgeDone; // the guaranteed last dash at the bank has fired
    public int lineColor = 0xFFE8E4D0; // in-hand line render colour, from the line type (§immersion)
    /** The exact rod stack the cast was made with; switching hotbar slots ends the session. */
    public net.minecraft.world.item.ItemStack rodStackRef = net.minecraft.world.item.ItemStack.EMPTY;

    // Pole pull-out QTE (§pull-qte): one timing after the strike; the heavier the fish, the narrower
    // the zone and the faster the sweep (reuses floatPeriod/floatZoneHalf for the marker).
    public boolean pullMode;
    public long pullWindowEnd;

    public FishingSession(InteractionHand hand, BlockPos target, RodClass rodClass,
                          long biteDelay, long biteAtTick, Identifier species) {
        this.hand = hand;
        this.target = target;
        this.rodClass = rodClass;
        this.biteDelay = biteDelay;
        this.biteAtTick = biteAtTick;
        this.species = species;
    }
}
