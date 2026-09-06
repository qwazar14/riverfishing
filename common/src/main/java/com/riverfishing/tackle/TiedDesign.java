package com.riverfishing.tackle;

import com.riverfishing.engine.LureColor;
import com.riverfishing.item.StackNbt;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * §tying (0.9.1): a lure the player TIED — a 16×16 canvas of materials, and everything the bite
 * engine reads off it.
 *
 * <p>The canvas is the object: what you draw is what you fish and what you see in the slot. The
 * palette is materials, not colours — thread in the sixteen dye colours, hackle, fur, a bead, tinsel,
 * an eye — and every pixel costs a sliver of its material at the vise. The bite engine never reads
 * the drawing as art; it reads it as a lure:
 * <ul>
 *   <li>{@link #analyse} folds it into a SILHOUETTE (size, weight), a MEAN COLOUR (the same
 *       colour-vs-light model painted lures use — {@link LureColor}), FLASH (tinsel and bead over the
 *       fill), ACTION (hackle and fur over the fill), EYES, and the nearest of eight
 *       {@link Template}s by overlap after the drawing is fitted to its bounding box;</li>
 *   <li>{@link Analysis#affinity} turns the template into a per-family bite factor — an ant reads
 *       as insect food to a roach and to nothing much for a pike, a streamer the other way round.
 *       A drawing that matches nothing is a curiosity: it fishes everything at 0.6.</li>
 * </ul>
 * Balance lives in eight masks and three tables here, never in the infinity of drawings.
 */
public final class TiedDesign {
    public static final int SIZE = 16;
    public static final String TAG_DESIGN = "Design", TAG_HOOK = "Hook", TAG_MAKER = "Maker";

    /** Pixel values. 1..16 are thread in DyeColor order; the rest are single materials. */
    public static final int EMPTY = 0, THREAD0 = 1, HACKLE = 17, FUR = 18, BEAD_IRON = 19, BEAD_GOLD = 20, TINSEL = 21, EYE = 22, LAST = 22;

    /** Thread colours, DyeColor order (white … black) — a table of our own so no version's DyeColor API is asked. */
    private static final int[] THREAD_RGB = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    public static final String[] DYE_NAMES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    public static int rgb(int px) {
        if (px >= THREAD0 && px < THREAD0 + 16) return THREAD_RGB[px - THREAD0];
        return switch (px) {
            case HACKLE -> 0x6B4A2B;
            case FUR -> 0xA08C6A;
            case BEAD_IRON -> 0xC9CDD0;
            case BEAD_GOLD -> 0xE8C14A;
            case TINSEL -> 0xDDE8EE;
            case EYE -> 0x101010;
            default -> 0;
        };
    }

    /** Lang-key tail of a material: {@code material.riverfishing.<name>}. */
    public static String materialKey(int px) {
        if (px >= THREAD0 && px < THREAD0 + 16) return "thread_" + DYE_NAMES[px - THREAD0];
        return switch (px) {
            case HACKLE -> "hackle"; case FUR -> "fur"; case BEAD_IRON -> "bead_iron";
            case BEAD_GOLD -> "bead_gold"; case TINSEL -> "tinsel"; case EYE -> "eye"; default -> "none";
        };
    }

    /** Pixels one unit of the material buys. The vise consumes ceil(px / this). */
    public static int pixelsPerUnit(int px) {
        if (px >= THREAD0 && px < THREAD0 + 16) return 40;   // a string (and a dye) ties forty
        return switch (px) {
            case HACKLE -> 24;      // a feather
            case FUR -> 40;         // a block of wool
            case BEAD_IRON, BEAD_GOLD -> 3;   // a nugget
            case TINSEL -> 16;      // a copper ingot
            case EYE -> 4;          // an ink sac
            default -> Integer.MAX_VALUE;
        };
    }

    // ---- the templates ---------------------------------------------------------------------------

    /**
     * The eight shapes a drawing is read as, each a 16×16 mask that fills its own box, and the
     * bait family it fishes as. Families: how the engine multiplies the species' bait score by the
     * fish's group — cyprinid (roach, bream, carp), predator (pike, perch, zander), salmonid (trout,
     * grayling), sea, sturgeon, anything else.
     */
    public enum Template {
        PELLET("pellet", new String[]{
                "................", "................", "................", "......####......", "....########....", "...##########...", "...##########...", "..############..", "..############..", "...##########...", "...##########...", "....########....", "......####......", "................", "................", "................"},
                1.00, 0.90, 0.90, 0.80),
        DROP("drop", new String[]{
                ".......##.......", ".......##.......", "......####......", "......####......", "......####......", ".....######.....", ".....######.....", ".....######.....", "....########....", "....########....", "....########....", "....########....", "....########....", ".....######.....", ".....######.....", "......####......"},
                1.00, 0.95, 0.90, 0.85),
        DEVIL("devil", new String[]{
                ".......##.......", ".......##.......", ".......##.......", ".......##.......", "......####......", "......####......", "......####......", "......####......", "......####......", "......####......", "......####......", "......####......", "......####......", ".....######.....", "....########....", ".....######....."},
                0.90, 1.10, 0.85, 0.80),
        ANT("ant", new String[]{
                "................", "......####......", ".....######.....", ".....######.....", "......####......", ".......##.......", ".......##.......", ".......##.......", "......####......", ".....######.....", "....########....", "....########....", "....########....", ".....######.....", "......####......", "................"},
                1.15, 0.60, 1.15, 0.50),
        NYMPH("nymph", new String[]{
                "................", "................", "................", "................", "................", "..###...........", ".#####..........", "###########.....", "###############.", ".#############.#", "..############..", "...##########...", "....######......", "................", "................", "................"},
                1.15, 0.65, 1.20, 0.55),
        STREAMER("streamer", new String[]{
                "................", "................", "................", ".###............", "#######.........", "##########......", "############....", "##############..", "###############.", "##############..", "############....", "##########......", "#######.........", ".###............", "................", "................"},
                0.50, 1.20, 1.00, 1.05),
        SHRIMP("shrimp", new String[]{
                "................", "................", "......#####.....", "....#########...", "...####...####..", "..###.......###.", "..##.........##.", "..##.........##.", "..##.........##.", "..###.......###.", "...###.....###..", "....##.....##...", ".....#.....#....", "................", "................", "................"},
                0.80, 1.05, 0.90, 1.15),
        DRY_FLY("dry_fly", new String[]{
                "..##........##..", "...##......##...", "....##....##....", ".....##..##.....", "......####......", "......####......", ".....######.....", ".....######.....", ".....######.....", "......####......", ".......##.......", ".......##.......", ".......##.......", "........##......", ".........#......", "................"},
                1.00, 0.40, 1.25, 0.40),
        NONE("none", new String[16], 0.60, 0.60, 0.60, 0.60);

        public final String key;
        final boolean[][] mask = new boolean[SIZE][SIZE];
        final double cyprinid, predator, salmonid, sea;

        Template(String key, String[] rows, double cyprinid, double predator, double salmonid, double sea) {
            this.key = key;
            for (int y = 0; y < SIZE; y++) {
                if (rows[y] == null) continue;
                for (int x = 0; x < SIZE; x++) mask[y][x] = rows[y].charAt(x) == '#';
            }
            this.cyprinid = cyprinid; this.predator = predator; this.salmonid = salmonid; this.sea = sea;
        }

        double family(String group) {
            if (group == null) return (cyprinid + predator + salmonid) / 3.0;
            return switch (group) {
                case "cyprinid", "koi" -> cyprinid;
                case "predator", "big_game" -> predator;
                case "salmonid" -> salmonid;
                case "sea" -> sea;
                default -> (cyprinid + predator + salmonid) / 3.0;
            };
        }
    }

    // ---- reading the canvas ----------------------------------------------------------------------

    public static byte[] design(ItemStack stack) {
        byte[] d = StackNbt.get(stack).getByteArray(TAG_DESIGN).orElse(null);
        return d != null && d.length == SIZE * SIZE ? d : null;
    }

    public static int hookSize(ItemStack stack) {
        return StackNbt.get(stack).getIntOr(TAG_HOOK, 0);
    }

    public static boolean valid(byte[] d) {
        if (d == null || d.length != SIZE * SIZE) return false;
        int fill = 0;
        for (byte b : d) {
            if (b < 0 || b > LAST) return false;
            if (b != EMPTY) fill++;
        }
        return fill >= 4;
    }

    /** Units of each material the drawing costs, indexed by pixel value. */
    public static int[] cost(byte[] d) {
        int[] px = new int[LAST + 1];
        for (byte b : d) if (b > 0 && b <= LAST) px[b]++;
        int[] out = new int[LAST + 1];
        for (int i = 1; i <= LAST; i++) out[i] = px[i] == 0 ? 0 : (px[i] + pixelsPerUnit(i) - 1) / pixelsPerUnit(i);
        return out;
    }

    /** What the engine reads off a drawing. */
    public record Analysis(Template template, double match, int fill, int boxW, int boxH,
                           int meanRgb, LureColor lureColor, double weightG, double flash, double action, boolean eyes) {
        /** The bite factor for a species of {@code group}: the template's family table, scaled by how well the drawing matched it. */
        public double affinity(String group) {
            double base = template == Template.NONE ? 0.6 : template.family(group) * (0.6 + 0.4 * match);
            boolean hunter = "predator".equals(group) || "big_game".equals(group) || "sea".equals(group);
            if (eyes && hunter) base *= 1.10;                       // a lure with eyes gets looked at
            base *= 1.0 + flash * (hunter ? 0.15 : "salmonid".equals(group) ? 0.10 : 0.0);
            return base;
        }

        /** Size in mm, one per pixel — the longer side of the drawing. */
        public int sizeMm() { return Math.max(boxW, boxH); }
    }

    public static Analysis analyse(byte[] d) {
        if (!valid(d)) return new Analysis(Template.NONE, 0, 0, 0, 0, 0x888888, LureColor.NATURAL, 0.3, 0, 0, false);
        int fill = 0, x0 = SIZE, y0 = SIZE, x1 = -1, y1 = -1, r = 0, g = 0, b = 0, flashPx = 0, actionPx = 0, eyePx = 0;
        double weight = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int p = d[y * SIZE + x];
                if (p == EMPTY) continue;
                fill++;
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
                int c = rgb(p);
                r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
                switch (p) {
                    case BEAD_IRON -> { weight += 0.35; flashPx++; }
                    case BEAD_GOLD -> { weight += 0.65; flashPx++; }
                    case TINSEL -> { weight += 0.03; flashPx++; }
                    case HACKLE, FUR -> { weight += 0.01; actionPx++; }
                    case EYE -> { weight += 0.02; eyePx++; }
                    default -> weight += 0.02;
                }
            }
        }
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        // fit the drawing to its box, then overlap it with every template that fills its own box
        Template best = Template.NONE;
        double bestIou = 0;
        for (Template t : Template.values()) {
            if (t == Template.NONE) continue;
            int tx0 = SIZE, ty0 = SIZE, tx1 = -1, ty1 = -1;
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (t.mask[y][x]) {
                tx0 = Math.min(tx0, x); ty0 = Math.min(ty0, y); tx1 = Math.max(tx1, x); ty1 = Math.max(ty1, y);
            }
            int tw = tx1 - tx0 + 1, th = ty1 - ty0 + 1;
            int inter = 0, union = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    boolean m = t.mask[y][x];
                    boolean f;
                    if (x < tx0 || x > tx1 || y < ty0 || y > ty1) f = false;
                    else {
                        int sx = x0 + (x - tx0) * w / tw, sy = y0 + (y - ty0) * h / th;
                        f = d[sy * SIZE + sx] != EMPTY;
                    }
                    if (m && f) inter++;
                    if (m || f) union++;
                }
            }
            double iou = union == 0 ? 0 : inter / (double) union;
            // fitting to the box erased the proportions — put them back: a tall thin devil and a round
            // pellet fill the same box and must not read as one drawing
            double arD = w / (double) h, arT = tw / (double) th;
            iou *= Math.min(arD, arT) / Math.max(arD, arT);
            if (iou > bestIou) { bestIou = iou; best = t; }
        }
        if (bestIou < 0.45) best = Template.NONE;
        int mean = (r / fill) << 16 | (g / fill) << 8 | (b / fill);
        return new Analysis(best, Math.min(1.0, (bestIou - 0.45) / 0.45), fill, w, h, mean, LureColor.fromRgb(mean),
                Math.max(0.1, weight), flashPx / (double) fill, actionPx / (double) fill, eyePx >= 2);
    }

    public static Analysis analyse(ItemStack stack) {
        return analyse(design(stack));
    }

    public static int hash(byte[] d) {
        return Arrays.hashCode(d);
    }

    private TiedDesign() {}
}
