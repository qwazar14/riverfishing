package com.riverfishing.integration;

import com.riverfishing.engine.Season;
import com.riverfishing.platform.PlatformHelper;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Soft integration with Serene Seasons (§10.1). All access is reflective so the mod compiles
 * and runs without it; when the mod is absent {@link #getSeason} returns null and the engine
 * uses a season factor of 1.0 for everyone.
 */
public final class SeasonProvider {
    private static Boolean available;
    private static Method getSeasonState;
    private static Method getSeason;

    private SeasonProvider() {}

    private static boolean init() {
        if (available != null) return available;
        try {
            if (!PlatformHelper.isModLoaded("sereneseasons")) {
                return available = false;
            }
            Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
            getSeasonState = helper.getMethod("getSeasonState", Level.class);
            Class<?> state = Class.forName("sereneseasons.api.season.ISeasonState");
            getSeason = state.getMethod("getSeason");
            return available = true;
        } catch (Throwable t) {
            return available = false;
        }
    }

    /** @return the current season, or null if Serene Seasons is not present. */
    public static Season getSeason(Level level) {
        if (!init()) return null;
        try {
            Object stateObj = getSeasonState.invoke(null, level);
            Object seasonObj = getSeason.invoke(stateObj);
            String name = ((Enum<?>) seasonObj).name();
            return switch (name) {
                case "SPRING" -> Season.SPRING;
                case "SUMMER" -> Season.SUMMER;
                case "AUTUMN", "FALL" -> Season.AUTUMN;
                case "WINTER" -> Season.WINTER;
                default -> null;
            };
        } catch (Throwable t) {
            return null;
        }
    }
}
