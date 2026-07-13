package com.riverfishing.client.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * The CLIENT platform-abstraction seam (§multiloader). These hooks have no Architectury wrapper,
 * so each loader implements them in {@code com.riverfishing.client.platform.<neoforge|fabric>.ClientPlatformImpl}.
 * §26.1: the BEWLR-based hooks (registerItemRenderers / registerExtraModels / bakedModel) are GONE —
 * item icons are data-driven client items now (assets/riverfishing/items/*.json); the dynamic
 * rod-composite / fish-scale icons are a follow-up on the new SpecialModelRenderer path.
 * Everything else (screens, BER, HUD, disconnect, the client command) rides Architectury's own
 * client events from {@link com.riverfishing.client.ClientInit}.
 */
public final class ClientPlatform {
    private ClientPlatform() {}

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

    /** Draw the in-world fishing lines (NeoForge {@code RenderLevelStageEvent} / Fabric {@code WorldRenderEvents}). */
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
}
