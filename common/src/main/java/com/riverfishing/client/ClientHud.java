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
     *
     * <p>§finder-strip (1.1.0): drawn as the instrument's own little screen. The water darkens with depth,
     * the bed is its own colour with the bright line a real echo makes where the sound hits it, and the fish
     * are fish — the sounder's arches, as many as the living water says are there, amber where they hunt —
     * instead of one line per species the water could hold. It glides between soundings rather than
     * jumping a column a second, slides in when the finder comes out, and the needle under it turns
     * rather than snapping.
     */
    static final int STRIP_W = 132, STRIP_H = 78;
    private static final int STRIP_STEP = 2;
    private static final int FISH = 0xFF6FF5CF, HUNTER = 0xFFFFB347, SURFACE_LINE = 0xFF7FE9D0;
    private static float stripIn, shownDepth = -1, shownScale = 6, needle = Float.NaN;
    private static long stripFrame;

    private static void renderFinderStrip(GuiGraphicsExtractor g, Minecraft mc) {
        if (mc.player == null) return;
        boolean held = isFinder(mc.player.getMainHandItem()) || isFinder(mc.player.getOffhandItem());
        if (!held) {
            FinderState.clear();
            stripIn = 0;
            shownDepth = -1;
            needle = Float.NaN;
            return;
        }
        java.util.List<FinderState.Col> trace = FinderState.trace();
        boolean live = FinderState.fresh() && !trace.isEmpty();

        long now = net.minecraft.util.Util.getMillis();
        float dt = Math.min(0.1f, (now - stripFrame) / 1000f);
        stripFrame = now;
        float k = 1f - (float) Math.exp(-dt * 10f);
        stripIn += ((live ? 1f : 0f) - stripIn) * k;
        if (!live && stripIn < 0.01f && ClientSoundings.target() == null) return;

        // §finder-hud-settings: each piece can be switched off and put anywhere (the finder screen's lower keys);
        // in its own corner the sounder still slides in from the edge
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int[] at = FinderHudSettings.stripPos(sw, sh), dial = FinderHudSettings.arrowPos(sw, sh);
        int x = at[0] + (FinderHudSettings.stripMoved() ? 0 : Math.round((1f - stripIn) * (STRIP_W + 12)));
        if (FinderHudSettings.showStrip && !trace.isEmpty() && stripIn > 0.01f) drawStrip(g, mc, x, at[1], trace, k, now);
        if (FinderHudSettings.showArrow) drawNeedle(g, mc, dial[0], dial[1], k);
    }

    private static void drawStrip(GuiGraphicsExtractor g, Minecraft mc, int x, int y, java.util.List<FinderState.Col> trace,
                                  float k, long now) {
        // the casing: a rounded dark body with a lit rim
        rounded(g, x, y, STRIP_W, STRIP_H, 0xFF3C4E47);
        rounded(g, x + 1, y + 1, STRIP_W - 2, STRIP_H - 2, 0xF0141C1A);
        g.fill(x + 3, y + 1, x + STRIP_W - 3, y + 2, 0x30FFFFFF);

        FinderState.Col last = trace.get(trace.size() - 1);
        if (shownDepth < 0) shownDepth = last.depth();
        shownDepth += (last.depth() - shownDepth) * k;
        int scale = 6;
        for (FinderState.Col c : trace) scale = Math.max(scale, c.depth() + 1);
        shownScale += (scale - shownScale) * k;

        // the header: how deep where you aim, which way it is going, what the bottom is, and the power lamp
        String depth = Component.translatable("finder.riverfishing.metres", Math.round(shownDepth)).getString();
        g.text(mc.font, depth, x + 5, y + 4, 0xFFD6FFF2, true);
        int dx = x + 7 + mc.font.width(depth);
        if (trace.size() >= 2) {
            int prev = trace.get(trace.size() - 2).depth();
            if (prev != last.depth()) {
                g.text(mc.font, last.depth() > prev ? "↓" : "↑", dx, y + 4,
                        last.depth() > prev ? 0xFF6FB0F5 : 0xFFB0F56F, true);
            }
        }
        String bed = Component.translatable("bed.riverfishing." + FinderScreen.bedKey(last.bed())).getString();
        g.text(mc.font, bed, x + STRIP_W - 12 - mc.font.width(bed), y + 4,
                FinderScreen.lighten(FinderScreen.bedColour(last.bed())), true);
        int lamp = 0x40 + (int) (0xBF * (0.5 + 0.5 * Math.sin(now / 260.0)));
        g.fill(x + STRIP_W - 8, y + 6, x + STRIP_W - 5, y + 9, (lamp << 24) | 0x5CF08A);

        // the screen
        int sx = x + 4, sy = y + 15, sw = STRIP_W - 8, sh = STRIP_H - 19;
        g.fill(sx - 1, sy - 1, sx + sw + 1, sy + sh + 1, 0xFF070B0A);
        for (int r = 0; r < sh; r++) {   // water, darkening with depth
            g.fill(sx, sy + r, sx + sw, sy + r + 1, lerp(0xFF12404A, 0xFF06161C, r / (float) sh));
        }
        float px = sh / shownScale;
        int gridStep = shownScale <= 7 ? 1 : shownScale <= 14 ? 2 : shownScale <= 35 ? 5 : 10;
        for (int d = gridStep; d < shownScale; d += gridStep) {
            g.fill(sx, sy + Math.round(d * px), sx + sw, sy + Math.round(d * px) + 1, 0x1C40E0B0);
        }

        g.enableScissor(sx, sy, sx + sw, sy + sh);
        int cols = trace.size();
        int shift = Math.round((1f - FinderState.slide()) * STRIP_STEP);
        for (int i = 0; i < cols; i++) {
            FinderState.Col c = trace.get(i);
            int cx = sx + sw - (cols - i) * STRIP_STEP + shift;
            if (cx + STRIP_STEP < sx) continue;
            int bedC = FinderScreen.bedColour(c.bed());
            float from = i > 0 ? trace.get(i - 1).depth() : c.depth();
            for (int p = 0; p < STRIP_STEP; p++) {
                // the bed slopes from the last sounding to this one instead of stepping a metre at a time
                int floorY = sy + Math.round((from + (c.depth() - from) * (p + 1) / (float) STRIP_STEP) * px);
                // the bright line of the echo, then its colour, fading into the ground under it
                g.fill(cx + p, floorY + 3, cx + p + 1, sy + sh, lerp(bedC, 0xFF000000, 0.55f));
                g.fill(cx + p, floorY + 1, cx + p + 1, floorY + 3, bedC);
                g.fill(cx + p, floorY, cx + p + 1, floorY + 1, FinderScreen.lighten(bedC));
            }
        }
        for (int i = 0; i < cols; i++) {
            FinderState.Col c = trace.get(i);
            int cx = sx + sw - (cols - i) * STRIP_STEP + shift;
            if (cx + 8 < sx) continue;
            int floorY = sy + Math.round(c.depth() * px);
            // §finder-strip-calm: at most one echo a column, and only now and then — a sounder shows a fish
            // where its cone crossed one, not every fish the water holds. The chance grows with the log of
            // everything heard; which fish it is goes by its share, so forty roach make a busy strip and a
            // lone pike the odd amber arch. (Every species in every column was a wall of arches.)
            int heard = 0;
            for (int n : c.n()) heard += Math.max(1, n);   // 0 = the old engine's "may be here": count it once
            if (heard == 0) continue;
            double chance = Math.min(0.28, 0.03 + 0.035 * (Math.log(1 + heard) / Math.log(2)));
            long h = mix(c.seq() * 0x9E3779B97F4A7C15L);
            if ((h >>> 11) % 1000 >= chance * 1000) continue;
            h = mix(h);
            long r = (h >>> 11) % heard;
            int j = 0;
            for (int acc = 0; j < c.n().length; j++) {
                acc += Math.max(1, c.n()[j]);
                if (r < acc) break;
            }
            if (j >= c.n().length) continue;
            double lo = c.dmin()[j], hi = Math.min(c.dmax()[j], c.depth() - 0.6);
            if (hi < lo) lo = hi = Math.max(0.3, c.depth() - 0.8);
            // nearer the middle of its band than its edges: two draws averaged
            double u = ((((h >>> 32) & 0xFFFF) + (mix(h) & 0xFFFF)) / 131070.0);
            double d = lo + (hi - lo) * u;
            int fy = sy + Math.round((float) (d * px));
            int w = c.pred()[j] ? 7 : 5;
            if (fy + 2 >= floorY || fy <= sy + 1) continue;
            arch(g, cx - w / 2, fy, w, c.pred()[j] ? HUNTER : FISH);
        }
        g.disableScissor();
        g.fill(sx, sy, sx + sw, sy + 1, SURFACE_LINE);
        String deepest = String.valueOf(Math.round(shownScale - 1));
        g.text(mc.font, deepest, sx + sw - mc.font.width(deepest) - 1, sy + sh - 9, 0x7040E0B0, false);
    }

    /** A sounder's fish: the echo of a body crossing the cone, an arch brightest at its crown. */
    private static void arch(GuiGraphicsExtractor g, int x, int y, int w, int colour) {
        g.fill(x + 1, y, x + w - 1, y + 1, colour);
        g.fill(x, y + 1, x + 1, y + 2, colour & 0xA0FFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + 2, colour & 0xA0FFFFFF);
    }

    /** A rectangle with its corners taken off — the casing's shape at HUD size. */
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        g.fill(x + 2, y, x + w - 2, y + 1, colour);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, colour);
        g.fill(x, y + 2, x + w, y + h - 2, colour);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, colour);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, colour);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gr = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    /**
     * §ledge-arrow: a pointer to the nearest feature you have found, under the strip — a compass dial now.
     * Rotated by the difference between where it is and where you face, so straight up is "walk forward",
     * and eased round rather than snapped. Only for features already on the map — this finds your way
     * back to a hole, it does not find holes.
     */
    private static void drawNeedle(GuiGraphicsExtractor g, Minecraft mc, int ax, int ay, float k) {
        int[] near = FinderState.latest().getIntArray("near").orElse(new int[0]);
        // §arrow-target: a mark picked on the chart outranks the nearest one, and it is measured
        // from where you stand right now rather than from the last sounding — you may have walked.
        boolean picked = false;
        Long target = ClientSoundings.target();
        if (target != null) {
            Byte kind = ClientSoundings.spots().get(target);
            near = new int[]{ClientSoundings.keyX(target) - mc.player.getBlockX(),
                    ClientSoundings.keyZ(target) - mc.player.getBlockZ(), kind == null ? 1 : kind};
            picked = true;
        }
        if (near == null || near.length != 3) {
            needle = Float.NaN;
            return;
        }
        double toSpot = Math.toDegrees(Math.atan2(-near[0], near[1]));    // yaw the spot lies at
        float rel = (float) Math.toRadians(net.minecraft.util.Mth.wrapDegrees(toSpot - mc.player.getYRot()));
        if (Float.isNaN(needle)) needle = rel;
        float turn = (float) Math.atan2(Math.sin(rel - needle), Math.cos(rel - needle));   // the short way round
        needle += turn * k;

        int r = 13;
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(ax - hw, ay + dy, ax + hw + 1, ay + dy + 1, 0xE0141C1A);
        }
        for (int a = 0; a < 64; a++) {
            double t = a * Math.PI / 32;
            int px = (int) Math.round(ax + Math.sin(t) * r), py = (int) Math.round(ay - Math.cos(t) * r);
            g.fill(px, py, px + 1, py + 1, 0xFF3C4E47);
        }
        g.fill(ax, ay - r + 1, ax + 1, ay - r + 4, SURFACE_LINE);   // forward
        double sx = Math.sin(needle), sy = -Math.cos(needle);
        for (int kk = -8; kk <= 8; kk++) {
            int px = (int) Math.round(ax + sx * kk), py = (int) Math.round(ay + sy * kk);
            g.fill(px, py, px + 2, py + 2, kk > 0 ? 0xFFFFC83C : 0xFF8A6A20);
        }
        // the head: two short strokes back from the tip
        for (int kk = 0; kk < 5; kk++) {
            double bx = sx * (8 - kk), by = sy * (8 - kk);
            int lx = (int) Math.round(ax + bx + sy * kk), ly = (int) Math.round(ay + by - sx * kk);
            int rx = (int) Math.round(ax + bx - sy * kk), ry = (int) Math.round(ay + by + sx * kk);
            g.fill(lx, ly, lx + 2, ly + 2, 0xFFFFC83C);
            g.fill(rx, ry, rx + 2, ry + 2, 0xFFFFC83C);
        }
        int dist = (int) Math.round(Math.sqrt((double) near[0] * near[0] + (double) near[1] * near[1]));
        // §arrow-label: two lines under the dial, centred — what it is, then how far.
        String kind = (picked ? "★ " : "") + Component.translatable("spot.riverfishing." + (near[2] == 0 ? "hole" : "ledge")).getString();
        String range = Component.translatable("finder.riverfishing.metres", dist).getString();
        g.text(mc.font, kind, ax - mc.font.width(kind) / 2, ay + r + 4, 0xFFFFC83C, true);
        g.text(mc.font, range, ax - mc.font.width(range) / 2, ay + r + 14, 0xFFFFC83C, true);
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
        // §ice-rhythm: the jig gauge sits where the charge bar would — that one yields (below).
        JigClient.render(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), partialTick);
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
        /*if (mc.player == null || mc.options.hideGui) return;
        *///?} else {
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        //?}
        ClientLineState.Line l = ClientLineState.lines().get(mc.player.getId());
        if (l == null || !l.fighting) return;
        String key;
        int color;
        byte move = l.move;   // §fight-moves: a move has its own answer
        if (l.smoothTension > 0.85f || move == com.riverfishing.fishing.FightMoves.WEEDED
                || (move == com.riverfishing.fishing.FightMoves.TORPEDO && l.running)) {
            key = "hud.riverfishing.drag_now"; color = 0xFFFF5040;
        } else if (move == com.riverfishing.fishing.FightMoves.SULK) {
            key = "hud.riverfishing.lift"; color = 0xFFFFC850;
        } else if (move == com.riverfishing.fishing.FightMoves.CHARGE || move == com.riverfishing.fishing.FightMoves.CHARGE_SLACK) {
            key = "hud.riverfishing.reel_fast"; color = 0xFF7CE07C;
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
    private static final net.minecraft.resources.Identifier BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    /**
     * §cast-metres: the cast-power gauge, in METRES.
     *
     * <p>It was a bar that filled: fifty percent, eighty-five, a colour. Nothing on it said how far the
     * line would go, and "how far" is the only thing an angler charging a cast wants to know. The
     * number printed above the gauge now is the real throw — the same {@code castDistance} the server
     * lands the line at, read off the same rod and rig on this side, so it cannot disagree.
     *
     * <p>§cast-bar: drawn off the sheet the author painted (§gui-art) — an oak frame with a brass
     * rim, the charge as a lit tube from green through amber to red, the dead band an under-loaded
     * rig cannot reach hatched in red, and the metres on a parchment plaque. Ticks every five metres,
     * because the scale is metres now and a tick at "fifty percent" would be a tick at nothing.
     */
    private static void renderCastPower(GuiGraphicsExtractor g, Minecraft mc) {
        Player player = mc.player;
        if (player == null || !player.isUsingItem()) return;
        if (!(player.getUseItem().getItem() instanceof com.riverfishing.item.RodItem rodItem)) return;
        // §spin-charge (2.3): lure rods now charge-and-cast too, so they show the bar — but only while
        // charging. Once a line is out, holding is a RETRIEVE, not a charge, so hide it (next line).
        if (ClientLineState.active()) return;
        if (JigClient.isActive()) return;   // §ice-rhythm: the jig's hold is a rhythm, not a charge

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
        // §gui-art: the author's sheet, measured. Frame 256x34 at (0,0); the trough inside it is
        // 230x14 at (+13,+10) — and the fill sprite is exactly that 230 wide, so nothing is ever
        // scaled. The bottom edge stays where the old 120x16 frame's was (sh-54), which is what keeps
        // a 34-tall frame clear of the health row.
        final int FW = 256, FH = 34, TW = 230, TH = 14;
        int x = (sw - FW) / 2, y = sh - 88;
        int tx = x + 13, ty = y + 10;                        // the recess the sheet leaves for the fill
        // §gui-180: the sheet is painted 256 wide and drawn 180 wide — scaled about the frame's own
        // centre, so every rect below stays in the sheet's numbers and only this one factor moves.
        final float S = 180f / FW;
        float ccx = x + FW / 2f, ccy = y + FH / 2f;
        g.pose().pushMatrix();
        g.pose().translate(ccx, ccy);
        g.pose().scale(S, S);
        g.pose().translate(-ccx, -ccy);

        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, x, y, 0f, 0f, FW, FH, 256, 96);
        // The charge, clipped to the metres: a fraction of the base reach, so the tube fills to where
        // the line will land on the gauge's own scale.
        int fill = (int) Math.round(TW * Math.min(1.0, (metres / Math.max(1.0, base))));
        if (fill > 0) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, tx, ty, 0f, 36f, fill, TH, 256, 96);
        // The dead band: hatched, tiled, from the rig's reach to the rod's.
        int cut = (int) Math.round(TW * usable);
        // §gui-art: the blocked chip is 18 wide, so the tile step is 18 — a step that is not the
        // chip's own width either seams or overlaps the hatching.
        for (int hx = tx + cut; hx < tx + TW; hx += 18) {
            int hw = Math.min(18, tx + TW - hx);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, hx, ty, 234f, 36f, hw, TH, 256, 96);
        }
        if (usable < 1f) g.fill(tx + cut, ty, tx + cut + 1, ty + TH, 0xFFE05A4A);
        // Ticks every five metres of the base reach, brighter at ten.
        for (int m = 5; m < base; m += 5) {
            int px = tx + (int) Math.round(TW * (m / base));
            g.fill(px, ty, px + 1, ty + TH, (m % 10 == 0) ? 0x88FFFFFF : 0x44FFFFFF);
        }

        // The plaque rides with the frame — it is art, and art scales.
        int px = x + (FW - 100) / 2, py = y - 44;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, px, py, 0f, 51f, 100, 38, 256, 96);
        g.pose().popMatrix();

        // §gui-180: the writing does NOT scale. The metres in bold, at the font's own size, centred
        // on the plaque where the scale put it — the parchment is full width only on rows 7..30, so
        // the line sits on the middle of that band.
        Component label = Component.literal(String.format(java.util.Locale.ROOT, "%.1f m", metres))
                .withStyle(net.minecraft.ChatFormatting.BOLD);
        int lw = mc.font.width(label);
        float signCy = ccy + (py + 19 - ccy) * S;
        g.text(mc.font, label, (int) (ccx - lw / 2f), (int) (signCy - 4f), 0xFF3A2A18, false);
        // And what the rod could do, small, past the frame's right end — which a 180-wide gauge has
        // room for again.
        String top = String.format(java.util.Locale.ROOT, "%.0f", base);
        g.text(mc.font, top, (int) (ccx + (x + FW - ccx) * S) + 3, (int) (ccy - 4f), 0xFFB08D3C, true);
    }

}
