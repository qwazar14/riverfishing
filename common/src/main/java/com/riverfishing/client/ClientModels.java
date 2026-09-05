package com.riverfishing.client;

import com.riverfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
    public static List<ResourceLocation> allCandidates() {
        List<ResourceLocation> list = new ArrayList<>(RodModelLayers.candidates());
        for (String sp : ModItems.FISH_SPECIES) {
            list.add(FishItemRenderer.iconModel(sp));
        }
        list.add(FryItemRenderer.FALLBACK); // §breeding: the static fry icon the procedural one falls back to
        // §pattern-mask: one flat mask model per patterned draw per family. Unlisted here they would
        // never be baked, and the renderer would draw the missing model — which is nothing, silently.
        for (String draw : FishItemRenderer.PATTERN_DRAWS) {
            for (String fam : com.riverfishing.fish.Pattern.families()) {
                if (!"plain".equals(fam)) list.add(FishItemRenderer.patternModel(draw, fam));
            }
        }
        return list;
    }

    /**
     * Filters to the models whose JSON is actually present, so undrawn variants cost nothing and don't
     * spam the log (§rod-layers). Safe to call at model-registration time on either loader — the client
     * resource manager is up by then.
     */
    public static List<ResourceLocation> present(List<ResourceLocation> in) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation loc : in) {
            ResourceLocation json = ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), "models/" + loc.getPath() + ".json");
            if (rm.getResource(json).isPresent()) {
                out.add(loc);
            }
        }
        return out;
    }
}
