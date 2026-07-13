package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a strike-timing mini-game has started (or ended). The client renders the runner bar
 * with a GREEN target zone (100% hook) and flanking ORANGE band (25% hook); the server is authoritative.
 */
public class FloatTimingPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<FloatTimingPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("float_timing"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, FloatTimingPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), FloatTimingPacket::decode);

    public final boolean active;
    public final long startTick;
    public final int windowTicks;
    public final int periodTicks;
    public final float greenStart;
    public final float greenEnd;
    public final float orangeStart;
    public final float orangeEnd;

    public FloatTimingPacket(boolean active, long startTick, int windowTicks, int periodTicks,
                             float greenStart, float greenEnd, float orangeStart, float orangeEnd) {
        this.active = active;
        this.startTick = startTick;
        this.windowTicks = windowTicks;
        this.periodTicks = periodTicks;
        this.greenStart = greenStart;
        this.greenEnd = greenEnd;
        this.orangeStart = orangeStart;
        this.orangeEnd = orangeEnd;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeLong(startTick);
        buf.writeInt(windowTicks);
        buf.writeInt(periodTicks);
        buf.writeFloat(greenStart);
        buf.writeFloat(greenEnd);
        buf.writeFloat(orangeStart);
        buf.writeFloat(orangeEnd);
    }

    public static FloatTimingPacket decode(FriendlyByteBuf buf) {
        return new FloatTimingPacket(buf.readBoolean(), buf.readLong(), buf.readInt(), buf.readInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.FloatTimingClient.accept(this));
    }
}
