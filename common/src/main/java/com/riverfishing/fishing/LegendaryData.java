package com.riverfishing.fishing;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * §legendary (0.5.0): the server-wide book of caught legendaries. Each species with a "legendary"
 * block in its profile hides ONE named one-of-a-kind specimen per server — once somebody lands it,
 * it is gone for everyone, forever (that is the point: the catch is a server event). Stored on the
 * overworld like the other mod-wide books.
 */
public final class LegendaryData extends SavedData {
    private static final String NAME = "riverfishing_legendary";

    private final Set<String> caught = new HashSet<>();

    public static LegendaryData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LegendaryData::new, LegendaryData::load,
                        (net.minecraft.util.datafix.DataFixTypes) null), NAME);
    }

    public boolean isCaught(ResourceLocation species) {
        return caught.contains(species.getPath());
    }

    public void markCaught(ResourceLocation species) {
        caught.add(species.getPath());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String s : caught) list.add(StringTag.valueOf(s));
        tag.put("Caught", list);
        return tag;
    }

    public static LegendaryData load(CompoundTag tag, HolderLookup.Provider registries) {
        LegendaryData d = new LegendaryData();
        ListTag list = tag.getList("Caught", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) d.caught.add(list.getString(i));
        return d;
    }
}
