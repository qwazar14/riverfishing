package com.riverfishing.fabric.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/**
 * §fabric-poi: Architectury registers our {@link PoiType} through the raw registry, but on Fabric that
 * does NOT populate {@code PoiTypes.TYPE_BY_STATE} (Forge auto-does it), so villagers never recognise the
 * Fishing Stall as a job site and never take the fisherman profession. This invoker exposes the private
 * {@code PoiTypes.registerBlockStates} so the Fabric bootstrap can map the stall's states after registration.
 */
@Mixin(PoiTypes.class)
public interface PoiTypesInvoker {
    @Invoker("registerBlockStates")
    static void riverfishing$registerBlockStates(Holder<PoiType> holder, Set<BlockState> states) {
        throw new AssertionError();
    }
}
