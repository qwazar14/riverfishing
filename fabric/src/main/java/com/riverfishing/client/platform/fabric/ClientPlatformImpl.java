package com.riverfishing.client.platform.fabric;

/**
 * Fabric side of the client platform seam (§multiloader) — see
 * {@link com.riverfishing.client.platform.ClientPlatform}. Called from {@code onInitializeClient}.
 * §26.1: most of the old hooks became data-driven — BEWLR item renderers/extra models → client item
 * definitions (assets/riverfishing/items/*.json), painted-lure tints → a {@code minecraft:dye} tint
 * there, block render layers → {@code force_translucent} on model textures (cutout is automatic).
 */
public final class ClientPlatformImpl {
    private ClientPlatformImpl() {}

    /** §26.1: no-op — the DYED_COLOR tint ships in the client item definitions now. */
    public static void registerItemColors() {
    }

    public static void registerScreens() {
        // §26.1: arch's registerScreenFactory is gone — vanilla MenuScreens.register is opened up by
        // fabric-api's transitive classtweaker instead.
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.riverfishing.registry.ModMenus.ROD_ASSEMBLY.get(), com.riverfishing.client.RodAssemblyScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.riverfishing.registry.ModMenus.RIG.get(), com.riverfishing.client.RigScreen::new);
    }

    /**
     * §26.1 TODO(line-render): Fabric removed WorldRenderEvents with the frame-graph renderer and offers
     * no replacement hook yet — the in-world cast-line (§line-multiplayer) is temporarily not drawn on
     * Fabric (rod-pod lines still render through their BER). Follow-up: a small LevelRenderer mixin.
     */
    public static void registerLevelRenderer() {
    }

    /** §26.1: no-op — layers are data-driven (force_translucent in the model; cutout is automatic). */
    public static void registerRenderTypes() {
    }
}
