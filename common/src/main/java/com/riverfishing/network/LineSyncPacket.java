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
    public static final ResourceLocation TYPE = RiverFishing.id("line_sync");

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
    /** §fight-course: FightCourse.ordinal() — which way this run goes, so the rod can lean into it. */
    public final byte course;

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
                          byte floatKind, boolean biting, float tension, boolean fighting,
                          boolean running) {
        this(playerId, active, target, progress, color, floatKind, biting, tension, fighting, running,
                (byte) 0);
    }

    public LineSyncPacket(int playerId, boolean active, BlockPos target, float progress, int color,
                          byte floatKind, boolean biting, float tension, boolean fighting,
                          boolean running, byte course) {
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
        this.course = course;
    }

    @Override
    public ResourceLocation type() {
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
        buf.writeByte(course);
    }

    public static LineSyncPacket decode(FriendlyByteBuf buf) {
        return new LineSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readBlockPos(),
                buf.readFloat(), buf.readInt(), buf.readByte(), buf.readBoolean(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean(), buf.readByte());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.ClientLineState.accept(this));
    }
}
