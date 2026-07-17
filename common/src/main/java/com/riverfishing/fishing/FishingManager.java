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
    private static final double TACKLE_BREAK_CHANCE = 0.008; // §10: 0.8% per hook-up, the line parts, rig lost
    // §snag: per fishing action, 3% a dead (глухой) snag that loses the rig, 7% a recoverable one you
    // tug free. Scaled by the difficulty config's snagChance().
    private static final double SNAG_DEAD_CHANCE = 0.03;
    private static final double SNAG_TOTAL_CHANCE = 0.10;

    private FishingManager() {}

    public static void clear(UUID uuid) {
        FishingSession session = SESSIONS.remove(uuid);
        if (session != null && session.bossBar != null) {
            session.bossBar.removeAllPlayers();
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
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), false, null, 0f, 0, false)); // line now lives on the pod
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
                session.rodClass == RodClass.FLOAT));
        hookUp(sp, level, session, now);
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
                // retrieve is driven by holding right-click (onUseTick); nothing to do on the press
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

    public static boolean hasSession(ServerPlayer sp) {
        return SESSIONS.containsKey(sp.getUUID());
    }

    /** Reels the line in (ends the session) if one is out — used when opening the assembly GUI (§session-guard). */
    public static void reelInIfAny(ServerPlayer sp) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session != null) {
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.reeled_in"));
        }
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
            actionbar(sp, Component.translatable("message.riverfishing.not_assembled").withStyle(ChatFormatting.RED));
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
        if (rigCheck.getItem() instanceof RigItem ri
                && type.castWeightMin() > 0
                && ri.rigType().massGrams() < type.castWeightMin()) {
            maxRange *= 0.55;
            underloaded = true;
            actionbar(sp, Component.translatable("message.riverfishing.rod_underloaded").withStyle(ChatFormatting.YELLOW));
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
        double rodMax = ctx.rod.castWeightMax();
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
            actionbar(sp, Component.translatable("message.riverfishing.no_bites_here").withStyle(ChatFormatting.GRAY));
            return false;
        }

        Identifier species = maybeKoi(outcome.pickSpecies(random), ctx, random);

        // Chunk fishing pressure (Module 7): a fished-out spot makes bites much slower (W_total falls).
        FishingPressureData pressure = FishingPressureData.get(level);
        long chunkKey = ChunkPos.pack(waterPos);
        double depletion = pressure.attractiveness(chunkKey, now, spawnRegen(level));
        // §skills QUICK_BITE: a keen angler feels the bite sooner (shorter wait).
        // §rod-test: an under-loaded blank presents the bait clumsily — a SILENT ~20% fewer bites
        // (longer wait). Never announced (the player only sees the shortened cast).
        double underloadWait = underloaded ? 1.25 : 1.0;
        long delay = (long) Math.min(2400, outcome.ticksToBite / Math.max(0.1, depletion)
                * AnglerSkills.biteSpeedMult(sp) * underloadWait);
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

        // ACTIVE rods only "bite" while being retrieved, so their clock starts on the first retrieve tick.
        long biteAt = (rodClass == RodClass.ACTIVE) ? -1 : now + delay;
        FishingSession session = new FishingSession(hand, waterPos, rodClass, delay, biteAt, species);
        // Worn line keeps less of its strain; a dull hook is read from the rig (§3.8).
        int lineWear = WearData.get(RodData.get(rod, ComponentSlot.LINE));
        if (lineWear >= 100) {
            actionbar(sp, Component.translatable("message.riverfishing.line_worn_out").withStyle(ChatFormatting.RED));
        }
        session.lineStrainKg = ctx.lineType.breakingStrainKg(ctx.lineDiameterMm) * WearData.lineStrainMultiplier(lineWear);
        session.dragKg = ctx.reelSize / 1000.0;
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
        com.riverfishing.item.RodData.setLineOut(rod, true); // §rod-layers: hide in-hand tackle overlays
        SESSIONS.put(sp.getUUID(), session);
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, waterPos, 0f, session.lineColor,
                rodClass == RodClass.FLOAT));

        pressure.addCast(chunkKey, now);

        // A feeder cage empties one groundbait per cast and feeds the landing spot (§consumables) —
        // exactly like hand-feeding the water with a right-click.
        ItemStack rigNow = RodData.get(rod, ComponentSlot.RIG);
        if (RiverFishingConfig.consumeGroundbait() && rigNow.getItem() instanceof RigItem) {
            String fedCategory = RigData.consumeGroundbait(rigNow);
            if (fedCategory != null) {
                RodData.set(rod, ComponentSlot.RIG, rigNow);
                FeedZoneData.get(level).feed(waterPos, fedCategory, now);
            }
        }
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
            actionbar(sp, Component.translatable("message.riverfishing.not_assembled").withStyle(ChatFormatting.RED));
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
            actionbar(sp, Component.translatable("message.riverfishing.no_bites_here").withStyle(ChatFormatting.GRAY));
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
        com.riverfishing.item.RodData.setLineOut(rod, true); // §rod-layers: hide in-hand tackle overlays
        SESSIONS.put(sp.getUUID(), session);
        pressure.addCast(chunkKey, now);
        // §ice-fishing: no float on the line under the ice — the line just drops into the hole (bobber=false).
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, waterPos, 0f, session.lineColor, false));
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
        }
        return null;
    }

    /** Called every tick the player holds right-click on a spinning rod (Module 1 retrieve). */
    public static void retrieveTick(ServerPlayer sp) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session == null || session.rodClass != RodClass.ACTIVE || session.bitten || session.fighting) return;
        ServerLevel level = sp.level();
        long now = level.getGameTime();

        if (session.biteAtTick < 0) {
            session.biteAtTick = now + session.biteDelay; // start the clock on first retrieve
        }
        session.retrieving = true;
        session.retrieveTicks++;

        // §topwater (0.4.0): a popper is fished on the SURFACE with a pop-pause cadence, not a straight
        // crank. Detected once per cast from the rig's lure slot; everything below rides the same
        // hold-to-retrieve input — a "pop" is simply resuming the retrieve after a short pause.
        if (session.retrieveTicks == 1) {
            ItemStack tw = sp.getItemInHand(session.hand);
            if (tw.getItem() instanceof RodItem) {
                ItemStack rg = RodData.get(tw, ComponentSlot.RIG);
                session.topwater = rg.getItem() instanceof RigItem && RigData.baitIds(rg).contains("popper");
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
                    session.lineColor, false));
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

        if (now >= session.biteAtTick) {
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
        }
    }

    /** A snag near the bank (§7.1): {@code lost} = a dead (глухой) snag that costs the rig, else tug free. */
    private static void handleSnag(ServerPlayer sp, ServerLevel level, FishingSession session, boolean lost) {
        sp.stopUsingItem();
        ItemStack rod = sp.getItemInHand(session.hand);
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
        }
        endSession(sp, session);
    }

    /** Called when the player releases right-click on a spinning rod. */
    public static void onRetrieveStop(ServerPlayer sp) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session == null || session.rodClass != RodClass.ACTIVE) return;
        if (session.fighting) return;
        if (session.bitten) {
            // §strike-qte (2.4): letting go of the retrieve DURING the take is a valid hook-set — check the
            // runner now. (Clicking again works too, via handleRodUse.) If it's not up yet, do nothing and
            // leave the bite window open (tick() times it out if the player never reacts).
            if (session.floatPeriod > 0 && sp.level().getGameTime() <= session.biteWindowEnd) {
                activeStrike(sp, sp.level(), session, sp.level().getGameTime());
            }
            return;
        }
        endSession(sp, session);
        actionbar(sp, Component.translatable("message.riverfishing.retrieve_empty").withStyle(ChatFormatting.GRAY));
    }

    // ---- per-tick progress (FLOAT / BOTTOM waiting, and the fight for all classes) ----

    public static void tick(ServerPlayer sp) {
        FishingSession session = SESSIONS.get(sp.getUUID());
        if (session == null) return;
        ServerLevel level = sp.level();
        long now = level.getGameTime();

        // The line is tied to THE rod it was cast with: switching hotbar slots (a different stack in
        // hand) drops the cast (§session-guard), same as walking away.
        ItemStack inHand = sp.getItemInHand(session.hand);
        boolean holdingRod = inHand.getItem() instanceof RodItem
                && (session.rodStackRef.isEmpty() || inHand == session.rodStackRef);
        boolean tooFar = sp.distanceToSqr(session.target.getX() + 0.5, sp.getY(), session.target.getZ() + 0.5)
                > MAX_SESSION_DISTANCE * MAX_SESSION_DISTANCE;
        if (!holdingRod || tooFar) {
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
                    visProgress, session.lineColor, session.rodClass == RodClass.FLOAT,
                    session.bitten && !session.fighting && now <= session.biteWindowEnd));
        }

        if (session.fighting) {
            tickFight(sp, level, session, now);
            return;
        }

        if (session.rodClass == RodClass.ACTIVE) {
            // Bites only fire during retrieve (handled in retrieveTick); here we only time out the strike.
            if (session.bitten && now > session.biteWindowEnd) {
                endSession(sp, session);
                actionbar(sp, Component.translatable("message.riverfishing.missed").withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        // FLOAT / BOTTOM: wait for the bite, then a window to strike.
        if (!session.bitten) {
            if (now >= session.biteAtTick) {
                session.bitten = true;
                session.biteWindowEnd = now + biteWindow(session.rodClass);
                // §silent-bite: NO audible cue without an alarm — watch the float / the line.
                playBite(level, session.target);
                // §catch-the-moment: NO "Поклёвка!" text — the bobber PLUNGES on the client and
                // that's the whole cue; spotting it is the game.
                ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target, 0f,
                        session.lineColor, session.rodClass == RodClass.FLOAT && !session.iceFishing, true));
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
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.missed").withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- hook-up: start the fight ----

    private static void hookUp(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        sp.stopUsingItem(); // stop any retrieve animation
        clearFloatTiming(sp); // hide the timing HUD if it was up
        FishProfile profile = FishProfileManager.get().byId(session.species);
        if (profile == null) {
            endSession(sp, session);
            return;
        }
        RandomSource random = level.getRandom();

        // §tackle-break (§10): a flat 0.8% catastrophic failure — the line parts on the take and the whole
        // rig is lost, fish and all. Independent of the weight-vs-strain break in the fight (that's earned);
        // this is the rare gut-punch that keeps every strike a little tense.
        if (random.nextDouble() < TACKLE_BREAK_CHANCE) {
            ItemStack broken = sp.getItemInHand(session.hand);
            if (broken.getItem() instanceof RodItem) {
                RodData.set(broken, ComponentSlot.RIG, ItemStack.EMPTY);
            }
            addLineWear(broken, 5);
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.LINE_BREAK.get(),
                    SoundSource.PLAYERS, 0.9f, 1.0f);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.line_break")
                    .withStyle(ChatFormatting.RED));
            endSession(sp, session);
            return;
        }

        // Every strike stresses the blank (§rod-durability); at zero the rod snaps for good.
        ItemStack rodWear = sp.getItemInHand(session.hand);
        if (rodWear.getItem() instanceof RodItem && rodWear.isDamageableItem()) {
            rodWear.hurtAndBreak(1, sp,
                    session.hand == InteractionHand.MAIN_HAND
                            ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                            : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }

        // The fish ate the natural bait on the strike (§consumables) — lures are never consumed.
        ItemStack rodForBait = sp.getItemInHand(session.hand);
        // §skills FRUGAL: a frugal angler sometimes re-uses the bait (the fish nibbled without stripping it).
        if (RiverFishingConfig.consumeBait() && rodForBait.getItem() instanceof RodItem
                && random.nextDouble() >= AnglerSkills.baitSkipChance(sp)) {
            ItemStack rigForBait = RodData.get(rodForBait, ComponentSlot.RIG);
            if (rigForBait.getItem() instanceof RigItem && RigData.consumeBait(rigForBait)) {
                RodData.set(rodForBait, ComponentSlot.RIG, rigForBait);
            }
        }

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
        ItemStack rigSource = !session.rodStackRef.isEmpty() ? session.rodStackRef : sp.getItemInHand(session.hand);
        if (rigSource.getItem() instanceof RodItem) {
            ItemStack rigS = RodData.get(rigSource, ComponentSlot.RIG);
            if (rigS.getItem() instanceof RigItem) livebaitW = RigData.livebaitWeightG(rigS);
        }
        rollFish(random, profile, session, AnglerSkills.trophyChanceBonus(sp), livebaitW);

        ItemStack rod = sp.getItemInHand(session.hand);
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
        double drag = session.reelSize / 1000.0;                       // 0 for a reel-less float rod
        double requiredKg = Math.max(0.5, profile.fightStrength * (1.0 + weightKg) * 2.0);
        double effectiveStrain = session.lineStrainKg + 0.5 * drag;    // lineStrain already wear-reduced (§3.8)
        double baseTolerance = Mth.clamp(effectiveStrain / requiredKg, 0.2, 1.0);
        // Tolerance shrinks with break-sensitivity (§14) and rod overload (#5): thin/worn line + heavy
        // fish + small reel + overloaded blank => snaps with the slightest over-pull.
        // §skills STRONG_LINE: steadier hands let the line hold a little more tension before it snaps.
        session.breakTension = Mth.clamp(
                baseTolerance / RiverFishingConfig.breakSensitivity() * session.overloadPenalty
                        * AnglerSkills.lineToleranceMult(sp), 0.1, 1.0);
        session.requiredKg = requiredKg; // §tackle-stress: for the break-load message

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

        session.runTensionPulse = 0.18 * sens * (0.7 + 0.6 * weightStress);
        session.calmTensionPulse = 0.07 * sens;
        session.landPulse = 0.05 / (0.7 + 0.6 * weightStress) * (0.9 + session.reelSize / 14000.0);
        session.relaxTick = 0.010 + dragRelief * 0.02;                 // big reel gives line faster
        session.fightPattern = profile.fightPattern;
        session.fightAggression = profile.fightAggression;
        session.fightTimeout = (long) Mth.clamp(
                700 + weightKg * 80
                        + ("burst".equals(profile.fightPattern) ? 300
                        : "relentless".equals(profile.fightPattern) ? 500 : 0), 700, 2400);

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
        session.runTicksLeft = 0;
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
        session.bossBar = new ServerBossEvent(java.util.UUID.randomUUID(), 
                Component.translatable("message.riverfishing.fighting"),
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
        session.runTicksLeft = 22;      // the first second FEELS alive — fish or boot?
        session.nextRunAt = now + 100000;
        session.fightStartTick = now;
        session.fightTimeout = 600;
        session.fightPattern = "steady";
        session.bossBar = new ServerBossEvent(java.util.UUID.randomUUID(), 
                Component.translatable("message.riverfishing.fighting"),
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
        ItemStack rod = sp.getItemInHand(session.hand);
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
        Component lootName = loot.getHoverName();
        if (!sp.getInventory().add(loot)) {
            sp.drop(loot, false);
        }
        // §challenges: the classic — you fished up an old boot.
        if (!treasure && loot.is(Items.LEATHER_BOOTS)) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "old_boot");
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
        boolean inRun = session.runTicksLeft > 0;
        // Reeling in a run spikes tension and barely gains line — you should ease off during runs.
        session.tension += inRun ? session.runTensionPulse : session.calmTensionPulse;
        session.landProgress = Mth.clamp(
                session.landProgress + session.landPulse * (inRun ? 0.2 : 1.0), 0.0, 1.0);
        session.tension = Math.max(0.0, session.tension);

        level.playSound(null, sp.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.25f, 1.6f);

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
            actionbar(sp, Component.translatable("message.riverfishing.shake_off").withStyle(ChatFormatting.YELLOW));
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
            }
            return;
        }
        session.tension = Math.max(0.0, session.tension - session.relaxTick);
        session.landProgress = Math.max(0.0, session.landProgress - 0.0008);

        double progress = session.landProgress;
        if (session.runTicksLeft > 0) {
            session.runTicksLeft--;
            if (session.runTicksLeft == 0) {
                session.nextRunAt = now + runInterval(session, progress, random);
            }
        } else if (now >= session.nextRunAt) {
            if (session.runsLeft > 0 && random.nextDouble() < runChance(session, progress)) {
                session.runTicksLeft = runDuration(session, progress, random);
                session.runsLeft--;
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
            session.tension += session.runTensionPulse * 1.25;
            session.landProgress = Math.max(0.0, session.landProgress - 0.03);
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.7f, 1.5f);
            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                    session.target.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.25);
        }

        // The classic last dash at the bank: one guaranteed surge just before landing — ease off or snap.
        if (!session.finalSurgeDone && session.landProgress >= 0.85) {
            session.finalSurgeDone = true;
            session.runTicksLeft = Math.max(session.runTicksLeft, (session.trophy ? 38 : 28) + random.nextInt(14));
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 0.7f);
            // §sound: the long drag scream tears off for the final dash — at the player (the reel).
            level.playSound(null, sp.blockPosition(), com.riverfishing.registry.ModSounds.DRAG_LONG.get(),
                    SoundSource.PLAYERS, 0.9f, 1.0f);
            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                    session.target.getZ() + 0.5, 20, 0.3, 0.15, 0.3, 0.3);
            actionbar(sp, Component.translatable("message.riverfishing.final_surge").withStyle(ChatFormatting.RED));
        }

        // §tackle-stress (0.4.0): the probabilistic break — rolled once per tick, after every tension
        // mutation of this tick (decay, head-shakes, the player's reel pulses in between).
        if (overstressTick(sp, level, session, random)) {
            return;
        }

        if (now - session.fightStartTick > session.fightTimeout) {
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.missed").withStyle(ChatFormatting.GRAY));
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
        session.bossBar.setColor(session.tension >= session.breakTension ? BossEvent.BossBarColor.RED
                : inRun ? BossEvent.BossBarColor.RED
                : session.tension > session.breakTension * 0.66 ? BossEvent.BossBarColor.YELLOW
                : BossEvent.BossBarColor.GREEN);

        // Keep every client's view of the line in step with the fight so it visibly reels in (§immersion).
        if (now % 5 == 0) {
            ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target,
                    (float) Mth.clamp(session.landProgress, 0.0, 1.0), session.lineColor,
                    session.rodClass == RodClass.FLOAT));
        }
    }

    // ---- per-fish fight patterns (#3) ----

    private static int fightRunCount(FishProfile profile, double weightKg) {
        int runs = Math.max(1, profile.fightRuns);
        switch (profile.fightPattern) {
            case "aggressive" -> runs += 2;
            case "relentless" -> runs += 3; // §grass-carp: the amur just keeps charging
            case "burst" -> runs = Math.max(2, runs);
            default -> { /* steady / active_then_passive use the profile value */ }
        }
        if (weightKg > 2.0) runs += 1; // a big specimen has an extra run in it
        return runs;
    }

    /** Probability a run starts when the timer is up, by pattern and how far into the fight we are. */
    private static double runChance(FishingSession s, double progress) {
        return switch (s.fightPattern) {
            case "relentless" -> 0.97; // §grass-carp: fights just as hard at the net as at the strike
            case "aggressive" -> 0.95;
            case "burst" -> 0.70;
            case "active_then_passive" -> progress < 0.5 ? 0.90 : 0.25; // bream: fights early, tires late
            default -> 0.60;
        };
    }

    private static int runDuration(FishingSession s, double progress, RandomSource r) {
        return switch (s.fightPattern) {
            case "relentless" -> 40 + r.nextInt(35); // §grass-carp: long torpedo runs toward open water
            case "aggressive" -> 22 + r.nextInt(18);
            case "burst" -> 50 + r.nextInt(40);
            case "active_then_passive" -> progress < 0.5 ? 30 + r.nextInt(20) : 14 + r.nextInt(10);
            default -> 25 + r.nextInt(20);
        };
    }

    private static int runInterval(FishingSession s, double progress, RandomSource r) {
        return switch (s.fightPattern) {
            case "relentless" -> 20 + r.nextInt(25); // §grass-carp: barely a breath between charges
            case "aggressive" -> 25 + r.nextInt(30);
            case "burst" -> 80 + r.nextInt(80);
            case "active_then_passive" -> progress < 0.5 ? 30 + r.nextInt(30) : 90 + r.nextInt(60);
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
        giveFish(sp, session.species, session.weightG, session.lengthCm, legal, session.trophy);
        // §population: a landed fish leaves the water for real — depletion lands on THIS species only.
        FishingPressureData.get(level).addCatch(ChunkPos.pack(session.target),
                session.species.getPath(), level.getGameTime());
        if (legal) {
            boolean newSpecies = JournalData.isNewSpecies(sp, session.species);
            boolean personalBest = JournalData.isPersonalBest(sp, session.species, session.weightG);
            JournalData.record(sp, session.species, session.weightG); // records (§15)
            if (session.trophy) JournalData.addTrophy(sp);
            if (session.iceFishing) JournalData.addIceCatch(sp); // §winter-quests
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
            giveFish(sp, session.species, Math.max(1, w), Math.max(1, l), true, false);
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
                                 boolean legal, boolean trophy) {
        ItemStack fish = FishItem.create(ModItems.fishItem(species), species, weightG, lengthCm, legal, trophy);
        // §prime-fish: a legal top-of-range specimen gets the prime grade — the fisherman buys these.
        FishProfile profile = FishProfileManager.get().byId(species);
        if (legal && profile != null) {
            int threshold = FishItem.primeThresholdG(profile.weightMax);
            if (weightG >= threshold) {
                FishItem.gradePrime(fish, threshold);
            }
        }
        // §fish-scale: the icon now scales purely from LENGTH (FishItem.getIconScale), no NBT needed.
        if (!sp.getInventory().add(fish)) {
            sp.drop(fish, false);
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
            addLineWear(sp.getItemInHand(session.hand), 1);
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
        addLineWear(sp.getItemInHand(session.hand), (int) Math.round(5 * lineWearScaled()));
        double weightKg = session.weightG / 1000.0;
        double strain = Math.max(0.5, session.lineStrainKg);
        // §balance: a strong line vs a light fish nearly always just throws the hook (5% floor), while a
        // weak line vs a heavy fish loses the whole rig at the 30% hard cap. Leader bite-offs always lose.
        double loseChance = Mth.clamp(0.30 * (weightKg * 1.5 / strain), 0.05, 0.30);
        boolean loseRig = leader || level.getRandom().nextDouble() < loseChance;
        if (loseRig) {
            ItemStack rod = sp.getItemInHand(session.hand);
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
                        leader ? "message.riverfishing.leader_bite_off" : "message.riverfishing.line_break")
                        .withStyle(ChatFormatting.RED));
            }
        } else {
            level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6f, 0.7f);
            sp.sendSystemMessage(Component.translatable("message.riverfishing.shake_off")
                    .withStyle(ChatFormatting.YELLOW));
        }
        endSession(sp, session);
    }

    private static void endSession(ServerPlayer sp, FishingSession session) {
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
        // Clear the line for everyone who can see this angler (§line-multiplayer).
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), false, null, 0f, 0, false));
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
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.mistimed").withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- fish generation ----

    private static void rollFish(RandomSource random, FishProfile p, FishingSession session, double trophyBonus,
                                 int livebaitWeightG) {
        double biased = Math.pow(random.nextDouble(), 2.4); // big fish are rare (§2.1)

        // Trophy roll (configurable): a specimen from the top of the species' size range. It fights
        // accordingly (weight drives the fight), shimmers as an item and gives triple XP.
        // §skills ANGLERS_LUCK adds a flat bonus (+1%/rank) to the trophy chance.
        if (random.nextDouble() < RiverFishingConfig.trophyChance() + trophyBonus) {
            session.trophy = true;
            biased = 0.85 + 0.15 * random.nextDouble();
        }

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

        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;
        session.weightG = (int) Math.round(weight);

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
        return (int) Math.round(2 * RiverFishingConfig.hookWearRate() / 1.5);
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
            ctx.castWeightG = rg.rigType().massGrams();
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
        ctx.season = SeasonProvider.getSeason(level);
        ctx.time = TimeOfDay.fromDayTime(level.getOverworldClockTime());
        ctx.weather = level.isThundering() ? Weather.THUNDER : (level.isRaining() ? Weather.RAIN : Weather.CLEAR);
        ctx.pressureFactor = com.riverfishing.engine.BarometricPressure.biteFactor(level);
        ctx.biomeTemperature = level.getBiome(waterPos).value().getBaseTemperature();
        ctx.waterDepth = measureDepth(level, waterPos);
        ctx.biomeGroups = biomeGroups(level, waterPos, body);

        // Groundbait: the rig's feeder/flat/grusha cage delivers feed at the spot; the hand-fed zone
        // (right-clicking water) can be fresher. Use whichever is stronger.
        FeedZoneData.Query feed = FeedZoneData.get(level).query(waterPos, now);
        double zoneFresh = feed.inZone() ? feed.freshness() : 0.0;
        String cageCategory = rigStack.getItem() instanceof RigItem ? RigData.groundbaitCategory(rigStack) : null;
        double cageFresh = cageCategory != null ? 0.5 : 0.0;
        if (zoneFresh >= cageFresh) {
            ctx.inFeedZone = feed.inZone();
            ctx.feedFreshness = zoneFresh;
            ctx.feedCategory = feed.category();
        } else {
            ctx.inFeedZone = true;
            ctx.feedFreshness = cageFresh;
            ctx.feedCategory = cageCategory;
        }

        return ctx;
    }

    /**
     * Water analysis for the fish finder / admin probe (§QoL). Environment-only (no tackle): lists
     * which species CAN bite here right now. The admin variant adds the full habitat summary,
     * per-species environment scores, level gates and the species' favourite bait.
     */
    public static void analyzeWater(ServerPlayer sp, ServerLevel level, BlockPos waterPos, boolean admin) {
        WaterBody body = WaterBodyCache.forLevel(level).get(level, waterPos);
        if (body.type() == WaterType.NONE) {
            actionbar(sp, Component.translatable("message.riverfishing.no_water").withStyle(ChatFormatting.RED));
            return;
        }
        BiteContext env = new BiteContext();
        env.water = body.type();
        env.waterWidth = body.width();
        env.waterDepth = measureDepth(level, waterPos);
        env.biomeGroups = biomeGroups(level, waterPos, body);
        env.season = SeasonProvider.getSeason(level);
        env.time = TimeOfDay.fromDayTime(level.getOverworldClockTime());
        env.weather = level.isThundering() ? Weather.THUNDER : (level.isRaining() ? Weather.RAIN : Weather.CLEAR);
        env.biomeTemperature = level.getBiome(waterPos).value().getBaseTemperature();
        env.anglerLevel = Integer.MAX_VALUE; // environment view ignores the holder's level

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
            for (var e : here) {
                FishProfile p = e.getKey();
                String bait = topBait(p);
                sp.sendSystemMessage(Component.literal(String.format("E=%.2f  ", e.getValue()))
                        .withStyle(ChatFormatting.AQUA)
                        .append(fishName(p.id))
                        .append(Component.literal(String.format("  lvl>=%d  bait: %s", p.minAnglerLevel, bait))
                                .withStyle(ChatFormatting.DARK_GRAY)));
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
        sp.sendSystemMessage(pressureLine(level));
        level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 1.5f);
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
            default -> ChatFormatting.RED;
        };
        return Component.translatable("finder.riverfishing.pressure", hpa, arrow)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" "))
                .append(Component.translatable("finder.riverfishing.outlook." + outlook).withStyle(colour));
    }

    /** Which habitat gate blocks this species here — mirrors environmentScore's order (§QoL). */
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
    private static int measureDepth(ServerLevel level, BlockPos surface) {
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
        float temp = biome.value().getBaseTemperature();
        groups.add(temp < 0.3f ? "cold" : (temp > 0.95f ? "warm" : "temperate"));
        if (biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)) groups.add("river_biome");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) groups.add("ocean_biome");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)) groups.add("beach");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE)) groups.add("jungle");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_FOREST)) groups.add("forest");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_TAIGA)) groups.add("taiga");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN) || biome.is(net.minecraft.tags.BiomeTags.IS_HILL)) groups.add("mountain");
        if (biome.is(net.minecraft.tags.BiomeTags.IS_SAVANNA) || biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS)) groups.add("dry");
        if (body.swamp() || biome.is(com.riverfishing.water.ModBiomeTags.IS_SWAMP)) groups.add("swamp");
        // §koi: cherry groves are koi water. Match by name so vanilla cherry_grove AND BoP
        // cherry_blossom_grove both count without needing a dedicated tag.
        biome.unwrapKey().ifPresent(k -> {
            if (k.identifier().getPath().contains("cherry")) groups.add("cherry");
        });
        return groups;
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
