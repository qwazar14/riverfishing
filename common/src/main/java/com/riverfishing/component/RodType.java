package com.riverfishing.component;

/**
 * Rod blanks (Â§3.1). Each rod is its own item; the reel/line/rig/hook it carries
 * are stored in the rod's NBT. Cast-weight range mirrors the GDD's "ÑÐµÑÑ" concept (Â§3.4):
 * a rig whose mass falls inside the range gets a distance/accuracy bonus.
 */
public enum RodType {
    //        jsonKey       base  takesReel minReel maxReel castMin castMax longRange
    STICK     ("stick",      6,   false,    0,      0,      2,      25,     false),
    BAMBOO    ("bamboo",     8,   false,    0,      0,      2,      30,     false),
    POLE      ("pole",      11,   false,    0,      0,      2,      30,     false),
    // Short reel-less winter (mormyshka/nod) rod (Â§ice-fishing): fished vertically through an ice hole.
    WINTER    ("winter",     4,   false,    0,      0,      1,      12,     false),
    ULTRALIGHT("ultralight",14,   true,     1000,   2000,   1,      10,     false),
    SPINNING  ("spinning",  17,   true,     2000,   4000,   3,      35,     true),
    FEEDER    ("feeder",    21,   true,     3000,   5000,   20,     90,     true),
    BOTTOM    ("bottom",    23,   true,     5000,   7000,   40,     160,    true),
    CARP      ("carp",      25,   true,     5000,   7000,   60,     220,    true),
    // Â§sea-tackle (0.5.0): the saltwater tier. The freshwater ladder tops out at 7 kg of drag and a
    // 220 g cast â ocean fish are an order of magnitude heavier, so sea gear is a GATE, not flavour.
    SURF      ("surf",      28,   true,     6000,   8000,   80,     250,    true),
    SEA_SPIN  ("sea_spin",  19,   true,     5000,   9000,   20,     120,    true),
    BOAT      ("boat",      16,   true,     8000,   12000,  100,    400,    false),
    // §trolling (0.5.0): the towing rod — fished from a MOVING boat, not by casting.
    TROLLING  ("trolling",  12,   true,     10000,  14000,  150,    600,    false);

    private final String jsonKey;
    private final double baseDistance;
    private final boolean takesReel;
    private final int minReel;
    private final int maxReel;
    private final double castWeightMin;
    private final double castWeightMax;
    private final boolean longRange;

    RodType(String jsonKey, double baseDistance, boolean takesReel, int minReel, int maxReel,
            double castWeightMin, double castWeightMax, boolean longRange) {
        this.jsonKey = jsonKey;
        this.baseDistance = baseDistance;
        this.takesReel = takesReel;
        this.minReel = minReel;
        this.maxReel = maxReel;
        this.castWeightMin = castWeightMin;
        this.castWeightMax = castWeightMax;
        this.longRange = longRange;
    }

    public String jsonKey() { return jsonKey; }
    public double baseDistance() { return baseDistance; }
    public boolean takesReel() { return takesReel; }
    public int minReel() { return minReel; }
    public int maxReel() { return maxReel; }
    public double castWeightMin() { return castWeightMin; }
    public double castWeightMax() { return castWeightMax; }
    /** Long-range methods (feeder/bottom/carp/spinning) are blocked on narrow water (Â§4.1). */
    public boolean longRange() { return longRange; }

    public boolean acceptsReelSize(int size) {
        return takesReel && size >= minReel && size <= maxReel;
    }

    /** Which fishing flow this rod uses (Module 1). */
    public RodClass rodClass() {
        return switch (this) {
            case SPINNING, ULTRALIGHT, SEA_SPIN, TROLLING -> RodClass.ACTIVE;
            case FEEDER, BOTTOM, CARP, SURF, BOAT -> RodClass.BOTTOM;
            case STICK, BAMBOO, POLE, WINTER -> RodClass.FLOAT;
        };
    }

    /** Spinning-style rods that are fished by retrieving the lure (hold right-click). */
    public boolean activeRetrieve() {
        return rodClass() == RodClass.ACTIVE;
    }

    /**
     * The rig that lives permanently inside this rod, or {@code null} if the rod still takes swappable
     * rigs (Â§closed-slots). Float rods and lure rods each have exactly one natural tackle, so their rig
     * is built in and the assembly GUI shows its slots inline (float/hook/bait, leader/lure) with no RIG
     * column. Bottom rods (feeder/bottom/carp) return null â they keep the swappable RIG column.
     */
    public RigType nativeRig() {
        return switch (this) {
            case STICK -> RigType.PRIMITIVE;             // hook + bait, no float
            case BAMBOO -> RigType.FLOAT_LIGHT;          // float + one hook + bait
            case POLE -> RigType.FLOAT;                  // float + two hooks (Ð´ÑÐ¿Ð»ÐµÑ)
            case WINTER -> RigType.WINTER;               // a single mormyshka
            case ULTRALIGHT, SPINNING, SEA_SPIN, TROLLING -> RigType.PREDATOR; // leader + lure
            case FEEDER, BOTTOM, CARP, SURF, BOAT -> null; // still use swappable bottom rigs
        };
    }

    /**
     * True when this rod carries its tackle DIRECTLY (Â§closed-slots): the assembly GUI shows the built-in
     * rig's own slots inline instead of a swappable RIG column. Float and lure rods do; bottom rods don't.
     */
    public boolean directTackle() {
        return nativeRig() != null;
    }
}
