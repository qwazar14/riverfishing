package com.riverfishing.client;

import com.riverfishing.network.ShoalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * §shoal client state: the shoals the server last described, plus a fade so fish drift in and out instead
 * of popping. Held statically because there is exactly one local player.
 */
public final class ShoalState {
    private static volatile List<ShoalPacket.Spot> spots = List.of();
    /** The level these shoals belong to, so a world change cannot leave stale fish on screen. */
    private static volatile Level owner;
    /** 0..1, eased in every frame — a shoal that appears the instant you round a corner reads as a glitch. */
    private static float fade;

    private ShoalState() {}

    public static void accept(ShoalPacket p) {
        spots = p.spots();
        owner = Minecraft.getInstance().level;
    }

    public static void clear() {
        spots = List.of();
        owner = null;
        fade = 0f;
    }

    public static List<ShoalPacket.Spot> spots() { return spots; }
    public static Level owner() { return owner; }

    /** Call once per frame. Returns the eased visibility to draw at. */
    public static float tickFade(float partialTick) {
        float target = spots.isEmpty() ? 0f : 1f;
        fade += (target - fade) * Math.min(1f, 0.06f * Math.max(1f, partialTick * 20f));
        if (Math.abs(target - fade) < 0.002f) fade = target;
        return fade;
    }
}
