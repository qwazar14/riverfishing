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

    /** §tackle-box: the placed box's insert. Item tints are data-driven on 26.x; block tints are not. */
    public static void registerColors() {
        for (var box : com.riverfishing.registry.ModBlocks.TACKLE_BOXES.values()) {
            net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(
                    com.riverfishing.client.TackleBoxTint.LAYERS, box.get());
        }
    }

    public static void registerScreens() {
        // §26.1: arch's registerScreenFactory is gone — vanilla MenuScreens.register is opened up by
        // fabric-api's transitive classtweaker instead.
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.riverfishing.registry.ModMenus.ROD_ASSEMBLY.get(), com.riverfishing.client.RodAssemblyScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.riverfishing.registry.ModMenus.RIG.get(), com.riverfishing.client.RigScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.riverfishing.registry.ModMenus.TACKLE_STATION.get(), com.riverfishing.client.TackleStationScreen::new);
        // §keepnet + §tackle-box (0.7.0): the two boxes.
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.riverfishing.registry.ModMenus.KEEPNET.get(), com.riverfishing.client.KeepnetScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(
                com.riverfishing.registry.ModMenus.TACKLE_BOX.get(), com.riverfishing.client.TackleBoxScreen::new);
    }

    /** §26.1: handled by FeatureRenderDispatcherMixin (WorldRenderEvents is gone) — nothing to register. */
    public static void registerLevelRenderer() {
    }

    /** §26.1: no-op — layers are data-driven (force_translucent in the model; cutout is automatic). */
    public static void registerRenderTypes() {
    }
}
