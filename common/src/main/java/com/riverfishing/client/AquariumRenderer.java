package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.block.AquariumBlock;
import com.riverfishing.block.AquariumBlockEntity;
import com.riverfishing.item.FishItem;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the mounted fish swimming inside the glass tank (§aquarium) and the nameplate on the wooden
 * base — one compact line per fish (species + weight). Anchored on the master (bottom-left) cell.
 * §26.1: two-phase render-state model — swim positions are computed at extract time (BE access),
 * submit only replays the snapshot into the collector.
 */
public class AquariumRenderer implements BlockEntityRenderer<AquariumBlockEntity, AquariumRenderer.State> {
    private final ItemModelResolver itemModelResolver;
    private final Font font;

    public AquariumRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
        this.font = ctx.font();
    }

    /** Fish at or above this weight are too big to loop the figure-8 — they just cruise back and forth. */
    private static final int BIG_FISH_G = 3000;

    static class Swim {
        final ItemStackRenderState item = new ItemStackRenderState();
        double x, y, z;
        float yRot;
        float scale;
        Component row; // nameplate line
    }

    public static class State extends BlockEntityRenderState {
        final List<Swim> fishes = new ArrayList<>();
        float plateYRot;
        double plateX, plateZ;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AquariumBlockEntity be, State s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, overlay);
        s.fishes.clear();
        List<ItemStack> fishes = be.getFishes();
        if (fishes.isEmpty()) return;

        Direction facing = be.getBlockState().hasProperty(AquariumBlock.FACING)
                ? be.getBlockState().getValue(AquariumBlock.FACING) : Direction.NORTH;
        Direction cw = facing.getClockWise();
        float time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;

        // Centre of the 2-wide × 1-tall glass tank (upper row), relative to the master cell corner.
        double tankX = 0.5 + cw.getStepX() * 0.5;
        double tankZ = 0.5 + cw.getStepZ() * 0.5;

        for (int i = 0; i < fishes.size(); i++) {
            ItemStack fish = fishes.get(i);
            if (fish.isEmpty()) continue;
            boolean big = FishItem.getWeightG(fish) >= BIG_FISH_G;
            // Spread the fish out in phase and depth so they don't overlap.
            float t = time * 0.05f + i * 2.094f;                 // 120° apart
            double depth = (i - 1) * 0.20;                        // front/mid/back lane
            double u, height;
            float travel; // horizontal travel direction: +1 swims one way, −1 the other
            if (big) {
                // §aquarium-big: a big fish just cruises side to side (the old behaviour). The cruise
                // amplitude shrinks with the fish's rendered length so a tank-filling giant (§fish-scale:
                // FIXED caps at 2 blocks) sways in place instead of poking through the glass.
                float fishLen = Math.min(2.0f, FishItem.getIconScale(fish)) * 0.9f;
                double amp = Mth.clamp(0.95 - fishLen / 2.0, 0.05, 0.30);
                u = Mth.sin(t) * amp;
                height = 1.5 + Mth.sin(time * 0.09f + i) * 0.04;
                travel = Mth.cos(t) >= 0 ? 1f : -1f;
            } else {
                // §aquarium-eight: a Gerono lemniscate ∞ — sin(t) across, ½·sin(2t) up = a figure-8.
                u = Mth.sin(t) * 0.60;
                height = 1.5 + 0.5 * Mth.sin(2 * t) * 0.28 + (i - 1) * 0.04;
                travel = Mth.cos(t) >= 0 ? 1f : -1f;
            }
            Swim swim = new Swim();
            swim.x = tankX + cw.getStepX() * u + facing.getStepX() * depth;
            swim.y = height;
            swim.z = tankZ + cw.getStepZ() * u + facing.getStepZ() * depth;
            // Keep the fish BROADSIDE to the viewer; 180° flip when it turns so the head leads the swim
            // (§aquarium-side) — 180° instead of a negative scale so face culling/lighting stay correct.
            float flip = travel > 0 ? 180f : 0f;
            swim.yRot = -facing.toYRot() + flip + Mth.sin(time * 0.15f + i) * 4f;
            swim.scale = big ? 0.9f : 0.7f;
            itemModelResolver.updateForTopItem(swim.item, fish, ItemDisplayContext.FIXED, be.getLevel(), null, i);

            Identifier sp = FishItem.getSpecies(fish);
            Component name = sp != null
                    ? Component.translatable("fish." + sp.getNamespace() + "." + sp.getPath())
                    : fish.getHoverName();
            swim.row = Component.literal(name.getString() + "  " + FishItem.weightLabel(FishItem.getWeightG(fish)));
            s.fishes.add(swim);
        }

        // Engraved FLAT on the wooden base's front face (§aquarium): centred across the 2-wide base,
        // oriented to the block's facing like a wall sign — it does NOT track the player.
        double cxCentre = 0.5 + cw.getStepX() * 0.5;
        double czCentre = 0.5 + cw.getStepZ() * 0.5;
        s.plateX = cxCentre + facing.getStepX() * 0.51;
        s.plateZ = czCentre + facing.getStepZ() * 0.51;
        s.plateYRot = -facing.toYRot();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.fishes.isEmpty()) return;

        for (Swim swim : s.fishes) {
            pose.pushPose();
            pose.translate(swim.x, swim.y, swim.z);
            pose.mulPose(Axis.YP.rotationDegrees(swim.yRot));
            pose.scale(swim.scale, swim.scale, swim.scale);
            swim.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        pose.pushPose();
        pose.translate(s.plateX, 0.62, s.plateZ);
        pose.mulPose(Axis.YP.rotationDegrees(s.plateYRot));
        pose.scale(0.011f, -0.011f, 0.011f); // Y flipped for text
        int n = s.fishes.size();
        float lineH = 10f;
        float startY = -((n - 1) * lineH) / 2f - 2f;
        for (int i = 0; i < n; i++) {
            Component row = s.fishes.get(i).row;
            collector.submitText(pose, -font.width(row) / 2f, startY + i * lineH, row.getVisualOrderText(),
                    false, Font.DisplayMode.POLYGON_OFFSET, s.lightCoords, 0xFFEAF6FF, 0, 0);
        }
        pose.popPose();
    }
}
