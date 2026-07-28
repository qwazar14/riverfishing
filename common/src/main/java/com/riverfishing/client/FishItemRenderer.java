package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.RiverFishing;
import com.riverfishing.item.FishItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a caught fish scaled by its weight (§fish-scale): a 10 kg carp fills the slot, a 30 g roach
 * is small. A Forge {@link BlockEntityWithoutLevelRenderer} hooked via
 * {@code FishItem.initializeClient}; the fish item model is {@code builtin/entity}, and the actual
 * sprite lives in an {@code item/fish_icon/<species>} model that this renderer draws, scaled.
 */
public final class FishItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static FishItemRenderer instance;

    public static FishItemRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new FishItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    public FishItemRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher dispatcher,
                            net.minecraft.client.model.geom.EntityModelSet models) {
        super(dispatcher, models);
    }

    /** Icon model location for a species (registered as an additional model). */
    public static ResourceLocation iconModel(String speciesPath) {
        return RiverFishing.id("item/fish_icon/" + speciesPath);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation sp = FishItem.getSpecies(stack);
        if (sp == null) return;
        Minecraft mc = Minecraft.getInstance();
        ModelManager mm = mc.getModelManager();
        BakedModel model = com.riverfishing.client.platform.ClientPlatform.bakedModel(iconModel(sp.getPath()));
        if (model == null || model == mm.getMissingModel()) return;

        float s = FishItem.getIconScale(stack);
        // §fish-scale (0.5.0): the giants are capped per context — an inventory slot or item frame
        // stays readable at ≤2, but in hand, dropped on the ground or mounted the monsters are the
        // spectacle they should be (a 380 cm Megalodon towers over the player at 5 blocks).
        float cap = (ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED
                || ctx == ItemDisplayContext.HEAD) ? 2.0f : 5.0f;
        s = Math.min(s, cap);
        // §gui-readability: in a SLOT even a gudgeon should be recognisable — floor the GUI scale
        // (the ground drop and the aquarium keep the true-size 0.45 floor).
        if (ctx == ItemDisplayContext.GUI) s = Math.max(s, 0.8f);
        // §release: a dropped fish shrinks away over its final 2 s in the water (the client mirrors
        // the server countdown stored in NBT). Only the loose item entity shrinks, not the inventory.
        if ((ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.NONE) && mc.level != null) {
            net.minecraft.nbt.CompoundTag tag = com.riverfishing.item.StackNbt.get(stack);
            if (tag.contains(FishItem.TAG_RELEASE_AT)) {
                float remain = tag.getLong(FishItem.TAG_RELEASE_AT) - mc.level.getGameTime();
                s *= Mth.clamp(remain / (float) FishItem.RELEASE_TICKS, 0f, 1f);
                if (s <= 0.001f) return; // fully released — nothing left to draw
            }
        }
        pose.pushPose();
        // Cancel one of the ItemRenderer's centring translations (see RodItemRenderer) and scale the
        // sprite about its centre so a longer fish simply reads bigger.
        pose.translate(0.5, 0.5, 0.5);
        // §fish-pose: dropped on the ground, a flatfish lies on it. Only the loose entity — in a slot the
        // icon is a picture of the fish and should stay the picture that was drawn.
        if ((ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.NONE)
                && com.riverfishing.fish.FishPose.isFlat(sp.getPath())) {
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
        }
        // §morph-shape: a stunted fish is short and thin for its weight, a humpbacked one short and deep.
        // Neither reads from colour, so the sprite itself is stretched — a different silhouette out of the
        // same drawing, which is the only honest way to show a body-shape morph without new art.
        float[] sh = com.riverfishing.fish.FishMorph.stretch(FishItem.getMorph(stack));
        pose.scale(s * sh[0], s * sh[1], s);
        ItemRenderer ir = mc.getItemRenderer();
        // §morph: the multiply tint arrives through the registered item colour, but multiplying can only
        // ever darken. A young fish is PALER than its own sprite and an albino is nearly white, so the
        // second half of the paint job rides the overlay's white channel (see FishTint).
        int ov = FishTint.overlay(stack);
        ir.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light,
                ov == net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY ? overlay : ov, model);
        pose.popPose();
    }
}
