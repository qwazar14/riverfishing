package com.riverfishing.client;

import com.riverfishing.fish.FishShape;
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
 * <p>Three rules taken straight from the design this borrows, all present from the first build rather
 * than promised for later:
 * <ul>
 *   <li><b>You can always rearrange.</b> Pick anything up, put it anywhere it fits, at any time.</li>
 *   <li><b>The game never places for you — but it offers.</b> Shift-click drops a fish wherever it fits,
 *       and TIDY repacks the whole box biggest-first.</li>
 *   <li><b>It opens only in a pause.</b> On the held keepnet, and nowhere else.</li>
 * </ul>
 *
 * <p>The grid is drawn by hand rather than out of slots because a fish covers several cells at once, and
 * every measurement comes from {@link KeepnetMenu}'s layout constants — the menu places the inventory
 * slots against the same numbers, and the first cut of this screen disagreed with it by a few pixels and
 * drew the player's inventory straight across the grid.
 */
public class KeepnetScreen extends AbstractContainerScreen<KeepnetMenu> {
    private static final int CELL = KeepnetMenu.CELL;
    /**
     * §keepnet-tune: the three numbers that decide how big a fish is drawn in its footprint. They are
     * mutable statics rather than constants so {@code /rfnet} can dial them in with the box open — the
     * same arrangement the in-hand rod pose uses, and for the same reason: this is a look-at-it decision,
     * not a compute-it one. Whatever ends up looking right gets pasted back here as the default.
     */
    public static float iconScale = 1.0f;
    /** How much of its square icon a fish fills, across and down. Bigger numbers draw the fish smaller. */
    public static float canvasW = 16f;
    public static float canvasH = 7f;

    /** Which way round the thing on the cursor goes down. Client state: it is a property of the pointer. */
    private int rot;
    private int btnW = 54;

    public KeepnetScreen(KeepnetMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        KeepnetTier tier = tier();
        this.imageWidth = KeepnetMenu.panelWidth(tier);
        this.imageHeight = KeepnetMenu.panelHeight(tier);
        this.inventoryLabelY = KeepnetMenu.invLabel(tier);
        this.titleLabelY = 6;
    }

    private KeepnetTier tier() {
        return menu.net().getItem() instanceof KeepnetItem k ? k.tier() : KeepnetTier.WICKER;
    }

    private int gridX() { return leftPos + KeepnetMenu.GRID_LEFT; }

    private int gridY() { return topPos + KeepnetMenu.GRID_TOP; }

    private int tidyX() { return leftPos + KeepnetMenu.GRID_LEFT; }

    private int tidyY() { return topPos + KeepnetMenu.buttonRow(tier()); }

