package com.riverfishing.groundbait;

import com.riverfishing.engine.Season;
import com.riverfishing.integration.SeasonProvider;
import com.riverfishing.item.IceAugerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * §groundbait-mix (0.8.0): how much the fish at this spot can actually eat right now.
 *
 * <p>This is the number that makes feeding a decision instead of a chore, and it is the one piece of
 * the feature taken straight from the real thing. A carp eats about 2.8% of its body weight a day at
 * 28 °C and about 0.01% at 6 °C — not a factor, <b>two orders of magnitude</b>. So the same jar of rich
 * groundbait that barely registers in a warm summer lake stuffs a spot solid under ice, and the classic
 * cold-water mistake is not "slightly too much", it is a dead swim.
 *
 * <p>The punishment needs no special case: {@code FeedZoneData.feed} divides the feed by this number, so
 * a small appetite turns an ordinary handful into a full spot on its own.
 *
 * <p>Nothing here is new bookkeeping. Temperature, ice and season are already read by the bite engine,
 * and how hard a place has been fished is already tracked per chunk. The three waters the design is
 * built around — a hammered town lake, a stocked pond, a wild lake — fall out of state the world
 * already keeps.
 */
public final class GroundbaitAppetite {

    private GroundbaitAppetite() {}

    /** Below this the water is winter-cold whatever the calendar says. */
    private static final float COLD_TEMPERATURE = 0.3f;
    private static final float WARM_TEMPERATURE = 0.9f;

    /**
     * 0..1 for the water at {@code pos}. 1 is a warm summer lake that will take everything you throw;
     * 0.08 is under the ice, where a spot wants a handful and nothing more.
     */
    public static double at(ServerLevel level, BlockPos pos) {
        float temperature = level.getBiome(pos).value().getBaseTemperature();

        // Biome first: a jungle river runs warm in every season, the tundra never does.
        double appetite = Mth.clamp((temperature - COLD_TEMPERATURE)
                / (WARM_TEMPERATURE - COLD_TEMPERATURE), 0.0, 1.0);
        appetite = 0.15 + 0.85 * appetite;

        // Season, when the player runs Serene Seasons. Without it this returns null and the biome
        // decides alone, which is the same answer the rest of the mod gives in that case.
        Season season = SeasonProvider.getSeason(level);
        if (season != null) {
            appetite *= switch (season) {
                case SUMMER -> 1.0;
                case SPRING -> 0.75;   // waking up, and spawning runs make them greedy
                case AUTUMN -> 0.70;   // feeding up, but the water is already dropping
                case WINTER -> 0.12;
            };
        }

        // Ice over the spot beats everything above it: whatever the biome or the calendar claims, water
        // under a lid is cold water, and this is where overfeeding actually kills a swim.
        if (IceAugerItem.isIce(level.getBlockState(pos.above()))) {
            appetite = Math.min(appetite, 0.08);
        }

        return Mth.clamp(appetite, 0.05, 1.0);
    }

    /** A word for the tooltip, so the number is not the only thing the player gets. */
    public static String describe(double appetite) {
        if (appetite >= 0.75) return "high";
        if (appetite >= 0.40) return "moderate";
        if (appetite >= 0.18) return "low";
        return "barely";
    }
}
