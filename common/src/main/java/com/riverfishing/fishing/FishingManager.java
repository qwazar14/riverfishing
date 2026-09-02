package com.riverfishing.fishing;

import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.LineType;
import com.riverfishing.component.RigType;
import com.riverfishing.component.RodClass;
import com.riverfishing.component.RodType;
import com.riverfishing.config.RiverFishingConfig;
import com.riverfishing.engine.BiteContext;
import com.riverfishing.engine.BiteEngine;
import com.riverfishing.engine.TimeOfDay;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.riverfishing.engine.BarometricPressure;
import com.riverfishing.engine.Weather;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.integration.SeasonProvider;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.LineItem;
import com.riverfishing.item.ReelItem;
import com.riverfishing.item.RigItem;
import com.riverfishing.item.RodData;
import com.riverfishing.item.RodItem;
import com.riverfishing.item.WearData;
import com.riverfishing.network.FloatTimingPacket;
import com.riverfishing.network.FightInputPacket;
import com.riverfishing.network.LineSyncPacket;
import com.riverfishing.network.ModNetwork;
import com.riverfishing.rig.RigData;
import com.riverfishing.rig.RigLayout;
import com.riverfishing.rig.SlotRole;
import com.riverfishing.registry.ModItems;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import com.riverfishing.water.WaterBodyDetector;
import com.riverfishing.water.WaterType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side fishing loop (§4, §7, Module 1). One {@link FishingSession} per player, with a
 * per-rod-class state machine:
 * <ul>
 *   <li><b>ACTIVE</b> (spinning/ultralight): cast → hold right-click to <i>retrieve</i> → a strike
 *       can hit during the retrieve → release and click to set the hook → fight.</li>
 *   <li><b>FLOAT</b> (pole/bamboo/stick): cast → watch the float → click to set the hook → fight.
 *       No reel, so no retrieve.</li>
 *   <li><b>BOTTOM</b> (feeder/bottom/carp): long cast → wait (forgiving window) → click → fight.
 *       Hands-free rod-pod + alarms arrive in a later module.</li>
 * </ul>
 * The fight is a tension duel on a boss bar; over-tension snaps the line — 50/50 to either throw the
 * hook (keep the rig) or break off (lose the whole rig) per Module 5.
 */
public final class FishingManager {
    private static final Map<UUID, FishingSession> SESSIONS = new HashMap<>();
    /** §spin-harder: counts active (spinning/ultralight) casts per player to burn 1 food point per 4. */
    private static final Map<UUID, Integer> ACTIVE_CAST_COUNT = new HashMap<>();
    private static final double CAST_REACH = 32.0;
    private static final double MAX_SESSION_DISTANCE = 40.0;
    private static final double ROD_BREAK_RATIO = 2.5; // rig mass > rodMax * this -> the blank snaps (#5)
    private static final double FOUL_CHANCE = 0.01;     // §9: 1% per spinning retrieve to foul-hook (× config)
    /**
     * §dive-cost: what ONE sounding dive takes off the land bar, whole. The 0.5.0 value in the shape
     * it was actually tuned in — a dive of average length then (85 ticks) at the old 0.0035 a tick.
     */
    private static final double DIVE_COST = 0.30;

    /**
     * §tire-within-the-fight: the most of its OWN fight a fish may spend before it is fully worn down.
     * Only ever a ceiling on the absolute fatigue clock — see where it is used.
     */
    private static final double FATIGUE_FIGHT_SHARE = 0.55;

    private static final double TACKLE_BREAK_CHANCE = 0.003; // §10: 0.3% per hook-up, the line parts, rig lost
    // §snag: per fishing action, 3% a dead (глухой) snag that loses the rig, 7% a recoverable one you
    // tug free. Scaled by the difficulty config's snagChance().
    private static final double SNAG_DEAD_CHANCE = 0.03;
    private static final double SNAG_TOTAL_CHANCE = 0.10;

    private FishingManager() {}

    public static void clear(UUID uuid) {
        TROLL_GOOD.remove(uuid);
        TROLL_LAST.remove(uuid);
        FishingSession session = SESSIONS.remove(uuid);
        if (session != null) {
            if (session.bossBar != null) {
                session.bossBar.removeAllPlayers();
            }
            // §rod-bend §rod-layers: logging out mid-cast would otherwise SAVE the fishing look into
            // the inventory — a bent rod, or a rod still wearing its line-is-out overlay with no line
            // in the water. Both flags live in the same custom_model_data, so both are stowed here.
            com.riverfishing.item.RodData.setLineOut(session.rodStackRef, false);
            com.riverfishing.item.RodData.setBend(session.rodStackRef, 0);
        }
    }

