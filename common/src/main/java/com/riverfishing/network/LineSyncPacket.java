package com.riverfishing.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → clients: a player's fishing-line state (§immersion) — whose line it is, where it lands,
 * how far the fish has been reeled in (0..1), and the line's colour (by line type). Broadcast to
 * everyone tracking the angler (§line-multiplayer) so other players see the line too;
 * {@code active=false} clears it.
 */
public class LineSyncPacket {
    public final int playerId; // entity id of the angler this line belongs to
    public final boolean active;
    public final BlockPos target;
    public final float progress;
    public final int color;
    public final boolean bobber; // draw a float at the line's end (float rigs only, §bobber-render)
    public final boolean biting; // a bite is on RIGHT NOW: the bobber plunges / the line twitches

    public LineSyncPacket(int playerId, boolean active, BlockPos target, float progress, int color,
                          boolean bobber) {
        this(playerId, active, target, progress, color, bobber, false);
    }

    public LineSyncPacket(int playerId, boolean active, BlockPos target, float progress, int color,
                          boolean bobber, boolean biting) {
        this.playerId = playerId;
        this.active = active;
        this.target = target == null ? BlockPos.ZERO : target;
        this.progress = progress;
        this.color = color;
        this.bobber = bobber;
        this.biting = biting;
    }

    public static void encode(LineSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.playerId);
        buf.writeBoolean(p.active);
        buf.writeBlockPos(p.target);
        buf.writeFloat(p.progress);
        buf.writeInt(p.color);
        buf.writeBoolean(p.bobber);
        buf.writeBoolean(p.biting);
    }

    public static LineSyncPacket decode(FriendlyByteBuf buf) {
        return new LineSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readBlockPos(),
                buf.readFloat(), buf.readInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(LineSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.riverfishing.client.ClientLineState.accept(p)));
        c.setPacketHandled(true);
    }
}
