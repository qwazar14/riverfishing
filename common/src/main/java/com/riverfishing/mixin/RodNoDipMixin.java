package com.riverfishing.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §rod-anim: no vanilla dip on a rod click. Every click that uses an item drops the hand to the bottom of the
 * screen and lets it climb back (itemUsed sets the hand height to zero) — so each turn of the reel and each
 * twitch of a lure bobbed the whole rod down and up. The rod's own motion ({@link com.riverfishing.client.RodAnim})
 * is what a click looks like now.
 */
@Mixin(ItemInHandRenderer.class)
public class RodNoDipMixin {
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void riverfishing$noDip(InteractionHand hand, CallbackInfo ci) {
        var p = Minecraft.getInstance().player;
        if (p != null && p.getItemInHand(hand).getItem() instanceof com.riverfishing.item.RodItem) ci.cancel();
    }
}
