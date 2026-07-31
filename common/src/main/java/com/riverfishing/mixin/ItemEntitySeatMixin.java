package com.riverfishing.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §fish-seat (0.7.0): a dropped fish sits on its belly instead of hanging in the air.
 *
 * <p>26.x made the item model do the sizing (§fish-scale), and vanilla seats a dropped item by putting
 * its MODEL BOUNDING BOX on the ground — {@code translate(0, bob - box.minY + 1/16, 0)}. That box is
 * not the fish: {@code item/generated} emits its front and back faces as FULL rectangles of the sprite,
 * silhouette or not, so the box is the whole 256px canvas including the empty sky above the fish and
 * the empty water below it. At the ×1 every other item uses, that margin is a couple of pixels and
 * nobody notices. On a legendary sturgeon at ×5 it is two and a half blocks, and the fish hangs there
 * with its shadow a long way underneath.
 *
 * <p>Moving the model down inside its own transform cannot fix it — the lift is {@code -minY}, so a
 * shift down moves minY by the same amount and vanilla lifts it straight back. The correction has to
 * happen here, after the lift.
 *
 * <p>The number is not invented: {@link com.riverfishing.client.FishBounds} already measures how much
 * of its canvas each species fills, because the keepnet needs it to fit a fish to its cells. Half of
 * what is left over is the margin below, and the box's own height is the scale that was applied — so
 * neither the bucket ladder nor the per-context caps are duplicated here, and they cannot drift out
 * of step with this.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntitySeatMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;"
            + "Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void riverfishing$measureFish(ItemEntity entity, ItemEntityRenderState state, float partialTick,
                                          CallbackInfo ci) {
        Identifier sp = com.riverfishing.item.FishItem.getSpecies(entity.getItem());
        float margin = 0f;
        if (sp != null) {
            // FishBounds is {width, height} as fractions of the square canvas; the fish is drawn across
            // the middle of it, so half the leftover height is the empty strip under the belly.
            margin = Math.max(0f, (1f - com.riverfishing.client.FishBounds.of(sp.getPath())[1]) * 0.5f);
        }
        ((SeatedFish) state).riverfishing$setBottomMargin(margin);
    }

    /** Straight after vanilla's own lift — the first {@code translate} in the method. */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void riverfishing$seatFish(ItemEntityRenderState state, PoseStack pose,
                                       SubmitNodeCollector collector, CameraRenderState camera,
                                       CallbackInfo ci) {
        float margin = ((SeatedFish) state).riverfishing$bottomMargin();
        if (margin <= 0f) return;   // not a fish: vanilla's seating is already right
        AABB box = state.item.getModelBoundingBox();
        pose.translate(0f, (float) -(margin * (box.maxY - box.minY)), 0f);
    }
}
