package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the fly cast's rhythm gauge (§fly) — on or off, where the needle's sweep started,
 * its period, how wide the stops are, how many false casts are in the air (and the most the rod can
 * carry), and whether the loop has collapsed. Re-sent after every beat; the server is authoritative.
 */
public class FlyCastPacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<FlyCastPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("fly_cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlyCastPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), FlyCastPacket::decode);

    public final boolean active;
    public final long startTick;
    public final int period;
    public final float zoneHalf;
    public final int beats;
    public final int maxBeats;
    public final boolean openLoop;
    /** The end the last good beat landed on (0 left, 1 right), -1 when either is next. */
    public final byte lastEnd;
    /** 0 = the fly cast, 1 = the winter rod's jig (§ice-rhythm). */
    public final byte mode;

    public FlyCastPacket(boolean active, long startTick, int period, float zoneHalf, int beats,
                         int maxBeats, boolean openLoop, byte lastEnd, byte mode) {
        this.active = active;
        this.startTick = startTick;
        this.period = period;
        this.zoneHalf = zoneHalf;
        this.beats = beats;
        this.maxBeats = maxBeats;
        this.openLoop = openLoop;
        this.lastEnd = lastEnd;
        this.mode = mode;
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
        buf.writeBoolean(openLoop);
        buf.writeByte(lastEnd);
        buf.writeByte(mode);
    }

    public static FlyCastPacket decode(FriendlyByteBuf buf) {
        return new FlyCastPacket(buf.readBoolean(), buf.readLong(), buf.readInt(), buf.readFloat(),
                buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readByte(), buf.readByte());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.FlyCastClient.accept(this));
    }
}
