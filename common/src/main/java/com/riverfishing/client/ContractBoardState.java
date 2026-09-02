package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.ContractItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * §contracts: the board of posts beside the fisherman's counter.
 *
 * <p>Bound to the merchant window exactly the way {@link OrderState} is — the packet lands just before
 * the screen-open packet, the first window claims it, a different window drops it. Drawn as parchment
 * from fills rather than a texture: the posts are all text and icons, and there is no chrome to paint.
 * It stands to the right of the window, or under it when the GUI is too narrow.
 */
public final class ContractBoardState {
    private static final int MERCHANT_W = 276, MERCHANT_H = 166;
    private static final int W = 118, ROW = 34, HEAD = 26;
    private static final int PAPER = 0xFFE3D6B8, EDGE = 0xFF6E5A3C, INK = 0xFF241A0E, INK2 = 0xFF6E5A3C,
            HOVER = 0x38E8B430;

    private static CompoundTag board;
    private static int boundId = -1;

    private ContractBoardState() {}

    public static void accept(CompoundTag t) {
        board = t;
        boundId = -1;
    }

    private static boolean bind() {
        if (board == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int id = mc.player.containerMenu.containerId;
        if (boundId == -1) boundId = id;
        else if (boundId != id) { board = null; return false; }
        return true;
    }

    private static int[] bounds() {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int leftPos = (sw - MERCHANT_W) / 2, topPos = (sh - MERCHANT_H) / 2;
        int n = board.getListOrEmpty("posts").size();
        int h = HEAD + n * ROW + 4;
        int x = leftPos + MERCHANT_W + 4, y = topPos;
        if (x + W > sw - 2) { x = leftPos; y = topPos + MERCHANT_H + 3; }   // no room beside: below
        return new int[]{x, y, h};
    }

    public static void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!bind()) return;
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int[] b = bounds();
        int x = b[0], y = b[1], h = b[2];
        g.fill(x - 1, y - 1, x + W + 1, y + h + 1, EDGE);
        g.fill(x, y, x + W, y + h, PAPER);
        g.text(font, Component.translatable("screen.riverfishing.contract_board.title"), x + 5, y + 5, INK, false);
        g.text(font, Component.translatable("screen.riverfishing.contract_board.rep", board.getIntOr("rep", 0)),
                x + 5, y + 15, INK2, false);

        ListTag posts = board.getListOrEmpty("posts");
        for (int i = 0; i < posts.size(); i++) {
            CompoundTag t = posts.getCompoundOrEmpty(i);
            int ry = y + HEAD + i * ROW;
            if (mouseX >= x && mouseX < x + W && mouseY >= ry && mouseY < ry + ROW) g.fill(x, ry, x + W, ry + ROW, HOVER);
            FishIcon.draw(g, t.getStringOr("Sp", ""), x + 3, ry + 2);
            // The head line, shortened to the count and the fish; the size and the terms wrap under it.
            g.text(font, Component.translatable("journal.riverfishing.contract_short",
                    t.getIntOr("N", 0), Component.translatable("fish.riverfishing." + t.getStringOr("Sp", ""))),
                    x + 22, ry + 3, INK, false);
            StringBuilder sb = new StringBuilder(ContractItem.grams(t.getIntOr("W", 0)));
            for (Component c : ContractItem.terms(t)) sb.append(" · ").append(c.getString());
            var lines = font.split(Component.literal(sb.toString()), W - 44);
            for (int k = 0; k < lines.size() && k < 2; k++) {
                g.text(font, lines.get(k), x + 22, ry + 13 + k * 9, INK2, false);
            }
            ItemStack em = new ItemStack(Items.EMERALD);
            g.fakeItem(em, x + W - 20, ry + 8);
            g.itemDecorations(font, em, x + W - 20, ry + 8, String.valueOf(t.getIntOr("Em", 0)));
        }
    }

    /** A click on a post asks the server for its paper. */
    public static boolean click(double mx, double my) {
        if (board == null) return false;
        int[] b = bounds();
        int x = b[0], y = b[1];
        if (mx < x || mx >= x + W || my < y + HEAD) return false;
        int slot = (int) ((my - y - HEAD) / ROW);
        if (slot < 0 || slot >= board.getListOrEmpty("posts").size()) return false;
        com.riverfishing.network.ModNetwork.toServer(
                new com.riverfishing.network.ContractTakePacket(board.getIntOr("vid", 0), slot));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
        return true;
    }
}

// §ported
