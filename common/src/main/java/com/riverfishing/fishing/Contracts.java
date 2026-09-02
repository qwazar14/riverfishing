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
        return sp.getServer() == null ? 0L : sp.getServer().overworld().getDayTime() / 24000L;
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
            if (journal.getCompound(RiverFishing.id(sp).toString()).getInt("count") <= 0) continue;
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
        //
        // The profile field is weight_g and it is ALREADY GRAMS — an earlier cut multiplied by 1000 on
        // the way in and asked for peacock bass "from 1753.1 kg", which is not a hard contract, it is an
        // impossible one. tools/check_contract_weights.py exists so that cannot come back.
        double share = 0.6 + rng.nextDouble() * 0.4;
        int g = (int) Math.round(p.weightMean * share);
        return round(Math.max(0, Math.min(g, (int) Math.round(p.weightMax * 0.8))));
    }

    /**
     * A bar an angler would say out loud. "From 251 g" is a computation showing its working; the board
     * asks for 250 g, and for anything over a kilo it asks in round hundreds.
     */
    private static int round(int grams) {
        int step = grams >= 1000 ? 100 : 50;
        return Math.max(step, (grams / step) * step);
    }

    // ---- what has been filled -----------------------------------------------------------------------

    /** The filled set, cleared whenever the day it was written on is no longer today. */
    private static CompoundTag state(ServerPlayer sp) {
        CompoundTag root = PlayerData.root(sp).getCompound(TAG);
        if (root.getLong("day") != today(sp)) {
            root = new CompoundTag();
            root.putLong("day", today(sp));
        }
        return root;
    }

    private static boolean isFilled(CompoundTag state, String id) {
        return state.getBoolean(id);
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

        List<Held> have = held(sp.getInventory(), c.species(), c.minGrams());
        if (have.size() < c.count()) {
            sp.displayClientMessage(Component.translatable("message.riverfishing.contract_short",
                    have.size(), c.count(), c.fish()).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        take(sp, have.subList(0, c.count()));

        st.putBoolean(c.id(), true);
        PlayerData.root(sp).put(TAG, st);
        PlayerData.markDirty(sp);

        ItemStack pay = new ItemStack(Items.EMERALD, c.emeralds());
        if (!sp.getInventory().add(pay)) sp.drop(pay, false);
        JournalData.addXp(sp, c.xp());

        sp.displayClientMessage(Component.translatable("message.riverfishing.contract_filled",
                c.count(), c.fish(), c.emeralds()).withStyle(ChatFormatting.GREEN), false);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_YES,
                SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    /**
     * Where one qualifying fish is: which inventory slot, and where inside the keepnet in that slot —
     * {@code -1} for a fish lying loose. The weight rides along so the list can be sorted without
     * reading every stack again.
     */
    public record Held(int slot, int inNet, int grams) {}

    /**
     * Every fish this contract accepts, loose in the bag OR inside a keepnet in it, SMALLEST FIRST.
     *
     * <p>Keepnets count because that is where a catch actually lives — a net is the thing you carry a
     * session home in, and a contract that could not see into one would be asking you to tip the net
     * out on the bank first.
     *
     * <p>One method, both sides: the journal row counts with it and the server takes with it, so what
     * the row promises and what the claim finds cannot drift apart.
     */
    public static List<Held> held(net.minecraft.world.entity.player.Inventory inv,
                                  String species, int minGrams) {
        List<Held> out = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof FishItem f) {
                if (f.species().getPath().equals(species) && FishItem.getWeightG(s) >= minGrams) {
                    out.add(new Held(i, -1, FishItem.getWeightG(s)));
                }
                continue;
            }
            if (!(s.getItem() instanceof com.riverfishing.item.KeepnetItem)) continue;
            List<com.riverfishing.item.KeepnetData.Placed> placed =
                    com.riverfishing.item.KeepnetData.read(s).items();
            for (int k = 0; k < placed.size(); k++) {
                ItemStack fish = placed.get(k).stack();
                if (!(fish.getItem() instanceof FishItem f)) continue;
                if (!f.species().getPath().equals(species)) continue;
                if (FishItem.getWeightG(fish) < minGrams) continue;
                out.add(new Held(i, k, FishItem.getWeightG(fish)));
            }
        }
        // Smallest first: a contract must never quietly walk off with the trophy when an ordinary fish
        // would have done.
        out.sort(java.util.Comparator.comparingInt(Held::grams));
        return out;
    }

    /**
     * Hand these over. Loose fish are cleared from their slot; netted ones are pulled out of the net
     * they are in, HIGHEST INDEX FIRST — the net is a list, and removing from the front of one shifts
     * everything after it, so taking two fish out of the same net in the order they were found would
     * take the wrong second fish.
     */
    private static void take(ServerPlayer sp, List<Held> taking) {
        java.util.Map<Integer, List<Integer>> nets = new java.util.HashMap<>();
        for (Held h : taking) {
            if (h.inNet() < 0) {
                sp.getInventory().setItem(h.slot(), ItemStack.EMPTY);
            } else {
                nets.computeIfAbsent(h.slot(), k -> new ArrayList<>()).add(h.inNet());
            }
        }
        for (java.util.Map.Entry<Integer, List<Integer>> e : nets.entrySet()) {
            ItemStack net = sp.getInventory().getItem(e.getKey());
            com.riverfishing.item.KeepnetData data = com.riverfishing.item.KeepnetData.read(net);
            List<Integer> idx = new ArrayList<>(e.getValue());
            idx.sort(java.util.Comparator.reverseOrder());
            for (int i : idx) {
                if (i >= 0 && i < data.items().size()) data.items().remove(i);
            }
            data.write(net);
        }
    }
}
