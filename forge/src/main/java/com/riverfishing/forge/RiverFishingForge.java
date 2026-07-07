package com.riverfishing.forge;

import com.riverfishing.RiverFishing;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge bootstrap (§multiloader): replaces the old single-project {@code @Mod} class. It does nothing
 * Forge-specific beyond existing — it just hands off to the common {@link RiverFishing#init()}.
 * Forge-only extras (e.g. the JEI plugin) live in this module, reached through the platform layer.
 */
@Mod(RiverFishing.MODID)
public final class RiverFishingForge {
    public RiverFishingForge() {
        RiverFishing.init();
    }
}
