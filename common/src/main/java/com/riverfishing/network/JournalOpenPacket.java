package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** Server → client: the player's journal records, so the client can open the bestiary screen (§15). */
public class JournalOpenPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<JournalOpenPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("journal_open"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, JournalOpenPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), JournalOpenPacket::decode);

    private final CompoundTag data;

    public JournalOpenPacket(CompoundTag data) {
        this.data = data;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static JournalOpenPacket decode(FriendlyByteBuf buf) {
        return new JournalOpenPacket(buf.readNbt());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.JournalScreen.open(data == null ? new CompoundTag() : data));
    }
}
