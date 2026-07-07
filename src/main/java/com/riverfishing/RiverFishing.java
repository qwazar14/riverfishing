package com.riverfishing;

import com.mojang.logging.LogUtils;
import com.riverfishing.config.RiverFishingConfig;
import com.riverfishing.registry.ModBlockEntities;
import com.riverfishing.registry.ModBlocks;
import com.riverfishing.registry.ModCreativeTabs;
import com.riverfishing.registry.ModItems;
import com.riverfishing.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RiverFishing.MODID)
public class RiverFishing {
    public static final String MODID = "riverfishing";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RiverFishing() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.REGISTER.register(modBus);   // load ModItems first (ALL list) so block items can join it
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        com.riverfishing.registry.ModVillagers.POI_TYPES.register(modBus);
        com.riverfishing.registry.ModVillagers.PROFESSIONS.register(modBus);
        ModMenus.REGISTER.register(modBus);
        com.riverfishing.registry.ModRecipes.REGISTER.register(modBus);
        com.riverfishing.registry.ModSounds.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RiverFishingConfig.SPEC);
        com.riverfishing.network.ModNetwork.register();
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            // Villagers find job sites through PoiTypes' state->POI map. If our stall's states are not
            // in it (Forge doesn't always populate it for modded POIs), register them manually — this
            // is what makes an unemployed villager actually take the fisherman profession.
            var stall = ModBlocks.FISHING_STALL.get();
            boolean mapped = net.minecraft.world.entity.ai.village.poi.PoiTypes
                    .forState(stall.defaultBlockState()).isPresent();
            if (!mapped) {
                // registerBlockStates is private; find PoiTypes' single static state->POI map by TYPE
                // (obfuscation-proof) and add our stall states directly.
                try {
                    var holder = net.minecraftforge.registries.ForgeRegistries.POI_TYPES
                            .getHolder(com.riverfishing.registry.ModVillagers.FISHERMAN_POI.get()).orElseThrow();
                    java.util.Map<net.minecraft.world.level.block.state.BlockState, Object> map = null;
                    for (var f : net.minecraft.world.entity.ai.village.poi.PoiTypes.class.getDeclaredFields()) {
                        if (java.util.Map.class.isAssignableFrom(f.getType())
                                && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            f.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            var m = (java.util.Map<net.minecraft.world.level.block.state.BlockState, Object>) f.get(null);
                            map = m;
                            break;
                        }
                    }
                    if (map != null) {
                        for (var state : stall.getStateDefinition().getPossibleStates()) {
                            map.putIfAbsent(state, holder);
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.error("River Fishing: failed to register stall POI states manually", ex);
                }
            }
            LOGGER.info("River Fishing setup: profession={}, fishing-stall mappedToPoi={} (was {} before setup)",
                    com.riverfishing.registry.ModVillagers.FISHERMAN.getId(),
                    net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(stall.defaultBlockState()).isPresent(),
                    mapped);
        }));
        LOGGER.info("River Fishing loaded: {} items registered", ModItems.ALL.size());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
