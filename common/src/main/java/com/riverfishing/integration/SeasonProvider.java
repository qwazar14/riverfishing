package com.riverfishing.integration;

import com.riverfishing.engine.Calendar;
import com.riverfishing.engine.Season;
import com.riverfishing.platform.PlatformHelper;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Soft integration with Serene Seasons (§10.1). All access is reflective so the mod compiles
 * and runs without it. §breeding-A: when the mod is absent {@link #getSeason} answers from
 * {@link Calendar}'s own 96-day year, so the bite engine, the order board and the spawning windows
 * all see seasons whether or not Serene Seasons is installed — and the SAME seasons when it is.
 */
public final class SeasonProvider {
    private static Boolean available;
    private static Method getSeasonState;
    private static Method getSeason;
    // §breeding-A: optional — an older SS without them still gives us the season.
    private static Method getSubSeason, getCycleTicks, getCycleDuration;

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
            // §breeding-A: each optional method in its own try — one missing must not cost the season.
            try { getSubSeason = state.getMethod("getSubSeason"); } catch (Throwable ignored) {}
            try {
                getCycleTicks = state.getMethod("getSeasonCycleTicks");
                getCycleDuration = state.getMethod("getSeasonCycleDuration");
            } catch (Throwable ignored) { getCycleTicks = getCycleDuration = null; }
            return available = true;
        } catch (Throwable t) {
            return available = false;
        }
    }

    /** §breeding-A: Serene Seasons' season when present and readable, else {@link Calendar}'s own. Never null. */
    public static Season getSeason(Level level) {
        Season s = serene(level);
        return s != null ? s : Calendar.season(level);
    }

    /** True when Serene Seasons is loaded and its API resolved. */
    public static boolean present() {
        return init();
    }

    /** @return Serene Seasons' season, or null if it is not present (or the read failed). */
    public static Season serene(Level level) {
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

    /**
     * §breeding-A: SS's sub-season (EARLY_SPRING, MID_SUMMER, ...) folded to its first word, or null when SS
     * is absent, too old to say, or names one we do not know.
     */
    public static Calendar.Sub sereneSub(Level level) {
        if (!init() || getSubSeason == null) return null;
        try {
            String name = ((Enum<?>) getSubSeason.invoke(getSeasonState.invoke(null, level))).name();
            return Calendar.subOf(name.substring(0, Math.max(0, name.indexOf('_'))));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * §breeding-A: where SS is in its year, scaled onto Calendar's 96 days; -1 when SS is absent.
     *
     * <p>The cycle's ticks over its duration is the one ratio SS exposes that needs no knowledge of
     * its day length or sub-season length (both are config). Its seasons are quarters and its
     * sub-seasons twelfths of the cycle, so floor(96 * ratio) / 24 is exactly SS's season and
     * (floor(96 * ratio) % 24) / 8 exactly its sub-season — the scaled day cannot contradict them.
     * Without those methods the middle of the reported sub-season is the honest answer.
     */
    public static int sereneDayOfYear(Level level) {
        if (!init()) return -1;
        try {
            Object state = getSeasonState.invoke(null, level);
            if (getCycleTicks != null) {
                long ticks = ((Number) getCycleTicks.invoke(state)).longValue();
                long total = ((Number) getCycleDuration.invoke(state)).longValue();
                if (total > 0) return (int) Math.floorMod(ticks * Calendar.YEAR_DAYS / total, (long) Calendar.YEAR_DAYS);
            }
            Season s = serene(level);
            Calendar.Sub sub = sereneSub(level);
            if (s == null) return -1;
            return s.ordinal() * Calendar.SEASON_DAYS
                    + (sub == null ? Calendar.SEASON_DAYS / 2 : sub.ordinal() * Calendar.SUB_DAYS + Calendar.SUB_DAYS / 2);
        } catch (Throwable t) {
            return -1;
        }
    }
}
