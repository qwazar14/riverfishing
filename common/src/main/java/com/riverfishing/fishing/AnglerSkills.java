package com.riverfishing.fishing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Angler skill tree (§skills). Every angler LEVEL ({@link JournalData}) grants one skill point; points
 * are spent on ranked perks. Ranks live in the journal NBT under {@code skills}, so they travel with the
 * player and are wiped by {@code /rffish reset}.
 *
 * <p>Seven perks, each 0/5. Six are always-on multipliers; {@link Perk#FINESSE} widens the strike QTE.
 */
public final class AnglerSkills {
    private static final String SKILLS = "skills";
    private static final int MAX_RANK = 5;

    /** A ranked perk: id (NBT + lang key) and branch (lang group). All cap at {@link #MAX_RANK}. */
    public enum Perk {
        FRUGAL("frugal", "bait"),
        QUICK_BITE("quick_bite", "sense"),
        NATURALIST("naturalist", "knowledge"),
        STRONG_LINE("strong_line", "hand"),
        ANGLERS_LUCK("anglers_luck", "fortune"),
        FINESSE("finesse", "skill");

        public final String id;
        public final String branch;
        public final int maxRank = MAX_RANK;

        Perk(String id, String branch) {
            this.id = id;
            this.branch = branch;
        }

        public static Perk byId(String id) {
            for (Perk p : values()) if (p.id.equals(id)) return p;
            return null;
        }
    }

    private AnglerSkills() {}

    // ---- point economy ----

    /** Total points ever granted = the angler's current level (one per level). */
    public static int totalPoints(Player player) {
        return JournalData.getLevel(player);
    }

    public static int spentPoints(Player player) {
        CompoundTag skills = JournalData.get(player).getCompound(SKILLS);
        int sum = 0;
        for (Perk p : Perk.values()) sum += skills.getInt(p.id);
        return sum;
    }

    public static int availablePoints(Player player) {
        return Math.max(0, totalPoints(player) - spentPoints(player));
    }

    public static int rank(Player player, Perk perk) {
        return JournalData.get(player).getCompound(SKILLS).getInt(perk.id);
    }

    /** Spend one point on a perk (§skills). Server-validated: a free point + below max rank. */
    public static boolean tryUnlock(Player player, Perk perk) {
        if (perk == null) return false;
        if (availablePoints(player) <= 0) return false;
        CompoundTag root = JournalData.get(player);
        CompoundTag skills = root.getCompound(SKILLS);
        int cur = skills.getInt(perk.id);
        if (cur >= perk.maxRank) return false;
        skills.putInt(perk.id, cur + 1);
        root.put(SKILLS, skills);
        PlayerData.root(player).put(JournalData.TAG, root);
        PlayerData.markDirty(player);
        return true;
    }

    // ---- gameplay effects (all neutral at rank 0) ----

    /** Бережливость: chance a natural bait is NOT consumed on a bite (+5%/rank → up to 25%). */
    public static double baitSkipChance(Player player) {
        return rank(player, Perk.FRUGAL) * 0.05;
    }

    /** Чуткость: time-to-bite multiplier (−5%/rank) — bites come sooner. */
    public static double biteSpeedMult(Player player) {
        return 1.0 - rank(player, Perk.QUICK_BITE) * 0.05;
    }

    /** Натуралист: overall bite-chance bonus (+5%/rank) — you find the fish. */
    public static double naturalistBonus(Player player) {
        return rank(player, Perk.NATURALIST) * 0.05;
    }

    /** Крепкая рука: line break-tolerance multiplier (+5%/rank). */
    public static double lineToleranceMult(Player player) {
        return 1.0 + rank(player, Perk.STRONG_LINE) * 0.05;
    }

    /** Рыбацкая удача: flat trophy-chance bonus added to the roll (+1%/rank). */
    public static double trophyChanceBonus(Player player) {
        return rank(player, Perk.ANGLERS_LUCK) * 0.01;
    }

    /** Умение: how much wider the strike QTE green zone is (+1%/rank). */
    public static double strikeZoneBonus(Player player) {
        return rank(player, Perk.FINESSE) * 0.01;
    }
}
