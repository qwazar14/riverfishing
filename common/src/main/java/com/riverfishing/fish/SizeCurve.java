package com.riverfishing.fish;

/**
 * §weight-curve / §weight-scale: the size curve of a species' catches, and its distribution — the roll in
 * FishingManager.rollFish and the scale on the fish card read the same two formulas from here. Pure, so it
 * checks itself:
 *
 * <pre>
 *   java -cp common/build/classes/java/main com.riverfishing.fish.SizeCurve
 * </pre>
 */
public final class SizeCurve {
    private SizeCurve() {}

    /**
     * The exponent: a catch weighs {@code min + (max - min) * u^k} for a uniform u, with k solved so that
     * half the catches land under the mean (median(u^k) = 0.5^k). No mean: the classic big-fish-are-rare 2.4.
     */
    public static double k(double min, double max, double mean, boolean meanSet) {
        if (meanSet && mean > min && mean < max) {
            double f = (mean - min) / (max - min);
            return Math.max(0.5, Math.min(8.0, Math.log(f) / Math.log(0.5)));
        }
        return 2.4;
    }

    /** The share of ordinary catches lighter than {@code grams}: P(min + range * u^k <= g) = f^(1/k). */
    public static double percentile(double min, double max, double k, double grams) {
        if (max <= min) return 0.5;
        double f = Math.max(0, Math.min(1, (grams - min) / (max - min)));
        return Math.pow(f, 1.0 / k);
    }

    /** Rolls the curve and counts: the formula must say what the dice do, to a hundredth. */
    public static void main(String[] args) {
        java.util.Random r = new java.util.Random(7);
        double[][] species = {{100, 3000, 800, 1}, {2000, 30000, 6000, 1}, {5, 400, 0, 0}, {50, 900, 880, 1}};
        double worst = 0;
        for (double[] s : species) {
            double k = k(s[0], s[1], s[2], s[3] > 0);
            double[] at = {s[0] + (s[1] - s[0]) * 0.1, s[2] > 0 ? s[2] : (s[0] + s[1]) / 2, s[0] + (s[1] - s[0]) * 0.9};
            int[] under = new int[at.length];
            int n = 400_000;
            for (int i = 0; i < n; i++) {
                double w = s[0] + (s[1] - s[0]) * Math.pow(r.nextDouble(), k);
                for (int j = 0; j < at.length; j++) if (w <= at[j]) under[j]++;
            }
            for (int j = 0; j < at.length; j++) {
                double seen = under[j] / (double) n, said = percentile(s[0], s[1], k, at[j]);
                worst = Math.max(worst, Math.abs(seen - said));
            }
            if (s[3] > 0 && Math.abs(percentile(s[0], s[1], k, s[2]) - 0.5) > 1e-9 && k > 0.5 && k < 8.0) {
                throw new IllegalStateException("the mean is not the median");
            }
        }
        System.out.printf("SizeCurve: worst gap between the formula and the dice %.4f%n", worst);
        if (worst > 0.01) throw new IllegalStateException("the scale does not say what the roll does");
        System.out.println("SizeCurve: all checks pass");
    }
}
