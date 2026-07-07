package com.riverfishing.client;

import com.riverfishing.component.ComponentSlot;
import com.riverfishing.menu.RodAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Polished, primitive-drawn screen for {@link RodAssemblyMenu}. */
public class RodAssemblyScreen extends AbstractContainerScreen<RodAssemblyMenu> {
    private DepthSlider depthSlider;

    /** §tackle-compat: a transient reel/line warning shown INSIDE the window (never external text). */
    private static Component warning = null;
    private static long warningExpiry = 0L;

    /** Called from {@link com.riverfishing.network.RodWarningPacket} — flash a warning for ~3.5 s. */
    public static void showWarning(Component message) {
        warning = message;
        warningExpiry = System.currentTimeMillis() + 3500L;
    }

    public RodAssemblyScreen(RodAssemblyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 190; // taller: the socketed rig's own slots live inline (§rig-inline)
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = 10;
        this.titleLabelY = 8;   // inside the header band, under the wood frame (§gui-cyrillic)
        this.inventoryLabelX = 10;
    }

    @Override
    protected void init() {
        super.init();
        depthSlider = new DepthSlider(this.leftPos + 150, this.topPos + 26);
        addRenderableWidget(depthSlider);
    }

    /**
     * Vertical float-depth slider ("спуск", §fishing-depth): three stops — surface / mid / bottom.
     * Only shown while the rig slot holds a rig with a float loaded; hidden otherwise.
     */
    private class DepthSlider extends net.minecraft.client.gui.components.AbstractWidget {
        DepthSlider(int x, int y) {
            super(x, y, 14, 54, Component.translatable("tooltip.riverfishing.float_depth", ""));
        }

        private int idx() {
            return switch (com.riverfishing.item.RodData.getDepth(menu.rodStack())) {
                case "surface" -> 0;
                case "bottom" -> 2;
                default -> 1;
            };
        }

