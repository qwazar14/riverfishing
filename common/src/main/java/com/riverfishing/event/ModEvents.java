package com.riverfishing.event;

import com.riverfishing.RiverFishing;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fishing.FishingManager;
import com.riverfishing.registry.ModItems;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.LootEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Common gameplay events (§multiloader). The Forge {@code @SubscribeEvent} handlers become Architectury
 * event registrations, called once from {@link RiverFishing#init()} via {@link #init()}. Mob bait now
 * comes from a loot-table injection (cross-loader) rather than Forge's {@code LivingDropsEvent}.
 *
 * <p>The old {@code PlayerEvent.Clone} journal-copy is gone: Stage 4 moves journal/quest data to a level
 * {@code SavedData} keyed by player UUID, which survives death without any copy.
 */
public final class ModEvents {
    private static final double WORM_CHANCE = 0.10;          // §9.6 dig with a shovel
    private static final float CHICKEN_LIVER_CHANCE = 0.25f; // §3.6
    private static final float MOB_BAIT_CHANCE = 0.33f;      // drowned bloodworm / zombie maggot
    private static final float SEED_CHANCE = 0.05f;           // §bait-crops: per seed type, from grass

    private ModEvents() {}

    public static void init() {
        // Data-driven fish profiles reload with datapacks (§13).
        ReloadListenerRegistry.register(PackType.SERVER_DATA, FishProfileManager.get(), RiverFishing.id("fish_profiles"));

        // Drive each player's fishing session + fed-spot particles server-side.
        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer sp) {
                FishingManager.tick(sp);
                FishingManager.trollingTick(sp); // trolling v1 (0.5.0): boat-agnostic towing loop
                if (sp.tickCount % 10 == 0) {
                    var level = sp.level();
                    com.riverfishing.fishing.FeedZoneData.get(level)
                            .emitParticles(level, sp.blockPosition(), level.getGameTime());
                }
            }
        });

        PlayerEvent.PLAYER_QUIT.register(player -> FishingManager.clear(player.getUUID()));

        // Worms from digging soil with a shovel (§9.6).
        //? if <26.2 {
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
        //?} else {
        /*BlockEvent.BREAK.register((level, pos, state, player) -> { // arch 21 dropped the xp param
        *///?}
            if (!level.isClientSide() && player != null
                    && player.getMainHandItem().getItem() instanceof ShovelItem
                    && isDiggableSoil(state)
                    && level.getRandom().nextDouble() < WORM_CHANCE) {
                Block.popResource(level, pos, new ItemStack(ModItems.WORM.get()));
            }
            return EventResult.pass();
        });

        // Bait from mobs (§bait-gathering): chicken liver, drowned bloodworm, zombie maggot — injected
        // into the vanilla entity loot tables so it works identically on Forge and Fabric.
        LootEvent.MODIFY_LOOT_TABLE.register((lootKey, context, builtin) -> {
            if (matches(lootKey.identifier(), "chicken")) addDrop(context, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.CHICKEN_LIVER.get()), CHICKEN_LIVER_CHANCE);
            else if (matches(lootKey.identifier(), "drowned")) addDrop(context, RiverFishing.id("bloodworm"), MOB_BAIT_CHANCE);
            else if (matches(lootKey.identifier(), "zombie")) addDrop(context, RiverFishing.id("maggot"), MOB_BAIT_CHANCE);
            // §bait-crops: bait-crop seeds drop from grass like vanilla wheat seeds (a little rarer).
            else if (matchesBlock(lootKey.identifier(), "short_grass") || matchesBlock(lootKey.identifier(), "tall_grass")) {
                addDrop(context, RiverFishing.id("corn_seeds"), SEED_CHANCE);
                addDrop(context, RiverFishing.id("pea_seeds"), SEED_CHANCE);
                addDrop(context, RiverFishing.id("barley_seeds"), SEED_CHANCE);
            }
        });
    }

    private static boolean matches(Identifier lootId, String entity) {
        return lootId.getNamespace().equals("minecraft") && lootId.getPath().equals("entities/" + entity);
    }

    private static boolean matchesBlock(Identifier lootId, String block) {
        return lootId.getNamespace().equals("minecraft") && lootId.getPath().equals("blocks/" + block);
    }

    private static void addDrop(LootEvent.LootTableModificationContext context, Identifier itemId, float chance) {
        var item = BuiltInRegistries.ITEM.getValue(itemId);
        context.addPool(LootPool.lootPool()
                .add(LootItem.lootTableItem(item))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                );
    }

    private static boolean isDiggableSoil(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.MUD);
    }
}
