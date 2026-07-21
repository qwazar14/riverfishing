package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.block.TrophyStandBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * §mini-aquarium (0.5.1): the small fish CIRCLE inside the desktop tank — each on the same ring,
 * spread out in phase, nose leading the swim, with a gentle per-fish bob.
 * §26.1: two-phase render-state model — extract snapshots the fish states, submit draws the ring.
 */
public class TrophyStandRenderer implements BlockEntityRenderer<TrophyStandBlockEntity, TrophyStandRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public TrophyStandRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
    }

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState[] fish = new ItemStackRenderState[TrophyStandBlockEntity.CAPACITY];
        int count;
        float time;

        public State() {
            for (int i = 0; i < fish.length; i++) fish[i] = new ItemStackRenderState();
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrophyStandBlockEntity be, State s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, overlay);
        List<ItemStack> fishes = be.getFishes();
        s.count = Math.min(fishes.size(), s.fish.length);
        s.time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;
        for (int i = 0; i < s.count; i++) {
            itemModelResolver.updateForTopItem(s.fish[i], fishes.get(i),
                    ItemDisplayContext.FIXED, be.getLevel(), null, i);
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < s.count; i++) {
            float a = s.time * 0.045f + i * (Mth.TWO_PI / TrophyStandBlockEntity.CAPACITY);
            double px = 0.5 + Math.cos(a) * 0.26;
            double pz = 0.5 + Math.sin(a) * 0.26;
            double py = 0.55 + Mth.sin(s.time * 0.09f + i * 1.7f) * 0.03 + (i % 2) * 0.08;

            pose.pushPose();
            pose.translate(px, py, pz);
            // Tangent heading: -(a+90°) aligns the sprite's long axis with the swim direction.
            pose.mulPose(Axis.YP.rotationDegrees(-(float) Math.toDegrees(a) - 90f));
            pose.scale(0.5f, 0.5f, 0.5f);
            s.fish[i].submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
