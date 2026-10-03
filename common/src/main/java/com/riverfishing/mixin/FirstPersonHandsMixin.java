package com.riverfishing.mixin;

/**
 * §cast-anim (§26.3): 26.3's door onto the first-person rod — {@link com.riverfishing.client.RodFirstPerson}.
 * The hand and the stack are noted as submitArmWithItem starts, and the item's submit is where the rod is
 * posed (and, as a 3D chain, drawn instead of the item). On 26.2 this is an empty mixin; ItemInHandRendererMixin
 * is the door there. No comments inside the version blocks: Stonecutter turns their comment marks into code
 * when it swaps a block in.
 */
//? if >=26.3 {
/*@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsMixin {
    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.world.item.ItemStack riverfishing$stack = net.minecraft.world.item.ItemStack.EMPTY;
    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.world.InteractionHand riverfishing$hand = net.minecraft.world.InteractionHand.MAIN_HAND;

    @org.spongepowered.asm.mixin.injection.Inject(method = "submitArmWithItem", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void riverfishing$note(net.minecraft.client.renderer.state.level.PlayerRenderState player,
                                   net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state,
                                   float a, float b, net.minecraft.world.InteractionHand hand, float c,
                                   net.minecraft.world.item.ItemStack stack, float d, com.mojang.blaze3d.vertex.PoseStack pose,
                                   net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
                                   org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        riverfishing$stack = stack;
        riverfishing$hand = hand;
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "submitArmWithItem", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void riverfishing$rod(net.minecraft.client.renderer.item.ItemStackRenderState item, com.mojang.blaze3d.vertex.PoseStack pose,
                                  net.minecraft.client.renderer.SubmitNodeCollector collector, int light, int overlay, int outline) {
        if (!com.riverfishing.client.RodFirstPerson.submit(riverfishing$stack, riverfishing$hand, pose, collector, light)) {
            item.submit(pose, collector, light, overlay, outline);
        }
    }
}
*///?} else {
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Minecraft.class)
public class FirstPersonHandsMixin {
}
//?}
