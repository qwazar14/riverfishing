package com.riverfishing.engine;

import com.riverfishing.fish.FishProfile;
import com.riverfishing.integration.SeasonProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * §breeding: the year the fish live by.
 *
 * <p>Spawning is a WINDOW — a pike goes in early spring, a carp in early summer — and a window needs a
 * calendar to be measured against. Serene Seasons is one, but a mod about fish cannot make its central
 * mechanic depend on someone else's mod being installed, so this class carries its own: 96 world days,
 * four seasons of 24, each cut into thirds of 8 ({@link Sub}). Day 0 is early spring, because a fresh
 * world starting in the season fish are most active in is the friendliest first hour.
 *
 * <p>When Serene Seasons IS present it wins, read through {@link SeasonProvider}: the season and the
 * sub-season are SS's own words, and {@link #dayOfYear} is SS's position in its cycle scaled onto the
 * 96-day year — SS's seasons are quarters and its sub-seasons twelfths of the cycle, exactly like
 * ours, so the scaled day always lands in the season SS reports. SS's default year is 96 days too;
 * ponytail: with a longer SS year, {@link #daysUntil} counts in 96ths of a year rather than real
 * days — the checklist says "12 days" where SS would say 24. Multiply by the SS year length / 96 if
 * anyone ever asks for exact.
 *
 * <p>One source of truth: everything here is arithmetic over {@link #dayOfYear}, so a season and its
 * sub-season can never disagree with the day they were derived from.
 */
public final class Calendar {
    /** A third of a season — 8 days each; 24 per season; 96 per year. */
    public enum Sub { EARLY, MID, LATE }

    public static final int SUB_DAYS = 8, SEASON_DAYS = 24, YEAR_DAYS = 96;

    private Calendar() {}

    /** 0..95. Serene Seasons' cycle scaled to 96 when present, else the overworld day mod 96. */
    public static int dayOfYear(Level level) {
        int ss = SeasonProvider.sereneDayOfYear(level);
        if (ss >= 0) return ss % YEAR_DAYS;
        // The OVERWORLD's clock on the server: a nether portal must not reset the calendar. On the client
        // there is only one level, and its day time is the overworld's anyway.
        long day = (level.getServer() != null ? level.getServer().overworld() : level).getOverworldClockTime() / 24000L;
        return (int) Math.floorMod(day, (long) YEAR_DAYS);
    }

    /** Serene Seasons' season when present, else the own calendar's. Never null. */
    public static Season season(Level level) {
        Season ss = SeasonProvider.serene(level);
        return ss != null ? ss : Season.values()[dayOfYear(level) / SEASON_DAYS];
    }

    /** Serene Seasons' sub-season when present (MID if its API hides it), else the own calendar's. */
    public static Sub sub(Level level) {
        if (SeasonProvider.present()) {
            Sub ss = SeasonProvider.sereneSub(level);
            return ss != null ? ss : Sub.MID;
        }
        return Sub.values()[dayOfYear(level) % SEASON_DAYS / SUB_DAYS];
    }

    /**
     * Days until the window opens, 0 while inside it. {@code sub == null} means the whole season.
     * Modular over the year, so late winter asks "how long until early spring" and gets 8, not -88.
     */
    public static int daysUntil(Level level, Season s, Sub sub) {
        int start = s.ordinal() * SEASON_DAYS + (sub == null ? 0 : sub.ordinal() * SUB_DAYS);
        int length = sub == null ? SEASON_DAYS : SUB_DAYS;
        int sinceStart = Math.floorMod(dayOfYear(level) - start, YEAR_DAYS);
        return sinceStart < length ? 0 : YEAR_DAYS - sinceStart;
    }

    /** True while the species' spawning window is open. */
    public static boolean inWindow(Level level, FishProfile p) {
        return daysUntil(level, p.spawnSeason, p.spawnSub) == 0;
    }

    /**
     * "late spring". One key per (sub, season) pair rather than adjective + noun, because Russian and
     * Ukrainian decline the adjective by the season's gender ("поздняя весна" but "позднее лето") and
     * a pattern with two holes cannot say that. {@code sub == null} is just the season's name.
     */
    public static Component name(Season s, Sub sub) {
        if (sub == null) return Component.translatable("season.riverfishing." + s.jsonKey());
        return Component.translatable("calendar.riverfishing.name." + sub.name().toLowerCase() + "_" + s.jsonKey());
    }

    /** The season whose profile key this is ("spring"), or null. */
    public static Season seasonOf(String key) {
        for (Season s : Season.values()) if (s.jsonKey().equals(key)) return s;
        return null;
    }

    /** The sub whose profile key this is ("late"), or null — which for a profile means the whole season. */
    public static Sub subOf(String key) {
        for (Sub s : Sub.values()) if (s.name().equalsIgnoreCase(key)) return s;
        return null;
    }
}

// §ported26
