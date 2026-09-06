package com.riverfishing.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.riverfishing.RiverFishing;
import com.riverfishing.tackle.TiedDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * §tying (26.x): a tied lure's icon IS its drawing. The 16×16 canvas becomes a texture the first time
 * a design is seen (keyed by its hash, a small LRU so a server full of patterns cannot fill VRAM) and
 * is submitted as a two-sided pixel quad in every item context. The 26.x twin of the 1.21
 * {@code TiedLureItemRenderer}, on the special-model-renderer path the fry bucket already uses.
 */
public final class TiedLureSpecialRenderer implements SpecialModelRenderer<byte[]> {
    public static final Identifier ID = RiverFishing.id("tied_lure");
    private static final int CACHE = 256;
    private static final Map<Integer, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Identifier> e) {
            if (size() <= CACHE) return false;
            Minecraft.getInstance().getTextureManager().release(e.getValue());
            return true;
        }
    };

    /** The data-driven half: {@code {"type": "riverfishing:tied_lure"}} in the client item definition. */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<byte[]> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }

        @Override public SpecialModelRenderer<byte[]> bake(BakingContext context) { return new TiedLureSpecialRenderer(); }
    }

    /** The texture for a design, built on first sight. */
    public static Identifier texture(byte[] design) {
        int h = TiedDesign.hash(design);
        Identifier loc = TEXTURES.get(h);
        if (loc != null) return loc;
        NativeImage img = new NativeImage(TiedDesign.SIZE, TiedDesign.SIZE, false);
        for (int y = 0; y < TiedDesign.SIZE; y++) {
            for (int x = 0; x < TiedDesign.SIZE; x++) {
                int px = design[y * TiedDesign.SIZE + x];
                int rgb = TiedDesign.rgb(px);
                boolean edge = px != 0 && (x == 0 || y == 0 || x == 15 || y == 15
                        || design[y * 16 + x - 1] == 0 || design[y * 16 + x + 1] == 0
                        || design[(y - 1) * 16 + x] == 0 || design[(y + 1) * 16 + x] == 0);
                if (edge) rgb = ((rgb >> 16 & 255) * 3 / 4) << 16 | ((rgb >> 8 & 255) * 3 / 4) << 8 | (rgb & 255) * 3 / 4;
                int abgr = px == 0 ? 0 : 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
                img.setPixelABGR(x, y, abgr);
            }
        }
        String name = "tied_" + Integer.toHexString(h);
        loc = RiverFishing.id("dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(() -> name, img));
        TEXTURES.put(h, loc);
        return loc;
    }

    @Override
    public byte[] extractArgument(ItemStack stack) {
        return TiedDesign.design(stack);
    }

    @Override
    public void submit(byte[] design, PoseStack pose, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outlineColor) {
        if (design == null) return;
        Identifier tex = texture(design);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);   // the special renderer's origin is the item's corner, like a block
        collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(tex),
                (posePose, vc) -> {
                    Matrix4f m = posePose.pose();
                    float t = 1f / 32f;
                    quad(vc, m, -0.5f, 0.5f, t, 0f, 1f, light, overlay, 0f, 0f, 1f);
                    quad(vc, m, 0.5f, -0.5f, -t, 1f, 0f, light, overlay, 0f, 0f, -1f);
                });
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float xa, float xb, float z, float ua, float ub,
                             int light, int overlay, float nx, float ny, float nz) {
        vtx(vc, m, xa, -0.5f, z, ua, 1f, light, overlay, nx, ny, nz);
        vtx(vc, m, xb, -0.5f, z, ub, 1f, light, overlay, nx, ny, nz);
        vtx(vc, m, xb, 0.5f, z, ub, 0f, light, overlay, nx, ny, nz);
        vtx(vc, m, xa, 0.5f, z, ua, 0f, light, overlay, nx, ny, nz);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                            int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        for (int i = 0; i < 8; i++) out.accept(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
    }
}
