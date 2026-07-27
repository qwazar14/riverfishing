package com.riverfishing.quest;

import com.riverfishing.RiverFishing;
import com.riverfishing.fishing.JournalData;
import com.riverfishing.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Angler quests (§quests): a fixed chain of directed goals across five stages that give the mod a spine —
 * "what do I do next?" Completion is DERIVED live from the journal records (so it needs no extra tracking),
 * and each quest hands out its reward exactly once (guarded by {@link QuestData}). Rewards are chosen to
 * pull the player into the next stage (e.g. "catch 8 species" gifts a spinning rod). Evaluated after every
 * catch and level-up from {@code FishingManager}. The journal's Quests tab reads the same list.
 */
public final class Quests {
    private Quests() {}

    /** A quest goal, evaluated against the journal-records NBT (works on both server and client). */
    public interface Goal {
        boolean complete(CompoundTag journal);
        default String progress(CompoundTag journal) { return ""; }
    }

    public record Quest(String id, int stage, Goal goal, Supplier<ItemStack> reward, int xp) {
        public Component title() { return Component.translatable("quest.riverfishing." + id); }
        public ItemStack rewardStack() {
            ItemStack s = reward.get();
            return s == null ? ItemStack.EMPTY : s;
        }
    }

    // ---- goal factories (read the same journal NBT the records tab uses) ----

    private static String rec(String sp) { return RiverFishing.id(sp).toString(); }

    /** Catch a species {@code count} times (any size). */
    private static Goal species(String sp, int count) {
        String key = rec(sp);
        return new Goal() {
            public boolean complete(CompoundTag j) { return j.getCompound(key).getInt("count") >= count; }
            public String progress(CompoundTag j) { return Math.min(count, j.getCompound(key).getInt("count")) + "/" + count; }
        };
    }

    /** Catch a specimen of a species at or above {@code minGrams}. */
    private static Goal weight(String sp, int minGrams) {
        String key = rec(sp);
        return j -> j.getCompound(key).getInt("best") >= minGrams;
    }

    private static Goal distinct(int n) {
        return new Goal() {
            public boolean complete(CompoundTag j) { return distinctCount(j) >= n; }
            public String progress(CompoundTag j) { return Math.min(n, distinctCount(j)) + "/" + n; }
        };
    }

    private static Goal total(int n) {
        return new Goal() {
            public boolean complete(CompoundTag j) { return j.getInt(JournalData.TOTAL) >= n; }
            public String progress(CompoundTag j) { return Math.min(n, j.getInt(JournalData.TOTAL)) + "/" + n; }
        };
    }

    private static Goal level(int l) {
        return j -> JournalData.levelForXp(j.getLong(JournalData.XP)) >= l;
    }

    private static Goal trophies(int n) {
        return new Goal() {
            public boolean complete(CompoundTag j) { return j.getInt(JournalData.TROPHIES) >= n; }
            public String progress(CompoundTag j) { return Math.min(n, j.getInt(JournalData.TROPHIES)) + "/" + n; }
        };
    }

    /** Land {@code n} fish through the ice (§winter-quests). */
    private static Goal ice(int n) {
        return new Goal() {
            public boolean complete(CompoundTag j) { return j.getInt(JournalData.ICE) >= n; }
            public String progress(CompoundTag j) { return Math.min(n, j.getInt(JournalData.ICE)) + "/" + n; }
        };
    }

    /** Any one of the listed species has been caught (§billfish: "поймай любого биллфиша"). */
    private static Goal anyOf(String... sps) {
        return j -> {
            for (String sp : sps) if (j.getCompound(rec(sp)).getInt("count") > 0) return true;
            return false;
        };
    }

    private static Goal koi() {
        return j -> {
            for (String sp : ModItems.FISH_SPECIES) {
                if (sp.startsWith("carp_koi_") && j.getCompound(rec(sp)).getInt("count") > 0) return true;
            }
            return false;
        };
    }

    private static int distinctCount(CompoundTag j) {
        int c = 0;
        for (String sp : ModItems.FISH_SPECIES) {
            if (j.getCompound(rec(sp)).getInt("count") > 0) c++;
        }
        return c;
    }

    // ---- stage completion (§stage-reward) ----

    public static final int STAGES = 8;
    /** Reach this fraction of a stage's tasks to UNLOCK the next stage; 100% earns the stage reward. */
    private static final double STAGE_UNLOCK_FRACTION = 0.70;

    /** The per-stage prize quest (id ends in "_done") is not itself a "task". */
    public static boolean isStageReward(Quest q) {
        return q.id().endsWith("_done");
    }

    public static int stageTaskTotal(int stage) {
        int n = 0;
        for (Quest q : ALL) if (q.stage() == stage && !isStageReward(q)) n++;
        return n;
    }

