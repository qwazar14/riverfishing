package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
    /**
     * §cull (0.7.0): species an operator has removed from a region with the electrofisher. Kept beside the
     * stocking book rather than in a file of its own because they are the same statement about the same
     * region — one says "this lives here now", the other "this does not live here any more" — and a second
     * SavedData keyed the same way would be a second thing to keep in step.
     */
    private final Map<Long, Set<String>> culled = new HashMap<>();

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
        // A fish you physically put back is in the water, whatever the book said before: releasing one
        // lifts a cull. That keeps the world honest rather than leaving a species that visibly swims here
        // and cannot be caught, and it gives the operator an in-world undo.
        Set<String> c = culled.get(region);
        if (c != null) c.remove(species);
        setDirty();
    }

    /** §cull: is this species banned from this region? */
    public boolean isCulled(long region, String species) {
        Set<String> s = culled.get(region);
        return s != null && s.contains(species);
    }

    public void setCulled(long region, String species, boolean on) {
        if (on) {
            culled.computeIfAbsent(region, k -> new HashSet<>()).add(species);
            Set<String> stockedHere = regions.get(region);
            if (stockedHere != null) stockedHere.remove(species);   // it is not stocked here any more either
        } else {
            Set<String> s = culled.get(region);
            if (s != null) s.remove(species);
        }
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
        CompoundTag out = new CompoundTag();
        for (Map.Entry<Long, Set<String>> e : culled.entrySet()) {
            ListTag list = new ListTag();
            for (String s : e.getValue()) list.add(StringTag.valueOf(s));
            out.put(Long.toString(e.getKey()), list);
        }
        tag.put("Culled", out);
        return tag;
    }

    public static StockedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StockedData d = new StockedData();
        CompoundTag all = tag.getCompoundOrEmpty("Regions");
        for (String key : all.keySet()) {
            d.regions.put(Long.parseLong(key), new HashSet<>(all.getCompoundOrEmpty(key).keySet()));
        }
        CompoundTag out = tag.getCompoundOrEmpty("Culled");
        for (String key : out.keySet()) {
            ListTag list = out.getListOrEmpty(key);
            Set<String> s = new HashSet<>();
            for (int i = 0; i < list.size(); i++) s.add(list.getStringOr(i, ""));
            d.culled.put(Long.parseLong(key), s);
        }
        return d;
    }
}
