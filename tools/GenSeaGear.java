import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * §sea-gear-icons: (1) the 8000-14000 reels — palette-swap the 7000's purple onto a saltwater metal
 * per size, so the big-game tier reads as a progression like 1000-7000 did; (2) the four sea rod item
 * icons — chunky diagonal blanks in the style of the freshwater rods (thick grip, tapering blank,
 * guide wraps), one palette per rod. Deterministic; run: java tools/GenSeaGear.java <textures/item>.
 */
public class GenSeaGear {
    static int W = 16, H = 16;
    static int[] px;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);

        // ---- reels: recolor the 7000 donor's saturated (purple) pixels onto a new ramp ----
        BufferedImage donor = ImageIO.read(new File(dir, "reel_7000.png"));
        recolorReel(donor, dir, "reel_8000", 0x2E8E8E);   // teal — the first salt reel
        recolorReel(donor, dir, "reel_10000", 0x9AA4AC);  // brushed steel
        recolorReel(donor, dir, "reel_12000", 0xC8971E);  // gold big-game
        recolorReel(donor, dir, "reel_14000", 0xB03030);  // crimson bluewater flagship

        // ---- sea rods: chunky diagonal blanks, freshwater-icon style ----
        rod(dir, "surf_rod", 0x8A6A42, 0xD8C8A0, 0x2E6E8E, 1);   // sand blank, blue wraps, extra long
        rod(dir, "sea_spin_rod", 0x6A4A2E, 0x8EA4B4, 0x1E4E6E, 0); // steel-blue blank
        rod(dir, "boat_rod", 0x4A3020, 0x7A5A38, 0xC8971E, -1);  // short thick walnut, gold wraps
        rod(dir, "trolling_rod", 0x3A2A20, 0x54423A, 0xB03030, -1); // dark blank, red wraps, roller tip
        System.out.println("done");
    }

    static void recolorReel(BufferedImage donor, File dir, String name, int rgb) throws Exception {
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int tr = (rgb >> 16) & 255, tg = (rgb >> 8) & 255, tb = rgb & 255;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int c = donor.getRGB(x, y);
            int a = (c >>> 24);
            if (a == 0) { out.setRGB(x, y, 0); continue; }
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max - min > 24) { // a saturated (rim) pixel — remap by its brightness onto the target
                double l = max / 255.0;
                r = (int) (tr * l); g = (int) (tg * l); b = (int) (tb * l);
            }
            out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
        }
        ImageIO.write(out, "png", new File(dir, name + ".png"));
    }

    /** A diagonal rod: grip bottom-left, blank to top-right, wrap accents. len: 1 long, 0 mid, -1 short. */
    static void rod(File dir, String name, int grip, int blank, int wrap, int len) throws Exception {
        px = new int[W * H];
        int tip = len > 0 ? 0 : len < 0 ? 3 : 1;      // how far from the corner the tip stops
        int butt = len < 0 ? 12 : 13;                  // grip start
        // the blank: a 2px-wide diagonal from (butt,15-?) to (tip, ...)
        for (int i = tip; i <= 14; i++) {
            int x = i, y = 15 - (15 - i); // main diagonal y = x
            set(x, 15 - x, i >= butt ? grip : blank);
            if (i < butt) set(x, 15 - x + 1, shade(blank, -30)); // underside shading — reads thick
            if (i >= butt) { // the grip is 3px thick
                set(x, 15 - x + 1, shade(grip, -25));
                set(x, 15 - x - 1, shade(grip, 20));
            }
        }
        // reel-seat band just above the grip
        set(butt - 1, 15 - (butt - 1), 0xC0C4C8);
        set(butt - 1, 16 - (butt - 1), 0x8A8E92);
        // guide wraps along the blank
        for (int i = tip + 2; i < butt - 2; i += 3) {
            set(i, 15 - i, wrap);
            set(i, 14 - i, shade(wrap, 25));
        }
        // tip ring (trolling gets a gold roller)
        set(tip, 15 - tip, name.equals("trolling_rod") ? 0xE8C21E : shade(blank, 35));
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) out.setRGB(x, y, px[y * W + x]);
        ImageIO.write(out, "png", new File(dir, name + ".png"));
    }

    static void set(int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < W && y < H) px[y * W + x] = 0xFF000000 | rgb;
    }

    static int shade(int rgb, int d) {
        int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + d));
        int g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + d));
        int b = Math.max(0, Math.min(255, (rgb & 255) + d));
        return (r << 16) | (g << 8) | b;
    }
}