    public static int stageTaskDone(CompoundTag j, int stage) {
        int n = 0;
        for (Quest q : ALL) if (q.stage() == stage && !isStageReward(q) && q.goal().complete(j)) n++;
        return n;
    }

    /** All tasks of the stage are complete (100%) — earns the stage reward. */
    private static Goal stageComplete(int stage) {
        return new Goal() {
            public boolean complete(CompoundTag j) {
                int total = stageTaskTotal(stage);
                return total > 0 && stageTaskDone(j, stage) >= total;
            }
            public String progress(CompoundTag j) {
                return stageTaskDone(j, stage) + "/" + stageTaskTotal(stage);
            }
        };
    }

    /**
     * §stage-reveal: the highest stage the player can see/claim. Stage 1 is always open; each later stage
     * unlocks once {@link #STAGE_UNLOCK_FRACTION} (70%) of the previous stage's tasks are done.
     */
    public static int maxUnlockedStage(CompoundTag j) {
        int max = 1;
        for (int s = 1; s < STAGES; s++) {
            int total = stageTaskTotal(s);
            if (total == 0) break;
            if (stageTaskDone(j, s) / (double) total >= STAGE_UNLOCK_FRACTION) max = s + 1;
            else break;
        }
        return max;
    }

    // ---- reward factories ----

    private static Supplier<ItemStack> item(String id, int count) {
        return () -> {
            Item it = BuiltInRegistries.ITEM.get(RiverFishing.id(id));
            return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
        };
    }

    private static Supplier<ItemStack> emeralds(int n) {
        return () -> new ItemStack(Items.EMERALD, n);
    }

    // ---- the chain ----

    public static final List<Quest> ALL = List.of(
            // Stage 1 — first casts at the pond
            new Quest("q_first_fish", 1, total(1), item("worm", 8), 15),
            new Quest("q_roach", 1, species("roach", 1), item("maggot", 8), 15),
            new Quest("q_species3", 1, distinct(3), item("hook_12", 4), 30),
            new Quest("q_crucian", 1, species("crucian_carp", 1), item("groundbait_grain", 4), 20),
            new Quest("q_ten_fish", 1, total(10), item("bait_trap", 1), 25),
            new Quest("q_stage1_done", 1, stageComplete(1), emeralds(12), 40),
            // Stage 2 — float & feeder
            new Quest("q_bream", 2, species("bream", 1), item("hook_8", 3), 25),
            new Quest("q_rudd", 2, species("rudd", 1), item("groundbait_powder", 4), 20),
            new Quest("q_tench", 2, species("tench", 1), item("groundbait_pellet", 4), 35),
            new Quest("q_bream_big", 2, weight("bream", 2000), emeralds(6), 40),
            new Quest("q_species8", 2, distinct(8), item("spinning_rod", 1), 60),
            new Quest("q_stage2_done", 2, stageComplete(2), item("reel_3000", 1), 60),
            // Stage 3 — predators
            new Quest("q_perch", 3, species("perch", 1), item("spinner", 2), 25),
            new Quest("q_pike", 3, species("pike", 1), item("leader", 2), 40),
            new Quest("q_pike_big", 3, weight("pike", 5000), emeralds(10), 70),
            new Quest("q_zander", 3, species("zander", 1), item("wobbler", 1), 45),
            new Quest("q_asp", 3, species("asp", 1), emeralds(6), 45),
            new Quest("q_stage3_done", 3, stageComplete(3), item("leader_titanium", 1), 80),
            // Stage 4 — heavy tackle
            new Quest("q_carp", 4, species("carp", 1), item("boilie", 8), 50),
            new Quest("q_carp_big", 4, weight("carp", 8000), emeralds(10), 80),
            new Quest("q_catfish", 4, species("catfish", 1), item("leader_titanium", 1), 80),
            new Quest("q_catfish_big", 4, weight("catfish", 20000), emeralds(20), 120),
            new Quest("q_trout", 4, species("trout", 1), emeralds(6), 50),
            new Quest("q_hundred", 4, total(100), item("reel_5000", 1), 90),
            new Quest("q_stage4_done", 4, stageComplete(4), emeralds(32), 120),
            // Stage 5 — master
            new Quest("q_species15", 5, distinct(15), item("reel_7000", 1), 100),
            new Quest("q_sterlet", 5, species("sterlet", 1), emeralds(16), 100),
            new Quest("q_grayling", 5, species("grayling", 1), emeralds(10), 70),
            new Quest("q_koi", 5, koi(), emeralds(12), 80),
            new Quest("q_trophy", 5, trophies(1), emeralds(8), 60),
            new Quest("q_trophy5", 5, trophies(5), emeralds(24), 140),
            new Quest("q_species20", 5, distinct(20), emeralds(20), 150),
            new Quest("q_master", 5, level(20), emeralds(30), 0),
            new Quest("q_stage5_done", 5, stageComplete(5), item("carp_rod", 1), 150),
            // Stage 6 — under the ice (§winter-quests)
            new Quest("q_ice_first", 6, ice(1), item("mormyshka", 2), 40),
            new Quest("q_ice_burbot", 6, species("burbot", 1), item("groundbait_cake", 4), 60),
            new Quest("q_ice_ruffe", 6, species("ruffe", 1), item("maggot", 12), 30),
            new Quest("q_ice_ten", 6, ice(10), item("winter_rod", 1), 80),
            new Quest("q_ice_thirty", 6, ice(30), emeralds(24), 160),
            new Quest("q_stage6_done", 6, stageComplete(6), emeralds(50), 200),
            // Stage 7 — the north wave (§north): taiga rivers, the salmon run, the taimen.
            // Rewards hand out the exact lure the NEXT quest's fish wants — the stage teaches itself.
            new Quest("q_rotan", 7, species("rotan", 1), item("spinner", 1), 20),
            new Quest("q_nase", 7, species("nase", 1), item("maggot", 12), 30),
            new Quest("q_vimba", 7, species("vimba", 1), item("groundbait_grain", 4), 40),
            new Quest("q_whitefish", 7, species("whitefish", 1), item("bloodworm", 12), 50),
            new Quest("q_char", 7, species("char", 1), item("castmaster", 1), 60),
            new Quest("q_lenok", 7, species("lenok", 1), item("wobbler", 1), 70),
            new Quest("q_salmon", 7, species("salmon", 1), item("spoon", 2), 90),
            new Quest("q_taimen", 7, weight("taimen", 15000), emeralds(30), 160),
            new Quest("q_stage7_done", 7, stageComplete(7), item("surf_rod", 1), 180),
            // Stage 8 — the sea and big game (§ocean): coast → shelf → the pelagic monsters.
            new Quest("q_seabass", 8, species("seabass", 1), item("castmaster", 1), 50),
            new Quest("q_herring", 8, species("herring", 5), item("fish_strip", 8), 50),
            new Quest("q_cod", 8, species("cod", 1), emeralds(8), 60),
            new Quest("q_species40", 8, distinct(40), item("trolling_rod", 1), 120),
            new Quest("q_mahi", 8, species("mahi", 1), emeralds(10), 90),
            new Quest("q_tuna_big", 8, weight("yellowfin_tuna", 60000), emeralds(24), 150),
            new Quest("q_halibut", 8, species("halibut", 1), item("line_braid_060", 1), 150),
            new Quest("q_billfish", 8, anyOf("blue_marlin", "sailfish", "swordfish"), emeralds(40), 200),
            new Quest("q_species60", 8, distinct(60), item("reel_14000", 1), 250),
            new Quest("q_stage8_done", 8, stageComplete(8), emeralds(64), 300)
    );

