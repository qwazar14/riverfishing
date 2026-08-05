package com.riverfishing.mixin;

import com.riverfishing.client.ClientLineState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §fight-edge (0.7.2): you cannot walk off a pier while a fish is on.
 *
 * <p>Foxsy1798 reported two separate things about the fight input and only one of them is about keys.
 * The other is that answering the boss bar could walk you off a pier — and §fight-keys does not fix that
 * one, because §fight-footwork deliberately asks you to back away from the water to win line, and that
 * request stands for the whole fight whatever the rod is bound to. So the mod is permanently telling
 * anglers to walk backwards, and it has to stop them walking backwards off a ledge.
 *
 * <p>Vanilla already solves exactly this problem — it is why you cannot shuffle off a block while
 * crouching. {@code Player.maybeBackOffFromEdge} clamps the movement vector at a drop, and it is gated on
 * {@code isStayingOnGroundSurface()}, which vanilla answers with "is the player sneaking". Saying yes
 * while a fish is on borrows the whole behaviour rather than reimplementing it: no new clamp, no new
 * constant, and it is identical on every version because the signature is.
 *
 * <p>Client-side, and guarded to the LOCAL player. Movement is client-authoritative here, so this is the
 * side where it belongs; and {@link ClientLineState} only knows about lines it has been told about, so
 * asking it about anyone else would be asking the wrong object.
 */
@Mixin(Player.class)
public abstract class FightEdgeGuardMixin {

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true, require = 0)
    private void riverfishing$dontWalkOffThePier(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (Object) this != mc.player) return;
        ClientLineState.Line own = ClientLineState.lines().get(mc.player.getId());
        if (own != null && own.fighting) cir.setReturnValue(true);
    }
}
