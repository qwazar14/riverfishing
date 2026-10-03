package com.riverfishing.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * §finder-hud-settings: "Move" on the finder's screen — the corner sounder and the direction dial stand on the game
 * as outlines, each is dragged where it should be, and Done keeps it (Cancel leaves it, Reset puts both back in the
 * corner). What it stores is the player's own setting, not the finder's: {@link FinderHudSettings}.
 */
public class FinderHudEditScreen extends Screen {
    private static final int RIM = 0xFF3C4E47, BODY = 0xC0141C1A, INK = 0xFFB0E8D8, DIM = 0xFF6FA89A, GOLD = 0xFFFFC83C;
    private final Screen back;
    private int stripX, stripY, arrowX, arrowY;
    /** 0 nothing, 1 the sounder, 2 the dial — and where in it the cursor took hold. */
    private int drag;
    private double grabX, grabY;

    public FinderHudEditScreen(Screen back) {
        super(Component.translatable("finder.riverfishing.hud_move"));
        this.back = back;
    }

    @Override
    protected void init() {
        int[] s = FinderHudSettings.stripPos(width, height), a = FinderHudSettings.arrowPos(width, height);
        stripX = s[0]; stripY = s[1]; arrowX = a[0]; arrowY = a[1];
        int bw = 70, y = height / 2 - 10, x = width / 2 - (bw * 3 + 8) / 2;
        addRenderableWidget(Button.builder(Component.translatable("finder.riverfishing.hud_done"), b -> {
            FinderHudSettings.place(stripX, stripY, arrowX, arrowY, width, height);
            FinderHudSettings.save();
            onClose();
        }).bounds(x, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("finder.riverfishing.hud_reset"), b -> {
            FinderHudSettings.reset();
            int[] s0 = FinderHudSettings.stripPos(width, height), a0 = FinderHudSettings.arrowPos(width, height);
            stripX = s0[0]; stripY = s0[1]; arrowX = a0[0]; arrowY = a0[1];
            FinderHudSettings.save();
        }).bounds(x + bw + 4, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 2 * (bw + 4), y, bw, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x60000000);
        String hint = Component.translatable("finder.riverfishing.hud_hint").getString();
        g.text(font, hint, width / 2 - font.width(hint) / 2, height / 2 - 26, INK, true);

        // the sounder: its casing and a line of water, dimmed when it is switched off
        boolean on = FinderHudSettings.showStrip;
        g.fill(stripX, stripY, stripX + ClientHud.STRIP_W, stripY + ClientHud.STRIP_H, drag == 1 ? GOLD : RIM);
        g.fill(stripX + 1, stripY + 1, stripX + ClientHud.STRIP_W - 1, stripY + ClientHud.STRIP_H - 1, BODY);
        g.fill(stripX + 6, stripY + 30, stripX + ClientHud.STRIP_W - 6, stripY + 31, on ? 0xFF7FE9D0 : DIM);
        String strip = Component.translatable(on ? "finder.riverfishing.hud_strip" : "finder.riverfishing.hud_strip_off").getString();
        g.text(font, strip, stripX + ClientHud.STRIP_W / 2 - font.width(strip) / 2, stripY + 12, on ? INK : DIM, true);

        // the dial: its ring and a needle pointing ahead
        on = FinderHudSettings.showArrow;
        int r = FinderHudSettings.DIAL_R;
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(arrowX - hw, arrowY + dy, arrowX + hw + 1, arrowY + dy + 1, drag == 2 ? 0xC0403818 : BODY);
        }
        g.fill(arrowX, arrowY - 8, arrowX + 2, arrowY + 8, on ? GOLD : DIM);
        String arrow = Component.translatable(on ? "finder.riverfishing.hud_arrow" : "finder.riverfishing.hud_arrow_off").getString();
        g.text(font, arrow, arrowX - font.width(arrow) / 2, arrowY + r + 4, on ? GOLD : DIM, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor g) {
        // the game has to show through: the pieces are placed on it
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        double mx = event.x(), my = event.y();
        int r = FinderHudSettings.DIAL_R;
        if ((mx - arrowX) * (mx - arrowX) + (my - arrowY) * (my - arrowY) <= (r + 2) * (r + 2)) {
            drag = 2; grabX = mx - arrowX; grabY = my - arrowY;
            return true;
        }
        if (mx >= stripX && mx < stripX + ClientHud.STRIP_W && my >= stripY && my < stripY + ClientHud.STRIP_H) {
            drag = 1; grabX = mx - stripX; grabY = my - stripY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (drag == 1) {
            stripX = clamp((int) Math.round(event.x() - grabX), 0, width - ClientHud.STRIP_W);
            stripY = clamp((int) Math.round(event.y() - grabY), 0, height - ClientHud.STRIP_H);
            return true;
        }
        if (drag == 2) {
            int r = FinderHudSettings.DIAL_R;
            arrowX = clamp((int) Math.round(event.x() - grabX), r, width - r);
            arrowY = clamp((int) Math.round(event.y() - grabY), r, height - r - FinderHudSettings.DIAL_LABELS);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        drag = 0;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        //? if <26.2 {
        /*minecraft.setScreen(back);
        *///?} else {
        minecraft.setScreenAndShow(back);
        //?}
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
