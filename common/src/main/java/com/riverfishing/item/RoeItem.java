package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    /** §pattern: the index the clutch runs at — inherited from the pair, not rolled. */
    public static final String TAG_PATTERN = com.riverfishing.fish.Pattern.TAG;

    public RoeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(Identifier species, String genome, int count, long day) {
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
    public static Identifier species(ItemStack s) {
        String id = StackNbt.get(s).getStringOr(TAG_SPECIES, "");
        return id.isEmpty() ? null : Identifier.tryParse(id);
    }

    public static String genome(ItemStack s) {
        return StackNbt.get(s).getStringOr(TAG_GENOME, "");
    }

    public static int count(ItemStack s) {
        return StackNbt.get(s).getIntOr(TAG_COUNT, 0);
    }

    /**
     * §pattern: the clutch's index — the parents' mean with a small mutation, written when the pair
     * spawns and carried through the egg into the fry, because the pattern is the LINE and the line is
     * what a breeder is working on. {@link com.riverfishing.fish.Pattern#NONE} on roe laid before this.
     */
    public static int pattern(ItemStack s) {
        CompoundTag t = StackNbt.get(s);
        return t.getIntOr(TAG_PATTERN, com.riverfishing.fish.Pattern.NONE);
    }

    public static void setPattern(ItemStack s, int pattern) {
        if (com.riverfishing.fish.Pattern.has(pattern)) {
            StackNbt.mutate(s, t -> t.putInt(TAG_PATTERN, pattern));
        }
    }

    /** The species' display name, the same key the fish item uses. */
    public static Component speciesName(Identifier species) {
        return Component.translatable("fish." + species.getNamespace() + "." + species.getPath());
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier sp = species(stack);
        return sp == null ? Component.translatable("item.riverfishing.roe.generic")
                : Component.translatable("item.riverfishing.roe", speciesName(sp));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        brood(stack, "tooltip.riverfishing.roe_count", tooltip);
    }

    /** The two lines both brood items show: how many, and what genes. Nothing when the stack is a bare entry. */
    static void brood(ItemStack stack, String countKey, java.util.function.Consumer<Component> tooltip) {
        CompoundTag t = StackNbt.get(stack);
        if (!t.contains(TAG_COUNT)) return;
        tooltip.accept(Component.translatable(countKey, t.getIntOr(TAG_COUNT, 0)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.riverfishing.genome", t.getStringOr(TAG_GENOME, ""))
                .withStyle(ChatFormatting.DARK_GRAY));
        // §pattern: the clutch's index and the family it will hatch into — what the line is FOR.
        // §pattern-gate: on the species that wear one; roe laid before the gate keeps its int unread.
        int pattern = com.riverfishing.registry.ModItemTags.patterned(species(stack))
                ? pattern(stack) : com.riverfishing.fish.Pattern.NONE;
        if (com.riverfishing.fish.Pattern.has(pattern)) {
            tooltip.accept(Component.translatable("tooltip.riverfishing.pattern", pattern,
                    Component.translatable("pattern.riverfishing."
                            + com.riverfishing.fish.Pattern.family(pattern)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

// §ported26
