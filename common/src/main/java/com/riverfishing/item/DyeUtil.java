package com.riverfishing.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * §lure-color (1.20.1 backport): the dyed RGB of a painted lure. On 1.21 this rode the
 * {@code minecraft:dyed_color} component; on 1.20.1 there is no such component, so we use the vanilla
 * dyeable convention — the colour lives in the {@code display.color} NBT int (exactly where leather armour
 * keeps it). The custom {@code LureDyeRecipe} writes it here and the item colour provider reads it for the
 * model tint.
 */
public final class DyeUtil {
    private DyeUtil() {}

    private static final String DISPLAY = "display";
    private static final String COLOR = "color";

    /** The lure's dyed RGB, or -1 if it carries no colour. */
    public static int color(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DISPLAY, Tag.TAG_COMPOUND)) return -1;
        CompoundTag display = tag.getCompound(DISPLAY);
        return display.contains(COLOR, Tag.TAG_INT) ? display.getInt(COLOR) : -1;
    }

    /** Paints the lure the given RGB (stored the vanilla dyeable way). */
    public static void setColor(ItemStack stack, int rgb) {
        stack.getOrCreateTagElement(DISPLAY).putInt(COLOR, rgb);
    }

    /**
     * Mixes {@code dyes} into the lure's colour with vanilla's leather-armour algorithm (average the
     * channels, then rescale to the average of the per-dye maxima) and returns a fresh single dyed copy.
     */
    public static ItemStack applyDyes(ItemStack lure, java.util.List<net.minecraft.world.item.DyeItem> dyes) {
        int[] sum = new int[3];
        int totalMax = 0;
        int count = 0;
        int existing = color(lure);
        if (existing >= 0) {
            sum[0] += (existing >> 16) & 0xFF;
            sum[1] += (existing >> 8) & 0xFF;
            sum[2] += existing & 0xFF;
            totalMax += Math.max(sum[0], Math.max(sum[1], sum[2]));
            count++;
        }
        for (net.minecraft.world.item.DyeItem dye : dyes) {
            float[] c = dye.getDyeColor().getTextureDiffuseColors();
            int r = (int) (c[0] * 255f);
            int g = (int) (c[1] * 255f);
            int b = (int) (c[2] * 255f);
            totalMax += Math.max(r, Math.max(g, b));
            sum[0] += r;
            sum[1] += g;
            sum[2] += b;
            count++;
        }
        if (count == 0) return lure;
        int r = sum[0] / count;
        int g = sum[1] / count;
        int b = sum[2] / count;
        float avgMax = (float) totalMax / count;
        float maxOfAvg = Math.max(r, Math.max(g, b));
        r = (int) (r * avgMax / maxOfAvg);
        g = (int) (g * avgMax / maxOfAvg);
        b = (int) (b * avgMax / maxOfAvg);
        ItemStack out = lure.copyWithCount(1);
        setColor(out, (r << 16) | (g << 8) | b);
        return out;
    }
}
