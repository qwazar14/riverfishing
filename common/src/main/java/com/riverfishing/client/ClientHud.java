package com.riverfishing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * The on-screen HUD overlays (§immersion): the float-timing cue (#5) and the cast-power bar
 * (§cast-minigame). Driven by Architectury's {@code ClientGuiEvent.RENDER_HUD}, which hands us a
 * {@link GuiGraphics} and the frame partial-tick on both loaders.
 */
public final class ClientHud {
    private ClientHud() {}

    /**
     * §finder-hud: the sounder strip, live while the finder is in a hand.
     *
     * <p>A HUD strip rather than a screen, and that is not a stylistic choice: a screen takes the
     * controls, so a "walk the bank and watch the trace" device CANNOT be a screen. Held, it draws;
     * right-clicked, {@link FinderScreen} opens with the names and the reasons on it.
     *
     * <p>It scrolls: each sounding is a column pushed on the right, the way a paper sounder wrote. So
     * walking a bank draws the bottom you walked over, and a hole in the bed is a shape you can see
     * rather than a number that changed while you were not looking.
     */
    private static void renderFinderStrip(GuiGraphics g, Minecraft mc) {
        if (mc.player == null) return;
        boolean held = isFinder(mc.player.getMainHandItem()) || isFinder(mc.player.getOffhandItem());
        if (!held) {
            FinderState.clear();
            return;
        }
        boolean live = FinderState.fresh() && !FinderState.trace().isEmpty();
        if (!live && ClientSoundings.target() == null) return;
        java.util.List<int[]> trace = FinderState.trace();

        final int W = 122, H = 62;
        int x = mc.getWindow().getGuiScaledWidth() - W - 6;
        int y = 6;

        int scale = 6;
        for (int[] col : trace) scale = Math.max(scale, col[0]);

        if (live) {
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xCC0B1E22);
        g.fill(x, y, x + W, y + 1, 0x5540E0B0);

        int cols = trace.size();
        int step = Math.max(1, W / FinderState.TRACE);
        for (int i = 0; i < cols; i++) {
            int[] col = trace.get(i);
            // Oldest at the left edge, newest against the right: the direction a sounder writes.
            int cx = x + W - (cols - i) * step;
            if (cx < x) continue;
            int floorY = y + 2 + (int) Math.round(col[0] / (double) scale * (H - 6));
            g.fill(cx, floorY, cx + step, y + H, 0xFF6B5A38);
            g.fill(cx, floorY, cx + step, floorY + 1, 0xFF8A7448);
            for (int k = 1; k < col.length; k++) {
                int fy = y + 2 + (int) Math.round(Math.min(col[k], col[0]) / (double) scale * (H - 6));
                if (fy >= floorY) continue;      // a fish under the bed is a fish that is not here
                g.fill(cx, fy, cx + step, fy + 2, 0xFF40E0B0);
            }
        }

        // The one number worth carrying on the strip: how deep it is where you are aiming.
        String depth = FinderState.latest().getCompound("water").getInt("depth") + " m";
        g.drawString(mc.font, depth, x + 3, y + 3, 0xFF9FE9D0, false);
        }

        // §ledge-arrow: a pointer to the nearest feature you have found, under the strip. Rotated by
        // the difference between where it is and where you face, so it reads like a compass needle:
        // straight up is "walk forward". Only for features already on the map — this finds your way
        // back to a hole, it does not find holes.
        int[] near = FinderState.latest().getIntArray("near");
        // §arrow-target: a mark picked on the chart outranks the nearest one, and it is measured
        // from where you stand right now rather than from the last sounding — you may have walked.
        boolean picked = false;
        Long target = ClientSoundings.target();
        if (target != null && mc.player != null) {
            Byte kind = ClientSoundings.spots().get(target);
            near = new int[]{ClientSoundings.keyX(target) - mc.player.getBlockX(),
                    ClientSoundings.keyZ(target) - mc.player.getBlockZ(), kind == null ? 1 : kind};
            picked = true;
        }
        if (near != null && near.length == 3 && mc.player != null) {
            double toSpot = Math.toDegrees(Math.atan2(-near[0], near[1]));    // yaw the spot lies at
            double rel = Math.toRadians(net.minecraft.util.Mth.wrapDegrees(toSpot - mc.player.getYRot()));
            int ax = x + W / 2, ay = y + H + 14;
            double sx = Math.sin(rel), sy = -Math.cos(rel);
            g.fill(ax - 11, ay - 11, ax + 12, ay + 12, 0xCC0B1E22);
            for (int k = -8; k <= 8; k++) {
                int px = (int) Math.round(ax + sx * k), py = (int) Math.round(ay + sy * k);
                g.fill(px, py, px + 2, py + 2, 0xFFFFC83C);
            }
            // the head: two short strokes back from the tip
            for (int k = 0; k < 5; k++) {
                double bx = sx * (8 - k), by = sy * (8 - k);
                int lx = (int) Math.round(ax + bx + sy * k), ly = (int) Math.round(ay + by - sx * k);
                int rx = (int) Math.round(ax + bx - sy * k), ry = (int) Math.round(ay + by + sx * k);
                g.fill(lx, ly, lx + 2, ly + 2, 0xFFFFC83C);
                g.fill(rx, ry, rx + 2, ry + 2, 0xFFFFC83C);
            }
            int dist = (int) Math.round(Math.sqrt((double) near[0] * near[0] + (double) near[1] * near[1]));
            // §arrow-label: two lines under the needle, centred — what it is, then how far. One
            // line beside it read as a sentence; a needle wants a caption.
            String kind = (picked ? "\u2605 " : "") + Component.translatable("spot.riverfishing." + (near[2] == 0 ? "hole" : "ledge")).getString();
            String range = Component.translatable("finder.riverfishing.metres", dist).getString();
            g.drawString(mc.font, kind, ax - mc.font.width(kind) / 2, ay + 14, 0xFFFFC83C, true);
            g.drawString(mc.font, range, ax - mc.font.width(range) / 2, ay + 24, 0xFFFFC83C, true);
        }
    }

    private static boolean isFinder(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof com.riverfishing.item.WaterProbeItem probe && !probe.admin();
    }

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Minecraft mc = Minecraft.getInstance();
        if (FloatTimingClient.isActive()) {
            FloatTimingClient.render(graphics,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    partialTick);
        }
        renderCastPower(graphics, mc);
        renderPumpReel(graphics, mc);
        renderFinderStrip(graphics, mc);
    }

    /**
     * §pump-reel (0.6.0): the fight coach — a compact cue under the crosshair replacing pure
     * intuition. Fish RUNNING → ease off (open the drag / stop cranking); calm → crank. Near the
     * break point the cue turns into a drag alarm.
     */
    private static void renderPumpReel(GuiGraphics g, Minecraft mc) {
        if (mc.player == null || mc.options.hideGui) return;
        ClientLineState.Line l = ClientLineState.lines().get(mc.player.getId());
        if (l == null || !l.fighting) return;
        String key;
        int color;
        if (l.smoothTension > 0.85f) {
            key = "hud.riverfishing.drag_now"; color = 0xFFFF5040;
        } else if (l.running) {
            key = "hud.riverfishing.ease"; color = 0xFFFFC850;
        } else {
            key = "hud.riverfishing.reel"; color = 0xFF7CE07C;
        }
        var font = mc.font;
        String text = net.minecraft.client.resources.language.I18n.get(key);
        // Round 6: the coach lives right under the boss bar — the fight info reads in ONE glance.
        int cx = g.guiWidth() / 2, y = 30;
        int w = font.width(text);
        g.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 11, 0x66000000);
        g.drawCenteredString(font, text, cx, y, color);

        // §rod-load: the key cue is GONE — no more [ arrow ] under the crosshair naming the binding to
        // hold. The rod is the instrument: the blank bends toward the fish and loads with the pull, so a
        // glyph spelling the answer only repeated what the tackle already shows, and reading a keycap is
        // not fishing. The bindings still work (§fight-keys, the quiet override) — nothing advertises them.
    }

    /** Cast power bar (§cast-minigame): shown while charging a cast (holding RMB with no line out). */
    private static final net.minecraft.resources.ResourceLocation BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    /**
     * §cast-metres: the cast-power gauge, in METRES.
     *
     * <p>It was a bar that filled: fifty percent, eighty-five, a colour. Nothing on it said how far the
     * line would go, and "how far" is the only thing an angler charging a cast wants to know. The
     * number printed above the gauge now is the real throw — the same {@code castDistance} the server
     * lands the line at, read off the same rod and rig on this side, so it cannot disagree.
     *
     * <p>§cast-bar: drawn off a generated sheet (tools/gen_cast_bar.py) — an oak frame with a brass
     * rim, the charge as a lit tube from green through amber to red, the dead band an under-loaded
     * rig cannot reach hatched in red, and the metres on a parchment plaque. Ticks every five metres,
     * because the scale is metres now and a tick at "fifty percent" would be a tick at nothing.
     */
    private static void renderCastPower(GuiGraphics g, Minecraft mc) {
        Player player = mc.player;
        if (player == null || !player.isUsingItem()) return;
        if (!(player.getUseItem().getItem() instanceof com.riverfishing.item.RodItem rodItem)) return;
        // §spin-charge (2.3): lure rods now charge-and-cast too, so they show the bar — but only while
        // charging. Once a line is out, holding is a RETRIEVE, not a charge, so hide it (next line).
        if (ClientLineState.active()) return;

        int used = player.getUseItem().getUseDuration(player) - player.getUseItemRemainingTicks();
        float power = com.riverfishing.item.RodItem.castPower(used);
        var rod = player.getUseItem();

        // The far end of the gauge is what the rod TYPE can do; the reachable part is what this rod,
        // with this rig on it, actually does. The gap between them is the dead band (§cast-bar-cut).
        double base = com.riverfishing.fishing.FishingManager.castRangeBase(rodItem.rodType());
        double reach = com.riverfishing.fishing.FishingManager.castRangeMax(rod);
        double metres = com.riverfishing.fishing.FishingManager.castDistance(rod, power);
        float usable = base <= 0 ? 1f : (float) Math.min(1.0, reach / base);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (sw - FW) / 2, y = sh - 70;
        int tx = x + 4, ty = y + 4;                          // the recess the sheet leaves for the fill

        g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 128, 48);
        // The charge, clipped to the metres: a fraction of the base reach, so the tube fills to where
        // the line will land on the gauge's own scale.
        int fill = (int) Math.round(TW * Math.min(1.0, (metres / Math.max(1.0, base))));
        if (fill > 0) g.blit(BAR, tx, ty, fill, TH, 0f, 16f, fill, TH, 128, 48);
        // The dead band: hatched, tiled, from the rig's reach to the rod's.
        int cut = (int) Math.round(TW * usable);
        for (int hx = tx + cut; hx < tx + TW; hx += 8) {
            int hw = Math.min(8, tx + TW - hx);
            g.blit(BAR, hx, ty, hw, TH, 0f, 24f, hw, TH, 128, 48);
        }
        if (usable < 1f) g.fill(tx + cut, ty, tx + cut + 1, ty + TH, 0xFFE05A4A);
        // Ticks every five metres of the base reach, brighter at ten.
        for (int m = 5; m < base; m += 5) {
            int px = tx + (int) Math.round(TW * (m / base));
            g.fill(px, ty, px + 1, ty + TH, (m % 10 == 0) ? 0x88FFFFFF : 0x44FFFFFF);
        }

        // The metres, on the plaque above the frame.
        String label = String.format(java.util.Locale.ROOT, "%.1f m", metres);
        int lw = mc.font.width(label);
        int px = x + (FW - 48) / 2, py = y - 18;
        g.blit(BAR, px, py, 48, 16, 0f, 32f, 48, 16, 128, 48);
        g.drawString(mc.font, label, px + (48 - lw) / 2, py + 4, 0xFF3A2A18, false);
        // And what the rod could do, small, at the far end.
        String top = String.format(java.util.Locale.ROOT, "%.0f", base);
        g.drawString(mc.font, top, x + FW + 3, y + 4, 0xFFB08D3C, true);
    }

}
