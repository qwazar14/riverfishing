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

    // §roe-frames: gen_aquarium_roe.py's 80x16 strip — four incubation days (the fifth frame, a generic
    // shoal, is no longer drawn: hatched fry wear their species' sprite, see extractFry). Bound
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

    /** §fry-sprite: one copy is a fry; a bucket of twenty or more is six. Each ~0.18 of a block long. */
    private static final int FRY_MAX = 6;
    private static final float FRY_LEN = 0.18f;
    /**
     * §26.x: no gridScale override here — the fish's size is baked into its client item (§fish-scale
     * range_dispatch on custom_model_data). A 5 cm throwaway lands in bucket 0, whose FIXED transform is
     * 0.45 of a block, so the pose scales that down to FRY_LEN.
     */
    private static final float FRY_SCALE = FRY_LEN / 0.45f;

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
        double roeX, roeY, roeZ;
        float roeYRot;
        // §fry-sprite: the hatched shoal — one resolved fish item, replayed at each copy's pose.
        final ItemStackRenderState fryItem = new ItemStackRenderState();
        final List<Swim> fry = new ArrayList<>();
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
        s.fry.clear();
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
     * picture and the window agree. Hatched fry are a shoal of the SPECIES' OWN sprite (§fry-sprite: the
     * strip's fifth frame was a generic silver fish — a hatched pike swam as a roach), drawn through the
     * same item pipeline as the mounted fish, upright and broadside, drifting side to side on the game
     * clock with each copy out of phase so it reads as a shoal, not one sprite.
     */
    private void extractRoe(AquariumBlockEntity be, ItemStack roe, float time, Direction facing,
                            Direction cw, double tankX, double tankZ, State s) {
        if (roe.getItem() instanceof FryItem) {
            extractFry(be, roe, time, facing, cw, tankX, tankZ, s);
            return;
        }
        if (!(roe.getItem() instanceof RoeItem)) return;
        long start = be.getIncubate();
        long day = start == 0 || be.getLevel() == null ? 0 : (be.getLevel().getOverworldClockTime() - start) / 24000L;
        // ponytail: a cold-climate clutch takes eight days and sits on the day-4 frame for its second
        // half; the client would need the profile and the biome to stretch the strip over it.
        s.roeFrame = (int) Mth.clamp(day, 0, ROE_FRAMES - 2);
        s.roeX = tankX;
        s.roeY = ROE_FLOOR;
        s.roeZ = tankZ;
        // Same yaw as the fish so the strip's left-right is the tank's left-right whichever way it faces.
        s.roeYRot = -facing.toYRot();
    }

    /**
     * §fry-sprite: the fry are the mounted-fish draw in miniature — a throwaway fish stack of the fry's
     * species (1 g, so FishTint shades it as the palest juvenile, which a fry is) resolved once and
     * submitted per copy exactly like the fish above: same yaw, same head-first flip on the turn, same
     * lie-down for a flatfish, same three depth lanes. The motion is the old strip's drift (period 3 s,
     * amplitude 3/16) plus a bob, offset per copy in phase and height so the copies never stack.
     */
    private void extractFry(AquariumBlockEntity be, ItemStack fryStack, float time, Direction facing,
                            Direction cw, double tankX, double tankZ, State s) {
        Identifier sp = FryItem.species(fryStack);
        if (sp == null) return;
        ItemStack fish = FishItem.create(com.riverfishing.registry.ModItems.fishItem(sp), sp, 1, 5, true);
        boolean flat = com.riverfishing.fish.FishPose.isFlat(sp.getPath());
        itemModelResolver.updateForTopItem(s.fryItem, fish, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
        int n = Math.min(FRY_MAX, FryItem.count(fryStack) / 4 + 1);
        for (int i = 0; i < n; i++) {
            float t = time * (float) (Math.PI * 2 / 60) + i * 1.1f;
            double u = Mth.sin(t) * (3.0 / 16);
            double depth = ((i % 3) - 1) * 0.20;
            double y = flat ? 1.06 + Mth.sin(time * 0.05f + i) * 0.02
                    : 1.5 + Mth.sin(time * 0.13f + i) * 0.02 + ((i % 5) - 2) * 0.04;   // ±0.08 spread
            float flip = Mth.cos(t) >= 0 ? 180f : 0f;
            Swim c = new Swim();
            c.x = tankX + cw.getStepX() * u + facing.getStepX() * depth;
            c.y = y;
            c.z = tankZ + cw.getStepZ() * u + facing.getStepZ() * depth;
            c.yRot = -facing.toYRot() + flip + Mth.sin(time * 0.15f + i) * 4f;
            c.xRot = flat ? com.riverfishing.fish.FishPose.lay() : 0f;
            c.scale = FRY_SCALE;
            s.fry.add(c);
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.roeFrame >= 0) submitRoe(s, pose, collector);
        if (!s.fry.isEmpty()) submitFry(s, pose, collector);
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

    private static void submitFry(State s, PoseStack pose, SubmitNodeCollector collector) {
        for (Swim c : s.fry) {
            pose.pushPose();
            pose.translate(c.x, c.y, c.z);
            pose.mulPose(Axis.YP.rotationDegrees(c.yRot));
            if (c.xRot != 0f) pose.mulPose(Axis.XP.rotationDegrees(c.xRot));
            pose.scale(c.scale, c.scale, c.scale);
            s.fryItem.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    private static void submitRoe(State s, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(s.roeX, s.roeY, s.roeZ);
        // Same yaw as the fish, then laid flat on the gravel; the quad is drawn in its own x-y plane below.
        pose.mulPose(Axis.YP.rotationDegrees(s.roeYRot));
        pose.mulPose(Axis.XP.rotationDegrees(90f));
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
