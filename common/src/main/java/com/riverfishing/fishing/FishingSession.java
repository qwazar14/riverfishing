package com.riverfishing.fishing;

import com.riverfishing.component.RigType;
import com.riverfishing.component.RodClass;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.InteractionHand;

/** One active line in the water for a player. Lives only on the server. */
public class FishingSession {
    public final InteractionHand hand;
    /** §trolling: mutable — a trolled lure TRAILS the boat (the target follows ~14 blocks astern). */
    public BlockPos target;
    /** §live-conditions: re-picked from the fresh weights while the line waits (a koi stays sticky). */
    public ResourceLocation species;
    public final RodClass rodClass;

    /** Engine's sampled time-to-bite, in ticks. */
    public final long biteDelay;
    /** Absolute tick the bite fires; -1 until the clock starts (set lazily for ACTIVE on first retrieve). */
    public long biteAtTick;
    /** §live-conditions (0.5.0): the cast's context snapshot — its dynamic half is refreshed every ~15 s. */
    public com.riverfishing.engine.BiteContext ctx;
    /** Bite speed at the last (re-)evaluation: swarm-capped W × frenzy × feed. Rescales the wait on change. */
    public double biteSpeed;

    // ---- ACTIVE (spinning) retrieve state ----
    public boolean retrieving;
    public int retrieveTicks;
    public int retrieveMax;
    /** §click-retrieve (0.5.1): game-time of the previous crank CLICK — the lure-game cadence clock. */
    public long lastClickTick;
    /** §lure-game: wobbler/crankbait swim-action — only works at a steady crank rhythm. */
    public boolean lureStrict;
    /** §trolling: the boat trails the lure — line never depletes, the take self-strikes. */
    public boolean trolling;
    // ---- Â§topwater (0.4.0): popper surface retrieve ----
    public boolean topwater;          // popper on the rig: surface lure with a pop-pause cadence
    public long lastRetrieveTick;     // game-time of the previous retrieve tick (detects pauses = pops)
    public double popRhythm = 1.0;    // 0.6..1.5 â good cadence advances the bite clock, bad stalls it
    public boolean blowupTelegraphed; // the pre-take boil has fired
    /** This retrieve's snag fate, decided at cast: 0 none, 1 recoverable (tug free), 2 dead (lose rig). */
    public int snagOutcome;
    /** Retrieve tick the snag strikes at (second half of the retrieve, as the lure nears the bank). */
    public int snagAtTick;
    /** Â§foul-hook: this retrieve will foul-hook a passing fish (rolled once at cast, ~1%). */
    public boolean willFoul;
    /** Retrieve tick the foul-hook strikes at. */
    public int foulAtTick;

    // ---- Ice fishing (Â§ice-jig): jig the mormyshka in a steady rhythm to draw fish through the hole ----
    public boolean iceFishing;
    public long lastJigTick;

    // ---- bite window ----
    public boolean bitten;
    public long biteWindowEnd;

    // ---- tackle facts captured at cast, used by the fight (Â§7) ----
    public double lineStrainKg;
    public double dragKg;
    public boolean hasLeader;
    public double leaderProtection; // bite-through resistance of the fitted leader (#4)
    public RigType rigType;
    public int hookWear;          // dullness of the sharpest hook (Â§3.8)
    public boolean foulHooked;    // snagged by the body on a spinning retrieve (Â§7.1) -> legal=false
    public int reelSize;          // 0 = no reel (float/pole) â drives the fight feel (#2)
    public double overloadPenalty = 1.0; // <1 when the rig overloads the rod (#5)

    // Fight dynamics, precomputed at hook-up from line/reel/weight/pattern (#2, #3, #4)
    public String fightPattern = "steady";
    public double fightAggression = 0.5;
    public double runTensionPulse;
    /** §fish-fatigue (0.5.1): 0..1 — the fish burns out over the fight; runs weaken, landing speeds up. */
    public double fatigue;
    /** Fatigue gained per RUN tick (weight-scaled at setup); calm ticks add 20% of it. */
    public double fatigueRunTick;
    public double calmTensionPulse;
    public double landPulse;
    public double relaxTick;
    public long fightTimeout = 900; // ticks; scaled up for big/burst fish so they stay winnable

    // Predator fight (2.1): a lure-caught fish (or any toothy predator) fights fast and sharp â harder
    // pulls, a tighter margin, and sudden head-shakes. Scaled by weight so an ultralight tiddler is fair.
    public boolean predator;
    public double headShakeChance; // per-tick chance of a sudden thrash during the fight

    // Float strike-timing mini-game (#5). The green (100%) zone is [centerÂ±zoneHalf]; a flanking orange
    // band out to [centerÂ±orangeHalf] gives a 25% hook chance. Center is RANDOM per attempt (Â§float-zones).
    public long floatStart;
    public int floatPeriod;
    public float floatZoneHalf;
    public float floatZoneCenter = 0.5f;
    public float floatOrangeHalf;

    // ---- fight state ----
    public boolean fighting;
    public ServerBossEvent bossBar;
    public double tension;        // 0..1; over breakTension the line is in overstress (Â§tackle-stress)
    public double landProgress;   // 0..1; reaching 1 lands the fish
    public double breakTension;   // how much tension the tackle tolerates for THIS fish
    // Â§tackle-stress (0.4.0): crossing the limit no longer snaps instantly â a per-tick break chance
    // grows with the overshoot and with how long the line has been held over it.
    public double requiredKg;     // the fish's pull in kg (drives the break-load message)
    public double overStress;     // accumulated time-over-the-limit (0..2), decays when eased off
    public int overStressTicks;   // total ticks over the limit this fight (drives extra line wear)
    public boolean overstressWarned; // one "ease off!" warning per overstress episode
    public int runsLeft;
    public int runTicksLeft;      // >0 while the fish is making a run (don't reel!)
    public long nextRunAt;
    public long fightStartTick;
    public int weightG;
    public int lengthCm;
    public boolean trophy;         // trophy-class specimen: top-of-range size, glint, 3x XP
    public int bycatch;            // Â§bycatch-intrigue: 0 = fish, 1 = junk, 2 = treasure (short heavy fight)
    public boolean finalSurgeDone; // the guaranteed last dash at the bank has fired
    public int lineColor = 0xFFE8E4D0; // in-hand line render colour, from the line type (Â§immersion)
    /** The exact rod stack the cast was made with; switching hotbar slots ends the session. */
    public net.minecraft.world.item.ItemStack rodStackRef = net.minecraft.world.item.ItemStack.EMPTY;

    // §big-game greyhounding (0.5.0): reeling inside this window throws the hook — give slack.
    public long jumpWindowEnd;

    // Pole pull-out QTE (Â§pull-qte): one timing after the strike; the heavier the fish, the narrower
    // the zone and the faster the sweep (reuses floatPeriod/floatZoneHalf for the marker).
    public boolean pullMode;
    public long pullWindowEnd;

    public FishingSession(InteractionHand hand, BlockPos target, RodClass rodClass,
                          long biteDelay, long biteAtTick, ResourceLocation species) {
        this.hand = hand;
        this.target = target;
        this.rodClass = rodClass;
        this.biteDelay = biteDelay;
        this.biteAtTick = biteAtTick;
        this.species = species;
    }
}
