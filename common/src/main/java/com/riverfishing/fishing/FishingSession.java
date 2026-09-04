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
    /**
     * §scale-genes: which carp is coming — "scaled", "mirror", "linear", "naked" — or "" for every
     * other fish. The three scale varieties are one species now, so {@link #species} says {@code carp}
     * and this says which one it is; the card turns it into the K/N genotype.
     *
     * <p>ponytail: a line handed over from a rod POD keeps only its species (that is all the pod
     * saves), so a podded carp lands as the scaled one. Save it beside the species in RodPodBlockEntity
     * if that ever matters.
     */
    public String variety = "";
    public final RodClass rodClass;

    /** Engine's sampled time-to-bite, in ticks. */
    public final long biteDelay;
    /** Absolute tick the bite fires; -1 until the clock starts (set lazily for ACTIVE on first retrieve). */
    public long biteAtTick;
    /** §live-conditions (0.5.0): the cast's context snapshot — its dynamic half is refreshed every ~15 s. */
    public com.riverfishing.engine.BiteContext ctx;
    /**
     * §respec: the two roll inputs that cannot be recovered later. The specimen is rolled at the cast, but
     * a long wait RE-PICKS which species is coming — and the new fish has to be rolled against its own
     * profile, or it inherits the weight of a species it is not.
     */
    public double rollLuck;
    public int rollLivebaitG;
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
    /** §bossbar-2: last shown state (0 calm / 1 run / 2 tired) so the name only re-sends on change. */
    public int barState = -1;
    public double calmTensionPulse;
    public double landPulse;
    public double relaxTick;
    public long fightTimeout = 900; // ticks; scaled up for big/burst fish so they stay winnable

    // Predator fight (2.1): a lure-caught fish (or any toothy predator) fights fast and sharp â harder
    // pulls, a tighter margin, and sudden head-shakes. Scaled by weight so an ultralight tiddler is fair.
    public boolean predator;
    /** §nature: CatchCard.NATURE index rolled at the bite, -1 before one. */
    public byte nature = -1;
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
    /**
     * §tackle-margin: effective strain over what this fish demands, UNCAPPED. Above 1 the tackle
     * out-guns the fish, and that is what makes a heavier line worth spooling: it does not raise the
     * ceiling, it slows how fast everything fills it.
     */
    public double tackleMargin = 1.0;
    // Â§tackle-stress (0.4.0): crossing the limit no longer snaps instantly â a per-tick break chance
    // grows with the overshoot and with how long the line has been held over it.
    public double requiredKg;     // the fish's pull in kg (drives the break-load message)
    /** §rod-load: the fish's pull over the BLANK's own power (RodType.fightPowerKg) — the bend gauge. */
    public double rodPull01;
    public double overStress;     // accumulated time-over-the-limit (0..2), decays when eased off
    public int overStressTicks;   // total ticks over the limit this fight (drives extra line wear)
    public boolean overstressWarned; // one "ease off!" warning per overstress episode
    public int runsLeft;
    public int runTicksLeft;      // >0 while the fish is making a run (don't reel!)
    /**
     * §dive-cost: how long the CURRENT run was when it started. A sounding dive drains the land bar,
     * and that drain has to be a share of the bar rather than a rate per tick — otherwise retuning run
     * lengths silently retunes how much a dive costs, which is exactly what happened between 0.5.0 and
     * 0.7.0 and made the beluga unlandable.
     */
    public int runTicksTotal;
    /** §fight-course: which way THIS run is going, and how well the rod is being held against it. */
    public FightCourse course = FightCourse.NONE;
    public float courseAlign = 1f;
    /** §fight-course: the movement key currently held, as {@link com.riverfishing.network.FightInputPacket}. */
    public byte pullDir;
    /**
     * §fight-footwork: horizontal distance from the angler to the hook LAST tick. Negative means "not
     * measured yet", so the first tick of a fight never reads a jump as a sprint.
     */
    public double lastDist = -1.0;
    /** §fight-footwork: how long the angler has been walking AT the fish on a dead line. */
    public int slackTicks;
    /** So the slack warning fires once per episode rather than every tick. */
    public boolean slackWarned;
    /** §fight-footwork: the angler's legs are loading the line RIGHT NOW, so it must not relax. */
    public boolean legPull;
    /** Which run of the fight this is, so a pattern can script its directions in order. */
    public int runIndex;
    /**
     * §angler-stamina: 1 fresh, 0 spent. Winding and holding a rod against a running fish are WORK, and
     * it only comes back when you stop doing them and let the drag do the fighting — which is the real
     * technique the fight never asked for before.
     */
    public double anglerStamina = 1.0;
    /** So the "you are spent" warning fires once per episode rather than every tick. */
    public boolean staminaWarned;
    public long nextRunAt;
    public long fightStartTick;
    public int weightG;
    public int lengthCm;
    public boolean trophy;         // trophy-class specimen: top-of-range size, glint, 3x XP
    public int bycatch;            // Â§bycatch-intrigue: 0 = fish, 1 = junk, 2 = treasure (short heavy fight)
    public boolean finalSurgeDone; // the guaranteed last dash at the bank has fired
    public int lineColor = 0xFFE8E4D0; // in-hand line render colour, from the line type (Â§immersion)
    /** The exact rod stack the cast was made with; also the rig source for pods, which fish with no
     *  rod in hand. NEVER compare this by ==: the slot can be rewritten with an equal-but-different
     *  object and the reference goes stale (§session-guard). */
    public net.minecraft.world.item.ItemStack rodStackRef = net.minecraft.world.item.ItemStack.EMPTY;
    /** Hotbar slot the cast was made from, or -1 for off-hand/pod. This is what "still the same rod"
     *  actually means — an index survives the stack object being replaced. */
    public int rodSlot = -1;
    /** §float-kind: 0 = nothing on the surface, 1 = a plain peg (float rod, no float item),
     *  2 = the proper float (a float item is rigged). Decided once at the cast — the client cannot
     *  see the rig, so this has to cross the wire. */
    public byte floatKind;

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
