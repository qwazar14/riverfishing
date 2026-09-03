package com.riverfishing.fishing;

import com.riverfishing.RiverFishing;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.item.ContractItem;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.StackNbt;
import com.riverfishing.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * §contracts (0.9.0): jobs posted by a fisherman, taken as a paper, fished under its terms, handed back.
 *
 * <p>The first cut was a board in the journal that paid for any three bream — an order of the day with
 * a count, which is what the complaint said. This is the shape a fishing sim gives it: the FISHERMAN
 * posts, the board is HIS (three posts a day per villager, so two fishermen are two boards), you take a
 * post off it as a paper, the paper says HOW the fish are to be caught — from a river, on a float rod,
 * on worm, at night — and only a fish landed under those terms counts. Bring the paper back with the
 * fish and he pays, in emeralds, XP and REPUTATION; enough reputation and his counter opens a shelf
 * the rest of the village never sees.
 *
 * <p>Nothing about a board is stored: which three posts stand today is the villager's UUID and the
 * world day through a seeded random, so it is the same board all day, for everyone, after a restart,
 * with no save data. The paper stores its own terms and its own progress; the player's reputation is
 * one integer in their player data.
 */
public final class Contracts {
    /** Posts per fisherman per day. */
    public static final int POSTS = 3;
    /** Papers a player may carry at once — a choice, not a collection. */
    public static final int MAX_ACTIVE = 2;
    /** World days a paper stays good for. */
    public static final int DAYS_TO_FILL = 7;
    /** Reputation at which each trusted shelf opens — see ModVillagers.trustedSlots. */
    public static final int[] TRUST_STEPS = {5, 15, 30};

    /** Paid over the plain counter price for the same fish, one at a time — the reason to bother. */
    private static final double SET_BONUS = 1.6;
    /** More per term: a job with conditions is a harder job. */
    private static final double TERM_BONUS = 0.3;
    private static final int XP_PER_EMERALD = 3;
    private static final String REP = "contract_rep";

    private Contracts() {}

    // ---- the board ----------------------------------------------------------------------------------

    public static long today(ServerLevel level) {
        return level.getServer().overworld().getDayTime() / 24000L;
    }

    /** Fish any fisherman buys, in the fixed species order so the draw is stable. */
    private static List<String> pool() {
        List<String> out = new ArrayList<>();
        for (String sp : ModItems.FISH_SPECIES) {
            if (com.riverfishing.registry.ModVillagers.baseEmeralds(sp) > 0) out.add(sp);
        }
        return out;
    }

    /**
     * Today's posts at this fisherman, as tags with the SAME keys the paper carries, so a post is turned
     * into a paper by copying it and the board, the tooltip and the journal read one format.
     */
    public static List<CompoundTag> posts(Villager v, ServerLevel level) {
        List<String> pool = pool();
        List<CompoundTag> out = new ArrayList<>();
        if (pool.isEmpty()) return out;
        long day = today(level);
        List<String> taken = new ArrayList<>();
        for (int slot = 0; slot < POSTS; slot++) {
            // §board-3: a post lives DAYS_TO_FILL days, the same life its paper has, so the two end
            // together; the three slots are staggered a third of that apart, so the board never
            // turns over all at once. A post's epoch is its identity — the day it started on.
            int stagger = slot * DAYS_TO_FILL / POSTS;
            long epoch = (day + stagger) / DAYS_TO_FILL;
            long ends = (epoch + 1) * DAYS_TO_FILL - stagger;    // the first day it is gone
            Random rng = new Random(epoch * 1_000_003L + v.getUUID().hashCode() * 31L + slot);
            String species = null;
            for (int tries = 0; tries < 8 && species == null; tries++) {
                String pick = pool.get(rng.nextInt(pool.size()));
                if (!taken.contains(pick)) species = pick;
            }
            if (species == null) break;
            taken.add(species);
            FishProfile p = FishProfileManager.get().byId(RiverFishing.id(species));

            CompoundTag t = new CompoundTag();
            t.putString("Id", v.getUUID().toString().substring(0, 8) + "_" + epoch + "_" + slot);
            t.putLong("Exp", ends - 1);                          // the last day the paper is good
            t.putString("Sp", species);
            if (slot == 2 && rng.nextBoolean()) {    // §e: the third post is a fry order half the time
                fryPost(t, species, rng);
                out.add(t);
                continue;
            }
            int count = 2 + rng.nextInt(3);          // 2..4
            t.putInt("N", count);
            t.putInt("W", minGrams(p, rng));
            int terms = terms(t, p, rng);
            int base = com.riverfishing.registry.ModVillagers.baseEmeralds(species);
            int em = Math.max(1, (int) Math.round(base * count * SET_BONUS * (1 + TERM_BONUS * terms)));
            t.putInt("Em", em);
            t.putInt("Xp", Math.max(1, em * XP_PER_EMERALD));
            t.putInt("Rep", 1 + terms);
            out.add(t);
        }
        return out;
    }

