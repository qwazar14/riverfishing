package com.riverfishing.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.client.ClientLineState;
import com.riverfishing.client.RodHandTransform;
import com.riverfishing.item.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §cast-anim (§26.1): the first-person casting motion — the rod loads BACK while the throw charges
 * (tracking the power bar) and WHIPS forward on release (riding the vanilla swing). The old BEWLR
 * applied this inside the item renderer; data-driven models can't, so the pitch is injected into the
 * arm frame right before the in-hand item renders. Common mixin — both loaders load
 * riverfishing.mixins.json.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void riverfishing$castAnim(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                       PoseStack pose, SubmitNodeCollector collector, int light,
                                       CallbackInfo ci) {
        if (!(stack.getItem() instanceof RodItem)) return;
        if (ctx != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && ctx != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) return;

        float chargePower = 0f;
        // Wind-up only while actively charging a cast (holding, no line out yet) — not during a retrieve.
        if (mc.player.isUsingItem() && mc.player.getUseItem() == stack && !ClientLineState.active()) {
            int used = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
            chargePower = RodItem.castPower(used);
        }
        float swing = mc.player.getAttackAnim(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        float pitch = RodHandTransform.castPitch(chargePower, swing);
        if (pitch != 0f) {
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
        }
        // §rod-debug: the whole first-person hand pose lives in code so /rfrod tunes it LIVE —
        // the model's hand display only carries the per-layer depth lift.
        RodHandTransform.apply(pose, ctx);
    }
}
