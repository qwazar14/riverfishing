package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.block.TrophyStandBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * §mini-aquarium (0.5.1): the small fish CIRCLE inside the desktop tank — each on the same ring,
 * spread out in phase, nose leading the swim, with a gentle per-fish bob.
 */
public class TrophyStandRenderer implements BlockEntityRenderer<TrophyStandBlockEntity> {
    private final ItemRenderer itemRenderer;

    public TrophyStandRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(TrophyStandBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        List<ItemStack> fishes = be.getFishes();
        if (fishes.isEmpty()) return;
        float time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;

        for (int i = 0; i < fishes.size(); i++) {
            ItemStack fish = fishes.get(i);
            if (fish.isEmpty()) continue;
            float a = time * 0.045f + i * (Mth.TWO_PI / TrophyStandBlockEntity.CAPACITY);
            double r = 0.26;
            double px = 0.5 + Math.cos(a) * r;
            double pz = 0.5 + Math.sin(a) * r;
            double py = 0.55 + Mth.sin(time * 0.09f + i * 1.7f) * 0.03 + (i % 2) * 0.08;

            pose.pushPose();
            pose.translate(px, py, pz);
            // Tangent heading: the sprite faces its swim direction around the ring.
            pose.mulPose(Axis.YP.rotationDegrees(-(float) Math.toDegrees(a)));
            pose.scale(0.5f, 0.5f, 0.5f);
            itemRenderer.renderStatic(fish, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), i);
            pose.popPose();
        }
    }
}
