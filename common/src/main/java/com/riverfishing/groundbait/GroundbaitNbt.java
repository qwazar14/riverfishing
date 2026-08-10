package com.riverfishing.groundbait;

import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.item.StackNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * How a {@link GroundbaitMix} rides on an {@link ItemStack}.
 *
 * <p>Split from the mix itself on purpose. GroundbaitMix is the arithmetic that decides what a
 * groundbait is, and its self-check is what guards the balance of the feature; keeping Minecraft out of
 * that class means the check runs with plain `java` and no mapped game jar, so it stays runnable
 * instead of becoming decoration. Everything that needs a stack is here.
 */
public final class GroundbaitNbt {

    private static final String ROOT = "Groundbait";
    private static final String PARTS = "Parts";
    private static final String ID = "id";
    private static final String SPOONS = "n";

    private GroundbaitNbt() {}

    /**
     * What is in this jar.
     *
     * <p>A stack with no mix tag is one of the four ready-made items, so its preset comes back rather
     * than null — every groundbait in the game answers this, including the ones sitting in chests in
     * worlds saved before 0.8.0.
     */
    public static GroundbaitMix read(ItemStack stack) {
        CompoundTag tag = StackNbt.get(stack);
        if (tag.contains(ROOT)) {
            ListTag list = tag.getCompound(ROOT).getList(PARTS, Tag.TAG_COMPOUND);
            List<GroundbaitMix.Part> recipe = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag part = list.getCompound(i);
                recipe.add(new GroundbaitMix.Part(part.getString(ID), part.getInt(SPOONS)));
            }
            GroundbaitMix mixed = GroundbaitMix.of(recipe);
            // A tag that no longer stirs — a component renamed out of the pantry, say — falls back to
            // the preset rather than throwing. A stale jar should fish badly, not crash a save.
            if (mixed != null) return mixed;
        }
        return preset(stack);
    }

    /** The preset behind a stack, by the item it is. */
    public static GroundbaitMix preset(ItemStack stack) {
        String category = stack.getItem() instanceof GroundbaitItem g ? g.category() : "powder";
        return GroundbaitMix.PRESETS.getOrDefault(category, GroundbaitMix.PRESETS.get("powder"));
    }

    /** Stamp a mix onto a stack. A preset writes nothing — its numbers come from the item itself. */
    public static void write(ItemStack stack, GroundbaitMix mix) {
        if (mix == null || mix.isPreset()) return;
        StackNbt.mutate(stack, tag -> {
            ListTag list = new ListTag();
            for (GroundbaitMix.Part p : mix.parts()) {
                CompoundTag part = new CompoundTag();
                part.putString(ID, p.id());
                part.putInt(SPOONS, p.spoons());
                list.add(part);
            }
            CompoundTag root = new CompoundTag();
            root.put(PARTS, list);
            tag.put(ROOT, root);
        });
    }
}
