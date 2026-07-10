package com.riverfishing.fishing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-loader per-player persistent store (§multiloader). Replaces Forge's {@code player.getPersistentData()}
 * — which Fabric has no equivalent for — with a level {@link SavedData} on the overworld, keyed by player
 * UUID. Survives death and dimension changes (unlike a per-entity capability), so the journal/quests/skills
 * carry over automatically (that's why the old {@code PlayerEvent.Clone} copy was dropped).
 *
 * <p>{@link #root(Player)} returns the live per-player root {@link CompoundTag}; call {@link #markDirty(Player)}
 * after mutating it so the store is saved.
 */
public final class PlayerData extends SavedData {
    private static final String NAME = "riverfishing_players";

    private final Map<UUID, CompoundTag> players = new HashMap<>();

    /** The per-player root NBT (the drop-in for {@code player.getPersistentData()}). Empty tag off-server. */
    public static CompoundTag root(Player player) {
        if (!(player instanceof ServerPlayer sp) || sp.getServer() == null) {
            return new CompoundTag();
        }
        return store(sp.getServer()).players.computeIfAbsent(sp.getUUID(), u -> new CompoundTag());
    }

    /** Flag the store dirty after a write to a player's root. */
    public static void markDirty(Player player) {
        if (player instanceof ServerPlayer sp && sp.getServer() != null) {
            store(sp.getServer()).setDirty();
        }
    }

    private static PlayerData store(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(PlayerData::new, PlayerData::load, (net.minecraft.util.datafix.DataFixTypes) null), NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((uuid, data) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("UUID", uuid);
            e.put("Data", data);
            list.add(e);
        });
        tag.put("Players", list);
        return tag;
    }

    public static PlayerData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        PlayerData d = new PlayerData();
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            d.players.put(e.getUUID("UUID"), e.getCompound("Data"));
        }
        return d;
    }
}
