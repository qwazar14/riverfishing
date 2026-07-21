import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * §fish-sprites v3: the new species are built FROM the hand-drawn originals — a luminance-ranked
 * palette swap of the closest-bodied donor sprite keeps every hand-placed pixel (outline, fin rays,
 * eye, texture), then species markings are added by DARKENING/TINTING existing pixels so the drawn
 * texture survives. This is the koi technique, generalised. Deterministic; rerun to regenerate.
 * Usage: java tools/GenFishSwap.java <fishDir>
 */
public final class GenFishSwap {
    static File dir;
    static int W, H;
    static int[] px;

    public static void main(String[] args) throws Exception {
        dir = new File(args.length > 0 ? args[0] : "common/src/main/resources/assets/riverfishing/textures/item/fish");
        // id, donor, 6-stop ramp dark->light, accent (0 = none: warm donor tones go through the ramp)
        make("bluegill", "perch", ramp(0x1E2A1C, 0x3E5A38, 0x5E7E4A, 0x8FA86A, 0xB9C48A, 0xE2D2A0), 0xC46A2A);
        make("largemouth_bass", "zander", ramp(0x18241A, 0x2E4A2C, 0x4A6A3E, 0x7FA05C, 0xAABF86, 0xDCD8AC), 0);
        make("rainbow_trout", "trout", ramp(0x20261E, 0x46543E, 0x6E7A5E, 0x9EA88E, 0xC6CBB4, 0xEDEADA), 0);
        make("channel_catfish", "catfish", ramp(0x161C24, 0x2E3A46, 0x475766, 0x76858F, 0xA8B2B6, 0xD9D4C4), 0);
        make("silver_carp", "grass_carp", ramp(0x1C2222, 0x3E4A4A, 0x5E6E6E, 0x93A2A0, 0xC2CCC8, 0xE8EAE2), 0);
        make("sabrefish", "asp", ramp(0x1C222A, 0x3A4650, 0x5E6A76, 0x9AA6B0, 0xCDD2D8, 0xF2F3EE), 0);
        make("blue_bream", "white_bream", ramp(0x1A2028, 0x36445A, 0x54687E, 0x8496A8, 0xB8C2CC, 0xE4E7E4), 0);
        make("mackerel", "asp", ramp(0x14201E, 0x1E4A46, 0x2E6E6A, 0x7EA6A2, 0xC2CFCE, 0xF0F1EA), 0);
        make("herring", "bleak", ramp(0x181E26, 0x2E3E52, 0x4A5E74, 0x8A9AAA, 0xC6CDD3, 0xF2F2EC), 0);
        make("garfish", "sterlet", ramp(0x14201A, 0x2A503E, 0x3E7E5E, 0x86AA96, 0xC4D0C8, 0xEEF0E8), 0);
        make("seabass", "zander", ramp(0x1A2024, 0x38444C, 0x5E6A72, 0x94A0A8, 0xC4CBD0, 0xEDEEE8), 0);
        make("flounder", "crucian_carp", ramp(0x241C12, 0x4A3A24, 0x6E5A42, 0x8A7452, 0xA8926A, 0xC9B892), 0);
        make("cod", "burbot", ramp(0x221E12, 0x4A4228, 0x6E6248, 0x9A8C64, 0xC2B692, 0xE6E0C8), 0);
        make("saithe", "zander", ramp(0x14181C, 0x262E36, 0x3A4650, 0x64707A, 0x9AA4AC, 0xD2D6D6), 0);
        make("conger", "eel", ramp(0x181E24, 0x323E4A, 0x4E5A66, 0x7C8892, 0xAEB6BC, 0xDCDFDC), 0);
        make("ray", "crucian_carp", ramp(0x241C10, 0x50402A, 0x74603E, 0x9A8258, 0xBCA678, 0xDCCCA4), 0);
        make("mahi", "chub", ramp(0x14240E, 0x2A5E2A, 0x3E8E4E, 0x8EB44E, 0xC8C87E, 0xF0E8B8), 0xC8A21E);
        make("wahoo", "pike", ramp(0x121A26, 0x203852, 0x2E4A6E, 0x6E86A0, 0xAEBAC6, 0xE8EBEA), 0);
        make("yellowfin_tuna", "trout", ramp(0x101822, 0x1A2E44, 0x24384E, 0x5E7288, 0xA0AEB8, 0xE6E9E6), 0);
        make("barracuda", "pike", ramp(0x1A2026, 0x363E46, 0x5E6E76, 0x94A2AA, 0xC8CFD4, 0xF2F3EE), 0);
        make("blue_marlin", "pike", ramp(0x101A2A, 0x1A3252, 0x24466E, 0x5E7A96, 0x9EB0BE, 0xE4E8EA), 0);
        make("sailfish", "pike", ramp(0x121E2E, 0x1E3A5A, 0x2E4E72, 0x6A88A0, 0xA6B6C2, 0xE6E9E8), 0);
        make("swordfish", "pike", ramp(0x141A20, 0x28323C, 0x3A4652, 0x6E7A86, 0xA6AEB6, 0xE2E5E4), 0);
        make("mako", "zander", ramp(0x141C26, 0x2A3A4E, 0x3E5266, 0x7A8A9A, 0xB2BEC8, 0xEEEFEC), 0);
        // north-wave (0.5.0): the taiga / salmon / giants twelve.
        make("rotan", "crucian_carp", ramp(0x1A1C10, 0x343A22, 0x4E5432, 0x6E7448, 0x8E9060, 0xB2AC80), 0);
        make("nase", "chub", ramp(0x181E1C, 0x2E3C38, 0x4E5E58, 0x86948E, 0xBEC6C0, 0xEAECE4), 0);
        make("vimba", "white_bream", ramp(0x181C24, 0x323E4E, 0x4E5E70, 0x8494A4, 0xBCC5CE, 0xEBEDEA), 0);
        make("smelt", "bleak", ramp(0x161E1A, 0x2C4038, 0x486456, 0x88A494, 0xC2D2C6, 0xF0F4EC), 0);
        make("whitefish", "asp", ramp(0x161A1E, 0x303A44, 0x505E6A, 0x8C99A4, 0xC6CDD2, 0xF4F4EE), 0);
        make("char", "trout", ramp(0x1A1E1A, 0x363E36, 0x525E52, 0x808878, 0xAEB0A0, 0xD8D4C4), 0);
        make("lenok", "trout", ramp(0x1E1E12, 0x40402A, 0x5E6040, 0x8C8A5C, 0xB8B282, 0xE0D8AC), 0);
        make("taimen", "pike", ramp(0x1C1C16, 0x38382C, 0x545444, 0x807E66, 0xAAA68A, 0xD4CFB4), 0);
        make("salmon", "asp", ramp(0x161A1C, 0x323C42, 0x566068, 0x909AA2, 0xC8CDD0, 0xF2F2EC), 0);
        make("pink_salmon", "white_bream", ramp(0x171B20, 0x333E48, 0x525F6C, 0x8B98A2, 0xC2C9CE, 0xF0F1EC), 0);
        make("sturgeon", "sterlet", ramp(0x14120E, 0x2C281E, 0x46402F, 0x6A6248, 0x928866, 0xBCB08C), 0);
        make("halibut", "crucian_carp", ramp(0x1A140C, 0x362A18, 0x524026, 0x6E5836, 0x8E744A, 0xB49A6A), 0);
        System.out.println("done");
    }

