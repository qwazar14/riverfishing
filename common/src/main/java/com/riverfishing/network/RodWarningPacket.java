package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a tackle warning (reel/line incompatibility, §tackle-compat) shown INSIDE the rod
 * assembly window rather than as external chat/actionbar text.
 */
public class RodWarningPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RodWarningPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("rod_warning"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RodWarningPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), RodWarningPacket::decode);

    private final Component message;

    public RodWarningPacket(Component message) {
        this.message = message;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        // §data-components (1.21): Components need registry access on the wire → ComponentSerialization
        // over the RegistryFriendlyByteBuf the StreamCodec actually hands us.
        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(
                (net.minecraft.network.RegistryFriendlyByteBuf) buf, message);
    }

    public static RodWarningPacket decode(FriendlyByteBuf buf) {
        return new RodWarningPacket(net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(
                (net.minecraft.network.RegistryFriendlyByteBuf) buf));
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.RodAssemblyScreen.showWarning(message));
    }
}
