package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.block.AquariumBlock;
import com.riverfishing.block.AquariumBlockEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riverfishing.RiverFishing;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.FryItem;
import com.riverfishing.item.RoeItem;
import net.minecraft.client.renderer.RenderType;
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
import org.joml.Matrix4f;

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

    // §roe-frames: gen_aquarium_roe.py's 80x16 strip — four incubation days, then the hatched shoal. Bound
    // straight from its path rather than stitched: a texture nothing in a model references is not on the
    // block atlas, and an atlases/blocks.json entry is a second file for one quad.
    private static final ResourceLocation ROE_STRIP = RiverFishing.id("textures/block/aquarium_roe.png");
    private static final int ROE_FRAMES = 5;
    private static final RenderType ROE_LAYER = RenderType.entityCutoutNoCull(ROE_STRIP);
    /** The tank model's gravel floor tops out at 2/16 of the upper cell; a hair above it, or they z-fight. */
    private static final float ROE_FLOOR = 1f + 2f / 16f + 0.004f;
    private static final float ROE_HALF = 6f / 16f;   // 12/16 wide: between the two kelp stands

    @Override
    public void render(AquariumBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        java.util.List<ItemStack> fishes = be.getFishes();
        ItemStack roe = be.getRoe();
        if (fishes.isEmpty() && roe.isEmpty()) return;

        Direction facing = be.getBlockState().hasProperty(AquariumBlock.FACING)
                ? be.getBlockState().getValue(AquariumBlock.FACING) : Direction.NORTH;
        Direction cw = facing.getClockWise();
        float time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;

        // Centre of the 2-wide × 1-tall glass tank (upper row), relative to the master cell corner.
        double tankX = 0.5 + cw.getStepX() * 0.5;
        double tankZ = 0.5 + cw.getStepZ() * 0.5;

        if (!roe.isEmpty()) renderRoe(be, roe, time, facing, cw, tankX, tankZ, pose, buffers, light, overlay);
        if (fishes.isEmpty()) return;

        for (int i = 0; i < fishes.size(); i++) {
            ItemStack fish = fishes.get(i);
            if (fish.isEmpty()) continue;
            boolean big = FishItem.getWeightG(fish) >= BIG_FISH_G;
            ResourceLocation fsp = FishItem.getSpecies(fish);
            boolean flat = fsp != null && com.riverfishing.fish.FishPose.isFlat(fsp.getPath());
            // Spread the fish out in phase and depth so they don't overlap.
            float t = time * 0.05f + i * 2.094f;                 // 120° apart
            double depth = ((i % 3) - 1) * 0.20;                  // §aq-fix: three lanes, six fish — the second trio shares them                        // front/mid/back lane
            double u, height;
            float travel; // horizontal travel direction: +1 swims one way, −1 the other
            if (big) {
                // §aquarium-big: a big fish just cruises side to side (the old behaviour). The cruise
                // amplitude shrinks with the fish's rendered length so a tank-filling giant (§fish-scale:
                // FIXED caps at 2 blocks) sways in place instead of poking through the glass.
                float fishLen = Math.min(2.0f, FishItem.getIconScale(fish)) * 0.9f;
                double amp = Mth.clamp(0.95 - fishLen / 2.0, 0.05, 0.30);
                u = Mth.sin(t) * amp;
                height = 1.5 + Mth.sin(time * 0.09f + i) * 0.04 - (i / 3) * 0.14;
                travel = Mth.cos(t) >= 0 ? 1f : -1f;
            } else {
                // §aquarium-eight: a Gerono lemniscate ∞ — sin(t) across, ½·sin(2t) up = a figure-8.
                u = Mth.sin(t) * 0.60;
                height = 1.5 + 0.5 * Mth.sin(2 * t) * 0.28 + ((i % 3) - 1) * 0.04 - (i / 3) * 0.14;
                travel = Mth.cos(t) >= 0 ? 1f : -1f;
            }
            // §fish-pose: a flatfish does not loop through open water — it works the floor of the tank.
            if (flat) {
                u = Mth.sin(t) * 0.55;
                height = 1.06 + Mth.sin(time * 0.05f + i) * 0.02;
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
            // §fish-pose: the flatfish lie down in the tank too, parallel to its floor — which is also
            // where they are swimming (see the height below), because that is what they do.
            if (flat) pose.mulPose(Axis.XP.rotationDegrees(com.riverfishing.fish.FishPose.lay()));
            float scale = big ? 0.9f : 0.7f;
            pose.scale(scale, scale, scale);
            itemRenderer.renderStatic(fish, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        renderNameplate(be, fishes, facing, cw, pose, buffers, light);
    }

    /**
     * §roe-frames: the roe slot, drawn where it is. Incubating roe is a flat quad on the gravel, centred
     * on the seam between the two upper cells (the kelp stands at each cell's own centre, so the middle
     * is the one clear patch of floor); the frame is the incubation day, counted on the client from the
     * synced start time and the world clock — the same arithmetic the server's ticker does, so the
     * picture and the sneak-click status agree. Hatched fry stand upright and broadside like the fish
     * (a flat shoal seen from the front of a tank is a line) and drift side to side on the game clock.
     */
    private void renderRoe(AquariumBlockEntity be, ItemStack roe, float time, Direction facing, Direction cw,
                           double tankX, double tankZ, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
        boolean fry = roe.getItem() instanceof FryItem;
        if (!fry && !(roe.getItem() instanceof RoeItem)) return;
        int frame;
        double u = 0, y;
        if (fry) {
            frame = ROE_FRAMES - 1;
            // Period ~3 s (60 ticks), amplitude 3/16 — a shoal working the middle of the tank, plus a bob.
            u = Mth.sin(time * (float) (Math.PI * 2 / 60)) * (3.0 / 16);
            y = 1.5 + Mth.sin(time * 0.13f) * 0.02;   // mid-water: the quad's bottom edge just clears the gravel
        } else {
            long start = be.getIncubate();
            long day = start == 0 || be.getLevel() == null ? 0 : (be.getLevel().getDayTime() - start) / 24000L;
            // ponytail: a cold-climate clutch takes eight days and sits on the day-4 frame for its second
            // half; the client would need the profile and the biome to stretch the strip over it.
            frame = (int) Mth.clamp(day, 0, ROE_FRAMES - 2);
            y = ROE_FLOOR;
        }
        pose.pushPose();
        pose.translate(tankX + cw.getStepX() * u, y, tankZ + cw.getStepZ() * u);
        // Same yaw as the fish so the strip's left-right is the tank's left-right whichever way it faces.
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // The roe lies down; the fry stand up. Either way the quad is drawn in its own x-y plane below.
        if (!fry) pose.mulPose(Axis.XP.rotationDegrees(90f));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(ROE_LAYER);
        float u0 = frame / (float) ROE_FRAMES, u1 = (frame + 1) / (float) ROE_FRAMES;
        float h = ROE_HALF;
        vertex(m, vc, -h, -h, u0, 1f, light, overlay);
        vertex(m, vc, h, -h, u1, 1f, light, overlay);
        vertex(m, vc, h, h, u1, 0f, light, overlay);
        vertex(m, vc, -h, h, u0, 0f, light, overlay);
        pose.popPose();
    }

    /** One corner of the roe quad; lit like the fish beside it, normal up so the entity shader's directional light is steady. */
    private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float u, float v, int light, int overlay) {
        // §26.x: addVertex/setColor/setUv/setOverlay/setLight/setNormal is the 1.21.1 chain; 1.20.1 is
        // vertex/color/uv/overlayCoords/uv2/normal + endVertex(), 26.x is this chain against
        // RenderTypes.entityCutoutNoCull and a submitCustomGeometry from submit().
        vc.vertex(m, x, y, 0f)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(0f, 1f, 0f)
                .endVertex();
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
