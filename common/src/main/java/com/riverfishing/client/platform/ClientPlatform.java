package com.riverfishing.client.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.Identifier;

/**
 * The CLIENT platform-abstraction seam (§multiloader). These three hooks have no Architectury wrapper,
 * so each loader implements them in {@code com.riverfishing.client.platform.<forge|fabric>.ClientPlatformImpl}:
 * <ul>
 *   <li>{@link #registerItemRenderers()} — the weight/layer BEWLR on the rod &amp; fish items
 *       (Forge {@code IClientItemExtensions} via a mixin / Fabric {@code BuiltinItemRendererRegistry});</li>
 *   <li>{@link #registerExtraModels()} — bake the sprite-layer models
 *       (Forge {@code ModelEvent.RegisterAdditional} / Fabric {@code ModelLoadingPlugin});</li>
 *   <li>{@link #registerLevelRenderer()} — draw the in-world fishing lines
 *       (Forge {@code RenderLevelStageEvent} / Fabric {@code WorldRenderEvents}).</li>
 * </ul>
 * Everything else (screens, BER, HUD, disconnect, the client command) rides Architectury's own
 * client events from {@link com.riverfishing.client.ClientInit}.
 */
public final class ClientPlatform {
    private ClientPlatform() {}

    @ExpectPlatform
    public static void registerItemRenderers() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /**
     * Register the assembly / rig menu screen factories. On Fabric this delegates to Architectury's
     * {@code MenuRegistry.registerScreenFactory}; on NeoForge that path is deferred to
     * {@code FMLClientSetupEvent}, which fires <em>after</em> {@code RegisterMenuScreensEvent} — too late,
     * so the client had no factory and menus opened with "Failed to create screen for menu type". NeoForge
     * therefore registers screens directly on the native {@code RegisterMenuScreensEvent} instead (this is a
     * no-op there).
     */
    @ExpectPlatform
    public static void registerScreens() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /**
     * Register the tint provider that colours painted lures (§lure-color) — the dyed {@code DyedItemColor}
     * RGB on tint-layer 0. Fabric uses {@code ColorProviderRegistry}, NeoForge the
     * {@code RegisterColorHandlersEvent.Item} mod-bus event.
     */
    @ExpectPlatform
    public static void registerItemColors() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    @ExpectPlatform
    public static void registerExtraModels() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    @ExpectPlatform
    public static void registerLevelRenderer() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /**
     * Set the non-solid render layers for our blocks (§aquarium glass/water, ice hole, bait trap mesh).
     * Forge reads {@code "render_type"} straight from the block model JSON, so this is a no-op there;
     * vanilla/Fabric ignores that field, so Fabric wires it up via {@code BlockRenderLayerMap}.
     */
    @ExpectPlatform
    public static void registerRenderTypes() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /**
     * A baked extra model (§rod-layers / §fish-scale) by its plain {@link Identifier}. Vanilla's
     * {@code ModelManager#getModel} only takes a {@code ModelResourceLocation}; the loader-patched
     * lookup (Forge's overload / Fabric's {@code FabricBakedModelManager}) lives per platform.
     */
    @ExpectPlatform
    public static BakedModel bakedModel(Identifier loc) {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }
}
