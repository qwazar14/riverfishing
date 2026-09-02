package com.riverfishing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * The on-screen HUD overlays (§immersion): the float-timing cue (#5) and the cast-power bar
 * (§cast-minigame). Driven by Architectury's {@code ClientGuiEvent.RENDER_HUD}, which hands us a
 * {@link GuiGraphicsExtractor} and the frame partial-tick on both loaders.
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
    private static void renderFinderStrip(GuiGraphicsExtractor g, Minecraft mc) {
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
        String depth = FinderState.latest().getCompoundOrEmpty("water").getIntOr("depth", 0) + " m";
        g.text(mc.font, depth, x + 3, y + 3, 0xFF9FE9D0, false);
        }

        // §ledge-arrow: a pointer to the nearest feature you have found, under the strip. Rotated by
        // the difference between where it is and where you face, so it reads like a compass needle:
        // straight up is "walk forward". Only for features already on the map — this finds your way
        // back to a hole, it does not find holes.
        int[] near = FinderState.latest().getIntArray("near").orElse(null);
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
            String label = (picked ? "\u2605 " : "") + Component.translatable("spot.riverfishing." + (near[2] == 0 ? "hole" : "ledge")).getString()
                    + " " + Component.translatable("finder.riverfishing.metres", dist).getString();
            g.text(mc.font, label, ax + 16, ay - 4, 0xFFFFC83C, true);
        }
    }

    private static boolean isFinder(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof com.riverfishing.item.WaterProbeItem probe && !probe.admin();
    }

    public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
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
    private static void renderPumpReel(GuiGraphicsExtractor g, Minecraft mc) {
        // §26.2: Options.hideGui moved onto the Hud itself (mc.gui.hud.isHidden()).
        //? if <26.2 {
        if (mc.player == null || mc.options.hideGui) return;
        //?} else {
        /*if (mc.player == null || mc.gui.hud.isHidden()) return;
        *///?}
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
        int cx = mc.getWindow().getGuiScaledWidth() / 2, y = 30;
        int w = font.width(text);
        g.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 11, 0x66000000);
        // §26.1: drawString/drawCenteredString are gone — it's text(), and centring is on us.
        g.text(font, text, cx - w / 2, y, color, false);

        // §rod-load: the key cue is GONE — no more [ arrow ] under the crosshair naming the binding to
        // hold. The rod is the instrument: the blank bends toward the fish and loads with the pull, so a
        // glyph spelling the answer only repeated what the tackle already shows, and reading a keycap is
        // not fishing. The bindings still work (§fight-keys, the quiet override) — nothing advertises them.
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
        // §cast-bar-cut (round 6): mirror the server's ACTUAL weight curve — bench-chosen grams,
        // in-window 85..100%, sqrt collapse below — instead of the stale fixed-mass 0.55 cut.
        if (rig.getItem() instanceof com.riverfishing.item.RigItem && rodType.castWeightMax() > 0) {
            double wG = com.riverfishing.rig.RigData.effectiveWeightG(rig);
            double minW = rodType.castWeightMin(), maxW = rodType.castWeightMax();
            double f = wG >= minW
                    ? 0.85 + 0.15 * Math.min(1.0, Math.max(0.0, (wG - minW) / Math.max(1.0, maxW - minW)))
                    : 0.85 * Math.sqrt(Math.max(0.10, wG / Math.max(1.0, minW)));
            usable = (float) Math.min(1.0, Math.max(0.30, f));
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
