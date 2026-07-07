package com.riverfishing.component;

/**
 * Rigs / оснастки (§3.4). Mass drives casting (§4); leader-bearing rigs protect
 * against pike/zander bite-offs (§2.2 note 2). Hook count is informational for now.
 */
public enum RigType {
    //         jsonKey        massG hooks leader
    PRIMITIVE ("primitive",    4,   1,   false),
    FLOAT_LIGHT("float_light", 6,   1,   false),
    FLOAT     ("float",        7,   1,   false),
    WINTER    ("winter",       3,   1,   false),
    FEEDER    ("feeder",      30,   1,   false),
    FLAT_FEEDER("flat_feeder",40,   1,   false),
    GROUND    ("ground",      28,   1,   false),
    GRUSHA    ("grusha",      55,   3,   false),
    CARP      ("carp",        65,   1,   false),
    PREDATOR  ("predator",    14,   1,   true),
    CATFISH   ("catfish",     95,   1,   true);

    private final String jsonKey;
    private final double massGrams;
    private final int hookCount;
    private final boolean hasLeader;

    RigType(String jsonKey, double massGrams, int hookCount, boolean hasLeader) {
        this.jsonKey = jsonKey;
        this.massGrams = massGrams;
        this.hookCount = hookCount;
        this.hasLeader = hasLeader;
    }

    public String jsonKey() { return jsonKey; }
    public double massGrams() { return massGrams; }
    public int hookCount() { return hookCount; }
    public boolean hasLeader() { return hasLeader; }
}
