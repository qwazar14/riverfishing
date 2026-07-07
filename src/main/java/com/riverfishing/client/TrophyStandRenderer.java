package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.block.TrophyStandBlock;
import com.riverfishing.block.TrophyStandBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the mounted fish standing above a trophy stand, its side facing the nameplate front
 * (the side it was placed from, §15.5), with a gentle display bob.
 */
public class TrophyStandRenderer implements BlockEntityRenderer<TrophyStandBlockEntity> {
    private final ItemRenderer itemRenderer;

    public TrophyStandRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(TrophyStandBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack fish = be.getFish();
        if (fish.isEmpty()) return;

        Direction facing = be.getBlockState().hasProperty(TrophyStandBlock.FACING)
                ? be.getBlockState().getValue(TrophyStandBlock.FACING) : Direction.NORTH;

        float time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;
        double bob = Mth.sin(time * 0.08f) * 0.025;

        pose.pushPose();
        // Sit just above the cap (top at y=0.75), centred on the pedestal.
        pose.translate(0.5, 0.92 + bob, 0.5);
        // Match the nameplate front: base content faces +Z, rotate by -facing.toYRot() (same as the blockstate).
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.scale(0.85f, 0.85f, 0.85f);
        itemRenderer.renderStatic(fish, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();

        // Trophy plaque (§trophy-info): species + weight, billboarded above the mount.
        var mc = net.minecraft.client.Minecraft.getInstance();
        var label = fish.getHoverName(); // the FishItem name already includes the weight
        pose.pushPose();
        pose.translate(0.5, 1.5 + bob, 0.5);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.016f, -0.016f, 0.016f);
        var font = mc.font;
        float tx = -font.width(label) / 2f;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
        font.drawInBatch(label, tx, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, bg, light);
        pose.popPose();
    }
}
