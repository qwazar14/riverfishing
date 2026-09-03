package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.riverfishing.RiverFishing;
import com.riverfishing.item.FryItem;
import com.riverfishing.registry.ModItems;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * §breeding: the fry bucket is drawn as the shoal it holds — three copies of the SPECIES' own item
 * sprite, shrunk to fry size and staggered the way the static {@code fry.png} staggers its three silver
 * fish.
 *
 * <p>§26.x: a {@link SpecialModelRenderer} — the BEWLR is gone; {@code items/fry.json} names this
 * renderer ({@code riverfishing:fry}) for a stack that carries custom data and the static
 * {@code item/fry_icon} model for one that does not (the creative tab), so the picture is never blank.
 * Each fish is the species' own client item, resolved and submitted like any other stack: the sprite
 * the inventory draws for a pike is the sprite the fry bucket draws three of.
 */
public final class FrySpecialRenderer implements SpecialModelRenderer<Identifier> {
    public static final Identifier ID = RiverFishing.id("fry");

    /**
     * The three fish of tools/gen_fry_icon.py: bodies at (2,2), (7,6), (3,10) in 16-px icon space, each
     * 8x3 with the outline, so their centres sit at (5.5,3), (10.5,7), (6.5,11). Rows are {x, y, scale,
     * mirrored}: x/y as offsets from the icon centre in model units (1/16 per pixel, +y up), scale as
     * the fraction of a full sprite, and the middle fish — a touch larger — facing the other way.
     */
    private static final float[][] FISH = {
            {-2.5f / 16f,  5f / 16f, 0.40f, 0f},
            {-1.5f / 16f, -3f / 16f, 0.40f, 0f},
            { 2.5f / 16f,  1f / 16f, 0.45f, 1f},
    };

    /** The data-driven half: {@code {"type": "riverfishing:fry"}} in a client item definition. */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<Identifier> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Identifier> bake(BakingContext context) {
            return new FrySpecialRenderer();
        }
    }

    @Override
    public Identifier extractArgument(ItemStack stack) {
        return FryItem.species(stack);
    }

    @Override
    public void submit(Identifier species, PoseStack pose, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outlineColor) {
        Minecraft mc = Minecraft.getInstance();
        RegistrySupplier<Item> fish = species == null ? null : ModItems.FISH_ITEMS.get(species);
        if (fish == null) {
            // Custom data without a species: a fresh fry stack has none and falls to the static icon.
            submitStack(mc, new ItemStack(ModItems.FRY.get()), pose, collector, light, overlay, outlineColor);
            return;
        }
        ItemStack stack = new ItemStack(fish.get());
        for (int i = 0; i < FISH.length; i++) {
            float[] f = FISH[i];
            pose.pushPose();
            // a hair of depth per fish so the flat sprites never z-fight where they overlap
            pose.translate(0.5 + f[0], 0.5 + f[1], 0.5 + i * 0.02);
            if (f[3] > 0f) pose.mulPose(Axis.YP.rotationDegrees(180f)); // mirror: the sprite is two-faced
            pose.scale(f[2], f[2], f[2]);
            submitStack(mc, stack, pose, collector, light, overlay, outlineColor);
            pose.popPose();
        }
    }

    /** One stack through the ordinary item pipeline, centred on the pose's origin (NONE: no display transform). */
    private static void submitStack(Minecraft mc, ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
                                    int light, int overlay, int outlineColor) {
        ItemStackRenderState state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.NONE, mc.level, null, 0);
        state.submit(pose, collector, light, overlay, outlineColor);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        // The unit box the three sprites sit in — an inventory slot's worth, never "oversized".
        for (int i = 0; i < 8; i++) out.accept(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
    }
}