    /**
     * §e §breeding: a fry order — the fisherman restocks a water and wants 20/30/40 fry of the species,
     * no size bar, no terms: fry have no card to meet them with. Paid per fry at a fifth of a fish,
     * with the set bonus; the reputation of a two-term job, because it takes a tank and a season.
     */
    private static void fryPost(CompoundTag t, String species, Random rng) {
        int n = 20 + 10 * rng.nextInt(3);
        t.putString("Kind", "fry");
        t.putInt("N", n);
        int base = com.riverfishing.registry.ModVillagers.baseEmeralds(species);
        int em = Math.max(1, (int) Math.round(base * n / 5.0 * SET_BONUS));
        t.putInt("Em", em);
        t.putInt("Xp", Math.max(1, em * XP_PER_EMERALD));
        t.putInt("Rep", 2);
    }

    /**
     * The terms, drawn from what the profile says the fish actually likes, so every term is one the
     * fish can be caught under: a water it lives in, a bait it takes, a time it feeds. Each is rolled
     * separately; a post that rolled none gets the water, because a contract with no terms is the
     * order of the day again.
     *
     * @return how many terms were set
     */
    private static int terms(CompoundTag t, FishProfile p, Random rng) {
        int n = 0;
        String water = best(p == null ? null : p.waterBodies, rng, 1.0);
        if (water != null && rng.nextDouble() < 0.55) { t.putString("Water", water); n++; }
        // ponytail: the rod class comes off the family — predators and salmonids are worked, the rest
        // sit under a float or on the bottom. idealRods would name a rod, not a class.
        if (rng.nextDouble() < 0.4) {
            String g = p == null ? "" : p.group;
            String rod = g.equals("predator") || g.equals("salmonid") ? "active"
                    : rng.nextBoolean() ? "float" : "bottom";
            t.putString("Rod", rod);
            n++;
        }
        String bait = best(p == null ? null : p.baitScores, rng, 0.8);
        if (bait != null && rng.nextDouble() < 0.4) { t.putString("Bait", bait); n++; }
        String time = best(p == null ? null : p.time, rng, 1.05);
        if (time != null && rng.nextDouble() < 0.35) { t.putString("Time", time); n++; }
        if (n == 0) {
            if (water != null) { t.putString("Water", water); n++; }
            else { t.putString("Rod", rng.nextBoolean() ? "float" : "bottom"); n++; }
        }
        return n;
    }

    /** A random key whose factor clears the bar, or null. */
    private static String best(java.util.Map<String, Double> m, Random rng, double min) {
        if (m == null || m.isEmpty()) return null;
        List<String> ok = new ArrayList<>();
        for (var e : new java.util.TreeMap<>(m).entrySet()) if (e.getValue() >= min) ok.add(e.getKey());
        return ok.isEmpty() ? null : ok.get(rng.nextInt(ok.size()));
    }

    /**
     * The size bar, off the species' own profile: 60-100% of an ordinary specimen, a bar you clear with
     * a decent fish. weight_g is ALREADY GRAMS — tools/check_contract_weights.py guards the x1000.
     */
    private static int minGrams(FishProfile p, Random rng) {
        if (p == null) return 0;
        double share = 0.6 + rng.nextDouble() * 0.4;
        int g = (int) Math.round(p.weightMean * share);
        return round(Math.max(0, Math.min(g, (int) Math.round(p.weightMax * 0.8))));
    }

    private static int round(int grams) {
        int step = grams >= 1000 ? 100 : 50;
        return Math.max(step, (grams / step) * step);
    }

    // ---- taking one ---------------------------------------------------------------------------------

