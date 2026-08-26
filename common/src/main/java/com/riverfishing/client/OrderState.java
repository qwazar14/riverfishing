package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.network.OrderPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * §order-panel: the board that hangs over the fisherman's counter.
 *
 * <p>The art is one texture and everything on it is drawn live: the fish is the species the server
 * named, the emerald carries the real pay, and the caption is in the player's language. A texture can
 * hold none of the three, so the PNG is chrome only and this places the rest into it.
 *
 * <p>The binding is the only clever part, and it is deliberately not clever. The packet arrives on the
 * same connection just BEFORE the screen-open packet, so the order is always here before there is a
 * screen to draw it on; the first merchant window to appear after it claims it by container id, and any
 * later window with a different id clears it. That is what stops a librarian opened after a fisherman
 * from wearing the fisherman's sign, with no timer to tune and nothing to clean up on disconnect.
 */
public final class OrderState {
    private static final Identifier PANEL = RiverFishing.id("textures/gui/order_panel.png");
    /** The sign occupies the top 48 rows of a 256x64 texture — see tools/gen_order_panel.py. */
    private static final int W = 256, H = 48;

    private static Identifier species;
    private static int pay, base, tier;
    private static int boundId = -1;                 // -1: arrived, not yet claimed by a window

    private OrderState() {}

    public static void accept(OrderPacket p) {
        species = p.species();
        pay = p.pay();
        base = p.base();
        tier = p.tier();
        boundId = -1;
    }

    private static boolean bind() {
        if (species == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int id = mc.player.containerMenu.containerId;
        if (boundId == -1) {
            boundId = id;
        } else if (boundId != id) {
            species = null;                          // a different counter — this order is not its
            return false;
        }
        return true;
    }

    /**
     * The vanilla merchant window, straight out of its constructor. Read rather than guessed, and kept
     * here rather than shadowed: {@code leftPos} lives on the superclass, and a shadow of an inherited
     * field has to survive remapping before an injector is ever consulted — when it does not, the client
     * does not start. This needs no name from the game at all.
     */
    private static final int MERCHANT_W = 276, MERCHANT_H = 166;

    /** Draw the sign above the merchant window, or below it when there is no room above. */
    public static void draw(GuiGraphicsExtractor g) {
        if (!bind()) return;
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        // The same arithmetic AbstractContainerScreen.init does, for the same reason it does it.
        int leftPos = (mc.getWindow().getGuiScaledWidth() - MERCHANT_W) / 2;
        int topPos = (mc.getWindow().getGuiScaledHeight() - MERCHANT_H) / 2;
        int x = leftPos + (MERCHANT_W - W) / 2;
        int y = topPos - H - 3;
        if (y < 2) y = topPos + MERCHANT_H + 3;      // a short window on a small GUI scale

        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PANEL,
                x, y, 0f, 0f, W, H, W, H, 256, 64);

        // The trade row, into the two gilded wells the texture leaves for it.
        FishIcon.draw(g, species.getPath(), x + 21, y + 16);
        // No emerald when this stall cannot take the fish — an empty well is honest, and the caption
        // below says who can.
        if (pay > 0) {
            ItemStack em = new ItemStack(Items.EMERALD);
            g.fakeItem(em, x + 63, y + 16);
            g.itemDecorations(font, em, x + 63, y + 16, String.valueOf(pay));
        }

        // The parchment. Two lines, because the break between them is a design decision rather than
        // whatever the wrap happens to do: the title holds still and the species line moves.
        //
        // ENGRAVED, not just dark: one ink-coloured pass offset a pixel down-right under a lighter pass
        // on top. Small dark type on a light ground reads badly at GUI scale because there is nothing
        // separating the strokes from the paper; giving every stroke its own one-pixel shadow is what
        // real signwriting does with a chisel, and it costs one extra draw call.
        int tx = x + 168;
        Component fish = Component.translatable("fish.riverfishing." + species.getPath());
        // Two captions, one sign: what he pays, or who would. The second is the answer to the question
        // a junior stall actually raises, printed where it is asked.
        Component line;
        if (pay > 0) {
            String mult = base > 0 ? String.format(Locale.ROOT, "%.1f", pay / (float) base) : "?";
            line = Component.translatable("screen.riverfishing.order_panel.line", fish, mult);
        } else {
            line = Component.translatable("screen.riverfishing.order_panel.too_junior", fish, tier);
        }
        var lines = font.split(line, 132);
        // Centre the whole block in the parchment rather than pinning the title: a caption that wraps
        // to two lines must not push itself off the bottom edge.
        int total = 10 + lines.size() * 10;
        int ty = y + 5 + (38 - total) / 2;
        engraveCentered(g, Component.translatable("screen.riverfishing.order_panel.title"), tx, ty);
        for (int i = 0; i < lines.size(); i++) {
            engraveCentered(g, lines.get(i), tx, ty + 12 + i * 10);
        }
    }

