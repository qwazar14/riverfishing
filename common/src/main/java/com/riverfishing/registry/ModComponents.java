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

    /** Marks a fish specimen as prime grade (§prime-fish, ≥70% of the species' max weight). Set at catch
     *  time and required by the fisherman's buy-trade {@code ItemCost}, so only prime fish are accepted. */
    public static final RegistrySupplier<DataComponentType<Boolean>> PRIME =
            REGISTER.register("prime", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    public static void init() {
        REGISTER.register();
    }

    private ModComponents() {}
}
