package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * §g §breeding (0.9.0): where the water-body upgrades stand, per dimension.
 *
 * <p>A cast asks "what has been built around this swim?" and the answer has to come without scanning
 * 49x13x49 blocks per bite, so the blocks are recorded when placed (ModEvents PLACE) and forgotten when
 * broken (BREAK). {@link #at} also checks the block is still there, because pistons and creepers do not
 * fire BREAK for the player — a stale entry heals itself the first time anyone fishes near it.
 *
 * <p>The feeding station's charges are here too rather than in a block entity: it is one counter and a
 * day stamp, decayed lazily when read. No ticker, nothing to sync.
 */
public final class WaterUpgrades extends SavedData {
    private static final String NAME = "riverfishing_water_upgrades";
    /** How far an upgrade reaches — a swim's worth, not a lake's. */
    public static final int RANGE_H = 24;
    public static final int RANGE_V = 6;
    public static final int MAX_CHARGES = 8;

    private static final class Entry {
        String kind;
        int charges;
        long lastDay;
    }

    /** packed BlockPos -> the upgrade there. */
    private final Map<Long, Entry> entries = new HashMap<>();

    /** The world day as of the last {@link #get}: the SavedData has no level of its own to ask. */
    private long day;

    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now.
    private static final net.minecraft.world.level.saveddata.SavedDataType<WaterUpgrades> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("riverfishing", NAME),
                    WaterUpgrades::new,
                    CompoundTag.CODEC.xmap(t -> WaterUpgrades.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);

    public static WaterUpgrades get(ServerLevel level) {
        WaterUpgrades d = level.getDataStorage().computeIfAbsent(TYPE);
        d.day = level.getOverworldClockTime() / 24000L;
        return d;
    }

    public void put(BlockPos pos, String kind) {
        Entry e = entries.computeIfAbsent(pos.asLong(), k -> new Entry());
        e.kind = kind;
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos.asLong()) != null) setDirty();
    }

    public int charges(BlockPos pos) {
        Entry e = entries.get(pos.asLong());
        return e == null ? 0 : e.charges;
    }

    /** Add groundbait charges, capped; the decay clock restarts today so the first charge lasts a full day. */
    public void load(BlockPos pos, int n) {
        Entry e = entries.get(pos.asLong());
        if (e == null) return;
        e.charges = Math.min(MAX_CHARGES, e.charges + n);
        e.lastDay = day;
        setDirty();
    }

    /**
     * The kinds of upgrade within reach of a water block: "aerator", "snags", "gravel", "warm_outflow",
     * "feeding_station" — the last only while it has groundbait in it.
     *
     * <p>ponytail: linear over every upgrade in the dimension, once per bite roll. A server with a few
     * hundred of them costs nothing; index by chunk if it ever gets into the thousands.
     */
    public static Set<String> at(ServerLevel level, BlockPos waterPos) {
        WaterUpgrades data = get(level);
        long day = data.day;
        Set<String> kinds = new HashSet<>();
        Iterator<Map.Entry<Long, Entry>> it = data.entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> me = it.next();
            BlockPos pos = BlockPos.of(me.getKey());
            if (Math.abs(pos.getX() - waterPos.getX()) > RANGE_H
                    || Math.abs(pos.getZ() - waterPos.getZ()) > RANGE_H
                    || Math.abs(pos.getY() - waterPos.getY()) > RANGE_V) continue;
            if (level.hasChunkAt(pos)
                    && !(level.getBlockState(pos).getBlock() instanceof com.riverfishing.block.WaterUpgradeBlock)) {
                it.remove();                                  // blown up, pushed, or /setblock'd away
                data.setDirty();
                continue;
            }
            Entry e = me.getValue();
            if (e.charges > 0 && day > e.lastDay) {           // one charge per world day, settled on read
                e.charges = (int) Math.max(0, e.charges - (day - e.lastDay));
                e.lastDay = day;
                data.setDirty();
            }
            if (com.riverfishing.block.WaterUpgradeBlock.FEEDING_STATION.equals(e.kind) && e.charges <= 0) continue;
            kinds.add(e.kind);
        }
        return kinds;
    }

    // ---- persistence ---------------------------------------------------------------------------

    public static WaterUpgrades load(CompoundTag tag, HolderLookup.Provider registries) {
        WaterUpgrades d = new WaterUpgrades();
        ListTag list = tag.getListOrEmpty("upgrades");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            Entry e = new Entry();
            e.kind = t.getStringOr("k", "");
            e.charges = t.getIntOr("c", 0);
            e.lastDay = t.getLongOr("d", 0L);
            d.entries.put(t.getLongOr("p", 0L), e);
        }
        return d;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> me : entries.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", me.getKey());
            t.putString("k", me.getValue().kind);
            t.putInt("c", me.getValue().charges);
            t.putLong("d", me.getValue().lastDay);
            list.add(t);
        }
        tag.put("upgrades", list);
        return tag;
    }
}
