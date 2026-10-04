package com.riverfishing.mixin;

import com.riverfishing.item.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §rod-anim: no vanilla dip on a rod click, and no re-equip bob when the rod's NBT changes in the hand.
 *
 * <p>Every click that uses an item drops the hand to the bottom of the screen and lets it climb back
 * (itemUsed sets the hand height to zero) — so each turn of the reel and each twitch of a lure bobbed the
 * whole rod down and up. The rod's own motion ({@link com.riverfishing.client.RodAnim}) is what a click
 * looks like now.
 *
 * <p>The swap bob: 26.x turns it off with {@code "hand_animation_on_swap": false} in the rod's item
 * definition. 1.20.1 has no such field — tick() compares the shown stack with the held one and drops the
 * hand whenever they stop matching, which a server-side NBT write (line wear, bait, the rig) does
 * mid-fight. So a rod in the hand is simply taken as the shown one; RodAnim draws its own equip when the
 * rod really changes. One mixin, both loaders (Forge's shouldCauseReequipAnimation then sees the same stack).
 */
@Mixin(ItemInHandRenderer.class)
public class RodNoDipMixin {
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void riverfishing$noDip(InteractionHand hand, CallbackInfo ci) {
        var p = Minecraft.getInstance().player;
        if (p != null && p.getItemInHand(hand).getItem() instanceof RodItem) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void riverfishing$noSwapBob(CallbackInfo ci) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (main.getItem() instanceof RodItem) mainHandItem = main;
        if (off.getItem() instanceof RodItem) offHandItem = off;
    }
}
