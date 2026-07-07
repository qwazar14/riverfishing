package com.riverfishing.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

/** Fabric implementation of {@link com.riverfishing.platform.PlatformHelper} (§multiloader). */
public final class PlatformHelperImpl {
    private PlatformHelperImpl() {}

    public static String platformName() {
        return "Fabric";
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
