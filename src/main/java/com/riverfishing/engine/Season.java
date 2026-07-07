package com.riverfishing.engine;

/** Season buckets (§10.1). Mapped from Serene Seasons if present, otherwise unused. */
public enum Season {
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn"),
    WINTER("winter");

    private final String jsonKey;

    Season(String jsonKey) { this.jsonKey = jsonKey; }

    public String jsonKey() { return jsonKey; }
}
