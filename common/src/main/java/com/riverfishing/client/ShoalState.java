package com.riverfishing.client;

import com.riverfishing.network.ShoalPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * §shoal client state: the last shoal the server described, plus a fade so fish drift in and out instead
 * of popping. Held statically because there is exactly one local player.
 */
public final class ShoalState {
    private static volatile BlockPos centre = BlockPos.ZERO;
    private static volatile float clarity;
    private static volatile byte spread;
    private static volatile List<ShoalPacket.Entry> fish = List.of();
    /** The specimen stacks, built once per packet — not per frame, for 28 fish at 60 fps. */
    private static volatile List<ItemStack> stacks = List.of();
    /** 0..1, eased towards clarity every frame — a shoal that appears instantly reads as a glitch. */
    private static float fade;

    private ShoalState() {}

    public static void accept(ShoalPacket p) {
        centre = p.centre();
        clarity = p.clarity();
        spread = p.spread();
        fish = p.fish();
        List<ItemStack> built = new java.util.ArrayList<>(p.fish().size());
        for (ShoalPacket.Entry e : p.fish()) built.add(stackFor(e));
        stacks = built;
    }

    /**
     * A real specimen stack, not a bare item: the sprite scales from the length in NBT, and with no NBT
     * {@code getIconScale} returns a flat 1.0 — which is why the first cut drew every fish the same size.
     */
    private static ItemStack stackFor(ShoalPacket.Entry e) {
        var item = com.riverfishing.registry.ModItems.fishItem(e.species());
        if (item == null) return ItemStack.EMPTY;
        return com.riverfishing.item.FishItem.create(item, e.species(), e.weightG(), e.lengthCm(), true);
    }

    public static void clear() {
        fish = List.of();
        stacks = List.of();
        clarity = 0f;
        fade = 0f;
    }

    public static BlockPos centre() { return centre; }
    public static List<ShoalPacket.Entry> fish() { return fish; }
    public static List<ItemStack> stacks() { return stacks; }
    /** Half-span of the water body in blocks: how far the outermost circuit may reach. */
    public static byte spread() { return spread; }

    /** Call once per frame. Returns the eased visibility to draw at. */
    public static float tickFade(float partialTick) {
        float target = fish.isEmpty() ? 0f : clarity;
        fade += (target - fade) * Math.min(1f, 0.06f * Math.max(1f, partialTick * 20f));
        if (Math.abs(target - fade) < 0.002f) fade = target;
        return fade;
    }
}
