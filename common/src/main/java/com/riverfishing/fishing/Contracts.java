package com.riverfishing.fishing;

import com.riverfishing.RiverFishing;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.item.FishItem;
import com.riverfishing.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * §contracts (0.8.3): standing jobs from the fisherman — bring him {@code n} of a species at a size,
 * and be paid for the set.
 *
 * <p>The complaint this answers is that a fish had exactly one use: sell it. The order of the day
 * (§order-board) rewards CATCHING one, so the fish is still there afterwards and still gets sold. A
 * contract is the first thing in the mod that TAKES the fish, which is what makes it a choice: the
 * emeralds on the counter now, or the better price for three of them together.
 *
 * <p>Nothing about the board is stored. Which three jobs stand today is derived from the world day and
 * who is asking, so it is the same board every time it is drawn, survives a restart, and costs no save
 * data; only which ones you have already filled is written down, and that resets with the day.
 *
 * <p>They are drawn from the species you have ALREADY CAUGHT. A contract naming a fish you have never
 * seen is a wiki lookup, not a job — and the order of the day is the part of the mod that sends you
 * somewhere new, with a checklist to get you there.
 */
public final class Contracts {
    /** How many stand at once. Three is enough to choose between and few enough to read at a glance. */
    public static final int PER_DAY = 3;

    /** Paid over the plain counter price for the same fish, one at a time — the reason to bother. */
    private static final double SET_BONUS = 1.6;

    /** Angler XP per emerald of pay — one dial, so the two rewards cannot drift apart. */
    private static final int XP_PER_EMERALD = 3;

    private static final String TAG = "riverfishing_contracts";

    private Contracts() {}

    /**
     * One job. {@code id} is derived, not stored: the day plus the slot, so a claim can name a contract
     * the server can rebuild and check rather than believe.
     */
    public record Contract(String id, String species, int count, int minGrams, int emeralds, int xp) {
        public Component fish() {
            return Component.translatable("fish.riverfishing." + species);
        }
    }

    // ---- the board ----------------------------------------------------------------------------------

    public static long today(ServerPlayer sp) {
        // 26.x: the server hangs off the level, and the day is the OVERWORLD clock by name.
        net.minecraft.server.MinecraftServer server = sp.level().getServer();
        return server == null ? 0L : server.overworld().getOverworldClockTime() / 24000L;
    }

    /**
     * What this player can be given a job for: landed at least once AND bought by some fisherman, in the
     * fixed species order so the draw is stable.
     *
     * <p>Both halves are filtered HERE rather than at the draw. Dropping an unpriceable species after it
     * was picked would delete that slot from the board — a job that silently is not there, with nothing
     * anywhere saying why, which is the same shape of bug as a recipe the journal quietly stops drawing.
     */
    private static List<String> pool(CompoundTag journal) {
        List<String> out = new ArrayList<>();
        for (String sp : ModItems.FISH_SPECIES) {
            if (journal.getCompoundOrEmpty(RiverFishing.id(sp).toString()).getIntOr("count", 0) <= 0) continue;
            if (com.riverfishing.registry.ModVillagers.baseEmeralds(sp) <= 0) continue;
            out.add(sp);
        }
        return out;
    }

    /**
     * Today's board. Empty until the player has caught something — with nothing landed there is nothing
     * to draw from, and the quest chain is what carries the first hour anyway.
     */
    public static List<Contract> forPlayer(ServerPlayer sp) {
        List<String> pool = pool(JournalData.get(sp));
        if (pool.isEmpty()) return List.of();

        long day = today(sp);
        List<Contract> out = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (int slot = 0; slot < PER_DAY; slot++) {
            // Seeded per (day, player, slot): the same board for the whole day, a different one for the
            // player next to you, and no state anywhere.
            Random rng = new Random(day * 1_000_003L + sp.getUUID().hashCode() * 31L + slot);
            String species = null;
            // A board of three that names one fish three times is one job wearing three hats.
            for (int tries = 0; tries < 8 && species == null; tries++) {
                String pick = pool.get(rng.nextInt(pool.size()));
                if (!taken.contains(pick)) species = pick;
            }
            if (species == null) break;              // pool smaller than the board: show what there is
            taken.add(species);

            int base = com.riverfishing.registry.ModVillagers.baseEmeralds(species);
            int count = 2 + rng.nextInt(3);          // 2..4
            int emeralds = Math.max(1, (int) Math.round(base * count * SET_BONUS));
            // Angler XP off the pay rather than off the trade's own xp: 26.x reads its prices out of a
            // datapack registry that carries no xp at all, and a contract that paid differently by game
            // version would be a balance difference nobody chose.
            out.add(new Contract("c" + day + "_" + slot, species, count, minGrams(species, rng),
                    emeralds, Math.max(1, emeralds * XP_PER_EMERALD)));
        }
        return out;
    }

