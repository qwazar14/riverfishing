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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The maggot farm's brood (§bait-farm), composter-style: rotten flesh goes in a piece per click (16 max,
 * 4 pieces per visible layer), then roughly every minute one piece hatches into 4 maggots and the heap
 * visibly sinks. Right-click with anything but flesh collects the maggots.
 */
public class MaggotFarmBlockEntity extends BlockEntity {
    private static final int MAX_FLESH = 16;
    private static final int MAGGOTS_PER_FLESH = 4;
    private static final int MAX_MAGGOTS = 64;

    private int flesh;
    private int maggots;
    private int progress;
    private int nextAt = -1;

    public MaggotFarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGGOT_FARM.get(), pos, state);
    }

    void serverTick(Level level) {
        if (!(level instanceof ServerLevel server)) return;
        if (flesh <= 0 || maggots > MAX_MAGGOTS - MAGGOTS_PER_FLESH) return;

        if (nextAt < 0) {
            nextAt = 900 + server.getRandom().nextInt(900); // ~1 minute per piece of flesh
        }
        progress++;
        if (progress % 140 == 0) {
            server.sendParticles(ParticleTypes.MYCELIUM,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.7, worldPosition.getZ() + 0.5,
                    3, 0.3, 0.05, 0.3, 0.0);
        }
        if (progress >= nextAt) {
            progress = 0;
            nextAt = -1;
            flesh--;
            maggots = Math.min(MAX_MAGGOTS, maggots + MAGGOTS_PER_FLESH);
            updateLevel(server);
            setChanged();
        }
    }

    /** One piece of flesh per click (§bait-farm, composter-style); the heap rises a layer every 4. */
    void depositOne(Player player, ItemStack held) {
        if (flesh >= MAX_FLESH) {
            player.sendOverlayMessage(Component.translatable("message.riverfishing.maggot_farm_full")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        flesh++;
        setChanged();
        if (level != null) {
            updateLevel(level);
            level.playSound(null, worldPosition, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.8f, 0.8f);
        }
        player.sendOverlayMessage(Component.translatable("message.riverfishing.maggot_farm_fill",
                flesh + "/" + MAX_FLESH).withStyle(ChatFormatting.GREEN));
    }

    /** Keep the block's visible fill (LEVEL 0..4) in step with the flesh count (4 pieces per layer). */
    private void updateLevel(Level level) {
        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(MaggotFarmBlock.LEVEL)) return;
        int lvl = Mth.clamp((int) Math.ceil(flesh / 4.0), 0, 4);
        if (state.getValue(MaggotFarmBlock.LEVEL) != lvl) {
            level.setBlock(worldPosition, state.setValue(MaggotFarmBlock.LEVEL, lvl), 3);
        }
    }

    void collect(Player player) {
        if (maggots <= 0) {
            player.sendOverlayMessage(Component.translatable("message.riverfishing.maggot_farm_empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        var maggot = BuiltInRegistries.ITEM.getValue(com.riverfishing.RiverFishing.id("maggot"));
        if (maggot != null) {
            ItemStack out = new ItemStack(maggot, maggots);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        maggots = 0;
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.7f, 1.2f);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putInt("Flesh", flesh);
        tag.putInt("Maggots", maggots);
        tag.putInt("Progress", progress);
        tag.putInt("NextAt", nextAt);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        flesh = Math.min(MAX_FLESH, tag.getIntOr("Flesh", 0));
        maggots = tag.getIntOr("Maggots", 0);
        progress = tag.getIntOr("Progress", 0);
        nextAt = tag.getInt("NextAt").orElse(-1);
    }
}
