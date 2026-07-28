package com.riverfishing.menu;

import com.riverfishing.item.KeepnetData;
import com.riverfishing.item.KeepnetItem;
import com.riverfishing.item.KeepnetTier;
import com.riverfishing.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * §keepnet (0.7.0): the menu behind the spatial grid.
 *
 * <p>It is deliberately thin. The grid is NOT slots — a fish covers several cells at once, which no
 * vanilla slot can express — so the contents live in the keepnet's own NBT, which the client already has
 * because the keepnet is in the player's hand and inventories are synced. The menu supplies the two
 * things vanilla is genuinely good at: the player's own inventory, and the stack on the cursor.
 *
 * <p>Picking a fish out of the grid puts it on the cursor; clicking a cell with something on the cursor
 * puts it there if it fits. Every change is applied SERVER-side through
 * {@link com.riverfishing.network.KeepnetActionPacket} — the client only ever asks.
 */
public class KeepnetMenu extends AbstractContainerMenu {
    /**
     * §keepnet-layout: the ONE place the panel's geometry is decided. The menu positions the inventory
     * slots and the screen draws around them, and when those two disagreed by even a few pixels the
     * inventory ended up drawn across the grid — which is exactly what happened the first time.
     */
    public static final int CELL = 18;
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 18;
    /** The row of buttons under the grid. */
    public static final int BUTTON_H = 14;

    public static int gridBottom(KeepnetTier t) {
        return GRID_TOP + t.height() * CELL;
    }

    public static int buttonRow(KeepnetTier t) {
        return gridBottom(t) + 4;
    }

    public static int invLabel(KeepnetTier t) {
        return buttonRow(t) + BUTTON_H + 6;
    }

    public static int invTop(KeepnetTier t) {
        return invLabel(t) + 11;
    }

    public static int panelWidth(KeepnetTier t) {
        return Math.max(176, GRID_LEFT * 2 + t.width() * CELL);
    }

    public static int panelHeight(KeepnetTier t) {
        // three rows, a gap, the hotbar, and a margin
        return invTop(t) + 3 * CELL + 4 + CELL + 7;
    }

    public static final int INV_LEFT = 8;

    private final Player player;
    private final InteractionHand hand;

    public KeepnetMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.KEEPNET.get(), id);
        this.player = inv.player;
        this.hand = hand;

        KeepnetTier tier = net().getItem() instanceof KeepnetItem k ? k.tier() : KeepnetTier.WICKER;
        int invTop = invTop(tier);
        // The inventory is centred under the grid rather than pinned left: the grid is wider than nine
        // slots on the bigger boxes, and an off-centre inventory reads as a mistake.
        int invLeft = (panelWidth(tier) - 9 * CELL) / 2;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, invLeft + col * CELL, invTop + row * CELL));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, invLeft + col * CELL, invTop + 3 * CELL + 4));
        }
    }

    public static KeepnetMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new KeepnetMenu(id, inv, buf.readEnum(InteractionHand.class));
    }

    /** The keepnet itself, read fresh every time — a cached stack goes stale (§live-rod). */
    public ItemStack net() {
        return player.getItemInHand(hand);
    }

    public InteractionHand hand() {
        return hand;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.getItemInHand(hand).getItem() instanceof KeepnetItem;
    }

    /**
     * Shift-click from the inventory drops a fish into the grid wherever it fits. This is the auto-place
     * the borrowed design says must exist on day one: without it, filling a box is a chore rather than a
     * decision, and the decision is the whole point.
     */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack net = net();
        if (!(net.getItem() instanceof KeepnetItem)) return ItemStack.EMPTY;

        KeepnetData data = KeepnetData.read(net);
        ItemStack one = stack.copyWithCount(1);
        if (!data.autoPlace(one)) return ItemStack.EMPTY;   // no room: leave it where it is
        data.write(net);
        stack.shrink(1);
        slot.setChanged();
        return ItemStack.EMPTY;                             // one at a time: each fish is its own decision
    }

    /** §keepnet: opened from the held keepnet only — never from a hotbar shortcut, never mid-fight. */
    public static void open(ServerPlayer sp, InteractionHand hand) {
        dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(sp,
                new dev.architectury.registry.menu.ExtendedMenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return sp.getItemInHand(hand).getHoverName();
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                        return new KeepnetMenu(id, inv, hand);
                    }

                    @Override
                    public void saveExtraData(FriendlyByteBuf buf) {
                        buf.writeEnum(hand);
                    }
                });
    }
}
