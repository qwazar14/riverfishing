package com.riverfishing.mixin;

import com.riverfishing.client.FlyLineClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §rope: while the fly line is out, the two mouse buttons are the line hand and nothing else —
 * vanilla's swing, block hit, block mining and item use all stay home. Draining the key clicks was
 * not enough: {@code handleKeybinds} sees the click before any tick event, so the swing had already
 * jerked the rod tip (and the whole line with it) by the time the drain ran.
 */
@Mixin(Minecraft.class)
public abstract class RopeInputMixin {

    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void riverfishing$ropeAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FlyLineClient.active()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack(Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void riverfishing$ropeMine(boolean down, CallbackInfo ci) {
        if (FlyLineClient.active()) ci.cancel();
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void riverfishing$ropeUse(CallbackInfo ci) {
        // Shift + use is the rod's own interface (assembly, rig) — that still has to open, but never
        // while the fly is in the water: swapping tackle under a live drift is a bug farm.
        Minecraft mc = (Minecraft) (Object) this;
        boolean shift = mc.player != null && mc.player.isShiftKeyDown();
        if (FlyLineClient.active() && !(shift && !FlyLineClient.flyOnWater())) ci.cancel();
    }
}
