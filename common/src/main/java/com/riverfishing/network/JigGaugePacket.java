package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the jig gauge (§ice-rhythm) — on or off, where the needle's sweep started, its
 * period, how wide the stops are, and how long the combo is. Re-sent after every click, hit or missed;
 * the server is authoritative, and a combo that arrives as 0 after being higher IS the miss (§jig-3 —
 * the client needs no second field to flash for it).
 */
public class JigGaugePacket implements ModNetwork.RfPacket {
    public static final ResourceLocation TYPE = RiverFishing.id("jig_gauge");

    public final boolean active;
    public final long startTick;
    public final int period;
    public final float zoneHalf;
    public final int beats;

    public JigGaugePacket(boolean active, long startTick, int period, float zoneHalf, int beats) {
        this.active = active;
        this.startTick = startTick;
        this.period = period;
        this.zoneHalf = zoneHalf;
        this.beats = beats;
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeLong(startTick);
        buf.writeInt(period);
        buf.writeFloat(zoneHalf);
        buf.writeInt(beats);
    }

    public static JigGaugePacket decode(FriendlyByteBuf buf) {
        return new JigGaugePacket(buf.readBoolean(), buf.readLong(), buf.readInt(), buf.readFloat(),
                buf.readInt());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.JigClient.accept(this));
    }
}
