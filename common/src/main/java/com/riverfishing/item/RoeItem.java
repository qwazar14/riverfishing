package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §breeding (0.9.0): a clutch of roe out of the aquarium. NBT via {@link StackNbt}: {@code Species}
 * (id string), {@code Genome} (the offspring's, already crossed), {@code Count} (eggs), {@code Laid}
 * (world day). One clutch is one item — it does not stack, because two clutches are two genomes.
 */
public class RoeItem extends Item {
    public static final String TAG_SPECIES = "Species";
    public static final String TAG_GENOME = "Genome";
    public static final String TAG_COUNT = "Count";
    public static final String TAG_LAID = "Laid";

    public RoeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(ResourceLocation species, String genome, int count, long day) {
        ItemStack s = new ItemStack(com.riverfishing.registry.ModItems.ROE.get());
        StackNbt.mutate(s, t -> {
            t.putString(TAG_SPECIES, species.toString());
            t.putString(TAG_GENOME, genome);
            t.putInt(TAG_COUNT, count);
            t.putLong(TAG_LAID, day);
        });
        return s;
    }

    /** Null for a stack without a species (the creative-tab entry). Shared with {@link FryItem}: same keys. */
    public static ResourceLocation species(ItemStack s) {
        String id = StackNbt.get(s).getString(TAG_SPECIES);
        return id.isEmpty() ? null : ResourceLocation.tryParse(id);
    }

    public static String genome(ItemStack s) {
        return StackNbt.get(s).getString(TAG_GENOME);
    }

    public static int count(ItemStack s) {
        return StackNbt.get(s).getInt(TAG_COUNT);
    }

    /** The species' display name, the same key the fish item uses. */
    public static Component speciesName(ResourceLocation species) {
        return Component.translatable("fish." + species.getNamespace() + "." + species.getPath());
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation sp = species(stack);
        return sp == null ? Component.translatable("item.riverfishing.roe.generic")
                : Component.translatable("item.riverfishing.roe", speciesName(sp));
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        brood(stack, "tooltip.riverfishing.roe_count", tooltip);
    }

    /** The two lines both brood items show: how many, and what genes. Nothing when the stack is a bare entry. */
    static void brood(ItemStack stack, String countKey, List<Component> tooltip) {
        CompoundTag t = StackNbt.get(stack);
        if (!t.contains(TAG_COUNT)) return;
        tooltip.add(Component.translatable(countKey, t.getInt(TAG_COUNT)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.riverfishing.genome", t.getString(TAG_GENOME))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
