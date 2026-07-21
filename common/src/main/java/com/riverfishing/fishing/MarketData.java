package com.riverfishing.fishing;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * §market (0.5.0): the dynamic fish market. Two forces move the fisherman's buy prices:
 *
 * <ul>
 *   <li><b>Glut</b> — every prime fish LANDED on the server saturates that species' market a little
 *       (+0.08, capped at 1.0); a glutted species pays down to ×0.5. It recovers ~15%/day — overfish
 *       the bream and the bream money dries up, spreading anglers across species (the economy-level
 *       twin of the per-chunk depletion).</li>
 *   <li><b>Order of the day</b> — one species per Minecraft day (deterministic rotation, same for the
 *       whole server) pays ×2.5 regardless of glut. The daily reason to go fish something else.</li>
 * </ul>
 */
public final class MarketData extends SavedData {
    private static final String NAME = "riverfishing_market";
    private static final double GLUT_PER_CATCH = 0.08;
    private static final double DAILY_RECOVERY = 0.85; // glut multiplier per day passed
    public static final double ORDER_MULT = 2.5;

    /** The daily-order rotation — liked, catchable species across the whole progression. */
    private static final String[] ORDER_POOL = {
            "bream", "perch", "pike", "roach", "crucian_carp", "carp", "zander", "trout",
            "bluegill", "largemouth_bass", "sabrefish", "mackerel", "herring", "cod", "seabass", "flounder"
    };

    private final Map<String, Double> glut = new HashMap<>();
    private long lastDay = -1;

    // §26.1: SavedData.Factory is gone — a codec-backed SavedDataType drives load/save now.
    private static final net.minecraft.world.level.saveddata.SavedDataType<MarketData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    Identifier.fromNamespaceAndPath("riverfishing", "market"),
                    MarketData::new,
                    CompoundTag.CODEC.xmap(t -> MarketData.load(t, null), d -> d.save(new CompoundTag(), null)),
                    null);

    public static MarketData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The species the fisherman pays ×2.5 for today (same for the whole server). */
    public static String orderOfTheDay(ServerLevel level) {
        long day = level.getServer().overworld().getOverworldClockTime() / 24000L;
        // A hash step so the rotation doesn't read as a fixed alphabetical cycle.
        int idx = (int) Math.floorMod(day * 31L + 7L, ORDER_POOL.length);
        return ORDER_POOL[idx];
    }

    /** A prime specimen of {@code species} was landed — its market saturates a little. */
    public void addSupply(String species) {
        decay();
        glut.merge(species, GLUT_PER_CATCH, Double::sum);
        glut.computeIfPresent(species, (k, v) -> Math.min(1.0, v));
        setDirty();
    }

    /** The fisherman's actual pay for {@code species} given a base price. Never below 1 emerald. */
    public int price(ServerLevel level, String species, int base) {
        decayWith(level);
        if (species.equals(orderOfTheDay(level))) {
            return Math.max(1, (int) Math.round(base * ORDER_MULT));
        }
        double g = glut.getOrDefault(species, 0.0);
        return Math.max(1, (int) Math.round(base * (1.0 - 0.5 * g)));
    }

    private void decay() {
        // Day is read lazily on the next level-aware call; plain adds just accumulate.
    }

    private void decayWith(ServerLevel level) {
        long day = level.getServer().overworld().getOverworldClockTime() / 24000L;
        if (lastDay < 0) lastDay = day;
        if (day > lastDay) {
            double k = Math.pow(DAILY_RECOVERY, day - lastDay);
            glut.replaceAll((s, v) -> v * k);
            glut.values().removeIf(v -> v < 0.01);
            lastDay = day;
            setDirty();
        }
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag g = new CompoundTag();
        glut.forEach(g::putDouble);
        tag.put("Glut", g);
        tag.putLong("LastDay", lastDay);
        return tag;
    }

    public static MarketData load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketData d = new MarketData();
        CompoundTag g = tag.getCompoundOrEmpty("Glut");
        for (String k : g.keySet()) d.glut.put(k, g.getDoubleOr(k, 0.0));
        d.lastDay = tag.getLongOr("LastDay", -1L);
        return d;
    }
}
