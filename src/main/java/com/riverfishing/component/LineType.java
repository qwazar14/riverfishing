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

    public String jsonKey() { return jsonKey; }
    public double strengthFactor() { return strengthFactor; }
    /** §line-visibility: relative conspicuousness of the material (fluoro low, braid high). */
    public double visibilityFactor() { return visibilityFactor; }

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
