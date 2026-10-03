package com.riverfishing.item;

import com.riverfishing.fish.Boilie;
import com.riverfishing.fish.Flavour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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

import java.util.ArrayList;
import java.util.List;

/**
 * §boilies: the boilie — one item, the id every fish profile already names, and what it IS lives in its data:
 * up to two flavours, how it floats, its size, fish meal in the base, and a dip. A boilie with no data is the
 * plain one it always was, so every boilie in every chest and every profile keeps working.
 *
 * <p>Right-clicked at water (not on a rod), it throws a handful in — prebaiting. The fish that eat it learn
 * its flavours, which is what a baiting campaign is for.
 */
public class BoilieItem extends BaitItem implements Tinted {
    public static final String TAG = "Boilie";
    /** Casts a dip lasts before it has washed out. */
    public static final int DIP_CASTS = 8;
    /** How many boilies one throw puts in, and what they weigh (kg) as feed. */
    private static final int HANDFUL = 10;
    private static final double BOILIE_KG = 0.01;
    private static final double REACH = 24.0;

    public BoilieItem(Properties properties) {
        super("boilie", false, properties);
    }

    // ---- data ----

    public static Boilie read(ItemStack stack) {
        CompoundTag t = StackNbt.get(stack).getCompound(TAG);
        if (t.isEmpty()) return Boilie.PLAIN;
        return fromTag(t, t.getInt("DL") > 0);
    }

    static Boilie fromTag(CompoundTag t, boolean withDip) {
        List<Flavour> fl = new ArrayList<>();
        for (String id : t.getString("F").split(",")) {
            Flavour f = Flavour.of(id);
            if (f != null && !fl.contains(f)) fl.add(f);
        }
        Boilie.Buoyancy by;
        try { by = Boilie.Buoyancy.valueOf(t.getString("B")); } catch (IllegalArgumentException e) { by = Boilie.Buoyancy.SINKER; }
        int size = t.contains("S") ? t.getInt("S") : 20;
        Flavour dip = withDip ? Flavour.of(t.getString("D")) : null;
        return new Boilie(fl, by, size, t.getBoolean("M"), dip);
    }

    static CompoundTag toTag(Boilie b) {
        CompoundTag t = new CompoundTag();
        StringBuilder f = new StringBuilder();
        for (Flavour x : b.flavours()) f.append(f.length() == 0 ? "" : ",").append(x.id());
        t.putString("F", f.toString());
        t.putString("B", b.buoyancy().name());
        t.putInt("S", b.sizeMm());
        if (b.meal()) t.putBoolean("M", true);
        return t;
    }

    public static void write(ItemStack stack, Boilie b) {
        StackNbt.mutate(stack, tag -> tag.put(TAG, toTag(b)));
    }

    /** §boilies: soak a whole stack in a dip — it lasts {@link #DIP_CASTS} casts. */
    public static void dip(ItemStack stack, Flavour flavour) {
        StackNbt.mutate(stack, tag -> {
            CompoundTag t = tag.contains(TAG) ? tag.getCompound(TAG) : toTag(Boilie.PLAIN);
            t.putString("D", flavour.id());
            t.putInt("DL", DIP_CASTS);
            tag.put(TAG, t);
        });
    }

    /** A cast: the dip washes out a little. True if the stack changed. */
    public static boolean useDip(ItemStack stack) {
        CompoundTag t = StackNbt.get(stack).getCompound(TAG);
        int left = t.getInt("DL");
        if (left <= 0) return false;
        StackNbt.mutate(stack, tag -> {
            CompoundTag c = tag.getCompound(TAG);
            if (left - 1 <= 0) { c.remove("DL"); c.remove("D"); } else c.putInt("DL", left - 1);
            tag.put(TAG, c);
        });
        return true;
    }

    // ---- look ----

    /**
     * §boilie-look: the icon is a little pile of balls, each split down the middle — tint 0 paints the left halves
     * with the first flavour, tint 1 the right with the second (or the first again), and a dipped boilie gets a
     * third, dripping layer in the dip's colour (models/item/boilie.json switches to boilie_dipped on the
     * riverfishing:dipped item property, registered in ClientInit).
     */
    @Override
    public int tint(ItemStack stack, int tintIndex) {
        Boilie b = read(stack);
        if (tintIndex == 2) return b.dip() == null ? -1 : 0xFF000000 | b.dip().rgb;
        if (tintIndex > 2) return -1;
        if (b.flavours().isEmpty()) return 0xFFC89A6A;   // the plain boilie's own tan
        int rgb = b.flavours().get(tintIndex == 1 && b.flavours().size() > 1 ? 1 : 0).rgb;
        // a pop-up is a fluo ball: its colour pushed toward white-hot
        if (b.buoyancy() == Boilie.Buoyancy.POPUP) rgb = brighten(rgb, 0.35);
        return 0xFF000000 | rgb;
    }

    static int brighten(int rgb, double k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        r = (int) (r + (255 - r) * k); g = (int) (g + (255 - g) * k); bl = (int) (bl + (255 - bl) * k);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag t = StackNbt.get(stack).getCompound(TAG);
        if (t.isEmpty()) return;
        describe(fromTag(t, false), tooltip);
        if (t.getInt("DL") > 0) {
            Flavour d = Flavour.of(t.getString("D"));
            if (d != null) tooltip.add(Component.translatable("tooltip.riverfishing.boilie_dip",
                    Component.translatable("flavour.riverfishing." + d.id()), t.getInt("DL")).withStyle(s -> s.withColor(d.rgb)));
        }
        tooltip.add(Component.translatable("tooltip.riverfishing.boilie_throw").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The lines a boilie and its paste share: size and how it floats, then each flavour in its colour. */
    static void describe(Boilie b, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.riverfishing.boilie_kind",
                Component.translatable("boilie.riverfishing." + b.buoyancy().name().toLowerCase(java.util.Locale.ROOT)),
                b.sizeMm()).withStyle(ChatFormatting.GRAY));
        for (Flavour f : b.flavours()) {
            tooltip.add(Component.translatable("flavour.riverfishing." + f.id()).withStyle(s -> s.withColor(f.rgb)));
        }
        if (b.meal()) tooltip.add(Component.translatable("tooltip.riverfishing.boilie_meal").withStyle(ChatFormatting.GRAY));
    }

    // ---- prebaiting ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        HitResult hit = sp.pick(REACH, 1.0f, true);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        ServerLevel sl = sp.serverLevel();
        if (!com.riverfishing.water.WaterBodyDetector.isWater(sl, pos)) return InteractionResultHolder.pass(stack);
        int n = Math.min(HANDFUL, stack.getCount());
        Boilie b = read(stack);
        com.riverfishing.fishing.AlifeData.fedBoilies(sl, pos, b, n * BOILIE_KG);
        if (!sp.getAbilities().instabuild) stack.shrink(n);
        sl.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5f, 1.4f);
        sl.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3 + n, 0.8, 0.1, 0.8, 0.1);
        sp.displayClientMessage(Component.translatable("message.riverfishing.boilies_thrown", n).withStyle(ChatFormatting.GREEN), true);
        return InteractionResultHolder.consume(stack);
    }
}
