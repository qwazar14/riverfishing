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
import net.minecraft.world.InteractionResult;
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
 * Groundbait / прикормка (§3.5). Right-click while aiming at water to feed a 3x3 spot (§5).
 *
 * <p>§groundbait-one-jar: there is one of these, and there used to be four. The jar is only ever a
 * container now — what it does in the water is decided entirely by the mix stamped on the stack, so an
 * item class with a {@code category} field would be a second opinion about a question the mix already
 * answers. A plain jar off the shelf is the BASE: neutral, useful, and beaten by anything you mixed.
 */
public class GroundbaitItem extends Item {
    private static final double REACH = 32.0;

    public GroundbaitItem(Properties properties) {
        super(properties);
    }

    /**
     * §groundbait-tint: what the speckle layer of the icon is painted with.
     *
     * <p>Layer 1 of the model is the icon's pale speckles, drawn pure white so the tint MULTIPLIES to
     * exactly this colour (see {@code tools/gen_groundbait_tint.py}). The colour is the mix's own — the
     * spoon-weighted blend of everything in it, dye included — so a jar shows what is in it, and a dyed
     * jar shows the dye. A ready-made jar with no mix stamped on it gets its preset colour, which is
     * the colour its art is already drawn in, so nothing about the shop items changes.
     *
     * <p>Lives here rather than in each loader's colour handler because there are six of those across
     * three trees, and one of them getting a different answer is how these things rot.
     */
    public static int speckleTint(ItemStack stack, int tintIndex) {
        return tintIndex == 1
                ? 0xFF000000 | com.riverfishing.groundbait.GroundbaitNbt.read(stack).rgb()
                : -1;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = sp.level();
        HitResult hit = sp.pick(REACH, 1.0f, true);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!WaterBodyDetector.isWater(serverLevel, pos)) {
            return InteractionResult.FAIL;
        }

        com.riverfishing.groundbait.GroundbaitMix mix =
                com.riverfishing.groundbait.GroundbaitNbt.read(stack);

        // §last-thrown-wins: read what is down there BEFORE the throw. If it is a different recipe, this
        // jar takes the swim over rather than adding to it, and that is worth saying at the moment it
        // happens — afterwards there is nothing left to compare against.
        FeedZoneData.Query before = FeedZoneData.get(serverLevel).query(pos, serverLevel.getGameTime());
        boolean overwrote = before.inZone() && before.mix() != null
                && !before.mix().signature().equals(mix.signature());
        FeedZoneData.get(serverLevel).feed(pos, mix, serverLevel.getGameTime());
        stack.shrink(1);

        serverLevel.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7f, 1.1f);
        serverLevel.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                16, 0.4, 0.1, 0.4, 0.1);
        // §groundbait-particles: the cloud is the colour of the mix, so a bucket of your own blend looks
        // like itself in the water and two different spots are told apart at a glance.
        serverLevel.sendParticles(FeedZoneData.particleFor(mix.rgb()), pos.getX() + 0.5, pos.getY() + 1.05,
                pos.getZ() + 0.5, 20, 0.4, 0.06, 0.4, 0.0);

        // You cannot overfeed a swim (§no-overfeeding), so the only thing worth warning about is throwing
        // a DIFFERENT recipe on top of one that was working: the old bed does not add to the new one, it
        // is simply gone. Yellow rather than red — it is a thing to know, not a mistake to punish.
        sp.sendOverlayMessage(Component.translatable(overwrote
                        ? "message.riverfishing.spot_replaced" : "message.riverfishing.fed_spot")
                .withStyle(overwrote ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.groundbait_use").withStyle(s -> s.withColor(0x80A080)));

        // §groundbait-mix: the number AND the word. The figure alone teaches nothing the first time and
        // the word alone is never precise enough to compare two jars.
        com.riverfishing.groundbait.GroundbaitMix mix =
                com.riverfishing.groundbait.GroundbaitNbt.read(stack);
        tooltip.accept(Component.translatable("tooltip.riverfishing.gb_nutrition",
                        String.format(java.util.Locale.ROOT, "%.2f", mix.nutrition()),
                        Component.translatable("tooltip.riverfishing.gb_rich_" + richWord(mix.nutrition())))
                .withStyle(s -> s.withColor(0xC8A050)));
        tooltip.accept(Component.translatable("tooltip.riverfishing.gb_fraction",
                        String.format(java.util.Locale.ROOT, "%.2f", mix.fraction()),
                        Component.translatable("tooltip.riverfishing.gb_coarse_" + coarseWord(mix.fraction())))
                .withStyle(s -> s.withColor(0x8FB0C8)));
        // §groundbait-tint: the colour, named by the same classifier a painted lure goes through, and
        // drawn IN that colour. It claims nothing about fishing on purpose — the bite engine does not
        // read groundbait colour, and this line must not imply that it does.
        tooltip.accept(Component.translatable("tooltip.riverfishing.gb_color",
                        Component.translatable("tooltip.riverfishing.gb_color_"
                                + com.riverfishing.engine.LureColor.fromRgb(mix.rgb()).name().toLowerCase()))
                .withStyle(s -> s.withColor(mix.rgb())));
        if (!mix.isBase()) {
            for (com.riverfishing.groundbait.GroundbaitMix.Part p : mix.parts()) {
                tooltip.accept(Component.literal("  " + p.spoons() + " x ")
                        .append(Component.translatable(partKey(p.id())))
                        .withStyle(s -> s.withColor(0x7F8C99)));
            }
        }
    }

    /** Coarse words for the three bands a player actually distinguishes. */
    private static String richWord(double nutrition) {
        return nutrition >= 0.70 ? "high" : nutrition >= 0.35 ? "mid" : "low";
    }

    private static String coarseWord(double fraction) {
        return fraction >= 0.65 ? "coarse" : fraction >= 0.30 ? "mid" : "fine";
    }

    /** A pantry id is either one of ours or a vanilla item; both name themselves the same way. */
    private static String partKey(String id) {
        return id.startsWith("minecraft:")
                ? "item.minecraft." + id.substring("minecraft:".length())
                : "item.riverfishing." + id;
    }
}
