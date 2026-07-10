package com.riverfishing.platform.neoforge;

import net.neoforged.fml.ModList;

/** NeoForge implementation of {@link com.riverfishing.platform.PlatformHelper} (§multiloader, 1.21.1). */
public final class PlatformHelperImpl {
    private PlatformHelperImpl() {}

    public static String platformName() {
        return "NeoForge";
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
