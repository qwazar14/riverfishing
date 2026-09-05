package com.riverfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §breeding (0.9.0): a bucketful of fry hatched from roe (or netted with the fry net). NBT: the same
 * {@code Species} / {@code Genome} / {@code Count} keys as {@link RoeItem} — fry are roe that lived,
 * so the readers are shared. The item does not stack; {@code Count} is how many fry it holds.
 */
public class FryItem extends Item {
    public FryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(ResourceLocation species, String genome, int count) {
        ItemStack s = new ItemStack(com.riverfishing.registry.ModItems.FRY.get());
        StackNbt.mutate(s, t -> {
            t.putString(RoeItem.TAG_SPECIES, species.toString());
            t.putString(RoeItem.TAG_GENOME, genome);
            t.putInt(RoeItem.TAG_COUNT, count);
        });
        return s;
    }

    public static ResourceLocation species(ItemStack s) {
        return RoeItem.species(s);
    }

    /**
     * §fry-look: the fish this fry will be, as a stack the renderers can draw — the species' own item
     * with a card carrying the variety read off the genome, the genes and the inherited pattern. A bare
     * species stack drew a white koi on 26.x and a kohaku for every koi on 1.21.1; this is what a
     * showa's fry looks like. EMPTY when the fry names no species (a creative-tab bucket).
     */
    public static ItemStack look(ItemStack fry) {
        var sp = species(fry);
        if (sp == null) return ItemStack.EMPTY;
        var item = com.riverfishing.registry.ModItems.fishItem(sp);
        if (item == null) return ItemStack.EMPTY;
        String path = sp.getPath(), genome = genome(fry);
        String variety = com.riverfishing.fish.Genome.isKoiId(path)
                ? "koi_" + com.riverfishing.fish.Genome.koiVariety(genome)
                : com.riverfishing.fish.Genome.varietyOfSpecies(path).isEmpty() ? ""
                : com.riverfishing.fish.Genome.carpVariety(genome);
        net.minecraft.nbt.CompoundTag t = StackNbt.get(fry);
        int pattern = t.contains(com.riverfishing.fish.Pattern.TAG) ? t.getInt(com.riverfishing.fish.Pattern.TAG) : com.riverfishing.fish.Pattern.NONE;
        ItemStack s = com.riverfishing.item.FishItem.create(item, sp, 1, 5, true);
        net.minecraft.nbt.CompoundTag card = new net.minecraft.nbt.CompoundTag();
        if (!variety.isEmpty()) card.putString("Variety", variety);
        card.putString("Genes", genome);
        card.putInt(com.riverfishing.fish.Pattern.TAG, pattern);
        StackNbt.mutate(s, tag -> tag.put(com.riverfishing.fish.CatchCard.TAG, card));
        return s;
    }

    public static String genome(ItemStack s) {
        return RoeItem.genome(s);
    }

    public static int count(ItemStack s) {
        return RoeItem.count(s);
    }

    public static void setCount(ItemStack s, int n) {
        StackNbt.mutate(s, t -> t.putInt(RoeItem.TAG_COUNT, n));
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation sp = species(stack);
        return sp == null ? Component.translatable("item.riverfishing.fry.generic")
                : Component.translatable("item.riverfishing.fry", RoeItem.speciesName(sp));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RoeItem.brood(stack, "tooltip.riverfishing.fry_count", tooltip);
    }
}
