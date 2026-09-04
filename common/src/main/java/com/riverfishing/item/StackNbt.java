package com.riverfishing.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * §data-components (1.21): item NBT is gone — arbitrary per-stack data now lives in the
 * {@code minecraft:custom_data} component ({@link CustomData}, an IMMUTABLE tag wrapper). This helper
 * bridges the old {@code ItemStack#getTag/getOrCreateTag/hasTag} ergonomics: {@link #get} returns a
 * read-only COPY (mutating it does NOT persist — use {@link #mutate}), and {@link #mutate} does the
 * read-modify-write in one shot.
 */
public final class StackNbt {
    private StackNbt() {}

    /**
     * The stack's custom-data tag — never null, empty when the stack has none. READ ONLY: this is the live
     * tag, not a copy, so writing to it would edit every stack that shares it.
     *
     * <p>§nbt-read: it used to hand back a deep copy. That is a fine price once and a terrible one
     * sixty times a second: drawing ONE fish asks for its species, its scale, its morph, its card and
     * its pattern, and the item colour asks again per layer — about twenty-five copies of a
     * twenty-entry card per fish per frame. A chest holding twenty-one of them dropped the frame rate
     * on its own, which is how this was found. Nothing in the mod mutates what {@code get} returns;
     * everything that writes goes through {@link #mutate}.
     */
    public static CompoundTag get(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
    }

    /** Whether the stack carries any custom data at all (old {@code stack.hasTag()}). */
    public static boolean isEmpty(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty();
    }

    /** Whether the stack's custom data contains {@code key}. */
    public static boolean contains(ItemStack stack, String key) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(key);
    }

    /** Read-modify-write: apply {@code f} to a mutable copy and store it back (old {@code getOrCreateTag} + edit). */
    public static void mutate(ItemStack stack, Consumer<CompoundTag> f) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, f);
    }

    /** Replace the stack's custom data with {@code tag} wholesale. */
    public static void set(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Remove all custom data from the stack. */
    public static void clear(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }
}
