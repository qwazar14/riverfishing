package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.RiverFishing;
import com.riverfishing.client.platform.ClientPlatform;
import com.riverfishing.item.FryItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * §breeding: the fry bucket is drawn as the shoal it holds — three copies of the SPECIES' own sprite
 * (the {@code item/fish_icon/<species>} model {@link FishItemRenderer} draws), shrunk to fry size and
 * staggered the way the static {@code fry.png} staggers its three silver fish. A stack with no species
 * (the creative tab) falls back to that static icon, so the picture is never blank.
 */
public final class FryItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static FryItemRenderer instance;

    /** The static three-silver-fish icon as a plain generated model (the fallback). */
    public static final ResourceLocation FALLBACK = RiverFishing.id("item/fry_icon");

    /**
     * The three fish of tools/gen_fry_icon.py: bodies at (2,2), (7,6), (3,10) in 16-px icon space, each
     * 8x3 with the outline, so their centres sit at (5.5,3), (10.5,7), (6.5,11). Rows are {x, y, scale,
     * mirrored}: x/y as offsets from the icon centre in model units (1/16 per pixel, +y up), scale as
     * the fraction of a full sprite, and the middle fish — a touch larger — facing the other way.
     */
    private static final float[][] FISH = {
            {-2.5f / 16f,  5f / 16f, 0.40f, 0f},
            {-1.5f / 16f, -3f / 16f, 0.40f, 0f},
            { 2.5f / 16f,  1f / 16f, 0.45f, 1f},
    };

    public static FryItemRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new FryItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    public FryItemRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher dispatcher,
                           net.minecraft.client.model.geom.EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel missing = mc.getModelManager().getMissingModel();
        ItemRenderer ir = mc.getItemRenderer();
        ResourceLocation sp = FryItem.species(stack);
        BakedModel fish = sp == null ? null : ClientPlatform.bakedModel(FishItemRenderer.iconModel(sp.getPath()));
        if (fish == null || fish == missing) {
            BakedModel fallback = ClientPlatform.bakedModel(FALLBACK);
            if (fallback == null || fallback == missing) return;
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5); // cancel the ItemRenderer's centring, as FishItemRenderer does
            ir.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, fallback);
            pose.popPose();
            return;
        }
        for (int i = 0; i < FISH.length; i++) {
            float[] f = FISH[i];
            pose.pushPose();
            // a hair of depth per fish so the flat sprites never z-fight where they overlap
            pose.translate(0.5 + f[0], 0.5 + f[1], 0.5 + i * 0.02);
            if (f[3] > 0f) pose.mulPose(Axis.YP.rotationDegrees(180f)); // mirror: the sprite is two-faced
            pose.scale(f[2], f[2], f[2]);
            ir.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, fish);
            pose.popPose();
        }
    }
}
