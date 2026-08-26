package com.riverfishing.mixin;

import com.riverfishing.client.OrderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §order-panel: hangs the day's order over the vanilla merchant window.
 *
     * <p>TAIL of {@code extractContents}: 26.x screens hand their drawing to an extractor, and
     * that method is the last thing the merchant screen contributes.
 *
 * <p>It shadows NOTHING. The window's own position is computed the same way
 * {@code AbstractContainerScreen.init} computes it, because a shadow of an inherited field is resolved
 * before any injector is even considered — so one that misses takes the client down regardless of how
 * optional the injection was. Nothing here has a name that has to survive remapping.
 *
 * <p>{@code require = 0} on purpose. This is decoration: if another mod owns the merchant screen's
 * render path and the injector finds nowhere to go, the right outcome is a missing sign, not a client
 * that will not start. Every other mixin in this mod is load-bearing and required; this one is not.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantOrderPanelMixin {

    @Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"), require = 0)
    private void riverfishing$orderPanel(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        OrderState.draw(g);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void riverfishing$orderClick(net.minecraft.client.input.MouseButtonEvent e, boolean doubled,
                                         org.spongepowered.asm.mixin.injection.callback
                                                 .CallbackInfoReturnable<Boolean> cir) {
        if (OrderState.click(e.x(), e.y())) cir.setReturnValue(true);
    }
}
