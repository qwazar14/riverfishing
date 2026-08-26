package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.fishing.FishingManager;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: which way the angler is pulling during a fight (§fight-course).
 *
 * <p>It exists because this version of Minecraft does not tell the server what a standing player is
 * pressing — raw movement input only crosses the wire for someone riding a vehicle. The first cut read the
 * player's AIM instead, which needed no packet and was the wrong answer anyway: countering a run meant
 * turning thirty degrees away from the water, so the mechanic asked you to stop looking at the thing you
 * were fighting, and the rod itself swung off screen.
 *
 * <p>Sent only when the pressed direction CHANGES, and only while a fight is live, so a whole fight costs a
 * handful of bytes. Nothing here is trusted for gain: the server independently knows the run's course, and
 * a byte outside 0..4 simply reads as "not pulling".
 */
public class FightInputPacket implements ModNetwork.RfPacket {
    public static final byte NONE = 0;
    /** The four movement keys, named by what they do to the rod rather than by the letter. */
    public static final byte PULL_LEFT = 1;   // A — strafe left
    public static final byte PULL_RIGHT = 2;  // D — strafe right
    public static final byte PUSH = 3;        // W — rod forward / down
    public static final byte LIFT = 4;        // S — rod back / up

    public static final CustomPacketPayload.Type<FightInputPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("fight_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FightInputPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), FightInputPacket::decode);

    private final byte dir;

    public FightInputPacket(byte dir) {
        this.dir = dir;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(dir);
    }

    public static FightInputPacket decode(FriendlyByteBuf buf) {
        return new FightInputPacket(buf.readByte());
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            FishingManager.setPullDirection(sp, dir);
        }
    }
}
