package com.riverfishing.engine;

/** Weather buckets (§1.2 f_погода). */
public enum Weather {
    CLEAR("clear"),
    RAIN("rain"),
    THUNDER("thunder");

    private final String jsonKey;

    Weather(String jsonKey) { this.jsonKey = jsonKey; }

    public String jsonKey() { return jsonKey; }
}
