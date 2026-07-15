package com.riverfishing.item;

import com.riverfishing.fishing.FeedZoneData;
import com.riverfishing.water.WaterBodyDetector;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Groundbait / прикормка (§3.5). Right-click while aiming at water to feed a 3x3 spot (§5);
 * the category decides which fish the fed spot pulls in.
 */
public class GroundbaitItem extends Item {
    private static final double REACH = 32.0;

    private final String category; // powder / grain / pellet / cake

    public GroundbaitItem(String category, Properties properties) {
        super(properties);
        this.category = category;
    }

    public String category() { return category; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        ServerLevel serverLevel = sp.serverLevel();
        HitResult hit = sp.pick(REACH, 1.0f, true);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.fail(stack);
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!WaterBodyDetector.isWater(serverLevel, pos)) {
            return InteractionResultHolder.fail(stack);
        }

        FeedZoneData.get(serverLevel).feed(pos, category, serverLevel.getGameTime());
        stack.shrink(1);

        serverLevel.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7f, 1.1f);
        serverLevel.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                16, 0.4, 0.1, 0.4, 0.1);
        // §groundbait-particles: a coloured cloud of THIS groundbait so different feeds look different.
        serverLevel.sendParticles(FeedZoneData.particleFor(category), pos.getX() + 0.5, pos.getY() + 1.05,
                pos.getZ() + 0.5, 20, 0.4, 0.06, 0.4, 0.0);
        sp.displayClientMessage(Component.translatable("message.riverfishing.fed_spot").withStyle(ChatFormatting.GREEN), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.groundbait_use").withStyle(s -> s.withColor(0x80A080)));
    }
}
