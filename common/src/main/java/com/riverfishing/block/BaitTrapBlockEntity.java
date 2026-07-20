package com.riverfishing.block;

import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Gathers live bait over time while standing in water (§livebait): fry swim into the net every few
 * minutes, up to a small stack. Right-click collects everything.
 */
public class BaitTrapBlockEntity extends BlockEntity {
    private static final int MAX_STORED = 12;
    private static final int MAX_FISH = 4;
    /** §trap-fish: a catch event lands a real SMALL fish (≤150 g) instead of fry this often. */
    private static final double FISH_CHANCE = 0.35;

    private int stored;
    private int progress;
    private int nextAt = -1;
    /** §trap-feed: groundbait charges — while any remain the clock runs at double speed. */
    private int feedCharges;
    private final java.util.List<ItemStack> fishes = new java.util.ArrayList<>();

    /* jade (0.4.0): gathered livebait count for the look-at tooltip. */
    public int storedCount() { return stored; }

    public BaitTrapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BAIT_TRAP.get(), pos, state);
    }

    void serverTick(Level level) {
        if (!(level instanceof ServerLevel server)) return;
        if (!inWater(server)) return;

        if (nextAt < 0) {
            nextAt = 2400 + server.getRandom().nextInt(2400); // 2–4 minutes per fry
        }
        if (stored >= MAX_STORED && fishes.size() >= MAX_FISH) return;

        // §trap-feed: a fed trap pulls fish in twice as fast, one charge per catch.
        progress += feedCharges > 0 ? 2 : 1;
        if (progress % 100 == 0) {
            server.sendParticles(ParticleTypes.BUBBLE,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5,
                    3, 0.25, 0.2, 0.25, 0.02);
        }
        if (progress >= nextAt) {
            progress = 0;
            nextAt = -1;
            if (feedCharges > 0) feedCharges--;
            // §trap-fish: sometimes the net holds a real small fish of THIS water's community —
            // whatever actually lives within reach of the trap, up to 150 g.
            ItemStack small = server.getRandom().nextDouble() < FISH_CHANCE && fishes.size() < MAX_FISH
                    ? rollSmallFish(server) : ItemStack.EMPTY;
            if (!small.isEmpty()) {
                fishes.add(small);
            } else if (stored < MAX_STORED) {
                stored++;
            }
            setChanged();
            server.sendParticles(ParticleTypes.SPLASH,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5,
                    8, 0.25, 0.1, 0.25, 0.15);
        }
    }

    /** A random ≤150 g specimen from the species that actually live in the water at the trap. */
    private ItemStack rollSmallFish(ServerLevel server) {
        BlockPos waterPos = waterAt(server);
        if (waterPos == null) return ItemStack.EMPTY;
        var body = com.riverfishing.water.WaterBodyCache.forLevel(server).get(server, waterPos);
        if (body.type() == com.riverfishing.water.WaterType.NONE) return ItemStack.EMPTY;
        var env = com.riverfishing.fishing.FishingManager.environmentAt(server, waterPos, body);
        java.util.List<com.riverfishing.fish.FishProfile> pool = new java.util.ArrayList<>();
        java.util.List<Double> weights = new java.util.ArrayList<>();
        double total = 0;
        for (var p : com.riverfishing.fish.FishProfileManager.get().all()) {
            if (p.weightMin > 150 || p.base <= 0) continue;
            double e = com.riverfishing.engine.BiteEngine.environmentScore(p, env);
            if (e <= 1e-4) continue;
            pool.add(p);
            weights.add(e * p.base);
            total += e * p.base;
        }
        if (pool.isEmpty()) return ItemStack.EMPTY;
        double roll = server.getRandom().nextDouble() * total;
        com.riverfishing.fish.FishProfile pick = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) { pick = pool.get(i); break; }
        }
        double maxW = Math.min(150, pick.weightMax);
        int w = (int) (pick.weightMin + server.getRandom().nextDouble() * Math.max(1, maxW - pick.weightMin));
        double lf = (w - pick.weightMin) / Math.max(1.0, pick.weightMax - pick.weightMin);
        int len = (int) Math.round(pick.lengthMin + (pick.lengthMax - pick.lengthMin) * lf);
        var item = com.riverfishing.registry.ModItems.FISH_ITEMS.get(pick.id);
        if (item == null) return ItemStack.EMPTY;
        return com.riverfishing.item.FishItem.create(item.get(), pick.id, w, Math.max(1, len), true);
    }

    private BlockPos waterAt(ServerLevel level) {
        BlockState state = getBlockState();
        if (state.hasProperty(BaitTrapBlock.WATERLOGGED) && state.getValue(BaitTrapBlock.WATERLOGGED)) {
            return worldPosition;
        }
        for (BlockPos p : new BlockPos[]{worldPosition.below(), worldPosition.north(), worldPosition.south(),
                worldPosition.east(), worldPosition.west()}) {
            if (level.getFluidState(p).is(FluidTags.WATER)) return p;
        }
        return null;
    }

    /** §trap-feed: pour a groundbait in — +4 double-speed charges (cap 12). */
    void addFeed(Player player, ItemStack held) {
        if (feedCharges >= 12) {
            player.displayClientMessage(Component.translatable("message.riverfishing.trap_fed_full")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        held.shrink(1);
        feedCharges = Math.min(12, feedCharges + 4);
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        player.displayClientMessage(Component.translatable("message.riverfishing.trap_fed")
                .withStyle(ChatFormatting.AQUA), true);
    }

    private boolean inWater(ServerLevel level) {
        // The waterlogged net itself counts — plus any adjacent water for a trap on the bank's edge.
        BlockState state = getBlockState();
        if (state.hasProperty(BaitTrapBlock.WATERLOGGED) && state.getValue(BaitTrapBlock.WATERLOGGED)) {
            return true;
        }
        BlockPos p = worldPosition;
        return level.getFluidState(p.below()).is(FluidTags.WATER)
                || level.getFluidState(p.north()).is(FluidTags.WATER)
                || level.getFluidState(p.south()).is(FluidTags.WATER)
                || level.getFluidState(p.east()).is(FluidTags.WATER)
                || level.getFluidState(p.west()).is(FluidTags.WATER);
    }

    void collect(Player player) {
        if (stored <= 0 && fishes.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.riverfishing.trap_empty")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        var livebait = BuiltInRegistries.ITEM.get(com.riverfishing.RiverFishing.id("livebait"));
        if (livebait != null && stored > 0) {
            ItemStack out = new ItemStack(livebait, stored);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        for (ItemStack f : fishes) {
            if (!player.getInventory().add(f)) player.drop(f, false);
        }
        fishes.clear();
        stored = 0;
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 0.8f, 1.1f);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Stored", stored);
        tag.putInt("Progress", progress);
        tag.putInt("NextAt", nextAt);
        tag.putInt("Feed", feedCharges);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ItemStack f : fishes) list.add(f.save(registries, new CompoundTag()));
        tag.put("Fishes", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getInt("Stored");
        progress = tag.getInt("Progress");
        nextAt = tag.contains("NextAt") ? tag.getInt("NextAt") : -1;
        feedCharges = tag.getInt("Feed");
        fishes.clear();
        net.minecraft.nbt.ListTag list = tag.getList("Fishes", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack f = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!f.isEmpty()) fishes.add(f);
        }
    }
}
