package com.riverfishing.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.client.RodHandTransform;
import com.riverfishing.item.RodItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §rod-debug (§26.1): the THIRD-PERSON rod hand pose lives in code (RodHandTransform) so /rfrod can
 * tune it live — injected right before the held item submits, i.e. after the arm attach transforms
 * and before the (depth-lift-only) model display. First person rides ItemInHandRendererMixin.
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Inject(method = "submitArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void riverfishing$rodHandPose(ArmedEntityRenderState state, ItemStackRenderState itemState,
                                          ItemStack stack, HumanoidArm arm, PoseStack pose,
                                          SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (!(stack.getItem() instanceof RodItem)) return;
        RodHandTransform.apply(pose, arm == HumanoidArm.LEFT
                ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }
}