    public static int rep(Player p) {
        return PlayerData.root(p).getInt(REP);
    }

    private static int activeCount(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof ContractItem) n++;
        }
        return n;
    }

    private static boolean holds(Player p, String id) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof ContractItem && ContractItem.tag(s).getString("Id").equals(id)) return true;
        }
        return false;
    }

    /** The posts this player has taken, pruned of anything older than a paper can live. */
    public static CompoundTag ledger(ServerPlayer sp, ServerLevel level) {
        CompoundTag taken = PlayerData.root(sp).getCompound("contract_taken");
        long now = today(level);
        for (String k : new ArrayList<>(taken.getAllKeys())) {
            if (taken.getLong(k) < now - DAYS_TO_FILL - 1) taken.remove(k);
        }
        return taken;
    }

    /** Held now, or taken any time this post has existed. */
    public static boolean taken(ServerPlayer sp, CompoundTag ledger, String id) {
        return holds(sp, id) || ledger.contains(id);
    }

    /** The client clicked a post: rebuild the board for THAT villager and hand the paper over. */
    public static void take(ServerPlayer sp, int villagerId, int slot) {
        ServerLevel level = sp.serverLevel();
        if (!(level.getEntity(villagerId) instanceof Villager v) || v.distanceToSqr(sp) > 100) return;
        take0(sp, level, v, slot);
        // The board answers every click, taken or refused: the client greys a post the moment it is
        // clicked, and this is what puts it back when the server said no (hands full).
        com.riverfishing.registry.ModVillagers.sendBoard(v, level, sp);
    }

    private static void take0(ServerPlayer sp, ServerLevel level, Villager v, int slot) {
        if (Warden.banned(sp)) return;   // §i: the board he saw was blank; a stale click gets nothing
        List<CompoundTag> posts = posts(v, level);
        if (slot < 0 || slot >= posts.size()) return;
        CompoundTag post = posts.get(slot);
        // §cards-2: a post you took stays taken for its whole life — throwing the paper away and
        // clicking again is not a second contract, it is the same one without the paper.
        CompoundTag taken = ledger(sp, level);
        long now = today(level);
        if (taken(sp, taken, post.getString("Id"))) return;   // §board-taken: the board already shows it greyed
        if (activeCount(sp) >= MAX_ACTIVE) {
            say(sp, "contract_hands_full", ChatFormatting.YELLOW, MAX_ACTIVE);
            return;
        }
        ItemStack paper = new ItemStack(ModItems.CONTRACT.get());
        CompoundTag t = post.copy();
        StackNbt.set(paper, t);
        if (!sp.getInventory().add(paper)) sp.drop(paper, false);
        taken.putLong(post.getString("Id"), now);
        PlayerData.root(sp).put("contract_taken", taken);
        PlayerData.markDirty(sp);
        sp.displayClientMessage(Component.translatable("message.riverfishing.contract_taken",
                ContractItem.headline(t)).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.8f, 1f);
    }

    // ---- handing it in ------------------------------------------------------------------------------

    /**
     * The paper comes back to a fisherman. Re-checks the day, the count caught under the terms, and the
     * fish actually in the bag — and takes nothing at all unless the whole set is there.
     *
     * @return true when the paper was dealt with (paid or torn up) and the trade screen should not open
     */
    public static boolean handIn(ServerPlayer sp, ItemStack paper) {
        ServerLevel level = sp.serverLevel();
        CompoundTag t = ContractItem.tag(paper);
        if (!t.contains("Sp")) return false;
        if (t.getLong("Exp") < today(level)) {
            paper.shrink(1);
            say(sp, "contract_expired", ChatFormatting.RED);
            return true;
        }
        int n = t.getInt("N");
        String species = t.getString("Sp");
        if (ContractItem.isFry(t)) {   // §e: a fry order is filled out of fry buckets, not fish
            int fry = fryHeld(sp.getInventory(), species);
            if (fry < n) {
                say(sp, "contract_short_fry", ChatFormatting.YELLOW, fry, n, Component.translatable("fish.riverfishing." + species));
                return true;
            }
            takeFry(sp.getInventory(), species, n);
        } else {
            List<Held> have = held(sp.getInventory(), species, t.getInt("W"), t);
            if (have.size() < n) {
                sp.displayClientMessage(Component.translatable("message.riverfishing.contract_short",
                        have.size(), n, Component.translatable("fish.riverfishing." + species))
                        .withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
            take(sp, have.subList(0, n));
        }
        paper.shrink(1);

        ItemStack pay = new ItemStack(Items.EMERALD, t.getInt("Em"));
        if (!sp.getInventory().add(pay)) sp.drop(pay, false);
        JournalData.addXp(sp, t.getInt("Xp"));
        int before = rep(sp), after = before + t.getInt("Rep");
        PlayerData.root(sp).putInt(REP, after);
        PlayerData.markDirty(sp);

        sp.displayClientMessage(Component.translatable("message.riverfishing.contract_filled",
                n, Component.translatable("fish.riverfishing." + species), t.getInt("Em"), t.getInt("Rep"))
                .withStyle(ChatFormatting.GREEN), false);
        for (int step : TRUST_STEPS) {
            if (before < step && after >= step) {
                sp.displayClientMessage(Component.translatable("message.riverfishing.contract_trusted", step)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            }
        }
        level.playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8f, 1.1f);
        return true;
    }

    private static void say(ServerPlayer sp, String key, ChatFormatting colour, Object... args) {
        sp.displayClientMessage(Component.translatable("message.riverfishing." + key, args).withStyle(colour), true);
    }

    // ---- the journal --------------------------------------------------------------------------------

    /** The papers in the bag, as the journal lists them; the day rides along so it can say "n days left". */
    public static ListTag build(ServerPlayer sp) {
        ListTag list = new ListTag();
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.getItem() instanceof ContractItem) list.add(ContractItem.tag(s).copy());
        }
        return list;
    }

    // ---- the fish in the bag ------------------------------------------------------------------------

    /** Where one qualifying fish is: inventory slot, index inside the keepnet there ({@code -1} loose), weight. */
    public record Held(int slot, int inNet, int grams) {}

    /**
     * Every fish this contract accepts — species, size AND the terms on its card — loose in the bag OR
     * inside a keepnet in it, SMALLEST FIRST.
     * Keepnets count because that is where a catch actually lives. One method for both the journal's
     * count and the server's take, so what the row promises and what the hand-in finds cannot differ.
     */
    public static List<Held> held(net.minecraft.world.entity.player.Inventory inv,
                                  String species, int minGrams, CompoundTag terms) {
        List<Held> out = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof FishItem f) {
                if (f.species().getPath().equals(species) && FishItem.getWeightG(s) >= minGrams
                        && com.riverfishing.fish.CatchCard.meets(s, terms)) {   // §catch-card
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
                if (!com.riverfishing.fish.CatchCard.meets(fish, terms)) continue;
                out.add(new Held(i, k, FishItem.getWeightG(fish)));
            }
        }
        // Smallest first: a contract must never quietly walk off with the trophy.
        out.sort(java.util.Comparator.comparingInt(Held::grams));
        return out;
    }

    /**
     * Hand these over. Loose fish are cleared from their slot; netted ones are pulled out HIGHEST INDEX
     * FIRST — the net is a list, and removing from the front shifts everything after it.
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

    // ---- §e the fry in the bag ----------------------------------------------------------------------

    /** Fry of this species in the bag, summed over every bucket: one counter for the row and the hand-in. */
    public static int fryHeld(net.minecraft.world.entity.player.Inventory inv, String species) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof com.riverfishing.item.FryItem && RiverFishing.id(species).equals(com.riverfishing.item.FryItem.species(s))) {
                n += com.riverfishing.item.FryItem.count(s);
            }
        }
        return n;
    }

    /** Take {@code n} fry out of the buckets, front of the bag first; an emptied bucket goes with them. */
    private static void takeFry(net.minecraft.world.entity.player.Inventory inv, String species, int n) {
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof com.riverfishing.item.FryItem) || !RiverFishing.id(species).equals(com.riverfishing.item.FryItem.species(s))) continue;
            int c = com.riverfishing.item.FryItem.count(s);
            if (c <= n) { inv.setItem(i, ItemStack.EMPTY); n -= c; }
            else { com.riverfishing.item.FryItem.setCount(s, c - n); n = 0; }
        }
    }

    /** The lower-case name the terms use for a rod class. */
    public static String rodKey(com.riverfishing.component.RodClass c) {
        return c.name().toLowerCase(Locale.ROOT);
    }
}
