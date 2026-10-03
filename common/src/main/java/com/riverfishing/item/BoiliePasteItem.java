package com.riverfishing.item;

import com.riverfishing.fish.Boilie;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * §boilies: boilie paste — base, egg, flavours, floater, rolled — and it becomes boilies the way boilies are
 * made: boiled. Right-click a cauldron of water with a lit campfire under it and the whole stack comes out as
 * boilies, four to a ball of paste, carrying every property the paste had.
 */
public class BoiliePasteItem extends Item implements Tinted {
    /** Boilies one ball of paste makes. */
    public static final int PER_PASTE = 4;

    public BoiliePasteItem(Properties properties) {
        super(properties);
    }

    public static Boilie read(ItemStack stack) {
        net.minecraft.nbt.CompoundTag t = StackNbt.get(stack).getCompoundOrEmpty(BoilieItem.TAG);
        return t.isEmpty() ? Boilie.PLAIN : BoilieItem.fromTag(t, false);
    }

    public static void write(ItemStack stack, Boilie b) {
        StackNbt.mutate(stack, tag -> tag.put(BoilieItem.TAG, BoilieItem.toTag(b)));
        if (stack.getItem() instanceof Tinted t) t.stampTint(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState cauldron = level.getBlockState(pos);
        if (!cauldron.is(Blocks.WATER_CAULDRON)) return InteractionResult.PASS;
        BlockState under = level.getBlockState(pos.below());
        boolean fire = under.is(BlockTags.CAMPFIRES) && under.hasProperty(CampfireBlock.LIT) && under.getValue(CampfireBlock.LIT);
        Player player = ctx.getPlayer();
        if (!fire) {
            if (!level.isClientSide() && player != null) {
                player.sendOverlayMessage(Component.translatable("message.riverfishing.boil_needs_fire").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack paste = ctx.getItemInHand();
        Boilie b = read(paste);
        int made = paste.getCount() * PER_PASTE;
        paste.shrink(paste.getCount());
        for (int left = made; left > 0; left -= 64) {
            ItemStack out = new ItemStack(com.riverfishing.registry.ModItems.BOILIE.get(), Math.min(64, left));
            BoilieItem.write(out, b);
            if (player == null || !player.getInventory().add(out)) {
                net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, out);
                level.addFreshEntity(e);
            }
        }
        ServerLevel sl = (ServerLevel) level;
        sl.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.25, 0.15, 0.25, 0.02);
        sl.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 16, 0.25, 0.05, 0.25, 0.0);
        sl.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.8f, 1.0f);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.riverfishing.boiled", made).withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int tint(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return -1;
        Boilie b = read(stack);
        return b.flavours().isEmpty() ? 0xFFE0C8A0 : 0xFF000000 | b.flavours().get(0).rgb;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        BoilieItem.describe(read(stack), tooltip);
        tooltip.accept(Component.translatable("tooltip.riverfishing.paste_boil").withStyle(ChatFormatting.DARK_GRAY));
    }
}
