package com.riverfishing.network;

import com.riverfishing.client.FloatTimingClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: a strike-timing mini-game has started (or ended). The client renders the runner bar
 * with a GREEN target zone (100% hook) and a flanking ORANGE band (25% hook); the server is authoritative
 * on the strike. Both compute the marker from {@code startTick} (game time). The zone can sit anywhere on
 * the bar (§float-zones), so its bounds are sent explicitly.
 */
public class FloatTimingPacket {
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

    public static void encode(FloatTimingPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active);
        buf.writeLong(p.startTick);
        buf.writeInt(p.windowTicks);
        buf.writeInt(p.periodTicks);
        buf.writeFloat(p.greenStart);
        buf.writeFloat(p.greenEnd);
        buf.writeFloat(p.orangeStart);
        buf.writeFloat(p.orangeEnd);
    }

    public static FloatTimingPacket decode(FriendlyByteBuf buf) {
        return new FloatTimingPacket(buf.readBoolean(), buf.readLong(), buf.readInt(), buf.readInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(FloatTimingPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FloatTimingClient.accept(p)));
        c.setPacketHandled(true);
    }
}
