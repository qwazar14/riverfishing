package com.riverfishing.fishing;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.FryItem;
import com.riverfishing.item.NetItem;
import com.riverfishing.registry.ModVillagers;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * §i §breeding (0.9.0): the warden (рыбинспектор). A fishery with nets in it needs somebody whose job
 * is to say no, and in this mod that somebody is a villager: the {@code warden} profession, at his
 * {@code warden_post}. He does three things, all from here —
 *
 * <ul>
 *   <li>{@link #onPoach}: a net hauled from water the player did not stock, within {@link #REACH}
 *       blocks of a warden, is confiscated and fined. Out of his sight the haul only goes on the
 *       player's record ({@code poach_count} in {@link PlayerData}); the fishermen's trust drop from
 *       the nets guide stays as it was.</li>
 *   <li>{@link #banned}: three on the record and the contract board is empty for that player — a
 *       fisherman does not post work for a poacher — until the record is worked off, one mature fish
 *       released into water per point ({@link #workOff}, called where the brood ledger is written).</li>
 *   <li>{@link #trades}: two — he buys nets back for an emerald (the honest way out), and sells fry
 *       for restocking.</li>
 * </ul>
 */
public final class Warden {
    /** The record that empties the board. Three, so one net in a strange pond is a lesson, not a life. */
    public static final int BAN_AT = 3;
    /** How far a warden sees a net go in. */
    public static final int REACH = 48;
    /** The fine, capped by what the poacher actually carries — a warden takes emeralds, not debts. */
    public static final int FINE = 10;
    private static final String KEY = "poach_count";
    private static final int FRY_EMERALDS = 6, FRY_PER_BUCKET = 10;

    private Warden() {}

    public static int poachCount(Player p) {
        return PlayerData.root(p).getInt(KEY);
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
        root.putInt(KEY, root.getInt(KEY) + 1);
        PlayerData.markDirty(sp);
        Villager warden = nearest(level, where);
        if (warden == null) return;
        // The net is whichever hand holds one — NetItem.use runs from either. Shrunk to nothing rather
        // than replaced, so the caller's hurtAndBreak on the same stack is a no-op on an empty stack.
        ItemStack net = sp.getMainHandItem().getItem() instanceof NetItem ? sp.getMainHandItem() : sp.getOffhandItem();
        if (net.getItem() instanceof NetItem) net.setCount(0);
        int fine = takeEmeralds(sp.getInventory(), FINE);
        sp.displayClientMessage(Component.translatable("message.riverfishing.warden_caught", fish, fine)
                .withStyle(ChatFormatting.RED), false);
        level.playSound(null, warden.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    /** One mature fish released into water pays one point off the record (FishingManager.releaseFish). */
    public static void workOff(ServerPlayer sp) {
        CompoundTag root = PlayerData.root(sp);
        int n = root.getInt(KEY);
        if (n <= 0) return;
        root.putInt(KEY, n - 1);
        PlayerData.markDirty(sp);
    }

    @Nullable
    private static Villager nearest(ServerLevel level, BlockPos where) {
        List<Villager> seen = level.getEntitiesOfClass(Villager.class, new AABB(where).inflate(REACH),
                v -> v.getVillagerData().getProfession() == ModVillagers.WARDEN.get());
        return seen.isEmpty() ? null : seen.get(0);
    }

    /** Emeralds out of the inventory, up to {@code max}; returns how many were actually there to take. */
    private static int takeEmeralds(Inventory inv, int max) {
        int left = max;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(Items.EMERALD)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        return max - left;
    }

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
    public static Int2ObjectMap<List<VillagerTrades.ItemListing>> trades() {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> t = new Int2ObjectOpenHashMap<>();
        t.put(1, List.of(buys("seine_net"), buys("cast_net")));
        t.put(2, List.of(Warden::fry));
        return t;
    }

    /** Buys one net — worn or new, the cost matches the item alone — for one emerald. */
    private static VillagerTrades.ItemListing buys(String path) {
        return (trader, random) -> {
            Item i = BuiltInRegistries.ITEM.get(RiverFishing.id(path));
            if (i == Items.AIR) return null;
            return new MerchantOffer(new ItemCost(i), new ItemStack(Items.EMERALD, 1), 12, 2, 0.05f);
        };
    }

    @Nullable
    private static MerchantOffer fry(Entity trader, RandomSource random) {
        List<String> pool = ModVillagers.buyableSpecies();
        if (pool.isEmpty()) return null;
        String species = pool.get(random.nextInt(pool.size()));
        ItemStack fry = FryItem.of(RiverFishing.id(species), ModVillagers.randomGenome(random), FRY_PER_BUCKET);
        return new MerchantOffer(new ItemCost(Items.EMERALD, FRY_EMERALDS), fry, 8, 6, 0.05f);
    }
}
