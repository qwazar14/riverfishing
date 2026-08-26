package com.riverfishing.fish;

import com.riverfishing.item.FishItem;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * §keepnet-shape (0.7.0): how much room a fish takes up, worked out from the length and the weight the
 * mod already records for every specimen.
 *
 * <p>Nothing here is authored per species, and that is the point: the footprint falls out of the fish's
 * own proportions, so an eel is a long thin bar and a bream is a fat block without anyone deciding so.
 *
 * <ul>
 *   <li><b>Length</b> gives the long axis, one cell per 25 cm.</li>
 *   <li><b>Condition</b> gives the short axis. Fulton's K — 100·W/L³, the number a real angler's club
 *       uses to say how deep-bodied a fish is — runs about 0.2 for an eel, 1 for a pike and over 2 for a
 *       bream or a flounder. Width is the long axis scaled by it, so slimness and length are independent:
 *       a metre of pike is long and narrow, half a metre of carp is short and broad.</li>
 *   <li><b>Big fish taper.</b> Anything three cells across AND four long loses its four corners, because a
 *       rectangle is not a fish and because a tapered piece tetrises like one. Shorter than that and the
 *       taper would eat the whole shape.</li>
 * </ul>
 *
 * <p>Run {@link #main} to print the table for the real species — the shapes are checked there rather than
 * in the game, since being wrong about them is a data question, not a rendering one.
 */
public final class FishShape {
    /** One cell per this many centimetres of fish. */
    private static final double CM_PER_CELL = 25.0;
    /** The condition factor of an ordinary fish; width is measured against it. */
    private static final double NORMAL_K = 1.6;
    /** Nothing is longer than this many cells, or the biggest keepnet could never hold one. */
    private static final int MAX_LONG = 7;
    private static final int MAX_WIDE = 4;

    private final int w;
    private final int h;
    private final boolean[] mask;

    private FishShape(int w, int h, boolean[] mask) {
        this.w = w;
        this.h = h;
        this.mask = mask;
    }

    public int width() { return w; }

    public int height() { return h; }

    /** Is this cell of the shape's bounding box actually part of the fish? */
    public boolean at(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && mask[y * w + x];
    }

    /** How many cells the fish really occupies — the tapered corners are free space. */
    public int cells() {
        int n = 0;
        for (boolean b : mask) {
            if (b) n++;
        }
        return n;
    }

    /** The same fish turned a quarter turn. Long fish are placed either way round, which is the game. */
    public FishShape rotated() {
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // (x,y) -> (h-1-y, x) in the transposed box
                out[x * h + (h - 1 - y)] = mask[y * w + x];
            }
        }
        return new FishShape(h, w, out);
    }

    public FishShape rotated(int quarterTurns) {
        FishShape s = this;
        for (int i = 0; i < Math.floorMod(quarterTurns, 2); i++) s = s.rotated();
        return s;
    }

    /** The footprint of a caught specimen. Anything that is not a fish is a plain single cell. */
    public static FishShape of(ItemStack stack) {
        int len = FishItem.getLengthCm(stack);
        int weight = FishItem.getWeightG(stack);
        if (len <= 0 || weight <= 0) return of(1, 1);
        return of(len, weight);
    }

    public static FishShape of(int lengthCm, int weightG) {
        int cellsLong = (int) Mth.clamp(Math.ceil(lengthCm / CM_PER_CELL), 1, MAX_LONG);
        // Fulton's condition factor: 100·W/L³. Deep-bodied fish are over 2, eels are a fifth of that.
        double k = 100.0 * weightG / Math.pow(Math.max(1, lengthCm), 3);
        // Never as wide as it is long: a fish is longer than it is deep, and a square block reads as a
        // crate rather than a carp.
        int widest = Math.min(MAX_WIDE, Math.max(1, cellsLong - (cellsLong >= 3 ? 1 : 0)));
        int cellsWide = (int) Mth.clamp(Math.round(cellsLong * k / NORMAL_K), 1, widest);

        boolean[] mask = new boolean[cellsLong * cellsWide];
        java.util.Arrays.fill(mask, true);
        // A fish three cells across tapers at the nose and the tail — but only once it is long enough to
        // have a middle. Taking the corners off a 3x3 leaves a plus sign, which is not a fish.
        if (cellsWide >= 3 && cellsLong >= 4) {
            mask[0] = false;                                             // nose, top
            mask[(cellsWide - 1) * cellsLong] = false;                   // nose, bottom
            mask[cellsLong - 1] = false;                                 // tail, top
            mask[(cellsWide - 1) * cellsLong + cellsLong - 1] = false;   // tail, bottom
        }
        return new FishShape(cellsLong, cellsWide, mask);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(w + "x" + h + " (" + cells() + " cells)\n");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) sb.append(at(x, y) ? '#' : '.');
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Self-check: the shapes for real specimens, and the invariants that must hold for every one. */
    public static void main(String[] args) {
        record Fish(String name, int cm, int g) {}
        Fish[] table = {
                new Fish("bleak", 12, 25), new Fish("roach", 20, 120), new Fish("perch", 25, 250),
                new Fish("bream", 40, 900), new Fish("tench", 38, 800), new Fish("eel", 80, 1000),
                new Fish("pike", 67, 2000), new Fish("carp", 60, 3500), new Fish("flounder", 44, 2200),
                new Fish("catfish", 150, 30000), new Fish("halibut", 152, 52000),
                new Fish("blue_marlin", 300, 200000),
        };
        for (Fish f : table) {
            FishShape s = of(f.cm(), f.g());
            System.out.printf("%-12s %3d cm %7d g -> %s", f.name(), f.cm(), f.g(), s);
            assert s.cells() > 0 : "a fish with no cells";
            assert s.width() >= 1 && s.height() >= 1;
            FishShape r = s.rotated();
            assert r.width() == s.height() && r.height() == s.width() : "rotation lost the box";
            assert r.cells() == s.cells() : "rotation lost cells";
            assert s.rotated().rotated().toString().equals(s.toString()) : "two turns is not identity";
        }
        // A slim fish must never be wider than a deep one of the same length.
        assert of(80, 1000).height() <= of(80, 9000).height() : "an eel came out fatter than a carp";
        System.out.println("ok");
    }
}
