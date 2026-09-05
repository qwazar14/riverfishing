package com.riverfishing.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * §data-components (1.20.1 backport): on 1.20.1 there is no component system — arbitrary per-stack data
 * lives in the item's single root NBT tag ({@link ItemStack#getTag()}). This helper keeps the same
 * surface the whole mod was written against (so no call site changes between versions): {@link #get}
 * returns a read-only COPY (mutating it does NOT persist — use {@link #mutate}), and {@link #mutate}
 * does the read-modify-write on the live tag in one shot. The mod namespaces its own keys
 * (e.g. {@code RodComponents}) so they never collide with vanilla tags in the shared root.
 */
public final class StackNbt {
    private StackNbt() {}

    /**
     * The stack's tag — never null, empty when the stack has none. READ ONLY: this is the live
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
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag;
    }

    /** Whether the stack carries any NBT at all (old {@code stack.hasTag()}). */
    public static boolean isEmpty(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || tag.isEmpty();
    }

    /** Whether the stack's tag contains {@code key}. */
    public static boolean contains(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key);
    }

    /** Read-modify-write: apply {@code f} to the live tag and it persists (old {@code getOrCreateTag} + edit). */
    public static void mutate(ItemStack stack, Consumer<CompoundTag> f) {
        f.accept(stack.getOrCreateTag());
    }

    /** Replace the stack's tag with {@code tag} wholesale. */
    public static void set(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag);
    }

    /** Remove all NBT from the stack. */
    public static void clear(ItemStack stack) {
        stack.setTag(null);
    }
}
