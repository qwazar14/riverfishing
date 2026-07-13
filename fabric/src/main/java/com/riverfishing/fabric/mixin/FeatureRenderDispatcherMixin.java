package com.riverfishing.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.client.LineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §26.1: Fabric removed WorldRenderEvents with the frame-graph renderer and offers no replacement, so
 * the in-world cast line (§line-multiplayer) hooks the translucent-feature pass directly — the same
 * stage the rod-pod BER lines render in, right inside the level's main pass.
 */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
    @Inject(method = "renderTranslucentFeatures", at = @At("TAIL"))
    private void riverfishing$renderCastLines(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        LineRenderer.render(new PoseStack(), mc.gameRenderer.getMainCamera().position(),
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
