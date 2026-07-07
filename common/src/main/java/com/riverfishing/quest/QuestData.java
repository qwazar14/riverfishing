package com.riverfishing.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Which angler quests a player has already been REWARDED for (§quests), in the player's persistent NBT
 * (copied on death by {@code ModEvents}'s clone handler). Whether a quest is COMPLETE is derived live
 * from the journal records — this only stops a reward being handed out twice.
 */
public final class QuestData {
    public static final String TAG = "riverfishing_quests";

    private QuestData() {}

    public static boolean isRewarded(Player player, String questId) {
        return player.getPersistentData().getCompound(TAG).getBoolean(questId);
    }

    public static void markRewarded(Player player, String questId) {
        CompoundTag root = player.getPersistentData().getCompound(TAG);
        root.putBoolean(questId, true);
        player.getPersistentData().put(TAG, root);
    }
}