    private static final int INK = 0xFF241A0E, RELIEF = 0xFFF6F0DE;

    /**
     * One engrave, and one only. Drawing a caption twice — once as a Component and once as a wrapped
     * sequence — is a caption with two sets of defaults, and the default that differed here was the drop
     * shadow: right on a dark HUD, wrong on parchment, and applied to the title alone.
     */
    private static void engraveCentered(GuiGraphicsExtractor g, Component c, int cx, int y) {
        engraveCentered(g, c.getVisualOrderText(), cx, y);
    }

    private static void engraveCentered(GuiGraphicsExtractor g, net.minecraft.util.FormattedCharSequence c, int cx, int y) {
        var font = Minecraft.getInstance().font;
        int half = font.width(c) / 2;
        g.text(font, c, cx - half + 1, y + 1, RELIEF, false);
        g.text(font, c, cx - half, y, INK, false);
    }

    /**
     * §order-panel: where the sign is, so a click can be tested against it. Worked out the same way
     * draw() works it out — one method would be better still, but the two callers want different halves
     * of the answer and neither wants to hold a field that the other could leave stale.
     */
    private static int[] bounds() {
        Minecraft mc = Minecraft.getInstance();
        int leftPos = (mc.getWindow().getGuiScaledWidth() - MERCHANT_W) / 2;
        int topPos = (mc.getWindow().getGuiScaledHeight() - MERCHANT_H) / 2;
        int x = leftPos + (MERCHANT_W - W) / 2;
        int y = topPos - H - 3;
        if (y < 2) y = topPos + MERCHANT_H + 3;
        return new int[]{x, y};
    }

    /**
     * A click on the sign selects the trade it advertises — the same thing clicking its row in the list
     * would do, because it IS that row. Without this the sign shows a trade you cannot take, which is
     * worse than not showing it.
     *
     * @return true when the click was ours and should go no further
     */
    public static boolean click(double mx, double my) {
        if (species == null || pay <= 0) return false;    // nothing to select on a junior stall
        int[] b = bounds();
        if (mx < b[0] || mx >= b[0] + W || my < b[1] || my >= b[1] + H) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu
                instanceof net.minecraft.world.inventory.MerchantMenu menu)) return false;
        var offers = menu.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (!(offers.get(i).getCostA().getItem() instanceof com.riverfishing.item.FishItem f)) continue;
            if (!f.species().equals(species)) continue;
            // Exactly what vanilla's own trade button does, minus the private bookkeeping: hint the
            // selection, pull the cost out of the inventory, and tell the server which row is live.
            menu.setSelectionHint(i);
            menu.tryMoveItems(i);
            if (mc.getConnection() != null) {
                mc.getConnection().send(
                        new net.minecraft.network.protocol.game.ServerboundSelectTradePacket(i));
            }
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            return true;
        }
        return false;
    }
}
