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

import java.util.ArrayList;
import java.util.List;

/**
 * §contracts: the board of posts beside the fisherman's counter.
 *
 * <p>Bound to the merchant window exactly the way {@link OrderState} is — the packet lands just before
 * the screen-open packet, the first window claims it, a different window drops it. Drawn as parchment
 * from fills rather than a texture: the posts are all text and icons, and there is no chrome to paint.
 * It stands to the right of the window, or under it when the GUI is too narrow.
 *
 * <p>Every term of a post is a line of its own — a post is as tall as it needs to be, never folded —
 * and the click uses the same layout the draw did.
 */
public final class ContractBoardState {
    private static final int MERCHANT_W = 276, MERCHANT_H = 166;
    private static final int W = 124, HEAD = 26, LINE = 9;
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

    /** The lines under a post's head line: the size bar, then every term. */
    private static List<Component> lines(CompoundTag t) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(ContractItem.grams(t.getIntOr("W", 0))));
        out.addAll(ContractItem.terms(t));
        // §board-taken: when this post goes — the board turns over with the world day, and the client
        // has the clock. Real minutes, because that is how long the player has to decide.
        Minecraft mc = Minecraft.getInstance();
        long ticks = mc.level == null ? 0 : 24000L - (mc.level.getOverworldClockTime() % 24000L);
        out.add(Component.translatable("screen.riverfishing.contract_board.refresh", (ticks + 1199) / 1200));
        return out;
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
        List<List<Component>> body = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            List<Component> ls = lines(list.getCompoundOrEmpty(i));
            body.add(ls);
            int h = 13 + LINE * ls.size() + 3;
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
            if (!taken && mouseX >= x && mouseX < x + W && mouseY >= ry && mouseY < ry + rh) g.fill(x, ry, x + W, ry + rh, HOVER);
            FishIcon.draw(g, t.getStringOr("Sp", ""), x + 3, ry + 2);
            g.text(font, Component.translatable("journal.riverfishing.contract_short",
                    t.getIntOr("N", 0), Component.translatable("fish.riverfishing." + t.getStringOr("Sp", ""))),
                    x + 22, ry + 3, INK, false);
            int ly = ry + 13;
            for (Component c : body.get(i)) {
                g.text(font, c, x + 22, ly, taken ? TAKEN : INK2, false);
                ly += LINE;
            }
            if (taken) {
                g.fill(x, ry, x + W, ry + rh, 0x60E3D6B8);       // greyed: yours already, or was
                g.text(font, Component.translatable("screen.riverfishing.contract_board.taken"),
                        x + W - 20 - font.width(Component.translatable("screen.riverfishing.contract_board.taken")), ry + 3, TAKEN, false);
                continue;                                      // no emerald on a post you cannot take
            }
            ItemStack em = new ItemStack(Items.EMERALD);
            g.fakeItem(em, x + W - 20, ry + 2);
            g.itemDecorations(font, em, x + W - 20, ry + 2, String.valueOf(t.getIntOr("Em", 0)));
        }
    }

    /** A click on a post asks the server for its paper. */
    public static boolean click(double mx, double my) {
        if (board == null) return false;
        int[] o = origin();
        if (mx < o[0] || mx >= o[0] + W) return false;
        for (int i = 0; i < posts.length; i++) {
            if (my < posts[i][0] || my >= posts[i][0] + posts[i][1]) continue;
            if (board.getListOrEmpty("posts").getCompoundOrEmpty(i).getBooleanOr("taken", false)) return true;   // a taken post swallows the click
            board.getListOrEmpty("posts").getCompoundOrEmpty(i).putBoolean("taken", true);   // greyed at once; the server decides for real
            com.riverfishing.network.ModNetwork.toServer(
                    new com.riverfishing.network.ContractTakePacket(board.getIntOr("vid", 0), i));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            return true;
        }
        return false;
    }
}
