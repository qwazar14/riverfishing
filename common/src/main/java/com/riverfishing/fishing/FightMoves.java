package com.riverfishing.fishing;

import com.riverfishing.network.FightInputPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * §fight-moves (1.1.0): what makes a fish of each fight pattern fight like itself — one signature move a
 * pattern, twice a fight at most — and where on the water the fish is.
 *
 * <p>Every move is answered with something the fight already asks for: the rod held across (the camera or the
 * arrows), the drag opened (crouch), the reel. Nothing new to learn, only something to recognise:
 * <ul>
 *   <li><b>burst — the zig-zag.</b> One run that changes side two or three times; each leg is answered like a run.</li>
 *   <li><b>steady — into the weeds.</b> A heavy run for cover. Held across, the fish is turned; let go, it is in the
 *       weeds and nothing comes on the reel until the drag is opened and it swims out by itself.</li>
 *   <li><b>relentless — the torpedo.</b> A long straight run away. A closed drag loads toward the break; an open one
 *       lets it take line, and the fish is spent for it.</li>
 *   <li><b>sounding — the sulk.</b> After a dive it lies on the bottom; the reel does nothing. Lift, and it comes up.</li>
 *   <li><b>aggressive — the charge.</b> It turns and comes straight at the angler. Reel, or the line goes slack.</li>
 *   <li><b>greyhounding — the tail-walk.</b> Three jumps one after another, each answered like any jump.</li>
 *   <li><b>active_then_passive — the plank.</b> Beaten, it lies on its side and is towed in.</li>
 * </ul>
 *
 * <p>The position: the fish is at the line's length from the angler (the fight's progress, as the line has always
 * been drawn), on a bearing {@link FishingSession#swing} that its side runs sweep — a fish on a tight line cannot go
 * sideways without swinging round the rod. Held here, on the server, so the splash, the weed and the bubbles are
 * where the fish is, and every client draws the same fish.
 */
public final class FightMoves {
    private FightMoves() {}

    public static final byte NONE = 0, ZIGZAG = 1, COVER = 2, WEEDED = 3, TORPEDO = 4, SULK = 5, CHARGE = 6,
            CHARGE_SLACK = 7, TAILWALK = 8, PLANK = 9;
    /** How far round the rod a side run can swing the fish, radians. */
    public static final double MAX_SWING = 1.1;

    // ---- where the fish is ----

    /** The fish on the water: the line's end walked in by the progress, swung round the rod by its bearing. */
    public static Vec3 fishPos(ServerPlayer sp, FishingSession s) {
        Vec3 water = new Vec3(s.target.getX() + 0.5, s.target.getY() + 0.95, s.target.getZ() + 0.5);
        Vec3 bank = bank(sp.position(), sp.getViewVector(1f));
        return swung(bank, water.lerp(bank, Mth.clamp(s.landProgress * 0.85, 0.0, 0.9)), s.swing);
    }

    /** Where the rod holds the line — the client's LineRenderer reads the same point. */
    public static Vec3 bank(Vec3 feet, Vec3 view) {
        return feet.add(view.scale(1.2)).add(0, 0.1, 0);
    }

    /** {@code end} turned {@code a} radians round {@code pivot}, level: + toward the angler's right. */
    public static Vec3 swung(Vec3 pivot, Vec3 end, double a) {
        if (a == 0) return end;
        double dx = end.x - pivot.x, dz = end.z - pivot.z, c = Math.cos(a), sn = Math.sin(a);
        return new Vec3(pivot.x + dx * c - dz * sn, end.y, pivot.z + dx * sn + dz * c);
    }

    /** One tick of the fish's bearing: a side run swings it round at a fish's pace; the bank stops it. */
    private static void swim(ServerPlayer sp, ServerLevel level, FishingSession s) {
        boolean run = s.runTicksLeft > 0;
        double target;
        if (run && s.course == FightCourse.LEFT) target = -MAX_SWING;
        else if (run && s.course == FightCourse.RIGHT) target = MAX_SWING;
        else if (s.move == CHARGE || s.move == CHARGE_SLACK) target = 0;   // straight at the rod
        else target = s.swing * 0.995;   // between runs the reel draws it back in front, slowly
        Vec3 water = new Vec3(s.target.getX() + 0.5, s.target.getY() + 0.95, s.target.getZ() + 0.5);
        Vec3 bank = bank(sp.position(), sp.getViewVector(1f));
        Vec3 end = water.lerp(bank, Mth.clamp(s.landProgress * 0.85, 0.0, 0.9));
        double r = Math.max(2.0, Math.hypot(end.x - bank.x, end.z - bank.z));
        double pace = (run || s.move == CHARGE || s.move == CHARGE_SLACK ? 2.2 + s.lengthCm / 60.0 : 0.6) * (1.0 - 0.4 * s.fatigue);
        double step = pace / 20.0 / r;
        double next = s.swing + Mth.clamp(target - s.swing, -step, step);
        Vec3 at = swung(bank, end, next);
        if (!level.getFluidState(BlockPos.containing(at.x, s.target.getY(), at.z)).isEmpty()) s.swing = next;
    }

    // ---- what the reel does ----

    /** A move that holds the fish where it is: no new run starts, and the reel brings nothing in. */
    public static boolean holding(FishingSession s) {
        return s.move == WEEDED || s.move == SULK;
    }

    /** A new run, a head-shake, a leap or the dash at the bank may start: not while the fish is held, or coming at you. */
    public static boolean allowsRun(FishingSession s) {
        return !holding(s) && s.move != CHARGE && s.move != CHARGE_SLACK;
    }

    /** The share of a crank's line that comes in: none from a fish in the weeds or on the bottom, more from one coming at you. */
    public static double reelGain(FishingSession s) {
        return holding(s) ? 0.0 : s.move == CHARGE || s.move == CHARGE_SLACK ? 1.8 : 1.0;
    }

    /** Winding against a weeded or sulking fish rubs — the tension of a crank, and half again. */
    public static double reelTension(FishingSession s) {
        return holding(s) ? 1.5 : 1.0;
    }

    public static void onReel(FishingSession s, long now) {
        if (s.move == CHARGE || s.move == CHARGE_SLACK) {
            s.moveScore = now;   // the last crank
            s.moveStep++;
            s.move = CHARGE;
        }
    }

    // ---- the moves ----

    /** Room for a move: a real fish, a fair fight (an outclassed one is hard enough), two a fight at most. */
    private static boolean mayMove(FishingSession s, RandomSource r) {
        if (s.bycatch != 0 || s.outclassed || s.move != NONE || s.moves >= 2) return false;
        return r.nextDouble() < (s.moves == 0 ? 0.45 : 0.2);
    }

    private static void begin(ServerPlayer sp, FishingSession s, byte move, String say) {
        s.move = move;
        s.moves++;
        s.moveScore = 0;
        s.moveStep = 0;
        if (say != null) FishingManager.actionbar(sp, Component.translatable(say).withStyle(ChatFormatting.GOLD));
    }

    /** A scripted run has just been given its course. */
    public static void onRunStart(ServerPlayer sp, ServerLevel level, FishingSession s, long now, RandomSource r) {
        if (s.iceFishing) return;   // an ice hole fights as it always has
        if (s.move == PLANK) s.move = NONE;   // not beaten after all: upright for the run
        String p = s.fightPattern == null ? "" : s.fightPattern;
        switch (p) {
            case "burst" -> {
                if (!s.course.isRun() || s.course == FightCourse.DOWN || !mayMove(s, r)) return;
                if (s.course != FightCourse.LEFT && s.course != FightCourse.RIGHT) s.course = r.nextBoolean() ? FightCourse.LEFT : FightCourse.RIGHT;
                begin(sp, s, ZIGZAG, null);
                s.moveNext = now + 22 + r.nextInt(8);
            }
            case "steady" -> {
                if ((s.course != FightCourse.LEFT && s.course != FightCourse.RIGHT) || !mayMove(s, r)) return;
                if (!findCover(level, s, fishPos(sp, s))) return;   // §cover-check: open water, nothing to run into
                begin(sp, s, COVER, null);
            }
            case "relentless" -> {
                if (!mayMove(s, r)) return;
                begin(sp, s, TORPEDO, "message.riverfishing.fight_torpedo");
                s.course = FightCourse.NONE;   // straight away: no side to hold, only the drag
                s.runTicksLeft = 50 + r.nextInt(21);
                s.runTicksTotal = s.runTicksLeft;
                FishingManager.reelSound(sp, level, com.riverfishing.registry.ModSounds.DRAG_LONG.get(), 1.0f, 1.1f);
            }
            case "greyhounding" -> {
                if (s.course != FightCourse.UP || !mayMove(s, r)) return;
                begin(sp, s, TAILWALK, "message.riverfishing.fish_jumps");
                s.moveStep = 3;
                s.moveNext = now + 8;
            }
            default -> { }
        }
    }

    /** A scripted run has just ended; its course is already cleared. */
    public static void onRunEnd(ServerPlayer sp, ServerLevel level, FishingSession s, long now, RandomSource r, FightCourse was) {
        if (s.iceFishing) return;
        String p = s.fightPattern == null ? "" : s.fightPattern;
        if (s.move == ZIGZAG || s.move == TORPEDO) {
            s.move = NONE;
        } else if (s.move == COVER) {
            Vec3 at = fishPos(sp, s);
            if (s.moveScore / Math.max(1, s.moveStep) >= 0.55) {   // held across: turned
                s.move = NONE;
                s.fatigue = Math.min(1.0, s.fatigue + 0.08);
                s.landProgress = Math.min(1.0, s.landProgress + 0.03);
                level.playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.3f);
                FishingManager.actionbar(sp, Component.translatable("message.riverfishing.fight_turned").withStyle(ChatFormatting.GREEN));
            } else {
                s.move = WEEDED;
                s.moveEnd = now + 120;
                s.moveScore = 0;
                weed(level, s, at, 24);
                level.playSound(null, BlockPos.containing(at), "snags".equals(s.coverKind) ? SoundEvents.WOOD_BREAK : SoundEvents.GRASS_BREAK,
                        SoundSource.PLAYERS, 0.8f, 0.8f);
                FishingManager.actionbar(sp, Component.translatable(coverKey("fight_weeded", s)).withStyle(ChatFormatting.GOLD));
            }
        } else if ("sounding".equals(p) && was == FightCourse.DOWN && mayMove(s, r)) {
            begin(sp, s, SULK, "message.riverfishing.fight_sulk");
            s.course = FightCourse.DOWN;   // the rod stays pulled down; LIFT is the answer, as to any dive
            s.moveEnd = now + 160;
        } else if ("aggressive".equals(p) && was.isRun() && mayMove(s, r)) {
            begin(sp, s, CHARGE, "message.riverfishing.fight_charge");
            s.moveEnd = now + 30 + r.nextInt(12);
            s.moveScore = now;
        }
    }

    /** One tick of whatever move is under way, and the fish's bearing. Returns true if the fish is gone. */
    public static boolean tick(ServerPlayer sp, ServerLevel level, FishingSession s, long now, RandomSource r) {
        if (s.iceFishing) return false;   // under the ice it stays under the hole, and fights as it always has
        swim(sp, level, s);
        switch (s.move) {
            case ZIGZAG -> {
                if (s.runTicksLeft > 0 && now >= s.moveNext) {   // it turns: the next leg, the other way
                    s.course = s.course == FightCourse.LEFT ? FightCourse.RIGHT : FightCourse.LEFT;
                    s.barState = -1;
                    s.moveNext = now + 22 + r.nextInt(8);
                    Vec3 at = fishPos(sp, s);
                    level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, 10, 0.3, 0.1, 0.3, 0.2);
                    level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.6f, 1.4f);
                }
            }
            case COVER -> {
                if (s.runTicksLeft > 0) { s.moveScore += s.courseAlign; s.moveStep++; }
            }
            case WEEDED -> {
                Vec3 at = fishPos(sp, s);
                if (now % 8 == 0) weed(level, s, at, 3);
                if (sp.isCrouching()) s.moveScore++;   // given line, it swims out by itself
                boolean out = s.moveScore >= 30;
                if (out || now >= s.moveEnd) {
                    if (!out) s.landProgress = Math.max(0.0, s.landProgress - 0.05);   // it tore out on its own, with line
                    s.move = NONE;
                    s.nextRunAt = now + 40;
                    weed(level, s, at, 18);
                    FishingManager.actionbar(sp, Component.translatable(coverKey("fight_unweeded", s)).withStyle(ChatFormatting.GREEN));
                }
            }
            case TORPEDO -> {
                if (s.runTicksLeft > 0) {
                    if (sp.isCrouching()) {   // the drag open: it takes line, and spends itself doing it
                        s.landProgress = Math.max(0.0, s.landProgress - 0.0015);
                        s.fatigue = Math.min(1.0, s.fatigue + s.fatigueRunTick * 1.5);
                    } else {                  // the drag shut: the whole run goes into the tackle
                        s.tension += s.runTensionPulse * 0.18 * (1.0 - 0.55 * s.fatigue);
                    }
                    if (now % 3 == 0) {
                        Vec3 at = fishPos(sp, s);
                        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.05, at.z, 4, 0.25, 0.02, 0.25, 0.1);
                    }
                    if (now % 20 == 0) FishingManager.reelSound(sp, level, com.riverfishing.registry.ModSounds.DRAG_LONG.get(), 0.8f, 1.2f);
                }
            }
            case SULK -> {
                Vec3 at = fishPos(sp, s);
                if (now % 6 == 0) level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y - 1.5, at.z, 3, 0.2, 0.3, 0.2, 0.05);
                if (s.pullDir == FightInputPacket.LIFT) s.moveScore++;
                boolean up = s.moveScore >= 35;
                if (up || now >= s.moveEnd) {
                    if (up) {
                        s.landProgress = Math.min(1.0, s.landProgress + 0.05);
                        s.fatigue = Math.min(1.0, s.fatigue + 0.08);
                        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y - 1.0, at.z, 30, 0.4, 0.5, 0.4, 0.1);
                        level.playSound(null, BlockPos.containing(at), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.0f, 0.8f);
                        FishingManager.actionbar(sp, Component.translatable("message.riverfishing.fight_unsulk").withStyle(ChatFormatting.GREEN));
                    }
                    s.move = NONE;
                    s.course = FightCourse.NONE;
                    s.barState = -1;
                    s.nextRunAt = now + 60;
                }
            }
            case CHARGE, CHARGE_SLACK -> {
                s.landProgress = Math.min(0.97, s.landProgress + 0.0035);   // it swims to you by itself
                s.move = now - (long) s.moveScore > 12 ? CHARGE_SLACK : CHARGE;
                if (now >= s.moveEnd) {
                    s.move = NONE;
                    if (s.moveStep == 0 && r.nextDouble() < 0.3) {   // not one turn of the reel: the hook fell out
                        FishingManager.actionbar(sp, Component.translatable("message.riverfishing.charge_lost").withStyle(ChatFormatting.YELLOW));
                        FishingManager.endSession(sp, s);
                        return true;
                    }
                }
            }
            case TAILWALK -> {
                if (s.moveStep > 0 && now >= s.moveNext && now >= s.jumpWindowEnd) {
                    s.jumpWindowEnd = now + 15;   // the jump itself: FishingManager's window, reel and all
                    s.moveStep--;
                    s.moveNext = now + 24 + r.nextInt(10);
                    Vec3 at = fishPos(sp, s);
                    level.playSound(null, BlockPos.containing(at), SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 1.0f, 0.8f);
                    level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2, at.z, 40, 0.5, 0.5, 0.5, 0.4);
                } else if (s.moveStep == 0 && now >= s.jumpWindowEnd) {
                    s.move = NONE;
                }
            }
            case NONE -> {
                if ("active_then_passive".equals(s.fightPattern) && s.runTicksLeft == 0 && s.bycatch == 0
                        && s.landProgress >= 0.5 && s.fatigue >= 0.35) {
                    s.move = PLANK;   // beaten: on its side, towed in — no count against the two moves, it is the end of one
                }
            }
            default -> { }
        }
        return false;
    }

    private static void weed(ServerLevel level, FishingSession s, Vec3 at, int n) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, s.coverItem),
                at.x, at.y + 0.1, at.z, n, 0.35, 0.15, 0.35, 0.06);
    }

    // ---- §cover-check: a run for cover needs cover ----

    /** Weed, kelp, lilies, reeds and cattails, snags and mangrove roots — data, so a pack can add its own plants. */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> FISH_COVER =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, com.riverfishing.RiverFishing.id("fish_cover"));
    /** How far round the fish it looks, and how far under the surface (a reed stands one above it). */
    private static final int COVER_R = 5, COVER_DOWN = 6;

    /**
     * The nearest cover to the fish, into {@code s.coverKind} / {@code s.coverItem}; false in open water — the sea
     * off a beach, the middle of a bare pit — where "into the weeds" had nothing to go into.
     */
    static boolean findCover(ServerLevel level, FishingSession s, Vec3 fish) {
        BlockPos c = BlockPos.containing(fish);
        BlockPos best = null;
        net.minecraft.world.level.block.state.BlockState bestState = null;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-COVER_R, -COVER_DOWN, -COVER_R), c.offset(COVER_R, 1, COVER_R))) {
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
            if (st.is(FISH_COVER) && (best == null || p.distSqr(c) < best.distSqr(c))) {
                best = p.immutable();
                bestState = st;
            }
        }
        if (best == null) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bestState.getBlock()).getPath();
        s.coverKind = id.contains("reed") || id.contains("cattail") || id.contains("cane") || id.contains("bamboo") ? "reeds"
                : id.contains("snag") || id.contains("root") || id.contains("log") ? "snags" : "weeds";
        net.minecraft.world.item.Item item = bestState.getBlock().asItem();
        s.coverItem = item == Items.AIR ? Items.SEAGRASS : item;   // kelp_plant and tall_seagrass have no item of their own
        return true;
    }

    /** The message for the cover it ran into: fight_weeded, fight_weeded_reeds, fight_weeded_snags… */
    private static String coverKey(String base, FishingSession s) {
        return "message.riverfishing." + base + ("weeds".equals(s.coverKind) ? "" : "_" + s.coverKind);
    }
}
