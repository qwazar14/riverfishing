package com.riverfishing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * The on-screen HUD overlays (§immersion): the float-timing cue (#5) and the cast-power bar
 * (§cast-minigame). Driven by Architectury's {@code ClientGuiEvent.RENDER_HUD}, which hands us a
 * {@link GuiGraphicsExtractor} and the frame partial-tick on both loaders.
 */
public final class ClientHud {
    private ClientHud() {}

    public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Minecraft mc = Minecraft.getInstance();
        if (FloatTimingClient.isActive()) {
            FloatTimingClient.render(graphics,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    partialTick);
        }
        renderCastPower(graphics, mc);
    }

    /** Cast power bar (§cast-minigame): shown while charging a cast (holding RMB with no line out). */
    private static void renderCastPower(GuiGraphicsExtractor g, Minecraft mc) {
        Player player = mc.player;
        if (player == null || !player.isUsingItem()) return;
        if (!(player.getUseItem().getItem() instanceof com.riverfishing.item.RodItem rodItem)) return;
        // §spin-charge (2.3): lure rods now charge-and-cast too, so they show the bar — but only while
        // charging. Once a line is out, holding is a RETRIEVE, not a charge, so hide it (next line).
        if (ClientLineState.active()) return;

        int used = player.getUseItem().getUseDuration(player) - player.getUseItemRemainingTicks();
        float power = com.riverfishing.item.RodItem.castPower(used);

        // §cast-bar-cut: mirror the server's rod-test lower bound — an under-loaded blank can't throw
        // as far, so the far end of the bar is a dead zone the fill can't enter. Computed client-side
        // from the installed rig's mass vs the rod's minimum test (both pure, NBT-readable here).
        float usable = 1.0f;
        var rodType = rodItem.rodType();
        var rig = com.riverfishing.item.RodData.get(player.getUseItem(), com.riverfishing.component.ComponentSlot.RIG);
        if (rig.getItem() instanceof com.riverfishing.item.RigItem ri
                && rodType.castWeightMin() > 0
                && ri.rigType().massGrams() < rodType.castWeightMin()) {
            usable = 0.55f; // matches maxRange *= 0.55 server-side
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = 102, h = 7;
        int x = (sw - w) / 2, y = sh - 64;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xCC2A1E12);           // wood frame
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF5C4A34);
        g.fill(x, y, x + w, y + h, 0xFF1E1610);                            // track
        int cut = (int) (usable * w);
        // the unreachable far zone (under-loaded rod): a dim red dead band
        if (usable < 1.0f) {
            g.fill(x + cut, y, x + w, y + h, 0x55B03030);
            g.fill(x + cut, y, x + cut + 1, y + h, 0xFFE05A4A);           // hard cut-off marker
        }
        int fill = (int) (Math.min(power, usable) * w);
        int color = power < 0.5f ? 0xFF7CB342 : (power < 0.85f ? 0xFFF4C542 : 0xFFE05A4A);
        g.fill(x, y, x + fill, y + h, color);
        // tick marks at 50% / 85% so the timing is readable (only within the usable zone)
        if (0.5f < usable) g.fill(x + w / 2, y, x + w / 2 + 1, y + h, 0x66FFFFFF);
        if (0.85f < usable) g.fill(x + (int) (w * 0.85f), y, x + (int) (w * 0.85f) + 1, y + h, 0x66FFFFFF);
    }
}
