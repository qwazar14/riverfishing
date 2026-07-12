package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * Server → clients: a player's fishing-line state (§immersion / §line-multiplayer) — whose line it is,
 * where it lands, reel-in progress (0..1), colour, whether to draw a float, and whether a bite is on now.
 * Broadcast to everyone tracking the angler; {@code active=false} clears it.
 */
public class LineSyncPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<LineSyncPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("line_sync"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, LineSyncPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), LineSyncPacket::decode);

    public final int playerId;
    public final boolean active;
    public final BlockPos target;
    public final float progress;
    public final int color;
    public final boolean bobber;
    public final boolean biting;

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

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeBoolean(active);
        buf.writeBlockPos(target);
        buf.writeFloat(progress);
        buf.writeInt(color);
        buf.writeBoolean(bobber);
        buf.writeBoolean(biting);
    }

    public static LineSyncPacket decode(FriendlyByteBuf buf) {
        return new LineSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readBlockPos(),
                buf.readFloat(), buf.readInt(), buf.readBoolean(), buf.readBoolean());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.ClientLineState.accept(this));
    }
}
