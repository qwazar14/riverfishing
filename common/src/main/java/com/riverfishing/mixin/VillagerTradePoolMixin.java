package com.riverfishing.mixin;

import com.riverfishing.registry.ModVillagers;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §trade-pool: vanilla hands EVERY profession exactly two offers per level ({@code Villager
 * .TRADES_PER_LEVEL}, a private constant baked into the {@code addOffersFromItemListings} call), and
 * there is no API to raise it for one profession. A fishing shop needs more counter space than that —
 * bait, line, a rod AND a fish buy — so the fisherman's levels are topped up here right after vanilla
 * fills them, and level 1 is guaranteed to buy a common fish (a new angler's first emerald must not
 * depend on a dice roll).
 *
 * <p>Injected at TAIL rather than as a {@code @ModifyArg} on the inner call, so the mixin only has to
 * match {@code updateTrades()} itself — one name, no descriptor. Every villager runs this hook, so
 * {@link ModVillagers#topUpOffers} returns immediately for anyone who isn't our fisherman.
 */
@Mixin(Villager.class)
public abstract class VillagerTradePoolMixin {
    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void riverfishing$topUpFishermanOffers(CallbackInfo ci) {
        ModVillagers.topUpOffers((Villager) (Object) this);
    }
}
