package com.riverfishing.groundbait;

import com.riverfishing.item.StackNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
    /** The dyed colour. Stored, not derived: a dye leaves no other trace on the stack. */
    private static final String RGB = "rgb";

    private GroundbaitNbt() {}

    /**
     * What is in this jar.
     *
     * <p>A stack with no mix tag is a plain jar off the shelf, so the BASE comes back rather than null —
     * every groundbait in the game answers this, including the ones sitting in chests in worlds saved
     * before anyone mixed anything.
     */
    public static GroundbaitMix read(ItemStack stack) {
        CompoundTag tag = StackNbt.get(stack);
        if (tag.contains(ROOT)) {
            // §26.1: the tag getters return Optionals, so every read states its own default.
            ListTag list = tag.getCompoundOrEmpty(ROOT).getListOrEmpty(PARTS);
            List<GroundbaitMix.Part> recipe = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag part = list.getCompoundOrEmpty(i);
                recipe.add(new GroundbaitMix.Part(part.getStringOr(ID, ""), part.getIntOr(SPOONS, 0)));
            }
            GroundbaitMix mixed = GroundbaitMix.of(recipe);
            // A tag that no longer stirs — a component dropped out of the pantry, which is exactly what
            // happened to the three dead jars in 0.8.0 — falls back to the base rather than throwing. A
            // stale jar should fish like a plain one, not crash a save.
            if (mixed != null) {
                int rgb = tag.getCompoundOrEmpty(ROOT).getIntOr(RGB, 0);
                return rgb != 0 ? mixed.recoloured(rgb) : mixed;
            }
        }
        return GroundbaitMix.BASE;
    }

    /**
     * Has a mix been stamped onto this stack — i.e. is it somebody's own groundbait rather than a
     * ready-made jar?
     *
     * <p>Deliberately asks the TAG, not what the tag stirs into. {@link #read} falls back to the preset
     * for a tag it cannot stir, so a derived test ("does this read as a preset?") would wave a
     * hand-edited or out-of-range jar straight back through as an ingredient — through the very hole it
     * exists to close.
     */
    public static boolean isStamped(ItemStack stack) {
        return StackNbt.get(stack).contains(ROOT);
    }

    /** Stamp a mix onto a stack. A plain jar writes nothing — the base is what an empty tag means. */
    public static void write(ItemStack stack, GroundbaitMix mix) {
        if (mix == null || mix.isBase()) return;
        // §26.x: item tinting here is declared in assets/riverfishing/items/*.json, and the only tint
        // source that can see a per-stack colour is minecraft:dye — which reads this component. So the
        // jar carries its colour as well as computing it, written from the same mix at the same moment
        // the parts are, because two colours that could disagree is exactly how this feature has been
        // bitten before.
        stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                new net.minecraft.world.item.component.DyedItemColor(mix.rgb()));
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
            root.putInt(RGB, mix.rgb());
            tag.put(ROOT, root);
        });
    }
}
