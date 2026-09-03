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
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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

    // §roe-frames: gen_aquarium_roe.py's 80x16 strip — four incubation days, then the hatched shoal. Bound
    // straight from its path rather than stitched: a texture nothing in a model references is not on the
    // block atlas, and an atlases/blocks.json entry is a second file for one quad.
    private static final Identifier ROE_STRIP = RiverFishing.id("textures/block/aquarium_roe.png");
    private static final int ROE_FRAMES = 5;
    // §26.x: RenderTypes (plural) is the factory now, entityCutout is the UNCULLED one (entityCutoutCull
    // is the culled twin; entityCutoutNoCull is gone), and the quad rides submitCustomGeometry below.
    private static final RenderType ROE_LAYER = RenderTypes.entityCutout(ROE_STRIP);
    /** The tank model's gravel floor tops out at 2/16 of the upper cell; a hair above it, or they z-fight. */
    private static final float ROE_FLOOR = 1f + 2f / 16f + 0.004f;
    private static final float ROE_HALF = 6f / 16f;   // 12/16 wide: between the two kelp stands

    static class Swim {
        final ItemStackRenderState item = new ItemStackRenderState();
        double x, y, z;
        float yRot;
        float xRot; // §fish-pose: pitch that lays a flatfish flat (0 for everything else)
        float scale;
        Component row; // nameplate line
    }

    public static class State extends BlockEntityRenderState {
        final List<Swim> fishes = new ArrayList<>();
        float plateYRot;
        double plateX, plateZ;
        // §roe-frames: the roe slot's quad; frame < 0 = nothing to draw.
        int roeFrame = -1;
        boolean fry;
        double roeX, roeY, roeZ;
        float roeYRot;
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
        s.roeFrame = -1;
        List<ItemStack> fishes = be.getFishes();
        ItemStack roe = be.getRoe();
        if (fishes.isEmpty() && roe.isEmpty()) return;

        Direction facing = be.getBlockState().hasProperty(AquariumBlock.FACING)
                ? be.getBlockState().getValue(AquariumBlock.FACING) : Direction.NORTH;
        Direction cw = facing.getClockWise();
        float time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;

        // Centre of the 2-wide × 1-tall glass tank (upper row), relative to the master cell corner.
        double tankX = 0.5 + cw.getStepX() * 0.5;
        double tankZ = 0.5 + cw.getStepZ() * 0.5;

        if (!roe.isEmpty()) extractRoe(be, roe, time, facing, cw, tankX, tankZ, s);

        for (int i = 0; i < fishes.size(); i++) {
            ItemStack fish = fishes.get(i);
            if (fish.isEmpty()) continue;
            boolean big = FishItem.getWeightG(fish) >= BIG_FISH_G;
            Identifier sp = FishItem.getSpecies(fish);
            boolean flat = sp != null && com.riverfishing.fish.FishPose.isFlat(sp.getPath());
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
            // §fish-pose: a flatfish does not loop through open water — it works the floor of the tank.
            if (flat) {
                u = Mth.sin(t) * 0.55;
                height = 1.06 + Mth.sin(time * 0.05f + i) * 0.02;
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
            // §fish-pose: the flatfish lie down in the tank too, parallel to its floor — which is also
            // where they are swimming (see the height above), because that is what they do.
            swim.xRot = flat ? com.riverfishing.fish.FishPose.lay() : 0f;
            swim.scale = big ? 0.9f : 0.7f;
            itemModelResolver.updateForTopItem(swim.item, fish, ItemDisplayContext.FIXED, be.getLevel(), null, i);

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

    /**
     * §roe-frames: the roe slot, drawn where it is. Incubating roe is a flat quad on the gravel, centred
     * on the seam between the two upper cells (the kelp stands at each cell's own centre, so the middle
     * is the one clear patch of floor); the frame is the incubation day, counted on the client from the
     * synced start time and the world clock — the same arithmetic the server's ticker does, so the
     * picture and the window agree. Hatched fry stand upright and broadside like the fish (a flat shoal
     * seen from the front of a tank is a line) and drift side to side on the game clock.
     */
    private static void extractRoe(AquariumBlockEntity be, ItemStack roe, float time, Direction facing,
                                   Direction cw, double tankX, double tankZ, State s) {
        boolean fry = roe.getItem() instanceof FryItem;
        if (!fry && !(roe.getItem() instanceof RoeItem)) return;
        double u = 0, y;
        if (fry) {
            s.roeFrame = ROE_FRAMES - 1;
            // Period ~3 s (60 ticks), amplitude 3/16 — a shoal working the middle of the tank, plus a bob.
            u = Mth.sin(time * (float) (Math.PI * 2 / 60)) * (3.0 / 16);
            y = 1.5 + Mth.sin(time * 0.13f) * 0.02;   // mid-water: the quad's bottom edge just clears the gravel
        } else {
            long start = be.getIncubate();
            long day = start == 0 || be.getLevel() == null ? 0 : (be.getLevel().getOverworldClockTime() - start) / 24000L;
            // ponytail: a cold-climate clutch takes eight days and sits on the day-4 frame for its second
            // half; the client would need the profile and the biome to stretch the strip over it.
            s.roeFrame = (int) Mth.clamp(day, 0, ROE_FRAMES - 2);
            y = ROE_FLOOR;
        }
        s.fry = fry;
        s.roeX = tankX + cw.getStepX() * u;
        s.roeY = y;
        s.roeZ = tankZ + cw.getStepZ() * u;
        // Same yaw as the fish so the strip's left-right is the tank's left-right whichever way it faces.
        s.roeYRot = -facing.toYRot();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.roeFrame >= 0) submitRoe(s, pose, collector);
        if (s.fishes.isEmpty()) return;

        for (Swim swim : s.fishes) {
            pose.pushPose();
            pose.translate(swim.x, swim.y, swim.z);
            pose.mulPose(Axis.YP.rotationDegrees(swim.yRot));
            if (swim.xRot != 0f) pose.mulPose(Axis.XP.rotationDegrees(swim.xRot));
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

    private static void submitRoe(State s, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(s.roeX, s.roeY, s.roeZ);
        pose.mulPose(Axis.YP.rotationDegrees(s.roeYRot));
        // The roe lies down; the fry stand up. Either way the quad is drawn in its own x-y plane below.
        if (!s.fry) pose.mulPose(Axis.XP.rotationDegrees(90f));
        float u0 = s.roeFrame / (float) ROE_FRAMES, u1 = (s.roeFrame + 1) / (float) ROE_FRAMES;
        float h = ROE_HALF;
        int light = s.lightCoords;
        // §26.x: the pose is snapshotted at submit; the lambda gets it back with the layer's buffer.
        collector.submitCustomGeometry(pose, ROE_LAYER, (p, vc) -> {
            Matrix4f m = p.pose();
            vertex(m, vc, -h, -h, u0, 1f, light);
            vertex(m, vc, h, -h, u1, 1f, light);
            vertex(m, vc, h, h, u1, 0f, light);
            vertex(m, vc, -h, h, u0, 0f, light);
        });
        pose.popPose();
    }

    /** One corner of the roe quad; lit like the fish beside it, normal up so the entity shader's directional light is steady. */
    private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float u, float v, int light) {
        vc.addVertex(m, x, y, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0f, 1f, 0f);
    }
}