    /** Detach a player's waiting bottom-rod session so it can move onto a rod-pod (Module 2). */
    public static FishingSession detachBottomSession(ServerPlayer sp) {
        FishingSession s = SESSIONS.get(sp.getUUID());
        if (s == null || s.fighting || s.rodClass != RodClass.BOTTOM) {
            return null;
        }
        if (s.bossBar != null) {
            s.bossBar.removeAllPlayers();
            s.bossBar = null;
        }
        SESSIONS.remove(sp.getUUID());
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), false, null, 0f, 0, (byte) 0)); // line now lives on the pod
        return s;
    }

    /** Start a fight straight from a podded line the player just grabbed during its bite window. */
    public static void startPodFight(ServerPlayer sp, BlockPos target, Identifier species,
                                     double lineStrainKg, double dragKg, boolean hasLeader, RigType rigType) {
        ServerLevel level = sp.level();
        long now = level.getGameTime();
        FishingSession session = new FishingSession(InteractionHand.MAIN_HAND, target, RodClass.BOTTOM, 0, now, species);
        session.lineStrainKg = lineStrainKg;
        session.dragKg = dragKg;
        session.hasLeader = hasLeader;
        session.leaderProtection = hasLeader ? 1.0 : 0.0;
        session.rigType = rigType;
        SESSIONS.put(sp.getUUID(), session);
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, target, 0f, session.lineColor,
                session.floatKind));
        hookUp(sp, level, session, now);
    }

    /**
     * §float-kind: what, if anything, floats on the surface for this cast. Derived from the RIG, not
     * from the rod class — a stick rod is FLOAT-class but its built-in primitive rig has no float slot
     * and can never hold one, so deriving it from the class alone drew a bobber that was not there.
     * An ice hole shows nothing: the line simply drops through.
     */
    private static byte floatKind(RodClass rodClass, boolean iceFishing, ItemStack rig) {
        if (rodClass != RodClass.FLOAT || iceFishing) return 0;
        return RigData.hasFloat(rig) ? (byte) 2 : (byte) 1;
    }

    private static long biteWindow(RodClass rodClass) {
        return switch (rodClass) {
            case ACTIVE -> 25;  // fast reaction during a retrieve
            case FLOAT -> 72;   // a few marker passes for the timing mini-game (#5)
            case BOTTOM -> 200; // long cast: a wide, forgiving reaction window (§bite-window)
        };
    }

    // ---- rod use: cast / strike / reel ----

    public static boolean handleRodUse(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return false;
        ServerLevel level = sp.level();
        long now = level.getGameTime();
        FishingSession session = SESSIONS.get(sp.getUUID());

        if (session != null) {
            if (session.fighting) {
                if (session.pullMode) {
                    pullStrike(sp, level, session, now);       // pole pull-out timing (§pull-qte)
                    return true;
                }
                reelPulse(sp, level, session);                 // вываживание
            } else if (session.bitten && now <= session.biteWindowEnd) {
                // Float rods AND lure rods (§strike-qte, 2.4) run the timing marker — hit the zone to set
                // the hook, miss and the fish is gone. Bottom rods keep the plain click подсечка.
                if ((session.rodClass == RodClass.FLOAT || session.rodClass == RodClass.ACTIVE)
                        && session.floatPeriod > 0) {
                    activeStrike(sp, level, session, now);
                } else {
                    hookUp(sp, level, session, now);           // подсечка
                }
            } else if (session.iceFishing && session.rodClass != RodClass.ACTIVE) {
                iceJig(sp, level, session, now);               // §ice-jig: work the mormyshka (attract), don't reel in
            } else if (session.rodClass == RodClass.ACTIVE) {
                clickRetrieve(sp, level, session, now); // §click-retrieve: the click IS the lure action
                return true;
            } else {
                endSession(sp, session);                       // reel in / recast
                actionbar(sp, Component.translatable("message.riverfishing.reeled_in"));
            }
            return true;
        }
        // No session: the cast now happens on RELEASE with a charged power bar (§cast-minigame).
        return false;
    }

    // ---- §trolling v1 (0.5.0): boat-agnostic — the MOVING BOAT does the casting and the retrieving ----

    /** Consecutive good-speed ticks per player (the anti-jitter ramp before the auto-cast). */
    private static final Map<UUID, Integer> TROLL_GOOD = new HashMap<>();
    /** §trolling-speed: last {x, z} of the vehicle — boats are CLIENT-driven, so the server-side
     *  getDeltaMovement() is ~zero for a paddled boat; real speed = position delta per tick. */
    private static final Map<UUID, double[]> TROLL_LAST = new HashMap<>();

    /**
     * Called every server player tick. Trolling needs: an assembled TROLLING/SEA_SPIN rod in the main
     * hand, a boat vehicle, and horizontal speed inside the working window (~3-9 m/s). Hold that for
     * three seconds and the line goes out by itself (a normal cast along the look vector — over open
     * sea that's always water); the boat's movement then works the lure (auto retrieve ticks), so
     * bites, strike QTE and the fight all ride the existing ACTIVE flow untouched. Any watercraft that
     * moves the player works — vanilla boats today, modded ships tomorrow.
     */
    /** §rod-bend: fight stress 0..1 — tension relative to THIS line's breaking point (0 outside a fight). */
    private static float fightStress(FishingSession session) {
        if (!session.fighting) return 0f;
        return (float) Mth.clamp(session.tension / Math.max(0.05, session.breakTension), 0.0, 1.0);
    }

    /**
     * §rod-load: how loaded the BLANK is 0..1 — the fish's pull against the rod's own power class,
     * eased with ^0.75 because a real blank shows half its bend well before half its rating. A run
     * is full pull (fading with fatigue); between runs the fish hangs on the line at about a third.
     */
    private static float rodLoad(FishingSession session) {
        if (!session.fighting) return 0f;
        double activity = session.runTicksLeft > 0 ? 1.0 - 0.35 * session.fatigue : 0.3;
        return (float) Math.pow(Mth.clamp(session.rodPull01 * activity, 0.0, 1.0), 0.75);
    }

    public static void trollingTick(ServerPlayer sp) {
        ItemStack trollRod = sp.getMainHandItem();
        boolean capable = trollRod.getItem() instanceof RodItem ri
                && (ri.rodType() == RodType.TROLLING || ri.rodType() == RodType.SEA_SPIN)
                && RodData.isAssembled(trollRod);
        if (!capable || !(sp.getVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat boat)) {
            TROLL_GOOD.remove(sp.getUUID());
            TROLL_LAST.remove(sp.getUUID());
            return;
        }
        // §trolling-speed: measure from the boat's actual position change — a player-paddled boat is
        // client-authoritative and its server-side delta movement stays ~0 (why trolling never armed).
        // §troll-smooth (0.5.1): the boat is client-authoritative, so its server position advances in
        // PACKET-SIZED jumps — raw per-tick deltas jitter between 0 and 2x the real speed. An EMA over
        // the deltas reads the true cruise speed through the jitter.
        double[] last = TROLL_LAST.get(sp.getUUID());
        double dx = last == null ? 0 : boat.getX() - last[0];
        double dz = last == null ? 0 : boat.getZ() - last[1];
        double inst = Math.sqrt(dx * dx + dz * dz);
        double speed = last == null ? inst : last[2] * 0.8 + inst * 0.2;
        TROLL_LAST.put(sp.getUUID(), new double[]{boat.getX(), boat.getZ(), speed});
        boolean inWindow = speed >= 0.12 && speed <= 0.60;
        ServerLevel level = sp.level();

        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session != null) {
            if (session.fighting || session.bitten) return; // the take is handled by the normal flow
            if (session.trolling && inWindow) {
                // §trolling: the lure TRAILS ~14 blocks astern — the target follows the boat, so the
                // session-guard's distance check never silently drops a travelling line, and the drawn
                // line visibly drags behind the stern.
                double n = Math.sqrt(dx * dx + dz * dz);
                if (n > 1e-3 && sp.tickCount % 10 == 0) {
                    session.target = BlockPos.containing(boat.getX() - dx / n * 14.0,
                            boat.getY(), boat.getZ() - dz / n * 14.0);
                }
                if (sp.tickCount % 2 == 0) {
                    retrieveTick(sp); // the moving boat works the lure
                }
            }
            return;
        }
        if (!inWindow) {
            // §troll-smooth: a rough patch DECAYS the arm counter instead of zeroing it — a turn or a
            // wave no longer restarts the whole 3-second arming from scratch.
            TROLL_GOOD.computeIfPresent(sp.getUUID(), (k, v) -> v > 3 ? v - 3 : null);
            return;
        }
        int good = TROLL_GOOD.merge(sp.getUUID(), 1, Integer::sum);
        if (good >= 60) {
            TROLL_GOOD.remove(sp.getUUID());
            if (startCast(sp, level, InteractionHand.MAIN_HAND, level.getGameTime(), 0.55)) {
                FishingSession s = SESSIONS.get(sp.getUUID());
                if (s != null) s.trolling = true; // §trolling: trailing line — see retrieveTick
                actionbar(sp, Component.translatable("message.riverfishing.trolling_start")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /**
     * §fight-brace (0.7.0): a hooked fish nails you to the spot.
     *
     * <p>The fight input is WASD, so without this a run turns into a foot race — you strafe half a chunk
     * answering three runs, which looks absurd and quietly beats the tension model by walking the fish in.
     * Braced against a rod you shuffle, and the movement that remains is meaningful rather than free
     * (see {@link #footwork}).
     *
     * <p>Transient on purpose: it is never written to the player's save, so no combination of disconnect,
     * crash or dimension change can leave someone permanently slow. {@link #endSession} is the single
     * teardown path every fight goes through, and it lifts this.
     */
    private static void brace(ServerPlayer sp, boolean on) {
        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on) {
            if (speed.getModifier(BRACE_ID) == null) speed.addTransientModifier(BRACE);
        } else {
            speed.removeModifier(BRACE_ID);
        }
    }

    private static final Identifier BRACE_ID = com.riverfishing.RiverFishing.id("fight_brace");
    private static final AttributeModifier BRACE = new AttributeModifier(
            BRACE_ID, -0.72, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /**
     * §fight-footwork (0.7.0): the angler's feet are tackle too, and this reads them whether or not the
     * player has ever heard of {@link FightCourse}. Only the change in distance to the hook matters, so an
     * angler who stands still fishes exactly as they did before — nothing here punishes not knowing it.
     *
     * <ul>
     *   <li><b>Backing away</b> is pumping with your legs: it wins line and it loads the rod, the same
     *       trade a crank makes. It is how you actually beat a fish off a bank.
     *   <li><b>Walking at the fish</b> is slack, and slack is how a hook falls out — the one way to lose a
     *       fish that has nothing to do with how strong the line is.
     * </ul>
     *
     * @return true if the fish came off and the session is already gone
     */
    private static boolean footwork(ServerPlayer sp, ServerLevel level, FishingSession session) {
        double dx = session.target.getX() + 0.5 - sp.getX();
        double dz = session.target.getZ() + 0.5 - sp.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        // Clamped so a teleport, a knockback or an elytra landing cannot be read as one giant heave.
        double delta = session.lastDist < 0 ? 0.0 : Mth.clamp(dist - session.lastDist, -0.5, 0.5);
        session.lastDist = dist;
        session.legPull = false;
        // A passenger is not walking anywhere — that is the BOAT moving, and reading it as footwork made a
        // §trolling fight (which happens under way, by definition) win or snap itself in a couple of
        // seconds while the player did nothing at all.
        if (sp.isPassenger()) {
            session.slackTicks = 0;
            return false;
        }
        if (Math.abs(delta) < 0.002) {          // standing still: the fight behaves exactly as it always did
            session.slackTicks = Math.max(0, session.slackTicks - 2);
            return false;
        }
        boolean inRun = session.runTicksLeft > 0;
        // An OPEN drag free-spools: walk to the next county and the reel just pays line out behind you.
        // Legs only move a fish through a working drag — and without this gate, crouching (which bleeds
        // tension three times as fast) plus walking would have handed back the free win above.
        if (delta > 0 && !sp.isCrouching()) {
            // Legs are a slow winch: line gained scales with the tackle exactly as a crank does, and during
            // a run it is throttled by the same course factor — you cannot walk a running fish backwards.
            session.landProgress = Mth.clamp(session.landProgress + session.landPulse * delta * 0.9
                    * (!inRun ? 1.0 : session.course.isRun() ? 0.2 + 0.5 * session.courseAlign : 0.2),
                    0.0, 1.0);
            session.tension += session.calmTensionPulse * delta * 3.0 * (inRun ? 2.5 : 1.0)
                    * (sp.isSprinting() ? 1.8 : 1.0);
            session.legPull = true;   // the line is loaded by the legs: it does not relax this tick
            session.slackTicks = Math.max(0, session.slackTicks - 2);
            session.slackWarned = false;
            return false;
        }
        session.tension = Math.max(0.0, session.tension + session.calmTensionPulse * delta * 4.0);
        // A RUNNING fish keeps its own line tight, so walking cannot put slack in it — and the answer to
        // an UP course is literally the forward key, which walks you at the fish. Without this the game
        // killed you for obeying its own boss bar: two of every three greyhounding runs are UP.
        // A boot on the end of the line has no mouth to spit the hook out of (§bycatch-intrigue).
        if (inRun || session.bycatch != 0 || session.tension >= 0.10 * session.breakTension) {
            session.slackTicks = Math.max(0, session.slackTicks - 1);
            return false;
        }
        session.slackTicks++;
        if (session.slackTicks >= 20 && !session.slackWarned) {
            session.slackWarned = true;
            actionbar(sp, Component.translatable("message.riverfishing.slack").withStyle(ChatFormatting.RED));
        }
        if (session.slackTicks > 45 && level.getRandom().nextDouble() < 0.05) {
            endSession(sp, session);
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE,
                    SoundSource.PLAYERS, 0.6f, 1.4f);
            actionbar(sp, Component.translatable("message.riverfishing.slack_lost",
                    FishItem.approxWeightText(session.weightG)).withStyle(ChatFormatting.YELLOW));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.SHAKE_OFF);
            return true;
        }
        return false;
    }

    /**
     * §fight-course: the client tells us which way it is pulling (see {@link FightInputPacket}). Kept on the
     * session so it dies with the fight; nothing outside a run ever reads it.
     */
    public static void setPullDirection(ServerPlayer sp, byte dir) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session != null) session.pullDir = dir >= 0 && dir <= 4 ? dir : 0;
    }

    public static boolean hasSession(ServerPlayer sp) {
        return SESSIONS.containsKey(sp.getUUID());
    }

    /** Entry point for the power-bar cast (§cast-minigame): called when the player releases the charge. */
    public static boolean chargedCast(ServerPlayer sp, InteractionHand hand, float power) {
        ServerLevel level = sp.level();
        if (SESSIONS.containsKey(sp.getUUID())) return false;
        return startCast(sp, level, hand, level.getGameTime(), Mth.clamp(power, 0.05f, 1.0f));
    }

    private static boolean startCast(ServerPlayer sp, ServerLevel level, InteractionHand hand, long now, double power) {
        ItemStack rod = sp.getItemInHand(hand);
        if (!RodData.isAssembled(rod)) {
            actionbar(sp, Component.translatable(RodData.missingKey(rod)).withStyle(ChatFormatting.RED));
            return false;
        }

        // Power-bar cast (§cast-minigame): the charge decides the throw distance along the look
        // direction; the rig lands where the power puts it — under- or over-throwing misses the fish.
        RodType type = ((RodItem) rod.getItem()).rodType();
        // §ice-only: the winter rod is fished vertically through a drilled hole ONLY — it can't be cast
        // into open water. Right-click a drilled ice hole with it instead (startIceFishing).
        if (type == RodType.WINTER) {
            actionbar(sp, Component.translatable("message.riverfishing.winter_needs_hole").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        // §closed-slots: float/lure rods always fish with their built-in rig — install it if a freshly
        // crafted or trade-bought rod hasn't been opened in the assembly GUI yet (no-op for bottom rods).
        RodData.ensureNativeRig(rod, type);
        double maxRange = !type.takesReel() ? 6.0 : (type.longRange() ? 32.0 : 18.0);
        // §spin-harder (3): the spinning rod's reach was too long — halve it (32 → 16). Bottom rods and
        // ultralight are untouched. The retrieve is made ~2× longer below to compensate.
        if (type == RodType.SPINNING) maxRange = 16.0;

        // Rod test, lower bound (§rod-test): an under-weighted rig doesn't load the blank — the cast
        // physically can't fly far. (The over-weight side already strains/snaps the blank.) The client
        // draws the same cut on the power bar (§cast-bar-cut). The bite penalty below is SILENT.
        boolean underloaded = false;
        ItemStack rigCheck = RodData.get(rod, ComponentSlot.RIG);
        // §test-tolerance (0.6.0): a hidden ±15% slack on the printed window — real blanks forgive a
        // little; the weight is the bench-chosen grams now, not the fixed type mass.
        if (rigCheck.getItem() instanceof RigItem && type.castWeightMax() > 0) {
            double wG = com.riverfishing.rig.RigData.effectiveWeightG(rigCheck);
            // §cast-weight (round 5): IN-WINDOW tackle always flies well — 85% at the window's bottom
            // rising to 100% at the top (a 160 g wobbler on a 150-600 trolling rod is fine). Only
            // BELOW the window does the flight collapse on a sqrt curve.
            double minW = type.castWeightMin(), maxW = type.castWeightMax();
            double f = wG >= minW
                    ? 0.85 + 0.15 * Mth.clamp((wG - minW) / Math.max(1.0, maxW - minW), 0.0, 1.0)
                    : 0.85 * Math.sqrt(Math.max(0.10, wG / Math.max(1.0, minW)));
            maxRange *= Mth.clamp(f, 0.30, 1.0);
            if (minW > 0 && wG < minW * 0.85) {
                underloaded = true;
                actionbar(sp, Component.translatable("message.riverfishing.rod_underloaded").withStyle(ChatFormatting.YELLOW));
            }
        }
        double throwDist = 2.0 + power * (maxRange - 2.0);
        net.minecraft.world.phys.Vec3 look = sp.getLookAngle();
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        if (hl < 1e-3) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return false;
        }
        double px = sp.getX() + (look.x / hl) * throwDist;
        double pz = sp.getZ() + (look.z / hl) * throwDist;
        BlockPos waterPos = findWaterColumn(level, px, sp.getEyeY() + 2.0, pz);
        if (waterPos == null) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return false;
        }
        // §ice-fishing: can't fish through a SOLID ice sheet — the water must be open (a drilled or natural
        // hole). Water capped by ice is rejected with a hint to drill an auger hole.
        if (com.riverfishing.item.IceAugerItem.isIce(level.getBlockState(waterPos.above()))) {
            actionbar(sp, Component.translatable("message.riverfishing.need_hole").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        WaterBody body = WaterBodyCache.forLevel(level).get(level, waterPos);
        if (body.type() == WaterType.NONE) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return false;
        }

        double dx = waterPos.getX() + 0.5 - sp.getX();
        double dz = waterPos.getZ() + 0.5 - sp.getZ();
        double castDistance = Math.sqrt(dx * dx + dz * dz);

        BiteContext ctx = buildContext(sp, level, rod, hand, body, waterPos, castDistance, now);
        RodClass rodClass = ctx.rod.rodClass();

        // A reel-less pole is just a fixed length of line on a tip — it physically can't reach far (§mechanics).
        if (ctx.reelSize == 0 && castDistance > 6.0) {
            actionbar(sp, Component.translatable("message.riverfishing.pole_too_far").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        // Rod test (#5): rig mass vs the blank's working range. Any rig is allowed, but a wildly
        // over-weight rig (a catfish rig on an ultralight) snaps the blank on the cast; a moderately
        // heavy one strains it (lower break tolerance in the fight).
        double overloadPenalty = 1.0;
        // §test-tolerance (0.6.0): the same hidden +15% slack on the top of the window.
        double rodMax = ctx.rod.castWeightMax() * 1.15;
        if (rodMax > 0 && ctx.castWeightG > rodMax * ROD_BREAK_RATIO) {
            // §rod-overload: a wildly over-weight rig no longer SNAPS the blank outright — it cracks it,
            // costing a THIRD of its durability (+1), so it survives a few abuses before finally breaking.
            if (rod.isDamageableItem()) {
                int dmg = (int) Math.ceil(rod.getMaxDamage() * 0.33) + 1;
                rod.hurtAndBreak(dmg, sp,
                        hand == InteractionHand.MAIN_HAND
                                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                                : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            }
            level.playSound(null, sp.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
            actionbar(sp, Component.translatable("message.riverfishing.rod_overload_crack").withStyle(ChatFormatting.RED));
            return false;
        }
        if (rodMax > 0 && ctx.castWeightG > rodMax) {
            double ratio = ctx.castWeightG / rodMax;
            overloadPenalty = Mth.clamp(1.0 - (ratio - 1.0) * 0.5, 0.4, 1.0);
            actionbar(sp, Component.translatable("message.riverfishing.rod_overloaded").withStyle(ChatFormatting.YELLOW));
        }

        if (ctx.rod.longRange() && body.width() < 12) {
            actionbar(sp, Component.translatable("message.riverfishing.too_narrow").withStyle(ChatFormatting.YELLOW));
        }
        if (ctx.baits.isEmpty()) {
            actionbar(sp, Component.translatable("message.riverfishing.no_bait").withStyle(ChatFormatting.YELLOW));
        }

        RandomSource random = level.getRandom();
        BiteEngine.Outcome outcome = BiteEngine.evaluate(FishProfileManager.get().all(), ctx, random);
        if (!outcome.willBite()) {
            noBitesHint(sp, ctx);
            GuideNudge.failure(sp, ctx.rod.rodClass(), GuideNudge.NO_BITES);
            return false;
        }

        Identifier species = maybeKoi(outcome.pickSpecies(random), ctx, random);

        // §feed-lands-where-the-rig-does: a feeder cage empties one jar per cast, and it empties it AT THE
        // BOBBER — the landing spot, not the water in front of your boots. It is exactly the same call
        // hand-feeding makes, at exactly the same block key, so a cage and a right-click build up the same
        // swim and a swim built by one is fished by the other.
        //
        // It happens here rather than after the session is stored, which is where it used to be: down
        // there the wait had already been priced and the cage's own feed did nothing until the next cast.
        // Here it is in the water before the clock is set, and it is still past every `return false`, so a
        // cast the rod refuses never eats a jar.
        ItemStack rigNow = RodData.get(rod, ComponentSlot.RIG);
        if (RiverFishingConfig.consumeGroundbait() && rigNow.getItem() instanceof RigItem) {
            ItemStack fedStack = RigData.consumeGroundbait(rigNow);
            if (!fedStack.isEmpty()) {
                RodData.set(rod, ComponentSlot.RIG, rigNow);
                FeedZoneData.get(level).feed(waterPos,
                        com.riverfishing.groundbait.GroundbaitNbt.read(fedStack), now);
                FeedZoneData.Query cage = FeedZoneData.get(level).query(waterPos, now);
                ctx.inFeedZone = cage.inZone();
                ctx.feedFreshness = cage.freshness();
                ctx.feedMix = cage.mix();
            }
        }

        // Chunk fishing pressure (Module 7): a fished-out spot makes bites much slower (W_total falls).
        FishingPressureData pressure = FishingPressureData.get(level);
        long chunkKey = ChunkPos.pack(waterPos);
        double depletion = pressure.attractiveness(chunkKey, now, spawnRegen(level));
        // §skills QUICK_BITE: a keen angler feels the bite sooner (shorter wait).
        // §rod-test: an under-loaded blank presents the bait clumsily — a SILENT ~20% fewer bites
        // (longer wait). Never announced (the player only sees the shortened cast).
        double underloadWait = underloaded ? 1.25 : 1.0;
        // §sounding: a hole or a ledge you FOUND holds fish, and the finding is the work being paid
        // for. Applied as a shorter wait, the way a fed spot pays out, so the two stack the way an
        // angler would expect: bait a feature and you have done both halves of the job.
        String spot = SoundingData.get(level).spotAt(waterPos);
        double spotWait = spot == null ? 1.0 : 1.0 / SoundingData.SPOT_BONUS;
        long delay = (long) (outcome.ticksToBite / Math.max(0.1, depletion)
                * AnglerSkills.biteSpeedMult(sp) * underloadWait * spotWait);
        if (spot != null) {
            actionbar(sp, Component.translatable("message.riverfishing.on_spot",
                    Component.translatable("spot.riverfishing." + spot)).withStyle(ChatFormatting.AQUA));
        }
        if (depletion < 0.4) {
            actionbar(sp, Component.translatable("message.riverfishing.depleted").withStyle(ChatFormatting.GRAY));
        }

        // §bite-pacing: each style has its own rhythm. Float fishing is the lively one (bites from
        // ~7 s); a long-range bottom rig takes patience (from ~33 s, waits stretched) but pays in size.
        delay = switch (rodClass) {
            case FLOAT -> Math.max(140, delay);
            // Long cast: from ~33 s, PLUS a big random spread so several rods cast in a row don't all
            // fire at once (§bite-window — the "three rods bite together" fix).
            case BOTTOM -> Math.max(660, (long) (delay * 1.5)) + level.getRandom().nextInt(900);
            default -> Math.max(40, delay); // ACTIVE: the clock only runs while retrieving anyway
        };

        // Feeding frenzy (жор): during a window the whole water body feeds — bites come much faster.
        boolean frenzy = isFrenzy(level);
        if (frenzy) {
            delay = (long) Math.max(20, delay / Math.max(1.0, RiverFishingConfig.frenzySpeed()));
        }
        // §groundbait: a fed spot doesn't just look active — it visibly PULLS bites in faster (up to −40%
        // wait at a fresh spot), on top of the bite-engine bonus for feeding the right groundbait.
        if (ctx.inFeedZone && ctx.feedFreshness > 0) {
            delay = (long) Math.max(20, delay * (1.0 - 0.40 * Mth.clamp(ctx.feedFreshness, 0.0, 1.0)));
        }
        // §honest-tail: a barely-matching setup no longer silently capped at two minutes — the wait is
        // real now, and the player is TOLD the water is dour so they change something instead of camping.
        if (delay > 2400) {
            actionbar(sp, Component.translatable("message.riverfishing.sluggish").withStyle(ChatFormatting.GRAY));
        }

        // ACTIVE rods only "bite" while being retrieved, so their clock starts on the first retrieve tick.
        long biteAt = (rodClass == RodClass.ACTIVE) ? -1 : now + delay;
        FishingSession session = new FishingSession(hand, waterPos, rodClass, delay, biteAt, species);
        // Worn line keeps less of its strain; a dull hook is read from the rig (§3.8).
        int lineWear = WearData.get(RodData.get(rod, ComponentSlot.LINE));
        if (lineWear >= 100) {
            actionbar(sp, Component.translatable("message.riverfishing.line_worn_out").withStyle(ChatFormatting.RED));
        }
        session.lineStrainKg = ctx.lineType.breakingStrainKg(ctx.lineDiameterMm) * WearData.lineStrainMultiplier(lineWear);
        session.dragKg = com.riverfishing.item.ReelItem.dragKgFor(ctx.reelSize);
        session.reelSize = ctx.reelSize;
        session.overloadPenalty = overloadPenalty;
        session.hasLeader = ctx.hasLeader;
        session.leaderProtection = ctx.leaderProtection;
        session.rigType = ctx.rig;
        session.hookWear = minHookWear(RodData.get(rod, ComponentSlot.RIG));
        if (rodClass == RodClass.ACTIVE) {
            // §spin-harder (3): a spinning retrieve is ~2× longer/slower than before (coefficient 10 → 20,
            // higher cap) so the shorter cast still takes real work to wind in. Ultralight keeps its pace.
            double coeff = (type == RodType.SPINNING) ? 20.0 : 10.0;
            int cap = (type == RodType.SPINNING) ? 340 : 220;
            session.retrieveMax = (int) Mth.clamp(castDistance * coeff, 80, cap);
            // §snag: decide this retrieve's snag fate up front — 3% dead (lose rig), 7% recoverable. If
            // snagged, it strikes somewhere in the second half of the retrieve, as the lure nears the bank.
            double sc = RiverFishingConfig.snagChance();
            double sroll = random.nextDouble();
            session.snagOutcome = sroll < SNAG_DEAD_CHANCE * sc ? 2 : (sroll < SNAG_TOTAL_CHANCE * sc ? 1 : 0);
            if (session.snagOutcome != 0) {
                session.snagAtTick = (int) (session.retrieveMax * (0.5 + random.nextDouble() * 0.45));
            }
            // §foul-hook (§9): a moving lure snags a passing fish in the body — a flat 1% per retrieve
            // (× difficulty). Decided up front; strikes somewhere across the retrieve like the snag does.
            session.willFoul = random.nextDouble() < FOUL_CHANCE * RiverFishingConfig.foulHookChance();
            if (session.willFoul) {
                session.foulAtTick = (int) (session.retrieveMax * (0.3 + random.nextDouble() * 0.5));
            }
        }
        session.lineColor = switch (ctx.lineType) {
            case BRAID -> 0xFF4A5A3A;   // dark moss green
            case FLUORO -> 0xFFC8DCE6;  // pale ice blue (near-invisible)
            default -> 0xFFE8E4D0;      // warm mono white
        };
        session.rodStackRef = rod;
        session.rodSlot = session.hand == InteractionHand.MAIN_HAND
                ? sp.getInventory().getSelectedSlot() : -1;
        session.floatKind = floatKind(session.rodClass, session.iceFishing,
                RodData.get(rod, ComponentSlot.RIG));
        com.riverfishing.item.RodData.setLineOut(rod, true); // §rod-layers: hide in-hand tackle overlays
        // §live-conditions: keep the snapshot + current speed so the waiting line can re-read the world.
        session.ctx = ctx;
        session.biteSpeed = currentBiteSpeed(level, ctx, outcome.totalWeight);
        SESSIONS.put(sp.getUUID(), session);
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, waterPos, 0f, session.lineColor,
                session.floatKind));

        pressure.addCast(chunkKey, now);

        double typeRate = ctx.lineType == LineType.FLUORO ? 0.6 : 1.0; // fluoro wears slower (§3.8)
        // Fractional wear: with the slower §balance rate a single cast usually adds nothing; the
        // remainder becomes a probability so wear still accumulates over many casts.
        double castWear = typeRate * lineWearScaled();
        int whole = (int) castWear;
        if (level.getRandom().nextDouble() < castWear - whole) whole++;
        addLineWear(rod, whole);
        playCast(level, waterPos, rodClass);
        // §cast-anim: the casting swing — moves the arm + rod for every observer, and drives the local
        // player's first-person rod whip (RodItemRenderer reads the swing progress).
        sp.swing(hand, true);
        // §spin-harder (2): actively working a lure burns hunger — 1 whole food point every 4 casts.
        if (rodClass == RodClass.ACTIVE) {
            int n = ACTIVE_CAST_COUNT.merge(sp.getUUID(), 1, Integer::sum);
            if (n % 4 == 0) {
                net.minecraft.world.food.FoodData food = sp.getFoodData();
                food.setFoodLevel(Math.max(0, food.getFoodLevel() - 1));
            }
        }
        if (frenzy) {
            actionbar(sp, Component.translatable("message.riverfishing.cast_frenzy").withStyle(ChatFormatting.AQUA));
        } else {
            actionbar(sp, Component.translatable(rodClass == RodClass.ACTIVE
                    ? "message.riverfishing.cast_spin"
                    : "message.riverfishing.cast_out"));
        }
        return true;
    }

    /**
     * §ice-fishing: start vertical fishing at a drilled ice hole (right-clicked with a winter rod). No
     * casting/aiming — the mormyshka drops straight down. Winter conditions are forced so only cold-water
     * fish bite; the session is then worked by jigging ({@link #iceJig}) until the кивок twitches.
     */
    public static boolean startIceFishing(ServerPlayer sp, BlockPos holePos, InteractionHand hand) {
        ServerLevel level = sp.level();
        long now = level.getGameTime();
        ItemStack rod = sp.getItemInHand(hand);
        if (!RodData.isAssembled(rod)) {
            actionbar(sp, Component.translatable(RodData.missingKey(rod)).withStyle(ChatFormatting.RED));
            return false;
        }
        if (SESSIONS.containsKey(sp.getUUID())) return false; // one line at a time
        RodType type = ((RodItem) rod.getItem()).rodType();
        RodData.ensureNativeRig(rod, type);
        BlockPos waterPos = findWaterColumn(level, holePos.getX() + 0.5, holePos.getY() + 0.5, holePos.getZ() + 0.5);
        if (waterPos == null) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return false;
        }
        WaterBody body = WaterBodyCache.forLevel(level).get(level, waterPos);
        if (body.type() == WaterType.NONE) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return false;
        }
        BiteContext ctx = buildContext(sp, level, rod, hand, body, waterPos, 2.0, now);
        ctx.iceHole = true;
        ctx.season = com.riverfishing.engine.Season.WINTER; // a hole in the ice = winter conditions
        RandomSource random = level.getRandom();
        BiteEngine.Outcome outcome = BiteEngine.evaluate(FishProfileManager.get().all(), ctx, random);
        if (!outcome.willBite()) {
            noBitesHint(sp, ctx);
            return false;
        }
        Identifier species = maybeKoi(outcome.pickSpecies(random), ctx, random);

        FishingPressureData pressure = FishingPressureData.get(level);
        long chunkKey = ChunkPos.pack(waterPos);
        double depletion = pressure.attractiveness(chunkKey, now, spawnRegen(level));
        // A patient winter wait — jigging the mormyshka in a steady rhythm is what pulls the bite in.
        long delay = (long) Mth.clamp(outcome.ticksToBite / Math.max(0.1, depletion) * AnglerSkills.biteSpeedMult(sp), 200, 2400);

        FishingSession session = new FishingSession(hand, waterPos, RodClass.FLOAT, delay, now + delay, species);
        session.iceFishing = true;
        int lineWear = WearData.get(RodData.get(rod, ComponentSlot.LINE));
        session.lineStrainKg = ctx.lineType.breakingStrainKg(ctx.lineDiameterMm) * WearData.lineStrainMultiplier(lineWear);
        session.reelSize = 0;
        session.hasLeader = ctx.hasLeader;
        session.leaderProtection = ctx.leaderProtection;
        session.rigType = ctx.rig;
        session.hookWear = minHookWear(RodData.get(rod, ComponentSlot.RIG));
        session.lineColor = switch (ctx.lineType) {
            case BRAID -> 0xFF4A5A3A;
            case FLUORO -> 0xFFC8DCE6;
            default -> 0xFFE8E4D0;
        };
        session.rodStackRef = rod;
        session.rodSlot = session.hand == InteractionHand.MAIN_HAND
                ? sp.getInventory().getSelectedSlot() : -1;
        session.floatKind = floatKind(session.rodClass, session.iceFishing,
                RodData.get(rod, ComponentSlot.RIG));
        com.riverfishing.item.RodData.setLineOut(rod, true); // §rod-layers: hide in-hand tackle overlays
        session.ctx = ctx;
        session.biteSpeed = currentBiteSpeed(level, ctx, outcome.totalWeight);
        SESSIONS.put(sp.getUUID(), session);
        pressure.addCast(chunkKey, now);
        // §ice-fishing: no float on the line under the ice — the line just drops into the hole (bobber=false).
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, waterPos, 0f, session.lineColor, (byte) 0));
        level.playSound(null, waterPos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5f, 1.4f);
        actionbar(sp, Component.translatable("message.riverfishing.ice_fishing").withStyle(ChatFormatting.AQUA));
        return true;
    }

    /**
     * §ice-jig (variant B, phase 1): a tap works the mormyshka in the hole. A STEADY rhythm (a jig every
     * ~0.4–1.0 s) draws fish in fast; frantic spamming or lazy jigging barely helps. The bite (the кивок
     * twitch) then triggers the normal strike/pull QTE — the "phase 2" nod strike.
     */
    private static void iceJig(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        long gap = now - session.lastJigTick;
        boolean steady = session.lastJigTick == 0 || (gap >= 8 && gap <= 20);
        session.lastJigTick = now;
        if (session.biteAtTick > now) {
            session.biteAtTick = Math.max(now + 10, session.biteAtTick - (steady ? 34 : 8));
        }
        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS,
                steady ? 0.35f : 0.25f, steady ? 1.7f : 1.3f);
        level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                session.target.getZ() + 0.5, steady ? 3 : 1, 0.1, 0.02, 0.1, 0.02);
        actionbar(sp, Component.translatable(steady
                ? "message.riverfishing.jig_good" : "message.riverfishing.jig").withStyle(ChatFormatting.AQUA));
    }

    /**
     * §spawn-recovery: spring is spawning season (нерест) — fished-out water restocks ~2.5x faster
     * (needs Serene Seasons; without it the season is null and recovery stays neutral).
     */
    private static double spawnRegen(ServerLevel level) {
        return SeasonProvider.getSeason(level) == com.riverfishing.engine.Season.SPRING ? 2.5 : 1.0;
    }

    /**
     * Feeding frenzy (жор): two deterministic windows per in-game day (~100 s each) when the whole
     * water body feeds — derived from the world seed and day number, so every player sees the same
     * frenzy with no saved state. During a window bites are 3x faster and fish splash visibly.
     */
    public static boolean isFrenzy(ServerLevel level) {
        long dayTime = level.getOverworldClockTime();
        long day = dayTime / 24000L;
        long t = dayTime % 24000L;
        java.util.Random r = new java.util.Random(level.getSeed() ^ (day * 0x9E3779B97F4A7C15L));
        long s1 = 500 + r.nextInt(8000);    // a morning-ish window
        long s2 = 11500 + r.nextInt(9000);  // an evening/night window
        return (t >= s1 && t < s1 + 2000) || (t >= s2 && t < s2 + 2000);
    }

    // §koi: the five ornamental koi are a hidden collectible — never in the normal bite pool
    // (their profile base is 0). Instead, a CARP-rig catch of a carp-family fish has a small chance
    // to turn out to be a koi. A cherry-grove pond is proper koi water, so there it's far likelier.
    private static final Identifier[] KOI = {
            com.riverfishing.RiverFishing.id("carp_koi_kohaku"),
            com.riverfishing.RiverFishing.id("carp_koi_tancho_sanke"),
            com.riverfishing.RiverFishing.id("carp_koi_showa_sanke"),
            com.riverfishing.RiverFishing.id("carp_koi_asagi"),
            com.riverfishing.RiverFishing.id("carp_koi_bekko"),
    };
    private static final double KOI_CHANCE = 0.005;       // 0.5% on carp tackle anywhere
    private static final double KOI_CHANCE_CHERRY = 0.35; // far higher in a cherry-grove pond

    private static Identifier maybeKoi(Identifier picked, BiteContext ctx, RandomSource random) {
        if (ctx.rig != RigType.CARP || !isCarpFamily(picked)) return picked;
        double chance = ctx.biomeGroups.contains("cherry") ? KOI_CHANCE_CHERRY : KOI_CHANCE;
        return random.nextDouble() < chance ? KOI[random.nextInt(KOI.length)] : picked;
    }

    private static boolean isCarpFamily(Identifier id) {
        String p = id.getPath();
        return "carp".equals(p) || "mirror_carp".equals(p) || "wild_carp".equals(p);
    }

    /** First water block scanning straight down a column — where the charged cast lands. */
    private static BlockPos findWaterColumn(ServerLevel level, double x, double yStart, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(yStart), Mth.floor(z));
        for (int i = 0; i < 24 && p.getY() > level.getMinY(); i++, p.move(0, -1, 0)) {
            if (WaterBodyDetector.isWater(level, p)) {
                return p.immutable();
            }
            // §cast-ceiling: the tackle FALLS to the water, so it cannot arrive under a floor. Without
            // this the scan tunnelled straight through the ground and hooked a cave lake twenty blocks
            // under a player standing on grass — reported, and the reason a cast could land somewhere
            // the angler could not see, reach or have aimed at.
            //
            // Ice is the one thing it must fall through: the ice check just below this call is what
            // says "drill a hole", and stopping here would answer a frozen lake with "no water".
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
            if (com.riverfishing.item.IceAugerItem.isIce(st)) continue;
            if (!st.getCollisionShape(level, p).isEmpty()) return null;
        }
        return null;
    }

    /**
     * §click-retrieve (0.5.1): one crank of the reel — each RIGHT-CLICK advances the lure a few ticks
     * and its GAP from the previous click is the lure action (#игры-с-приманкой). A wobbler/crankbait
     * wants a steady rhythm (its swim-action dies otherwise), spinner/spoon/jig forgive almost any
     * cadence, and the popper keeps its own pop-pause rules inside retrieveTick. Holding the button
     * auto-repeats ~every 4 ticks — that still winds line in, but the cadence is too fast to attract.
     */
    private static void clickRetrieve(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        long gap = session.retrieveTicks == 0 ? 12 : now - session.lastClickTick;
        session.lastClickTick = now;
        if (!session.topwater && session.biteAtTick > now) {
            boolean good = session.lureStrict ? (gap >= 8 && gap <= 18) : (gap >= 5 && gap <= 30);
            // A well-worked lure CALLS the fish — good cadence pulls the take closer, sloppy barely.
            session.biteAtTick = Math.max(now + 5, session.biteAtTick - (good ? 10 : 2));
        }
        for (int i = 0; i < 4; i++) {
            if (SESSIONS.get(sp.getUUID()) != session || session.bitten || session.fighting) return;
            retrieveTick(sp);
        }
    }

    /** Advances the retrieve one tick: clicks feed it 4 at a time, trolling drives it directly. */
    public static void retrieveTick(ServerPlayer sp) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session == null || session.rodClass != RodClass.ACTIVE || session.bitten || session.fighting) return;
        ServerLevel level = sp.level();
        long now = level.getGameTime();

        if (session.biteAtTick < 0) {
            session.biteAtTick = now + session.biteDelay; // start the clock on first retrieve
        }

        // §trolling (0.5.1): the boat TRAILS the lure — the line never comes in and never "empties",
        // there's no snag/foul over open water, and the take SELF-STRIKES: the boat's own momentum
        // sets the hook (which is exactly how real trolling works — no подсечка). Just fight it.
        if (session.trolling) {
            if (now >= session.biteAtTick) {
                session.bitten = true;
                level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 0.9f);
                level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5,
                        session.target.getY() + 1.0, session.target.getZ() + 0.5, 20, 0.4, 0.2, 0.4, 0.3);
                actionbar(sp, Component.translatable("message.riverfishing.trolling_fish_on")
                        .withStyle(ChatFormatting.RED));
                hookUp(sp, level, session, now);
            }
            return;
        }

        session.retrieving = true;
        session.retrieveTicks++;

        // §topwater (0.4.0): a popper is fished on the SURFACE with a pop-pause cadence, not a straight
        // crank. Detected once per cast from the rig's lure slot; everything below rides the same
        // hold-to-retrieve input — a "pop" is simply resuming the retrieve after a short pause.
        if (session.retrieveTicks == 1) {
            ItemStack tw = sessionRod(sp, session);
            if (tw.getItem() instanceof RodItem) {
                ItemStack rg = RodData.get(tw, ComponentSlot.RIG);
                java.util.List<String> lures = rg.getItem() instanceof RigItem
                        ? RigData.baitIds(rg) : java.util.List.of();
                session.topwater = lures.contains("popper");
                // §lure-game: a wobbler/crankbait swims only at a steady crank — strict cadence window.
                session.lureStrict = lures.contains("wobbler") || lures.contains("crankbait");
            }
            session.popRhythm = 1.0;
            session.lastRetrieveTick = now;
        }
        if (session.topwater) {
            long gap = now - session.lastRetrieveTick;
            session.lastRetrieveTick = now;
            double tprog = session.retrieveMax > 0
                    ? Mth.clamp((double) session.retrieveTicks / session.retrieveMax, 0.0, 1.0) : 0.0;
            double lx = Mth.lerp(tprog, session.target.getX() + 0.5, sp.getX());
            double lz = Mth.lerp(tprog, session.target.getZ() + 0.5, sp.getZ());
            double ly = session.target.getY() + 1.0;
            if (gap >= 6 && gap <= 30) {
                // A proper pop after a pause: the popper spits and bloops — this is what calls the fish up.
                session.popRhythm = Math.min(1.5, session.popRhythm + 0.15);
                level.playSound(null, BlockPos.containing(lx, ly, lz), SoundEvents.FISHING_BOBBER_SPLASH,
                        SoundSource.PLAYERS, 0.5f, 1.7f);
                level.sendParticles(ParticleTypes.SPLASH, lx, ly, lz, 6, 0.2, 0.02, 0.2, 0.12);
            } else if (gap <= 1) {
                session.popRhythm = Math.max(0.6, session.popRhythm - 0.01); // dragged under — wrong lure work
            } else if (gap > 60) {
                session.popRhythm = Math.max(0.8, session.popRhythm - 0.10); // sat dead too long
            }
            if (session.retrieveTicks % 3 == 0) { // the surface wake trailing the lure
                level.sendParticles(ParticleTypes.FISHING, lx, ly, lz, 2, 0.12, 0.0, 0.12, 0.02);
            }
            // Good cadence CALLS the fish — it advances the bite clock; bad cadence stalls it.
            if (session.biteAtTick > 0 && session.retrieveTicks % 20 == 0) {
                session.biteAtTick -= (long) ((session.popRhythm - 1.0) * 20.0);
            }
            // Telegraph: a boil right behind the lure moments before the take.
            if (!session.blowupTelegraphed && session.biteAtTick > 0 && now >= session.biteAtTick - 15) {
                session.blowupTelegraphed = true;
                level.sendParticles(ParticleTypes.BUBBLE, lx - 0.4, ly - 0.1, lz, 14, 0.25, 0.05, 0.25, 0.02);
                level.playSound(null, BlockPos.containing(lx, ly, lz), SoundEvents.FISH_SWIM,
                        SoundSource.PLAYERS, 0.8f, 0.8f);
            }
        }

        // §retrieve-visual: the lure actually COMES IN as you wind — pull the client's line end toward
        // the bank in step with how much line you've reeled (was only moving once a fish was on).
        if (session.retrieveTicks % 2 == 0 && session.retrieveMax > 0) {
            float prog = Mth.clamp((float) session.retrieveTicks / session.retrieveMax, 0f, 1f);
            ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target, prog,
                    session.lineColor, (byte) 0));
        }

        // The reel ticks while winding line (§reel-sound) — quiet fast clicks at the player's hands.
        if (session.retrieveTicks % 4 == 0) {
            level.playSound(null, sp.blockPosition(), SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                    SoundSource.PLAYERS, 0.35f, 1.7f + (session.retrieveTicks % 8 == 0 ? 0.1f : 0f));
        }

        if (session.retrieveTicks % 4 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                    session.target.getZ() + 0.5, 2, 0.15, 0.0, 0.15, 0.05);
        }

        // §snag: this retrieve's snag fate (rolled at cast) strikes as the lure nears the bank.
        if (session.snagOutcome != 0 && session.retrieveTicks >= session.snagAtTick) {
            handleSnag(sp, level, session, session.snagOutcome == 2);
            return;
        }
        if (session.willFoul && session.retrieveTicks >= session.foulAtTick) {
            session.foulHooked = true;
            session.bitten = true;
            hookUp(sp, level, session, now);
            return;
        }

        if (now >= session.biteAtTick && !spooked(level, session, now)) {
            session.bitten = true;
            // §strike-qte (2.4): the take fires a hook-set runner — stop it in the zone (release the retrieve,
            // or click) to set the hook. Deliberately EASY (imitating a подсечка, not a reaction test): slow
            // marker, wide zone, ~3 s window so there's no rush. §topwater: the blowup is the exception —
            // a shorter, reactive window sold by the surface explosion.
            session.biteWindowEnd = now + (session.topwater ? 35 : 60);
            if (session.topwater) {
                double tprog = session.retrieveMax > 0
                        ? Mth.clamp((double) session.retrieveTicks / session.retrieveMax, 0.0, 1.0) : 0.0;
                double lx = Mth.lerp(tprog, session.target.getX() + 0.5, sp.getX());
                double lz = Mth.lerp(tprog, session.target.getZ() + 0.5, sp.getZ());
                double ly = session.target.getY() + 1.0;
                // §topwater blowup: the strike EXPLODES on the surface — the money shot.
                level.sendParticles(ParticleTypes.SPLASH, lx, ly + 0.1, lz, 36, 0.45, 0.25, 0.45, 0.45);
                level.sendParticles(ParticleTypes.BUBBLE_POP, lx, ly, lz, 16, 0.3, 0.1, 0.3, 0.1);
                level.playSound(null, BlockPos.containing(lx, ly, lz), SoundEvents.FISHING_BOBBER_SPLASH,
                        SoundSource.PLAYERS, 1.0f, 0.6f);
                level.playSound(null, BlockPos.containing(lx, ly, lz), SoundEvents.DOLPHIN_JUMP,
                        SoundSource.PLAYERS, 0.7f, 0.9f);
                actionbar(sp, Component.translatable("message.riverfishing.topwater_blowup")
                        .withStyle(ChatFormatting.RED));
            } else {
                actionbar(sp, Component.translatable("message.riverfishing.strike").withStyle(ChatFormatting.AQUA));
            }
            playBite(level, session.target);
            startActiveStrikeTiming(sp, session, now);
        } else if (session.retrieveTicks >= session.retrieveMax) {
            endSession(sp, session);
            sp.stopUsingItem();
            actionbar(sp, Component.translatable("message.riverfishing.retrieve_empty").withStyle(ChatFormatting.GRAY));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.EMPTY);
        }
    }

    /** A snag near the bank (§7.1): {@code lost} = a dead (глухой) snag that costs the rig, else tug free. */
    private static void handleSnag(ServerPlayer sp, ServerLevel level, FishingSession session, boolean lost) {
        sp.stopUsingItem();
        ItemStack rod = sessionRod(sp, session);
        addLineWear(rod, 3);
        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6f, 0.5f);
        if (!lost) {
            actionbar(sp, Component.translatable("message.riverfishing.snag_free").withStyle(ChatFormatting.YELLOW));
        } else {
            if (rod.getItem() instanceof RodItem) {
                RodData.set(rod, ComponentSlot.RIG, ItemStack.EMPTY);
            }
            addLineWear(rod, 3);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.snag_lost").withStyle(ChatFormatting.RED));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.SNAG);
        }
        endSession(sp, session);
    }

    /** Called when the player releases right-click on a spinning rod. */
    public static void onRetrieveStop(ServerPlayer sp) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session == null || session.rodClass != RodClass.ACTIVE) return;
        if (session.fighting) return;
        if (session.bitten) {
            // §strike-qte (2.4): letting go DURING the take is a valid hook-set — check the runner now.
            if (session.floatPeriod > 0 && sp.level().getGameTime() <= session.biteWindowEnd) {
                activeStrike(sp, sp.level(), session, sp.level().getGameTime());
            }
        }
        // §click-retrieve (0.5.1): releasing the button is NOT "wind in" any more — line only comes
        // in by cranking (clicks). Ending the session here would kill a fresh cast on a stray release.
    }

    // ---- per-tick progress (FLOAT / BOTTOM waiting, and the fight for all classes) ----

    /**
     * §sounding: a marker cast. Walks the aim line metre by metre, measures the bed under each, and
     * writes the lot into {@link SoundingData} — which is how a swim stops being a number the engine
     * knows and the angler guesses at.
     *
     * <p>Features are read ALONG this one line, because a line is the only place this cast has
     * evidence. A hole inferred from two casts that never crossed would be invented bed.
     */
    public static void takeSounding(ServerPlayer sp, ServerLevel level) {
        net.minecraft.world.phys.Vec3 look = sp.getLookAngle();
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        if (hl < 1e-3) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return;
        }
        // §sounding-swath: five blocks wide, not one. A one-block line meant twenty casts to learn
        // one swim, which is a chore wearing the costume of realism; a marker float drags a bit of bed
        // either side of its line anyway, and five is the width at which the map fills at the pace
        // an angler is actually willing to cast.
        final int FROM = PROFILE_FROM, N = PROFILE_N, HALF = 2;
        double dx = look.x / hl, dz = look.z / hl;
        double sx = -dz, sz = dx;                       // across the line
        int wet = 0, y = sp.getBlockY();
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        SoundingData data = SoundingData.get(level);
        for (int o = -HALF; o <= HALF; o++) {
            int[] xs = new int[N], zs = new int[N], line = new int[N];
            for (int i = 0; i < N; i++) {
                double d = FROM + i;
                double px = sp.getX() + dx * d + sx * o;
                double pz = sp.getZ() + dz * d + sz * o;
                xs[i] = Mth.floor(px);
                zs[i] = Mth.floor(pz);
                BlockPos surface = findWaterColumn(level, px, sp.getEyeY() + 2.0, pz);
                if (surface == null) {
                    line[i] = -1;
                    continue;
                }
                y = surface.getY();
                line[i] = measureDepth(level, surface);
                wet++;
            }
            // Each strand of the swath is read on its own: features come from evidence along a line,
            // and the strands are five lines rather than one wide guess.
            found.addAll(data.record(y, xs, zs, line));
        }
        if (wet == 0) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return;
        }
        level.playSound(null, sp.blockPosition(), SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.PLAYERS, 0.7f, 1.4f);
        if (found.isEmpty()) {
            actionbar(sp, Component.translatable("message.riverfishing.sounded", wet)
                    .withStyle(ChatFormatting.AQUA));
            return;
        }
        // Finding something is the point of the exercise, so it is said out loud rather than in the
        // corner of the eye.
        String kind = SoundingData.get(level).spotAt(found.get(0));
        sp.sendSystemMessage(Component.translatable("message.riverfishing.spot_found",
                Component.translatable("spot.riverfishing." + (kind == null ? "hole" : kind)),
                found.get(0).getX(), found.get(0).getZ()).withStyle(ChatFormatting.GOLD));
        level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** Where the finder's bed profile starts and how far it reads, metres out from the rod. */
    public static final int PROFILE_FROM = 2, PROFILE_N = 23;
    /** The map window, blocks either side of the spot. The face draws it at three pixels a block. */
    public static final int MAP_REACH = 24;

    /**
     * §bed-type: what the bed is made of under this column — the first thing that is not water. The
     * engine does not read this (no profile asks for it), so it is description, not a gate; the reason
     * it is on the screen is that a real sounder shows it and an angler reads it.
     */
    public static byte bedType(ServerLevel level, BlockPos surface) {
        BlockPos.MutableBlockPos p = surface.mutable();
        while (p.getY() > level.getMinY()
                && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
            p.move(0, -1, 0);
        }
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
        if (st.is(net.minecraft.tags.BlockTags.SAND)) return 1;
        if (st.is(net.minecraft.world.level.block.Blocks.GRAVEL)) return 2;
        if (st.is(net.minecraft.world.level.block.Blocks.CLAY)) return 3;
        if (st.is(net.minecraft.tags.BlockTags.DIRT) || st.is(net.minecraft.world.level.block.Blocks.MUD)) return 4;
        if (st.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || st.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)) return 5;
        return 6;
    }

    /**
     * §finder-profile: the bed along the aim line, one reading a metre — the same walk the marker
     * cast makes, read and not recorded. This is what a sounder actually draws: not "eight metres",
     * but the SHAPE of the bottom out in front of you, which is where a hole or a bar is visible as a
     * hole or a bar. Depths in {@code d} (a negative for a metre that is not water), bed types in
     * {@code b}.
     */
    private static CompoundTag profileAlong(ServerPlayer sp, ServerLevel level) {
        CompoundTag out = new CompoundTag();
        net.minecraft.world.phys.Vec3 look = sp.getLookAngle();
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        byte[] d = new byte[PROFILE_N], b = new byte[PROFILE_N];
        if (hl < 1e-3) {
            java.util.Arrays.fill(d, (byte) -1);
        } else {
            for (int i = 0; i < PROFILE_N; i++) {
                double dist = PROFILE_FROM + i;
                double px = sp.getX() + (look.x / hl) * dist, pz = sp.getZ() + (look.z / hl) * dist;
                BlockPos surface = findWaterColumn(level, px, sp.getEyeY() + 2.0, pz);
                if (surface == null) {
                    d[i] = -1;
                    continue;
                }
                d[i] = (byte) Math.min(127, measureDepth(level, surface));
                b[i] = bedType(level, surface);
            }
        }
        out.putByteArray("d", d);
        out.putByteArray("b", b);
        return out;
    }

    /**
     * §finder-map: which columns of the map window are open water at the surface, so the map can draw
     * the lake's SHAPE and the bank around it — a map of sounded cells alone floated in the dark with
     * nothing to say where the shore was. Heightmap reads only; a cave lake under the bank is not
     * water you can cast to and does not show.
     */
    private static byte[] wetMask(ServerLevel level, BlockPos centre) {
        int n = MAP_REACH * 2 + 1;
        byte[] out = new byte[n * n];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dz = -MAP_REACH; dz <= MAP_REACH; dz++) {
            for (int dx = -MAP_REACH; dx <= MAP_REACH; dx++) {
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                p.set(x, y, z);
                out[(dz + MAP_REACH) * n + (dx + MAP_REACH)] =
                        (byte) (level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER) ? 1 : 0);
            }
        }
        return out;
    }

    /**
     * §sounding: what has been measured around this spot, for the screen's map — a window of columns
     * with their depths, and the features found in them.
     *
     * <p>A window rather than the whole world: the map is drawn at a few pixels a metre, so beyond a
     * short reach it is bytes for something nobody can see.
     *
     * <p>ponytail: walks every column ever sounded to find the ones nearby, once per screen open. Fine
     * at the scale one angler measures; bucket the store by chunk if a server ever sounds a lake flat.
     */
    private static ListTag soundingMap(ServerLevel level, BlockPos centre) {
        final int REACH = MAP_REACH;
        SoundingData data = SoundingData.get(level);
        ListTag out = new ListTag();
        for (var e : data.depths().entrySet()) {
            int x = SoundingData.keyX(e.getKey()), z = SoundingData.keyZ(e.getKey());
            if (Math.abs(x - centre.getX()) > REACH || Math.abs(z - centre.getZ()) > REACH) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("x", x - centre.getX());
            t.putInt("z", z - centre.getZ());
            t.putInt("d", e.getValue());
            String spot = data.spots().get(e.getKey());
            if (spot != null) t.putString("s", spot);
            out.add(t);
        }
        return out;
    }

    /**
     * §finder-hud: a sounding a second for whoever is holding a finder, so the strip on their HUD is
     * live as they walk the bank. Nothing is sent when they are not aiming at water — the strip fades
     * on its own rather than freezing on a reading that stopped being true.
     */
    public static void finderHudTick(ServerPlayer sp) {
        if (sp.tickCount % 20 != 0) return;
        boolean holding = isFinder(sp.getMainHandItem()) || isFinder(sp.getOffhandItem());
        if (!holding) return;
        ServerLevel level = sp.level();
        BlockPos water = com.riverfishing.item.WaterProbeItem.findWater(level, sp);
        if (water == null) return;
        com.riverfishing.network.ModNetwork.toPlayer(sp,
                new com.riverfishing.network.FinderPacket(finderPayload(sp, level, water, false), true));
    }

    private static boolean isFinder(ItemStack stack) {
        return stack.getItem() instanceof com.riverfishing.item.WaterProbeItem probe && !probe.admin();
    }

    public static void tick(ServerPlayer sp) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session == null) return;
        ServerLevel level = sp.level();
        long now = level.getGameTime();

        // The line is tied to THE rod it was cast with: switching hotbar slots (a different stack in
        // hand) drops the cast (§session-guard), same as walking away.
        ItemStack inHand = sp.getItemInHand(session.hand);
        // §session-guard: compare the SLOT, never the stack object. An ItemStack reference goes stale
        // the moment anything rewrites the inventory slot, and this branch ends the cast with no
        // message — which reads as the rod reeling itself in the instant you cast.
        boolean holdingRod = inHand.getItem() instanceof RodItem
                && (session.rodSlot < 0 || sp.getInventory().getSelectedSlot() == session.rodSlot);
        boolean tooFar = sp.distanceToSqr(session.target.getX() + 0.5, sp.getY(), session.target.getZ() + 0.5)
                > MAX_SESSION_DISTANCE * MAX_SESSION_DISTANCE;
        if (!holdingRod || tooFar) {
            // §fight-footwork teaches backing away, and a long cast leaves only a few blocks of room —
            // walking off the end of it used to delete the fight in silence.
            if (tooFar && session.fighting) {
                actionbar(sp, Component.translatable("message.riverfishing.too_far")
                        .withStyle(ChatFormatting.YELLOW));
            }
            endSession(sp, session);
            return;
        }

        // Refresh the line for everyone tracking (§line-multiplayer): players who walked into view
        // mid-cast get the line, and clients expire lines that stop being refreshed. Use the CURRENT
        // visual progress — for a spinning rod mid-retrieve/bite that's how far the lure is reeled in,
        // NOT landProgress (which is still 0 pre-fight and would snap the line back out — §line-jump).
        if (now % 40 == 0) {
            float visProgress;
            if (session.fighting) {
                visProgress = (float) Mth.clamp(session.landProgress, 0.0, 1.0);
            } else if (session.rodClass == RodClass.ACTIVE && session.retrieveMax > 0) {
                visProgress = Mth.clamp((float) session.retrieveTicks / session.retrieveMax, 0f, 1f);
            } else {
                visProgress = 0f;
            }
            ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target,
                    visProgress, session.lineColor, session.floatKind,
                    session.bitten && !session.fighting && now <= session.biteWindowEnd,
                    fightStress(session)));
        }

        if (session.fighting) {
            tickFight(sp, level, session, now);
            return;
        }

        if (session.rodClass == RodClass.ACTIVE) {
            // §click-retrieve: a lure left DEAD in the water doesn't get struck — an idle line pushes
            // the take out until it's worked again. The popper's pause is part of its game (longer
            // grace), and a trolled lure is always working (the boat moves it).
            if (!session.trolling && !session.bitten && session.retrieveTicks > 0 && session.biteAtTick > 0
                    && now - session.lastClickTick > (session.topwater ? 80 : 30)
                    && now >= session.biteAtTick - 5) {
                session.biteAtTick = now + 25;
            }
            // Bites only fire during retrieve (handled in retrieveTick); here we only time out the strike.
            if (session.bitten && now > session.biteWindowEnd) {
                endSession(sp, session);
                actionbar(sp, Component.translatable("message.riverfishing.missed").withStyle(ChatFormatting.GRAY));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
            }
            return;
        }

        // FLOAT / BOTTOM: wait for the bite, then a window to strike.
        if (!session.bitten) {
            // §live-conditions (0.5.0): every 15 s the waiting line re-reads the world — dusk, a weather
            // change, a starting frenzy or freshly thrown groundbait rescale the REMAINING wait, and the
            // biter is re-picked from the new weights. The cast snapshot no longer decides everything,
            // so sitting out a long bottom wait responds to the world exactly like a fresh cast would.
            if (session.ctx != null && session.biteAtTick > now && now % 300 == 0) {
                reEvaluate(level, session, now);
            }
            if (now >= session.biteAtTick && !spooked(level, session, now)) {
                session.bitten = true;
                session.biteWindowEnd = now + biteWindow(session.rodClass);
                // §silent-bite: NO audible cue without an alarm — watch the float / the line.
                playBite(level, session.target);
                // §catch-the-moment: NO "Поклёвка!" text — the bobber PLUNGES on the client and
                // that's the whole cue; spotting it is the game.
                ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target, 0f,
                        session.lineColor, session.floatKind, true));
                // Only ONE QTE per catch (§pull-qte): reel-less rods save their timing for the
                // pull-out, so their strike is a plain click; reeled float rods keep the strike QTE.
                if (session.rodClass == RodClass.FLOAT && session.reelSize > 0) {
                    startFloatTiming(sp, session, now);
                }
            } else if (now % 20 == 0) {
                level.sendParticles(ParticleTypes.FISHING,
                        session.target.getX() + 0.5, session.target.getY() + 1.0, session.target.getZ() + 0.5,
                        1, 0.1, 0.0, 0.1, 0.0);
                // During a frenzy the water visibly boils: fish splash around the float.
                if (isFrenzy(level)) {
                    RandomSource r = level.getRandom();
                    level.sendParticles(ParticleTypes.SPLASH,
                            session.target.getX() + 0.5 + (r.nextDouble() - 0.5) * 6.0,
                            session.target.getY() + 1.0,
                            session.target.getZ() + 0.5 + (r.nextDouble() - 0.5) * 6.0,
                            6, 0.3, 0.1, 0.3, 0.15);
                    if (r.nextInt(3) == 0) {
                        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH,
                                SoundSource.AMBIENT, 0.3f, 1.4f + r.nextFloat() * 0.3f);
                    }
                }
            }
        } else if (now > session.biteWindowEnd) {
            eatBait(sp, session);   // §consumables: it had the bait — you just did not set the hook
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.missed").withStyle(ChatFormatting.GRAY));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
        }
    }

    /**
     * §spook: the fish at the bait have just been frightened, so this attempt comes to nothing and the
     * next one is a second or two out.
     *
     * <p>Applied at the ROLL rather than folded into the bite speed on purpose. Bite speed is a snapshot
     * refreshed every fifteen seconds; spook rises and falls in single seconds, and a mechanic whose whole
     * job is to answer "you just stamped along the bank" has to answer within a second of it happening.
     *
     * <p>Nothing is said to the player. The rings running out across the water already said it, and a
     * text warning would turn reading the water into reading a HUD (§spook-quiet).
     */
    private static boolean spooked(ServerLevel level, FishingSession session, long now) {
        if (!fishAreSpooked(level, session.target, now)) return false;
        session.biteAtTick = now + 20 + level.getRandom().nextInt(40);
        return true;
    }

    /**
     * §spook: are the fish at this spot too frightened to take right now? Public because a rod on the pod
     * fishes the same water under the same rule — walk up to your own bivvy and stamp about, and the
     * podded line goes quiet exactly like the one in your hands.
     */
    public static boolean fishAreSpooked(ServerLevel level, BlockPos target, long now) {
        double s = SpookData.of(level).at(target, now);
        return s > 0.02 && level.getRandom().nextDouble() < s;
    }

    /** §live-conditions: bite speed at this spot right now — swarm-capped W × frenzy × fresh feed. */
    private static double currentBiteSpeed(ServerLevel level, BiteContext ctx, double totalWeight) {
        if (totalWeight <= 1e-6) return 0.0;
        double s = BiteEngine.effectiveWeight(totalWeight);
        if (isFrenzy(level)) s *= Math.max(1.0, RiverFishingConfig.frenzySpeed());
        if (ctx.inFeedZone && ctx.feedFreshness > 0) {
            s /= Math.max(0.2, 1.0 - 0.40 * Mth.clamp(ctx.feedFreshness, 0.0, 1.0));
        }
        return s;
    }

    /** §live-conditions: refresh the dynamic half of the cast snapshot and rescale the remaining wait.
     *  Public: the rod pod re-evaluates its docked lines through here too. */
    public static void reEvaluate(ServerLevel level, FishingSession session, long now) {
        BiteContext ctx = session.ctx;
        ctx.season = ctx.iceHole ? com.riverfishing.engine.Season.WINTER : SeasonProvider.getSeason(level);
        ctx.time = TimeOfDay.fromDayTime(level.getOverworldClockTime());
        ctx.weather = level.isThundering() ? Weather.THUNDER : (level.isRaining() ? Weather.RAIN : Weather.CLEAR);
        ctx.pressureFactor = com.riverfishing.engine.BarometricPressure.biteFactor(level);
        FishingPressureData popData = FishingPressureData.get(level);
        long popChunk = ChunkPos.pack(session.target);
        double popRegen = spawnRegen(level);
        ctx.speciesFactor = id -> popData.speciesAttractiveness(popChunk, id.getPath(), now, popRegen);
        // Groundbait thrown AFTER the cast registers, and so does groundbait that has washed away or been
        // replaced. §last-thrown-wins made the old "only ever ratchet up" shortcut a lie: throw a
        // different mix over your own swim and the swim really is that other mix now, sometimes weaker.
        // The zone is the one thing that knows, so the line simply asks it again.
        FeedZoneData.Query feed = FeedZoneData.get(level).query(session.target, now);
        ctx.inFeedZone = feed.inZone();
        ctx.feedFreshness = feed.freshness();
        ctx.feedMix = feed.mix();

        RandomSource random = level.getRandom();
        BiteEngine.Outcome outcome = BiteEngine.evaluate(FishProfileManager.get().all(), ctx, random);
        double sNew = currentBiteSpeed(level, ctx, outcome.totalWeight);
        if (sNew <= 0.0) {
            // The water went dead (night/season gated everything out) — the line just sits; a later
            // re-eval revives it when conditions come back.
            session.biteSpeed = 0.0;
            session.biteAtTick = now + 999_999;
            return;
        }
        if (session.biteSpeed <= 0.0) {
            // Dead water came back to life — restart the clock with a fresh sample at the new rate.
            session.biteAtTick = now + Math.max(100L,
                    (long) (-(BiteEngine.T_MIN_TICKS / sNew) * Math.log(1.0 - random.nextDouble())));
        } else {
            long remaining = Math.max(10L, session.biteAtTick - now);
            session.biteAtTick = now + Math.max(10L, (long) (remaining * session.biteSpeed / sNew));
        }
        session.biteSpeed = sNew;
        // Re-pick the biter from the fresh weights — but a koi decided at cast stays sticky (re-rolling
        // its chance every 15 s would compound a per-cast rarity into a near-guarantee over a long wait).
        if (!session.species.getPath().startsWith("carp_koi")) {
            Identifier was = session.species;
            session.species = outcome.pickSpecies(random);
            // §respec: and if it IS a different fish now, roll the SPECIMEN again against its own profile.
            //
            // Reported as a 242 g ruffe — a fish whose range tops out at 150 g. The weight, the length and
            // the trophy flag were all rolled once, at the cast, for whichever species was coming THEN;
            // this re-pick changed the species every fifteen seconds and left the specimen untouched, so a
            // wait that began on a perch and ended on a ruffe delivered a ruffe carrying the perch's
            // weight. Every out-of-range fish anyone has ever seen came through here.
            if (!session.species.equals(was)) {
                FishProfile fresh = FishProfileManager.get().byId(session.species);
                if (fresh != null) {
                    session.trophy = false;
                    rollFish(random, fresh, session, session.rollLuck, session.rollLivebaitG,
                            BiteEngine.matchScore(fresh, ctx));
                }
            }
        }
    }

    // ---- hook-up: start the fight ----

    /**
     * §consumables: the fish had the bait in its mouth, hook set or not.
     *
     * <p>ONE owner for "was the bait eaten", because it used to be answered in two places that
     * disagreed: {@code hookUp} charged for it, and every way of ending a session BEFORE the hook-set
     * charged for nothing. A bite you struck at and missed left the worm on the hook, so the cheapest
     * way to fish was to keep missing. The rod pod's own comment said "bait gone" about a path that
     * never took any.
     *
     * <p>Lures and the mormyshka are skipped inside {@link RigData#consumeBait} — nothing artificial is
     * ever eaten. The rod is read from the HAND rather than from {@code session.rodStackRef}: that
     * reference is documented as going stale (§session-guard), and a shrink written into a stale stack
     * is silently lost.
     */
    private static void eatBait(ServerPlayer sp, FishingSession session) {
        if (!RiverFishingConfig.consumeBait()) return;
        ItemStack rod = sessionRod(sp, session);
        if (!(rod.getItem() instanceof RodItem)) return;
        // §skills FRUGAL: a frugal angler sometimes re-uses the bait (the fish nibbled without stripping it).
        if (sp.level().getRandom().nextDouble() < AnglerSkills.baitSkipChance(sp)) return;
        ItemStack rig = RodData.get(rod, ComponentSlot.RIG);
        if (!(rig.getItem() instanceof RigItem)) return;
        // §bait-attribution: the bait the FISH prefers is the one eaten — not just the first slot.
        FishProfile p = session.species == null ? null : FishProfileManager.get().byId(session.species);
        if (RigData.consumeBait(rig, p == null ? null : p::baitScore)) {
            RodData.set(rod, ComponentSlot.RIG, rig);
        }
    }

    /**
     * §giant-taper: the mass a fight is actually fought against. Below the knee it IS the mass, so
     * every fish the mod was balanced around keeps the numbers it shipped with. Above it the curve
     * compresses - because the linear law asked 802 kg of line for a 400 kg marlin while the
     * strongest braid in the game carries 108, and a 600 kg beluga asked 1202. Those fish were not
     * hard, they were impossible, and a player who buys the best tackle in the mod deserves to find
     * out that it is the best tackle in the mod. Reported on Discord after 0.8.0: "he bites, but
     * there is no way on earth to land him".
     *
     * <p>Sub-linear is also the honest physics: a fish's pull does not scale with its mass, which is
     * why real crews land a 400 kg marlin on 60 kg line - with drag and time, not dead lift.
     */
    private static final double GIANT_KNEE_KG = 20.0, GIANT_TAPER = 0.55;

    /** §giant-taper: mass as the tackle feels it. Identity below the knee, compressed above it. */
    public static double fightMassKg(double kg) {
        return kg <= GIANT_KNEE_KG ? kg
                : GIANT_KNEE_KG * Math.pow(kg / GIANT_KNEE_KG, GIANT_TAPER);
    }

    private static void hookUp(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        sp.stopUsingItem(); // stop any retrieve animation
        clearFloatTiming(sp); // hide the timing HUD if it was up
        FishProfile profile = FishProfileManager.get().byId(session.species);
        if (profile == null) {
            endSession(sp, session);
            return;
        }
        RandomSource random = level.getRandom();

        // §tackle-break (§10): a flat 0.3% catastrophic failure — the line parts on the take and the whole
        // rig is lost, fish and all. Independent of the weight-vs-strain break in the fight (that's earned);
        // this is the rare gut-punch that keeps every strike a little tense.
        if (random.nextDouble() < TACKLE_BREAK_CHANCE) {
            ItemStack broken = sessionRod(sp, session);
            if (broken.getItem() instanceof RodItem) {
                RodData.set(broken, ComponentSlot.RIG, ItemStack.EMPTY);
            }
            addLineWear(broken, 5);
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.LINE_BREAK.get(),
                    SoundSource.PLAYERS, 0.9f, 1.0f);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.line_break_gone")
                    .withStyle(ChatFormatting.RED));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.BREAK);
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "snapped"); // §joke: the 0.3% gut-punch
            endSession(sp, session);
            return;
        }

        // Every strike stresses the blank (§rod-durability); at zero the rod snaps for good.
        ItemStack rodWear = sessionRod(sp, session);
        if (rodWear.getItem() instanceof RodItem && rodWear.isDamageableItem()) {
            rodWear.hurtAndBreak(1, sp,
                    session.hand == InteractionHand.MAIN_HAND
                            ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                            : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }

        eatBait(sp, session);

        // §7.1: a still-tackle "bite" can be a bottom snag (зацеп — tug free or lose the rig).
        // Foul-hooking (багрение) is NOT rolled here — a fish only gets snagged in the body on a
        // moving lure, so it's a spinning-rod thing only (handled in retrieveTick).
        if (!session.foulHooked && session.rodClass != RodClass.ACTIVE) {
            double sc = RiverFishingConfig.snagChance();
            double sroll = random.nextDouble();
            // §ice-snag: fishing vertically into a clean hole almost never snags — a flat 1% total, and
            // that 1% is only the recoverable "tug free" kind (the mormyshka comes back).
            if (session.iceFishing) {
                if (sroll < 0.01) {
                    handleSnag(sp, level, session, false);
                    return;
                }
            } else {
            if (sroll < SNAG_DEAD_CHANCE * sc) {          // 3% dead (глухой) — lose the rig
                handleSnag(sp, level, session, true);
                return;
            }
            if (sroll < SNAG_TOTAL_CHANCE * sc) {         // 7% recoverable — tug free
                handleSnag(sp, level, session, false);
                return;
            }
            }
        }

        // Bycatch (прилов): sometimes that "bite" was never a fish — an old boot, or a lucky find.
        // It still pulls like dead weight for a second or two (§bycatch-intrigue): fish or boot?
        // Lure fishing is exempt (a moving lure doesn't pick up bottom junk on the strike).
        if (!session.foulHooked && session.rodClass != RodClass.ACTIVE) {
            double roll = random.nextDouble();
            double junk = RiverFishingConfig.bycatchJunkChance();
            if (roll < junk) {
                startBycatchFight(sp, level, session, now, false);
                return;
            }
            if (roll < junk + RiverFishingConfig.bycatchTreasureChance()) {
                startBycatchFight(sp, level, session, now, true);
                return;
            }
        }

        // §livebait-2 (0.4.0): a weighed live baitfish on the rig culls the small takers. Read the rig
        // from the session's own rod stack (pods fish with the rod OFF-hand, so not getItemInHand).
        int livebaitW = 0;
        ItemStack rigSource = sessionRod(sp, session);
        if (rigSource.getItem() instanceof RodItem) {
            ItemStack rigS = RodData.get(rigSource, ComponentSlot.RIG);
            if (rigS.getItem() instanceof RigItem) livebaitW = RigData.livebaitWeightG(rigS);
        }
        // §match-size: how well the whole kit suits the species shapes the specimen it dares to take.
        double match = session.ctx != null ? BiteEngine.matchScore(profile, session.ctx) : 0.85;
        session.rollLuck = AnglerSkills.sizeLuck(sp);
        session.rollLivebaitG = livebaitW;
        rollFish(random, profile, session, session.rollLuck, livebaitW, match);

        ItemStack rod = sessionRod(sp, session);
        // A blunt hook can slip on the strike (§3.8) — empty set, fish gone, hook dulls a touch more.
        // (A foul-hooked fish is snagged by the body, so this doesn't apply.)
        if (!session.foulHooked && random.nextDouble() < WearData.hookEmptySetChance(session.hookWear)) {
            dullSharpestHook(rod, hookWearAmount());
            addLineWear(rod, 1);
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.empty_set").withStyle(ChatFormatting.GRAY));
            return;
        }

        double weightKg = session.weightG / 1000.0;
        double drag = session.dragKg;                                  // 0 for a reel-less float rod
        double requiredKg = Math.max(0.5,
                profile.fightStrength * (1.0 + fightMassKg(weightKg)) * 2.0);
        double effectiveStrain = session.lineStrainKg + 0.5 * drag;    // lineStrain already wear-reduced (§3.8)
        // §tackle-margin (0.7.0): how far the tackle OUT-GUNS this fish, uncapped. Reported as a bug and
        // it was one: baseTolerance below is clamped at 1, so every line from "just enough" upward gave
        // the identical tolerance — 108 kg of braid behaved exactly like 22 kg on a 10 kg catfish, and the
        // ten mono diameters and seven braids above the minimum bought the player nothing at all.
        session.tackleMargin = effectiveStrain / Math.max(0.5, requiredKg);
        double baseTolerance = Mth.clamp(session.tackleMargin, 0.2, 1.0);
        // Tolerance shrinks with break-sensitivity (§14) and rod overload (#5): thin/worn line + heavy
        // fish + small reel + overloaded blank => snaps with the slightest over-pull.
        // §skills STRONG_LINE: steadier hands let the line hold a little more tension before it snaps.
        session.breakTension = Mth.clamp(
                baseTolerance / RiverFishingConfig.breakSensitivity() * session.overloadPenalty
                        * AnglerSkills.lineToleranceMult(sp), 0.1, 1.0);
        session.requiredKg = requiredKg; // §tackle-stress: for the break-load message
        // §rod-load: how hard THIS fish loads THIS blank. Tension above is the line's break-risk, and
        // §tackle-margin deliberately starves it on over-gunned gear — which left a trolling blank
        // arrow-straight over a 2 kg bass. The rod must read the fight even with the line nowhere
        // near breaking, so the bend gets its own gauge: pull vs the blank's power class.
        if (rod.getItem() instanceof RodItem loadedRod) {
            session.rodPull01 = requiredKg / loadedRod.rodType().fightPowerKg();
        }

        // A leaderless line is bitten through; a fluorocarbon leader only partly protects (#4).
        if (profile.requiresLeader
                && random.nextDouble() < RiverFishingConfig.leaderBiteoffChance() * (1.0 - session.leaderProtection)) {
            breakLine(sp, level, session, true);
            return;
        }

        // Reel feel (#2): small reel = sensitive/twitchy (big tension spikes, little give); big reel =
        // coarse but absorbs (drag); a reel-less float is a direct hand-line (twitchy, slow to give line).
        double dragRelief = Mth.clamp(drag / 10.0, 0.0, 0.5);
        double sens = session.reelSize == 0
                ? 1.3
                : Mth.clamp(1.0 + (4000 - session.reelSize) / 4000.0 * 0.5, 0.6, 1.5);
        double weightStress = Mth.clamp(weightKg / 5.0, 0.2, 2.0);     // heavier fish pull harder, land slower

        // §tackle-margin: the LOAD is what strong tackle is supposed to change. Tension stays normalised
        // 0..1 (the bar, the bar colour and the rod bend all read tension/breakTension), so heavy gear
        // cannot raise the ceiling — it lowers how fast everything fills it. Exactly-adequate tackle
        // (margin 1) is unchanged, so the tuning that was right stays right; only the over-gunned case,
        // which was the broken one, moves.
        double loadFactor = Mth.clamp(Math.pow(Math.max(0.05, session.tackleMargin), -0.6), 0.25, 2.0);
        session.runTensionPulse = 0.18 * sens * (0.7 + 0.6 * weightStress) * loadFactor;
        session.calmTensionPulse = 0.07 * sens * loadFactor;
        // §small-fry (0.5.1): the weightStress floor (0.2) let a 50 g perch load the rod like a
        // kilo fish — sub-kilo fish now damp their pulses toward "barely felt" without touching
        // the balance above ~1.2 kg.
        double smallDamp = Math.min(1.0, 0.25 + weightKg / 1.5);
        session.runTensionPulse *= smallDamp;
        session.calmTensionPulse *= smallDamp;
        // §fish-fatigue (0.5.1): full burn-out after ~(4 + 2.5·kg) seconds of RUNNING — a perch gases
        // out in seconds, a carp holds for half a minute, big game outlasts the drag instead.
        // §fight-course: divided through by the run-length change (2.2x) and the new course bonus, or a
        // perfectly-fought fish hit fatigue 1.0 inside its FIRST run and everything after it went limp.
        // §fish-stamina (0.7.0): and by the SPECIES' own staying power, which until now was a number
        // every profile carried and nothing ever read — so a 2 kg pike and a 2 kg carp gassed out
        // identically, which is the one difference the field exists to describe. Measured against the
        // table's median (0.70) so wiring it up leaves the fish that were tuned right exactly as they
        // were; the clamp keeps a bleak from being untirable-fast and a tuna from being unkillable.
        double staminaFactor = Mth.clamp(profile.fightStamina / 0.70, 0.5, 1.6);
        session.landPulse = 0.05 / (0.7 + 0.6 * weightStress) * (0.9 + session.reelSize / 14000.0);
        session.relaxTick = 0.010 + dragRelief * 0.02;                 // big reel gives line faster
        session.fightPattern = profile.fightPattern;
        session.fightAggression = profile.fightAggression;
        session.fightTimeout = (long) Mth.clamp(
                700 + weightKg * 80
                        + ("burst".equals(profile.fightPattern) ? 300
                        : "relentless".equals(profile.fightPattern) ? 500
                        : "sounding".equals(profile.fightPattern) ? 700      // §big-game: dives eat time
                        : "greyhounding".equals(profile.fightPattern) ? 400 : 0), 900, 3400);

        // §tire-within-the-fight: the clock above grows with mass forever while fightTimeout is CLAMPED
        // at 3400 ticks, so past a certain size a fish could not reach fatigue inside its own fight at
        // all — a 90 kg beluga ended a full 170-second fight at 0.11 spent, a 600 kg one at 0.03. That
        // is not a hard fish, it is a fish with no second act: fatigue shortens runs, thins them out and
        // lifts the angler's gain, and none of it ever arrived. Reported twice after 0.8.1 as a beluga
        // that simply runs out the clock.
        //
        // So the absolute clock still decides it wherever it fits — every fish under ~3 kg keeps its
        // number to the tick — and where it does not fit, the fish is spent after this share of its own
        // fight instead. A cap, not a replacement: written as a share outright it would have made a
        // half-gram sunbleak twice as durable, which is the opposite of the point.
        session.fatigueRunTick = 1.0 / Math.min(
                20.0 * (10.4 + 6.5 * weightKg) * staminaFactor,
                session.fightTimeout * FATIGUE_FIGHT_SHARE * staminaFactor);

        session.fighting = true;
        session.tension = 0.0;
        session.overStress = 0.0;               // §tackle-stress: fresh stress budget per fight
        session.overStressTicks = 0;
        session.overstressWarned = false;
        // §retrieve-visual: a spinning fish that grabbed the lure MID-RETRIEVE is already partway in —
        // start the fight from where the lure was, so the line doesn't snap back out to the full cast.
        // A fish hooked near the bank is landed sooner (realistic); one that hit far out fights fully.
        session.landProgress = (session.rodClass == RodClass.ACTIVE && session.retrieveMax > 0)
                ? Mth.clamp((double) session.retrieveTicks / session.retrieveMax, 0.0, 0.85)
                : 0.0;
        session.runsLeft = fightRunCount(profile, weightKg);
        session.anglerStamina = 1.0;
        session.course = FightCourse.NONE;
        session.runIndex = 0;
        session.pullDir = 0;
        session.runTicksLeft = 0;
        session.runTicksTotal = 0;
        session.fightStartTick = now;
        session.nextRunAt = now + 30 + random.nextInt(40);

        // §predator-fight (2.1): a lure-caught fish (spinning/ultralight) or any toothy predator fights
        // fast and mean — harder head-shaking pulls, a tighter margin before the snap, and it comes in
        // slower so you have to work it. Everything scales with WEIGHT: an ultralight tiddler stays fair,
        // a big pike/zander/asp is a real handful with several extra runs and frequent head-shakes.
        session.predator = session.rodClass == RodClass.ACTIVE || profile.requiresLeader;
        if (session.predator) {
            double wAmp = Mth.clamp(weightKg / 4.0, 0.0, 1.5);        // ~0 (tiny) .. 1.5 (6 kg+)
            // §spin-harder (1, eased): angrier fish — sharper pulls, tighter margin, slower to land, more
            // runs and head-shakes. Dialled back from the "impossible" pass: the margin isn't so thin that
            // it snaps instantly; the difficulty is patience (ease off during runs), not a coin-flip.
            session.runTensionPulse *= 1.35 + 0.25 * wAmp;
            session.breakTension = Mth.clamp(session.breakTension * 0.92, 0.1, 1.0);
            session.landPulse *= 0.85;                               // reels in slower — real work
            session.calmTensionPulse *= 1.1;
            session.relaxTick *= 0.92;                              // tension eases off a little slower
            session.runsLeft += 1 + (int) Math.round(wAmp);
            session.headShakeChance = 0.008 + 0.011 * wAmp;
            session.fightTimeout += 300;
            // §spin-harder (4, eased): ULTRALIGHT stays the harder fight than spinning — fragile finesse
            // tackle — but no longer unwinnable. Slower landing + more thrashing rather than a hair trigger.
            if (rod.getItem() instanceof RodItem ri && ri.rodType() == RodType.ULTRALIGHT) {
                session.runTensionPulse *= 1.15;
                session.breakTension = Mth.clamp(session.breakTension * 0.92, 0.1, 1.0);
                session.landPulse *= 0.88;
                session.headShakeChance += 0.006;
            }
        }

        // A foul-hooked fish fights sideways — harder and longer, and won't count (§7.1).
        if (session.foulHooked) {
            session.runsLeft += 2;
            session.runTensionPulse *= 1.3;
            actionbar(sp, Component.translatable("message.riverfishing.foul_hooked").withStyle(ChatFormatting.RED));
        }

        // Reel-less pole (§pull-qte): after the strike comes THE one and only timing — the pull-out.
        // The heavier the hooked fish, the faster the sweep and the narrower the zone; the ROD TIER
        // softens the curve: a stick can never realistically land a trophy, a true pole can.
        if (session.rodClass == RodClass.FLOAT && session.reelSize == 0) {
            double wKg = session.weightG / 1000.0;
            if (wKg * 1.4 > Math.max(0.4, session.lineStrainKg)) {
                breakLine(sp, level, session, false);
                return;
            }
            // Tier curves (per user's table): stick 30/28/24/12/10, bamboo 30/28/26/16/12, pole 30/28/26/18/14.
            String rodKey = rod.getItem() instanceof RodItem ri ? ri.rodType().jsonKey() : "pole";
            double speedK;
            int speedFloor;
            double zoneK;
            double zoneMin;
            switch (rodKey) {
                case "stick" -> { speedK = 6.0; speedFloor = 10; zoneK = 0.045; zoneMin = 0.035; }
                case "bamboo" -> { speedK = 4.5; speedFloor = 12; zoneK = 0.038; zoneMin = 0.050; }
                default -> { speedK = 4.0; speedFloor = 14; zoneK = 0.033; zoneMin = 0.060; }
            }
            session.fighting = true;
            session.pullMode = true;
            session.floatPeriod = (int) Mth.clamp(30 - wKg * speedK, speedFloor, 30);
            session.floatZoneHalf = (float) Mth.clamp(0.20 - wKg * zoneK, zoneMin, 0.20);
            // §7.1: a foul-hooked fish thrashes — on a pole that means a tighter, faster pull-out
            // window (so багрение actually bites on float gear, not just on reels).
            if (session.foulHooked) {
                session.floatPeriod = Math.max(speedFloor - 2, session.floatPeriod - 4);
                session.floatZoneHalf = (float) Math.max(zoneMin * 0.6, session.floatZoneHalf - 0.04);
            }
            session.pullWindowEnd = now + session.floatPeriod * 2L + 10;
            beginTiming(sp, session, now, (int) (session.pullWindowEnd - now));
            actionbar(sp, Component.translatable("message.riverfishing.qte_start").withStyle(ChatFormatting.AQUA));
            return;
        }

        // §fight-mystery: NO species name during the fight — you learn what it was when you land it.
        // §26.x: ServerBossEvent needs an explicit id; §bossbar-2 keeps the "whose fight it is" title.
        session.bossBar = new ServerBossEvent(java.util.UUID.randomUUID(),
                Component.translatable("message.riverfishing.bar_fight", sp.getDisplayName()),
                BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        session.bossBar.setProgress(0.0f);
        session.bossBar.addPlayer(sp);

        // Hooking a fish wears the line a little and dulls the hook (§3.8).
        addLineWear(rod, (int) Math.round(2 * lineWearScaled()));
        dullSharpestHook(rod, hookWearAmount());

        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 0.8f);
        actionbar(sp, Component.translatable("message.riverfishing.hooked").withStyle(ChatFormatting.AQUA));
    }

    /**
     * §bycatch-intrigue: the boot/treasure doesn't surface instantly — it hangs on the line as a
     * short HEAVY pull (~1–2 s of reeling, one dead-weight tug at the start), indistinguishable from
     * a big lazy fish until it breaks the surface. The line can't snap on it.
     */
    private static void startBycatchFight(ServerPlayer sp, ServerLevel level, FishingSession session, long now,
                                          boolean treasure) {
        session.bycatch = treasure ? 2 : 1;
        session.rodPull01 = 0.25;       // §rod-load: a dragged boot still bows any blank a little

        // §bycatch-intrigue on a pole: a reel-less float rod has no tension fight — it uses the
        // float pull-out timing, exactly like a hooked fish, so junk feels the same until it surfaces.
        if (session.rodClass == RodClass.FLOAT && session.reelSize == 0) {
            session.fighting = true;
            session.pullMode = true;
            session.floatPeriod = 24;            // dead weight, easy-ish sweep
            session.floatZoneHalf = 0.18f;
            session.pullWindowEnd = now + session.floatPeriod * 2L + 20;
            beginTiming(sp, session, now, (int) (session.pullWindowEnd - now));
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.8f, 0.7f);
            actionbar(sp, Component.translatable("message.riverfishing.hooked").withStyle(ChatFormatting.AQUA));
            return;
        }

        session.fighting = true;
        session.pullMode = false;
        session.tension = 0.0;
        session.landProgress = 0.0;
        session.breakTension = 999.0;   // dead weight never snaps the line — it's just heavy
        session.runTensionPulse = 0.12;
        session.calmTensionPulse = 0.06;
        session.landPulse = 0.09;       // ~10 pulls ≈ 1.5–2 s of dragging
        session.relaxTick = 0.02;
        session.runsLeft = 0;
        session.anglerStamina = 1.0;
        session.course = FightCourse.NONE;
        session.runIndex = 0;
        session.pullDir = 0;
        session.runTicksLeft = 22;      // the first second FEELS alive — fish or boot?
        session.runTicksTotal = 22;     // §dive-cost: even this opener is a span, not a leftover
        session.nextRunAt = now + 100000;
        session.fightStartTick = now;
        session.fightTimeout = 600;
        session.fightPattern = "steady";
        // §26.x: ServerBossEvent needs an explicit id; §bossbar-2 keeps the "whose fight it is" title.
        session.bossBar = new ServerBossEvent(java.util.UUID.randomUUID(),
                Component.translatable("message.riverfishing.bar_fight", sp.getDisplayName()),
                BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        session.bossBar.setProgress(0.0f);
        session.bossBar.addPlayer(sp);
        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.8f, 0.7f);
        actionbar(sp, Component.translatable("message.riverfishing.hooked").withStyle(ChatFormatting.AQUA));
    }

    /** Bycatch (прилов): junk drags the mood down, treasure makes the day. Ends the session either way. */
    /**
     * §challenges: the code-driven advancements that depend on HOW the fish was caught (rod class, bait,
     * through the ice) — impossible to express with vanilla item triggers.
     */
    private static void checkCatchAdvancements(ServerPlayer sp, ServerLevel level, FishingSession session) {
        String sp2 = session.species.getPath();
        ItemStack rod = sessionRod(sp, session);
        RodType rodType = rod.getItem() instanceof RodItem ri ? ri.rodType() : null;
        boolean wooden = rodType == RodType.STICK || rodType == RodType.BAMBOO;
        java.util.List<String> baits = java.util.List.of();
        if (rod.getItem() instanceof RodItem) {
            ItemStack rig = RodData.get(rod, ComponentSlot.RIG);
            if (rig.getItem() instanceof RigItem) baits = RigData.baitIds(rig);
        }
        // Hard: a big pike on live bait, on a humble wooden rod.
        if (sp2.equals("pike") && session.weightG >= 4000 && wooden
                && (baits.contains("livebait") || baits.contains("gudgeon") || baits.contains("bleak"))) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "pike_on_wood");
        }
        // Thematic: a burbot pulled through the ice.
        if (sp2.equals("burbot") && session.iceFishing) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "ice_burbot");
        }
        // Funny/hard: a trophy landed on a reel-less POLE rod (no reel at all — just nerve). Gate on the
        // rod TYPE, not session.reelSize (bottom rods can read 0 mid-flow → the old false positive).
        if (session.trophy && (rodType == RodType.POLE || rodType == RodType.BAMBOO || rodType == RodType.STICK)) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "trophy_on_pole");
        }
        // §26.x: "Trophy" used to be a datapack predicate on the item's NBT. 26.x dropped the tag/nbt
        // fields from ItemPredicate and the codec IGNORES what it does not know, so the predicate
        // became empty — and an empty predicate matches everything, handing the goal out for the first
        // dirt block a player picked up. The condition is a fact the server already knows at exactly
        // this point, so it is asserted here instead of described in JSON.
        if (session.trophy) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "trophy");
        }
        // Land a fish mid-frenzy.
        if (isFrenzy(level)) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "frenzy_feast");
        }
    }

    private static void landBycatch(ServerPlayer sp, ServerLevel level, FishingSession session, boolean treasure) {
        RandomSource random = level.getRandom();
        ItemStack loot;
        if (treasure) {
            loot = switch (random.nextInt(6)) {
                case 0 -> new ItemStack(Items.NAME_TAG);
                case 1 -> new ItemStack(Items.SADDLE);
                case 2 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + random.nextInt(3));
                case 3 -> new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(3));
                default -> new ItemStack(Items.EMERALD, 2 + random.nextInt(3));
            };
        } else {
            loot = switch (random.nextInt(5)) {
                case 0 -> new ItemStack(Items.LEATHER_BOOTS);
                case 1 -> new ItemStack(Items.BONE);
                case 2 -> new ItemStack(Items.KELP, 1 + random.nextInt(2));
                case 3 -> new ItemStack(Items.INK_SAC);
                default -> new ItemStack(Items.STICK, 1 + random.nextInt(3));
            };
        }
        // §challenges: the classic — you fished up an old boot. Asked BEFORE the pickup, because
        // Inventory.add EMPTIES the stack it accepts and an empty stack reports Items.AIR. Asked after,
        // this fired only when the add FAILED — i.e. the advancement was awarded for a boot you could
        // not carry and withheld for every boot you could. The line below already caches the name for
        // exactly this reason; the item test was simply left on the wrong side of the mutation.
        if (!treasure && loot.is(Items.LEATHER_BOOTS)) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "old_boot");
        }
        Component lootName = loot.getHoverName();
        if (!sp.getInventory().add(loot)) {
            sp.drop(loot, false);
        }
        level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                session.target.getZ() + 0.5, 10, 0.25, 0.1, 0.25, 0.2);
        if (treasure) {
            JournalData.addXp(sp, 15);
            level.playSound(null, session.target, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.treasure_catch", lootName)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6f, 0.8f);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.junk_catch", lootName)
                    .withStyle(ChatFormatting.GRAY));
        }
        endSession(sp, session);
    }

    // ---- fight ----

    private static void reelPulse(ServerPlayer sp, ServerLevel level, FishingSession session) {
        // §drag (0.5.1): an OPEN drag free-spools — cranking gains NOTHING and adds no tension; the
        // handle just spins against the slipping spool. This is what makes the drag honest: crouched
        // you cannot snap, but you cannot gain either (closes the crouch+spam-click guaranteed-fish
        // exploit). Stand up to wind — and take the tension that comes with it.
        if (sp.isCrouching()) {
            level.playSound(null, sp.blockPosition(), SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                    SoundSource.PLAYERS, 0.3f, 0.9f);
            return;
        }
        boolean inRun = session.runTicksLeft > 0;
        // Reeling in a run spikes tension and barely gains line — you should ease off during runs.
        // §fish-fatigue: a tired fish pulls softer and comes in faster.
        double tired = 1.0 - 0.55 * session.fatigue;
        // §fight-course: winding against the run's own direction is the expensive mistake — up to three
        // times the tension of a crank made with the rod across it. §angler-stamina: and a spent angler
        // cannot wind hard, so the answer to being tired is to stop, not to click faster.
        // Only a DIRECTED run is scored. Outside one — a calm crank, a head-shake, the final surge —
        // the multiplier is a flat 1.0, or the re-fitted curve would have quietly halved the tension of
        // every ordinary crank in the mod.
        boolean directed = inRun && session.course.isRun();
        float align = directed ? session.course.alignment(session.pullDir) : 1f;
        double wrongWay = directed ? 2.2 - 1.7 * align : 1.0;
        double armStrength = 0.35 + 0.65 * session.anglerStamina;
        session.tension += (inRun ? session.runTensionPulse : session.calmTensionPulse) * tired * wrongWay
                * (1.0 + 0.5 * (1.0 - session.anglerStamina));
        // …and the payoff. Winding INTO a run has always been near-useless (0.2x); winding while leaning on
        // the fish from the right side gains most of a normal crank. That is the whole mechanic in one
        // number — the direction is not a tax to avoid, it is the thing that lets you work.
        session.landProgress = Mth.clamp(
                session.landProgress + session.landPulse
                        * (!inRun ? 1.0 : directed ? 0.2 + 0.5 * align : 0.2)
                        * (1.0 + 0.6 * session.fatigue) * armStrength, 0.0, 1.0);
        // A crank is work whether it gains anything or not.
        session.anglerStamina = Math.max(0.0, session.anglerStamina - (inRun ? 0.030 * wrongWay : 0.014));
        session.tension = Math.max(0.0, session.tension);

        level.playSound(null, sp.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.25f, 1.6f);

        // §big-game greyhounding (0.5.0): cranking against a jumping fish rips the hook straight out —
        // the answer to the breach is SLACK, not the reel.
        // §jump-pace: the first 4 ticks are GRACE. The window punishes cranking through a breach,
        // and a crank already on its way when the fish leaves the water is not a mistake - it is human
        // reaction time, which no player can beat and every player was being charged for.
        if (level.getGameTime() < session.jumpWindowEnd
                && level.getGameTime() >= session.jumpWindowEnd - 11
                && level.getRandom().nextDouble() < 0.35) {
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.7f, 0.5f);
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.jump_thrown").withStyle(ChatFormatting.YELLOW));
            return;
        }

        // §tackle-stress (0.4.0): crossing the limit no longer snaps instantly — the per-tick roll in
        // tickFight decides whether the line survives the overstress. Keep cranking and it won't.
        if (session.landProgress >= 1.0) {
            landFish(sp, level, session);
        }
    }

    /** Pull-out click (§pull-qte): in the zone the fish comes flying out; outside it throws the hook. */
    private static void pullStrike(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        float m = marker(now - session.floatStart, session.floatPeriod);
        clearFloatTiming(sp);
        if (inZone(session, m, level.getRandom())) {
            landFish(sp, level, session);
        } else {
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.shake_off",
                    FishItem.approxWeightText(session.weightG)).withStyle(ChatFormatting.YELLOW));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.SHAKE_OFF);
        }
    }

    private static void tickFight(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        RandomSource random = level.getRandom();

        // Pull-out mode (§pull-qte) has no tension model — only the timing window matters.
        if (session.pullMode) {
            if (now > session.pullWindowEnd) {
                clearFloatTiming(sp);
                endSession(sp, session);
                actionbar(sp, Component.translatable("message.riverfishing.missed").withStyle(ChatFormatting.GRAY));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
            }
            return;
        }
        // §fight-course: read the pull FIRST. The old order let the run load below use LAST tick's
        // alignment, so letting go of the key was felt one tick late and pressing it one tick early.
        session.courseAlign = session.course.isRun() ? session.course.alignment(session.pullDir) : 1f;
        brace(sp, true);   // §fight-brace: you are anchored to the rod for as long as it is bent

        session.landProgress = Math.max(0.0, session.landProgress - 0.0008);

        // §fight-footwork: where the angler's feet went since last tick, before anything reads the tension.
        if (footwork(sp, level, session)) return;
        // A line you are actively pulling on does not go slack. Measured: without this the passive give
        // out-bled the walk on EVERY tackle combination in the mod (0.0036/t of load against 0.014-0.020/t
        // of relax), so backing away won line at zero cost and 20-40 s of walking landed anything with no
        // cranking at all. Now the give is suspended for exactly as long as the legs are loading the rod,
        // which is what makes walking a fish in a burst move rather than the whole fight.
        if (!session.legPull) {
            session.tension = Math.max(0.0, session.tension - session.relaxTick);
        }

        // §run-load (0.5.1): a RUNNING fish loads the tackle BY ITSELF — before this, tension only rose
        // on cranks, so riding out a run with a closed drag was free (and the rod never bent). Now a hot
        // run against a standing drag climbs toward the break point on its own; crouch (open drag) and
        // the fish takes line instead of loading the rod. This is what makes the drag a real decision.
        if (session.runTicksLeft > 0 && !sp.isCrouching()) {
            // §fight-course: a rod held across the run bleads the fish and loads less; one pointed the
            // wrong way takes the full weight of it. A course-less "run" (a head-shake, the final surge)
            // is nobody's fault and nobody's credit — it loads exactly as it did before any of this.
            double wrongWay = session.course.isRun() ? 1.9 - 1.35 * session.courseAlign : 1.0;
            session.tension += session.runTensionPulse * 0.12 * (1.0 - 0.55 * session.fatigue) * wrongWay;
        }

        // §fish-fatigue: the fight itself wears the fish down — fast while it RUNS, slowly between.
        // §fight-course: and a fish held ACROSS its own run tires almost twice as fast. This is the
        // reward for reading the direction, and it is why a fight is now shorter when fought well
        // rather than merely safer.
        double courseGain = session.runTicksLeft > 0 && session.course.isRun()
                ? 1.0 + 1.8 * session.courseAlign * session.courseAlign : 1.0;
        session.fatigue = Math.min(1.0,
                session.fatigue + (session.runTicksLeft > 0 ? session.fatigueRunTick * courseGain
                                                            : session.fatigueRunTick * 0.2));

        // §angler-stamina: holding a rod against a running fish costs the ANGLER, and standing there
        // pointing it the wrong way costs more, because you are fighting the rod as well as the fish.
        if (session.runTicksLeft > 0 && !sp.isCrouching()) {
            session.anglerStamina -= 0.0030 + 0.0035 * (1.0 - session.courseAlign);
        }
        // It comes back only when you stop pulling. An open drag is rest; a standing drag between runs
        // is half of one; winding is not rest at all (see reelPulse).
        if (now - session.lastClickTick > 20) {
            session.anglerStamina += sp.isCrouching() ? 0.0110 : 0.0055;
        }
        session.anglerStamina = Mth.clamp(session.anglerStamina, 0.0, 1.0);
        if (session.anglerStamina < 0.22 && !session.staminaWarned) {
            session.staminaWarned = true;
            actionbar(sp, Component.translatable("message.riverfishing.spent").withStyle(ChatFormatting.RED));
        } else if (session.anglerStamina > 0.5) {
            session.staminaWarned = false;
        }

        // §drag (0.5.0): crouching OPENS the drag — the reel free-spools. Tension bleeds off fast and a
        // running fish TAKES line, but it cannot snap you: the answer to a jump or a dive you can't hold.
        // Stand up = working drag; holding the reel = winching. Three drag positions, zero new inputs.
        if (sp.isCrouching()) {
            session.tension = Math.max(0.0, session.tension - session.relaxTick * 3.0);
            // §drag-cost (0.5.1): an open drag ALWAYS pays out line — even a resting fish swims off with
            // it. Camping shift between runs is no longer free tension immunity; runs drain extra.
            session.landProgress = Math.max(0.0, session.landProgress
                    - (session.runTicksLeft > 0 ? 0.004 : 0.0025));
        }

        double progress = session.landProgress;
        if (session.runTicksLeft > 0) {
            session.runTicksLeft--;
            if (session.runTicksLeft == 0) {
                session.nextRunAt = now + runInterval(session, progress, random);
                session.course = FightCourse.NONE;
                session.barState = -1;
            }
        } else if (now >= session.nextRunAt) {
            if (session.runsLeft > 0 && random.nextDouble() < runChance(session, progress)) {
                session.runTicksLeft = runDuration(session, progress, random);
                session.runTicksTotal = session.runTicksLeft;   // §dive-cost
                session.runsLeft--;
                // §fight-course: the run gets a direction, scripted by the species' own fight pattern.
                session.course = FightCourse.forPattern(session.fightPattern, session.runIndex++, random);
                session.barState = -1;   // force the bar to re-title with the new course
                level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.7f, 1.2f);
                level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                        session.target.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.2);
                if ("relentless".equals(session.fightPattern)) {
                    // §grass-carp: the amur breaks the surface and goes like a torpedo — a big boil + leap.
                    level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.05,
                            session.target.getZ() + 0.5, 28, 0.4, 0.18, 0.4, 0.4);
                    level.playSound(null, session.target, SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 0.5f, 1.4f);
                }
            } else {
                session.nextRunAt = now + 50;
            }
        }

        // §predator-fight (2.1): a sudden head-shake — a brief violent thrash between runs that spikes
        // tension and rips a little line back. This is what gives the spinning fight its sharp, jerky,
        // unpredictable rhythm. If you keep cranking through it (reelPulse) the tension snaps you off;
        // the answer is to ease off for a moment and let it tire.
        if (session.predator && session.runTicksLeft == 0 && session.landProgress > 0.05
                && random.nextDouble() < session.headShakeChance) {
            session.runTicksLeft = 6 + random.nextInt(6);
            session.runTicksTotal = session.runTicksLeft;   // §dive-cost: a shake is its own span
            session.tension += session.runTensionPulse * 1.25;
            session.landProgress = Math.max(0.0, session.landProgress - 0.03);
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.7f, 1.5f);
            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                    session.target.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.25);
        }

        // §big-game (0.5.0): the two ocean patterns get their signature events.
        if ("sounding".equals(session.fightPattern) && session.runTicksLeft > 0) {
            // The dive TAKES LINE — progress drains while it sounds; pump it back between dives.
            //
            // §dive-cost: the drain is a SHARE OF THE BAR spread over the dive, not a rate per tick.
            // It shipped in 0.5.0 as a flat 0.0035 a tick, when a dive was 60-109 ticks and therefore
            // cost about a third of the bar. §fight-course then lengthened every run ~2.2x without
            // touching this line, so a dive quietly went to two thirds of the bar and a ten-dive
            // beluga asked the angler to pump back 6.6 full bars inside one fight. Reported twice
            // after 0.8.1, in both cases as the fight simply timing out. Written this way the cost
            // stays put the next time a run length moves.
            int span = Math.max(1, session.runTicksTotal > 0 ? session.runTicksTotal : 85);
            session.landProgress = Math.max(0.0, session.landProgress - DIVE_COST / span);
            if (session.runTicksLeft % 25 == 0) {
                level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.DRAG_LONG.get(),
                        SoundSource.PLAYERS, 0.7f, 0.8f);
                actionbar(sp, Component.translatable("message.riverfishing.sounding").withStyle(ChatFormatting.AQUA));
            }
        }
        if ("greyhounding".equals(session.fightPattern) && session.runTicksLeft == 0
                && now >= session.jumpWindowEnd && session.landProgress > 0.05
                // §jump-pace: a breach every ~4 s of a long fight was not drama, it was a
                // metronome the player could only lose to. Rarer, and rarer still as the
                // fish tires - so a fight that is being won visibly calms down.
                && random.nextDouble() < 0.008 * (1.0 - 0.75 * session.fatigue)) {
            // The jump: a full-body breach — SLACK OFF for the window or the hook rips out (reelPulse).
            session.jumpWindowEnd = now + 15;
            level.playSound(null, session.target, SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.2,
                    session.target.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.4);
            actionbar(sp, Component.translatable("message.riverfishing.fish_jumps").withStyle(ChatFormatting.RED));
        }

        // The classic last dash at the bank — ROLLED, not scripted (§final-surge-roll): a guaranteed
        // surge became a ritual the player waited out, and a ritual carries no fear. The odds follow
        // what is actually on the hook: a trophy nearly always makes that dash, a hard pattern often,
        // a modest fish usually comes in quiet — and a boot never fights the net. Rolled exactly once,
        // at the moment the bank is reached; a failed roll is a quiet landing, not a retry.
        if (!session.finalSurgeDone && session.landProgress >= 0.85) {
            session.finalSurgeDone = true;
            double odds = 0.35;
            if (session.trophy) odds += 0.35;
            String fp = session.fightPattern == null ? "" : session.fightPattern;
            if (fp.equals("aggressive") || fp.equals("relentless") || fp.equals("burst")) odds += 0.15;
            if (session.bycatch != 0) odds = 0;
            if (random.nextDouble() >= odds) {
                // it gave up at the net — this time
            } else {
            session.runTicksLeft = Math.max(session.runTicksLeft, (session.trophy ? 38 : 28) + random.nextInt(14));
            session.runTicksTotal = Math.max(session.runTicksTotal, session.runTicksLeft);   // §dive-cost
            // It is the fight's last real run, so it gets a course like every other one — otherwise the
            // dash at the net was the ONLY run in the fight with nothing to answer.
            session.course = FightCourse.forPattern(session.fightPattern, session.runIndex++, random);
            session.barState = -1;
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 0.7f);
            // §sound: the long drag scream tears off for the final dash — at the player (the reel).
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.DRAG_LONG.get(),
                    SoundSource.PLAYERS, 0.9f, 1.0f);
            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                    session.target.getZ() + 0.5, 20, 0.3, 0.15, 0.3, 0.3);
            actionbar(sp, Component.translatable("message.riverfishing.final_surge").withStyle(ChatFormatting.RED));
            }
        }

        // §tackle-stress (0.4.0): the probabilistic break — rolled once per tick, after every tension
        // mutation of this tick (decay, head-shakes, the player's reel pulses in between).
        if (overstressTick(sp, level, session, random)) {
            return;
        }

        if (now - session.fightStartTick > session.fightTimeout) {
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.missed").withStyle(ChatFormatting.GRAY));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
            return;
        }
        if (session.landProgress >= 1.0) {
            landFish(sp, level, session);
            return;
        }

        boolean inRun = session.runTicksLeft > 0;
        // Fight audio language: while the bar is RED the drag screams — a crossbow-ratchet whose pitch
        // rises with tension, so you can HEAR how close the line is to snapping. When calm but the
        // tension is critical, the rod creaks as a warning instead. Played AT THE PLAYER (the reel is
        // in their hands), so long casts never put the sound out of hearing range.
        double stress = Mth.clamp(session.tension / Math.max(0.05, session.breakTension), 0.0, 1.0);
        if (inRun && now % 2 == 0) {
            // §sound: the drag "note" fired every 2 ticks OVERLAPS itself into a continuous ratchet
            // (the note rings ~0.26 s); a higher base pitch makes the clicks come FASTER, and it
            // climbs with tension so you HEAR how close to snapping. Louder than the first pass.
            float pitch = 1.05f + (float) stress * 0.7f + ((now % 4 == 0) ? 0.05f : 0f);
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.DRAG_NOTE.get(),
                    SoundSource.PLAYERS, 0.8f, pitch);
        } else if (!inRun && stress > 0.75 && now % 18 == 0) {
            // Calm but critically loaded: the blank creaks a warning (~0.86 s, so spaced well out).
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.ROD_CREAK.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        session.bossBar.setProgress((float) Mth.clamp(session.landProgress, 0.0, 1.0));
        // §bossbar-2: the bar tells WHOSE fight it is and what the fish is doing — no more guessing
        // between two friends' bars. Name re-sends only when the state flips.
        int barState = session.runTicksLeft > 0 ? 1 : session.fatigue > 0.7 ? 2 : 0;
        if (barState != session.barState) {
            session.barState = barState;
            // §rod-load: the bar no longer SPELLS the course out ("goes LEFT — pull RIGHT") — the rod
            // itself is the instrument now: the blank bends toward the fish (§bend-plane) and loads
            // with the pull, so the text would only repeat what the tackle already shows. (The note
            // that used to live here said to flip this the day the 26.x rod could be read — that day
            // came with the §rod3d-26x chain gaining the bend plane and the springs.)
            session.bossBar.setName(Component.translatable(barState == 2
                    ? "message.riverfishing.bar_tired"
                    : "message.riverfishing.bar_fight", sp.getDisplayName()));
        }
        session.bossBar.setColor(session.tension >= session.breakTension ? BossEvent.BossBarColor.RED
                : inRun ? BossEvent.BossBarColor.RED
                : session.tension > session.breakTension * 0.66 ? BossEvent.BossBarColor.YELLOW
                : BossEvent.BossBarColor.GREEN);

        // §co-op (0.5.0): spectators — anyone within 12 blocks sees the fight on the boss bar too.
        if (now % 20 == 0 && session.bossBar != null) {
            for (ServerPlayer other : level.players()) {
                if (other != sp && other.distanceToSqr(sp) <= 144.0) {
                    session.bossBar.addPlayer(other);
                }
            }
        }
        // §co-op (0.5.0): the landing net — a crouching friend with an EMPTY main hand right beside the
        // angler scoops the tired fish out (fish at 85%+, not during a run). Small XP thank-you.
        if (!inRun && session.landProgress >= 0.85) {
            for (ServerPlayer helper : level.players()) {
                if (helper != sp && helper.isCrouching() && helper.getMainHandItem().isEmpty()
                        && helper.distanceToSqr(sp) <= 12.25) {
                    JournalData.addXp(helper, 5);
                    helper.sendSystemMessage(Component.translatable("message.riverfishing.netted_for",
                            sp.getDisplayName()).withStyle(ChatFormatting.GREEN));
                    sp.sendSystemMessage(Component.translatable("message.riverfishing.netted_by",
                            helper.getDisplayName()).withStyle(ChatFormatting.GREEN));
                    landFish(sp, level, session);
                    return;
                }
            }
        }

        // Keep every client's view of the line in step with the fight so it visibly reels in (§immersion).
        // §rod-bend: this same sync carries the live fight stress — the in-hand bend breathes off it.
        // §jump-cue: every tick while a jump is open, not every fifth. The window is 15 ticks, so a
        // 5-tick cadence loses up to a third of it at each edge — and the packet on the closing tick IS
        // the all-clear. Everywhere else the cadence is fine and the traffic stays where it was.
        if (now % 5 == 0 || now <= session.jumpWindowEnd) {
            ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target,
                    (float) Mth.clamp(session.landProgress, 0.0, 1.0), session.lineColor,
                    session.floatKind, false, fightStress(session), rodLoad(session),
                    // §pump-reel + §jump-cue: "do not reel right now" — a run OR a breach. The HUD cue
                    // used to read the run alone, so during a jump it showed a green "reel" directly
                    // under the red "do not reel", and the mod contradicted itself on one screen.
                    true, session.runTicksLeft > 0 || now < session.jumpWindowEnd,
                    (byte) session.course.ordinal())); // §fight-course: which way the tip gets dragged
            // §rod-bend (26.x): the bucket goes onto the ROD, not just into the packet — the item
            // definition range_dispatches the blank sprite on it, so the load is visible to every
            // player tracking this angler. setBend no-ops unless the bucket actually moved.
            com.riverfishing.item.RodData.setBend(session.rodStackRef,
                    com.riverfishing.item.RodData.bendBucket(fightStress(session)));
        }
    }

    // ---- per-fish fight patterns (#3) ----

    private static int fightRunCount(FishProfile profile, double weightKg) {
        int runs = Math.max(1, profile.fightRuns);
        switch (profile.fightPattern) {
            case "aggressive" -> runs += 2;
            case "relentless" -> runs += 3; // §grass-carp: the amur just keeps charging
            case "burst" -> runs = Math.max(2, runs);
            case "sounding" -> runs += 3;      // §big-game: tuna dives, again and again
            case "greyhounding" -> runs += 2;  // §big-game: billfish jump series
            default -> { /* steady / active_then_passive use the profile value */ }
        }
        if (weightKg > 2.0) runs += 1; // a big specimen has an extra run in it
        return runs;
    }

    /** Probability a run starts when the timer is up, by pattern and how far into the fight we are. */
    private static double runChance(FishingSession s, double progress) {
        // §fish-fatigue: a gassed-out fish stops running — the tell that it's ready for the net.
        return (1.0 - 0.65 * s.fatigue) * rawRunChance(s, progress);
    }

    private static double rawRunChance(FishingSession s, double progress) {
        return switch (s.fightPattern) {
            case "relentless" -> 0.97; // §grass-carp: fights just as hard at the net as at the strike
            case "aggressive" -> 0.95;
            case "burst" -> 0.70;
            case "active_then_passive" -> progress < 0.5 ? 0.90 : 0.25; // bream: fights early, tires late
            case "sounding" -> 0.92;      // §big-game: it WILL dive again
            case "greyhounding" -> 0.85;
            default -> 0.60;
        };
    }

    private static int runDuration(FishingSession s, double progress, RandomSource r) {
        // §fish-fatigue: tired runs are short runs.
        return Math.max(14, (int) (rawRunDuration(s, progress, r) * (1.0 - 0.35 * s.fatigue)));
    }

    /**
     * §fight-course (0.7.0): runs are ~2.2x their old length, in two passes — the first was still short
     * enough that the run was over before a player could read the bar, decide and press. A run is where
     * every fight decision now lives, so it has to last long enough to BE a decision. Fatigue also shortens
     * runs less harshly (0.35 rather than 0.5), or the back half of a long fight went limp.
     *
     * <p>The length is affordable because a correctly-answered run loads the tackle at roughly half rate:
     * held right, a long run is no harder than a short one used to be; held wrong, it is a real problem.
     */
    private static int rawRunDuration(FishingSession s, double progress, RandomSource r) {
        return switch (s.fightPattern) {
            case "relentless" -> 88 + r.nextInt(74); // §grass-carp: long torpedo runs toward open water
            case "aggressive" -> 48 + r.nextInt(40);
            case "burst" -> 108 + r.nextInt(80);
            case "active_then_passive" -> progress < 0.5 ? 68 + r.nextInt(46) : 32 + r.nextInt(22);
            case "sounding" -> 135 + r.nextInt(108);   // §big-game: the long vertical dive
            case "greyhounding" -> 40 + r.nextInt(30); // short bursts between jumps
            default -> 56 + r.nextInt(44);
        };
    }

    private static int runInterval(FishingSession s, double progress, RandomSource r) {
        return switch (s.fightPattern) {
            case "relentless" -> 20 + r.nextInt(25); // §grass-carp: barely a breath between charges
            case "aggressive" -> 25 + r.nextInt(30);
            case "burst" -> 80 + r.nextInt(80);
            case "active_then_passive" -> progress < 0.5 ? 30 + r.nextInt(30) : 90 + r.nextInt(60);
            case "sounding" -> 70 + r.nextInt(60);     // §big-game: the pump-back window between dives
            case "greyhounding" -> 35 + r.nextInt(30);
            default -> 50 + r.nextInt(50);
        };
    }

    private static void landFish(ServerPlayer sp, ServerLevel level, FishingSession session) {
        // The "fish" was a boot or a find all along (§bycatch-intrigue) — reveal it now.
        if (session.bycatch != 0) {
            landBycatch(sp, level, session, session.bycatch == 2);
            return;
        }
        RandomSource random = level.getRandom();
        boolean legal = !session.foulHooked;

        // §legendary (0.5.0): the one-of-a-kind named specimen — ONE per species per SERVER, and the
        // catch is a server event. Rolled at the landing so the whole fight already happened.
        boolean legendary = false;
        FishProfile legProfile = FishProfileManager.get().byId(session.species);
        if (legal && legProfile != null && legProfile.legendaryWeightG > 0
                && !LegendaryData.get(level).isCaught(session.species)
                && random.nextDouble() < legProfile.legendaryChance) {
            legendary = true;
            session.weightG = (int) (legProfile.legendaryWeightG * (0.97 + random.nextDouble() * 0.06));
            session.lengthCm = (int) legProfile.lengthMax;
            session.trophy = true;
            LegendaryData.get(level).markCaught(session.species);
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "legendary_catch");
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.riverfishing.legendary_caught",
                            sp.getDisplayName(),
                            Component.translatable("legendary.riverfishing." + session.species.getPath()),
                            FishItem.weightText(session.weightG))
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            level.playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        giveFish(sp, session.species, session.weightG, session.lengthCm, legal, session.trophy, legendary,
                session.target);
        // §population: a landed fish leaves the water for real — depletion lands on THIS species only.
        FishingPressureData.get(level).addCatch(ChunkPos.pack(session.target),
                session.species.getPath(), level.getGameTime());
        if (legal) {
            boolean newSpecies = JournalData.isNewSpecies(sp, session.species);
            boolean personalBest = JournalData.isPersonalBest(sp, session.species, session.weightG);
            JournalData.record(sp, session.species, session.weightG); // records (§15)
            if (session.trophy) JournalData.addTrophy(sp);
            // §guide-nudge: this rod class works for this player now — nothing about it is on its way.
            GuideNudge.success(sp, session.rodClass);
            if (GuideNudge.consumeHint(sp)) JournalData.markHinted(sp, session.species);
            if (session.iceFishing) JournalData.addIceCatch(sp); // §winter-quests
            // §species-advancements (0.5.0): tiered + "all species" are CODE-counted — the old JSON
            // hand-listed 25 criteria and drifted from the real roster with every content wave.
            if (newSpecies) {
                int n = JournalData.speciesCount(sp);
                if (n >= 10) com.riverfishing.quest.AnglerAdvancements.grant(sp, "species_10");
                if (n >= 25) com.riverfishing.quest.AnglerAdvancements.grant(sp, "species_25");
                if (n >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, "species_50");
                if (n >= JournalData.speciesTotal()) com.riverfishing.quest.AnglerAdvancements.grant(sp, "all_species");
            }
            awardAnglerXp(sp, level, session.weightG, session.lengthCm, newSpecies, personalBest, session.trophy);
            com.riverfishing.quest.Quests.onProgress(sp, level); // angler quests (§quests)
            checkCatchAdvancements(sp, level, session); // §challenges (code-driven)
        }
        if (session.trophy && legal) {
            sp.sendSystemMessage(Component.translatable("message.riverfishing.trophy_catch")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            level.playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9f, 1.2f);
            // A little celebration: sparks over the water and confetti around the angler.
            level.sendParticles(ParticleTypes.END_ROD, session.target.getX() + 0.5, session.target.getY() + 1.2,
                    session.target.getZ() + 0.5, 14, 0.35, 0.4, 0.35, 0.04);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                    16, 0.5, 0.6, 0.5, 0.1);
        }

        // Grusha (3 hooks): tiny chance of two or three near-identical fish at once (Module 4).
        int extras = legal ? grushaExtras(session, random) : 0;
        for (int i = 0; i < extras; i++) {
            int w = (int) Math.round(session.weightG * (0.9 + random.nextDouble() * 0.2));
            int l = (int) Math.round(session.lengthCm * (0.95 + random.nextDouble() * 0.1));
            giveFish(sp, session.species, Math.max(1, w), Math.max(1, l), true, false, false,
                    session.target);
        }

        playLand(level, session.target);
        // The catch lands in the player's hands — celebrate there so it's always audible (§sound-range).
        level.playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        if (extras > 0) {
            sp.sendSystemMessage(Component.translatable("message.riverfishing.caught_multi",
                    fishName(session.species), 1 + extras).withStyle(ChatFormatting.GOLD));
        } else {
            String key = legal ? "message.riverfishing.caught" : "message.riverfishing.caught_foul";
            sp.sendSystemMessage(Component.translatable(key,
                    fishName(session.species),
                    com.riverfishing.item.FishItem.weightText(session.weightG), session.lengthCm)
                    .withStyle(legal ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
        endSession(sp, session);
    }

    /** Angler progression: grant XP for a legal catch, with new-species / personal-best bonuses and level/rank feedback. */
    private static void awardAnglerXp(ServerPlayer sp, ServerLevel level, int weightG, int lengthCm,
                                      boolean newSpecies, boolean personalBest, boolean trophy) {
        // §xp-by-size (§anti-macro): weight-dominated, with only a token flat base — so a swarm of tiny
        // fish is poor XP/hour and targeting bigger fish pays. (bleak ~5, roach ~9, bream ~46, carp ~155,
        // catfish ~307). Was a flat 8 + weight/40, which over-rewarded mass-caught minnows.
        int xp = 2 + weightG / 25 + lengthCm / 4;
        if (newSpecies) xp += 50;
        else if (personalBest) xp += 20;
        if (trophy) xp *= 3; // a trophy specimen is the jackpot

        int before = JournalData.getLevel(sp);
        JournalData.addXp(sp, xp);
        int after = JournalData.getLevel(sp);

        sp.sendOverlayMessage(Component.translatable("message.riverfishing.xp_gained", xp)
                .withStyle(ChatFormatting.AQUA)); // action bar

        if (newSpecies) {
            sp.sendSystemMessage(Component.translatable("message.riverfishing.new_species")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        if (after > before) {
            level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.0f);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.level_up", after)
                    .withStyle(ChatFormatting.GOLD));
            String rankBefore = JournalData.rankKey(before);
            String rankAfter = JournalData.rankKey(after);
            if (!rankBefore.equals(rankAfter)) {
                sp.sendSystemMessage(Component.translatable("message.riverfishing.rank_up",
                                Component.translatable("rank.riverfishing." + rankAfter))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                level.playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
                if ("master".equals(rankAfter)) {
                    var adv = sp.level().getServer().getAdvancements()
                            .get(com.riverfishing.RiverFishing.id("riverfishing/master"));
                    if (adv != null) {
                        sp.getAdvancements().award(adv, "granted");
                    }
                }
            }
        }
    }

    private static int grushaExtras(FishingSession session, RandomSource random) {
        if (session.rigType != RigType.GRUSHA) return 0;
        double r = random.nextDouble();
        if (r < 0.001) return 2; // 0.1% -> three fish total
        if (r < 0.02) return 1;  // 2%   -> two fish total
        return 0;
    }

    private static void giveFish(ServerPlayer sp, Identifier species, int weightG, int lengthCm,
                                 boolean legal, boolean trophy, boolean legendary, BlockPos where) {
        ItemStack fish = FishItem.create(ModItems.fishItem(species), species, weightG, lengthCm, legal, trophy);
        if (legendary) {
            com.riverfishing.item.StackNbt.mutate(fish, t -> t.putBoolean(FishItem.TAG_LEGEND, true));
        }
        rollMorph(sp, fish, species, weightG, where);
        // §prime-fish: a legal top-of-range specimen gets the prime grade — the fisherman buys these.
        FishProfile profile = FishProfileManager.get().byId(species);
        if (legal && profile != null) {
            int threshold = FishItem.primeThresholdG(profile.weightMax);
            if (weightG >= threshold) {
                FishItem.gradePrime(fish, threshold);
                // market (0.5.0): every prime landing saturates that species a little.
                MarketData.get(sp.level()).addSupply(species.getPath());
                // §order-board: and if it IS today's order, that is the order filled.
                OrderBoard.credit(sp, species);
            }
        }
        // §fish-scale: the icon now scales purely from LENGTH (FishItem.getIconScale), no NBT needed.
        if (!sp.getInventory().add(fish)) {
            sp.drop(fish, false);
        }
    }

    /**
     * §morph: does this specimen carry a morph, and which?
     *
     * <p>Every trigger in the table reads state the mod already keeps and has never shown the player.
     * A swim fished down hands out stunted fish; a swim carrying far more of a species than it should
     * hands out the short, deep-bodied ones every carp farmer knows; a species stocked here that has
     * taken hold throws colour morphs; and a fish that is big for its kind carries the marks of having
     * been alive a long time. The pressure and stocking simulations finally have a face.
     *
     * <p>A morph the player has never seen before is worth marking, so the landing sound comes back a
     * fifth higher — DREDGE's trick, and it needs no new sound file.
     */
    private static void rollMorph(ServerPlayer sp, ItemStack fish, Identifier species,
                                  int weightG, BlockPos where) {
        ServerLevel level = sp.level();
        FishProfile p = FishProfileManager.get().byId(species);
        double age = com.riverfishing.fish.FishMorph.ageFraction(p, weightG);
        String path = species.getPath();
        WaterBody body = WaterBodyCache.forLevel(level).get(level, where);
        boolean settled = StockedData.get(level).isStocked(StockedData.region(where), path)
                && !nativeHere(level, where, body, species);
        double surplus = FishingPressureData.get(level).surplusAround(
                where.getX() >> 4, where.getZ() >> 4, path, level.getGameTime());

        var morph = com.riverfishing.fish.FishMorph.roll(path, age, settled, surplus, level.getRandom());
        if (morph == null) return;
        FishItem.setMorph(fish, morph.id());
        if (JournalData.recordMorph(sp, species, morph.id())) {
            level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                    0.5f, 1.5f);
            actionbar(sp, Component.translatable("message.riverfishing.morph_new",
                            Component.translatable("morph.riverfishing." + morph.id()))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            level.playSound(null, sp.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.6f, 1.8f);
        }
    }

    /**
     * Over-tension break (§7, Module 5). Whether the rig is LOST scales with how outgunned the line
     * is (§balance): a strong line vs a light fish nearly always just throws the hook (5%), while a
     * weak line vs a heavy fish loses the rig at the 30% hard cap. Leader bite-offs always lose it.
     */
    /**
     * §tackle-stress (0.4.0): while tension sits over the tackle limit, the line doesn't snap outright —
     * every tick rolls a break chance that grows with the overshoot AND with how long it's been held
     * there ({@code overStress} builds up; easing off lets it recover). Brief spikes are survivable —
     * the "she shouldn't have come out, but she did" stories; cranking through a run is not. Surviving
     * the abuse still frays the line (§3.8). Difficulty presets scale the whole curve (§14).
     */
    private static boolean overstressTick(ServerPlayer sp, ServerLevel level, FishingSession session,
                                          RandomSource random) {
        if (sp.isCrouching()) { // §drag: an OPEN drag cannot snap the line — it pays out instead
            session.overStress = Math.max(0.0, session.overStress - 0.05);
            return false;
        }
        if (session.tension < session.breakTension) {
            session.overStress = Math.max(0.0, session.overStress - 0.02);
            if (session.tension < session.breakTension * 0.9) {
                session.overstressWarned = false; // hysteresis: re-arm the warning for the next episode
            }
            return false;
        }
        double overshoot = (session.tension - session.breakTension) / Math.max(0.05, session.breakTension);
        session.overStress = Math.min(2.0, session.overStress + 0.015 + 0.02 * overshoot);
        session.overStressTicks++;
        if (!session.overstressWarned) {
            session.overstressWarned = true;
            actionbar(sp, Component.translatable("message.riverfishing.tackle_limit").withStyle(ChatFormatting.RED));
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.ROD_CREAK.get(),
                    SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        // Surviving over the limit still costs the line — it frays a wear point every ~15 such ticks.
        if (session.overStressTicks % 15 == 0) {
            addLineWear(sessionRod(sp, session), 1);
        }
        double chance = Math.min(0.5,
                (0.008 + 0.055 * overshoot + 0.028 * session.overStress) * RiverFishingConfig.breakSensitivity());
        if (random.nextDouble() < chance) {
            breakLine(sp, level, session, false);
            return true;
        }
        return false;
    }

    private static void breakLine(ServerPlayer sp, ServerLevel level, FishingSession session, boolean leader) {
        // A break stresses and abrades the line (§3.8).
        addLineWear(sessionRod(sp, session), (int) Math.round(5 * lineWearScaled()));
        double weightKg = session.weightG / 1000.0;
        double strain = Math.max(0.5, session.lineStrainKg);
        // §balance: a strong line vs a light fish nearly always just throws the hook (5% floor), while a
        // weak line vs a heavy fish loses the whole rig at the 30% hard cap. Leader bite-offs always lose.
        double loseChance = Mth.clamp(0.30 * (weightKg * 1.5 / strain), 0.05, 0.30);
        boolean loseRig = leader || level.getRandom().nextDouble() < loseChance;
        if (loseRig) {
            ItemStack rod = sessionRod(sp, session);
            if (rod.getItem() instanceof RodItem) {
                RodData.set(rod, ComponentSlot.RIG, ItemStack.EMPTY);
            }
            // §sound: the rig cracks off — a real snap, at the player and the water for reach.
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.LINE_BREAK.get(),
                    SoundSource.PLAYERS, 0.9f, 1.0f);
            level.playSound(null, session.target, com.riverfishing.registry.ModSounds.LINE_BREAK.get(),
                    SoundSource.PLAYERS, 0.6f, 1.0f);
            if (!leader && session.requiredKg > 0 && session.tension > 0) {
                // §tackle-stress: name the load that killed the line — the post-mortem teaches tackle choice.
                sp.sendSystemMessage(Component.translatable("message.riverfishing.line_break_load",
                        String.format("%.1f", Math.max(0.5, session.tension * session.requiredKg)))
                        .withStyle(ChatFormatting.RED));
            } else {
                sp.sendSystemMessage(Component.translatable(
                        leader ? "message.riverfishing.leader_bite_off" : "message.riverfishing.line_break",
                        FishItem.approxWeightText(session.weightG)).withStyle(ChatFormatting.RED));
                GuideNudge.failure(sp, session.rodClass, GuideNudge.BREAK);
            }
        } else {
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6f, 0.7f);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.shake_off",
                    FishItem.approxWeightText(session.weightG)).withStyle(ChatFormatting.YELLOW));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.SHAKE_OFF);
        }
        endSession(sp, session);
    }

    private static void endSession(ServerPlayer sp, FishingSession session) {
        brace(sp, false);   // §fight-brace: every fight exits through here, so this is the only lift needed
        if (session.bossBar != null) {
            session.bossBar.removeAllPlayers();
            session.bossBar = null;
        }
        if (session.floatPeriod > 0) {
            clearFloatTiming(sp); // hide the strike-timing HUD (float or lure §strike-qte)
        }
        SESSIONS.remove(sp.getUUID());
        // §rod-layers: the line is back in — show the in-hand tackle overlays again.
        if (!session.rodStackRef.isEmpty()) {
            com.riverfishing.item.RodData.setLineOut(session.rodStackRef, false);
        }
        // §rod-bend: the fight is over — unload the blank, or the rod sits bent in the inventory forever.
        com.riverfishing.item.RodData.setBend(session.rodStackRef, 0);
        // Clear the line for everyone who can see this angler (§line-multiplayer).
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), false, null, 0f, 0, (byte) 0));
    }

    // ---- float strike-timing mini-game (#5) ----

    /** Triangle wave 0..1, matching {@link com.riverfishing.client.FloatTimingClient}. */
    private static float marker(long elapsed, int period) {
        if (period <= 0) return 0.5f;
        float phase = (Math.floorMod(elapsed, period)) / (float) period;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    private static void startFloatTiming(ServerPlayer sp, FishingSession session, long now) {
        // The single hook-set QTE (§pole-realism): difficulty comes from the species — aggressive
        // fish dip the float faster (quicker marker), big wary fish give a narrower window.
        FishProfile p = FishProfileManager.get().byId(session.species);
        double aggression = p != null ? p.fightAggression : 0.5;
        double meanKg = p != null ? p.weightMean / 1000.0 : 0.5;
        session.floatPeriod = (int) Mth.clamp(30 - aggression * 12, 16, 30);
        session.floatZoneHalf = (float) Mth.clamp(0.19 - aggression * 0.05 - meanKg * 0.012, 0.07, 0.19);
        beginTiming(sp, session, now, (int) (session.biteWindowEnd - now));
    }

    /**
     * §float-zones: place a RANDOM-position green (100%) target with a flanking orange (25%) band, and
     * send the HUD. Caller sets {@code floatPeriod} and {@code floatZoneHalf} (the green half) first.
     */
    private static void beginTiming(ServerPlayer sp, FishingSession s, long now, int window) {
        // §skills FINESSE: widen the green strike zone (+1%/rank) — a more forgiving подсечка.
        float greenHalf = s.floatZoneHalf * (1f + (float) AnglerSkills.strikeZoneBonus(sp));
        s.floatZoneHalf = greenHalf;
        float orangeHalf = Math.min(0.47f, greenHalf + 0.11f);
        s.floatOrangeHalf = orangeHalf;
        float c = orangeHalf + sp.level().getRandom().nextFloat() * (1f - 2f * orangeHalf);
        s.floatZoneCenter = c;
        s.floatStart = now;
        ModNetwork.toPlayer(sp, new FloatTimingPacket(true, now, window, s.floatPeriod,
                c - greenHalf, c + greenHalf, c - orangeHalf, c + orangeHalf));
    }

    /** §float-zones: green centre = a certain hook; the orange flanks give a 25% chance; outside misses. */
    private static boolean inZone(FishingSession s, float m, RandomSource r) {
        float d = Math.abs(m - s.floatZoneCenter);
        if (d <= s.floatZoneHalf) return true;
        if (d <= s.floatOrangeHalf) return r.nextFloat() < 0.25f;
        return false;
    }

    private static void clearFloatTiming(ServerPlayer sp) {
        ModNetwork.toPlayer(sp, new FloatTimingPacket(false, 0, 0, 0, 0f, 0f, 0f, 0f));
    }

    /**
     * §strike-qte (2.4): the spinning hook-set runner. Kept EASY — the marker sweeps slowly (period 30–40
     * ticks) and the zone is WIDE (half 0.24–0.32, i.e. ~half the bar is green), so it's a relaxed подсечка,
     * not a precision test. Big/aggressive fish tighten it only slightly. Reuses the float-timing HUD/packet.
     */
    private static void startActiveStrikeTiming(ServerPlayer sp, FishingSession session, long now) {
        FishProfile p = FishProfileManager.get().byId(session.species);
        double aggression = p != null ? p.fightAggression : 0.5;
        double meanKg = p != null ? p.weightMean / 1000.0 : 0.5;
        session.floatPeriod = (int) Mth.clamp(40 - aggression * 6, 30, 40);
        session.floatZoneHalf = (float) Mth.clamp(0.32 - meanKg * 0.008, 0.24, 0.32);
        beginTiming(sp, session, now, (int) (session.biteWindowEnd - now));
    }

    /** §strike-qte (2.4): resolve a lure-rod hook-set — green hooks up, orange 25%, outside the fish is gone. */
    private static void activeStrike(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        float m = marker(now - session.floatStart, session.floatPeriod);
        clearFloatTiming(sp);
        if (inZone(session, m, level.getRandom())) {
            hookUp(sp, level, session, now);
        } else {
            eatBait(sp, session);   // §consumables: a mistimed strike still loses the bait
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.mistimed").withStyle(ChatFormatting.GRAY));
            GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
        }
    }

    // ---- fish generation ----

    private static void rollFish(RandomSource random, FishProfile p, FishingSession session, double luck,
                                 int livebaitWeightG, double match) {
        // §weight-curve (0.5.0): the profile's weight_g.mean is the MEDIAN catch — the power curve is
        // solved per species so half the catches land under it (0.5^k = (mean-min)/(max-min)). Profiles
        // without an explicit mean keep the classic big-fish-are-rare 2.4 curve.
        double k = 2.4;
        if (p.weightMeanSet && p.weightMean > p.weightMin && p.weightMean < p.weightMax) {
            double f = (p.weightMean - p.weightMin) / (p.weightMax - p.weightMin);
            k = Mth.clamp(Math.log(f) / Math.log(0.5), 0.5, 8.0); // median(u^k) = 0.5^k = f

        }
        // §match-size: a crude setup catches the smaller end — the big wary specimens ignore it.
        k += Math.max(0.0, 0.85 - match) * 2.0;
        // §skills ANGLERS_LUCK flattens the size curve instead of handing out a flag: a lucky angler
        // meets bigger fish, which is the only thing luck can honestly mean here.
        k /= 1.0 + luck * 2.0;
        // ★ BIG FRACTION CALLS BIG FISH. A bed of whole grain and boilies is a table only a decent fish
        // bothers with; dust is a cloud the small stuff swarms. It flattens the curve rather than setting
        // a floor, so it is a better CHANCE at a good one and never a promise of it — which is exactly
        // what coarse feed does in the real thing. Half the mix or finer changes nothing.
        if (session.ctx != null && session.ctx.feedMix != null && session.ctx.feedFreshness > 0) {
            double coarse = Mth.clamp((session.ctx.feedMix.fraction() - 0.5) * 2.0, 0.0, 1.0);
            k /= 1.0 + 0.55 * coarse * Mth.clamp(session.ctx.feedFreshness, 0.0, 1.0);
        }
        double biased = Math.pow(random.nextDouble(), k);

        // §livebait-2 (0.4.0): a predator that commits to a live baitfish is one that can swallow it —
        // roughly 6× the bait's weight and up. A weighed livebait FLOORS the size roll there (capped at
        // 60% of the species' range so the roll stays a roll). Only for species that actually take
        // livebait; everything else ignores it.
        if (livebaitWeightG > 0 && p.baitScore("livebait") >= 0.5 && p.weightMax > p.weightMin) {
            double minW = Mth.clamp(livebaitWeightG * 6.0, p.weightMin,
                    p.weightMin + (p.weightMax - p.weightMin) * 0.6);
            double floor = (minW - p.weightMin) / (p.weightMax - p.weightMin);
            biased = floor + (1.0 - floor) * biased;
        }

        // §lure-size (round 6): the same physics for ARTIFICIAL lures — a fish that commits to a
        // 200 g jig is one that can swallow it. The individual roll floors at ~8x the lure's mass
        // (capped at 60% of the range so the roll stays a roll). Kills the 709 g zander on a pilker.
        double lureW = session.ctx != null ? session.ctx.lureWeightG : 0;
        if (lureW > 0 && p.weightMax > p.weightMin) {
            double minW = Mth.clamp(lureW * 8.0, p.weightMin,
                    p.weightMin + (p.weightMax - p.weightMin) * 0.6);
            double floor = (minW - p.weightMin) / (p.weightMax - p.weightMin);
            biased = floor + (1.0 - floor) * biased;
        }

        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;
        session.weightG = (int) Math.round(weight);

        // §trophy (0.7.0): a trophy is a PROPERTY OF THE FISH, not a dice roll. It used to be rolled
        // first and the weight forced into the top band afterwards, which meant an ordinary fish could
        // out-weigh a trophy of the same species — a player reported catching a 240 g ruffe that was
        // ordinary and a lighter one that was a trophy, and he was right to call it broken. In a mod that
        // sells itself as a simulator the word has to mean what an angler means by it: this specimen is
        // in the top of its species' size range. Every floor above (livebait, lure mass, luck) can push a
        // fish into that band, which is exactly how those things work in the water.
        session.trophy = biased >= RiverFishingConfig.trophyFraction();

        // Length from weight by the real allometric law L ∝ W^(1/3) — a fish's mass grows with its volume
        // (~length³), so length tracks the CUBE ROOT of weight, anchored to the species' own length range.
        // (The old linear weight-fraction made a common mid-weight fish far too short — e.g. a 2.3 kg pike
        // came out ~56 cm instead of the real ~67 cm.) Endpoints still map min→min, max→max exactly.
        double wc = Math.cbrt(Math.max(1.0, weight));
        double wcMin = Math.cbrt(Math.max(1.0, p.weightMin));
        double wcMax = Math.cbrt(Math.max(1.0, p.weightMax));
        double lf = (wcMax > wcMin) ? (wc - wcMin) / (wcMax - wcMin) : 0.5;
        double length = p.lengthMin + (p.lengthMax - p.lengthMin) * lf;
        length *= 0.98 + random.nextDouble() * 0.04; // ±2% natural variation
        session.lengthCm = (int) Math.round(Mth.clamp(length, p.lengthMin, p.lengthMax));
    }

    private static Component fishName(Identifier species) {
        return Component.translatable("fish." + species.getNamespace() + "." + species.getPath());
    }

    // ---- gear wear (§3.8; §balance: line wears 2.5x slower, hooks 1.5x slower) ----

    private static double lineWearScaled() {
        // §wear-slow: line wears 4× slower than before (0.4 → 0.1) so a line lasts a long time.
        return RiverFishingConfig.lineWearRate() * 0.1;
    }

    private static int hookWearAmount() {
        // The amount is a whole wear point, so a rate the player merely DIMMED must not round down to
        // "never blunts" — arcade's 0.3 gives 0.4, and dullSharpestHook ignores 0. Turning hook wear
        // off is what a rate of exactly 0 is for.
        double rate = RiverFishingConfig.hookWearRate();
        return rate <= 0 ? 0 : Math.max(1, (int) Math.round(2 * rate / 1.5));
    }

    private static void addLineWear(ItemStack rod, int amount) {
        if (amount <= 0 || !(rod.getItem() instanceof RodItem)) return;
        ItemStack line = RodData.get(rod, ComponentSlot.LINE);
        if (line.isEmpty()) return;
        WearData.add(line, amount);
        RodData.set(rod, ComponentSlot.LINE, line);
    }

    /** Wear of the sharpest hook in the rig (you fish with your best hook). */
    private static int minHookWear(ItemStack rigStack) {
        if (!(rigStack.getItem() instanceof RigItem)) return 0;
        NonNullList<ItemStack> contents = RigData.load(rigStack);
        SlotRole[] roles = RigLayout.rolesFor(RigData.rigType(rigStack));
        int min = 0;
        boolean found = false;
        for (int i = 0; i < roles.length && i < contents.size(); i++) {
            if (roles[i] == SlotRole.HOOK && !contents.get(i).isEmpty()) {
                int w = WearData.get(contents.get(i));
                if (!found || w < min) {
                    min = w;
                    found = true;
                }
            }
        }
        return found ? min : 0;
    }

    /**
     * The stack this session is actually fishing with — NOT necessarily what is in the hand.
     *
     * <p>A rod pod fishes with the rod in the POD, so the player's hand holds whatever they picked up
     * since the cast. Reading the hand there sent every write to the wrong item: hook wear, line wear,
     * rod durability, eaten bait and a lost rig all went somewhere else, which is why hooks appeared to
     * stop blunting entirely for anyone fishing from a pod.
     *
     * <p>The hotbar SLOT comes first because that is what "still the same rod" means — an index survives
     * the slot being rewritten with an equal-but-different stack object, which is the trap §session-guard
     * exists for. Pods and off-hand casts have no slot (-1), and there the session's own stack is the
     * only handle on the real rod.
     */
    private static ItemStack sessionRod(ServerPlayer sp, FishingSession session) {
        if (session.rodSlot >= 0) {
            ItemStack slot = sp.getInventory().getItem(session.rodSlot);
            if (slot.getItem() instanceof RodItem) return slot;
        }
        if (!session.rodStackRef.isEmpty()) return session.rodStackRef;
        return sp.getItemInHand(session.hand);
    }

    private static void dullSharpestHook(ItemStack rod, int amount) {
        if (amount <= 0 || !(rod.getItem() instanceof RodItem)) return;
        ItemStack rig = RodData.get(rod, ComponentSlot.RIG);
        if (!(rig.getItem() instanceof RigItem)) return;
        NonNullList<ItemStack> contents = RigData.load(rig);
        SlotRole[] roles = RigLayout.rolesFor(RigData.rigType(rig));
        int best = -1;
        int bestWear = Integer.MAX_VALUE;
        for (int i = 0; i < roles.length && i < contents.size(); i++) {
            if (roles[i] == SlotRole.HOOK && !contents.get(i).isEmpty()) {
                int w = WearData.get(contents.get(i));
                if (w < bestWear) {
                    bestWear = w;
                    best = i;
                }
            }
        }
        if (best >= 0) {
            WearData.add(contents.get(best), amount);
            RigData.save(rig, contents);
            RodData.set(rod, ComponentSlot.RIG, rig);
        }
    }

    // ---- context assembly ----

    /**
     * §community (0.5.0): every ~128-block patch of water holds its own deterministic species set,
     * derived from the WORLD SEED — this lake is a tench lake forever, and the taimen river must be
     * FOUND. Small water is species-poor (60% of eligible species absent), big water rich (20%);
     * ubiquitous commons (profile base >= 0.95) live everywhere so no water is ever dead; ~8% of a
     * water's species come out as its SIGNATURE fish (×1.8 bites); and a fish RELEASED into the
     * water (§stocking) joins the set for good — that's how a server stocks its ponds.
     */
    private static java.util.function.ToDoubleFunction<Identifier> communityFactor(
            ServerLevel level, BlockPos waterPos, WaterBody body) {
        long region = StockedData.region(waterPos);
        double absent = body.width() < 8 ? 0.60 : body.width() < 16 ? 0.45 : body.width() < 32 ? 0.30 : 0.20;
        long worldSeed = level.getSeed();
        StockedData stocked = StockedData.get(level);
        FishingPressureData pd = FishingPressureData.get(level);
        int cx = waterPos.getX() >> 4, cz = waterPos.getZ() >> 4;
        return id -> {
            // §cull (0.7.0): removed from this water by an operator. FIRST, before the common-species
            // shortcut below — a culled roach is exactly the case this exists for, and roach take that
            // shortcut. Everything that asks "does this live here" comes through this lambda: the bite
            // engine, the shoal you can see in the water, the fish finder and the stocking check.
            if (stocked.isCulled(region, id.getPath())) return 0.0;
            FishProfile pr = FishProfileManager.get().byId(id);
            if (pr == null || pr.base >= 0.95) return 1.0;
            if (stocked.isStocked(region, id.getPath())) return 1.0;
            double r = hashUnit(worldSeed, region, id.getPath());
            if (r >= absent) return r > 0.92 ? 1.8 : 1.0;
            // §residency: an UNSETTLED transplant bites in proportion to its 0..100% temporary
            // population (3×3-chunk reach — fish don't respect chunk borders), dispersing as the
            // surplus decays away.
            return Math.min(1.0, pd.surplusAround(cx, cz, id.getPath(), level.getGameTime()));
        };
    }

    /**
     * §stocking 2.0: a fish RELEASED into water. Presence, surplus and settling all flow from here:
     * — a species already in the water (native or settled) banks a stock SURPLUS, scaled by the
     *   specimen's weight against the species mean (a trophy counts ~3 fish, a tiddler ~nothing —
     *   sport catch-and-release of PRIME fish is what feeds a water, not bucketfuls of fry);
     * — a species NOT living here rolls to SETTLE: chance = 0.18 × fit² × size (nonlinear in habitat
     *   fit — perfect water settles a prime fish at ~30-40%, a barely-livable one in the low single
     *   digits; water it cannot inhabit at all never settles);
     * — natives pack to 250% stock, transplants to 150% (§population floors).
     */
    public static void releaseFish(ServerLevel level, BlockPos pos, Identifier species,
                                   int weightG, int count,
                                   @org.jetbrains.annotations.Nullable ServerPlayer thrower) {
        FishProfile p = FishProfileManager.get().byId(species);
        if (p == null) return;
        // A floating item sits in the AIR block above the surface — resolve to the actual water.
        if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
            if (level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER)) pos = pos.below();
        }
        WaterBody body = WaterBodyCache.forLevel(level).get(level, pos);
        if (body.type() == WaterType.NONE) return;
        long region = StockedData.region(pos);
        long chunk = ChunkPos.pack(pos);
        long now = level.getGameTime();

        double fit = BiteEngine.environmentScore(p, habitatContext(level, pos, body));

        // §residency-guard: the community hash alone can roll "native" for a shark in a river (it
        // never looks at habitat) — native/present status flows from fit. But §settle-anything:
        // hostile water only CUTS the settle chance to its floor, it no longer forbids the attempt.
        boolean hostile = fit <= 0;
        boolean nativeHere = !hostile && nativeHere(level, pos, body, species);
        StockedData stocked = StockedData.get(level);
        boolean present = !hostile && (nativeHere || stocked.isStocked(region, species.getPath()));

        // §stock-units (0.5.1): SUPERLINEAR in size — 0.5·(w/mean)^1.5. A mean fish is half a unit
        // (a native pond needs ~17 of them for the full 250%), a double-mean trophy ~1.4 units
        // (~6 trophies), fry a rounding error. Packing a water stays real work.
        double sizeRatio = weightG / Math.max(1.0, p.weightMean);
        double units = 0.5 * Math.pow(Mth.clamp(sizeRatio, 0.0, 3.0), 1.5);
        // §stock-vs-settle (0.5.1): the two systems no longer fight over the same fish. Hostile water
        // kills the release outright; everywhere else EVERY release banks its weight units — the fish
        // physically swims here now, and while the surplus lasts the species is TEMPORARILY catchable
        // (communityFactor reads the surplus). Settling is a separate roll for PERMANENCE on top.
        boolean settledNow = false;
        double chance = 0.0;
        if (!present) {
            // §settle-anything (0.5.1): NONLINEAR in fit with a tiny floor — perfect water settles a
            // prime fish at ~20-40%, mediocre water in the low percents, and even water that fails
            // every parameter keeps a sliver (~0.5%): the chance is CUT, never zeroed. Size keeps the
            // RAW ratio (settling is about the specimen being adult, not tonnage).
            chance = 0.18 * (0.03 + Math.pow(Math.min(1.2, fit), 2.0)) * Mth.clamp(sizeRatio, 0.1, 2.0);
            for (int i = 0; i < Math.max(1, count) && !settledNow; i++) {
                if (level.getRandom().nextDouble() < chance) settledNow = true;
            }
            if (settledNow) stocked.markStocked(region, species.getPath());
        }
        FishingPressureData pressure = FishingPressureData.get(level);
        if (!hostile || settledNow) {
            // §residency: how deep the bank goes depends on the species' standing HERE —
            // native 250%, settled transplant 150%, an unsettled one builds a 0..100% temp population.
            double floor = nativeHere ? FishingPressureData.FLOOR_NATIVE
                    : (present || settledNow) ? FishingPressureData.FLOOR_SETTLED
                    : FishingPressureData.FLOOR_TRANSPLANT;
            pressure.addStock(chunk, species.getPath(), now, units * Math.max(1, count), floor);
        }

        if (thrower == null) return;
        net.minecraft.network.chat.Component name = fishName(species);
        if (settledNow) {
            thrower.sendOverlayMessage(Component.translatable("message.riverfishing.stocked_settled", name)
                    .withStyle(ChatFormatting.GREEN));
        } else if (hostile) {
            thrower.sendOverlayMessage(Component.translatable("message.riverfishing.stocked_hostile",
                    name, String.format("%.1f", chance * 100)).withStyle(ChatFormatting.RED));
        } else if (!present) {
            // §residency: a transplant has NO 100% baseline — its temp population grows from zero.
            int temp = (int) Math.round(pressure.surplus(chunk, species.getPath(), now) * 100);
            thrower.sendOverlayMessage(Component.translatable("message.riverfishing.stocked_failed",
                    name, (int) Math.round(chance * 100), temp).withStyle(ChatFormatting.GRAY));
        } else {
            thrower.sendOverlayMessage(Component.translatable("message.riverfishing.stocked",
                    name, pressure.stockPercent(chunk, species.getPath(), now))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * §stocking: the context to ask "can this species live in this water at all" against — the same
     * environment gates and factors the bite engine lives by, WITHOUT the community (settling is exactly
     * the act of joining a community the species isn't in yet) and with the time/weather noise flattened:
     * viability is about the WATER, not the hour of day.
     *
     * <p>This prices a RELEASE — how likely a fish thrown back is to settle. It is deliberately not
     * asked by the electrofisher: that is an admin item and puts any species into any water, and the
     * engine backs it up anyway, because §stocked-survival keeps a stocked fish at a quarter of full
     * activity whatever the natural gates say.
     */
    public static BiteContext habitatContext(ServerLevel level, BlockPos pos, WaterBody body) {
        BiteContext env = environmentAt(level, pos, body);
        env.communityFactor = null;
        env.time = TimeOfDay.DAY;
        env.weather = Weather.CLEAR;
        return env;
    }

    /** §residency: does the seed's community (or the commons rule) place this species here natively? */
    public static boolean nativeHere(ServerLevel level, BlockPos pos, WaterBody body, Identifier id) {
        FishProfile pr = FishProfileManager.get().byId(id);
        if (pr == null) return false;
        if (pr.base >= 0.95) return true;
        double absent = body.width() < 8 ? 0.60 : body.width() < 16 ? 0.45 : body.width() < 32 ? 0.30 : 0.20;
        return hashUnit(level.getSeed(), StockedData.region(pos), id.getPath()) >= absent;
    }

    /** §residency: native OR permanently settled — anything but a temporary transplant. */
    public static boolean residentHere(ServerLevel level, BlockPos pos, WaterBody body, Identifier id) {
        return nativeHere(level, pos, body, id)
                || StockedData.get(level).isStocked(StockedData.region(pos), id.getPath());
    }

    /** §residency: stocked presence at a spot — 1.0 settled, 0..1 temp transplant (3×3 chunks), 0 none. */
    public static java.util.function.ToDoubleFunction<Identifier> stockedPresence(
            ServerLevel level, BlockPos waterPos) {
        StockedData stocked = StockedData.get(level);
        FishingPressureData pd = FishingPressureData.get(level);
        long region = StockedData.region(waterPos);
        int cx = waterPos.getX() >> 4, cz = waterPos.getZ() >> 4;
        return id -> stocked.isStocked(region, id.getPath()) ? 1.0
                : Math.min(1.0, pd.surplusAround(cx, cz, id.getPath(), level.getGameTime()));
    }

    /** Environment-only context at a spot (no tackle): habitat + season/time/weather + community. */
    public static BiteContext environmentAt(ServerLevel level, BlockPos pos, WaterBody body) {
        BiteContext env = new BiteContext();
        env.water = body.type();
        env.waterWidth = body.width();
        env.waterDepth = measureDepth(level, pos);
        env.biomeGroups = biomeGroups(level, pos, body);
        env.season = SeasonProvider.getSeason(level);
        env.time = TimeOfDay.fromDayTime(level.getOverworldClockTime());
        env.weather = level.isThundering() ? Weather.THUNDER : (level.isRaining() ? Weather.RAIN : Weather.CLEAR);
        env.biomeTemperature = level.getBiome(pos).value().getBaseTemperature();
        env.anglerLevel = Integer.MAX_VALUE;
        env.communityFactor = communityFactor(level, pos, body);
        env.stockedPresence = stockedPresence(level, pos);
        return env;
    }

    /** §community: a stable [0,1) roll from (world seed, water region, species) — splitmix-style. */
    private static double hashUnit(long seed, long region, String species) {
        long h = seed ^ region * 0x9E3779B97F4A7C15L ^ (long) species.hashCode() * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) / (double) (1L << 53);
    }

    private static BiteContext buildContext(ServerPlayer sp, ServerLevel level, ItemStack rod,
                                            InteractionHand hand, WaterBody body, BlockPos waterPos,
                                            double castDistance, long now) {
        BiteContext ctx = new BiteContext();
        ctx.rod = ((RodItem) rod.getItem()).rodType();
        ctx.anglerLevel = JournalData.getLevel(sp);
        // §skills NATURALIST: a flat overall bite-chance bonus (+5%/rank).
        ctx.skillBiteBonus = AnglerSkills.naturalistBonus(sp);

        ItemStack reel = RodData.get(rod, ComponentSlot.REEL);
        if (reel.getItem() instanceof ReelItem r) ctx.reelSize = r.size();

        ItemStack line = RodData.get(rod, ComponentSlot.LINE);
        if (line.getItem() instanceof LineItem l) {
            ctx.lineType = l.lineType();
            ctx.lineDiameterMm = l.diameterMm();
        }

        // Module 4: hooks, baits, groundbait, leader all come from the rig's own inventory.
        ItemStack rigStack = RodData.get(rod, ComponentSlot.RIG);
        if (rigStack.getItem() instanceof RigItem rg) {
            ctx.rig = rg.rigType();
            // §tackle-station (0.6.0): bench-chosen grams (rig + tied lure) over the fixed type mass.
            ctx.castWeightG = RigData.effectiveWeightG(rigStack);
            ctx.lureWeightG = RigData.lureTackleWeightG(rigStack); // §lure-size: size gates the take
            ctx.hookSizes = RigData.hookSizes(rigStack);
            ctx.baits = RigData.baitIds(rigStack);
            int lureRgb = RigData.lureColorRgb(rigStack);
            ctx.lureColor = lureRgb >= 0 ? com.riverfishing.engine.LureColor.fromRgb(lureRgb) : null;
            ctx.hasLeader = RigData.hasLeader(rigStack);
            ctx.leaderProtection = RigData.leaderProtection(rigStack);
            ctx.leaderStealth = RigData.leaderStealth(rigStack);
            // Спуск (§fishing-depth): the rod's depth slider applies whenever a float is rigged.
            if (RigData.hasFloat(rigStack)) {
                ctx.floatDepth = RodData.getDepth(rod);
            }
        }

        ctx.water = body.type();
        ctx.biomeRiver = body.river();
        ctx.biomeSwamp = body.swamp();
        ctx.biomeOcean = body.ocean();
        ctx.waterWidth = body.width();
        ctx.castDistance = castDistance;

        // §population: per-species depletion at this spot — a fished-out species stops biting HERE while
        // the others carry on; recovery is time-based (faster in spring, §spawn-recovery).
        FishingPressureData popData = FishingPressureData.get(level);
        long popChunk = ChunkPos.pack(waterPos);
        double popRegen = spawnRegen(level);
        ctx.speciesFactor = id -> popData.speciesAttractiveness(popChunk, id.getPath(), now, popRegen);
        ctx.communityFactor = communityFactor(level, waterPos, body);
        ctx.stockedPresence = stockedPresence(level, waterPos);
        ctx.season = SeasonProvider.getSeason(level);
        ctx.time = TimeOfDay.fromDayTime(level.getOverworldClockTime());
        ctx.weather = level.isThundering() ? Weather.THUNDER : (level.isRaining() ? Weather.RAIN : Weather.CLEAR);
        ctx.pressureFactor = com.riverfishing.engine.BarometricPressure.biteFactor(level);
        ctx.biomeTemperature = level.getBiome(waterPos).value().getBaseTemperature();
        ctx.waterDepth = measureDepth(level, waterPos);
        ctx.biomeGroups = biomeGroups(level, waterPos, body);

        // §feed-lands-where-the-rig-does: THE FED ZONE AT THE BOBBER IS THE ONLY ANSWER.
        //
        // There used to be a second one beside it: a cage with groundbait in it claimed a flat 0.5
        // freshness of its own, and being the bigger number it usually WON — so the engine scored the
        // swim against a phantom that had never been thrown, at fraction 0.00, which is dust. A carp
        // angler with a cage of whole grain was quietly marked down for fishing a cloud, and no amount
        // of feeding the actual water could beat the phantom while the cage was loaded.
        //
        // The cage never needed a shortcut: it really does empty into the water at this exact position
        // on the cast, through the same feed() call a right-click makes. One thing feeds the swim, one
        // thing reads it, and the two cannot disagree because there is only one of them.
        FeedZoneData.Query feed = FeedZoneData.get(level).query(waterPos, now);
        ctx.inFeedZone = feed.inZone();
        ctx.feedFreshness = feed.inZone() ? feed.freshness() : 0.0;
        ctx.feedMix = feed.mix();

        return ctx;
    }

    /**
     * Water analysis for the fish finder / admin probe (§QoL). Environment-only (no tackle): lists
     * which species CAN bite here right now. The admin variant adds the full habitat summary,
     * per-species environment scores, level gates and the species' favourite bait.
     */
    /**
     * §cull: everything that can be caught in this water right now, best fit first — the same set and the
     * same order the fish finder prints, because it is the same question asked of the same function.
     */
    public static java.util.List<Identifier> speciesHere(ServerLevel level, BlockPos waterPos) {
        WaterBody body = WaterBodyCache.forLevel(level).get(level, waterPos);
        if (body.type() == WaterType.NONE) return java.util.List.of();
        BiteContext env = environmentAt(level, waterPos, body);
        java.util.List<java.util.Map.Entry<Identifier, Double>> here = new java.util.ArrayList<>();
        for (FishProfile p : FishProfileManager.get().all()) {
            double e = BiteEngine.environmentScore(p, env);
            if (e > 1e-4) here.add(java.util.Map.entry(p.id, e));
        }
        here.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return here.stream().map(java.util.Map.Entry::getKey).toList();
    }

    public static void analyzeWater(ServerPlayer sp, ServerLevel level, BlockPos waterPos, boolean admin) {
        WaterBody body = WaterBodyCache.forLevel(level).get(level, waterPos);
        if (body.type() == WaterType.NONE) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return;
        }
        // §dedupe (0.5.1): the finder/tablet view the water through the SAME context builder the bite
        // engine uses (community + stocked presence included) — hand-built copies kept drifting: the
        // settled-shark presence floor was missing here, so stocked species stayed invisible.
        BiteContext env = environmentAt(level, waterPos, body);

        java.util.List<java.util.Map.Entry<FishProfile, Double>> here = new java.util.ArrayList<>();
        for (FishProfile p : FishProfileManager.get().all()) {
            double e = BiteEngine.environmentScore(p, env);
            if (e > 1e-4) here.add(java.util.Map.entry(p, e));
        }
        here.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (admin) {
            sp.sendSystemMessage(Component.literal("== RiverFishing probe ==").withStyle(ChatFormatting.GOLD));
            sp.sendSystemMessage(Component.literal(String.format("water=%s width=%.0f depth=%d biomes=%s",
                    body.type().key(), body.width(), env.waterDepth, env.biomeGroups))
                    .withStyle(ChatFormatting.GRAY));
            sp.sendSystemMessage(Component.literal(String.format("season=%s time=%s weather=%s frenzy=%s",
                    env.season == null ? "-" : env.season.jsonKey(), env.time.jsonKey(),
                    env.weather.jsonKey(), isFrenzy(level)))
                    .withStyle(ChatFormatting.GRAY));
            sp.sendSystemMessage(Component.literal(String.format("pressure=%.1fhPa trend=%+.1f factor=%.2f",
                    BarometricPressure.hPa(level), BarometricPressure.trend(level),
                    BarometricPressure.biteFactor(level)))
                    .withStyle(ChatFormatting.GRAY));
            FishingPressureData probeStock = FishingPressureData.get(level);
            long probeChunk = ChunkPos.pack(waterPos);
            for (var e : here) {
                FishProfile p = e.getKey();
                String bait = topBait(p);
                boolean resident = residentHere(level, waterPos, body, p.id);
                int pct = resident
                        ? probeStock.stockPercent(probeChunk, p.id.getPath(), level.getGameTime())
                        : (int) Math.round(probeStock.surplusAround(waterPos.getX() >> 4, waterPos.getZ() >> 4,
                                p.id.getPath(), level.getGameTime()) * 100);
                sp.sendSystemMessage(Component.literal(String.format("E=%.2f  ", e.getValue()))
                        .withStyle(ChatFormatting.AQUA)
                        .append(fishName(p.id))
                        .append(Component.literal(String.format("  lvl>=%d  %s=%d%%  bait: %s",
                                p.minAnglerLevel, resident ? "stock" : "TEMP", pct, bait))
                                .withStyle(resident ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD)));
            }
            // Diagnosis (§QoL): group the GATED species by the first gate that blocks them here.
            java.util.Map<String, java.util.List<String>> blocked = new java.util.LinkedHashMap<>();
            for (FishProfile p : FishProfileManager.get().all()) {
                double e = BiteEngine.environmentScore(p, env);
                if (e > 1e-4) continue;
                blocked.computeIfAbsent(gateReason(p, env), k -> new java.util.ArrayList<>())
                        .add(fishName(p.id).getString());
            }
            for (var e : blocked.entrySet()) {
                sp.sendSystemMessage(Component.literal("blocked[" + e.getKey() + "]: "
                        + String.join(", ", e.getValue())).withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }

        // Player-facing fish finder: just the species list, no numbers.
        if (here.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("finder.riverfishing.none")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        net.minecraft.network.chat.MutableComponent list = Component.empty();
        int shown = 0;
        for (var e : here) {
            if (shown > 0) list.append(Component.literal(", "));
            list.append(fishName(e.getKey().id));
            if (++shown >= 8) break;
        }
        sp.sendSystemMessage(Component.translatable("finder.riverfishing.header")
                .withStyle(ChatFormatting.AQUA));
        sp.sendSystemMessage(list.withStyle(ChatFormatting.WHITE));
        // §community: name the water's signature species — the "this is a tench lake" line.
        net.minecraft.network.chat.MutableComponent sig = null;
        for (var e : here) {
            if (env.communityFactor.applyAsDouble(e.getKey().id) > 1.0) {
                if (sig == null) sig = Component.empty();
                else sig.append(Component.literal(", "));
                sig.append(fishName(e.getKey().id));
            }
        }
        if (sig != null) {
            sp.sendSystemMessage(Component.translatable("finder.riverfishing.signature", sig)
                    .withStyle(ChatFormatting.GOLD));
        }
        // §stocking / §residency: live per-species stock. Residents show their 10..250% around the
        // 100% baseline; an unsettled transplant shows its 0..100% TEMP population with a marker.
        FishingPressureData stockData = FishingPressureData.get(level);
        long stockChunk = ChunkPos.pack(waterPos);
        net.minecraft.network.chat.MutableComponent stockLine = null;
        for (var e : here) {
            boolean resident = residentHere(level, waterPos, body, e.getKey().id);
            int pct = resident
                    ? stockData.stockPercent(stockChunk, e.getKey().id.getPath(), level.getGameTime())
                    : (int) Math.round(stockData.surplusAround(waterPos.getX() >> 4, waterPos.getZ() >> 4,
                            e.getKey().id.getPath(), level.getGameTime()) * 100);
            if (resident && Math.abs(pct - 100) < 10) continue;
            if (stockLine == null) stockLine = Component.empty();
            else stockLine.append(Component.literal(", "));
            stockLine.append(fishName(e.getKey().id)).append(Component.literal(" " + pct + "%"));
            if (!resident) stockLine.append(Component.translatable("finder.riverfishing.temp"));
        }
        if (stockLine != null) {
            sp.sendSystemMessage(Component.translatable("finder.riverfishing.stock", stockLine)
                    .withStyle(ChatFormatting.AQUA));
        }
        sp.sendSystemMessage(pressureLine(level));
        level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    /**
     * §finder-screen: everything the fish finder draws, assembled where the data already is.
     *
     * <p>The screen cannot work any of this out for itself. Fish profiles load as SERVER_DATA, so a
     * client on a dedicated server has none of them, and the community, stock and pressure numbers are
     * world state. So the server answers the whole question once, in keys rather than sentences, and the
     * client turns {@code water.riverfishing.river} into its own language.
     *
     * <p>The BLOCKED species ride along with the gate that blocks them. That diagnosis already existed —
     * it was written for the admin probe and shown to nobody else, which is a waste: "pike-perch: too
     * shallow here" is the single most useful thing this tool can say, and it was hidden behind a
     * creative-only item.
     */
    public static CompoundTag finderPayload(ServerPlayer sp, ServerLevel level, BlockPos waterPos) {
        return finderPayload(sp, level, waterPos, true);
    }

    /**
     * §finder-hud: {@code full=false} is the strip's sounding — the section and nothing else. It runs
     * once a second for every player holding a finder, so it carries no blocked list, no stock query and
     * no bait scan: three lookups per species, per second, per angler, to draw something the strip has
     * no room to print anyway.
     */
    public static CompoundTag finderPayload(ServerPlayer sp, ServerLevel level, BlockPos waterPos,
                                            boolean full) {
        CompoundTag root = new CompoundTag();
        WaterBody body = WaterBodyCache.forLevel(level).get(level, waterPos);
        if (body.type() == WaterType.NONE) return root;
        BiteContext env = environmentAt(level, waterPos, body);

        CompoundTag w = new CompoundTag();
        w.putString("type", body.type().key());
        w.putFloat("width", (float) body.width());
        w.putInt("depth", env.waterDepth);
        w.putString("season", env.season == null ? "" : env.season.jsonKey());
        w.putString("time", env.time.jsonKey());
        w.putString("weather", env.weather.jsonKey());
        w.putInt("hpa", (int) Math.round(BarometricPressure.hPa(level)));
        w.putInt("trend", BarometricPressure.trendSign(level));
        w.putString("outlook", BarometricPressure.outlookKey(level));
        w.putInt("x", waterPos.getX());
        w.putInt("y", waterPos.getY());
        w.putInt("z", waterPos.getZ());
        w.putBoolean("frenzy", isFrenzy(level));
        w.putByte("bed", bedType(level, waterPos));
        root.put("water", w);

        FishingPressureData stock = FishingPressureData.get(level);
        long chunk = ChunkPos.pack(waterPos);
        int anglerLevel = JournalData.getLevel(sp);

        ListTag here = new ListTag();
        ListTag gone = new ListTag();
        for (FishProfile p : FishProfileManager.get().all()) {
            double e = BiteEngine.environmentScore(p, env);
            CompoundTag t = new CompoundTag();
            t.putString("sp", p.id.getPath());
            t.putInt("dmin", p.depthMin);
            t.putInt("dmax", p.depthMax);
            t.putInt("lvl", p.minAnglerLevel);
            if (e <= 1e-4) {
                // Only what the player could plausibly meet: the whole 93-species list with a reason
                // each is the wall of text this screen exists to replace.
                if (full && p.minAnglerLevel <= anglerLevel + 5) {
                    t.putString("why", gateReason(p, env));
                    gone.add(t);
                }
                continue;
            }
            t.putFloat("e", (float) e);
            t.putBoolean("sig", env.communityFactor.applyAsDouble(p.id) > 1.0);
            if (full) {
                boolean resident = residentHere(level, waterPos, body, p.id);
                t.putString("bait", topBait(p));
                t.putBoolean("res", resident);
                t.putInt("stock", resident
                        ? stock.stockPercent(chunk, p.id.getPath(), level.getGameTime())
                        : (int) Math.round(stock.surplusAround(waterPos.getX() >> 4, waterPos.getZ() >> 4,
                                p.id.getPath(), level.getGameTime()) * 100));
            }
            here.add(t);
        }
        root.put("here", here);
        root.put("gone", gone);
        if (full) {
            root.put("map", soundingMap(level, waterPos));
            root.put("profile", profileAlong(sp, level));
            root.putByteArray("wet", wetMask(level, waterPos));
            root.putInt("yaw", Math.round(sp.getYRot()));
        }
        return root;
    }

    /**
     * The fish finder's barometer read-out (§weather-pressure): pressure in hPa, a trend arrow, and a
     * colour-coded bite outlook — all straight from {@link BarometricPressure} so it can't drift.
     */
    private static Component pressureLine(ServerLevel level) {
        int hpa = (int) Math.round(BarometricPressure.hPa(level));
        int sign = BarometricPressure.trendSign(level);
        String arrow = sign < 0 ? "↓" : (sign > 0 ? "↑" : "→");
        String outlook = BarometricPressure.outlookKey(level);
        ChatFormatting colour = switch (outlook) {
            case "great" -> ChatFormatting.GREEN;
            case "good" -> ChatFormatting.DARK_GREEN;
            case "fair" -> ChatFormatting.YELLOW;
            case "poor" -> ChatFormatting.RED;
            default -> ChatFormatting.DARK_RED;
        };
        return Component.translatable("finder.riverfishing.pressure", hpa, arrow)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" "))
                .append(Component.translatable("finder.riverfishing.outlook." + outlook).withStyle(colour));
    }

    /** Which habitat gate blocks this species here — mirrors environmentScore's order (§QoL). */
    /**
     * §why-nothing: instead of "nothing bites here", say what is actually in the way.
     *
     * <p>Every species is asked the engine's own question ({@link BiteEngine#blockReason}) and the most
     * common answer wins. Two different situations, and the difference is the whole point:
     * <ul>
     *   <li>Some fish WOULD take, but your kit stops them — the bait, or the hook size. That is on you,
     *       and it is the case a player can fix in ten seconds once they know.</li>
     *   <li>Nothing here is feeding at all — then the answer is the water, the hour, the season or the
     *       weather, and the honest advice is to come back or move.</li>
     * </ul>
     *
     * <p>Deliberately a HINT, not an instruction: it names the category and never the answer. "The fish
     * here will not take that bait" sends a player to the journal; "use a worm" sends them to sleep.
     */
    private static void noBitesHint(ServerPlayer sp, BiteContext ctx) {
        java.util.Map<String, Integer> tackle = new java.util.HashMap<>();
        java.util.Map<String, Integer> absent = new java.util.HashMap<>();
        int couldBeHere = 0;
        for (FishProfile p : FishProfileManager.get().all()) {
            String r = BiteEngine.blockReason(p, ctx);
            if ("absent".equals(r)) {
                // Only species that at least live in THIS KIND of water can say anything useful about
                // why the swim is dead — a marlin has no opinion about a village pond.
                if (p.waterFactor(ctx.water) > 0) {
                    String g = gateReason(p, ctx);
                    int br = g.indexOf('(');
                    absent.merge(br < 0 ? g : g.substring(0, br), 1, Integer::sum);
                }
                continue;
            }
            couldBeHere++;
            if (r != null) tackle.merge(r, 1, Integer::sum);
        }
        String key = couldBeHere > 0 ? top(tackle, "other") : top(absent, "water");
        actionbar(sp, Component.translatable("message.riverfishing.no_bites." + key)
                .withStyle(ChatFormatting.GRAY));
    }

    private static String top(java.util.Map<String, Integer> tally, String fallback) {
        String best = fallback;
        int n = 0;
        for (var e : tally.entrySet()) {
            if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    private static String gateReason(FishProfile p, BiteContext c) {
        if (p.waterFactor(c.water) <= 0) return "water";
        if (c.waterDepth < p.depthMin || c.waterDepth > p.depthMax) return "depth(" + c.waterDepth + ")";
        if (c.waterWidth < p.widthMin || c.waterWidth > p.widthMax) return "width";
        if (!p.biomes.isEmpty()) {
            boolean any = false;
            for (var e : p.biomes.entrySet()) {
                if (c.biomeGroups.contains(e.getKey()) && e.getValue() > 0) any = true;
            }
            if (!any) return "biome";
        }
        if (p.seasonFactor(c.season) <= 0) return "season";
        if (p.timeFactor(c.time) <= 0) return "time";
        if (p.weatherFactor(c.weather) <= 0) return "weather";
        return "other";
    }

    private static String topBait(FishProfile p) {
        String best = "-";
        double bestV = 0;
        for (var e : p.baitScores.entrySet()) {
            if (e.getValue() > bestV) { bestV = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    /** Water-column depth at the cast point (blocks of water straight down, capped) — habitat gate. */
    /** Package-visible: §spook reads the same depth the bite engine does rather than measuring its own. */
    static int measureDepth(ServerLevel level, BlockPos surface) {
        int depth = 0;
        BlockPos.MutableBlockPos p = surface.mutable();
        while (depth < 16 && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
            depth++;
            p.move(0, -1, 0);
        }
        return depth;
    }

    /**
     * Classifies the spot into biome groups for the habitat model (§ecology): climate from the base
     * temperature plus terrain from vanilla biome tags (BoP biomes carry these tags too), plus the
     * mod's own swamp tag (which lists BoP swamps explicitly).
     */
    private static java.util.Set<String> biomeGroups(ServerLevel level, BlockPos pos, WaterBody body) {
        java.util.Set<String> groups = new java.util.HashSet<>();
        var biome = level.getBiome(pos);
        addBiomeGroups(biome, groups);
        // §river-banks: a river is its OWN biome — minecraft:river, temperature 0.5 — so the land it runs
        // through never reached the fish. A river across a taiga read as plain temperate water, and since
        // a missed biome list is a hard zero (BiteEngine.biomeGroupFactor -> speciesWeight), twenty of the
        // sixty river species could not be caught in a river at all: the whole northern group (taimen,
        // trout, grayling, lenok, char, whitefish) wants cold/taiga/mountain, and the taimen has the
        // highest river affinity in the mod. Lakes were always fine — a lake sits inside the land biome.
        //
        // So a river asks its banks. The land's groups are merged in, and because biomeGroupFactor takes
        // the BEST listed group, a taiga river reads as both cold and temperate: the taimen can live there
        // without evicting the roach.
        if (biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)) {
            groups.addAll(riverBankGroups(level, pos));
        }
        if (body.swamp() || biome.is(com.riverfishing.water.ModBiomeTags.IS_SWAMP)) groups.add("swamp");
        return groups;
    }

    /**
     * §river-banks: what the land around a river block is made of.
     *
     * <p>Eight compass directions, stepping out until the first sample that is neither river nor ocean —
     * that is the bank. Water samples are skipped rather than accepted, so a river mouth does not import
     * the sea (and could not anyway: the species' own {@code water_bodies} still gates it).
     *
     * <p>Cached without a TTL, because a biome is a pure function of position and seed and never changes.
     * The map is bounded and keyed by chunk: a river's banks do not differ meaningfully inside one.
     */
    private static final java.util.LinkedHashMap<Long, java.util.Set<String>> BANK_CACHE =
            new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Long, java.util.Set<String>> e) {
                    return size() > 2048;
                }
            };

    private static final int[] BANK_STEPS = {6, 12, 20, 32};
    private static final int[][] BANK_DIRS =
            {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static java.util.Set<String> riverBankGroups(ServerLevel level, BlockPos pos) {
        long key = ((long) (pos.getX() >> 4) << 32) ^ ((pos.getZ() >> 4) & 0xffffffffL);
        synchronized (BANK_CACHE) {
            java.util.Set<String> hit = BANK_CACHE.get(key);
            if (hit != null) return hit;
        }
        java.util.Set<String> found = new java.util.HashSet<>();
        for (int[] d : BANK_DIRS) {
            for (int step : BANK_STEPS) {
                var b = level.getBiome(pos.offset(d[0] * step, 0, d[1] * step));
                if (b.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                        || b.is(net.minecraft.tags.BiomeTags.IS_OCEAN)
                        || b.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) {
                    continue;   // still water in this direction — keep walking
                }
                addBiomeGroups(b, found);
                break;          // the first land in this direction is the bank
            }
        }
        synchronized (BANK_CACHE) {
            BANK_CACHE.put(key, found);
        }
        return found;
    }

    /**
     * One biome to its groups. Pulled out so the water block and each bank sample are read by the same
     * function — two copies of this mapping would be two answers to "what kind of place is this".
     *
     * <p>The swamp group is NOT here: it also depends on the water body's own shape, so it stays with the
     * caller that has one.
     */
    private static void addBiomeGroups(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
                                       java.util.Set<String> groups) {
        float temp = biome.value().getBaseTemperature();
        groups.add(temp < 0.3f ? "cold" : (temp > 0.95f ? "warm" : "temperate"));
        if (biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)) groups.add("river_biome");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) groups.add("ocean_biome");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) groups.add("deep"); // ocean-zones (0.5.0)
        if (biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)) groups.add("beach");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE)) groups.add("jungle");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_FOREST)) groups.add("forest");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_TAIGA)) groups.add("taiga");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN) || biome.is(net.minecraft.tags.BiomeTags.IS_HILL)) groups.add("mountain");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_SAVANNA) || biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS)) groups.add("dry");
        // §koi: cherry groves are koi water. Match by name so vanilla cherry_grove AND BoP
        // cherry_blossom_grove both count without needing a dedicated tag.
        biome.unwrapKey().ifPresent(k -> {
            if (k.identifier().getPath().contains("cherry")) groups.add("cherry");
        });
    }

    // ---- presentation ----

    private static void playCast(ServerLevel level, BlockPos pos, RodClass rodClass) {
        // §sound: a spinning whirr, a heavy long-cast whoosh, or a light float plop (§sound).
        net.minecraft.sounds.SoundEvent cast = switch (rodClass) {
            case ACTIVE -> com.riverfishing.registry.ModSounds.CAST_SPIN.get();
            case BOTTOM -> com.riverfishing.registry.ModSounds.CAST_BOTTOM.get();
            default -> null; // FLOAT: keep the gentle vanilla bobber plop
        };
        if (cast != null) {
            level.playSound(null, pos, cast, SoundSource.PLAYERS, 0.9f, 1.0f);
        } else {
            level.playSound(null, pos, SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                8, 0.2, 0.1, 0.2, 0.1);
        // §spook: the tackle hitting the water is the ONE disturbance that reaches a spot the angler is
        // nowhere near — which is exactly why a long cast is worth making. A feeder lead lands with a
        // thump, a lure with a slap, a float with a plop, and the fish under each react accordingly.
        SpookTracker.onCastLanded(level, pos, switch (rodClass) {
            case BOTTOM -> 0.32;
            case ACTIVE -> 0.20;
            default -> 0.12;
        });
    }

    /** §silent-bite: a bite is VISUAL only — no sound unless a mounted alarm reports it. */
    private static void playBite(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                14, 0.25, 0.0, 0.25, 0.2);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                12, 0.2, 0.1, 0.2, 0.2);
    }

    private static void playLand(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                20, 0.3, 0.2, 0.3, 0.25);
    }

    private static void actionbar(ServerPlayer sp, Component message) {
        sp.sendOverlayMessage(message);
    }
}
