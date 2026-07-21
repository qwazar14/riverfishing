package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * §stocking (0.5.0): the book of released fish. A caught fish thrown back into water joins that
 * water's community (§community) for good — the way a server stocks a pond with a species the
 * seed didn't put there. Keyed by the same ~128-block water region the community hash uses.
 */
public final class StockedData extends SavedData {
    private static final String NAME = "riverfishing_stocked";

    private final Map<Long, Set<String>> regions = new HashMap<>();

    /** The ~128-block community region a position belongs to (shared with §community's hash). */
    public static long region(BlockPos pos) {
        return (((long) (pos.getX() >> 7)) << 32) ^ ((pos.getZ() >> 7) & 0xFFFFFFFFL);
    }

    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now.
    private static final net.minecraft.world.level.saveddata.SavedDataType<StockedData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("riverfishing", "stocked"),
                    StockedData::new,
                    CompoundTag.CODEC.xmap(t -> StockedData.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);

    public static StockedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isStocked(long region, String species) {
        Set<String> s = regions.get(region);
        return s != null && s.contains(species);
    }

    public void markStocked(long region, String species) {
        regions.computeIfAbsent(region, k -> new HashSet<>()).add(species);
        setDirty();
    }

    // §26.1: species stored as a compound of boolean keys (the ListTag string API changed;
    // no pre-0.5.0 saves exist on this branch, so the format is free to differ).
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<Long, Set<String>> e : regions.entrySet()) {
            CompoundTag set = new CompoundTag();
            for (String s : e.getValue()) set.putBoolean(s, true);
            all.put(Long.toString(e.getKey()), set);
        }
        tag.put("Regions", all);
        return tag;
    }

    public static StockedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StockedData d = new StockedData();
        CompoundTag all = tag.getCompoundOrEmpty("Regions");
        for (String key : all.keySet()) {
            d.regions.put(Long.parseLong(key), new HashSet<>(all.getCompoundOrEmpty(key).keySet()));
        }
        return d;
    }
}
