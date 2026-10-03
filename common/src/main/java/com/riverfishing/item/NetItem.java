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
            com.riverfishing.fishing.PondData.Claim claim0 = com.riverfishing.fishing.PondLife.claimNear(sl, water);
            java.util.UUID pondOwner0 = claim0 == null ? null : claim0.owner;
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
        long region = StockedData.regionAt(level, pos);
        long chunk = ChunkPos.pack(pos);
        long now = level.getGameTime();
        StockedData stocked = StockedData.get(level);
        FishingPressureData pressure = FishingPressureData.get(level);
        RandomSource rng = level.getRandom();
        // §pond: in a claimed pond the OWNER is legal for every species — his fish, his net. Anyone else
        // is poaching the lot, native book or no. Outside claimed water the stocking book rules as before.
        // §alife-pond: a net cast a step past the claimed columns is still in the pond — the claim is frozen at the
        // sign, and a pond dug wider used to answer as wild water: endless fresh fish of every native species.
        com.riverfishing.fishing.PondData.Claim claim = com.riverfishing.fishing.PondLife.claimNear(level, pos);
        UUID pondOwner = claim == null ? null : claim.owner;
        com.riverfishing.alife.Lake living = claim != null ? com.riverfishing.fishing.PondLife.lake(level, pos)
                : com.riverfishing.config.RiverFishingConfig.alife() ? com.riverfishing.fishing.AlifeData.get(level).lakeAt(level, pos) : null;
        if (living != null && !living.zones.isEmpty()) {
            haulLiving(sp, level, pos, living, claim, minFish + rng.nextInt(maxFish - minFish + 1));
            return;
        }

        List<FishProfile> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (FishProfile p : FishProfileManager.get().all()) {
            String id = p.id.getPath();
            if (stocked.isCulled(region, id)) continue;
            // §pond: nothing is resident in a claimed pond but what was put in — so the transplants still
            // dispersing there count too, or the net would come up empty the day after stocking.
            // §pond-book: in a claimed pond the book is the whole roster — the chunk bank reaches three
            // chunks around and read the sea's releases into a beluga pond; a pond settles its species
            // the day they go in, so nothing waits in the bank any more.
            if (pondOwner != null ? !stocked.pondHolds(region, id) : !FishingManager.residentHere(level, pos, body, p.id)) continue;   // §pond-empty
            // The community hash can call a shark native to a brook; the habitat score is what keeps
            // the bite engine honest about that, so the net asks it too.
            if (BiteEngine.environmentScore(p, FishingManager.habitatContext(level, pos, body)) <= 0) continue;
            int pct = pressure.stockPercent(chunk, id, now);
            // §ledger-presence: an unsettled brood is counted by its book, not by a weight bank that
            // eleven hauls empty — the same function the bite reads, so the net and the sounder agree.
            if (pct <= 0 && !stocked.isStocked(region, id)) {
                pct = (int) Math.round(100 * FishingManager.stockedPresence(level, pos).applyAsDouble(p.id));
            }
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

        int poached = 0, hauled = 0;
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            FishProfile p = pick(pool, weights, total, rng);
            // §pond-roster: out of a pond the net lifts one of the fish that went in, grown.
            CompoundTag rec = pondOwner != null ? stocked.peekFish(region, p.id.getPath(), rng) : null;
            // §pond-roster-net: the pool was priced once, before the loop, so a third fish was rolled
            // after the two that were put in had come up — a carp nobody released. In a pond a species
            // with no record left gives only what the head count says is UNRECORDED (fry that grew, the
            // seasons' growth); with none of that either it leaves the pool, and a pond with nothing
            // left ends the haul short.
            // §pond-old-ledger: guarded on AvgW like pondHolds — a ledger from before the head count has no
            // Adults to read, and its zero emptied an old pond that was working.
            if (pondOwner != null && rec == null && stocked.avgWeight(region, p.id.getPath()) > 0
                    && stocked.adults(region, p.id.getPath()) <= stocked.rememberedFish(region, p.id.getPath())) {
                int at = pool.indexOf(p);
                total -= weights.remove(at);
                pool.remove(at);
                i--;
                continue;
            }
            hauled++;
            final int weightG = rec != null ? stocked.grownWeight(level, region, p.id.getPath(), p, rec) : rollWeight(p, rng);
            // §net-grade: a trophy by its weight, as on the rod
            ItemStack fish = FishItem.create(ModItems.fishItem(p.id), p.id, weightG, lengthCm(p, weightG, rng), true,
                    weightG >= FishItem.trophyThresholdG(p.weightMin, p.weightMax));
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
            if (rec != null) {   // §pond-roster
                FishingManager.applyPondFish(fish, rec);
                stocked.takeFish(region, p.id.getPath(), rec.getLongOr("Uid", 0L));
            }
            if (!poachedFish) FishingManager.gradePrime(sp, fish, p.id, weightG);   // §net-grade: a poached fish is no sale
            // §variety-icon: 26.x draws a fish from what its stack carries, and the koi's four tints and
            // the carp's variety drawing are read OFF the card — so the icon has to be stamped after it.
            // The rod does this at the end of its own catch; a net hauled fish out with no stamp at all.
            com.riverfishing.item.FishItem.stampIcon(fish);
            if (!sp.getInventory().add(fish)) com.riverfishing.compat.Mc.drop(sp, fish, false);
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
        if (hauled == 0) {   // §pond-roster-net: the pond had nothing left to lift
            sp.sendOverlayMessage(Component.translatable("message.riverfishing.net_empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        sp.sendOverlayMessage(Component.translatable("message.riverfishing.net_haul", hauled)
                .withStyle(ChatFormatting.GREEN));
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    /**
     * §alife-pond: a haul out of a living pond — each fish one of the pond's own, taken out of it, every badge
     * and its card kept. Nothing is ever rolled here: an empty pond nets empty, and a netted-out pond stays so.
     */
    private void haulLiving(ServerPlayer sp, ServerLevel level, BlockPos pos, com.riverfishing.alife.Lake lake,
                            @org.jetbrains.annotations.Nullable com.riverfishing.fishing.PondData.Claim claim, int count) {
        RandomSource rng = level.getRandom();
        com.riverfishing.alife.Lake.Zone at = lake.zones.get(com.riverfishing.fishing.AlifeData.zoneAt(lake, pos));
        int hauled = 0, poached = 0;
        for (int i = 0; i < count; i++) {
            // a pond is small enough to sweep whole; in wild water the net reaches the shoals around it
            List<com.riverfishing.alife.Lake.Agent> near = new ArrayList<>();
            int total = 0;
            for (com.riverfishing.alife.Lake.Agent a : lake.agents) {
                if (a.fry || !a.alive() || (claim != null && a.heads == null)) continue;
                com.riverfishing.alife.Lake.Zone z = lake.zones.get(a.zone);
                if (claim == null && Math.hypot(z.x - at.x, z.z - at.z) > NET_REACH) continue;
                near.add(a);
                total += a.count;
            }
            if (total == 0) break;
            int roll = rng.nextInt(total);
            com.riverfishing.alife.Lake.Agent from = near.get(near.size() - 1);
            for (com.riverfishing.alife.Lake.Agent a : near) if ((roll -= a.count) < 0) { from = a; break; }
            FishProfile p = FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(from.sp.id()));
            if (p == null) continue;
            com.riverfishing.alife.Life.Head h = lake.pickHead(from);
            int w;
            boolean mine;
            if (h != null) {
                lake.takeHead(from, h);
                w = (int) Math.round(h.weightG);
                mine = claim != null ? claim.owner.equals(sp.getUUID()) : sp.getUUID().equals(com.riverfishing.fishing.PondLife.owner(h));
            } else {
                w = (int) Math.round(lake.take(from));   // a wild shoal's fish: nobody's, so nobody may net it
                mine = false;
            }
            final int weightG = Math.max(1, w);
            boolean poachedFish = !mine;
            boolean trophy = h != null ? h.trophy : weightG >= FishItem.trophyThresholdG(p.weightMin, p.weightMax);
            ItemStack fish = FishItem.create(ModItems.fishItem(p.id), p.id, weightG, lengthCm(p, weightG, rng), true, trophy);
            if (h != null && h.legend) com.riverfishing.item.StackNbt.mutate(fish, t -> t.putBoolean(FishItem.TAG_LEGEND, true));
            int base = com.riverfishing.registry.ModVillagers.baseEmeralds(p.id.getPath());
            int value = base > 0 ? com.riverfishing.fishing.MarketData.get(level).price(level, p.id.getPath(), base) : 0;
            String eco = h != null ? "stocked" : "native";
            com.riverfishing.item.StackNbt.mutate(fish, t -> t.put(com.riverfishing.fish.CatchCard.TAG,
                    com.riverfishing.fish.CatchCard.netted(sp, level, p, weightG, pos, eco, value, poachedFish)));
            if (h != null) FishingManager.applyPondFish(fish, com.riverfishing.fishing.PondLife.record(h));
            if (!poachedFish) FishingManager.gradePrime(sp, fish, p.id, weightG);   // §net-grade: as on the rod
            com.riverfishing.item.FishItem.stampIcon(fish);
            if (!sp.getInventory().add(fish)) com.riverfishing.compat.Mc.drop(sp, fish, false);
            hauled++;
            if (poachedFish) poached++;
        }
        if (hauled == 0) {
            sp.sendOverlayMessage(Component.translatable("message.riverfishing.net_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (poached > 0) {
            if (claim != null) sp.sendSystemMessage(Component.translatable("message.riverfishing.pond_not_yours", claim.ownerName)
                    .withStyle(ChatFormatting.RED));
            sp.sendSystemMessage(Component.translatable("message.riverfishing.poaching").withStyle(ChatFormatting.RED));
            com.riverfishing.fishing.Warden.onPoach(sp, level, pos, poached);
        }
        sp.sendOverlayMessage(Component.translatable("message.riverfishing.net_haul", hauled).withStyle(ChatFormatting.GREEN));
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    /** §alife-wild: how far along the water a net's sweep reaches for shoals, in blocks. */
    private static final double NET_REACH = 24;

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
        double length = p.lengthAt(weightG) * (0.98 + rng.nextDouble() * 0.04);   // §length-weight: as the rod's
        return (int) Math.max(1, Math.round(Math.min(length, p.lengthMax)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.net").withStyle(ChatFormatting.DARK_GRAY));
    }
}

// §ported26
