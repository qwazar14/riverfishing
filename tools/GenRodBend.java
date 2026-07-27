import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * §rod-bend: generates the 6 bent variants of every rod blank sprite by a progressive downward
 * arc-shear — the butt (reel end) stays put, the tip droops by amp * u^2 along the rod axis.
 * Normal sprites run butt-left → tip-right; mirrored (rod_m) the other way. Rerun after adding a
 * rod or redrawing a blank, then rerun {@code tools/gen_dynamic_icons.py}.
 *
 * <p>§26.1: SPRITES ONLY. The 1.21.1 BEWLR needed a one-off {@code item/generated} model per
 * sprite; the data-driven icons don't — {@code gen_dynamic_icons.py} writes the layer models
 * (with their per-context display transforms) and the range_dispatch that picks a bucket.
 *
 * <p>Run: {@code java tools/GenRodBend.java}
 */
public final class GenRodBend {
    private static final double[] AMP = {1.2, 2.2, 3.4, 4.7, 6.1, 7.6}; // px at 32px, bend1..6

    public static void main(String[] args) throws Exception {
        Path assets = Path.of("common/src/main/resources/assets/riverfishing");
        int made = 0;
        for (String dir : new String[]{"rod", "rod_m"}) {
            boolean mirroredDir = dir.endsWith("_m");
            File texDir = assets.resolve("textures/item/" + dir).toFile();
            for (File f : texDir.listFiles()) {
                String name = f.getName();
                if (!name.startsWith("blank_") || !name.endsWith(".png") || name.contains("_bend")) continue;
                String base = name.substring(0, name.length() - 4);
                BufferedImage src = ImageIO.read(f);
                int w = src.getWidth(), h = src.getHeight();
                for (int b = 1; b <= AMP.length; b++) {
                    double amp = AMP[b - 1] * (w / 32.0);
                    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    for (int x = 0; x < w; x++) {
                        double u = mirroredDir ? (w - 1.0 - x) / (w - 1.0) : x / (w - 1.0);
                        int dy = (int) Math.round(amp * u * u);
                        for (int y = 0; y < h; y++) {
                            int sy = y - dy;
                            out.setRGB(x, y, sy >= 0 && sy < h ? src.getRGB(x, sy) : 0);
                        }
                    }
                    ImageIO.write(out, "png", new File(texDir, base + "_bend" + b + ".png"));
                    made++;
                }
            }
        }
        System.out.println("wrote " + made + " bent sprites (run gen_dynamic_icons.py next)");
    }
}
