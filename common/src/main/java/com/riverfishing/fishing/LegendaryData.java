package com.riverfishing.fishing;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
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
    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now.
    private static final net.minecraft.world.level.saveddata.SavedDataType<LegendaryData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    Identifier.fromNamespaceAndPath("riverfishing", "legendary"),
                    LegendaryData::new,
                    CompoundTag.CODEC.xmap(t -> LegendaryData.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);

    private final Set<String> caught = new HashSet<>();

    public static LegendaryData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isCaught(Identifier species) {
        return caught.contains(species.getPath());
    }

    public void markCaught(Identifier species) {
        caught.add(species.getPath());
        setDirty();
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag set = new CompoundTag();
        for (String s : caught) set.putBoolean(s, true);
        tag.put("Caught", set);
        return tag;
    }

    public static LegendaryData load(CompoundTag tag, HolderLookup.Provider registries) {
        LegendaryData d = new LegendaryData();
        d.caught.addAll(tag.getCompoundOrEmpty("Caught").keySet());
        return d;
    }
}
