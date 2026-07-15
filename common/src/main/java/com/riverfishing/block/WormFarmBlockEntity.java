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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The worm farm's brood (§bait-farm): while there is compost in the crate (the block's LEVEL) and soil
 * below, the worms eat through it — every few minutes one compost level sinks away and turns into worms.
 * Right-click collects the worms. Composter-style: fill it up, wait, watch the heap shrink.
 */
public class WormFarmBlockEntity extends BlockEntity {
    private static final int WORMS_PER_LEVEL = 3;
    private static final int MAX_WORMS = 24;

    private int worms;
    private int progress;
    private int nextAt = -1;

    public WormFarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORM_FARM.get(), pos, state);
    }

    void serverTick(Level level, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        if (!server.getBlockState(worldPosition.below()).is(BlockTags.DIRT)) return; // needs soil below
        int compost = state.getValue(WormFarmBlock.LEVEL);
        if (compost <= 0 || worms >= MAX_WORMS) return;

        if (nextAt < 0) {
            nextAt = 2400 + server.getRandom().nextInt(2400); // 2–4 minutes per compost level
        }
        progress++;
        if (progress % 120 == 0) {
            server.sendParticles(ParticleTypes.COMPOSTER,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.7, worldPosition.getZ() + 0.5,
                    2, 0.3, 0.05, 0.3, 0.0);
        }
        if (progress >= nextAt) {
            progress = 0;
            nextAt = -1;
            worms = Math.min(MAX_WORMS, worms + WORMS_PER_LEVEL);
            // the worms ate a layer — the heap visibly sinks (§bait-farm)
            server.setBlock(worldPosition, state.setValue(WormFarmBlock.LEVEL, compost - 1), 3);
            setChanged();
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.75, worldPosition.getZ() + 0.5,
                    4, 0.3, 0.1, 0.3, 0.0);
        }
    }

    void collect(Player player) {
        if (worms <= 0) {
            player.displayClientMessage(Component.translatable("message.riverfishing.farm_empty")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        var worm = BuiltInRegistries.ITEM.get(com.riverfishing.RiverFishing.id("worm"));
        if (worm != null) {
            ItemStack out = new ItemStack(worm, worms);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        worms = 0;
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.7f, 1.1f);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Worms", worms);
        tag.putInt("Progress", progress);
        tag.putInt("NextAt", nextAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        worms = tag.contains("Worms") ? tag.getInt("Worms") : tag.getInt("Stored");
        progress = tag.getInt("Progress");
        nextAt = tag.contains("NextAt") ? tag.getInt("NextAt") : -1;
    }
}
