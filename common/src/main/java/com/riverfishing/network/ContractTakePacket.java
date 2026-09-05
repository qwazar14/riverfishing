package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * §contracts, client → server: the player clicked post {@code slot} on villager {@code vid}'s board. The
 * server rebuilds that board and decides; nothing in the click is believed.
 */
public record ContractTakePacket(int vid, int slot) implements ModNetwork.RfPacket {

    public static final CustomPacketPayload.Type<ContractTakePacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("contract_take"));

    public static final StreamCodec<FriendlyByteBuf, ContractTakePacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), buf -> new ContractTakePacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(vid);
        buf.writeVarInt(slot);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) com.riverfishing.fishing.Contracts.take(sp, vid, slot);
    }
}
