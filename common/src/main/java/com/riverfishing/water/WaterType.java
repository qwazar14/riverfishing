package com.riverfishing.water;

/**
 * Classified water-body type (§10.2). The key matches the JSON profile's "water_bodies" map.
 * A profile factor of 0 for a type means the fish does not live there.
 */
public enum WaterType {
    PUDDLE("puddle"),
    POND("pond"),
    LAKE("lake"),
    RIVER("river"),
    SWAMP("swamp"),
    SEA("sea"),
    NONE("none");

    private final String key;

    WaterType(String key) { this.key = key; }

    public String key() { return key; }
}
