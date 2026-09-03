package com.riverfishing.client.platform.fabric;

import com.riverfishing.client.ClientModels;
import com.riverfishing.client.FishItemRenderer;
import com.riverfishing.client.LineRenderer;
import com.riverfishing.client.RodItemRenderer;
import com.riverfishing.registry.ModBlocks;
import com.riverfishing.registry.ModItems;
import dev.architectury.registry.registries.RegistrySupplier;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Fabric side of the client platform seam (§multiloader) — see
 * {@link com.riverfishing.client.platform.ClientPlatform}. Called from {@code onInitializeClient}.
 */
public final class ClientPlatformImpl {
    private ClientPlatformImpl() {}

    public static void registerItemColors() {
        net.minecraft.client.color.item.ItemColor tint = (stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            net.minecraft.world.item.component.DyedItemColor dc =
                    stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR);
            return dc != null ? (0xFF000000 | dc.rgb()) : -1;
        };
        for (RegistrySupplier<Item> r : ModItems.ALL) {
            if (r.get() instanceof com.riverfishing.item.BaitItem b && b.artificial()) {
                net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(tint, r.get());
            }
        }
        // §tackle-box: the dyed inserts, on the item and on the placed box alike. Both read the same
        // stack, so a box cannot look one colour in the hand and another on the bank.
        net.minecraft.client.color.item.ItemColor boxTint = (stack, tintIndex) ->
                tintIndex == 1 ? (0xFF000000 | com.riverfishing.item.TackleBoxItem.color(stack)) : -1;
        for (RegistrySupplier<Item> r : ModItems.ALL) {
            if (r.get() instanceof com.riverfishing.item.TackleBoxItem) {
                net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(boxTint, r.get());
            }
        }
        for (var b : com.riverfishing.registry.ModBlocks.TACKLE_BOXES.values()) {
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                    (state, view, pos, tintIndex) -> {
                        if (tintIndex != 1) return -1;
                        return 0xFF000000 | (view != null && pos != null
                                && view.getBlockEntity(pos)
                                        instanceof com.riverfishing.block.TackleBoxBlockEntity be
                                ? be.color() : 0xE8E6DF);
                    }, b.get());
        }
        // §groundbait-tint: the jar's speckles wear the mix's own colour (layer 1).
        for (RegistrySupplier<Item> r : ModItems.ALL) {
            if (r.get() instanceof com.riverfishing.item.GroundbaitItem) {
                net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                        com.riverfishing.item.GroundbaitItem::speckleTint, r.get());
            }
        }

        // §morph: the fish's own colour — age shading and its morph, from the shared table.
        net.minecraft.client.color.item.ItemColor fish = com.riverfishing.client.FishTint::itemColor;
        for (RegistrySupplier<Item> r : ModItems.FISH_ITEMS.values()) {
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(fish, r.get());
        }
    }

    public static void registerScreens() {
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.ROD_ASSEMBLY.get(), com.riverfishing.client.RodAssemblyScreen::new);
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.RIG.get(), com.riverfishing.client.RigScreen::new);
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.TACKLE_STATION.get(), com.riverfishing.client.TackleStationScreen::new);
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.KEEPNET.get(), com.riverfishing.client.KeepnetScreen::new);
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.TACKLE_BOX.get(), com.riverfishing.client.TackleBoxScreen::new);
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.AQUARIUM.get(), com.riverfishing.client.AquariumScreen::new);
    }

    public static void registerItemRenderers() {
        // The composited rod icon (§rod-layers) — one shared renderer over all rod blanks.
        BuiltinItemRendererRegistry.DynamicItemRenderer rod =
                (stack, ctx, pose, buffers, light, overlay) ->
                        RodItemRenderer.get().renderByItem(stack, ctx, pose, buffers, light, overlay);
        for (RegistrySupplier<Item> r : ModItems.RODS) {
            BuiltinItemRendererRegistry.INSTANCE.register(r.get(), rod);
        }
        // The weight-scaled fish icon (§fish-scale) — one shared renderer over every species item.
        BuiltinItemRendererRegistry.DynamicItemRenderer fish =
                (stack, ctx, pose, buffers, light, overlay) ->
                        FishItemRenderer.get().renderByItem(stack, ctx, pose, buffers, light, overlay);
        for (RegistrySupplier<Item> f : ModItems.FISH_ITEMS.values()) {
            BuiltinItemRendererRegistry.INSTANCE.register(f.get(), fish);
        }
        // §breeding: the fry bucket draws three of its species' sprite.
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.FRY.get(),
                (stack, ctx, pose, buffers, light, overlay) ->
                        com.riverfishing.client.FryItemRenderer.get().renderByItem(stack, ctx, pose, buffers, light, overlay));
    }

    public static void registerExtraModels() {
        ModelLoadingPlugin.register(ctx -> ctx.addModels(ClientModels.present(ClientModels.allCandidates())));
    }

    public static void registerLevelRenderer() {
        // §shoal is UNDER the water, so it has to be drawn before the water is — the translucent terrain
        // pass writes depth, and anything submitted after it that sits behind the surface is thrown away
        // by the depth test. This is the same pass vanilla draws a squid in, and it gets the water's own
        // tint blended over the fish for free.
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                com.riverfishing.client.ShoalRenderer.render(context.matrixStack(),
                        context.camera().getPosition(),
                        context.tickCounter().getGameTimeDeltaPartialTick(false)));
        // The line is above the water, so it stays after the water.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                LineRenderer.render(context.matrixStack(), context.camera().getPosition(),
                        context.tickCounter().getGameTimeDeltaPartialTick(false)));
    }

    /** Vanilla/Fabric ignores the model's "render_type", so wire the non-solid layers up explicitly. */
    public static void registerRenderTypes() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.AQUARIUM.get(), RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ICE_HOLE.get(), RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BAIT_TRAP.get(), RenderType.cutout());
        // §mini-aquarium: the glass tank needs cutout (NeoForge reads render_type from the model JSON).
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TROPHY_STAND.get(), RenderType.cutout());
        // §bait-crops: crop cross-models need cutout (NeoForge reads it from the model JSON).
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CORN_CROP.get(), RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PEA_CROP.get(), RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BARLEY_CROP.get(), RenderType.cutout());
    }

    /** Fabric's model-loading API mixes {@code getModel(ResourceLocation)} in via FabricBakedModelManager. */
    public static BakedModel bakedModel(ResourceLocation loc) {
        return ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(loc);
    }
}
