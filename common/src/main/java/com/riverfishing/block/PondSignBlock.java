package com.riverfishing.block;

import com.riverfishing.fishing.PondData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * §pond §breeding (0.9.0): the private-pond sign. Plant it within three blocks of water and that
 * water body is yours ({@link PondData}); pull it out and the water is wild again. The block itself is
 * furniture — the claim lives in the SavedData, this class only translates place/break into it and
 * tells the player why a claim was refused. A refused sign pops back off, so a standing sign always
 * means a standing claim.
 */
public class PondSignBlock extends Block {
    /** §pond-sign: which way the board looks — set from whoever planted it, like the rest of the furniture. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    /** The post, and the board across it: the hitbox is the sign and not the air it stands in. */
    private static final VoxelShape POST = Block.box(6, 0, 6, 10, 7, 10);
    private static final VoxelShape NORTH_SOUTH = Shapes.or(POST, Block.box(1, 6, 6, 15, 15, 9));
    private static final VoxelShape EAST_WEST = Shapes.or(POST, Block.box(6, 6, 1, 9, 15, 15));

    public PondSignBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!(level instanceof ServerLevel sl) || !(placer instanceof ServerPlayer sp)) return;
        List<Long> body = PondData.flood(sl, pos);
        String refuse = null;
        Object arg = null;
        if (body == null) {
            refuse = "message.riverfishing.no_water";
        } else if (body.size() > PondData.MAX_BLOCKS) {
            refuse = "message.riverfishing.pond_too_big";
        } else {
            BlockPos water = PondData.columnPos(body.get(0));
            UUID owner = PondData.owner(sl, water);
            if (owner != null && !owner.equals(sp.getUUID())) {   // somebody else's sign already stands here
                refuse = "message.riverfishing.pond_not_yours";
                arg = PondData.ownerName(sl, water);
            }
        }
        if (refuse != null) {
            sp.sendOverlayMessage((arg == null ? Component.translatable(refuse) : Component.translatable(refuse, arg))
                    .withStyle(ChatFormatting.RED));
            sl.destroyBlock(pos, true);   // hands the sign back; no sign, no claim
            return;
        }
        PondData.get(sl).put(pos, sp, body);
        sp.sendOverlayMessage(Component.translatable("message.riverfishing.pond_claimed", body.size())
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * §pond-name: right-click with a named name tag and the pond takes that name — the owner's hand
     * only, the tag is spent like on a mob. Any other click reads the sign: the name, whose it is, how
     * much water, what is built on it, and who lives in it — a farmer looks at his pond, he does not
     * open a menu on it.
     */
    @Override
    protected net.minecraft.world.InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                                  Player player, net.minecraft.world.InteractionHand hand,
                                                                  net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.is(net.minecraft.world.item.Items.NAME_TAG) || !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        PondData.Claim c = PondData.get(sl).bySign(pos);
        if (c == null) return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!c.owner.equals(sp.getUUID())) {
            sp.sendOverlayMessage(Component.translatable("message.riverfishing.pond_name_not_owner", c.ownerName).withStyle(ChatFormatting.RED));
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        String name = stack.getHoverName().getString().trim();
        if (name.isEmpty()) return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        PondData.get(sl).rename(pos, name);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        sp.sendOverlayMessage(Component.translatable("message.riverfishing.pond_named", name).withStyle(ChatFormatting.GREEN));
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return net.minecraft.world.InteractionResult.PASS;
        PondData.Claim c = PondData.get(sl).bySign(pos);
        if (c == null) return net.minecraft.world.InteractionResult.PASS;
        for (Component line : describe(sl, c)) sp.sendSystemMessage(line);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** What the sign says when read: the head line, the modules, the population. */
    static List<Component> describe(ServerLevel sl, PondData.Claim c) {
        List<Component> out = new java.util.ArrayList<>();
        net.minecraft.network.chat.MutableComponent head = c.name.isEmpty()
                ? Component.translatable("message.riverfishing.pond_info_unnamed", c.ownerName, c.size())
                : Component.translatable("message.riverfishing.pond_info_head", c.name, c.ownerName, c.size());
        out.add(head.withStyle(ChatFormatting.GOLD));
        List<Component> modules = com.riverfishing.fishing.WaterUpgrades.inside(sl, packed -> c.holds(PondData.column(BlockPos.of(packed))));
        if (modules.isEmpty()) {
            out.add(Component.translatable("message.riverfishing.pond_info_no_modules").withStyle(ChatFormatting.GRAY));
        } else {
            net.minecraft.network.chat.MutableComponent list = Component.empty();
            for (int i = 0; i < modules.size(); i++) {
                if (i > 0) list.append(", ");
                list.append(modules.get(i));
            }
            out.add(Component.translatable("message.riverfishing.pond_info_modules", list).withStyle(ChatFormatting.AQUA));
        }
        // the ledger is per ~128-block region; a pond may straddle two, so every region the water
        // touches is read and the same species is summed across them
        java.util.Set<Long> regions = new java.util.LinkedHashSet<>();
        for (int i = 0; i < c.size(); i++) regions.add(com.riverfishing.fishing.StockedData.region(PondData.columnPos(c.water[i])));
        com.riverfishing.fishing.StockedData stocked = com.riverfishing.fishing.StockedData.get(sl);
        java.util.Map<String, int[]> fish = new java.util.TreeMap<>();
        for (long r : regions) {
            for (String sp : stocked.farmSpecies(r)) {
                int[] n = fish.computeIfAbsent(sp, k -> new int[2]);
                n[0] += stocked.adults(r, sp);
                n[1] += stocked.fryCount(r, sp);
            }
        }
        fish.values().removeIf(n -> n[0] + n[1] == 0);
        if (fish.isEmpty()) {
            out.add(Component.translatable("message.riverfishing.pond_info_empty").withStyle(ChatFormatting.GRAY));
        } else {
            for (var e : fish.entrySet()) {
                out.add(Component.translatable("message.riverfishing.pond_info_fish",
                        Component.translatable("fish.riverfishing." + e.getKey()), e.getValue()[0], e.getValue()[1])
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        return out;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // The message belongs to the player who pulled the sign; the release itself is in
        // affectNeighborsAfterRemoval so a piston or a creeper frees the water too.
        if (level instanceof ServerLevel sl && PondData.get(sl).remove(pos)) {
            player.sendOverlayMessage(Component.translatable("message.riverfishing.pond_released")
                    .withStyle(ChatFormatting.GRAY));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ponytail: 26.x has no Block.appendHoverText hook any more — the line is kept for the day it comes back through the item.
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.pond_sign").withStyle(ChatFormatting.DARK_GRAY));
    }

    // §26.1: onRemove is gone — every way the sign leaves the world lands here.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        PondData.get(level).remove(pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }
}
