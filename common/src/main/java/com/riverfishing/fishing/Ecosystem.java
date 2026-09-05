package com.riverfishing.fishing;

import com.riverfishing.engine.BiteContext;
import com.riverfishing.engine.Calendar;
import com.riverfishing.engine.Season;
import com.riverfishing.fish.FishGroup;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * §f §breeding (0.9.0): a settled species changes the water it lives in, and so does what you build
 * on the bank. One table, read off {@link StockedData} for the region and {@link WaterUpgrades} for
 * the spot, applied onto a {@link BiteContext} AFTER its {@code speciesFactor} has been (re)set —
 * the composition wraps whatever function is there, so a second call on a fresh factor is one
 * application, and every other field written here is set from scratch (clarity, temperature) or
 * to a fixed value (bed), so calling it again is a no-op.
 *
 * <p>Why a table and not per-species JSON: nine effects, each a sentence a player can read on the
 * finder. A datapack field per effect per species would be ninety numbers nobody could check.
 */
public final class Ecosystem {
    private Ecosystem() {}

    /** Grass carp eat the weed these three live in. */
    private static final Set<String> WEED_FISH = Set.of("tench", "rudd", "crucian_carp", "golden_crucian");
    /** Hunters that need to SEE the prey — zander is a night fish and is deliberately not here. */
    private static final Set<String> SIGHT_HUNTERS = Set.of("pike", "asp", "perch", "chub", "rainbow_trout", "trout");
    /** The bottom-rooting carp that silt a sand or clay bed into mud. */
    private static final Set<String> BIG_CARP = Set.of("carp", "wild_carp", "mirror_carp");
    /** Predators big enough to thin the minnows and leave the bream more to eat. */
    private static final Set<String> BIG_PREDATORS = Set.of("pike", "zander", "catfish");
    private static final Set<String> SMALL_CYPRINIDS = Set.of("bleak", "verkhovka", "gudgeon", "roach", "rudd", "white_bream");

    /** Everything at this spot in one read, so apply/weightScale/frySurvival/describe cannot disagree. */
    private record Spot(boolean grassCarp, boolean silverCarp, boolean bigCarp, boolean bigPredator,
                        boolean aerator, boolean snags, boolean gravel, boolean warmOutflow, boolean feeder) {
        double clarity() {
            double c = 1.0;
            if (grassCarp) c += 0.15;
            if (silverCarp) c += 0.25;
            if (bigCarp) c -= 0.2;
            if (aerator) c += 0.1;
            return c;
        }
    }

    private static Spot spot(ServerLevel level, BlockPos pos) {
        StockedData st = StockedData.get(level);
        long region = StockedData.region(pos);
        Set<String> up = WaterUpgrades.at(level, pos);
        return new Spot(st.isStocked(region, "grass_carp"), st.isStocked(region, "silver_carp"),
                BIG_CARP.stream().anyMatch(s -> st.isStocked(region, s)),
                BIG_PREDATORS.stream().anyMatch(s -> st.isStocked(region, s)),
                up.contains("aerator"), up.contains("snags"), up.contains("gravel"),
                up.contains("warm_outflow"), up.contains("feeding_station"));
    }

    private static String group(Identifier id) {
        FishProfile p = FishProfileManager.get().byId(id);
        return p == null || p.group == null ? FishGroup.OTHER : p.group;
    }

    /** Call once the context's {@code speciesFactor} is in its final (pre-ecosystem) state. */
    public static void apply(ServerLevel level, BlockPos pos, BiteContext env) {
        Spot s = spot(level, pos);
        double clarity = s.clarity();
        env.clarity = clarity;

        if (s.bigCarp() && (env.bed == 1 || env.bed == 3)) env.bed = 4;   // sand / clay -> mud
        if (s.gravel()) env.bed = 2;
        // From the biome, not += : reEvaluate re-runs this every fifteen seconds on the same context.
        if (s.warmOutflow()) env.biomeTemperature = level.getBiome(pos).value().getBaseTemperature() + 0.2;
        if (s.feeder()) {
            env.inFeedZone = true;
            env.feedFreshness = Math.max(env.feedFreshness, 0.6);
        }

        // 0.9 + 0.3·1.0 is 1.2, so untouched water would hand every sight hunter +20% for nothing;
        // the term only exists once something has actually changed the clarity.
        double sight = clarity == 1.0 ? 1.0 : Math.max(0.8, Math.min(1.3, 0.9 + 0.3 * clarity));
        boolean summer = Calendar.season(level) == Season.SUMMER;
        ToDoubleFunction<Identifier> old = env.speciesFactor;
        env.speciesFactor = id -> {
            double f = old == null ? 1.0 : old.applyAsDouble(id);
            String p = id.getPath(), g = group(id);
            if (s.grassCarp() && WEED_FISH.contains(p)) f *= 0.8;
            if (SIGHT_HUNTERS.contains(p)) f *= sight;
            if (s.bigCarp() && FishGroup.SALMONID.equals(g)) f *= 0.7;
            if (s.bigPredator() && SMALL_CYPRINIDS.contains(p)) f *= 0.75;
            if (s.aerator() && summer) f *= 1.15;
            if (s.snags() && FishGroup.PREDATOR.equals(g)) f *= 1.2;
            return f;
        };
    }

    /** The weight roll: 1.1 for a cyprinid that is not minnow-sized when a big predator thins the minnows. */
    public static double weightScale(ServerLevel level, BlockPos pos, Identifier species) {
        if (!FishGroup.CYPRINID.equals(group(species)) || SMALL_CYPRINIDS.contains(species.getPath())) return 1.0;
        return spot(level, pos).bigPredator() ? 1.1 : 1.0;
    }

    /** Wild fry survival bonus, 0..0.3: cover to hide in and oxygen to breathe. */
    public static double frySurvival(ServerLevel level, BlockPos pos) {
        Spot s = spot(level, pos);
        double b = (s.snags() ? 0.15 : 0.0) + (s.aerator() ? 0.1 : 0.0);
        return Math.max(0.0, Math.min(0.3, b));
    }

    /** Lang-key tails of the effects active here, in table order — the finder carries these. */
    public static List<String> effects(ServerLevel level, BlockPos pos) {
        Spot s = spot(level, pos);
        List<String> out = new ArrayList<>();
        if (s.grassCarp()) out.add("grass_carp");
        if (s.silverCarp()) out.add("silver_carp");
        if (s.bigCarp()) out.add("carp");
        if (s.bigPredator()) out.add("predator");
        if (s.aerator()) out.add("aerator");
        if (s.snags()) out.add("snags");
        if (s.gravel()) out.add("gravel");
        if (s.warmOutflow()) out.add("warm_outflow");
        if (s.feeder()) out.add("feeding_station");
        return out;
    }

    /** One translatable line per active effect, {@code ecosystem.riverfishing.<effect>}. */
    public static List<Component> describe(ServerLevel level, BlockPos pos) {
        return effects(level, pos).stream()
                .map(k -> (Component) Component.translatable("ecosystem.riverfishing." + k)).toList();
    }
}
