package com.riverfishing.item;

import com.riverfishing.fishing.FishingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Water analysis tools (§QoL): the player-facing fish finder lists the species able to bite here and
 * now; the admin probe dumps the full environmental summary with per-species scores and gate reasons.
 * Works both when the crosshair is on water and when it lands on the bank/bottom next to it, and
 * always gives feedback (never silently does nothing).
 */
public class WaterProbeItem extends Item {
    private final boolean admin;

    public WaterProbeItem(boolean admin, Properties properties) {
        super(properties);
        this.admin = admin;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean handled = scan(level, player);
        return handled
                ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
                : InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();

        // Admin probe on a Fishing Stall reports the POI record (villager job-site diagnostics).
        if (admin && level.getBlockState(ctx.getClickedPos())
                .is(com.riverfishing.registry.ModBlocks.FISHING_STALL.get())) {
            if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
                var poi = sl.getPoiManager().getType(ctx.getClickedPos());
                boolean mapped = net.minecraft.world.entity.ai.village.poi.PoiTypes
                        .forState(sl.getBlockState(ctx.getClickedPos())).isPresent();
                sp.displayClientMessage(Component.literal(
                        "POI record at stall: "
                        + (poi.isPresent() ? poi.get().unwrapKey().map(Object::toString).orElse("?") : "NONE")
                        + " | state mapped: " + mapped
                        + (poi.isEmpty() ? " -> break & re-place the stall" : ""))
                        .withStyle(poi.isPresent() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Clicking the bank or the bottom under water still scans — the water is along the same ray.
        return scan(level, player)
                ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    /** Finds water along the player's view (24 blocks, any fluid shape) and runs the analysis. */
    private boolean scan(Level level, Player player) {
        BlockPos waterPos = findWater(level, player);
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            com.riverfishing.RiverFishing.LOGGER.info("[RiverFishing] probe scan by {}: admin={}, water={}",
                    sp.getGameProfile().getName(), admin, waterPos);
            if (waterPos == null) {
                // Chat (not action bar) so the feedback can never be missed.
                sp.displayClientMessage(Component.translatable("message.riverfishing.no_water")
                        .withStyle(ChatFormatting.RED), false);
            } else {
                FishingManager.analyzeWater(sp, sl, waterPos, admin);
                player.getCooldowns().addCooldown(this, 10);
            }
        }
        return waterPos != null;
    }

    @Nullable
    private static BlockPos findWater(Level level, Player player) {
        var eye = player.getEyePosition();
        var end = eye.add(player.getLookAngle().scale(24));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (level.getFluidState(pos).is(FluidTags.WATER)) return pos;
        // Crosshair on the bank/bottom: try just past the clicked face (the bucket pattern).
        BlockPos beyond = pos.relative(hit.getDirection());
        if (level.getFluidState(beyond).is(FluidTags.WATER)) return beyond;
        // Last resort: the column right below the hit (clicked the bottom through shallow water).
        BlockPos above = pos.above();
        if (level.getFluidState(above).is(FluidTags.WATER)) return above;
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(admin
                ? "tooltip.riverfishing.hydro_probe" : "tooltip.riverfishing.fish_finder")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
