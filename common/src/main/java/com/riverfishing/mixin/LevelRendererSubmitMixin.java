package com.riverfishing.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.client.LineRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * §26.2: the level pass is fully retained — the in-world cast line (§line-multiplayer) submits its
 * geometry through the frame's SubmitNodeCollector at the end of the level's feature submission.
 * Loader-neutral (targets vanilla), so ONE mixin serves Fabric and NeoForge; on 26.1 this class is
 * an empty no-op and each loader keeps its own immediate-mode hook.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererSubmitMixin {
    //? if >=26.2 {
    /*@Inject(method = "submitFeatures", at = @At("TAIL"))
    private void riverfishing$submitCastLines(net.minecraft.client.renderer.state.level.LevelRenderState state,
            net.minecraft.client.renderer.SubmitNodeCollector collector, boolean translucentOnly,
            CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        LineRenderer.submit(new PoseStack(), state.cameraRenderState.pos,
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), collector);
    }
    *///?}
}
