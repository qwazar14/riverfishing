package com.riverfishing.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.VillagerTrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * §market-live: what a generated trade GIVES, which is the base price before the market moves it.
 *
 * <p>§26.1 made the fisherman's trades data — {@code trade_set/fisherman/level_N.json} — so the
 * emerald count lives in the datapack and {@link VillagerTrade} keeps no getter for it. Reading the
 * field back is what stops the base from being copied into Java, where it would drift away from the
 * generator the first time anyone rebalanced one and not the other. The trade the offer was built
 * from is the base, by definition.
 */
@Mixin(VillagerTrade.class)
public interface VillagerTradeGivesAccessor {
    @Accessor("gives")
    ItemStackTemplate riverfishing$gives();
}
