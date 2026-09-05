package com.riverfishing.fish;

import net.minecraft.core.BlockPos;

import java.util.Random;

/**
 * §pattern (0.9.0): the pattern index — one number, 0..999, on every landed fish.
 *
 * <p>The author's idea, taken from CS:GO knife finishes: ONE texture, and the skin is a window into it
 * at an angle and an offset. A rare alignment lands on a special zone and the knife comes out a colour
 * nobody can farm. That is the collector's layer the fish were missing — two kohaku of the same weight
 * should not be the same fish.
 *
 * <p>The number does three things, all of them deterministic:
 * <ol>
 *   <li>it falls into one of twelve named <b>families</b> of uneven width, each with its own hue
 *       transform over the tint layers the koi work already cut out of the sprite;</li>
 *   <li>the index WITHIN the band shifts the hue a few more degrees and moves the patch a notch, so
 *       #237 and #238 are cousins rather than twins;</li>
 *   <li>twelve exact indices out of the thousand are <b>gems</b>: the fish takes one saturated colour
 *       the bands never produce. One landed fish in 83 is a gem of some kind; a NAMED gem is one in a
 *       thousand.</li>
 * </ol>
 *
 * <p>Everything here is pure arithmetic on the index. That is the one bug this feature cannot have: a
 * pattern whose colour changes between two loads is worse than no pattern at all, so nothing in this
 * file may reach for a clock, a {@code Random} or a hash of an object — {@code tools/check_pattern.py}
 * greps for exactly that.
 */
public final class Pattern {
    /** The NBT key on the catch card, on roe and on fry. */
    public static final String TAG = "Pattern";

    /** Indices run 0..{@value}; {@code -1} is a fish landed before this existed. */
    public static final int MAX = 999;

    /** No pattern at all — an old card, a creative-tab fish, a shoal sprite in open water. */
    public static final int NONE = -1;

    /**
     * The twelve families, by the index each band STARTS at; the next entry's start is its end. Uneven
     * widths on purpose — the common finishes should be common, and the two at the top rare enough that
     * a player can want them. 90 + 110 + 90 + 110 + 80 + 80 + 80 + 70 + 70 + 70 + 80 + 70 = 1000.
     */
    private static final int[] BAND = {0, 90, 200, 290, 400, 480, 560, 640, 710, 780, 850, 930};

    /** The band names, in band order. Lang: {@code pattern.riverfishing.<name>}. */
    private static final String[] FAMILY = {
            "plain", "drift", "crown", "banded", "speckled", "mask",
            "marbled", "veined", "dappled", "ghost", "ember", "aurora"};

    /**
     * The hue each family turns the fish, in degrees. They alternate sign and grow outward so the
     * families read as a SEQUENCE — a plain fish is the sprite as drawn, and an aurora is as far from
     * it as the mod will go without becoming a different animal.
     */
    private static final int[] HUE = {0, 3, -4, 6, -7, 9, -11, 12, -14, 15, -17, 18};

    /**
     * §pattern-hue: and how much lighter or darker, in value. The families used to be hue alone, which
     * meant hue had to reach ±70° to tell twelve of them apart — and 60° turns a red koi green, which
     * is what the first test chest showed. Half the work moves here: a family is now a few degrees of
     * hue AND a lift or a shade, so the twelve read as pale, deep, warm and cool specimens of the same
     * fish. Same indexing as {@link #HUE} and {@link #FAMILY}.
     */
    private static final double[] LIFT = {0.0, 0.06, -0.06, 0.10, -0.10, 0.04,
                                          -0.04, 0.12, -0.12, 0.08, -0.08, 0.14};

    /**
     * The rare zones: twelve exact indices, one inside each band, spread so no two share a family.
     * Deliberately unmemorable numbers — a round one would look designed, and half the pleasure is that
     * nobody can tell by looking at a fish that #512 was ever going to be anything.
     */
    private static final int[] GEM_AT = {13, 127, 239, 341, 439, 512, 601, 677, 733, 811, 887, 971};

    /** Lang: {@code gem.riverfishing.<name>}, indexed with {@link #GEM_AT}. */
    private static final String[] GEM = {
            "sapphire", "gold", "emerald", "jet", "amethyst", "pearl",
            "ruby", "copper", "jade", "amber", "opal", "obsidian"};

    /** What a gem paints the WHOLE fish — every layer, one saturated colour, no ground showing. */
    private static final int[] GEM_RGB = {
            0x1B3FCF, 0xF2B01E, 0x0FA05A, 0x141218, 0x8A3FD0, 0xF2EDE0,
            0xD2143C, 0xC1642A, 0x2FB79A, 0xFF9A16, 0x9FE8E0, 0x241A2E};

    private Pattern() {}

    // ---- reading the number --------------------------------------------------------------------

    /** True for an index that names a pattern at all: an old card carries {@link #NONE}. */
    public static boolean has(int pattern) {
        return pattern >= 0 && pattern <= MAX;
    }

    /** 0..11, the band this index falls in; 0 for a fish with no pattern. */
    public static int familyIndex(int pattern) {
        if (!has(pattern)) return 0;
        int i = BAND.length - 1;
        while (i > 0 && pattern < BAND[i]) i--;
        return i;
    }

