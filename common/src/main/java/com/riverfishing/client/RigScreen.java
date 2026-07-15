package com.riverfishing.client;

import com.riverfishing.menu.RigMenu;
import com.riverfishing.rig.SlotRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Polished, primitive-drawn screen for {@link RigMenu}: colour-coded, labelled role slots. */
public class RigScreen extends AbstractContainerScreen<RigMenu> {
    public RigScreen(RigMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = 10;
        this.titleLabelY = 8;   // inside the header band, under the wood frame (§gui-cyrillic)
        this.inventoryLabelX = 10;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        // Full role name on hover over an empty role slot.
        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem()) {
            int menuIndex = this.menu.slots.indexOf(this.hoveredSlot);
            SlotRole role = this.menu.roleAt(menuIndex);
            if (role != null) {
                graphics.renderTooltip(this.font, Component.translatable(role.langKey()), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        GuiStyle.panel(graphics, x, y, imageWidth, imageHeight);

        int rig = this.menu.rigSlotCount();
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            int sx = x + slot.x;
            int sy = y + slot.y;
            GuiStyle.slot(graphics, sx, sy);
            if (i < rig) {
                SlotRole role = this.menu.roleAt(i);
                GuiStyle.accentFrame(graphics, sx, sy, accentColor(role));
                if (!slot.hasItem()) {
                    GuiStyle.ghostGlyph(graphics, this.font, roleLetter(role), sx, sy);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title,
                this.titleLabelX, this.titleLabelY, GuiStyle.TEXT, false);
        // Long Cyrillic hints wrap inside the panel instead of spilling past its edge (§gui-cyrillic).
        Component hint = Component.translatable("menu.riverfishing.rig_hint");
        int hy = 50;
        for (net.minecraft.util.FormattedCharSequence seq : this.font.split(hint, this.imageWidth - 20)) {
            graphics.drawString(this.font, seq, 10, hy, GuiStyle.TEXT_HINT, false);
            hy += 10;
        }
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiStyle.TEXT, false);
    }

    private static int accentColor(SlotRole role) {
        if (role == null) return 0xFF888888;
        return switch (role) {
            case HOOK -> 0xFFB8B8C0;
            case BAIT -> 0xFF9A7A50;
            case GROUNDBAIT -> 0xFFB0935A;
            case FLOAT -> 0xFFCB5050;
            case LEADER -> 0xFF8088A0;
            case LURE -> 0xFF5AA078;
        };
    }

    private static String roleLetter(SlotRole role) {
        return switch (role) {
            case HOOK -> "H";
            case BAIT -> "B";
            case GROUNDBAIT -> "G";
            case FLOAT -> "F";
            case LEADER -> "P";
            case LURE -> "L";
        };
    }
}
