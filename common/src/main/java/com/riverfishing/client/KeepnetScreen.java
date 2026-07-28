package com.riverfishing.client;

import com.riverfishing.fish.FishShape;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.KeepnetData;
import com.riverfishing.item.KeepnetItem;
import com.riverfishing.item.KeepnetTier;
import com.riverfishing.menu.KeepnetMenu;
import com.riverfishing.network.KeepnetActionPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * §keepnet (0.7.0): the box, drawn.
 *
 * <p>Three rules taken straight from the design this borrows, all of them there from the first build
 * rather than promised for later:
 * <ul>
 *   <li><b>You can always rearrange.</b> Pick anything up, put it anywhere it fits, at any time. A
 *       spatial inventory you cannot reshuffle is a frustration generator.</li>
 *   <li><b>The game never places for you — but it offers.</b> Shift-click drops a fish in wherever it
 *       fits, and TIDY repacks the whole box biggest-first. Both are offers; neither moves anything you
 *       placed by hand unless you ask.</li>
 *   <li><b>It opens only in a pause.</b> On the held keepnet, and nowhere else.</li>
 * </ul>
 *
 * <p>The grid is drawn by hand rather than out of slots because a fish covers several cells at once. The
 * client reads the box straight from the keepnet's NBT, which it already has, and every change is a
 * request to the server.
 */
public class KeepnetScreen extends AbstractContainerScreen<KeepnetMenu> {
    private static final int CELL = 18;
    private static final int GRID_LEFT = 8;
    private static final int GRID_TOP = 22;

    /** Which way round the thing on the cursor goes down. Kept on the client: it is a pointer state. */
    private int rot;
    private int tidyX, tidyY, emptyX, emptyY;

    public KeepnetScreen(KeepnetMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        KeepnetTier tier = tier();
        this.imageWidth = Math.max(176, GRID_LEFT * 2 + tier.width() * CELL);
        this.imageHeight = GRID_TOP + tier.height() * CELL + 14 + 76;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private KeepnetTier tier() {
        return menu.net().getItem() instanceof KeepnetItem k ? k.tier() : KeepnetTier.WICKER;
    }

    @Override
    protected void init() {
        super.init();
        KeepnetTier t = tier();
        tidyX = leftPos + GRID_LEFT;
        tidyY = topPos + GRID_TOP + t.height() * CELL + 2;
        emptyX = tidyX + 58;
        emptyY = tidyY;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        KeepnetTier t = tier();
        int x0 = leftPos + GRID_LEFT, y0 = topPos + GRID_TOP;
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2118);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF473726);

        // The water, and the gear strip at its edge — a different colour because it holds different things.
        for (int y = 0; y < t.height(); y++) {
            for (int x = 0; x < t.width(); x++) {
                int cx = x0 + x * CELL, cy = y0 + y * CELL;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1,
                        t.isGearCell(x) ? 0xFF3A3226 : 0xFF1E3A44);
                g.renderOutline(cx, cy, CELL - 1, CELL - 1, 0x30FFFFFF);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        KeepnetData data = KeepnetData.read(menu.net());
        KeepnetTier t = tier();
        int x0 = leftPos + GRID_LEFT, y0 = topPos + GRID_TOP;

        // What is in the box. A fish is drawn once, over the middle of its own footprint, at a size that
        // says how much room it is taking — the whole reason the mechanic exists.
        for (KeepnetData.Placed p : data.items()) {
            FishShape s = p.shape();
            for (int y = 0; y < s.height(); y++) {
                for (int x = 0; x < s.width(); x++) {
                    if (!s.at(x, y)) continue;
                    int cx = x0 + (p.x() + x) * CELL, cy = y0 + (p.y() + y) * CELL;
                    g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0x66FFFFFF & 0x33FFFFFF | 0xFF14343E);
                }
            }
            int mx = x0 + p.x() * CELL + (s.width() * CELL) / 2 - 8;
            int my = y0 + p.y() * CELL + (s.height() * CELL) / 2 - 8;
            g.renderItem(p.stack(), mx, my);
        }

