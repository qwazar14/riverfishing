package com.riverfishing.client;

import com.riverfishing.component.RodType;
import com.riverfishing.fishing.FlyCast;
import com.riverfishing.item.RodItem;
import com.riverfishing.network.FlyBeatPacket;
import com.riverfishing.network.FlyCastPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * §fly: the rhythm gauge — the cast bar's brass frame with a green stop at BOTH ends, the needle
 * sweeping between them, a pip above the bar for every false cast in the air, and the metres the line
 * will land at on the plaque. The sneak key's down-edge is the beat; the server judges it.
 */
public final class FlyCastClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static int maxBeats;
    private static boolean openLoop;
    private static boolean shiftWas;

    private static final net.minecraft.resources.ResourceLocation BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private FlyCastClient() {}

    public static void accept(FlyCastPacket p) {
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        maxBeats = p.maxBeats;
        openLoop = p.openLoop;
    }

    public static boolean isActive() {
        return active;
    }

    /** Triangle wave 0..1 with the given period; matches the server's marker. */
    private static float marker(float t) {
        if (period <= 0) return 0.5f;
        float phase = (t % period) / period;
        if (phase < 0) phase += 1f;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    /** The fly rod being held on the charge, or EMPTY when the hold is over (slot switch, hand change). */
    private static ItemStack heldFlyRod(Minecraft mc) {
        if (mc.player == null || !mc.player.isUsingItem()) return ItemStack.EMPTY;
        ItemStack use = mc.player.getUseItem();
        return use.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY ? use : ItemStack.EMPTY;
    }

    /** Client tick: the sneak key's down-edge while the gauge is up is a beat. */
    public static void tick(Minecraft mc) {
        if (!active) {
            shiftWas = false;
            return;
        }
        if (heldFlyRod(mc).isEmpty()) {
            // The hold ended without a release (a slot switch): the server never hears a release, so
            // the gauge comes down here — the next use begins a fresh rhythm anyway.
            active = false;
            return;
        }
        boolean down = mc.options.keyShift.isDown();
        if (down && !shiftWas) ModNetwork.toServer(new FlyBeatPacket());
        shiftWas = down;
    }

    public static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        ItemStack rod = heldFlyRod(mc);
        if (rod.isEmpty()) return;
        float t = (mc.level.getGameTime() - startTick) + partialTick;

        // The cast gauge's own geometry — frame 120x16, with a 112x8 recess at (4,4).
        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int tx = x + 4, ty = y + 4;
        g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 128, 48);

        // The two stops: green at both ends, as wide as the server's zone.
        int zw = (int) (zoneHalf * TW);
        g.fill(tx, ty, tx + zw, ty + TH, 0xE05FA84E);
        g.fill(tx + TW - zw, ty, tx + TW, ty + TH, 0xE05FA84E);

        // The needle, through the whole frame and a little past it.
        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, 0xFFFFE8A8);

        // One pip per false cast in the air, red when the loop has collapsed, hollow up to the rod's max.
        int px0 = x + (FW - maxBeats * 5) / 2, py0 = y - 6;
        for (int i = 0; i < maxBeats; i++) {
            int px = px0 + i * 5;
            g.fill(px, py0, px + 3, py0 + 3, i < beats ? (openLoop ? 0xFFE05A4A : 0xFFFFC83C) : 0x60231A10);
        }

        // The metres on the plaque: the pickup plus the false casts, capped at what the rod carries.
        double metres = Math.min(FlyCast.PICKUP + beats, com.riverfishing.fishing.FishingManager.castRangeMax(rod));
        String label = String.format(java.util.Locale.ROOT, "%.1f m", metres);
        int lw = mc.font.width(label);
        int px = x + (FW - 48) / 2, py = y - 26;
        g.blit(BAR, px, py, 48, 16, 0f, 32f, 48, 16, 128, 48);
        g.drawString(mc.font, label, px + (48 - lw) / 2, py + 4, 0xFF3A2A18, false);
        g.drawCenteredString(mc.font, Component.translatable("gui.riverfishing.fly_beats", beats),
                screenW / 2, py - 10, openLoop ? 0xFFE05A4A : 0xFFF0E6CD);
    }
}
