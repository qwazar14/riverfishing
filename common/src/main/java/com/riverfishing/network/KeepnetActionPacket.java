package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.KeepnetData;
import com.riverfishing.item.KeepnetItem;
import com.riverfishing.menu.KeepnetMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * §keepnet (0.7.0): client → server, the one message the grid needs.
 *
 * <p>The client never edits the box. It says "I clicked here, holding this rotation" and the server
 * decides, because the box is item NBT and item NBT that two sides disagree about is a duplication bug
 * waiting for a laggy tick.
 */
public record KeepnetActionPacket(int action, int x, int y, int rot) implements ModNetwork.RfPacket {
    /** Put whatever is on the cursor into the cell under it. */
    public static final int PLACE = 0;
    /** Take whatever covers the cell onto the cursor. */
    public static final int TAKE = 1;
    /** Tidy: repack everything biggest-first. Anything that no longer fits goes to the player. */
    public static final int REPACK = 2;
    /** Empty the whole box into the player's inventory. */
    public static final int EMPTY = 3;

    public static final CustomPacketPayload.Type<KeepnetActionPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("keepnet_action"));

    public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, KeepnetActionPacket>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
                    (buf, p) -> p.write(buf), KeepnetActionPacket::read);

    private static KeepnetActionPacket read(FriendlyByteBuf buf) {
        return new KeepnetActionPacket(buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeByte(x);
        buf.writeByte(y);
        buf.writeByte(rot);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server side. Every branch validates against the real box before it changes anything. */
    public void handleServer(dev.architectury.networking.NetworkManager.PacketContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(sp.containerMenu instanceof KeepnetMenu menu)) return;
        ItemStack net = menu.net();
        if (!(net.getItem() instanceof KeepnetItem)) return;
        KeepnetData data = KeepnetData.read(net);

        switch (action) {
            case PLACE -> {
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) return;
                ItemStack one = carried.copyWithCount(1);
                if (!data.place(one, x, y, rot)) return;
                carried.shrink(1);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                data.write(net);
            }
            case TAKE -> {
                if (!menu.getCarried().isEmpty()) return;   // one thing on the cursor at a time
                ItemStack taken = data.take(x, y);
                if (taken.isEmpty()) return;
                menu.setCarried(taken);
                data.write(net);
            }
            case REPACK -> {
                data.repack();
                data.write(net);
                for (ItemStack over : data.spilled()) {
                    if (!sp.getInventory().add(over)) sp.drop(over, false);
                }
            }
            case EMPTY -> {
                for (var p : java.util.List.copyOf(data.items())) {
                    if (!sp.getInventory().add(p.stack())) sp.drop(p.stack(), false);
                }
                data.items().clear();
                data.write(net);
            }
            default -> { }
        }
        menu.broadcastChanges();
    }
}
