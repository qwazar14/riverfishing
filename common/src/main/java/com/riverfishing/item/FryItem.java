package com.riverfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §breeding (0.9.0): fry hatched from roe or netted in a pond. NBT: the same {@code Species} / {@code Genome}
 * keys as {@link RoeItem} — fry are roe that lived, so the readers are shared.
 *
 * <p>§fry-stack (1.1.0): ONE item is ONE fry. A hatch or a haul is a stack of them, and fry of the same species,
 * genome and pattern stack like anything else. Until then the item was a bucket that did not stack, with a
 * {@code Count} of fry in its NBT; such a bucket still reads right everywhere ({@link #count}) and turns into its
 * fry the moment it sits in a player's inventory ({@link #inventoryTick}).
 */
public class FryItem extends Item {
    public FryItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    /**
     * {@code count} fry of one brood as one stack. A clutch from the aquarium can pass 64 (to about 75): the
     * stack is left whole in the tank's cup, and taking it out splits it like any overfull slot.
     */
    public static ItemStack of(Identifier species, String genome, int count) {
        ItemStack s = new ItemStack(com.riverfishing.registry.ModItems.FRY.get(), Math.max(1, count));
        StackNbt.mutate(s, t -> {
            t.putString(RoeItem.TAG_SPECIES, species.toString());
            t.putString(RoeItem.TAG_GENOME, genome);
        });
        return s;
    }

    public static Identifier species(ItemStack s) {
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
        int pattern = t.getIntOr(com.riverfishing.fish.Pattern.TAG, com.riverfishing.fish.Pattern.NONE);
        ItemStack s = com.riverfishing.item.FishItem.create(item, sp, 1, 5, true);
        net.minecraft.nbt.CompoundTag card = new net.minecraft.nbt.CompoundTag();
        if (!variety.isEmpty()) card.putString("Variety", variety);
        card.putString("Genes", genome);
        card.putInt(com.riverfishing.fish.Pattern.TAG, pattern);
        StackNbt.mutate(s, tag -> tag.put(com.riverfishing.fish.CatchCard.TAG, card));
        com.riverfishing.item.FishItem.stampIcon(s);   // 26.x: the icon is the stack
        return s;
    }

    public static String genome(ItemStack s) {
        return RoeItem.genome(s);
    }

    /** How many fry this is: the stack's size — or, for a bucket from before §fry-stack, the count it carried. */
    public static int count(ItemStack s) {
        int bucket = StackNbt.get(s).getIntOr(RoeItem.TAG_COUNT, 0);
        return bucket > 0 ? bucket * s.getCount() : s.getCount();
    }

    public static void setCount(ItemStack s, int n) {
        if (StackNbt.contains(s, RoeItem.TAG_COUNT)) StackNbt.mutate(s, t -> t.putInt(RoeItem.TAG_COUNT, n));
        else s.setCount(n);
    }

    /**
     * §fry-stack: an old bucket of N fry in the inventory becomes N fry — a stack of 64 where it lay, the rest
     * beside it (or at the player's feet when the bag is full). A fry picked up out of the water before it swam
     * off loses the release timer it was stamped with, so it stacks with its brood again.
     */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot) {
        net.minecraft.nbt.CompoundTag t = StackNbt.get(stack);
        boolean bucket = t.contains(RoeItem.TAG_COUNT), wet = t.contains(FishItem.TAG_RELEASE_AT);
        if (!bucket && !wet) return;
        int n = count(stack);
        StackNbt.mutate(stack, x -> {
            x.remove(RoeItem.TAG_COUNT);
            x.remove(FishItem.TAG_RELEASE_AT);
        });
        if (!bucket || !(entity instanceof net.minecraft.world.entity.player.Player p)) return;
        int max = stack.getMaxStackSize();
        stack.setCount(Math.min(n, max));
        for (int rest = n - stack.getCount(); rest > 0; ) {
            ItemStack more = stack.copyWithCount(Math.min(rest, max));
            rest -= more.getCount();
            if (!p.getInventory().add(more)) com.riverfishing.compat.Mc.drop(p, more, false);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier sp = species(stack);
        return sp == null ? Component.translatable("item.riverfishing.fry.generic")
                : Component.translatable("item.riverfishing.fry", RoeItem.speciesName(sp));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        if (StackNbt.contains(stack, RoeItem.TAG_COUNT)) RoeItem.brood(stack, "tooltip.riverfishing.fry_count", tooltip);
        else RoeItem.lineage(stack, tooltip);   // §fry-stack: the count is the stack's own number now
    }
}

// §ported26