    /**
     * After a catch or level-up: NOTIFY (once) about newly-completed quests. The reward itself is claimed
     * by CLICKING the quest in the journal (§quest-claim) — see {@link #claim}. The "_seen" marker in
     * {@link QuestData} keeps the chat message from repeating on every catch.
     */
    public static void onProgress(ServerPlayer sp, ServerLevel level) {
        CompoundTag journal = JournalData.get(sp);
        int maxStage = maxUnlockedStage(journal);
        for (Quest q : ALL) {
            if (q.stage() > maxStage) continue; // §stage-reveal: don't flag rewards in a locked stage
            if (QuestData.isRewarded(sp, q.id())) continue;
            if (QuestData.isRewarded(sp, q.id() + "_seen")) continue;
            if (!q.goal().complete(journal)) continue;
            QuestData.markRewarded(sp, q.id() + "_seen");
            sp.displayClientMessage(Component.translatable("message.riverfishing.quest_ready", q.title())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            level.playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.7f, 1.4f);
        }
    }

    /**
     * Claim a quest reward from the journal (§quest-claim). Server-validated: the goal must be complete
     * and unrewarded. Grants the item + quest XP, then re-runs {@link #onProgress} so a level-up from the
     * quest XP can immediately flag the next quest as ready.
     */
    public static void claim(ServerPlayer sp, ServerLevel level, String questId) {
        for (Quest q : ALL) {
            if (!q.id().equals(questId)) continue;
            if (QuestData.isRewarded(sp, q.id())) return;
            if (q.stage() > maxUnlockedStage(JournalData.get(sp))) return; // §stage-reveal: stage still locked
            if (!q.goal().complete(JournalData.get(sp))) return;
            QuestData.markRewarded(sp, q.id());
            ItemStack reward = q.rewardStack();
            if (!reward.isEmpty() && !sp.getInventory().add(reward)) sp.drop(reward, false);
            if (q.xp() > 0) JournalData.addXp(sp, q.xp());
            sp.displayClientMessage(Component.translatable("message.riverfishing.quest_done", q.title())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            level.playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 0.7f, 1.3f);
            onProgress(sp, level);
            return;
        }
    }
}
