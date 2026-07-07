package com.riverfishing.event;

import com.riverfishing.RiverFishing;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fishing.FishingManager;
import com.riverfishing.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RiverFishing.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModEvents {
    private static final double WORM_CHANCE = 0.10;        // §9.6 dig with a shovel
    private static final double CHICKEN_LIVER_CHANCE = 0.25; // §3.6 chicken drop

    private ModEvents() {}

    /** Register the data-driven fish profiles as a datapack reload listener (§13). */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(FishProfileManager.get());
    }

    /** Drive each player's fishing session server-side. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer sp) {
            FishingManager.tick(sp);
            // Tint nearby fed spots red so the player can see them (#7).
            if (sp.tickCount % 10 == 0) {
                net.minecraft.server.level.ServerLevel level = sp.serverLevel();
                com.riverfishing.fishing.FeedZoneData.get(level)
                        .emitParticles(level, sp.blockPosition(), level.getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        FishingManager.clear(event.getEntity().getUUID());
    }

    /** Keep the fishing journal (§15) across death. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        for (String tag : new String[]{com.riverfishing.fishing.JournalData.TAG,
                com.riverfishing.quest.QuestData.TAG}) {
            net.minecraft.nbt.CompoundTag old = event.getOriginal().getPersistentData().getCompound(tag);
            if (!old.isEmpty()) {
                event.getEntity().getPersistentData().put(tag, old.copy());
            }
        }
    }

    /** Worms from digging soil with a shovel (§9.6). */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        if (event.getPlayer() == null) return;
        ItemStack tool = event.getPlayer().getMainHandItem();
        if (!(tool.getItem() instanceof ShovelItem)) return;
        if (!isDiggableSoil(event.getState())) return;
        if (level.getRandom().nextDouble() < WORM_CHANCE) {
            Block.popResource(level, event.getPos(), new ItemStack(ModItems.WORM.get()));
        }
    }

    private static boolean isDiggableSoil(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.MUD);
    }

    /** Bait from mobs: chicken liver for catfish (§3.6) and maggots from zombies (§bait-gathering). */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Chicken chicken) {
            if (chicken.level().getRandom().nextDouble() < CHICKEN_LIVER_CHANCE) {
                ItemStack liver = new ItemStack(ModItems.CHICKEN_LIVER.get());
                event.getDrops().add(new ItemEntity(chicken.level(),
                        chicken.getX(), chicken.getY(), chicken.getZ(), liver));
            }
            return;
        }
        // Drowned FIRST — it extends Zombie, so the order matters (§bait-gathering).
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Drowned drowned) {
            if (drowned.level().getRandom().nextDouble() < 0.33) {
                ItemStack bloodworm = new ItemStack(
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                                com.riverfishing.RiverFishing.id("bloodworm")));
                event.getDrops().add(new ItemEntity(drowned.level(),
                        drowned.getX(), drowned.getY(), drowned.getZ(), bloodworm));
            }
            return;
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Zombie zombie) {
            if (zombie.level().getRandom().nextDouble() < 0.33) {
                ItemStack maggot = new ItemStack(
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                                com.riverfishing.RiverFishing.id("maggot")));
                event.getDrops().add(new ItemEntity(zombie.level(),
                        zombie.getX(), zombie.getY(), zombie.getZ(), maggot));
            }
        }
    }
}
