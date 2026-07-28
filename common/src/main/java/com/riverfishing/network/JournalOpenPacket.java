package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server → client: the player's journal records, so the client can open the bestiary screen (§15). */
public class JournalOpenPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<JournalOpenPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("journal_open"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, JournalOpenPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), JournalOpenPacket::decode);

    private final CompoundTag data;
    /** §guide-nudge: a guide page to open on, or "" for the journal's normal front page. */
    private final String guide;

    public JournalOpenPacket(CompoundTag data) {
        this(data, "");
    }

    public JournalOpenPacket(CompoundTag data, String guide) {
        this.data = data;
        this.guide = guide == null ? "" : guide;
    }

    /**
     * §order-board: the journal tag carries the order checklist under its own key. The SERVER builds it —
     * it is the only side that has the fish profiles, the water body and the season — and sends lang keys
     * rather than sentences, so a client in any language draws it correctly.
     */
    public static CompoundTag withOrder(CompoundTag journal, net.minecraft.server.level.ServerPlayer sp) {
        CompoundTag copy = journal.copy();
        copy.put("order", com.riverfishing.fishing.OrderBoard.build(sp));
        return copy;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(data);
        buf.writeUtf(guide, 32);
    }

    public static JournalOpenPacket decode(FriendlyByteBuf buf) {
        return new JournalOpenPacket(buf.readNbt(), buf.readUtf(32));
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.JournalScreen.open(
                        data == null ? new CompoundTag() : data, guide));
    }
}