    /** The family's lang tail — {@code pattern.riverfishing.<this>}. */
    public static String family(int pattern) {
        return FAMILY[familyIndex(pattern)];
    }

    /** Every family name in band order: the journal's twelve cells, and the check's table. */
    public static String[] families() {
        return FAMILY.clone();
    }

    /** The top band — {@code aurora}, the last 70 indices — which the counter pays half again for. */
    public static boolean topBand(int pattern) {
        return has(pattern) && familyIndex(pattern) == BAND.length - 1;
    }

    public static boolean isGem(int pattern) {
        return gemIndex(pattern) >= 0;
    }

    /** The gem's lang tail, or "" when this index is an ordinary one. */
    public static String gemName(int pattern) {
        int i = gemIndex(pattern);
        return i < 0 ? "" : GEM[i];
    }

    /** The one colour a gem paints every layer with; {@code -1} for an ordinary index. */
    public static int gemColor(int pattern) {
        int i = gemIndex(pattern);
        return i < 0 ? -1 : GEM_RGB[i];
    }

    private static int gemIndex(int pattern) {
        for (int i = 0; i < GEM_AT.length; i++) if (GEM_AT[i] == pattern) return i;
        return -1;
    }

    /**
     * The gem's colour as INK — lifted until it reads against the tooltip's dark ground. Jet and
     * obsidian are the point of the two of them and are all but black; printed raw they would be a gem
     * nobody could see they had.
     */
    public static int gemInk(int pattern) {
        int c = gemColor(pattern);
        if (c < 0) return 0xFFFFFFFF;
        int max = Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF));
        if (max >= 200) return 0xFF000000 | c;
        double k = 200.0 / Math.max(1, max);
        return 0xFF000000 | byteOf(((c >> 16) & 0xFF) * k / 255.0) << 16
                | byteOf(((c >> 8) & 0xFF) * k / 255.0) << 8 | byteOf((c & 0xFF) * k / 255.0);
    }

    /**
     * The colour that stands for a family on the journal's board: one ordinary fish colour, turned by
     * that family's own hue. So the twelve cells read left to right as the sequence the bands paint,
     * and the board needs no twelve new swatches to keep in step with the code.
     */
    public static int swatch(int familyIndex) {
        int i = Math.max(0, Math.min(HUE.length - 1, familyIndex));
        // §pattern-hue: the board shows what the family actually does — its own turn AND its own lift.
        // Exaggerating either here would make the journal promise a fish the water does not paint.
        return shift(0xC8A25A, HUE[i], LIFT[i]);
    }

    /**
     * The counter's pattern term. A gem is six times the fish — that is the whole point of a rare zone —
     * the top band is worth half again, and the other 918 indices are worth exactly what the fish is.
     */
    public static double value(int pattern) {
        return isGem(pattern) ? 6.0 : topBand(pattern) ? 1.5 : 1.0;
    }

    // ---- the two variations inside a band ------------------------------------------------------

    /**
     * Degrees of hue for this index: the family's own turn, plus up to ±10 more read off where in the
     * band it sits. Two neighbours differ by a fraction of a degree, so #237 and #238 are cousins; the
     * two ends of a band are visibly different fish.
     */
    public static double hueShift(int pattern) {
        if (!has(pattern)) return 0.0;
        int i = familyIndex(pattern);
        int start = BAND[i], end = i + 1 < BAND.length ? BAND[i + 1] : MAX + 1;
        double t = (pattern - start) / (double) (end - start);      // 0..1 across the band
        // §pattern-hue: ±4° inside a band, not ±10. Neighbours are cousins; the ends of a band are a
        // shade apart, not a colour apart.
        return HUE[i] + (t - 0.5) * 8.0;
    }

    /**
     * −2..+2, the "slide" of the patch inside the band.
     *
     * <p>The design asks for the mask to move a pixel or two. It cannot: the koi's four layers are FIXED
     * masks cut out of one sprite (tools/gen_koi_layers.py) and an item model cannot offset a layer, so
     * a moved mask would need four more drawings per step. The closest thing that works from the same
     * masks is DEPTH — the patch sits a few percent lighter or darker against the ground, which reads
     * as the marking lying nearer the surface or further under it. Same knob, honest about what it is.
     */
    public static int offset(int pattern) {
        return !has(pattern) ? 0 : (pattern * 7 + pattern / 13) % 5 - 2;
    }

    // ---- where the number comes from -----------------------------------------------------------

    /**
     * A wild fish's index: the world seed, the block it came out of and the tick it came out on, mixed.
     * The tick is in there so the same spot cannot be re-cast for the same number — the pattern is a
     * property of THIS catch, not of the swim.
     */
    public static int roll(long seed, BlockPos where, long time) {
        long h = mix(seed * 0x9E3779B97F4A7C15L + where.asLong());
        return (int) Math.floorMod(mix(h ^ (time * 0xC2B2AE3D27D4EB4FL)), (long) MAX + 1);
    }

    /** splitmix64's finalizer — cheap, and it spreads a low block coordinate across all 64 bits. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * A bred fish's index: the parents' mean, then a small mutation. This is the collector's hook — a
     * line can be bred TOWARD a family the way koi breeders breed toward a mark, and never lands on it
     * exactly, so the last few points are always work.
     *
     * <p>The mutation is a gaussian of about 12, which is a fifth of the narrowest band: two parents
     * inside a family almost always throw inside it, and a pair sitting near an edge throws over it
     * about once in four. A parent with no pattern (a fish landed before this update) contributes the
     * other's, so a line started from one known fish still breeds true.
     */
    public static int inherit(int mother, int father, Random rng) {
        int m = has(mother) ? mother : father, f = has(father) ? father : mother;
        if (!has(m)) return NONE;                       // neither parent has one: nothing to inherit
        int mean = (m + f) / 2 + (int) Math.round(rng.nextGaussian() * 12.0);
        return Math.max(0, Math.min(MAX, mean));
    }

    // ---- what it does to the picture -----------------------------------------------------------

    /**
     * One layer's colour under this pattern. The gem overrides everything — every layer takes the one
     * saturated colour, which is what makes a gem read as a solid fish; otherwise the band turns the
     * hue, and a PATCH layer (hi, sumi, the tancho crown — anything but the ground) also takes the
     * band's depth offset.
     *
     * @param rgb     the colour this layer would be without a pattern
     * @param pattern 0..999, or {@link #NONE} to leave the colour alone
     * @param patch   false for the body ground, true for a marking laid over it
     */
    public static int paint(int rgb, int pattern, boolean patch) {
        if (!has(pattern)) return rgb;
        int gem = gemColor(pattern);
        if (gem >= 0) return gem;
        // §pattern-hue: the family's own lift, and on a PATCH the depth offset on top of it.
        double lift = LIFT[familyIndex(pattern)] + (patch ? offset(pattern) * 0.03 : 0.0);
        return shift(rgb, hueShift(pattern), lift);
    }

    /**
     * Turn a colour's hue by {@code degrees} and its value by {@code lift}.
     *
     * <p>A near-grey has no hue to turn — a platinum koi is white and a karasu is black — so instead of
     * leaving half the varieties pattern-blind, a colour with almost no saturation is given a little:
     * 7% of the family's own hue, which is the faintest cast the eye still catches on white.
     */
    /**
     * §pattern-mask: the colour the family's MASK is painted, given the fish's ground colour. The mask
     * says where; this says what. A light fish takes a darker cut of its own ground and a dark one a
     * paler cut, so the marking always reads against the body; ghost is a pale wash whatever the
     * fish, ember is warm, aurora is the ground turned round the wheel by where the index sits in its
     * band. Then the family's own hue and lift, and the in-band turn — so two neighbours differ.
     * A gem returns the gem: the whole fish is already that colour and the mask must vanish into it.
     */
    public static int marking(int ground, int pattern) {
        int gem = gemColor(pattern);
        if (gem >= 0) return gem;
        int fam = familyIndex(pattern);
        if (fam == 0) return ground;
        double lum = (0.299 * ((ground >> 16) & 0xFF) + 0.587 * ((ground >> 8) & 0xFF) + 0.114 * (ground & 0xFF)) / 255.0;
        String name = FAMILY[fam];
        int base;
        if ("ghost".equals(name)) base = mix(ground, 0xF6F2EA, 0.62);
        else if ("ember".equals(name)) base = mix(ground, 0xE8702A, 0.72);
        else if ("aurora".equals(name)) base = shift(mix(ground, 0x60B8FF, 0.55), (pattern % 70) * 360.0 / 70.0, 0.10);
        else base = lum > 0.42 ? mix(ground, 0x241A12, 0.55) : mix(ground, 0xF0E6D2, 0.45);
        return shift(base, hueShift(pattern) - HUE[fam], LIFT[fam]) & 0xFFFFFF;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shift(int rgb, double degrees, double lift) {
        double r = ((rgb >> 16) & 0xFF) / 255.0, g = ((rgb >> 8) & 0xFF) / 255.0, b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        double h;
        if (d <= 0.0) h = 0.0;
        else if (max == r) h = ((g - b) / d + 6.0) % 6.0;
        else if (max == g) h = (b - r) / d + 2.0;
        else h = (r - g) / d + 4.0;
        double s = max <= 0.0 ? 0.0 : d / max;
        h = ((h * 60.0 + degrees) % 360.0 + 360.0) % 360.0;
        if (s < 0.08) s = Math.min(0.07, s + 0.07);      // white and black still wear the family
        double v = Math.max(0.0, Math.min(1.0, max + lift));
        return hsv(h, s, v);
    }

    private static int hsv(double h, double s, double v) {
        double c = v * s, x = c * (1.0 - Math.abs((h / 60.0) % 2.0 - 1.0)), m = v - c;
        double r, g, b;
        int sector = (int) (h / 60.0) % 6;
        switch (sector) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        return byteOf(r + m) << 16 | byteOf(g + m) << 8 | byteOf(b + m);
    }

    private static int byteOf(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v * 255.0)));
    }
}