    /**
     * The size bar, off the species' own profile rather than off this player's record: pinning it to a
     * personal best would make every contract harder the better you fished, which is a punishment
     * wearing the costume of a difficulty curve.
     */
    private static int minGrams(String species, Random rng) {
        FishProfile p = FishProfileManager.get().byId(RiverFishing.id(species));
        if (p == null) return 0;
        // 60-100% of an ordinary specimen: a bar you clear with a decent fish, not with the first one.
        double share = 0.6 + rng.nextDouble() * 0.4;
        int g = (int) Math.round(p.weightMean * 1000.0 * share);
        return Math.max(0, Math.min(g, (int) Math.round(p.weightMax * 1000.0 * 0.8)));
    }

    // ---- what has been filled -----------------------------------------------------------------------

    /** The filled set, cleared whenever the day it was written on is no longer today. */
    private static CompoundTag state(ServerPlayer sp) {
        CompoundTag root = PlayerData.root(sp).getCompoundOrEmpty(TAG);
        if (root.getLongOr("day", 0L) != today(sp)) {
            root = new CompoundTag();
            root.putLong("day", today(sp));
        }
        return root;
    }

    private static boolean isFilled(CompoundTag state, String id) {
        return state.getBooleanOr(id, false);
    }

    // ---- the journal payload ------------------------------------------------------------------------

    /** Today's board as the journal draws it, {@code done} included so a filled row can say so. */
    public static ListTag build(ServerPlayer sp) {
        CompoundTag st = state(sp);
        ListTag list = new ListTag();
        for (Contract c : forPlayer(sp)) {
            CompoundTag t = new CompoundTag();
            t.putString("id", c.id());
            t.putString("sp", c.species());
            t.putInt("n", c.count());
            t.putInt("w", c.minGrams());
            t.putInt("em", c.emeralds());
            t.putInt("xp", c.xp());
            t.putBoolean("done", isFilled(st, c.id()));
            list.add(t);
        }
        return list;
    }

    // ---- filling one --------------------------------------------------------------------------------

    /**
     * Hand the fish over. Re-derives the board rather than trusting the click, counts what is actually in
     * the bag, and takes nothing at all unless the whole set is there — a contract that ate two of the
     * three fish and paid nothing would be the worst bug this could have.
     */
    public static void claim(ServerPlayer sp, String id) {
        CompoundTag st = state(sp);
        if (isFilled(st, id)) return;
        Contract c = null;
        for (Contract candidate : forPlayer(sp)) {
            if (candidate.id().equals(id)) { c = candidate; break; }
        }
        if (c == null) return;

        List<Integer> slots = matching(sp, c);
        if (slots.size() < c.count()) {
            sp.sendOverlayMessage(Component.translatable("message.riverfishing.contract_short",
                    slots.size(), c.count(), c.fish()).withStyle(ChatFormatting.YELLOW));
            return;
        }
        for (int i = 0; i < c.count(); i++) {
            sp.getInventory().setItem(slots.get(i), ItemStack.EMPTY);
        }

        st.putBoolean(c.id(), true);
        PlayerData.root(sp).put(TAG, st);
        PlayerData.markDirty(sp);

        ItemStack pay = new ItemStack(Items.EMERALD, c.emeralds());
        if (!sp.getInventory().add(pay)) sp.drop(pay, false);
        JournalData.addXp(sp, c.xp());

        sp.sendSystemMessage(Component.translatable("message.riverfishing.contract_filled",
                c.count(), c.fish(), c.emeralds()).withStyle(ChatFormatting.GREEN));
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_YES,
                SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    /**
     * Inventory slots holding a fish this contract accepts, the SMALLEST qualifying ones first: a
     * contract must never quietly take the trophy off you when an ordinary fish would have done.
     */
    private static List<Integer> matching(ServerPlayer sp, Contract c) {
        List<Integer> slots = new ArrayList<>();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof FishItem f)) continue;
            if (!f.species().getPath().equals(c.species())) continue;
            if (FishItem.getWeightG(s) < c.minGrams()) continue;
            slots.add(i);
        }
        slots.sort((a, b) -> Integer.compare(
                FishItem.getWeightG(inv.getItem(a)), FishItem.getWeightG(inv.getItem(b))));
        return slots;
    }
}
