package com.riverfishing.client;

import com.riverfishing.network.ShoalPacket;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * §shoal client state: the last shoal the server described, plus a fade so fish drift in and out instead
 * of popping. Held statically because there is exactly one local player.
 */
public final class ShoalState {
    private static volatile BlockPos centre = BlockPos.ZERO;
    private static volatile float clarity;
    private static volatile List<ShoalPacket.Entry> fish = List.of();
    /** 0..1, eased towards clarity every frame — a shoal that appears instantly reads as a glitch. */
    private static float fade;

    private ShoalState() {}

    public static void accept(ShoalPacket p) {
        centre = p.centre();
        clarity = p.clarity();
        fish = p.fish();
    }

    public static void clear() {
        fish = List.of();
        clarity = 0f;
        fade = 0f;
    }

    public static BlockPos centre() { return centre; }
    public static List<ShoalPacket.Entry> fish() { return fish; }

    /** Call once per frame. Returns the eased visibility to draw at. */
    public static float tickFade(float partialTick) {
        float target = fish.isEmpty() ? 0f : clarity;
        fade += (target - fade) * Math.min(1f, 0.06f * Math.max(1f, partialTick * 20f));
        if (Math.abs(target - fade) < 0.002f) fade = target;
        return fade;
    }
}
