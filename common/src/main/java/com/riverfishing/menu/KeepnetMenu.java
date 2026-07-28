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
    /** Where the player's inventory sits, below however tall the grid is. */
    public static final int INV_LEFT = 8;

    private final Player player;
    private final InteractionHand hand;

    public KeepnetMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.KEEPNET.get(), id);
        this.player = inv.player;
        this.hand = hand;

        KeepnetTier tier = net().getItem() instanceof KeepnetItem k ? k.tier() : KeepnetTier.WICKER;
        int invTop = 30 + tier.height() * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_LEFT + col * 18, invTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_LEFT + col * 18, invTop + 58));
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
