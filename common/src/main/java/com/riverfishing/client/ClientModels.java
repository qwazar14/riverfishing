package com.riverfishing.client;

import com.riverfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The extra sprite-layer models the client must bake so {@link RodItemRenderer} (§rod-layers) and
 * {@link FishItemRenderer} (§fish-scale) can stack/scale them. On Forge these are declared through
 * {@code ModelEvent.RegisterAdditional}, on Fabric through the {@code ModelLoadingPlugin}; both go
 * through {@link com.riverfishing.client.platform.ClientPlatform#registerExtraModels()}.
 */
public final class ClientModels {
    private ClientModels() {}

    /** Every layer/icon model that MIGHT exist — the rod sprite layers plus the per-species fish icons. */
    public static List<Identifier> allCandidates() {
        List<Identifier> list = new ArrayList<>(RodModelLayers.candidates());
        for (String sp : ModItems.FISH_SPECIES) {
            list.add(FishItemRenderer.iconModel(sp));
        }
        return list;
    }

    /**
     * Filters to the models whose JSON is actually present, so undrawn variants cost nothing and don't
     * spam the log (§rod-layers). Safe to call at model-registration time on either loader — the client
     * resource manager is up by then.
     */
    public static List<Identifier> present(List<Identifier> in) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        List<Identifier> out = new ArrayList<>();
        for (Identifier loc : in) {
            Identifier json = Identifier.fromNamespaceAndPath(loc.getNamespace(), "models/" + loc.getPath() + ".json");
            if (rm.getResource(json).isPresent()) {
                out.add(loc);
            }
        }
        return out;
    }
}
