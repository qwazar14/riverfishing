package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.ContractItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * §contracts / §board-3: the board of posts beside the fisherman's counter.
 *
 * <p>Bound to the merchant window exactly the way {@link OrderState} is — the packet lands just before
 * the screen-open packet, the first window claims it, a different window drops it — and re-sent by the
 * server after every click, so what is greyed is what the server says is taken.
 *
 * <p>Each post: a head line with the fish, then the size bar and every term wrapped to the board's
 * width, then a foot line — when the post leaves the board on the left, the pay on the right. Nothing
 * shares a line with the emerald, so nothing can run under it.
 */
public final class ContractBoardState {
    private static final int MERCHANT_W = 276, MERCHANT_H = 166;
    private static final int W = 150, HEAD = 26, LINE = 9, FOOT = 18, TEXT_W = W - 26;
    private static final int PAPER = 0xFFE3D6B8, EDGE = 0xFF6E5A3C, INK = 0xFF241A0E, INK2 = 0xFF6E5A3C,
            HOVER = 0x38E8B430, TAKEN = 0xFFA89880;

    private static CompoundTag board;
    private static int boundId = -1;
    /** {top, height} of each post from the last draw, so a click is tested against what was drawn. */
    private static int[][] posts = new int[0][];

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

    /** The lines under a post's head line, wrapped: the size bar, then every term. */
    private static List<FormattedCharSequence> lines(CompoundTag t) {
        var font = Minecraft.getInstance().font;
        List<FormattedCharSequence> out = new ArrayList<>();
        if (ContractItem.isFry(t)) return out;   // §e: a fry post is its head line and its foot
        out.addAll(font.split(Component.literal(ContractItem.grams(t.getIntOr("W", 0))), TEXT_W));
        for (Component c : ContractItem.terms(t)) out.addAll(font.split(c, TEXT_W));
        return out;
    }

    /** When this post leaves the board: days while there are days, minutes on its last day. */
    private static Component leaves(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        long time = mc.level == null ? 0 : mc.level.getOverworldClockTime();
        long daysLeft = t.getLongOr("Exp", 0L) + 1 - time / 24000L;
        if (daysLeft > 1) return Component.translatable("screen.riverfishing.contract_board.refresh_days", daysLeft);
        long ticks = 24000L - (time % 24000L);
        return Component.translatable("screen.riverfishing.contract_board.refresh", (ticks + 1199) / 1200);
    }

    private static int[] origin() {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int leftPos = (sw - MERCHANT_W) / 2, topPos = (sh - MERCHANT_H) / 2;
        int x = leftPos + MERCHANT_W + 4, y = topPos;
        if (x + W > sw - 2) { x = leftPos; y = topPos + MERCHANT_H + 3; }   // no room beside: below
        return new int[]{x, y};
    }

    public static void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!bind()) return;
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int[] o = origin();
        int x = o[0], y = o[1];
        ListTag list = board.getListOrEmpty("posts");
        posts = new int[list.size()][];
        int total = HEAD + 4;
        List<List<FormattedCharSequence>> body = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            List<FormattedCharSequence> ls = lines(list.getCompoundOrEmpty(i));
            body.add(ls);
            int h = 13 + LINE * ls.size() + FOOT;
            posts[i] = new int[]{y + total, h};
            total += h;
        }
        g.fill(x - 1, y - 1, x + W + 1, y + total + 1, EDGE);
        g.fill(x, y, x + W, y + total, PAPER);
        g.text(font, Component.translatable("screen.riverfishing.contract_board.title"), x + 5, y + 5, INK, false);
        g.text(font, Component.translatable("screen.riverfishing.contract_board.rep", board.getIntOr("rep", 0)),
                x + 5, y + 15, INK2, false);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            int ry = posts[i][0], rh = posts[i][1];
            boolean taken = t.getBooleanOr("taken", false);
            if (i > 0) g.fill(x + 4, ry, x + W - 4, ry + 1, 0x30000000);
            if (!taken && mouseX >= x && mouseX < x + W && mouseY >= ry && mouseY < ry + rh) g.fill(x, ry, x + W, ry + rh, HOVER);
            FishIcon.draw(g, t.getStringOr("Sp", ""), x + 3, ry + 3);
            g.text(font, Component.translatable("journal.riverfishing.contract_short",
                    t.getIntOr("N", 0), Component.translatable("fish.riverfishing." + t.getStringOr("Sp", ""))),
                    x + 22, ry + 4, taken ? TAKEN : INK, false);
            int ly = ry + 14;
            for (FormattedCharSequence c : body.get(i)) {
                g.text(font, c, x + 22, ly, taken ? TAKEN : INK2, false);
                ly += LINE;
            }
            // The foot: when it leaves, and what it pays — or that it is yours already.
            int fy = ry + rh - FOOT + 2;
            g.text(font, leaves(t), x + 22, fy + 4, TAKEN, false);
            if (taken) {
                Component tk = Component.translatable("screen.riverfishing.contract_board.taken");
                g.text(font, tk, x + W - 5 - font.width(tk), fy + 4, TAKEN, false);
                g.fill(x, ry, x + W, ry + rh, 0x50E3D6B8);       // greyed: yours already, or was
            } else {
                ItemStack em = new ItemStack(Items.EMERALD);
                g.fakeItem(em, x + W - 21, fy);
                g.itemDecorations(font, em, x + W - 21, fy, String.valueOf(t.getIntOr("Em", 0)));
            }
        }
    }

    /** A click on a post asks the server for its paper; the server answers with the board again. */
    public static boolean click(double mx, double my) {
        if (board == null) return false;
        int[] o = origin();
        if (mx < o[0] || mx >= o[0] + W) return false;
        for (int i = 0; i < posts.length; i++) {
            if (my < posts[i][0] || my >= posts[i][0] + posts[i][1]) continue;
            CompoundTag post = board.getListOrEmpty("posts").getCompoundOrEmpty(i);
            if (post.getBooleanOr("taken", false)) return true;           // a taken post swallows the click
            post.putBoolean("taken", true);                       // greyed at once; the server's board follows
            com.riverfishing.network.ModNetwork.toServer(
                    new com.riverfishing.network.ContractTakePacket(board.getIntOr("vid", 0), i));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            return true;
        }
        return false;
    }
}
