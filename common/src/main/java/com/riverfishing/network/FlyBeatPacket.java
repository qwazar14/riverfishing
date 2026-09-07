package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.fishing.FlyCast;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: a sneak tap during the fly cast's rhythm (§fly) — one stop of the backcast or the
 * forward cast. Empty on purpose: the server judges it against its own needle when it arrives, and a
 * tap outside a live cast is simply ignored, so there is nothing here to trust.
 */
public class FlyBeatPacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<FlyBeatPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("fly_beat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlyBeatPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), FlyBeatPacket::decode);

    public FlyBeatPacket() {}

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {}

    public static FlyBeatPacket decode(FriendlyByteBuf buf) {
        return new FlyBeatPacket();
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            FlyCast.beat(sp, sp.serverLevel().getGameTime());
        }
    }
}
