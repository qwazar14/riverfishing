package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.registry.ModBlockEntities;
import com.riverfishing.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = RiverFishing.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.ROD_ASSEMBLY.get(), RodAssemblyScreen::new);
            MenuScreens.register(ModMenus.RIG.get(), RigScreen::new);
            // Aquarium render layers come from each part model's "render_type" (glass/water =
            // translucent, wood base = solid), so no ItemBlockRenderTypes override is needed.
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TROPHY_STAND.get(), TrophyStandRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ROD_POD.get(), RodPodRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.AQUARIUM.get(), AquariumRenderer::new);
    }

    /**
     * Bakes the assembled-rod sprite layers (§rod-layers) so {@link RodItemRenderer} can stack them.
     * We only register a layer whose model JSON actually exists, so un-drawn variants cost nothing
     * and don't spam the log.
     */
    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        for (ResourceLocation loc : RodModelLayers.candidates()) {
            registerIfPresent(event, rm, loc);
        }
        // Per-species fish sprites the FishItemRenderer scales by weight (§fish-scale).
        for (String sp : com.riverfishing.registry.ModItems.FISH_SPECIES) {
            registerIfPresent(event, rm, FishItemRenderer.iconModel(sp));
        }
    }

    private static void registerIfPresent(ModelEvent.RegisterAdditional event, ResourceManager rm, ResourceLocation loc) {
        ResourceLocation json = new ResourceLocation(loc.getNamespace(), "models/" + loc.getPath() + ".json");
        if (rm.getResource(json).isPresent()) {
            event.register(loc);
        }
    }
}
