package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.block.TrophyStandBlock;
import com.riverfishing.block.TrophyStandBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the mounted fish standing above a trophy stand, its side facing the nameplate front
 * (the side it was placed from, §15.5), with a gentle display bob.
 * §26.1: ported to the two-phase render-state model — {@code extractRenderState} snapshots the BE on
 * the extraction pass, {@code submit} hands draw nodes to the collector (no direct buffer writes).
 */
public class TrophyStandRenderer implements BlockEntityRenderer<TrophyStandBlockEntity, TrophyStandRenderer.State> {
    private final ItemModelResolver itemModelResolver;
    private final Font font;

    public TrophyStandRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
        this.font = ctx.font();
    }

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState fish = new ItemStackRenderState();
        boolean hasFish;
        float yRot;
        float bob;
        Component label;
        int labelBg;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrophyStandBlockEntity be, State s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, overlay);
        ItemStack fish = be.getFish();
        s.hasFish = !fish.isEmpty();
        if (!s.hasFish) return;

        Direction facing = be.getBlockState().hasProperty(TrophyStandBlock.FACING)
                ? be.getBlockState().getValue(TrophyStandBlock.FACING) : Direction.NORTH;
        s.yRot = -facing.toYRot();
        float time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;
        s.bob = Mth.sin(time * 0.08f) * 0.025f;
        itemModelResolver.updateForTopItem(s.fish, fish, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
        s.label = fish.getHoverName(); // the FishItem name already includes the weight
        s.labelBg = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!s.hasFish) return;

        pose.pushPose();
        // Sit just above the cap (top at y=0.75), centred on the pedestal.
        pose.translate(0.5, 0.92 + s.bob, 0.5);
        // Match the nameplate front: base content faces +Z, rotate by -facing.toYRot() (same as the blockstate).
        pose.mulPose(Axis.YP.rotationDegrees(s.yRot));
        pose.scale(0.85f, 0.85f, 0.85f);
        s.fish.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();

        // Trophy plaque (§trophy-info): species + weight, billboarded above the mount.
        pose.pushPose();
        pose.translate(0.5, 1.5 + s.bob, 0.5);
        pose.mulPose(camera.orientation);
        pose.scale(-0.016f, -0.016f, 0.016f);
        float tx = -font.width(s.label) / 2f;
        collector.submitText(pose, tx, 0, s.label.getVisualOrderText(), false,
                Font.DisplayMode.NORMAL, s.lightCoords, 0xFFFFFFFF, s.labelBg, 0);
        pose.popPose();
    }
}
