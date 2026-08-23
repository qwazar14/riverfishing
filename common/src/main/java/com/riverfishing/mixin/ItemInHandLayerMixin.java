package com.riverfishing.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.client.ClientLineState;
import com.riverfishing.client.RodChain;
import com.riverfishing.client.RodHandTransform;
import com.riverfishing.client.RodModelLayers;
import com.riverfishing.item.RodItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * §rod-bend-3d (§26.1): the THIRD-PERSON rod. Vanilla poses the arm, then submits the flat item
 * model — this redirect swaps that one submit for the bone chain, in the exact frame vanilla would
 * have drawn the item, so translateToHand and the arm rotations are all vanilla's own. The chain
 * refuses (no segment models, /rfrod blank off) → the flat model draws with the tunable sprite pose,
 * same as before. First person rides ItemInHandRendererMixin.
 *
 * <p>The LOCAL player's chain bends with the live §rod-load; everyone else's reads the bend bucket
 * the server writes into the rod's own NBT — synced anyway, and close enough for a rod seen from
 * ten blocks.
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Redirect(method = "submitArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void riverfishing$chainOrItem(ItemStackRenderState itemState, PoseStack pose,
                                          SubmitNodeCollector collector, int light, int overlay, int outline,
                                          ArmedEntityRenderState state, ItemStackRenderState itemState2,
                                          ItemStack stack, HumanoidArm arm, PoseStack pose2,
                                          SubmitNodeCollector collector2, int light2) {
        if (stack.getItem() instanceof RodItem) {
            ItemDisplayContext ctx = arm == HumanoidArm.LEFT
                    ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            String rodKey = RodModelLayers.rodKey(stack);
            if (rodKey != null && RodChain.has(rodKey)) {
                float load = RodChain.localHeld(stack)
                        ? ClientLineState.ownRodLoad()
                        : Mth.clamp((float) com.riverfishing.item.RodData.getBend(stack)
                                / com.riverfishing.item.RodData.BEND_BUCKETS, 0f, 1f);
                pose.pushPose();
                RodHandTransform.apply(pose, ctx, true);
                boolean drew = RodChain.submit(stack, rodKey, load, ctx, pose, collector, light, overlay);
                pose.popPose();
                if (drew) return;   // the chain IS the rod; vanilla's flat model must not stamp over it
            }
            // sprite fallback keeps its live-tunable pose
            RodHandTransform.apply(pose, ctx);
        }
        itemState.submit(pose, collector, light, overlay, outline);
    }
}
