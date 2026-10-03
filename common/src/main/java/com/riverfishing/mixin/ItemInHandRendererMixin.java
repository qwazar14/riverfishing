package com.riverfishing.mixin;

/**
 * §cast-anim (§26.1): 26.2's door onto the first-person rod — {@link com.riverfishing.client.RodFirstPerson}.
 * ItemInHandRenderer is gone in 26.3 (FirstPersonHandsMixin is its door there), so on 26.3 this is an empty
 * mixin with nothing to apply. No comments inside the version blocks: Stonecutter turns their comment marks
 * into code when it swaps a block in.
 */
//? if <26.3 {
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "renderItem", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void riverfishing$castAnim(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack,
                                       net.minecraft.world.item.ItemDisplayContext ctx, com.mojang.blaze3d.vertex.PoseStack pose,
                                       net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.riverfishing.client.RodFirstPerson.submit(entity, stack, ctx, pose, collector, light)) ci.cancel();
    }
}
//?} else {
/*@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Minecraft.class)
public class ItemInHandRendererMixin {
}
*///?}
