package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.block.AquariumBlock;
import com.riverfishing.block.AquariumBlockEntity;
import com.riverfishing.item.FishItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the mounted fish swimming inside the glass tank (§aquarium) and the nameplate on the wooden
 * base — line 1 the species, line 2 its weight and length. Anchored on the master (bottom-left) cell.
 */
public class AquariumRenderer implements BlockEntityRenderer<AquariumBlockEntity> {
    private final ItemRenderer itemRenderer;

    public AquariumRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    /** Fish at or above this weight are too big to loop the figure-8 — they just cruise back and forth. */
    private static final int BIG_FISH_G = 3000;

    @Override
    public void render(AquariumBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        java.util.List<ItemStack> fishes = be.getFishes();
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
            double px = tankX + cw.getStepX() * u + facing.getStepX() * depth;
            double pz = tankZ + cw.getStepZ() * u + facing.getStepZ() * depth;

            pose.pushPose();
            pose.translate(px, height, pz);
            // Keep the fish BROADSIDE to the viewer (you watch it from the side, like a real tank); a 180°
            // yaw when it turns around keeps it broadside but head-first the other way — never edge-on
            // (§aquarium-side). 180° instead of a negative scale so face culling/lighting stay correct.
            // Flip on travel>0 so the head leads the swim (travel<0 was tail-first — "задом наперёд").
            float flip = travel > 0 ? 180f : 0f;
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + flip + Mth.sin(time * 0.15f + i) * 4f));
            float scale = big ? 0.9f : 0.7f;
            pose.scale(scale, scale, scale);
            itemRenderer.renderStatic(fish, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        renderNameplate(be, fishes, facing, cw, pose, buffers, light);
    }

    private void renderNameplate(AquariumBlockEntity be, java.util.List<ItemStack> fishes, Direction facing,
                                 Direction cw, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Engraved FLAT on the wooden base's front face (§aquarium): centred across the 2-wide base,
        // oriented to the block's facing like a wall sign — it does NOT track the player. One compact
        // line per mounted fish (species + weight).
        double cxCentre = 0.5 + cw.getStepX() * 0.5;
        double czCentre = 0.5 + cw.getStepZ() * 0.5;
        double frontX = cxCentre + facing.getStepX() * 0.51;
        double frontZ = czCentre + facing.getStepZ() * 0.51;

        pose.pushPose();
        pose.translate(frontX, 0.62, frontZ);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.scale(0.011f, -0.011f, 0.011f); // Y flipped for text
        var mat = pose.last().pose();
        int n = fishes.size();
        float lineH = 10f;
        float startY = -((n - 1) * lineH) / 2f - 2f;
        for (int i = 0; i < n; i++) {
            ItemStack fish = fishes.get(i);
            ResourceLocation sp = FishItem.getSpecies(fish);
            Component name = sp != null
                    ? Component.translatable("fish." + sp.getNamespace() + "." + sp.getPath())
                    : fish.getHoverName();
            Component row = Component.literal(name.getString() + "  " + FishItem.weightLabel(FishItem.getWeightG(fish)));
            font.drawInBatch(row, -font.width(row) / 2f, startY + i * lineH, 0xFFEAF6FF, false, mat, buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, light);
        }
        pose.popPose();
    }
}
