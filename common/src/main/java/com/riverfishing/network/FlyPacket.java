package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Client → server, §fly-take: where the fly is (the rope is simulated on the client, the fish live on
 * the server), whether it sits in water, and what the hand just did. The server trusts none of it
 * beyond "a point near the player": the water column is checked there, the bite is its own roll,
 * and a strike outside a bite window is simply a lift of the line.
 */
public class FlyPacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<FlyPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("fly"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlyPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), FlyPacket::decode);

    public static final int ON_WATER = 1, STRIKE = 2, STRIP = 4, ACTIVE = 8, CAST = 16, LINED = 32;

    public final boolean mainHand;
    public final float x, y, z;
    public final int flags;
    /** §technique: 0..1, how well the fly is being fished for what it is. */
    public final float presentation;
    /** §fly-splash: how fast the fly hit the water this update, m/s; 0 when it did not land. */
    public final float landSpeed;

    public FlyPacket(boolean mainHand, double x, double y, double z, int flags) {
        this(mainHand, x, y, z, flags, 0.5f, 0f);
    }

    public FlyPacket(boolean mainHand, double x, double y, double z, int flags, float presentation, float landSpeed) {
        this.mainHand = mainHand;
        this.x = (float) x; this.y = (float) y; this.z = (float) z;
        this.flags = flags;
        this.presentation = presentation;
        this.landSpeed = landSpeed;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(mainHand);
        buf.writeFloat(x); buf.writeFloat(y); buf.writeFloat(z);
        buf.writeByte(flags);
        buf.writeByte(Math.round(Math.max(0f, Math.min(1f, presentation)) * 255f));
        buf.writeByte(Math.round(Math.max(0f, Math.min(15.9f, landSpeed)) * 16f));
    }

    public static FlyPacket decode(FriendlyByteBuf buf) {
        boolean main = buf.readBoolean();
        float x = buf.readFloat(), y = buf.readFloat(), z = buf.readFloat();
        int flags = buf.readByte();
        float pres = (buf.readByte() & 255) / 255f;
        return new FlyPacket(main, x, y, z, flags, pres, (buf.readByte() & 255) / 16f);
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            // Never further than a long line from the angler — anything else is not a fly.
            if (sp.distanceToSqr(x, y, z) > 40.0 * 40.0) return;
            com.riverfishing.fishing.FishingManager.flyUpdate(sp,
                    mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, x, y, z, flags, presentation, landSpeed);
        }
    }
}
