package com.riverfishing.mixin;

import com.riverfishing.client.OrderState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §order-panel: hangs the day's order over the vanilla merchant window.
 *
     * <p>Not TAIL: the last thing {@code render} does is draw the hovered slot's tooltip, and a
     * panel painted after that would cover it. Injecting BEFORE that call puts the sign under the
     * tooltip and over everything else.
     *
     * <p>The target names {@code MerchantScreen}, NOT {@code AbstractContainerScreen} where the
     * method is declared: the constant pool at the call site names the subclass, and a target
     * string that says otherwise simply does not match.
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

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/gui/screens/inventory/MerchantScreen;renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                     shift = At.Shift.BEFORE),
            require = 0)
    private void riverfishing$orderPanel(GuiGraphics g, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        OrderState.draw(g);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void riverfishing$orderClick(double mx, double my, int button,
                                         org.spongepowered.asm.mixin.injection.callback
                                                 .CallbackInfoReturnable<Boolean> cir) {
        if (OrderState.click(mx, my)) cir.setReturnValue(true);
    }
}
