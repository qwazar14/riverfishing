package com.riverfishing.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.client.LineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §26.1: Fabric removed WorldRenderEvents with the frame-graph renderer and offers no replacement, so
 * the in-world cast line (§line-multiplayer) hooks the LEVEL's main pass directly, right after the
 * translucent features render — the same spot NeoForge fires AfterTranslucentBlocks.
 *
 * <p>NOT a hook on FeatureRenderDispatcher.renderTranslucentFeatures itself: that dispatcher also runs
 * for GUI/hotbar and the first-person hand item, which drew the line 2–3× per frame with mismatched
 * matrices (visible as angle-dependent duplicates).
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    //? if <26.2 {
    @Inject(method = "lambda$addMainPass$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentFeatures()V",
                    shift = At.Shift.AFTER))
    private void riverfishing$renderCastLines(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        LineRenderer.render(new PoseStack(), mc.gameRenderer.getMainCamera().position(),
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
    //?}
    // On 26.2 the class is an empty no-op: the cast line goes through the loader-neutral common
    // LevelRendererSubmitMixin (submit-based) instead.
}
