package com.riverfishing.fish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riverfishing.RiverFishing;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads fish profiles from {@code data/<namespace>/fish_profiles/*.json} and exposes them to the
 * bite engine. Reloads with {@code /reload}, so the whole game can be re-balanced without a rebuild (§12).
 */
public final class FishProfileManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "fish_profiles";

    private static final FishProfileManager INSTANCE = new FishProfileManager();
    private Map<Identifier, FishProfile> profiles = Collections.emptyMap();

    private FishProfileManager() {
        super(GSON, DIRECTORY);
    }

    public static FishProfileManager get() {
        return INSTANCE;
    }

    public Collection<FishProfile> all() {
        return profiles.values();
    }

    public FishProfile byId(Identifier id) {
        return profiles.get(id);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, FishProfile> parsed = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : data.entrySet()) {
            Identifier file = entry.getKey();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                FishProfile profile = FishProfile.fromJson(file, json);
                parsed.put(file, profile);
            } catch (Exception e) {
                RiverFishing.LOGGER.error("Failed to parse fish profile {}: {}", file, e.toString());
            }
        }
        this.profiles = parsed;
        RiverFishing.LOGGER.info("Loaded {} fish profiles", parsed.size());
    }
}
