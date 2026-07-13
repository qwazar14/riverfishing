package com.riverfishing.util;

import dev.architectury.utils.EnvExecutor;
import dev.architectury.utils.GameInstance;
import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;

/**
 * §data-components (1.21): serializing/parsing a nested {@link net.minecraft.world.item.ItemStack} now
 * needs a {@link HolderLookup.Provider} (registry access), because component data is registry-linked.
 * Rod/rig contents live inside item NBT and are read/written from both server logic and client rendering,
 * so this resolves the current registry access on either side without threading it through every caller.
 */
public final class RegistryHelper {
    private RegistryHelper() {}

    /** Current registry access — the running server's, else the connected client's; null only off-world. */
    public static HolderLookup.Provider provider() {
        MinecraftServer server = GameInstance.getServer();
        if (server != null) return server.registryAccess();
        // Client dist only — the nested lambda keeps Minecraft off the dedicated server's classloader.
        return EnvExecutor.getEnvSpecific(() -> RegistryHelper::clientProvider, () -> () -> null);
    }

    private static HolderLookup.Provider clientProvider() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.getConnection() != null ? mc.getConnection().registryAccess() : null;
    }

    // ---- §26.1: ItemStack.save/parseOptional(provider, tag) are gone — nested-stack NBT (rod/rig
    // contents inside custom_data) goes through the stack codec with registry-aware NbtOps instead.

    private static com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> nbtOps() {
        HolderLookup.Provider p = provider();
        return p != null
                ? p.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE)
                : net.minecraft.nbt.NbtOps.INSTANCE;
    }

    /** Old {@code stack.save(provider, new CompoundTag())}: non-empty stack → {id,count,components?}. */
    public static net.minecraft.nbt.CompoundTag saveStack(net.minecraft.world.item.ItemStack stack) {
        return (net.minecraft.nbt.CompoundTag) net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
                .encodeStart(nbtOps(), stack)
                .result().orElseGet(net.minecraft.nbt.CompoundTag::new);
    }

    /** Old {@code ItemStack.parseOptional(provider, tag)}: missing/broken data → EMPTY, never null. */
    public static net.minecraft.world.item.ItemStack loadStack(net.minecraft.nbt.CompoundTag tag) {
        if (tag.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        return net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
                .parse(nbtOps(), tag)
                .result().orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }
}
