package com.riverfishing.client;

import com.riverfishing.network.CullListPacket;
import com.riverfishing.network.CullPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * §cull (0.7.0): pick the species to take out of this water.
 *
 * <p>Two clicks, never one. The first selects and turns the row into a question; only the second does
 * anything. A creative tool that empties a lake on a mis-click is a tool people lose worlds to, and the
 * confirmation costs a quarter of a second.
 *
 * <p>Rows for species already removed are shown struck through and put them BACK when confirmed, so the
 * screen is the undo as well as the do.
 */
public class CullScreen extends Screen {
    private static final int ROW = 14;
    private static final int VISIBLE = 12;

    private final BlockPos water;
    private final List<ResourceLocation> species;
    private final List<Boolean> culled;

    private int scroll;
    private int selected = -1;

    private CullScreen(CullListPacket p) {
        super(Component.translatable("gui.riverfishing.cull_title"));
        this.water = p.water;
        this.species = p.species;
        this.culled = new java.util.ArrayList<>(p.culled);
    }

    public static void open(CullListPacket p) {
        Minecraft.getInstance().setScreen(new CullScreen(p));
    }

    private int listTop() {
        return 40;
    }

    private int rowsShown() {
        return Math.min(VISIBLE, species.size());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);   // §1.20.1: no blur pass to skip, and a one-argument call
        int cx = width / 2;
        g.drawCenteredString(font, title, cx, 14, 0xFFE8E4D0);
        g.drawCenteredString(font, Component.translatable("gui.riverfishing.cull_where",
                water.getX(), water.getY(), water.getZ()).withStyle(ChatFormatting.DARK_GRAY), cx, 26, 0xFF808080);

        int top = listTop();
        int w = 220;
        g.fill(cx - w / 2 - 2, top - 2, cx + w / 2 + 2, top + rowsShown() * ROW + 2, 0xC0000000);
        for (int i = 0; i < rowsShown(); i++) {
            int idx = i + scroll;
            if (idx >= species.size()) break;
            int y = top + i * ROW;
            boolean hover = mouseX >= cx - w / 2 && mouseX <= cx + w / 2 && mouseY >= y && mouseY < y + ROW;
            boolean sel = idx == selected;
            if (sel) {
                g.fill(cx - w / 2, y, cx + w / 2, y + ROW, 0x80C03020);
            } else if (hover) {
                g.fill(cx - w / 2, y, cx + w / 2, y + ROW, 0x40FFFFFF);
            }
            Component name = Component.translatable("item.riverfishing." + species.get(idx).getPath());
            Component line = culled.get(idx)
                    ? name.copy().withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_GRAY)
                    : name.copy().withStyle(ChatFormatting.WHITE);
            g.drawString(font, line, cx - w / 2 + 6, y + 3, 0xFFFFFFFF, false);
            if (sel) {
                Component ask = Component.translatable(culled.get(idx)
                        ? "gui.riverfishing.cull_confirm_back" : "gui.riverfishing.cull_confirm");
                g.drawString(font, ask.copy().withStyle(ChatFormatting.YELLOW),
                        cx + w / 2 - font.width(ask) - 6, y + 3, 0xFFFFFF55, false);
            }
        }
        if (species.size() > VISIBLE) {
            g.drawCenteredString(font, Component.literal((scroll + 1) + "–"
                    + Math.min(species.size(), scroll + VISIBLE) + " / " + species.size()),
                    cx, top + rowsShown() * ROW + 6, 0xFF808080);
        }
        g.drawCenteredString(font, Component.translatable("gui.riverfishing.cull_scope")
                .withStyle(ChatFormatting.GRAY), cx, height - 28, 0xFF9A9A9A);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = width / 2, w = 220, top = listTop();
        for (int i = 0; i < rowsShown(); i++) {
            int idx = i + scroll;
            if (idx >= species.size()) break;
            int y = top + i * ROW;
            if (mx < cx - w / 2.0 || mx > cx + w / 2.0 || my < y || my >= y + ROW) continue;
            if (selected == idx) {
                // Second click on the same row: do it.
                boolean remove = !culled.get(idx);
                ModNetwork.toServer(new CullPacket(water, species.get(idx), remove));
                culled.set(idx, remove);
                selected = -1;
            } else {
                selected = idx;
            }
            return true;
        }
        selected = -1;   // clicking anywhere else cancels the pending confirmation
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (species.size() > VISIBLE) {
            scroll = Math.max(0, Math.min(species.size() - VISIBLE, scroll - (int) Math.signum(delta)));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