    private int emptyX() { return tidyX() + btnW + 4; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        KeepnetTier t = tier();
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2118);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF473726);

        // The water, and the gear strip at its edge — a different colour because it holds different things.
        int x0 = gridX(), y0 = gridY();
        for (int y = 0; y < t.height(); y++) {
            for (int x = 0; x < t.width(); x++) {
                int cx = x0 + x * CELL, cy = y0 + y * CELL;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, t.isGearCell(x) ? 0xFF3A3226 : 0xFF16323C);
                g.renderOutline(cx, cy, CELL - 1, CELL - 1, 0x28FFFFFF);
            }
        }
        // The player's own slots. Vanilla draws these as part of a background texture; this panel has no
        // texture, so they are drawn here or the inventory is nine rows of nothing.
        int invLeft = leftPos + (imageWidth - 9 * CELL) / 2;
        int invTop = topPos + KeepnetMenu.invTop(t);
        for (int row = 0; row < 4; row++) {
            int sy = invTop + (row < 3 ? row * CELL : 3 * CELL + 4);
            for (int col = 0; col < 9; col++) {
                int sx = invLeft + col * CELL;
                g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, 0xFF2E2519);
                g.renderOutline(sx, sy, CELL - 2, CELL - 2, 0x30FFFFFF);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        KeepnetData data = KeepnetData.read(menu.net());
        KeepnetTier t = tier();
        int x0 = gridX(), y0 = gridY();

        for (KeepnetData.Placed p : data.items()) {
            FishShape s = p.shape();
            for (int y = 0; y < s.height(); y++) {
                for (int x = 0; x < s.width(); x++) {
                    if (!s.at(x, y)) continue;
                    int cx = x0 + (p.x() + x) * CELL, cy = y0 + (p.y() + y) * CELL;
                    g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF23505E);
                }
            }
            drawInFootprint(g, p.stack(), x0 + p.x() * CELL, y0 + p.y() * CELL, s.width(), s.height());
        }

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
                        g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, ok ? 0x8040C040 : 0x80C04040);
                    }
                }
            } else {
                int cx = x0 + cell[0] * CELL, cy = y0 + cell[1] * CELL;
                g.renderOutline(cx, cy, CELL - 1, CELL - 1, 0xB0FFFFFF);
            }
        }

        drawButton(g, tidyX(), tidyY(), Component.translatable("keepnet.riverfishing.tidy"), mouseX, mouseY);
        drawButton(g, emptyX(), tidyY(), Component.translatable("keepnet.riverfishing.empty"), mouseX, mouseY);
        g.drawString(this.font, Component.translatable("keepnet.riverfishing.rotate"),
                emptyX() + btnW + 6, tidyY() + 4, 0xFF9C8F76, false);

        renderTooltip(g, mouseX, mouseY);
        // A fish under the pointer names itself, because "which one do I throw back" is the question.
        if (cell != null && carried.isEmpty()) {
            int i = data.at(cell[0], cell[1]);
            if (i >= 0) g.renderTooltip(this.font, data.items().get(i).stack(), mouseX, mouseY);
        }
    }

    /**
     * Draw a fish at the size of the space it is taking. The item renderer normally scales a fish by its
     * LENGTH, which here would multiply two versions of the same fact and make a catfish burst out of its
     * own footprint; inside the grid the footprint IS the statement about size.
     *
     * <p>The icon's square canvas holds a fish about {@link #canvasW} across and {@link #canvasH} tall, so
     * the scale is whichever of the two fits — that is what stops a 4x1 eel being drawn four cells tall.
     * All three numbers are live-tunable through {@code /rfnet}.
     */
    private void drawInFootprint(GuiGraphics g, ItemStack stack, int px, int py, int cw, int ch) {
        float scale = Math.min(cw * CELL / canvasW, ch * CELL / canvasH) * iconScale;
        FishItemRenderer.gridScale = scale;
        g.pose().pushPose();
        // renderItem draws a 16x16 at the given corner, so centre the scaled 16x16 on the footprint.
        g.pose().translate(px + cw * CELL / 2f, py + ch * CELL / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
        FishItemRenderer.gridScale = 0f;
    }

    private void drawButton(GuiGraphics g, int x, int y, Component label, int mx, int my) {
        boolean hot = mx >= x && my >= y && mx < x + btnW && my < y + KeepnetMenu.BUTTON_H;
        g.fill(x, y, x + btnW, y + KeepnetMenu.BUTTON_H, hot ? 0xFF6B573B : 0xFF52422D);
        g.renderOutline(x, y, btnW, KeepnetMenu.BUTTON_H, 0x50FFFFFF);
        g.drawCenteredString(this.font, label, x + btnW / 2, y + 3, 0xFFEDE3C8);
    }

    /** The grid cell under a screen position, or null. */
    private int[] cellAt(int mouseX, int mouseY) {
        KeepnetTier t = tier();
        if (mouseX < gridX() || mouseY < gridY()) return null;
        int x = (mouseX - gridX()) / CELL;
        int y = (mouseY - gridY()) / CELL;
        if (x < 0 || y < 0 || x >= t.width() || y >= t.height()) return null;
        return new int[]{x, y};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (my >= tidyY() && my < tidyY() + KeepnetMenu.BUTTON_H) {
            if (mx >= tidyX() && mx < tidyX() + btnW) {
                send(KeepnetActionPacket.REPACK, 0, 0);
                return true;
            }
            if (mx >= emptyX() && mx < emptyX() + btnW) {
                send(KeepnetActionPacket.EMPTY, 0, 0);
                return true;
            }
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
        // R turns whatever is on the cursor. The preview under the pointer shows the result before you commit.
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
        g.drawString(this.font, this.title, 8, this.titleLabelY, 0xFFEDE3C8, false);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFF9C8F76, false);
    }
}
