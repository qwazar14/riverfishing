package com.riverfishing.mixin;

import com.riverfishing.registry.ModVillagers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §starter-fish: a level-1 fisherman must always buy a common fish — that first emerald for a bleak is
 * the whole point of the profession and cannot depend on a dice roll. Everything else about the
 * §trade-pool rework is data ({@code trade_set}'s {@code amount} is the per-level draw count, and a
 * {@code merchant_predicate} weight folds variants into one pool slot), but the guarantee is not
 * expressible: the draw is pure chance, so it has to be patched up afterwards.
 *
 * <p>Injected at TAIL rather than as a {@code @ModifyArg} on the inner call, so the mixin only has to
 * match {@code updateTrades} itself — one name, no descriptor. §26.1: {@code Villager} moved to
 * {@code net.minecraft.world.entity.npc.villager} and the method took a {@code ServerLevel} parameter
 * (verified with javap against minecraft-merged.jar; the mixin config is {@code required} with
 * {@code defaultRequire: 1}, so a stale target would crash the game rather than fail quietly). Every
 * villager runs this hook, so {@link ModVillagers#ensureStarterFish} returns immediately for anyone who
 * isn't our fisherman on level 1.
 */
@Mixin(Villager.class)
public abstract class VillagerTradePoolMixin {
    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void riverfishing$guaranteeStarterFish(ServerLevel level, CallbackInfo ci) {
        ModVillagers.ensureStarterFish((Villager) (Object) this, level);
    }
}
