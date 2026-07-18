import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * §sea-tackle (0.5.0): generates the three saltwater rod blanks in the established 32x32 style —
 * a tapered diagonal blank (butt bottom-left → tip top-right) with line guides and a grip — plus the
 * horizontally mirrored rod_m/ copies and 16x16 standalone item icons. Deterministic; rerun to
 * regenerate, swap PNGs to restyle. Usage: java tools/GenSeaRods.java <assets/riverfishing/textures/item>
 */
public final class GenSeaRods {
    public static void main(String[] args) throws Exception {
        File base = new File(args.length > 0 ? args[0] : "common/src/main/resources/assets/riverfishing/textures/item");
        File rod = new File(base, "rod"); rod.mkdirs();
        File rodM = new File(base, "rod_m"); rodM.mkdirs();
        // key, blank colour, grip colour, thickness (butt px), guides
        gen(base, rod, rodM, "surf", 0xC8CDD2, 0x2E3A46, 3, 5);      // long pale surf blank, dark blue grip
        gen(base, rod, rodM, "sea_spin", 0x7C93A6, 0x1E2830, 2, 4);  // blue-gray coastal spinner
        gen(base, rod, rodM, "boat", 0x4E5A62, 0x5A3A22, 4, 3);      // short thick slate boat rod, cork grip
        System.out.println("done");
    }

    static void gen(File base, File rodDir, File rodMDir, String key, int blank, int grip,
                    int thick, int guides) throws Exception {
        int S = 32;
        int[] px = new int[S * S];
        // Blank: butt (2,29) -> tip (29,2), thickness tapering thick..1.
        for (int i = 0; i <= 27; i++) {
            int x = 2 + i, y = 29 - i;
            double t = i / 27.0;
            int w = Math.max(1, (int) Math.round(thick * (1.0 - t * 0.75)));
            int c = shade(blank, (int) (-20 * t));
            for (int o = 0; o < w; o++) put(px, S, x + o, y, c);
            if (w > 1) put(px, S, x, y - 1, shade(c, 25)); // top highlight
        }
        // Grip: first 7 diagonal steps get the grip colour, one px thicker.
        for (int i = 0; i <= 7; i++) {
            int x = 2 + i, y = 29 - i;
            for (int o = -1; o <= thick; o++) put(px, S, x + o, y, grip);
        }
        // Guides: small rings hanging under the blank along its length.
        for (int g = 1; g <= guides; g++) {
            int i = 8 + g * (19 / (guides + 1));
            int x = 2 + i, y = 29 - i;
            put(px, S, x, y + 1, 0x2A2A2E);
            put(px, S, x, y + 2, 0x2A2A2E);
            put(px, S, x + 1, y + 2, 0x44464C);
        }
        // Tip ring.
        put(px, S, 29, 3, 0x2A2A2E);
        write(px, S, new File(rodDir, "blank_" + key + ".png"));
        // Mirrored copy for the left hand (rod_m/).
        int[] mir = new int[S * S];
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) mir[y * S + (S - 1 - x)] = px[y * S + x];
        write(mir, S, new File(rodMDir, "blank_" + key + ".png"));
        // 16x16 standalone item icon: downscale by 2 (nearest).
        int[] icon = new int[16 * 16];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) icon[y * 16 + x] = px[(y * 2) * S + x * 2];
        write(icon, 16, new File(base, key + "_rod.png"));
        System.out.println("  " + key);
    }

    static void put(int[] px, int s, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < s && y < s) px[y * s + x] = 0xFF000000 | rgb;
    }

    static int shade(int rgb, int d) {
        int r = Math.min(255, Math.max(0, ((rgb >> 16) & 255) + d));
        int g = Math.min(255, Math.max(0, ((rgb >> 8) & 255) + d));
        int b = Math.min(255, Math.max(0, (rgb & 255) + d));
        return (r << 16) | (g << 8) | b;
    }

    static void write(int[] px, int s, File out) throws Exception {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, s, s, px, 0, s);
        ImageIO.write(img, "png", out);
    }
}
