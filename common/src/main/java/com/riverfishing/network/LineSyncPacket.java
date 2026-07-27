package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

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
    /** §float-kind: 0 none / 1 plain peg / 2 proper float — see FishingSession.floatKind. */
    public final byte floatKind;
    public final boolean biting;
    /** §rod-bend: live line tension 0..1 — drives the in-hand rod bend on the client. */
    public final float tension;
    /** §pump-reel HUD: the fight is on / the fish is currently running. */
    public final boolean fighting;
    public final boolean running;

    public LineSyncPacket(int playerId, boolean active, BlockPos target, float progress, int color,
                          byte floatKind) {
        this(playerId, active, target, progress, color, floatKind, false, 0f, false, false);
    }

    public LineSyncPacket(int playerId, boolean active, BlockPos target, float progress, int color,
                          byte floatKind, boolean biting) {
        this(playerId, active, target, progress, color, floatKind, biting, 0f, false, false);
    }

    public LineSyncPacket(int playerId, boolean active, BlockPos target, float progress, int color,
                          byte floatKind, boolean biting, float tension) {
        this(playerId, active, target, progress, color, floatKind, biting, tension, false, false);
    }

    public LineSyncPacket(int playerId, boolean active, BlockPos target, float progress, int color,
                          byte floatKind, boolean biting, float tension, boolean fighting, boolean running) {
        this.playerId = playerId;
        this.active = active;
        this.target = target == null ? BlockPos.ZERO : target;
        this.progress = progress;
        this.color = color;
        this.floatKind = floatKind;
        this.biting = biting;
        this.tension = tension;
        this.fighting = fighting;
        this.running = running;
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
        buf.writeByte(floatKind);
        buf.writeBoolean(biting);
        buf.writeFloat(tension);
        buf.writeBoolean(fighting);
        buf.writeBoolean(running);
    }

    public static LineSyncPacket decode(FriendlyByteBuf buf) {
        return new LineSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readBlockPos(),
                buf.readFloat(), buf.readInt(), buf.readByte(), buf.readBoolean(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.ClientLineState.accept(this));
    }
}
