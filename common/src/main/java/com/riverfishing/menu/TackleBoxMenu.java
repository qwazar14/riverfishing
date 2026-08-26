package com.riverfishing.menu;

import com.riverfishing.item.TackleBoxData;
import com.riverfishing.item.TackleBoxItem;
import com.riverfishing.item.TackleBoxTier;
import com.riverfishing.registry.ModMenus;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * §tackle-box (0.7.0): the box's slots.
 *
 * <p>It opens on a box you are HOLDING or on one you have set down, and the two are the same object —
 * the placed block stores the item stack itself, so there is exactly one copy of the contents, the name
 * and the colour no matter which way you got here. That is why the menu carries a
 * {@link Source} rather than two code paths that would eventually disagree.
 */
public class TackleBoxMenu extends AbstractContainerMenu {
    /** Where the open box lives: in a hand, or in a block at a position. */
    public record Source(InteractionHand hand, BlockPos pos) {
        public boolean isBlock() {
            return pos != null;
        }
    }

    public static final int PANEL_WIDTH = 176;
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 36;      // room for the name field above the slots
    public static final int NAME_TOP = 16;

    // ONE layout source. The keepnet shipped with the menu and its screen each doing this arithmetic
    // separately, and they disagreed by enough to draw the player's inventory across the grid.
    public static int invTop(TackleBoxTier t) {
        return GRID_TOP + t.rows() * 18 + 13;
    }

    public static int invLabel(TackleBoxTier t) {
        return invTop(t) - 11;
    }

    public static int panelHeight(TackleBoxTier t) {
        return invTop(t) + 80;
    }

    private final Player player;
    private final Source source;
    private final TackleBoxTier tier;
    private final Container contents;
    /**
     * Set once the container is filled. Without it, the load loop below writes an EMPTY box straight back
     * over the real one — {@code setItem} fires {@code setChanged} on every slot, and the first of those
     * ran while {@code contents} was still null, so the whole menu threw in its own constructor and the
     * box simply did not open.
     */
    private boolean ready;

    public TackleBoxMenu(int id, Inventory inv, Source source) {
        super(ModMenus.TACKLE_BOX.get(), id);
        this.player = inv.player;
        this.source = source;
        ItemStack box = box();
        this.tier = TackleBoxData.tierOf(box);

        NonNullList<ItemStack> loaded = TackleBoxData.load(box);
        SimpleContainer c = new SimpleContainer(tier.slots()) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (ready) save();
            }
        };
        this.contents = c;
        for (int i = 0; i < tier.slots(); i++) {
            c.setItem(i, loaded.get(i).copy());
        }
        this.ready = true;

        for (int row = 0; row < tier.rows(); row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new TackleSlot(contents, col + row * 9, GRID_LEFT + col * 18, GRID_TOP + row * 18));
            }
        }
        int invTop = invTop(tier);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, invTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, invTop + 58));
        }
    }

    /** Only tackle, and never the box itself — a box inside a box is a bag of holding, not a tackle box. */
    private static class TackleSlot extends Slot {
        TackleSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return TackleBoxData.isTackle(stack);
        }
    }

    public TackleBoxTier tier() {
        return tier;
    }

    public Source source() {
        return source;
    }

    /** The open box itself, wherever it lives. Never cached — a hand can be emptied mid-screen. */
    public ItemStack box() {
        if (source.isBlock()) {
            return player.level().getBlockEntity(source.pos())
                    instanceof com.riverfishing.block.TackleBoxBlockEntity be
                    ? be.box() : ItemStack.EMPTY;
        }
        return player.getItemInHand(source.hand());
    }

    private void save() {
        if (contents == null || player.level().isClientSide) return;
        ItemStack box = box();
        if (box.isEmpty()) return;
        NonNullList<ItemStack> out = NonNullList.withSize(tier.slots(), ItemStack.EMPTY);
        for (int i = 0; i < tier.slots(); i++) {
            out.set(i, contents.getItem(i));
        }
        TackleBoxData.save(box, out);
        if (source.isBlock()
                && player.level().getBlockEntity(source.pos())
                        instanceof com.riverfishing.block.TackleBoxBlockEntity be) {
            be.setChanged();
        }
    }

    @Override
    public void removed(Player p) {
        save();
        super.removed(p);
    }

    @Override
    public boolean stillValid(Player p) {
        ItemStack box = box();
        if (!(box.getItem() instanceof TackleBoxItem)) return false;
        return !source.isBlock() || p.distanceToSqr(source.pos().getCenter()) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int boxEnd = tier.slots();

        if (index < boxEnd) {
            if (!moveItemStackTo(stack, boxEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Shift-clicking a non-tackle item out of the inventory must do NOTHING rather than silently
            // land it somewhere else — mayPlace already refuses it, and moveItemStackTo honours that.
            if (!TackleBoxData.isTackle(stack) || !moveItemStackTo(stack, 0, boxEnd, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        save();
        return result;
    }

    public static TackleBoxMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new TackleBoxMenu(id, inv, new Source(hand, pos));
    }

    public static void open(ServerPlayer sp, InteractionHand hand) {
        openAt(sp, new Source(hand, null));
    }

    public static void open(ServerPlayer sp, BlockPos pos) {
        openAt(sp, new Source(InteractionHand.MAIN_HAND, pos));
    }

    private static void openAt(ServerPlayer sp, Source source) {
        // §1.20.1: arch 9.x carries the extra buffer on the PROVIDER (saveExtraData) rather than as a
        // separate consumer — the same shape every other menu in this build already uses.
        MenuRegistry.openExtendedMenu(sp, new dev.architectury.registry.menu.ExtendedMenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.empty();   // the screen draws the box's own name field
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new TackleBoxMenu(id, inv, source);
            }

            @Override
            public void saveExtraData(FriendlyByteBuf buf) {
                buf.writeEnum(source.hand());
                buf.writeBoolean(source.isBlock());
                if (source.isBlock()) buf.writeBlockPos(source.pos());
            }
        });
    }
}
