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
import net.minecraft.world.InteractionResult;
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The probe's ray so "that water there" means the same thing for every water tool.
        BlockPos water = WaterProbeItem.findWater(level, player);
        if (water == null) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.riverfishing.no_water")
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            haul(sp, sl, water);
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            // §pond-haste: your own pond is worked, not raided — a third of the wait.
            java.util.UUID pondOwner0 = com.riverfishing.fishing.PondData.owner(sl, water);
            player.getCooldowns().addCooldown(stack, pondOwner0 != null && pondOwner0.equals(sp.getUUID()) ? cooldownTicks / 3 : cooldownTicks);
        }
        return InteractionResult.SUCCESS;
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
        long chunk = ChunkPos.pack(pos);
        long now = level.getGameTime();
        StockedData stocked = StockedData.get(level);
        FishingPressureData pressure = FishingPressureData.get(level);
        RandomSource rng = level.getRandom();
        // §pond: in a claimed pond the OWNER is legal for every species — his fish, his net. Anyone else
        // is poaching the lot, native book or no. Outside claimed water the stocking book rules as before.
        UUID pondOwner = com.riverfishing.fishing.PondData.owner(level, pos);

        List<FishProfile> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (FishProfile p : FishProfileManager.get().all()) {
            String id = p.id.getPath();
            if (stocked.isCulled(region, id)) continue;
            // §pond: nothing is resident in a claimed pond but what was put in — so the transplants still
            // dispersing there count too, or the net would come up empty the day after stocking.
            if (!FishingManager.residentHere(level, pos, body, p.id)
                    && !(pondOwner != null && pressure.surplusAround(pos.getX() >> 4, pos.getZ() >> 4, id, now) > 0)) continue;
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
            sp.sendOverlayMessage(Component.translatable("message.riverfishing.net_empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        int poached = 0;
        for (int i = 0; i < count; i++) {
            FishProfile p = pick(pool, weights, total, rng);
            int weightG = rollWeight(p, rng);
            ItemStack fish = FishItem.create(ModItems.fishItem(p.id), p.id, weightG, lengthCm(p, weightG, rng), true);
            pressure.addCatch(chunk, p.id.getPath(), now);
            // §net-ledger: a netted fish pays the ledger exactly as a landed one does — a settled water
            // from its head count, an unsettled brood from F/M, and the last one out ends the attempt.
            // This used to be a copy of the settled half only, so 120 matured-but-unsettled fish could
            // be netted out one by one while the sounder went on counting every one of them.
            com.riverfishing.fishing.FishingManager.broodAfterCatch(level, sp, pos, p.id);

            // POACHING: a net is legal only in water YOU stocked. A native species is in nobody's book
            // (owner null) — nobody stocked it, so nobody may net it. The haul still happens: this is
            // a simulator, and poaching working is what makes it wrong. The water pays twice.
            UUID owner = stocked.owner(region, p.id.getPath());
            boolean poachedFish = pondOwner != null ? !pondOwner.equals(sp.getUUID())   // §pond
                    : owner == null || !owner.equals(sp.getUUID());
            if (poachedFish) {
                pressure.addCatch(chunk, p.id.getPath(), now);
                poached++;
            }
            // §netted-card: a card, so the fish can be stocked and bred — and one that says it was
            // netted, and whether it was poached. It never loses that.
            // §founders: the same three answers the rod gives — an unsettled transplant is neither
            // native nor stocked, and the card used to call it native.
            String eco = com.riverfishing.fishing.FishingManager.nativeHere(level, pos, body, p.id) ? "native"
                    : stocked.isStocked(region, p.id.getPath()) ? "stocked" : "";
            int base = com.riverfishing.registry.ModVillagers.baseEmeralds(p.id.getPath());
            int value = base > 0 ? com.riverfishing.fishing.MarketData.get(level).price(level, p.id.getPath(), base) : 0;
            com.riverfishing.item.StackNbt.mutate(fish, t -> t.put(com.riverfishing.fish.CatchCard.TAG,
                    com.riverfishing.fish.CatchCard.netted(sp, level, p, weightG, pos, eco, value, poachedFish)));
            // §variety-icon: 26.x draws a fish from what its stack carries, and the koi's four tints and
            // the carp's variety drawing are read OFF the card — so the icon has to be stamped after it.
            // The rod does this at the end of its own catch; a net hauled fish out with no stamp at all.
            com.riverfishing.item.FishItem.stampIcon(fish);
            if (!sp.getInventory().add(fish)) sp.drop(fish, false);
        }

        if (poached > 0) {
            // Every fisherman within earshot "saw" it: the trust the contracts run on drops, once per
            // haul — a net is one act, however many fish came up in it.
            // §o: the reputation hit lives in Warden.onPoach now, beside the record it belongs to,
            // and it lost its clamp at zero. Reputation goes NEGATIVE: the board stops showing a
            // number and starts showing a debt, in kilograms of fish owed back to wild water.
            if (pondOwner != null) {   // §pond: name whose pond it was
                sp.sendSystemMessage(Component.translatable("message.riverfishing.pond_not_yours",
                        com.riverfishing.fishing.PondData.ownerName(level, pos)).withStyle(ChatFormatting.RED));
            }
            sp.sendSystemMessage(Component.translatable("message.riverfishing.poaching")
                    .withStyle(ChatFormatting.RED));
            // §i: the warden. In his reach the net is his and the fine is due; out of it, the record
            // still grows (fishing/Warden).
            com.riverfishing.fishing.Warden.onPoach(sp, level, pos, poached);
        }
        sp.sendOverlayMessage(Component.translatable("message.riverfishing.net_haul", count)
                .withStyle(ChatFormatting.GREEN));
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.net").withStyle(ChatFormatting.DARK_GRAY));
    }
}

// §ported26
