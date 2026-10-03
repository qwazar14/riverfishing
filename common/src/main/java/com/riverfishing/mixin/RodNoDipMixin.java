package com.riverfishing.mixin;

/**
 * §rod-anim: no vanilla dip on a rod click. Every click that uses an item drops the hand to the bottom of the
 * screen and lets it climb back (itemUsed sets the hand height to zero) — so each turn of the reel and each
 * twitch of a lure bobbed the whole rod down and up. The rod's own motion ({@link com.riverfishing.client.RodAnim})
 * is what a click looks like now. 26.3 keeps the hand heights in FirstPersonHandsAndItems, 26.2 in
 * ItemInHandRenderer. No comments inside the version blocks: Stonecutter turns their comment marks into code
 * when it swaps a block in.
 */
//? if >=26.3 {
/*@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.player.FirstPersonHandsAndItems.class)
public class RodNoDipMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "itemUsed", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void riverfishing$noDip(net.minecraft.world.InteractionHand hand, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null && p.getItemInHand(hand).getItem() instanceof com.riverfishing.item.RodItem) ci.cancel();
    }
}
*///?} else {
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public class RodNoDipMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "itemUsed", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void riverfishing$noDip(net.minecraft.world.InteractionHand hand, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null && p.getItemInHand(hand).getItem() instanceof com.riverfishing.item.RodItem) ci.cancel();
    }
}
//?}
