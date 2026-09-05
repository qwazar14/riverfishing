package com.riverfishing.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;


/**
 * §i §breeding (0.9.0) · §o (layer 6): the debt to the fishermen. There is no warden villager — he
 * never took the job reliably, and a villager is not what makes poaching cost something here. The
 * fishermen are: a net hauled out of water you did not stock costs {@link #POACH_REP} points of the
 * reputation their contracts run on, and the number GOES NEGATIVE. Below zero the trusted shelf is
 * shut (every step of {@code Contracts.TRUST_STEPS} is positive), and at {@link #BAN_REP} the board
 * itself is blank.
 *
 * <p>The way back is not time and not emeralds: it is {@link #GRAMS_PER_POINT} grams of mature fish
 * put back into WILD water, per point. Not into your own claimed pond — a fish released there is
 * still yours: you stocked it, it grows for you, you catch it again, and the village is no better
 * off than before you took the net to it. Restitution has to leave your hands and go into water
 * anybody may fish, which is exactly the water a net emptied. That is also why it is measured in
 * kilograms rather than in fish: five kilos is five kilos whether it comes as one carp or ten roach,
 * and a bucket of undersized fish cannot buy a pardon (only mature fish count, the same size class
 * the stocking ledger takes as brood).
 *
 * <p>{@code poach_count} is kept as a plain record — the guide page and any future warden read it —
 * but nothing gates on it any more.
 */
public final class Warden {
    /** Reputation at or below which the contract board is closed: three hauls' worth, and no way to drift into it. */
    public static final int BAN_REP = -15;
    /** What one haul from water that is not yours costs, however many fish came up in it. */
    public static final int POACH_REP = 5;
    /** Grams of mature fish released into wild water that buy one point back. */
    public static final int GRAMS_PER_POINT = 5000;

    private static final String KEY = "poach_count";
    /** The same key {@code Contracts.rep} reads — one number: the contracts pay it, poaching takes it. */
    private static final String REP = "contract_rep";
    /** The part of a kilogram already released and not yet worth a point. */
    private static final String GRAMS = "rep_grams";

    private Warden() {}

    public static int poachCount(Player p) {
        return PlayerData.root(p).getInt(KEY);
    }

    /** Grams banked towards the next point of reputation, 0..{@link #GRAMS_PER_POINT}-1. */
    public static int repGrams(Player p) {
        return PlayerData.root(p).getInt(GRAMS);
    }

    /** §o: the board is empty for this player (ModVillagers.sendBoard; Contracts.take refuses too). */
    public static boolean banned(Player p) {
        return Contracts.rep(p) <= BAN_REP;
    }

    /**
     * Called from NetItem's poaching block, once per haul: the record grows and the fishermen's
     * trust drops. There is no floor at zero — the debt IS the punishment, and the board shows it.
     *
     * @param fish how many fish came up poached in this haul (the record counts hauls, not fish)
     */
    public static void onPoach(ServerPlayer sp, ServerLevel level, BlockPos where, int fish) {
        CompoundTag root = PlayerData.root(sp);
        root.putInt(KEY, root.getInt(KEY) + 1);
        root.putInt(REP, root.getInt(REP) - POACH_REP);   // §o: no clamp — reputation goes negative
        PlayerData.markDirty(sp);
    }

    /**
     * §o: kilograms of mature fish put back into wild water, banked. Every {@link #GRAMS_PER_POINT}
     * grams is one point of reputation; the remainder waits in {@code rep_grams} for the next fish,
     * so releasing a pound at a time works exactly as well as releasing a sturgeon.
     *
     * <p>Called from {@code FishingManager.releaseFish}, which decides what counts: mature, and not
     * into a claimed pond (see the class note for why).
     */
    public static void credit(ServerPlayer sp, int grams) {
        if (grams <= 0) return;
        CompoundTag root = PlayerData.root(sp);
        int banked = root.getInt(GRAMS) + grams;
        int points = banked / GRAMS_PER_POINT;
        root.putInt(GRAMS, banked - points * GRAMS_PER_POINT);
        if (points > 0) {
            int rep = root.getInt(REP) + points;
            root.putInt(REP, rep);
            sp.displayClientMessage(Component.translatable("message.riverfishing.rep_credit", points, rep)
                    .withStyle(rep < 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
        }
        PlayerData.markDirty(sp);
    }

    // ---- what the board and the finder say ------------------------------------------------------------
    // Pure arithmetic on the two numbers the server sends, so the caption and the finder line cannot
    // disagree about what is owed.

    /** Grams still to release for the next point. */
    public static int toNextPoint(int repGrams) {
        return GRAMS_PER_POINT - repGrams;
    }

    /** Grams still to release to get back to zero — 0 when there is no debt. */
    public static int toClear(int rep, int repGrams) {
        return rep >= 0 ? 0 : -rep * GRAMS_PER_POINT - repGrams;
    }

    /** Grams as kilograms with one decimal: both screens say the debt in the same words. */
    public static String kg(int grams) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, grams) / 1000.0);
    }
}
