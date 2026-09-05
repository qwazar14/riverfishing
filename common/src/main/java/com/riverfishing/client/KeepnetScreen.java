package com.riverfishing.client;

import com.riverfishing.fish.FishShape;
import com.riverfishing.item.KeepnetData;
import com.riverfishing.item.KeepnetItem;
import com.riverfishing.item.KeepnetTier;
import com.riverfishing.menu.KeepnetMenu;
import com.riverfishing.network.KeepnetActionPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
 *
 * <p>§26.1: drawn through the extract/submit render-state model — renderBg → extractBackground,
 * render → extractRenderState, renderLabels → extractLabels, drawString → text, drawCenteredString →
 * centeredText, renderOutline → outline, renderItem → item, and mouse/key input arrive as events.
 */
public class KeepnetScreen extends AbstractContainerScreen<KeepnetMenu> {
    private static final int CELL = KeepnetMenu.CELL;
    /**
     * §keepnet-tune: one multiplier over the measured fit, live-tunable through {@code /rfnet}.
     *
     * <p>It used to be three numbers, two of which were a GUESS at how much of its icon a fish fills. That
     * guess is why no single value worked: a ray fills its canvas nearly corner to corner and a flounder
     * is a flat oval, so a scale that suited one made the other tiny. The proportions are measured per
     * species now ({@link FishBounds}), which leaves nothing to guess and this knob at 1.0.
     */
    public static float iconScale = 1.0f;


    /** Which way round the thing on the cursor goes down. Client state: it is a property of the pointer. */
    private int rot;
    private int btnW = 54;

    public KeepnetScreen(KeepnetMenu menu, Inventory inv, Component title) {
        // §26.1: imageWidth/imageHeight are final now — the size goes through the super ctor, so the
        // tier has to be read off the menu ARGUMENT (the field is not assigned until super returns).
        super(menu, inv, title,
                KeepnetMenu.panelWidth(tierOf(menu)), KeepnetMenu.panelHeight(tierOf(menu)));
        this.inventoryLabelY = KeepnetMenu.invLabel(tierOf(menu));
        this.titleLabelY = 6;
    }

    private static KeepnetTier tierOf(KeepnetMenu menu) {
        return menu.net().getItem() instanceof KeepnetItem k ? k.tier() : KeepnetTier.SMALL;
    }

    private KeepnetTier tier() {
        return tierOf(menu);
    }

    private int gridX() { return leftPos + KeepnetMenu.GRID_LEFT; }

    private int gridY() { return topPos + KeepnetMenu.GRID_TOP; }

    private int tidyX() { return leftPos + KeepnetMenu.GRID_LEFT; }

    private int tidyY() { return topPos + KeepnetMenu.buttonRow(tier()); }

