package com.riverfishing.component;

/**
 * Line materials (§3.3). Breaking strain ~ diameter^2 scaled by the material factor:
 * braid is far stronger than mono/fluoro at equal diameter.
 */
public enum LineType {
    //      jsonKey    strength  visibility (§line-visibility: how much the fish sees it)
    MONO  ("mono",  1.00, 1.00),   // clear-ish nylon — the baseline
    FLUORO("fluoro",1.10, 0.45),   // refractive index near water — nearly invisible, a touch stronger
    BRAID ("braid", 3.00, 1.45);   // woven Dyneema — very strong for its diameter, but opaque/visible

    // §strain-recompute (2026-07-07): kg ≈ K·d²·factor, K tuned to realistic mono (0.25mm ≈ 6 kg,
    // 0.40mm ≈ 16 kg); braid's 3× factor makes thin braid the strong choice for big fish.
    private static final double STRAIN_K = 100.0; // kg per mm^2 (mono baseline)

    private final String jsonKey;
    private final double strengthFactor;
    private final double visibilityFactor;

    LineType(String jsonKey, double strengthFactor, double visibilityFactor) {
        this.jsonKey = jsonKey;
        this.strengthFactor = strengthFactor;
        this.visibilityFactor = visibilityFactor;
    }

    /** §line-visibility: 0.20 mm mono is the reference — it reads as exactly 1. */
    private static final double VIS_REFERENCE_MM = 0.20;

    public String jsonKey() { return jsonKey; }
    public double strengthFactor() { return strengthFactor; }

    /**
     * §line-visibility: how visible a spool of this line actually is — material AND diameter.
     *
     * <p>Both halves have always mattered to the bite engine, which is the only thing that ever asked.
     * The journal asked the material alone, so it printed the same number for 0.10 mm mono and 0.80 mm
     * mono while the engine treated the thick one as eight times as conspicuous. Two functions had an
     * opinion about one word; now there is one, and the page a player reads is the engine's own answer.
     */
    public double visibility(double diameterMm) {
        return visibilityFactor * (diameterMm / VIS_REFERENCE_MM);
    }

    /** Breaking strain in kilograms for a given diameter (used by the fight mini-game, stage 3). */
    public double breakingStrainKg(double diameterMm) {
        return STRAIN_K * diameterMm * diameterMm * strengthFactor;
    }

    public static LineType fromJsonKey(String key) {
        for (LineType t : values()) {
            if (t.jsonKey.equals(key)) return t;
        }
        return MONO;
    }
}
