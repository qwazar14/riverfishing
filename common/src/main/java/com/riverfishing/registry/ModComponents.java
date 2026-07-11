package com.riverfishing.registry;

import com.mojang.serialization.Codec;
import com.riverfishing.RiverFishing;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Registered item data components (§data-components, 1.21). The mod stores most per-stack state in
 * {@code minecraft:custom_data} via {@link com.riverfishing.item.StackNbt}, but the fisherman's
 * buy-trade needs a component the villager {@code ItemCost} can gate on: 1.21 {@code ItemCost} matches
 * components <em>exactly</em> (no subset-NBT match like 1.20.1), so a dedicated {@link #PRIME} flag is
 * required rather than a {@code custom_data} subtag.
 */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.DATA_COMPONENT_TYPE);

    /**
     * Prime grade (§prime-fish): present iff the specimen is ≥70% of the species' max weight, and its VALUE
     * is that species' minimum accepted weight in grams. Set at catch (see {@code FishItem.gradePrime}) and
     * expected by the fisherman's buy-trade {@code ItemCost} — 1.21 {@code ItemCost} matches components
     * exactly, so the value doubles as both the match key (same per species) AND the weight the trade slot
     * shows: {@code ItemCost} reconstructs its display stack from the predicate, so the threshold has to ride
     * a synced component to appear in the GUI (a server-only display stack would be lost on the wire).
     */
    public static final RegistrySupplier<DataComponentType<Integer>> PRIME =
            REGISTER.register("prime", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static void init() {
        REGISTER.register();
    }

    private ModComponents() {}
}