    static int[] ramp(int... stops) { return stops; }

    static void make(String id, String donor, int[] ramp, int accent) throws Exception {
        BufferedImage src = ImageIO.read(new File(dir, donor + ".png"));
        W = src.getWidth(); H = src.getHeight();
        px = new int[W * H];
        // Luminance range of the donor's opaque pixels.
        double lMin = 1e9, lMax = -1e9;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int argb = src.getRGB(x, y);
            if ((argb >>> 24) < 128) continue;
            double l = lum(argb);
            if (l < lMin) lMin = l;
            if (l > lMax) lMax = l;
        }
        // Map every pixel through the target ramp at its luminance rank; warm saturated tones
        // (the orange perch fins) optionally go through the accent instead.
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int argb = src.getRGB(x, y);
            if ((argb >>> 24) < 128) { px[y * W + x] = 0; continue; }
            int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            double p = (lum(argb) - lMin) / Math.max(1.0, lMax - lMin);
            int c;
            if (accent != 0 && r > g * 1.3 && r > b * 1.5 && r > 90) {
                c = shade(accent, (int) ((p - 0.5) * 110)); // fin/breast accent keeps its own shading
            } else {
                c = sample(ramp, p);
            }
            px[y * W + x] = 0xFF000000 | c;
        }
        mark(id, new Random(id.hashCode()));
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, W, H, px, 0, W);
        ImageIO.write(out, "png", new File(dir, id + ".png"));
        System.out.println("  " + id + " <- " + donor);
    }

    /** Species markings by darkening/tinting EXISTING pixels — the drawn texture stays. */
    static void mark(String id, Random rng) {
        int[] bb = bounds();
        int x0 = bb[0], y0 = bb[1], x1 = bb[2], y1 = bb[3], cyM = (y0 + y1) / 2;
        switch (id) {
            case "bluegill" -> { for (int sy = cyM - 3; sy <= cyM - 1; sy++) for (int sx = x0 + 8; sx <= x0 + 10; sx++) tint(sx, sy, -70); }                       // the dark ear flap
            case "largemouth_bass" -> { // ragged dark lateral stripe
                for (int x = x0 + 6; x <= x1 - 8; x++) tint(x, cyM + ((x / 3) % 2), -55);
            }
            case "rainbow_trout" -> { // pink band + fine dark speckles
                for (int x = x0 + 6; x <= x1 - 6; x++) { pink(x, cyM); pink(x, cyM + 1); }
                speckles(rng, 40, x0 + 5, y0 + 3, x1 - 6, cyM + 3, -50);
            }
            case "mackerel" -> { // wavy tiger bars over the back
                for (int x = x0 + 8; x <= x1 - 8; x += 3)
                    for (int y = y0 + 2; y <= cyM - 1; y++) tint(x + ((y & 2) >> 1), y, -45);
            }
            case "flounder", "ray" -> speckles(rng, 55, x0 + 4, y0 + 3, x1 - 5, y1 - 3, -45);
            case "cod" -> speckles(rng, 30, x0 + 6, y0 + 2, x1 - 6, cyM + 2, -40);
            case "saithe" -> { for (int x = x0 + 5; x <= x1 - 6; x++) tint(x, cyM, 55); } // pale lateral line
            case "wahoo" -> { for (int x = x0 + 10; x <= x1 - 8; x += 4)
                    for (int y = y0 + 2; y <= y1 - 4; y++) tint(x, y, -40); }
            case "yellowfin_tuna" -> { // the yellow finlet row
                for (int i = 0; i < 6; i++) { dot(x1 - 16 + i * 2, y0 + 1, 0xE8C21E); dot(x1 - 16 + i * 2, y1 - 1, 0xE8C21E); }
            }
            case "mahi" -> speckles(rng, 26, x0 + 8, cyM - 2, x1 - 8, y1 - 4, 45);   // golden glints
            case "blue_marlin" -> bill(x0, cyM - 1, 8, 0x1A2A3E);
            case "sailfish" -> { bill(x0, cyM - 1, 7, 0x1E3248); sail(x0, x1, y0); }
            case "swordfish" -> bill(x0, cyM, 11, 0x22303C);
            case "mako" -> { for (int g = 0; g < 4; g++) // gill slits
                    for (int y = cyM - 3; y <= cyM + 1; y++) tint(x0 + 12 + g * 2, y, -45); }
            case "rotan" -> speckles(rng, 45, x0 + 4, y0 + 2, x1 - 4, y1 - 2, -50);           // dark blotches
            case "nase" -> { for (int x = x0 + 5; x <= x1 - 6; x++) tint(x, cyM, -55); }       // dark lateral line
            case "smelt" -> { for (int x = x0 + 5; x <= x1 - 6; x++) tint(x, cyM, 50); }       // cucumber-silver stripe
            case "char" -> { // the spawning char: orange belly band + pale flank spots
                for (int x = x0 + 4; x <= x1 - 4; x++) for (int y = y1 - 2; y <= y1; y++) blend(x, y, 0xC85A2A, 0.5);
                speckles(rng, 26, x0 + 5, cyM - 3, x1 - 6, y1 - 3, 55);
            }
            case "lenok" -> speckles(rng, 50, x0 + 5, y0 + 2, x1 - 5, cyM + 3, -45);           // dense dark speckles
            case "taimen" -> { // the trademark red tail + sparse dark crosses
                for (int x = x1 - 5; x <= x1; x++) for (int y = y0; y <= y1; y++) blend(x, y, 0xB04030, 0.55);
                speckles(rng, 28, x0 + 6, y0 + 2, x1 - 8, cyM + 3, -40);
            }
            case "salmon" -> speckles(rng, 20, x0 + 6, y0 + 2, x1 - 8, cyM + 1, -45);          // sparse X-spots
            case "pink_salmon" -> speckles(rng, 30, x0 + (x1 - x0) / 2, y0 + 2, x1, y1 - 2, -45); // spotted rear+tail
            case "halibut" -> speckles(rng, 60, x0 + 4, y0 + 3, x1 - 5, y1 - 3, -40);          // mottled topside
            default -> { }
        }
    }

    // ---- marking helpers (all operate on existing opaque pixels) ----

    static int[] bounds() {
        int x0 = W, y0 = H, x1 = 0, y1 = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            if (px[y * W + x] != 0) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
        return new int[]{x0, y0, x1, y1};
    }

    static void tint(int x, int y, int d) {
        if (x >= 0 && y >= 0 && x < W && y < H && px[y * W + x] != 0)
            px[y * W + x] = 0xFF000000 | shade(px[y * W + x] & 0xFFFFFF, d);
    }

    static void blend(int x, int y, int rgb, double f) { // blend an existing pixel toward a colour
        if (x < 0 || y < 0 || x >= W || y >= H || px[y * W + x] == 0) return;
        int c = px[y * W + x] & 0xFFFFFF;
        int r = mix((c >> 16) & 255, (rgb >> 16) & 255, f), g = mix((c >> 8) & 255, (rgb >> 8) & 255, f),
            b = mix(c & 255, rgb & 255, f);
        px[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static void pink(int x, int y) { // blend the pixel 45% toward rose — the rainbow band
        if (x < 0 || y < 0 || x >= W || y >= H || px[y * W + x] == 0) return;
        int c = px[y * W + x] & 0xFFFFFF;
        int r = mix((c >> 16) & 255, 0xD9, 0.45), g = mix((c >> 8) & 255, 0x7E, 0.45), b = mix(c & 255, 0x8E, 0.45);
        px[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static void dot(int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < W && y < H) px[y * W + x] = 0xFF000000 | rgb;
    }

    static void speckles(Random rng, int n, int x0, int y0, int x1, int y1, int d) {
        for (int i = 0; i < n; i++) tint(x0 + rng.nextInt(Math.max(1, x1 - x0)), y0 + rng.nextInt(Math.max(1, y1 - y0)), d);
    }

    /** A billfish bill: shift the sprite right if the margin is tight, then draw the spear. */
    static void bill(int snoutX, int snoutY, int want, int rgb) {
        int len = Math.min(want, Math.max(3, snoutX - 1));
        if (len < want) { // not enough left margin: shift the whole sprite right by the shortfall
            int shift = Math.min(want - len, Math.max(0, W - 2 - bounds()[2]));
            if (shift > 0) {
                int[] moved = new int[W * H];
                for (int y = 0; y < H; y++) for (int x = W - 1; x >= shift; x--) moved[y * W + x] = px[y * W + (x - shift)];
                px = moved;
                snoutX += shift;
                len = Math.min(want, snoutX - 1);
            }
        }
        for (int i = 1; i <= len; i++) {
            dot(snoutX - i, snoutY, rgb);
            if (i < len - 1) dot(snoutX - i, snoutY + 1, shade(rgb, 22));
        }
    }

    /** The sailfish sail: a tall rayed fin raised over the mid-back, drawn in the mapped back tone. */
    static void sail(int x0, int x1, int y0) {
        int from = x0 + (x1 - x0) / 3, to = x0 + 2 * (x1 - x0) / 3;
        for (int x = from; x <= to; x++) {
            int h = 5 + (int) (4 * Math.sin((x - from) / (double) (to - from) * Math.PI));
            int base = topAt(x);
            if (base < 0) continue;
            int c = px[base * W + x] & 0xFFFFFF;
            for (int y = base - h; y < base; y++)
                dot(x, y, shade(c, ((x & 1) == 0 ? -18 : 2)));
        }
    }

    static int topAt(int x) {
        for (int y = 0; y < H; y++) if (px[y * W + x] != 0) return y;
        return -1;
    }

    // ---- colour math ----

    static double lum(int argb) {
        return 0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255);
    }

    static int sample(int[] ramp, double p) {
        double f = Math.max(0, Math.min(0.9999, p)) * (ramp.length - 1);
        int i = (int) f;
        double t = f - i;
        int a = ramp[i], b = ramp[i + 1];
        return (mix((a >> 16) & 255, (b >> 16) & 255, t) << 16)
                | (mix((a >> 8) & 255, (b >> 8) & 255, t) << 8)
                | mix(a & 255, b & 255, t);
    }

    static int mix(int a, int b, double t) { return (int) Math.round(a + (b - a) * t); }

    static int shade(int rgb, int d) {
        int r = Math.min(255, Math.max(0, ((rgb >> 16) & 255) + d));
        int g = Math.min(255, Math.max(0, ((rgb >> 8) & 255) + d));
        int b = Math.min(255, Math.max(0, (rgb & 255) + d));
        return (r << 16) | (g << 8) | b;
    }
}
