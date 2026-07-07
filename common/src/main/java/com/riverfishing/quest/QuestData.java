package com.riverfishing.quest;

import com.riverfishing.fishing.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Which angler quests a player has already been REWARDED for (§quests), in the cross-loader
 * {@link PlayerData} store (§multiloader). Whether a quest is COMPLETE is derived live from the journal
 * records — this only stops a reward being handed out twice. Survives death via {@link PlayerData}.
 */
public final class QuestData {
    public static final String TAG = "riverfishing_quests";

    private QuestData() {}

    public static boolean isRewarded(Player player, String questId) {
        return PlayerData.root(player).getCompound(TAG).getBoolean(questId);
    }

    public static void markRewarded(Player player, String questId) {
        CompoundTag root = PlayerData.root(player).getCompound(TAG);
        root.putBoolean(questId, true);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }
}
