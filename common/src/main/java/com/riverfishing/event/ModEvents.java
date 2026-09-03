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
                com.riverfishing.fishing.SpookTracker.tick(sp);  // §spook: what the fish just noticed
                com.riverfishing.fishing.ShoalTracker.tick(sp);  // §shoal: what is visible in the water
                FishingManager.trollingTick(sp); // trolling v1 (0.5.0): boat-agnostic towing loop
                FishingManager.finderHudTick(sp); // §finder-hud: the strip, while one is held
                announceDailyOrder(sp); // §market: one chat line per player per Minecraft day
                if (sp.tickCount % 10 == 0) {
                    var level = sp.level();
                    com.riverfishing.fishing.FeedZoneData.get(level)
                            .emitParticles(level, sp.blockPosition(), level.getGameTime());
                }
            }
        });

        // §e §breeding: roe is sold the way a contract is handed in — a trade cannot match the species
        // inside the NBT, so right-clicking the fisherman with the clutch IS the trade.
        dev.architectury.event.events.common.InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!(entity instanceof net.minecraft.world.entity.npc.villager.Villager v)) return EventResult.pass();
            if (!(player.getItemInHand(hand).getItem() instanceof com.riverfishing.item.RoeItem)) return EventResult.pass();
            if (!v.getVillagerData().profession().is(com.riverfishing.registry.ModVillagers.FISHERMAN.getKey())) return EventResult.pass();
            if (player instanceof ServerPlayer sp) com.riverfishing.fishing.RoeSale.sell(sp, player.getItemInHand(hand));
            return EventResult.interruptTrue();
        });

        // §contracts-b1: right-click a fisherman with a contract in hand and the paper is handed in
        // instead of the counter opening. Interrupted on both sides so the client does not open a
        // screen the server is about to refuse.
        dev.architectury.event.events.common.InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!(entity instanceof net.minecraft.world.entity.npc.villager.Villager v)) return EventResult.pass();
            if (!(player.getItemInHand(hand).getItem() instanceof com.riverfishing.item.ContractItem)) return EventResult.pass();
            if (!v.getVillagerData().profession().is(com.riverfishing.registry.ModVillagers.FISHERMAN.getKey())) return EventResult.pass();
            if (player instanceof ServerPlayer sp) com.riverfishing.fishing.Contracts.handIn(sp, player.getItemInHand(hand));
            return EventResult.interruptTrue();
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            FishingManager.clear(player.getUUID());
            com.riverfishing.fishing.SpookTracker.forget(player.getUUID());
        });

        // §g §breeding (0.9.0): a water-body upgrade goes into the ledger the moment it is placed, so a
        // bite never has to scan the swim for them. BREAK below takes it out again.
        BlockEvent.PLACE.register((level, pos, state, placer) -> {
            if (level instanceof net.minecraft.server.level.ServerLevel sl
                    && state.getBlock() instanceof com.riverfishing.block.WaterUpgradeBlock b) {
                com.riverfishing.fishing.WaterUpgrades.get(sl).put(pos, b.kind());
            }
            return EventResult.pass();
        });

        // Worms from digging soil with a shovel (§9.6).
        //? if <26.2 {
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
        //?} else {
        /*BlockEvent.BREAK.register((level, pos, state, player) -> { // arch 21 dropped the xp param
        *///?}
            // §spook: chopping a tree on the bank is the loudest thing an angler can do by accident.
            if (!level.isClientSide()) com.riverfishing.fishing.SpookTracker.onBlockBreak(level, pos);
            // §g: a broken upgrade leaves the ledger (see BlockEvent.PLACE above).
            if (level instanceof net.minecraft.server.level.ServerLevel sl
                    && state.getBlock() instanceof com.riverfishing.block.WaterUpgradeBlock) {
                com.riverfishing.fishing.WaterUpgrades.get(sl).remove(pos);
            }
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

    // §market: the daily-order announcement, once per player per Minecraft day. Without it the order
    // exists only for a player who thinks to open the journal, which is not what a daily is for.
    private static final java.util.Map<java.util.UUID, Long> ORDER_TOLD = new java.util.HashMap<>();

    private static void announceDailyOrder(ServerPlayer sp) {
        // §26.x: three names differ from the 1.21.1 original, not two — ServerPlayer.server is gone as
        // well, so the server comes through the level. Copying the old line and fixing only the clock
        // will not compile.
        long day = sp.level().getServer().overworld().getOverworldClockTime() / 24000L;
        Long told = ORDER_TOLD.get(sp.getUUID());
        if (told != null && told == day) return;
        String species = com.riverfishing.fishing.MarketData.orderOfTheDay(sp.level());
        // The pool is read out of the trade registry, so before the server has one there is no
        // order to name. Say nothing and do not mark the day told — the next tick will have it.
        if (species.isEmpty()) return;
        ORDER_TOLD.put(sp.getUUID(), day);
        sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.riverfishing.daily_order",
                net.minecraft.network.chat.Component.translatable("fish.riverfishing." + species))
                .withStyle(net.minecraft.ChatFormatting.GOLD));
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
