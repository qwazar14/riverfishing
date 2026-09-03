package com.riverfishing.item;

import com.riverfishing.engine.BiteEngine;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fishing.FishingManager;
import com.riverfishing.fishing.FishingPressureData;
import com.riverfishing.fishing.PlayerData;
import com.riverfishing.fishing.StockedData;
import com.riverfishing.registry.ModItems;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import com.riverfishing.water.WaterType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * §breeding: a net. Right-click water and it takes fish out of the region wholesale — no bite, no
 * fight, no card. The seine and the cast net differ only in how many fish, how long the cooldown, and
 * how fast the mesh wears, so the haul lives here once.
 *
 * <p>A net takes what the water HOLDS, not what is biting: every native or settled species of the
 * region, weighted by its stock percent. That is why it is the fish farmer's tool — and why it is
 * poaching anywhere else (see {@link #haul}).
 */
public abstract class NetItem extends Item {
    private final int minFish, maxFish, cooldownTicks;

    protected NetItem(Item.Properties props, int durability, int minFish, int maxFish, int cooldownTicks) {
        // B registers with a bare props(); the net owns its own durability so the wear is a decision
        // made next to the haul size it pays for, not in the registry.
        super(props.durability(durability));
        this.minFish = minFish;
        this.maxFish = maxFish;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The probe's ray so "that water there" means the same thing for every water tool.
        BlockPos water = WaterProbeItem.findWater(level, player);
        if (water == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.riverfishing.no_water")
                        .withStyle(ChatFormatting.RED), false);
            }
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            haul(sp, sl, water);
            stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(hand == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND));
            player.getCooldowns().addCooldown(this, cooldownTicks);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * The haul. Species are the region's residents (native by the community hash, or settled through
     * the stocking book), minus culled ones and minus anything the habitat outright rejects; each is
     * weighted by its stock percent, so a species fished to 0% yields nothing and a packed one
     * dominates the net. Every fish is a real removal ({@code addCatch}) — a net is not a bite.
     */
    private void haul(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        WaterBody body = WaterBodyCache.forLevel(level).get(level, pos);
        if (body.type() == WaterType.NONE) return;
        long region = StockedData.region(pos);
        long chunk = new ChunkPos(pos).toLong();
        long now = level.getGameTime();
        StockedData stocked = StockedData.get(level);
        FishingPressureData pressure = FishingPressureData.get(level);
        RandomSource rng = level.random;

        List<FishProfile> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (FishProfile p : FishProfileManager.get().all()) {
            String id = p.id.getPath();
            if (stocked.isCulled(region, id)) continue;
            if (!FishingManager.residentHere(level, pos, body, p.id)) continue;
            // The community hash can call a shark native to a brook; the habitat score is what keeps
            // the bite engine honest about that, so the net asks it too.
            if (BiteEngine.environmentScore(p, FishingManager.habitatContext(level, pos, body)) <= 0) continue;
            int pct = pressure.stockPercent(chunk, id, now);
            if (pct <= 0) continue;
            pool.add(p);
            weights.add(pct);
            total += pct;
        }

        int count = minFish + rng.nextInt(maxFish - minFish + 1);
        if (pool.isEmpty() || count == 0) {
            sp.displayClientMessage(Component.translatable("message.riverfishing.net_empty")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        int poached = 0;
        for (int i = 0; i < count; i++) {
            FishProfile p = pick(pool, weights, total, rng);
            int weightG = rollWeight(p, rng);
            ItemStack fish = FishItem.create(ModItems.fishItem(p.id), p.id, weightG, lengthCm(p, weightG, rng), true);
            if (!sp.getInventory().add(fish)) sp.drop(fish, false);
            pressure.addCatch(chunk, p.id.getPath(), now);

            // POACHING: a net is legal only in water YOU stocked. A native species is in nobody's book
            // (owner null) — nobody stocked it, so nobody may net it. The haul still happens: this is
            // a simulator, and poaching working is what makes it wrong. The water pays twice.
            UUID owner = stocked.owner(region, p.id.getPath());
            if (owner == null || !owner.equals(sp.getUUID())) {
                pressure.addCatch(chunk, p.id.getPath(), now);
                poached++;
            }
        }

        if (poached > 0) {
            // Every fisherman within earshot "saw" it: the trust the contracts run on drops, once per
            // haul — a net is one act, however many fish came up in it.
            CompoundTag root = PlayerData.root(sp);
            root.putInt("contract_rep", Math.max(0, root.getInt("contract_rep") - 5));
            PlayerData.markDirty(sp);
            sp.displayClientMessage(Component.translatable("message.riverfishing.poaching")
                    .withStyle(ChatFormatting.RED), false);
            // §i: the warden. In his reach the net is his and the fine is due; out of it, the record
            // still grows (fishing/Warden).
            com.riverfishing.fishing.Warden.onPoach(sp, level, pos, poached);
        }
        sp.displayClientMessage(Component.translatable("message.riverfishing.net_haul", count)
                .withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    private static FishProfile pick(List<FishProfile> pool, List<Integer> weights, int total, RandomSource rng) {
        int r = rng.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    /** Uniform between the species minimum and 1.5× its mean: a net takes the run of the water, not its trophies. */
    private static int rollWeight(FishProfile p, RandomSource rng) {
        double hi = Math.min(p.weightMax, p.weightMean * 1.5);
        double lo = Math.min(p.weightMin, hi);
        return (int) Math.round(lo + (hi - lo) * rng.nextDouble());
    }

    /** The bite engine's allometric law (L ∝ W^(1/3)) so a netted fish measures like a caught one. */
    private static int lengthCm(FishProfile p, int weightG, RandomSource rng) {
        double wc = Math.cbrt(Math.max(1.0, weightG));
        double wcMin = Math.cbrt(Math.max(1.0, p.weightMin));
        double wcMax = Math.cbrt(Math.max(1.0, p.weightMax));
        double lf = wcMax > wcMin ? (wc - wcMin) / (wcMax - wcMin) : 0.5;
        double length = p.lengthMin + (p.lengthMax - p.lengthMin) * lf;
        length *= 0.98 + rng.nextDouble() * 0.04;
        return (int) Math.round(Mth.clamp(length, p.lengthMin, p.lengthMax));
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.net").withStyle(ChatFormatting.DARK_GRAY));
    }
}
