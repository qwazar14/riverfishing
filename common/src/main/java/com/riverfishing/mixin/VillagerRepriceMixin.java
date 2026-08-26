package com.riverfishing.mixin;

import com.riverfishing.registry.ModVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §market-live: the fisherman's buy price is recomputed the moment a player opens the counter.
 *
 * <p>It has to be, because the price cannot live where it was written. {@code MarketData.price()} was
 * called inside a {@code VillagerTrades.ItemListing}, and a listing runs only from
 * {@code Villager.updateTrades()}, whose only callers in the whole game are
 * {@code increaseMerchantCareer()} and the first, lazy {@code getOffers()}. {@code restock()} resets
 * uses on the offers it already has; {@code readAdditionalSaveData} overwrites them from NBT. So the
 * emerald count was fixed at the instant a merchant level unlocked and then persisted — the order of
 * the day paid only if a stall happened to level up on that day, with that species in that slot,
 * which is to say almost never, on every version we have ever shipped.
 *
 * <p>HEAD of {@code startTrading} and not RETURN: {@code openTradingScreen} re-reads
 * {@code getOffers()} and builds the {@code sendMerchantOffers} packet from it, so what is changed
 * here is what the player is shown AND what {@code assemble()} hands over. Injecting after would pay
 * a price the screen never displayed. Vanilla's own {@code updateSpecialPrices} sits on the line
 * above, mutating live offers at exactly this moment, which is the precedent.
 *
 * <p>{@code startTrading(Player)} is private with the same single overload on 1.20.1, 1.21.1, 26.1.2
 * and 26.2 — checked with javap against all four game jars, not assumed from the name.
 */
@Mixin(Villager.class)
public abstract class VillagerRepriceMixin {
    @Inject(method = "startTrading", at = @At("HEAD"))
    private void riverfishing$repriceFishBuys(Player player, CallbackInfo ci) {
        ModVillagers.repriceFishBuys((Villager) (Object) this, player);
    }
}
