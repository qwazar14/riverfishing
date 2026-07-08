package com.riverfishing.component;

/**
 * Compatibility rules between the three swappable parts (§tackle-compat): rod ↔ reel ↔ line.
 *
 * <ul>
 *   <li><b>rod ↔ reel</b> — each {@link RodType} accepts a reel-size band ({@code minReel..maxReel});
 *       already enforced by {@link RodType#acceptsReelSize}.</li>
 *   <li><b>reel ↔ line</b> — a spool only holds line up to a maximum diameter: a small 1000 reel can't
 *       take thick 0.40 mm mono, a big 7000 reel is happy with anything up to 0.50 mm. Enforced here.</li>
 * </ul>
 *
 * These are hard rules in the assembly GUI (a bad combo can't be socketed) and are spelled out in the
 * journal so the player knows what fits what.
 */
public final class TackleCompat {
    private TackleCompat() {}

    /**
     * The thickest line (mm) a reel of {@code size} can spool: 1000 → 0.20, +0.05 per 1000, 7000 → 0.50.
     * A small tolerance covers floating-point diameters read back from item ids.
     */
    public static double maxLineDiameter(int reelSize) {
        return 0.15 + reelSize / 1000.0 * 0.05;
    }

    /**
     * The thinnest line (mm) that sensibly loads a reel of {@code size} — for the working-range display
     * only (thinner still fits, it just under-fills the spool, so it isn't hard-rejected).
     */
    public static double minLineDiameter(int reelSize) {
        return Math.max(0.06, maxLineDiameter(reelSize) - 0.14);
    }

    /** Can a reel of {@code size} spool a line of {@code diameterMm}? (Only the MAX is a hard limit.) */
    public static boolean reelAcceptsLine(int reelSize, double diameterMm) {
        return diameterMm <= maxLineDiameter(reelSize) + 1e-6;
    }

    /** The smallest reel size (1000..7000) that can spool this line, or 0 if none can. */
    public static int minReelForLine(double diameterMm) {
        for (int s = 1000; s <= 7000; s += 1000) {
            if (reelAcceptsLine(s, diameterMm)) return s;
        }
        return 0;
    }
}
