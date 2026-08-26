package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.TackleBoxItem;
import com.riverfishing.menu.TackleBoxMenu;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: rename the open tackle box (§tackle-box).
 *
 * <p>An anvil could already do this, and that is exactly the friction the request was about — you sort
 * tackle at the water, not at a workbench. The name lands on the stack's own {@code CUSTOM_NAME}, so it
 * shows in the hotbar and on the placed box too rather than being a private label the box keeps to itself.
 *
 * <p>Length-capped on BOTH sides: the field stops you typing past it, and this re-checks, because the
 * field is on the client and the client is not trusted with how long a string the server will store.
 */
public class TackleBoxRenamePacket implements ModNetwork.RfPacket {
    public static final int MAX_LENGTH = 32;

    public static final CustomPacketPayload.Type<TackleBoxRenamePacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("tackle_box_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TackleBoxRenamePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), TackleBoxRenamePacket::decode);

    private final String name;

    public TackleBoxRenamePacket(String name) {
        this.name = name;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, MAX_LENGTH);
    }

    public static TackleBoxRenamePacket decode(FriendlyByteBuf buf) {
        return new TackleBoxRenamePacket(buf.readUtf(MAX_LENGTH));
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
        // Only ever the box the player currently has OPEN — never an arbitrary stack named by the client.
        if (!(sp.containerMenu instanceof TackleBoxMenu menu)) return;
        ItemStack box = menu.box();
        if (!(box.getItem() instanceof TackleBoxItem)) return;

        String clean = net.minecraft.util.StringUtil.filterText(name).trim();
        if (clean.length() > MAX_LENGTH) clean = clean.substring(0, MAX_LENGTH);
        if (clean.isEmpty()) {
            box.remove(DataComponents.CUSTOM_NAME);           // blank field = back to the default name
        } else {
            box.set(DataComponents.CUSTOM_NAME, Component.literal(clean));
        }
        if (menu.source().isBlock()
                && sp.level().getBlockEntity(menu.source().pos())
                        instanceof com.riverfishing.block.TackleBoxBlockEntity be) {
            be.setChanged();
        }
    }
}
