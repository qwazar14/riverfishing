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

    private int stored;
    private int progress;
    private int nextAt = -1;

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
        if (stored >= MAX_STORED) return;

        progress++;
        if (progress % 100 == 0) {
            server.sendParticles(ParticleTypes.BUBBLE,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5,
                    3, 0.25, 0.2, 0.25, 0.02);
        }
        if (progress >= nextAt) {
            progress = 0;
            nextAt = -1;
            stored++;
            setChanged();
            server.sendParticles(ParticleTypes.SPLASH,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5,
                    8, 0.25, 0.1, 0.25, 0.15);
        }
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
        if (stored <= 0) {
            player.displayClientMessage(Component.translatable("message.riverfishing.trap_empty")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        var livebait = BuiltInRegistries.ITEM.get(com.riverfishing.RiverFishing.id("livebait"));
        if (livebait != null) {
            ItemStack out = new ItemStack(livebait, stored);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getInt("Stored");
        progress = tag.getInt("Progress");
        nextAt = tag.contains("NextAt") ? tag.getInt("NextAt") : -1;
    }
}
