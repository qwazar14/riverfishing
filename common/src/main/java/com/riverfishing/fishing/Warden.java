package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;


/**
 * §i §breeding (0.9.0): the poaching record. There is no warden villager any more — he never took
 * the job reliably, and a villager is not what makes poaching cost something here. The fishermen
 * are: every net haul from water you did not stock is counted, and at {@link #BAN_AT} the contract
 * board is closed to you until the fish are put back, one mature release per offence.
 */
public final class Warden {
    /** The record that empties the board. Three, so one net in a strange pond is a lesson, not a life. */
    public static final int BAN_AT = 3;
    /** How far a warden sees a net go in. */
    /** The fine, capped by what the poacher actually carries — a warden takes emeralds, not debts. */
    private static final String KEY = "poach_count";

    private Warden() {}

    public static int poachCount(Player p) {
        return PlayerData.root(p).getIntOr(KEY, 0);
    }

    /** The board is empty for this player (ModVillagers.sendBoard; Contracts.take refuses too). */
    public static boolean banned(Player p) {
        return poachCount(p) >= BAN_AT;
    }

    /**
     * Called from NetItem's poaching block, once per haul. The record grows either way; a warden in
     * reach turns it into a confiscation and a fine on the spot.
     *
     * @param fish how many fish came up poached in this haul — the message names the count
     */
    public static void onPoach(ServerPlayer sp, ServerLevel level, BlockPos where, int fish) {
        CompoundTag root = PlayerData.root(sp);
        root.putInt(KEY, root.getIntOr(KEY, 0) + 1);
        PlayerData.markDirty(sp);
    }

    /** One mature fish released into water pays one point off the record (FishingManager.releaseFish). */
    public static void workOff(ServerPlayer sp) {
        CompoundTag root = PlayerData.root(sp);
        int n = root.getIntOr(KEY, 0);
        if (n <= 0) return;
        root.putInt(KEY, n - 1);
        PlayerData.markDirty(sp);
    }


    /** Emeralds out of the inventory, up to {@code max}; returns how many were actually there to take. */

    // ---- trades ---------------------------------------------------------------------------------------

    /**
     * Level 1: buys either net for an emerald. Level 2: sells fry. Registered through the same platform
     * seam as the fisherman's table (ModVillagers.registerTrades, "§i").
     *
     * <p>The fry species is rolled ONCE, when the offer is minted at level-up, from every species some
     * fisherman buys — stream E's frySlot re-rolls the fisherman's bucket daily because it tracks the
     * order of the day, and a warden has no order to track: his programme is one species, and the
     * bucket says which. ponytail: "settled-or-native of his region" would need the villager's region
     * looked up at mint time; the buyable pool is the fisherman's own "any species you could sell" list.
     */
}

// §ported26
