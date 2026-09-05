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
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, frame);                 // the border
        g.fill(x0, y0, x1, y1, 0xF0100010);                             // the ground
        int inner = (frame & 0x00FFFFFF) | 0x60000000;
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, inner);                  // a second, fainter line inside
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, inner);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, inner);
        g.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, inner);
        g.pose().popPose();
        ci.cancel();
    }
}
