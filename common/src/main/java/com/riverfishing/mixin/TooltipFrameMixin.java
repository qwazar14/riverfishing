package com.riverfishing.mixin;

import com.riverfishing.client.FishCardClientTooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §fish-card: the frame around a fish card wears the fish's colour.
 *
 * <p>Vanilla paints every tooltip the same purple-on-black; the loaders offer a colour event on one
 * side and nothing on the other. This is the one place both go through. When a card component has
 * left a colour in {@link FishCardClientTooltip#FRAME} during layout, the background is painted here
 * instead — same shape, the fish's border — and the colour is cleared so the next tooltip is vanilla.
 *
 * <p>{@code require = 0}: decoration. If the method is not where this expects, the card gets the
 * vanilla frame and the game starts.
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipFrameMixin {

    @Inject(method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void riverfishing$cardFrame(GuiGraphics g, int x, int y, int w, int h, int z, CallbackInfo ci) {
        int frame = FishCardClientTooltip.FRAME;
        if (frame == 0) return;
        FishCardClientTooltip.FRAME = 0;
        int x0 = x - 3, y0 = y - 3, x1 = x + w + 3, y1 = y + h + 3;
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        if (FishCardClientTooltip.STYLE == 1) paper(g, x0, y0, x1, y1, y, frame);
        else water(g, x0, y0, x1, y1, frame);
        g.pose().popPose();
        ci.cancel();
    }

    /**
     * §fish-card-2: deep water — a dark teal that deepens toward the bottom, faint current lines, and the card's
     * colour only at the corners and in a thread along the top, not as a painted frame.
     */
    private static void water(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF060C0E);
        int h = y1 - y0;
        for (int i = 0; i < h; i += 2) {
            float t = i / (float) h;
            int col = 0xF6000000 | (int) (0x10 - 4 * t) << 16 | (int) (0x1E - 8 * t) << 8 | (int) (0x24 - 8 * t);
            g.fill(x0, y0 + i, x1, Math.min(y1, y0 + i + 2), col);
        }
        for (int yy = y0 + 9; yy < y1 - 2; yy += 7) g.fill(x0 + 2, yy, x1 - 2, yy + 1, 0x07FFFFFF);
        int a = accent | 0xFF000000;
        g.fill(x0, y0, x0 + 6, y0 + 1, a);
        g.fill(x0, y0, x0 + 1, y0 + 6, a);
        g.fill(x1 - 6, y0, x1, y0 + 1, a);
        g.fill(x1 - 1, y0, x1, y0 + 6, a);
        g.fill(x0, y1 - 1, x0 + 6, y1, a);
        g.fill(x0, y1 - 6, x0 + 1, y1, a);
        g.fill(x1 - 6, y1 - 1, x1, y1, a);
        g.fill(x1 - 1, y1 - 6, x1, y1, a);
        g.fill(x0 + 8, y0 - 1, x1 - 8, y0, (accent & 0x00FFFFFF) | 0x8C000000);
    }

    /**
     * §contract-paper: an order form — parchment under the ink, a dark band under the item's own name (which
     * vanilla writes in white), a brown edge.
     */
    private static void paper(GuiGraphics g, int x0, int y0, int x1, int y1, int nameY, int accent) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF5A4630);
        g.fill(x0, y0, x1, y1, 0xFFE6D9BB);
        g.fill(x0, y0, x1, nameY + 11, 0xFF3A2A1A);
        g.fill(x0, nameY + 11, x1, nameY + 12, (accent & 0x00FFFFFF) | 0xFF000000);
        // the paper's own grain: a slightly darker edge all round
        g.fill(x0, nameY + 12, x0 + 1, y1, 0x22603010);
        g.fill(x1 - 1, nameY + 12, x1, y1, 0x22603010);
        g.fill(x0, y1 - 1, x1, y1, 0x33603010);
    }
}
