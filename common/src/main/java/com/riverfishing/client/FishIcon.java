package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * §fish-icon (26.x): a fish drawn as its own 256px picture, straight onto the screen.
 *
 * <p>The journal has drawn fish this way since the port, for a reason its own comment records: routing
 * a fish through the item-model pipeline gives you back something that is not the drawing. The keepnet
 * did route them through it — {@code g.item()} with the pose scaled to the footprint — and paid for it
 * three times over:
 *
 * <ul>
 *   <li>A GUI item is a MODEL. Vanilla turns the sprite into an extruded slab, lights it, and for
 *       anything bigger than its 16×16 slot re-renders it through the oversized-item
 *       picture-in-picture pass — a fish four cells wide came back soft and washed out, which is what
 *       "the texture got squashed" was describing.</li>
 *   <li>The size had to travel through the MODEL, since there is no renderer to hand it to any more:
 *       the item carried a scale bucket, the bucket model applied it, and the screen divided the same
 *       number back out. Two halves that have to agree exactly, in two languages, is a bug waiting for
 *       a new species.</li>
 *   <li>The §morph tint had to come back the same long way round.</li>
 * </ul>
 *
 * <p>A blit has none of that: the size is the size you ask for, the aspect is the drawing's own, the
 * tint is an argument, and the pixels are the PNG's. One function so the journal and the keepnet cannot
 * disagree about what a fish looks like.
 */
public final class FishIcon {
    private FishIcon() {}

    public static Identifier texture(String speciesPath) {
        return RiverFishing.id("textures/item/fish/" + speciesPath + ".png");
    }

    /** The icon at 16×16, untinted — an inventory-sized picture. */
    public static void draw(GuiGraphicsExtractor g, String speciesPath, int x, int y) {
        draw(g, speciesPath, x, y, 16, -1);
    }

    /**
     * The icon in a {@code size}×{@code size} box at (x, y), under an ARGB multiply.
     *
     * <p>The canvas is square and the fish is drawn along it, so the box stays square too — the
     * transparent margin costs nothing and keeps the fish's own proportions exactly as drawn.
     */
    public static void draw(GuiGraphicsExtractor g, String speciesPath, int x, int y, int size, int argb) {
        if (size <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, texture(speciesPath), x, y, 0f, 0f,
                size, size, size, size, argb);
    }
}
