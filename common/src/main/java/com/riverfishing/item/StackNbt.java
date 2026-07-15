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

    /** A copy of the stack's tag — never null, empty when the stack has none. Read-only intent. */
    public static CompoundTag get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
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
