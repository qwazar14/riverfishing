package com.riverfishing.engine;

/** Time-of-day buckets (§1.2 f_время). Derived from the level's day time. */
public enum TimeOfDay {
    DAWN("dawn"),
    DAY("day"),
    DUSK("dusk"),
    NIGHT("night");

    private final String jsonKey;

    TimeOfDay(String jsonKey) { this.jsonKey = jsonKey; }

    public String jsonKey() { return jsonKey; }

    /** Maps a vanilla day time (0..24000) to a bucket. */
    public static TimeOfDay fromDayTime(long dayTime) {
        long t = ((dayTime % 24000) + 24000) % 24000;
        if (t >= 23000 || t < 1000) return DAWN;   // around sunrise
        if (t < 11000) return DAY;
        if (t < 13500) return DUSK;                 // around sunset
        return NIGHT;
    }
}
