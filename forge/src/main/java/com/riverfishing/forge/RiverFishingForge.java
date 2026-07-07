package com.riverfishing.forge;

import com.riverfishing.RiverFishing;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge bootstrap (§multiloader): replaces the old single-project {@code @Mod} class. It hands off to
 * the common {@link RiverFishing#init()}, then — on the client dist only — to the common client
 * bootstrap. Forge-only extras (e.g. the JEI plugin) live in this module.
 */
@Mod(RiverFishing.MODID)
public final class RiverFishingForge {
    public RiverFishingForge() {
        RiverFishing.init();
        // Client bootstrap during mod construction (mod bus still live for the model/render hooks). The
        // dist guard keeps the client-only class off the dedicated server's classloader — the invokestatic
        // that loads ClientInit only runs on the client branch.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.riverfishing.client.ClientInit.init();
        }
    }
}
