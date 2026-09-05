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

    // §roe-frames: gen_aquarium_roe.py's 80x16 strip — four incubation days (the fifth frame, a generic
    // shoal, is no longer drawn: hatched fry wear their species' sprite, see renderFry). Bound
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

        Direction facing = be.getBlockState().hasProperty(AquariumBlock.FACING)
                ? be.getBlockState().getValue(AquariumBlock.FACING) : Direction.NORTH;
        Direction cw = facing.getClockWise();
        float time = be.getLevel() != null ? (be.getLevel().getGameTime() + partialTick) : partialTick;

        // Centre of the 2-wide × 1-tall glass tank (upper row), relative to the master cell corner.
        double tankX = 0.5 + cw.getStepX() * 0.5;
        double tankZ = 0.5 + cw.getStepZ() * 0.5;

        // §aqua-view: the water and the modules first — an empty tank still has water in it.
        renderWater(be, facing, tankX, tankZ, pose, buffers, light, overlay);
        renderModules(be, facing, tankX, tankZ, pose, buffers, light, overlay);
        if (fishes.isEmpty() && roe.isEmpty()) return;

        if (!roe.isEmpty()) renderRoe(be, roe, time, facing, cw, tankX, tankZ, pose, buffers, light, overlay);
        if (fishes.isEmpty()) return;

        for (int i = 0; i < fishes.size(); i++) {
            ItemStack fish = fishes.get(i);
            if (fish.isEmpty()) continue;
            boolean big = FishItem.getWeightG(fish) >= BIG_FISH_G;
            ResourceLocation fsp = FishItem.getSpecies(fish);
            boolean flat = fsp != null && com.riverfishing.fish.FishPose.isFlat(fsp.getPath());
            // §aq-lanes: phase and lane come off the COUNT. They used to be a fixed 120° step and
            // `i % 3`, which is three of each — so with six fish, i and i+3 shared a phase AND a lane
            // and swam as one fish with a shadow 0.14 below it. n phases, n lanes, no two alike.
            int n = Math.max(1, fishes.size());
            float t = time * 0.05f + i * (float) (Math.PI * 2.0 / n);
            double lane = n == 1 ? 0.5 : i / (double) (n - 1);     // 0..1 from the front glass to the back
            double depth = (lane - 0.5) * 0.40;
            double u, height;
            float travel; // horizontal travel direction: +1 swims one way, −1 the other
            if (big) {
                // §aquarium-big: a big fish just cruises side to side (the old behaviour). The cruise
                // amplitude shrinks with the fish's rendered length so a tank-filling giant (§fish-scale:
                // FIXED caps at 2 blocks) sways in place instead of poking through the glass.
                float fishLen = Math.min(2.0f, FishItem.getIconScale(fish)) * 0.9f;
                double amp = Mth.clamp(0.95 - fishLen / 2.0, 0.05, 0.30);
                u = Mth.sin(t) * amp;
                height = 1.5 + Mth.sin(time * 0.09f + i) * 0.04 + (lane - 0.5) * 0.16;
                travel = Mth.cos(t) >= 0 ? 1f : -1f;
            } else {
                // §aquarium-eight: a Gerono lemniscate ∞ — sin(t) across, ½·sin(2t) up = a figure-8.
                u = Mth.sin(t) * 0.60;
                height = 1.5 + 0.5 * Mth.sin(2 * t) * 0.28 + (lane - 0.5) * 0.16;
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
     * picture and the sneak-click status agree. Hatched fry are a shoal of the SPECIES' OWN sprite
     * (§fry-sprite: the strip's fifth frame was a generic silver fish — a hatched pike swam as a roach),
     * drawn through the same item renderer as the mounted fish, upright and broadside, drifting side
     * to side on the game clock with each copy out of phase so it reads as a shoal, not one sprite.
     */
    private void renderRoe(AquariumBlockEntity be, ItemStack roe, float time, Direction facing, Direction cw,
                           double tankX, double tankZ, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
        if (roe.getItem() instanceof FryItem) {
            renderFry(be, roe, time, facing, cw, tankX, tankZ, pose, buffers, light, overlay);
            return;
        }
        if (!(roe.getItem() instanceof RoeItem)) return;
        long start = be.getIncubate();
        long day = start == 0 || be.getLevel() == null ? 0 : (be.getLevel().getDayTime() - start) / 24000L;
        // ponytail: a cold-climate clutch takes eight days and sits on the day-4 frame for its second
        // half; the client would need the profile and the biome to stretch the strip over it.
        int frame = (int) Mth.clamp(day, 0, ROE_FRAMES - 2);
        pose.pushPose();
        pose.translate(tankX, ROE_FLOOR, tankZ);
        // Same yaw as the fish so the strip's left-right is the tank's left-right whichever way it faces,
        // then laid flat on the gravel; the quad is drawn in its own x-y plane below.
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90f));
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

    /** §fry-sprite: one copy is a fry; a bucket of twenty or more is six. Each ~0.18 of a block long. */
    private static final int FRY_MAX = 6;
    private static final float FRY_LEN = 0.18f;

    /**
     * §fry-sprite: the fry are the mounted-fish draw in miniature — a throwaway fish stack of the fry's
     * species (1 g, so FishTint shades it as the palest juvenile, which a fry is) pushed through
     * {@code renderStatic} with the {@link FishItemRenderer#gridScale} override, exactly like the fish
     * in render(): same yaw, same head-first flip on the turn, same lie-down for a flatfish, same three
     * depth lanes. The motion is the old strip's drift (period 3 s, amplitude 3/16) plus a bob, offset
     * per copy in phase and height so the copies never stack into one sprite.
     */
    private void renderFry(AquariumBlockEntity be, ItemStack fryStack, float time, Direction facing, Direction cw,
                           double tankX, double tankZ, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
        ResourceLocation sp = FryItem.species(fryStack);
        if (sp == null) return;
        // §fry-look: the fish the fry will be — variety, genes, pattern — not a bare species stack.
        ItemStack fish = FryItem.look(fryStack);
        if (fish.isEmpty()) return;
        boolean flat = com.riverfishing.fish.FishPose.isFlat(sp.getPath());
        int n = Math.min(FRY_MAX, FryItem.count(fryStack) / 4 + 1);
        for (int i = 0; i < n; i++) {
            float t = time * (float) (Math.PI * 2 / 60) + i * 1.1f;
            double u = Mth.sin(t) * (3.0 / 16);
            double depth = ((i % 3) - 1) * 0.20;
            double y = flat ? 1.06 + Mth.sin(time * 0.05f + i) * 0.02
                    : 1.5 + Mth.sin(time * 0.13f + i) * 0.02 + ((i % 5) - 2) * 0.04;   // ±0.08 spread
            float flip = Mth.cos(t) >= 0 ? 180f : 0f;
            pose.pushPose();
            pose.translate(tankX + cw.getStepX() * u + facing.getStepX() * depth, y,
                    tankZ + cw.getStepZ() * u + facing.getStepZ() * depth);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + flip + Mth.sin(time * 0.15f + i) * 4f));
            if (flat) pose.mulPose(Axis.XP.rotationDegrees(com.riverfishing.fish.FishPose.lay()));
            FishItemRenderer.gridScale = FRY_LEN;
            itemRenderer.renderStatic(fish, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            FishItemRenderer.gridScale = 0f;
            pose.popPose();
        }
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


    // §aqua-view: the water is drawn here, not by the block model, so its colour can say how the tank
    // is doing. The box is the size the model's water element was: 0.6/16 in from the glass, 2/16 to
    // 15/16 up the upper cell, across both cells.
    private static final ResourceLocation WATER_TEX = RiverFishing.id("textures/block/aquarium_water.png");
    private static final RenderType WATER_LAYER = RenderType.entityTranslucent(WATER_TEX);
    private static final float W_HX = 1f - 0.6f / 16f, W_HZ = 0.5f - 0.6f / 16f, W_Y0 = 1f + 2f / 16f, W_Y1 = 1f + 15f / 16f;
    /** §aqua-view: the two module slots, drawn as their block items in the back corners of the gravel. */
    private static final float MOD_X = 0.72f, MOD_Z = -0.28f, MOD_Y = 1f + 2f / 16f + 0.13f, MOD_SCALE = 0.5f;

    /**
     * §aqua-view: ARGB for a water percentage. Three stops — clear blue at 100, green murk at 50 (the
     * line under which nothing spawns, so the colour crosses into "wrong" exactly there), brown at 0 —
     * and the alpha climbs as it fouls: dirty water is also water you cannot see through.
     */
    public static int waterColor(int water) {
        float t = Mth.clamp(water / 100f, 0f, 1f);
        int[] good = {0x6F, 0xC0, 0xF0, 0x6C}, mid = {0x7A, 0xB4, 0x8A, 0x8C}, bad = {0x6E, 0x58, 0x2E, 0xC0};
        int[] from = t >= 0.5f ? mid : bad, to = t >= 0.5f ? good : mid;
        float k = t >= 0.5f ? (t - 0.5f) * 2f : t * 2f;
        int r = Math.round(from[0] + (to[0] - from[0]) * k), g = Math.round(from[1] + (to[1] - from[1]) * k);
        int b = Math.round(from[2] + (to[2] - from[2]) * k), a = Math.round(from[3] + (to[3] - from[3]) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** §aqua-view: five faces of the water box, each wound both ways so culling cannot eat one. */
    private static void waterBox(Matrix4f m, VertexConsumer vc, int r, int g, int b, int a, int light, int overlay) {
        float[][] faces = {
                {-W_HX, W_Y1, -W_HZ, -W_HX, W_Y1, W_HZ, W_HX, W_Y1, W_HZ, W_HX, W_Y1, -W_HZ, 0f, 1f, 0f},
                {-W_HX, W_Y0, W_HZ, W_HX, W_Y0, W_HZ, W_HX, W_Y1, W_HZ, -W_HX, W_Y1, W_HZ, 0f, 0f, 1f},
                {W_HX, W_Y0, -W_HZ, -W_HX, W_Y0, -W_HZ, -W_HX, W_Y1, -W_HZ, W_HX, W_Y1, -W_HZ, 0f, 0f, -1f},
                {-W_HX, W_Y0, -W_HZ, -W_HX, W_Y0, W_HZ, -W_HX, W_Y1, W_HZ, -W_HX, W_Y1, -W_HZ, -1f, 0f, 0f},
                {W_HX, W_Y0, W_HZ, W_HX, W_Y0, -W_HZ, W_HX, W_Y1, -W_HZ, W_HX, W_Y1, W_HZ, 1f, 0f, 0f},
        };
        float[] us = {0f, 1f, 1f, 0f}, vs = {1f, 1f, 0f, 0f};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) tv(m, vc, f[i * 3], f[i * 3 + 1], f[i * 3 + 2], us[i], vs[i], r, g, b, a, f[12], f[13], f[14], light, overlay);
            for (int i = 3; i >= 0; i--) tv(m, vc, f[i * 3], f[i * 3 + 1], f[i * 3 + 2], us[i], vs[i], r, g, b, a, -f[12], -f[13], -f[14], light, overlay);
        }
    }

    private static void tv(Matrix4f m, VertexConsumer vc, float x, float y, float z, float u, float v, int r, int g, int b, int a, float nx, float ny, float nz, int light, int overlay) {
        vc.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(nx, ny, nz)
                .endVertex();
    }

    private void renderWater(AquariumBlockEntity be, Direction facing, double tankX, double tankZ,
                             PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int water = be.getWater();
        if (water <= 0) return;
        int c = waterColor(water);
        pose.pushPose();
        pose.translate(tankX, 0, tankZ);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        waterBox(pose.last().pose(), buffers.getBuffer(WATER_LAYER),
                (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF, light, overlay);
        pose.popPose();
    }

    private void renderModules(AquariumBlockEntity be, Direction facing, double tankX, double tankZ,
                               PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (int slot = 10; slot <= 11; slot++) {
            ItemStack m = be.getItem(slot);
            if (m.isEmpty()) continue;
            pose.pushPose();
            pose.translate(tankX, MOD_Y, tankZ);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.translate(slot == 10 ? -MOD_X : MOD_X, 0, MOD_Z);
            pose.scale(MOD_SCALE, MOD_SCALE, MOD_SCALE);
            itemRenderer.renderStatic(m, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }
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