        private int stopY(int i) {
            return getY() + 5 + i * (this.height - 10) / 2;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            // "Спуск" caption above the track
            Component cap = Component.translatable("guide.riverfishing.depth_short");
            g.drawString(RodAssemblyScreen.this.font, cap,
                    x + 7 - RodAssemblyScreen.this.font.width(cap) / 2, getY() - 10, GuiStyle.TEXT_HINT, false);
            // track
            g.fill(x + 5, getY(), x + 9, getY() + this.height, 0xFF2A1E12);
            g.fill(x + 6, getY() + 1, x + 8, getY() + this.height - 1, 0xFF1E1610);
            // stop marks
            for (int i = 0; i < 3; i++) {
                int sy = stopY(i);
                g.fill(x + 3, sy, x + 11, sy + 1, 0xFF6E5A3C);
            }
            // brass handle at the current stop
            int hy = stopY(idx());
            g.fill(x + 1, hy - 3, x + 13, hy + 4, 0xFF2A1E12);
            g.fill(x + 2, hy - 2, x + 12, hy + 3, 0xFFC89C4A);
            // hovering shows the depth name
            if (isHovered()) {
                g.renderTooltip(RodAssemblyScreen.this.font,
                        Component.translatable("depthset.riverfishing."
                                + com.riverfishing.item.RodData.getDepth(menu.rodStack())),
                        mouseX, mouseY);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setFromMouse(mouseY);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            setFromMouse(mouseY);
        }

        private void setFromMouse(double mouseY) {
            int best = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                double d = Math.abs(mouseY - stopY(i));
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            if (best != idx()) {
                int button = switch (best) {
                    case 0 -> RodAssemblyMenu.BUTTON_DEPTH_SURFACE;
                    case 2 -> RodAssemblyMenu.BUTTON_DEPTH_BOTTOM;
                    default -> RodAssemblyMenu.BUTTON_DEPTH_MID;
                };
                RodAssemblyScreen.this.minecraft.gameMode
                        .handleInventoryButtonClick(RodAssemblyScreen.this.menu.containerId, button);
                // local prediction for instant handle feedback (server sets the same value)
                com.riverfishing.item.RodData.setDepth(menu.rodStack(),
                        switch (best) { case 0 -> "surface"; case 2 -> "bottom"; default -> "mid"; });
            }
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        // The depth slider only exists on FLOAT-class rods while a float is actually rigged
        // (§fishing-depth) — never on spinning or long-cast bottom rods.
        if (depthSlider != null) {
            boolean floatRod = this.menu.rodStack().getItem() instanceof com.riverfishing.item.RodItem ri
                    && ri.rodType().rodClass() == com.riverfishing.component.RodClass.FLOAT;
            boolean show = floatRod && com.riverfishing.rig.RigData.hasFloat(this.menu.currentRig());
            depthSlider.visible = show;
            depthSlider.active = show;
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        // Flag a component slot the carried item can't go into (Module 6).
        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            for (int i = 0; i < this.menu.componentSlotCount(); i++) {
                if (this.menu.rejectionReason(i, carried) != null) {
                    Slot slot = this.menu.slots.get(i);
                    graphics.fill(this.leftPos + slot.x, this.topPos + slot.y,
                            this.leftPos + slot.x + 16, this.topPos + slot.y + 16, 0x80FF3030);
                }
            }
        }

        this.renderTooltip(graphics, mouseX, mouseY);

        if (!carried.isEmpty() && this.hoveredSlot != null) {
            int idx = this.menu.slots.indexOf(this.hoveredSlot);
            Component reason = this.menu.rejectionReason(idx, carried);
            if (reason != null) {
                graphics.renderTooltip(this.font, reason, mouseX, mouseY);
            }
        }

        // §tackle-compat: a transient reel/line warning banner INSIDE the window (from a failed shift-click).
        if (warning != null && System.currentTimeMillis() < warningExpiry) {
            String text = this.font.plainSubstrByWidth(warning.getString(), imageWidth - 12);
            int bw = this.font.width(text) + 8;
            int bx = this.leftPos + (imageWidth - bw) / 2;
            int by = this.topPos + 20;
            graphics.fill(bx, by, bx + bw, by + 12, 0xE0301010);
            graphics.fill(bx, by, bx + bw, by + 1, 0xFFE0503A);
            graphics.fill(bx, by + 11, bx + bw, by + 12, 0xFFE0503A);
            graphics.drawString(this.font, text, bx + 4, by + 2, 0xFFF0C0B0, false);
        } else {
            warning = null;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        GuiStyle.panel(graphics, x, y, imageWidth, imageHeight);

        int comp = this.menu.componentSlotCount();
        // Rod blank running through the component slots (#1).
        if (comp >= 2) {
            Slot first = this.menu.slots.get(0);
            Slot last = this.menu.slots.get(comp - 1);
            GuiStyle.line(graphics, x + first.x - 7, y + first.y + 22, x + last.x + 22, y + last.y - 7, 0xFF6B4A2A);
            // a hint of line dropping from the tip into the "water"
            GuiStyle.line(graphics, x + last.x + 22, y + last.y - 6, x + last.x + 22, y + last.y + 18, 0x88B8E0F0);
        }

        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (!slot.isActive()) continue; // hidden inline rig slots when no rig is socketed
            int sx = x + slot.x;
            int sy = y + slot.y;
            GuiStyle.slot(graphics, sx, sy);
            if (i < comp) {
                ComponentSlot type = this.menu.componentSlotType(i);
                GuiStyle.accentFrame(graphics, sx, sy, accentColor(type));
                if (!slot.hasItem()) {
                    GuiStyle.ghostGlyph(graphics, this.font, glyphFor(type), sx, sy);
                }
            } else if (slot instanceof RodAssemblyMenu.RigProxySlot proxy) {
                var role = proxy.role();
                if (role != null) {
                    GuiStyle.accentFrame(graphics, sx, sy, roleColor(role));
                    if (!slot.hasItem()) {
                        String name = Component.translatable(
                                "rig.riverfishing.role." + role.name().toLowerCase()).getString();
                        GuiStyle.ghostGlyph(graphics, this.font,
                                name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), sx, sy);
                    }
                }
            }
        }
    }

    private static int roleColor(com.riverfishing.rig.SlotRole role) {
        return switch (role) {
            case HOOK -> 0xFF9AA3AD;
            case BAIT -> 0xFF8A6A42;
            case GROUNDBAIT -> 0xFF6E8A3A;
            case FLOAT -> 0xFFC0392B;
            case LEADER -> 0xFF6E86A8;
            case LURE -> 0xFFB08D3C;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiStyle.TEXT, false);
        for (int i = 0; i < this.menu.componentSlotCount(); i++) {
            ComponentSlot type = this.menu.componentSlotType(i);
            if (type == null) continue;
            Slot slot = this.menu.slots.get(i);
            Component label = Component.translatable("menu.riverfishing.slot." + type.name().toLowerCase());
            int lx = slot.x + 8 - this.font.width(label) / 2;
            graphics.drawString(this.font, label, lx, slot.y - 11, GuiStyle.TEXT, false);
        }
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiStyle.TEXT, false);
    }

    private static int accentColor(ComponentSlot type) {
        if (type == null) return 0xFF888888;
        return switch (type) {
            case REEL -> 0xFF6E86A8;
            case LINE -> 0xFF5AB0C8;
            case RIG -> 0xFFB28A52;
            case HOOK -> 0xFF888888;
        };
    }

    private String glyphFor(ComponentSlot type) {
        String s = Component.translatable("menu.riverfishing.slot." + type.name().toLowerCase()).getString();
        return s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase();
    }
}
