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
}
