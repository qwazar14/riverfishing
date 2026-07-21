package com.riverfishing.fishing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Lightweight angling records (§15): per-species count + best weight, plus a global total, stored in
 * the player's persistent NBT (copied on death by {@code ModEvents}'s clone handler). The
 * fishing-journal item reads this back. A full bestiary GUI is a later step.
 */
public final class JournalData {
    public static final String TAG = "riverfishing_journal";
    public static final String TOTAL = "total";
    public static final String XP = "xp";
    public static final String TROPHIES = "trophies";
    public static final String ICE = "ice"; // §winter-quests: fish landed through an ice hole

    private JournalData() {}

    public static CompoundTag get(Player player) {
        return PlayerData.root(player).getCompound(TAG);
    }

    public static void record(Player player, ResourceLocation species, int weightG) {
        CompoundTag root = get(player);
        CompoundTag fish = root.getCompound(species.toString());
        fish.putInt("count", fish.getInt("count") + 1);
        fish.putInt("best", Math.max(fish.getInt("best"), weightG));
        root.put(species.toString(), fish);
        root.putInt(TOTAL, root.getInt(TOTAL) + 1);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    /** Records a trophy-grade catch (§quests): a separate counter for trophy-hunting goals. */
    public static void addTrophy(Player player) {
        CompoundTag root = get(player);
        root.putInt(TROPHIES, root.getInt(TROPHIES) + 1);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    /** Records a fish landed through the ice (§winter-quests): a counter for winter-fishing goals. */
    public static void addIceCatch(Player player) {
        CompoundTag root = get(player);
        root.putInt(ICE, root.getInt(ICE) + 1);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    /** True if the player has never landed this species before (call BEFORE {@link #record}). */
    public static boolean isNewSpecies(Player player, ResourceLocation species) {
        return get(player).getCompound(species.toString()).getInt("count") == 0;
    }

    /**
     * §species-advancements: discovered species count, KOI EXCLUDED (they are a hidden collectible
     * with their own challenge). Counted against the live built-in list, so the tiered "N species"
     * advancements can never drift from the real species roster again.
     */
    public static int speciesCount(Player player) {
        CompoundTag root = get(player);
        int n = 0;
        for (String id : com.riverfishing.registry.ModItems.FISH_SPECIES) {
            if (!id.startsWith("carp_koi") && root.getCompound("riverfishing:" + id).getInt("count") > 0) n++;
        }
        return n;
    }

    /** Non-koi species total — the "all species" bar. */
    public static int speciesTotal() {
        int n = 0;
        for (String id : com.riverfishing.registry.ModItems.FISH_SPECIES) {
            if (!id.startsWith("carp_koi")) n++;
        }
        return n;
    }

    /** True if {@code weightG} beats the player's stored best (call BEFORE {@link #record}). */
    public static boolean isPersonalBest(Player player, ResourceLocation species, int weightG) {
        return weightG > get(player).getCompound(species.toString()).getInt("best");
    }

    // ---- Angler progression: XP -> level -> rank, stored in the same NBT root ----

    public static long getXp(Player player) {
        return get(player).getLong(XP);
    }

    public static void addXp(Player player, long amount) {
        CompoundTag root = get(player);
        root.putLong(XP, root.getLong(XP) + Math.max(0, amount));
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    /** Cumulative XP required to REACH the given level (triangular curve). */
    public static long xpForLevel(int level) {
        return 50L * level * (level + 1);
    }

    public static int getLevel(Player player) {
        return levelForXp(getXp(player));
    }

    public static int levelForXp(long xp) {
        int level = 0;
        while (xpForLevel(level + 1) <= xp) level++;
        return level;
    }

    /** XP still needed to reach the next level. */
    public static long xpToNext(Player player) {
        return xpForLevel(getLevel(player) + 1) - getXp(player);
    }

    /** A lang-suffix rank key for the level: bronze &lt;5, silver &lt;10, gold &lt;20, else master. */
    public static String rankKey(int level) {
        if (level >= 20) return "master";
        if (level >= 10) return "gold";
        if (level >= 5) return "silver";
        return "bronze";
    }
}
