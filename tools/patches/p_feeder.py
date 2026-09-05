# -*- coding: utf-8 -*-
"""§feeder-fill: the feeding station shows how much groundbait is left in it.

    py -X utf8 tools/patches/p_feeder.py <root> [1211|1201|26]
    py -X utf8 tools/gen_feeding_station.py <root>      # …and the art the models switch between

The station holds WaterUpgrades.MAX_CHARGES of groundbait and spends one a world day, and none of that
was visible: the counter lives in a SavedData keyed by BlockPos, the block had no state at all, and the
only way to know whether a station was still feeding was to walk up and right-click it.

So the count is mirrored onto a blockstate. Four steps, not nine — a player reads a silhouette across
the water, not a number — and the window in the new texture is dark at 0 and full of meal at 3.

Three things had to line up for that:

  * a FeedingStationBlock, because WaterUpgradeBlock is five blocks and a property added there would
    land on the aerator and the gravel bed too, and Block's constructor calls createBlockStateDefinition
    before a subclass has assigned anything it could branch on;
  * the groundbait interaction pushes the new count out — it has a level in its hand already;
  * the DECAY does not, because it is settled lazily inside the SavedData, which has no level to tell.
    Rather than hand the SavedData a way to reach back into the world, the block asks: a random tick
    finds a given block about once a minute in chunks a player is near, and the charge moves once a day.
    That is the one deliberate looseness here — a station can read one step stale for up to a minute
    after its chunk loads.

It also fixes a real bug on the way past: the "already full" check read the raw counter instead of
settling it first, so a station last topped up a week ago reported itself full and refused bait until
somebody happened to cast near it.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")


def rd(p):
    return io.open(p, encoding="utf-8").read()


def wr(p, s):
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---- 1. the block: one property, and the two places it is kept honest ------------------------------
# Identical in all three dialects: randomTick, IntegerProperty, StateDefinition.Builder and setBlock
# have the same shape in 1.20.1, 1.21.1 and 26.x, and nothing here touches use/useItemOn.
BLOCK = '''package com.riverfishing.block;

import com.riverfishing.fishing.WaterUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * §feeder-fill: the feeding station — the one water upgrade you can read from the bank.
 *
 * <p>Its groundbait lives in {@link WaterUpgrades}, a SavedData keyed by position: a counter and a day
 * stamp, no block entity. That is still the right place for it, but it meant the block showed nothing:
 * a station brimming with bait and one that ran dry a week ago were the same six texels of plank. So
 * the count is MIRRORED here, onto a state the model can switch on.
 *
 * <p>{@link #FILL} is deliberately coarse — four steps out of {@link WaterUpgrades#MAX_CHARGES} —
 * because a player reads a silhouette across the water rather than a number, and four steps keep the
 * blockstate file to four lines.
 *
 * <p>Why a class of its own instead of a flag on {@link WaterUpgradeBlock}: that class is five blocks,
 * and a property added there lands on all five — every one of their blockstate files would have to
 * enumerate a value that means nothing to them. It cannot be branched on inside
 * createBlockStateDefinition either, because Block's constructor calls that before a subclass has
 * assigned its kind.
 */
public class FeedingStationBlock extends WaterUpgradeBlock {
    /** How full it LOOKS: 0 empty, 1 nearly out, 2 half (one jar), 3 brimming. */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);

    public FeedingStationBlock(String id, String kind, boolean inWater, Properties properties) {
        super(id, kind, inWater, properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(WATERLOGGED, Boolean.FALSE).setValue(FILL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILL);
    }

    /** Charges to the four steps the model draws: 0, 1-3, 4-6, 7-8. */
    public static int fill(int charges) {
        return Math.min(3, (charges + 2) / 3);
    }

    /** Put the ledger's count on the block. Harmless on a state that has no FILL — /setblock leaves those. */
    public static void sync(ServerLevel level, BlockPos pos, int charges) {
        BlockState state = level.getBlockState(pos);
        int step = fill(charges);
        if (!state.hasProperty(FILL) || state.getValue(FILL) == step) return;
        level.setBlock(pos, state.setValue(FILL, step), 3);
    }

    /**
     * The other half of keeping it honest. Loading is easy — the interaction has a level in its hand —
     * but the decay is settled lazily inside a SavedData with no level to tell, so nothing pushes the
     * drop out to the world. Rather than give the SavedData a way to reach back, the block asks. A
     * random tick finds a given block about once a minute, only in chunks a player is near, and costs
     * one HashMap lookup; the charge itself moves once a world day.
     *
     * <p>ponytail: so a station can read one step stale for up to a minute after its chunk loads. Give
     * it a block entity, or a scheduled tick re-armed on load, if that ever matters.
     */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        sync(level, pos, WaterUpgrades.get(level).settle(pos));
    }
}
'''
p = J + "block/FeedingStationBlock.java"
if not os.path.exists(p):
    wr(p, BLOCK)
    print("  FeedingStationBlock: a fill property, and a random tick to keep it true")

# ---- 2. the ledger: settling one entry's decay, factored out of the walk over all of them -----------
p = J + "fishing/WaterUpgrades.java"
s = rd(p)
if "feeder-fill" not in s:
    old = """            Entry e = me.getValue();
            if (e.charges > 0 && day > e.lastDay) {           // one charge per world day, settled on read
                e.charges = (int) Math.max(0, e.charges - (day - e.lastDay));
                e.lastDay = day;
                data.setDirty();
            }
            if (com.riverfishing.block.WaterUpgradeBlock.FEEDING_STATION.equals(e.kind) && e.charges <= 0) continue;"""
    assert old in s, "the decay moved out of WaterUpgrades.at"
    s = s.replace(old, """            Entry e = me.getValue();
            int charges = data.settle(e);                     // one charge per world day, settled on read
            if (com.riverfishing.block.WaterUpgradeBlock.FEEDING_STATION.equals(e.kind) && charges <= 0) continue;""", 1)
    # …the local it used is now the only reader of nothing.
    s = s.replace("""        WaterUpgrades data = get(level);
        long day = data.day;
        Set<String> kinds""", """        WaterUpgrades data = get(level);
        Set<String> kinds""", 1)

    old = """    public int charges(BlockPos pos) {"""
    assert old in s, "WaterUpgrades.charges moved"
    s = s.replace(old, """    /**
     * §feeder-fill: settle one station's day-decay and hand back what is left. {@link #at} does this
     * for every entry it walks; FeedingStationBlock asks about its own, so the block can show the count.
     */
    public int settle(BlockPos pos) {
        Entry e = entries.get(pos.asLong());
        return e == null ? 0 : settle(e);
    }

    private int settle(Entry e) {
        if (e.charges > 0 && day > e.lastDay) {
            e.charges = (int) Math.max(0, e.charges - (day - e.lastDay));
            e.lastDay = day;
            setDirty();
        }
        return e.charges;
    }

    public int charges(BlockPos pos) {""", 1)
    wr(p, s)
    print("  WaterUpgrades: settle(pos), and at() uses it instead of its own copy")

# ---- 3. the interaction: settle before refusing, and push the new count out -------------------------
p = J + "block/WaterUpgradeBlock.java"
s = rd(p)
if "feeder-fill" not in s:
    old = "        if (data.charges(pos) >= WaterUpgrades.MAX_CHARGES) {"
    assert old in s, "the full check moved"
    s = s.replace(old, """        // §feeder-fill: settle the decay first — a station last topped up a week ago used to
        // report itself full and refuse the jar until somebody happened to cast near it.
        if (data.settle(pos) >= WaterUpgrades.MAX_CHARGES) {""", 1)
    old = "        data.load(pos, CHARGES_PER_JAR);"
    assert old in s, "the load call moved"
    s = s.replace(old, "        data.load(pos, CHARGES_PER_JAR);\n"
                       "        FeedingStationBlock.sync(sl, pos, data.charges(pos));   // §feeder-fill: the window fills", 1)
    wr(p, s)
    print("  WaterUpgradeBlock: the jar settles the count first, then shows it")

# ---- 4. the registry: the station is its own block now, and it random-ticks -------------------------
p = J + "registry/ModBlocks.java"
s = rd(p)
old = 'new com.riverfishing.block.WaterUpgradeBlock("feeding_station"'
if old in s:
    i = s.index(old)
    s = s[:i] + s[i:].replace("WaterUpgradeBlock", "FeedingStationBlock", 1)
    j = s.index(old.replace("WaterUpgradeBlock", "FeedingStationBlock"))
    tail = ".sound(SoundType.WOOD)))"
    k = s.index(tail, j)
    s = s[:k] + ".sound(SoundType.WOOD).randomTicks()))" + s[k + len(tail):]
    wr(p, s)
    print("  ModBlocks: FEEDING_STATION is a FeedingStationBlock, and it random-ticks")
print("done (%s)" % D)
