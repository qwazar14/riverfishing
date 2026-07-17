package com.riverfishing.integration.jade;

import com.riverfishing.RiverFishing;
import com.riverfishing.block.AquariumBlock;
import com.riverfishing.block.AquariumBlockEntity;
import com.riverfishing.block.BaitTrapBlock;
import com.riverfishing.block.BaitTrapBlockEntity;
import com.riverfishing.block.MaggotFarmBlock;
import com.riverfishing.block.MaggotFarmBlockEntity;
import com.riverfishing.block.RodPodBlock;
import com.riverfishing.block.RodPodBlockEntity;
import com.riverfishing.block.WormFarmBlock;
import com.riverfishing.block.WormFarmBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade / WTHIT-style look-at info (§pack-integration, 0.4.0). Lives in COMMON with a compileOnly Jade
 * dep: NeoForge finds the {@code @WailaPlugin} annotation by scan, Fabric loads the {@code "jade"}
 * entrypoint declared in fabric.mod.json — either way, without Jade installed this class never loads.
 *
 * <p>One provider covers the five "what's inside?" blocks: worm/maggot farms (stock), the bait trap
 * (gathered livebait), the rod pod (rods mounted) and the aquarium (fish kept) — the counts players
 * currently have to click to discover.
 */
@WailaPlugin
public class RiverFishingJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Provider.INSTANCE, WormFarmBlockEntity.class);
        registration.registerBlockDataProvider(Provider.INSTANCE, MaggotFarmBlockEntity.class);
        registration.registerBlockDataProvider(Provider.INSTANCE, BaitTrapBlockEntity.class);
        registration.registerBlockDataProvider(Provider.INSTANCE, RodPodBlockEntity.class);
        registration.registerBlockDataProvider(Provider.INSTANCE, AquariumBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE, WormFarmBlock.class);
        registration.registerBlockComponent(Provider.INSTANCE, MaggotFarmBlock.class);
        registration.registerBlockComponent(Provider.INSTANCE, BaitTrapBlock.class);
        registration.registerBlockComponent(Provider.INSTANCE, RodPodBlock.class);
        registration.registerBlockComponent(Provider.INSTANCE, AquariumBlock.class);
    }

    enum Provider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = RiverFishing.id("info");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            var be = accessor.getBlockEntity();
            if (be instanceof WormFarmBlockEntity w) {
                data.putInt("rfWorms", w.wormCount());
            } else if (be instanceof MaggotFarmBlockEntity m) {
                data.putInt("rfMaggots", m.maggotCount());
                data.putInt("rfFlesh", m.fleshCount());
            } else if (be instanceof BaitTrapBlockEntity t) {
                data.putInt("rfTrap", t.storedCount());
            } else if (be instanceof RodPodBlockEntity p) {
                int rods = 0;
                for (ItemStack s : p.getRodsForDrop()) if (!s.isEmpty()) rods++;
                data.putInt("rfRods", rods);
            } else if (be instanceof AquariumBlockEntity aq) {
                data.putInt("rfFish", aq.getFishes().size());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag d = accessor.getServerData();
            if (d.contains("rfWorms")) {
                tooltip.add(Component.translatable("jade.riverfishing.worms", d.getInt("rfWorms")));
            }
            if (d.contains("rfMaggots")) {
                tooltip.add(Component.translatable("jade.riverfishing.maggots",
                        d.getInt("rfMaggots"), d.getInt("rfFlesh")));
            }
            if (d.contains("rfTrap")) {
                tooltip.add(Component.translatable("jade.riverfishing.trap", d.getInt("rfTrap")));
            }
            if (d.contains("rfRods")) {
                tooltip.add(Component.translatable("jade.riverfishing.rods", d.getInt("rfRods")));
            }
            if (d.contains("rfFish")) {
                tooltip.add(Component.translatable("jade.riverfishing.fish",
                        d.getInt("rfFish"), AquariumBlockEntity.MAX_FISH));
            }
        }
    }
}
