import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * §america-pack (0.4.0): generates the four US-species item sprites (64x64, side view facing left)
 * in the same hand-pixelled style as the rest of textures/item/fish/. Deterministic — rerun to
 * regenerate identical PNGs. Usage: java tools/GenUsFish.java <outDir>
 */
public final class GenUsFish {
    static int W = 64, H = 64;
    static int[] px;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "common/src/main/resources/assets/riverfishing/textures/item/fish");
        out.mkdirs();
        gen(out, "bluegill", 0x4A6E46, 0x8FA86A, 0xD9C08A, 20, 15, false, "bluegill");
        gen(out, "largemouth_bass", 0x3E5F35, 0x7FA05C, 0xD8D2A8, 26, 12, false, "bass");
        gen(out, "rainbow_trout", 0x5A6E4E, 0xB9BFAE, 0xE8E4D2, 26, 10, false, "trout");
        gen(out, "channel_catfish", 0x475766, 0x76858F, 0xD9D4C4, 27, 11, true, "catfish");
        // §ru-fish (0.4.0): толстолобик / чехонь / синец
        gen(out, "silver_carp", 0x5E6E6E, 0xA8B2B0, 0xE2E4DC, 27, 13, false, "silver");
        gen(out, "sabrefish", 0x4E5A66, 0xC7CCD2, 0xEFF0EA, 29, 7, false, "sabre");
        gen(out, "blue_bream", 0x46586A, 0x9FAAB4, 0xDFE2DE, 22, 12, false, "sinets");
        // §ocean (0.5.0): the coastal + shelf wave.
        gen(out, "mackerel", 0x2E6E6A, 0xB9C4C6, 0xEDEFE9, 26, 8, true, "mackerel");
        gen(out, "herring", 0x3E5468, 0xC6CDD3, 0xF0F1EC, 22, 7, true, "plain");
        gen(out, "garfish", 0x3E7E5E, 0xBCC8C4, 0xEDEFE9, 30, 4, false, "needle");
        gen(out, "seabass", 0x5E6A72, 0xB8BFC4, 0xE9EBE6, 24, 11, true, "spiky");
        gen(out, "flounder", 0x6E5A42, 0x8A7452, 0x8A7452, 24, 15, false, "flat");
        gen(out, "cod", 0x6E6248, 0xA89A74, 0xE2DECC, 26, 12, false, "cod");
        gen(out, "saithe", 0x3A4248, 0x6E787E, 0xC8CDD0, 26, 11, true, "dark");
        gen(out, "conger", 0x4E5A66, 0x76848E, 0xC9CFCE, 29, 6, false, "conger");
        gen(out, "ray", 0x8A7452, 0xA08A62, 0xD9CDB2, 26, 14, false, "ray");
        // §ocean (0.5.0): the pelagic four — trolling targets.
        gen(out, "mahi", 0x2E7E5E, 0x9EB94E, 0xEFE8C2, 26, 11, true, "mahi");
        gen(out, "wahoo", 0x2E4A6E, 0xAEBAC2, 0xE8EBE8, 29, 7, true, "wahoo");
        gen(out, "yellowfin_tuna", 0x24384E, 0x8FA0AC, 0xE6E9E6, 26, 10, true, "tuna");
        gen(out, "barracuda", 0x5E6E76, 0xC2C9CE, 0xF0F1EC, 30, 6, true, "cuda");
        System.out.println("done");
    }

    static void gen(File dir, String name, int back, int flank, int belly, int ax, int by,
                    boolean forkedTail, String kind) throws Exception {
        px = new int[W * H];
        int cx = 28, cy = 34;
        Random rng = new Random(name.hashCode());

        // Tail (behind the body, drawn first so the body overlaps its root).
        int tailBase = cx + ax - 3, tipX = Math.min(W - 3, cx + ax + 9);
        for (int x = tailBase; x <= tipX; x++) {
            double t = (double) (x - tailBase) / (tipX - tailBase);
            int half = (int) Math.round(2 + t * 9);
            for (int y = cy - half; y <= cy + half; y++) {
                if (forkedTail && Math.abs(y - cy) < half * 0.35 && t > 0.45) continue; // the fork notch
                put(x, y, shade(back, -10));
            }
        }
        // Body: ellipse, vertical colour bands (back -> flank -> belly) + top shading.
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double dx = (x - cx) / (double) ax, dy = (y - cy) / (double) by;
                double d = dx * dx + dy * dy;
                if (d <= 1.0) {
                    double v = (y - (cy - by)) / (2.0 * by); // 0 top .. 1 bottom
                    int c = v < 0.35 ? back : v < 0.7 ? flank : belly;
                    if (v < 0.18) c = shade(c, -18);          // dark dorsal ridge
                    if (d > 0.86) c = shade(c, -14);          // soft edge rolloff
                    put(x, y, c);
                }
            }
        }
        // Dorsal fin.
        for (int x = cx - ax / 2; x <= cx + ax / 2; x++) {
            int h = (int) (4 + 3 * Math.sin((x - (cx - ax / 2)) / (double) ax * Math.PI));
            for (int y = cy - by - h; y < cy - by + 1; y++) put(x, y, shade(back, -22));
        }
        // Pectoral + anal fins.
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j <= i; j++) put(cx - ax / 3 + j, cy + 2 + i, shade(flank, -25));
            for (int j = 0; j <= i / 2; j++) put(cx + ax / 3 + j, cy + by - 2 + i / 2, shade(back, -20));
        }
        switch (kind) {
            case "bluegill" -> {
                for (int bar = 0; bar < 6; bar++) {                    // vertical flank bars
                    int bx = cx - ax + 6 + bar * 6;
                    for (int y = cy - by + 4; y <= cy + by - 6; y++)
                        if (in(bx, y)) { put(bx, y, shade(back, -20)); put(bx + 1, y, shade(back, -14)); }
                }
                blob(cx - ax + 7, cy - 2, 2, 0x1A2A1E);               // the dark "ear" flap
                blob(cx - ax / 2, cy + by - 5, 4, 0xC46A2A);          // orange breast
            }
            case "bass" -> {
                for (int x = cx - ax + 3; x <= cx + ax - 4; x++) {     // ragged lateral stripe
                    int yy = cy + ((x * 7) % 3 == 0 ? 1 : 0);
                    put(x, yy, 0x24331F); put(x, yy + 1, 0x2E4227);
                    if (x % 5 == 0) put(x, yy + 2, 0x2E4227);
                }
                for (int i = 0; i < 8; i++) put(cx - ax + 2 + i, cy + 3 + i / 3, 0x22301C); // big jaw
            }
            case "trout" -> {
                for (int x = cx - ax + 3; x <= cx + ax - 3; x++) {     // the pink band
                    put(x, cy, 0xD98A8A); put(x, cy + 1, 0xC97A80);
                }
                for (int i = 0; i < 46; i++) {                         // speckles over back+flank
                    int sx = cx - ax + 3 + rng.nextInt(2 * ax - 6), sy = cy - by + 2 + rng.nextInt(by + 4);
                    if (in(sx, sy)) put(sx, sy, 0x2E3428);
                }
            }
            case "silver" -> { // толстолобик: a massive pale head plate (a third of the body) + gill curve
                for (int y = cy - by; y <= cy + by; y++)
                    for (int x = cx - ax; x < cx - ax + 12; x++)
                        if (in(x, y) && px[y * W + x] != 0) put(x, y, shade(flank, -4));
                for (int i = 0; i < 2 * by - 6; i++) put(cx - ax + 12, cy - by + 3 + i, shade(back, -18));
            }
            case "sabre" -> { // чехонь: straight dark back edge, bright silver flank, LOW lateral line
                for (int x = cx - ax + 2; x <= cx + ax - 3; x++) {
                    put(x, cy - by + 1, shade(back, -20));
                    put(x, cy + 2, 0x8A929C);
                }
                put(cx - ax + 1, cy - by + 2, 0x2A3038); // upturned mouth
                put(cx - ax + 2, cy - by + 1, 0x2A3038);
            }
            case "sinets" -> { // синец: bluish sheen band over the back + darkened fins
                for (int x = cx - ax + 3; x <= cx + ax - 4; x++) put(x, cy - by + 3, 0x5A7086);
                for (int i = 0; i < 5; i++) put(cx - ax + 1 + i, cy - 1 + i / 2, shade(flank, -10));
            }
            case "mackerel" -> { // wavy tiger bars over the back third
                for (int x = cx - ax + 4; x <= cx + ax - 4; x += 3) {
                    int wob = (x % 6 == 0) ? 1 : 0;
                    for (int y = cy - by + 1; y <= cy - 1; y++) put(x + wob, y, shade(back, -16));
                }
            }
            case "plain" -> { // plain bright silver schooler: just a darker straight back line
                for (int x = cx - ax + 2; x <= cx + ax - 3; x++) put(x, cy - by + 1, shade(back, -12));
            }
            case "needle" -> { // сарган: needle beak extending past the head
                for (int i = 1; i <= 7; i++) { put(cx - ax - i, cy, 0x33463E); put(cx - ax - i, cy + 1, 0x415A50); }
                for (int x = cx - ax + 2; x <= cx + ax - 3; x++) put(x, cy - by + 1, shade(back, -14));
            }
            case "spiky" -> { // лаврак: tall spiny dorsal rays
                for (int s = 0; s < 6; s++) {
                    int x = cx - ax / 2 + s * 3;
                    for (int y = cy - by - 5; y < cy - by; y++) put(x, y, shade(back, -24));
                }
            }
            case "flat" -> { // камбала: uniform sandy disc, speckles, tiny tail already drawn
                for (int i = 0; i < 40; i++) {
                    int sx = cx - ax + 3 + rng.nextInt(2 * ax - 6), sy = cy - by + 2 + rng.nextInt(2 * by - 4);
                    if (in(sx, sy) && px[sy * W + sx] != 0) put(sx, sy, (i % 3 == 0) ? 0x4E3E2A : 0x9A855E);
                }
            }
            case "cod" -> { // треска: speckled back, pale lateral line, chin barbel
                for (int i = 0; i < 34; i++) {
                    int sx = cx - ax + 4 + rng.nextInt(2 * ax - 8), sy = cy - by + 2 + rng.nextInt(by);
                    if (in(sx, sy)) put(sx, sy, shade(back, -22));
                }
                for (int x = cx - ax + 3; x <= cx + ax - 3; x++) put(x, cy, 0xD9D4BE);
                for (int i = 0; i < 4; i++) put(cx - ax + 4, cy + by - 3 + i, 0x3E362A);
            }
            case "dark" -> { // сайда: dark cod cousin — straight pale lateral line only
                for (int x = cx - ax + 3; x <= cx + ax - 3; x++) put(x, cy, 0xB9BFC2);
            }
            case "conger" -> { // морской угорь: dorsal ridge running the whole back
                for (int x = cx - ax + 4; x <= cx + ax + 6 && x < W - 2; x++) put(x, cy - by, shade(back, -18));
            }
            case "ray" -> { // скат: flat disc — wing shading + long thin tail whip
                for (int i = 0; i < 26; i++) {
                    int sx = cx - ax + 4 + rng.nextInt(2 * ax - 8), sy = cy - by + 3 + rng.nextInt(2 * by - 6);
                    if (in(sx, sy) && px[sy * W + sx] != 0) put(sx, sy, shade(flank, -14));
                }
                for (int i = 0; i <= 12; i++) put(cx + ax - 2 + i / 2, cy - i / 3, 0x6E5E42);
            }
            case "mahi" -> { // махи: the steep blunt forehead + golden flank wash
                for (int y = cy - by; y <= cy - 2; y++)
                    for (int x = cx - ax; x <= cx - ax + 6; x++)
                        if (in(x, y) && px[y * W + x] != 0) put(x, y, shade(back, -8));
                for (int i = 0; i < 22; i++) {
                    int sx = cx - ax + 8 + rng.nextInt(2 * ax - 12), sy = cy - 2 + rng.nextInt(by);
                    if (in(sx, sy) && px[sy * W + sx] != 0) put(sx, sy, 0xC9B94E);
                }
            }
            case "wahoo" -> { // ваху: full-height tiger bars down the flank
                for (int x = cx - ax + 5; x <= cx + ax - 5; x += 4)
                    for (int y = cy - by + 1; y <= cy + by - 2; y++) put(x, y, shade(back, -10));
            }
            case "tuna" -> { // тунец: torpedo — yellow finlet row along the rear back and belly
                for (int i = 0; i < 6; i++) {
                    int x = cx + ax / 3 + i * 2;
                    put(x, cy - by - 1, 0xE8C21E);
                    put(x, cy + by, 0xE8C21E);
                }
            }
            case "cuda" -> { // барракуда: pike-like jaw + scattered dark blotches
                for (int i = 0; i < 8; i++) put(cx - ax + 1 + i, cy + 1 + (i % 2), 0x2E3A42);
                for (int i = 0; i < 14; i++) {
                    int sx = cx - 2 + rng.nextInt(ax), sy = cy - by + 2 + rng.nextInt(2 * by - 3);
                    if (in(sx, sy) && px[sy * W + sx] != 0) put(sx, sy, shade(back, -14));
                }
            }
            case "catfish" -> {
                for (int i = 0; i < 7; i++) {                          // barbels (whiskers)
                    put(cx - ax + 2 - i / 2, cy + 4 + i, 0x2A3038);
                    put(cx - ax + 5 - i / 3, cy + 5 + i, 0x333A44);
                    put(cx - ax + 8, cy + 6 + i / 2, 0x2A3038);
                }
                for (int y = cy - by; y <= cy + by; y++)               // flat blunt head front
                    for (int x = cx - ax; x < cx - ax + 4; x++)
                        if (in(x, y) && px[y * W + x] != 0) put(x, y, shade(back, -6));
            }
        }
        // Eye ("silver" sits famously LOW; the flatfish/ray eyes sit ON TOP of the disc).
        boolean topEye = kind.equals("flat") || kind.equals("ray");
        int eyeX = kind.equals("silver") ? cx - ax + 6 : topEye ? cx - ax + 8 : cx - ax + 9;
        int eyeY = kind.equals("silver") ? cy + 3 : topEye ? cy - by + 4 : cy - by / 2;
        blob(eyeX, eyeY, 2, 0xF0EFE6);
        put(eyeX, eyeY, 0x14181C);
        outline();
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, W, H, px, 0, W);
        ImageIO.write(img, "png", new File(dir, name + ".png"));
        System.out.println("  " + name + ".png");
    }

    static boolean in(int x, int y) { return x >= 0 && y >= 0 && x < W && y < H; }
    static void put(int x, int y, int rgb) { if (in(x, y)) px[y * W + x] = 0xFF000000 | rgb; }
    static void blob(int cx, int cy, int r, int rgb) {
        for (int y = cy - r; y <= cy + r; y++) for (int x = cx - r; x <= cx + r; x++)
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) put(x, y, rgb);
    }
    static int shade(int rgb, int d) {
        int r = Math.min(255, Math.max(0, ((rgb >> 16) & 255) + d));
        int g = Math.min(255, Math.max(0, ((rgb >> 8) & 255) + d));
        int b = Math.min(255, Math.max(0, (rgb & 255) + d));
        return (r << 16) | (g << 8) | b;
    }
    static void outline() { // darken every filled pixel that borders a transparent one
        int[] copy = px.clone();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (copy[y * W + x] == 0) continue;
            boolean edge = x == 0 || y == 0 || x == W - 1 || y == H - 1
                    || copy[y * W + x - 1] == 0 || copy[y * W + x + 1] == 0
                    || copy[(y - 1) * W + x] == 0 || copy[(y + 1) * W + x] == 0;
            if (edge) px[y * W + x] = 0xFF000000 | shade(copy[y * W + x] & 0xFFFFFF, -70);
        }
    }
}
