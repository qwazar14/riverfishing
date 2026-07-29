package com.riverfishing.client;

import com.riverfishing.item.TackleBoxItem;
import com.riverfishing.item.TackleBoxTier;
import com.riverfishing.menu.TackleBoxMenu;
import com.riverfishing.network.ModNetwork;
import com.riverfishing.network.TackleBoxRenamePacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * §tackle-box (0.7.0): the box, drawn.
 *
 * <p>The name field is the top row rather than a button that opens a dialogue: renaming is the whole
 * point of having four boxes, so it should cost one click, and a box called "щука" beats four identical
 * boxes you have to open to tell apart.
 *
 * <p>Every measurement comes from {@link TackleBoxMenu}, which is where the slots are placed from — the
 * last container in this mod had its screen and its menu each doing the arithmetic and they disagreed.
 */
public class TackleBoxScreen extends AbstractContainerScreen<TackleBoxMenu> {
    private EditBox nameField;
    /** What the server already knows, so a field that has not been touched never sends anything. */
    private String sent = "";

    public TackleBoxScreen(TackleBoxMenu menu, Inventory inv, Component title) {
        // §26.1: imageWidth/imageHeight are final now — the size goes through the sized super ctor.
        super(menu, inv, title, TackleBoxMenu.PANEL_WIDTH, TackleBoxMenu.panelHeight(menu.tier()));
        this.inventoryLabelY = TackleBoxMenu.invLabel(menu.tier());
        this.titleLabelY = -100;   // the name FIELD is the title; the vanilla label would sit under it
    }

    @Override
    protected void init() {
        super.init();
        ItemStack box = menu.box();
        String current = box.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                ? box.getHoverName().getString() : "";
        sent = current;
        nameField = new EditBox(font, leftPos + 8, topPos + TackleBoxMenu.NAME_TOP - 6,
                TackleBoxMenu.PANEL_WIDTH - 16 - 22, 14, Component.empty());
        nameField.setMaxLength(TackleBoxRenamePacket.MAX_LENGTH);
        nameField.setValue(current);
        nameField.setHint(Component.translatable("gui.riverfishing.tackle_box_name_hint"));
        nameField.setBordered(true);
        nameField.setResponder(this::onNameTyped);
        addRenderableWidget(nameField);
        setInitialFocus(null);   // typing should move items by hotkey until the player clicks the field
    }

    private void onNameTyped(String value) {
        if (value.equals(sent)) return;
        sent = value;
        ModNetwork.toServer(new TackleBoxRenamePacket(value));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        TackleBoxTier t = menu.tier();
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2118);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF4A3A28);

        // The dyed insert, as a band behind the slots — the same colour the box itself is tinted with, so
        // the box you picked out of a shelf of four is recognisable the moment it opens.
        int colour = 0xFF000000 | TackleBoxItem.color(menu.box());
        int gridTop = y + TackleBoxMenu.GRID_TOP;
        g.fill(x + 5, gridTop - 3, x + imageWidth - 5, gridTop + t.rows() * 18 + 3, colour);
        g.fill(x + 6, gridTop - 2, x + imageWidth - 6, gridTop + t.rows() * 18 + 2, 0xFF2B2118);

        for (int row = 0; row < t.rows(); row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + TackleBoxMenu.GRID_LEFT + col * 18;
                int sy = gridTop + row * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF20180F);
                g.fill(sx, sy, sx + 16, sy + 16, 0xFF3A2D1F);
            }
        }
        int invTop = y + TackleBoxMenu.invTop(t);
        for (int row = 0; row < 4; row++) {
            int sy = row < 3 ? invTop + row * 18 : invTop + 58;
            for (int col = 0; col < 9; col++) {
                int sx = x + 8 + col * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF20180F);
                g.fill(sx, sy, sx + 16, sy + 16, 0xFF3A2D1F);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // §26.1: super already extracts the background, the slots and the hovered-slot tooltip — the old
        // explicit renderTooltip() call is gone.
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // The box's own icon sits beside the name field, tinted, so the colour you are choosing is visible
        // while you are naming the thing you chose it for.
        g.item(menu.box(), leftPos + imageWidth - 24, topPos + TackleBoxMenu.NAME_TOP - 7);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While the name field has focus, E must type an "e" rather than slamming the screen shut.
        if (nameField != null && nameField.isFocused() && event.key() != 256) {
            return nameField.keyPressed(event) || nameField.canConsumeInput()
                    || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }
}