    private int emptyX() { return tidyX() + btnW + 4; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        KeepnetTier t = tier();
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2118);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF473726);

        int x0 = gridX(), y0 = gridY();
        for (int y = 0; y < t.height(); y++) {
            for (int x = 0; x < t.width(); x++) {
                int cx = x0 + x * CELL, cy = y0 + y * CELL;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF16323C);
                g.outline(cx, cy, CELL - 1, CELL - 1, 0x28FFFFFF);
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
                g.outline(sx, sy, CELL - 2, CELL - 2, 0x30FFFFFF);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // §26.1: super already extracts the background, the slots and the hovered-slot tooltip — the old
        // explicit renderTooltip() call is gone.
        super.extractRenderState(g, mouseX, mouseY, partialTick);
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
            drawInFootprint(g, p.stack(), x0 + p.x() * CELL, y0 + p.y() * CELL,
                    s.width(), s.height(), p.rot());
        }

        int[] cell = cellAt(mouseX, mouseY);
        ItemStack carried = menu.getCarried();
        if (cell != null) {
            if (!carried.isEmpty()) {
                boolean ok = data.fits(carried.copyWithCount(1), cell[0], cell[1], rot);
                FishShape s = FishShape.of(carried).rotated(rot);
                // Ghost the fish itself into the cells it would take, turned as it would land.
                drawInFootprint(g, carried, x0 + cell[0] * CELL, y0 + cell[1] * CELL,
                        s.width(), s.height(), rot);
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
                g.outline(cx, cy, CELL - 1, CELL - 1, 0xB0FFFFFF);
            }
        }

        drawButton(g, tidyX(), tidyY(), Component.translatable("keepnet.riverfishing.tidy"), mouseX, mouseY);
        drawButton(g, emptyX(), tidyY(), Component.translatable("keepnet.riverfishing.empty"), mouseX, mouseY);
        g.text(this.font, Component.translatable("keepnet.riverfishing.rotate"),
                emptyX() + btnW + 6, tidyY() + 4, 0xFF9C8F76, false);

        // A fish under the pointer names itself, because "which one do I throw back" is the question.
        if (cell != null && carried.isEmpty()) {
            int i = data.at(cell[0], cell[1]);
            if (i >= 0) g.setTooltipForNextFrame(this.font, data.items().get(i).stack(), mouseX, mouseY);
        }
    }

    /**
     * Draw a fish at the size of the space it is taking, turned the way it was placed.
     *
     * <p>§fish-icon: this blits the species' own picture rather than rendering the item. Going through
     * the item model meant the fish arrived as a lit, extruded slab — and any fish wider than one cell
     * came back through vanilla's oversized-item pass looking soft and washed out, which is the
     * "squashed texture" this GUI was reported for. It also meant the size had to be handed over as a
     * scale bucket baked into the model and then divided back out here, two numbers that had to agree
     * exactly. A blit needs neither. See {@link FishIcon}.
     *
     * <p>The fit needs the fish's REAL proportions inside its square canvas, which vary enormously (a
     * ray is nearly square, an eel is a bar a seventh as tall as it is long); those are measured per
     * species rather than assumed, because assuming them made the ray look right and the flounder tiny.
     */
    private void drawInFootprint(GuiGraphicsExtractor g, ItemStack stack, int px, int py, int cw, int ch, int rot) {
        var sp = com.riverfishing.item.FishItem.getSpecies(stack);
        if (sp == null) {           // bycatch: a boot is an item, and an item is what it should look like
            g.item(stack, px + (cw * CELL - 16) / 2, py + (ch * CELL - 16) / 2);
            return;
        }
        float[] b = FishBounds.of(sp.getPath());
        // A turned fish lies on its side, so the footprint's axes swap for the purposes of fitting it.
        float availW = (rot == 0 ? cw : ch) * CELL;
        float availH = (rot == 0 ? ch : cw) * CELL;
        // The canvas side that makes the FISH — not the canvas — fill the space it was given.
        int side = Math.round(Math.min(availW / b[0], availH / b[1]) * iconScale);

        g.pose().pushMatrix();
        g.pose().translate(px + cw * CELL / 2f, py + ch * CELL / 2f);
        if (rot != 0) g.pose().rotate((float) (Math.PI / 2));
        // §morph: the same multiply the fish wears in the water and in the journal.
        FishIcon.draw(g, sp.getPath(), -side / 2, -side / 2, side,
                com.riverfishing.fish.FishMorph.tint(sp.getPath(),
                        com.riverfishing.item.FishItem.getAge(stack),
                        com.riverfishing.item.FishItem.getMorph(stack),
                        com.riverfishing.fish.CatchCard.pattern(stack)));   // §pattern
        g.pose().popMatrix();
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, Component label, int mx, int my) {
        boolean hot = mx >= x && my >= y && mx < x + btnW && my < y + KeepnetMenu.BUTTON_H;
        g.fill(x, y, x + btnW, y + KeepnetMenu.BUTTON_H, hot ? 0xFF6B573B : 0xFF52422D);
        g.outline(x, y, btnW, KeepnetMenu.BUTTON_H, 0x50FFFFFF);
        g.centeredText(this.font, label, x + btnW / 2, y + 3, 0xFFEDE3C8);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // R turns whatever is on the cursor. The preview under the pointer shows the result before you commit.
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            rot = 1 - rot;
            return true;
        }
        return super.keyPressed(event);
    }

    private void send(int action, int x, int y) {
        ModNetwork.toServer(new KeepnetActionPacket(action, x, y, rot));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, 8, this.titleLabelY, 0xFFEDE3C8, false);
        g.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFF9C8F76, false);
    }
}
