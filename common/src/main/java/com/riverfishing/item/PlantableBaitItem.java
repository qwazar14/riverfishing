package com.riverfishing.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.function.Supplier;

/**
 * §no-seeds: corn, peas and pearl barley are what you plant — there are no seed items. The bait stays a bait (every
 * {@code instanceof BaitItem} in the rig code still sees it) and on farmland it places its crop the way a seed does.
 */
public class PlantableBaitItem extends BaitItem {
    private final Supplier<Block> crop;

    public PlantableBaitItem(String baitId, Supplier<Block> crop, Properties properties) {
        super(baitId, false, properties);
        this.crop = crop;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        Level level = ctx.getLevel();
        BlockPos pos = place.getClickedPos();
        BlockState state = place.canPlace() ? crop.get().getStateForPlacement(place) : null;
        if (state == null || !state.canSurvive(level, pos)) return super.useOn(ctx);   // not on farmland: a bait
        if (!level.isClientSide()) {
            Player player = ctx.getPlayer();
            level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE);
            state.getBlock().setPlacedBy(level, pos, state, player, ctx.getItemInHand());
            SoundType sound = state.getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
            if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
