package com.riverfishing.engine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Barometric pressure (§weather-pressure). Minecraft has no atmosphere, so the pressure is SYNTHESISED
 * deterministically from the world seed and game time — a smooth "synoptic" wander between roughly 991
 * and 1035 hPa. It is decoupled from the vanilla sky (which keeps its own {@link Weather} factor): the
 * barometer LEADS the weather, exactly as a real angler reads it.
 *
 * <p>What matters to the fish is mostly the TREND. A falling glass (an approaching front) triggers heavy
 * feeding; a steady bluebird high slows everything down; a deep, settled low is mediocre. The absolute
 * value nudges on top: fish feed best around a slightly-low ~1008 hPa.
 *
 * <p>Everything is a pure function of {@code (seed, gameTime)} — no saved state, identical on client and
 * server, so a barometer item and the bite engine always agree.
 */
public final class BarometricPressure {
    /** Sea-level reference; the wander swings +/- {@link #SWING} around it. */
    public static final double BASE_HPA = 1013.0;
    private static final double SWING = 22.0;
    /** Trend window: pressure change over the last 3 in-game hours (1000 ticks/hour). */
    private static final long TREND_WINDOW = 3000L;

    private BarometricPressure() {}

    /** The raw synoptic wander in [-1, 1], layered sines phased by the world seed. */
    private static double wander(long gameTime, long seed) {
        double days = gameTime / 24000.0;
        double phase = ((seed % 100000L) / 100000.0) * Math.PI * 2.0;
        double v = 0.55 * Math.sin(days / 1.6 * Math.PI * 2.0 + phase)
                 + 0.30 * Math.sin(days / 3.7 * Math.PI * 2.0 + phase * 1.7)
                 + 0.15 * Math.sin(days / 0.8 * Math.PI * 2.0 + phase * 2.3);
        return Mth.clamp(v, -1.0, 1.0);
    }

    /** Current pressure in hPa (~991..1035). */
    public static double hPa(ServerLevel level) {
        return hPaAt(level.getGameTime(), level.getSeed());
    }

    public static double hPaAt(long gameTime, long seed) {
        return BASE_HPA + wander(gameTime, seed) * SWING;
    }

    /** Change over the last {@link #TREND_WINDOW} (positive = rising, negative = falling), in hPa. */
    public static double trend(ServerLevel level) {
        long t = level.getGameTime();
        return hPaAt(t, level.getSeed()) - hPaAt(Math.max(0, t - TREND_WINDOW), level.getSeed());
    }

    /**
     * The bite multiplier (§weather-pressure), ~0.70..1.35. Driven mostly by the trend (a falling glass
     * feeds; a rising one shuts down), with a gentle absolute bell that peaks just below 1010 hPa.
     */
    public static double biteFactor(ServerLevel level) {
        double hpa = hPa(level);
        double tr = trend(level);
        double trendFactor = Mth.clamp(1.0 - tr * 0.045, 0.85, 1.28); // falling (tr<0) boosts
        double d = hpa - 1008.0;
        double absFactor = Mth.clamp(1.05 - (d * d) * 0.00035, 0.82, 1.05);
        return Mth.clamp(trendFactor * absFactor, 0.70, 1.35);
    }

    /** -1 falling, 0 steady, +1 rising (|trend| < 1 hPa over the window counts as steady). */
    public static int trendSign(ServerLevel level) {
        double tr = trend(level);
        if (tr <= -1.0) return -1;
        if (tr >= 1.0) return 1;
        return 0;
    }

    /**
     * A qualitative bite outlook key for the fish finder (§weather-pressure): jumps straight from the
     * {@link #biteFactor} so the UI never disagrees with the engine.
     */
    public static String outlookKey(ServerLevel level) {
        double f = biteFactor(level);
        if (f >= 1.18) return "great";
        if (f >= 1.05) return "good";
        if (f >= 0.92) return "fair";
        return "poor";
    }
}
