package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the jig gauge (§ice-rhythm) — on or off, where the needle's sweep started, its
 * period, how wide the stops are, and how many accents have landed (of the most the combo holds).
 * Re-sent after every accent; the server is authoritative.
 */
public class JigGaugePacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<JigGaugePacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("jig_gauge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JigGaugePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), JigGaugePacket::decode);

    public final boolean active;
    public final long startTick;
    public final int period;
    public final float zoneHalf;
    public final int beats;
    public final int maxBeats;
    /** The stroke the last accent landed on, -1 when none has. */
    public final byte lastEnd;

    public JigGaugePacket(boolean active, long startTick, int period, float zoneHalf, int beats,
                          int maxBeats, byte lastEnd) {
        this.active = active;
        this.startTick = startTick;
        this.period = period;
        this.zoneHalf = zoneHalf;
        this.beats = beats;
        this.maxBeats = maxBeats;
        this.lastEnd = lastEnd;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeLong(startTick);
        buf.writeInt(period);
        buf.writeFloat(zoneHalf);
        buf.writeInt(beats);
        buf.writeInt(maxBeats);
        buf.writeByte(lastEnd);
    }

    public static JigGaugePacket decode(FriendlyByteBuf buf) {
        return new JigGaugePacket(buf.readBoolean(), buf.readLong(), buf.readInt(), buf.readFloat(),
                buf.readInt(), buf.readInt(), buf.readByte());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.JigClient.accept(this));
    }
}