        // The cell under the pointer, and whether what you are holding would go there.
        int[] cell = cellAt(mouseX, mouseY);
        ItemStack carried = menu.getCarried();
        if (cell != null) {
            if (!carried.isEmpty()) {
                boolean ok = data.fits(carried.copyWithCount(1), cell[0], cell[1], rot);
                FishShape s = FishShape.of(carried).rotated(rot);
                for (int y = 0; y < s.height(); y++) {
                    for (int x = 0; x < s.width(); x++) {
                        if (!s.at(x, y)) continue;
                        int gx = cell[0] + x, gy = cell[1] + y;
                        if (gx >= t.width() || gy >= t.height()) continue;
                        int cx = x0 + gx * CELL, cy = y0 + gy * CELL;
                        g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, ok ? 0x6040C040 : 0x60C04040);
                    }
                }
            } else {
                int cx = x0 + cell[0] * CELL, cy = y0 + cell[1] * CELL;
                g.renderOutline(cx, cy, CELL - 1, CELL - 1, 0xA0FFFFFF);
            }
        }

        // Two buttons and one line of state. Nothing else: the grid is the interface.
        drawButton(g, tidyX, tidyY, 54, Component.translatable("keepnet.riverfishing.tidy"), mouseX, mouseY);
        drawButton(g, emptyX, emptyY, 54, Component.translatable("keepnet.riverfishing.empty"), mouseX, mouseY);
        g.drawString(this.font, Component.translatable("keepnet.riverfishing.rotate"),
                emptyX + 60, emptyY + 5, 0xFFB9AE94, false);

        renderTooltip(g, mouseX, mouseY);
        // A fish under the pointer names itself, because "which one do I throw back" is the question.
        if (cell != null && carried.isEmpty()) {
            int i = data.at(cell[0], cell[1]);
            if (i >= 0) g.renderTooltip(this.font, data.items().get(i).stack(), mouseX, mouseY);
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, Component label, int mx, int my) {
        boolean hot = mx >= x && my >= y && mx < x + w && my < y + 14;
        g.fill(x, y, x + w, y + 14, hot ? 0xFF6B573B : 0xFF52422D);
        g.renderOutline(x, y, w, 14, 0x50FFFFFF);
        g.drawCenteredString(this.font, label, x + w / 2, y + 3, 0xFFEDE3C8);
    }

    /** The grid cell under a screen position, or null. */
    private int[] cellAt(int mouseX, int mouseY) {
        KeepnetTier t = tier();
        int x = (mouseX - (leftPos + GRID_LEFT)) / CELL;
        int y = (mouseY - (topPos + GRID_TOP)) / CELL;
        if (mouseX < leftPos + GRID_LEFT || mouseY < topPos + GRID_TOP) return null;
        if (x < 0 || y < 0 || x >= t.width() || y >= t.height()) return null;
        return new int[]{x, y};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (mx >= tidyX && my >= tidyY && mx < tidyX + 54 && my < tidyY + 14) {
            send(KeepnetActionPacket.REPACK, 0, 0);
            return true;
        }
        if (mx >= emptyX && my >= emptyY && mx < emptyX + 54 && my < emptyY + 14) {
            send(KeepnetActionPacket.EMPTY, 0, 0);
            return true;
        }
        int[] cell = cellAt(mx, my);
        if (cell != null) {
            send(menu.getCarried().isEmpty() ? KeepnetActionPacket.TAKE : KeepnetActionPacket.PLACE,
                    cell[0], cell[1]);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // R turns the thing on the cursor. One key, and the preview under the pointer shows the result.
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            rot = 1 - rot;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void send(int action, int x, int y) {
        ModNetwork.toServer(new KeepnetActionPacket(action, x, y, rot));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 8, 0xFFEDE3C8, false);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFFB9AE94, false);
    }
}
