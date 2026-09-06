package com.riverfishing.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riverfishing.RiverFishing;
import com.riverfishing.tackle.TiedDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §tying: a tied lure's icon IS its drawing. The 16×16 canvas becomes a texture the first time a
 * design is seen (keyed by its hash, a small LRU so a server full of patterns cannot fill VRAM) and
 * is drawn as a two-sided pixel quad in every item context — the slot, the hand, the ground, the
 * frame.
 */
public final class TiedLureItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static TiedLureItemRenderer instance;
    private static final int CACHE = 256;
    private static final Map<Integer, ResourceLocation> TEXTURES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, ResourceLocation> e) {
            if (size() <= CACHE) return false;
            Minecraft.getInstance().getTextureManager().release(e.getValue());
            return true;
        }
    };

    public static TiedLureItemRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new TiedLureItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    public TiedLureItemRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher dispatcher,
                                net.minecraft.client.model.geom.EntityModelSet models) {
        super(dispatcher, models);
    }

    /** The texture for a design, built on first sight. */
    public static ResourceLocation texture(byte[] design) {
        int h = TiedDesign.hash(design);
        ResourceLocation loc = TEXTURES.get(h);
        if (loc != null) return loc;
        NativeImage img = new NativeImage(TiedDesign.SIZE, TiedDesign.SIZE, false);
        for (int y = 0; y < TiedDesign.SIZE; y++) {
            for (int x = 0; x < TiedDesign.SIZE; x++) {
                int px = design[y * TiedDesign.SIZE + x];
                int rgb = TiedDesign.rgb(px);
                // NativeImage is ABGR; a thin darker edge where a pixel meets nothing reads as an outline
                boolean edge = px != 0 && (x == 0 || y == 0 || x == 15 || y == 15
                        || design[y * 16 + x - 1] == 0 || design[y * 16 + x + 1] == 0
                        || design[(y - 1) * 16 + x] == 0 || design[(y + 1) * 16 + x] == 0);
                if (edge) rgb = ((rgb >> 16 & 255) * 3 / 4) << 16 | ((rgb >> 8 & 255) * 3 / 4) << 8 | (rgb & 255) * 3 / 4;
                int abgr = px == 0 ? 0 : 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
                img.setPixelRGBA(x, y, abgr);
            }
        }
        loc = Minecraft.getInstance().getTextureManager().register("tied_" + Integer.toHexString(h), new DynamicTexture(img));
        TEXTURES.put(h, loc);
        return loc;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        byte[] design = TiedDesign.design(stack);
        if (design == null) return;
        ResourceLocation tex = texture(design);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);   // cancel the ItemRenderer's centring, as the fish icon does
        Matrix4f m = pose.last().pose();
        float t = 1f / 32f;   // a sixteenth of a block thick, like a generated item
        quad(vc, m, -0.5f, 0.5f, t, 0f, 1f, light, overlay, 0f, 0f, 1f);
        quad(vc, m, 0.5f, -0.5f, -t, 1f, 0f, light, overlay, 0f, 0f, -1f);
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
}
