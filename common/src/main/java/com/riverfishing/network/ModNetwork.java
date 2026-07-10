package com.riverfishing.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-loader networking (§multiloader, 1.21): Architectury {@link NetworkManager} on the
 * {@link CustomPacketPayload} + {@code StreamCodec} system. Each packet is a payload ({@link RfPacket})
 * carrying its own {@code TYPE} + {@code STREAM_CODEC}; the receiver gets the already-decoded payload and
 * hands off — C2S on the server thread, S2C to the client via each packet's {@code EnvExecutor}-guarded
 * handler. The {@code toPlayer/toServer/toTracking} send API is unchanged, so the call sites didn't move.
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** A packet payload that knows how to serialise itself (its {@code TYPE}/{@code STREAM_CODEC} are static). */
    public interface RfPacket extends CustomPacketPayload {
        void write(FriendlyByteBuf buf);
    }

    public static void register() {
        // Client -> server (handled on the server thread).
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, QuestClaimPacket.TYPE, QuestClaimPacket.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(() -> payload.handleServer(ctx)));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SkillUnlockPacket.TYPE, SkillUnlockPacket.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(() -> payload.handleServer(ctx)));

        // Server -> client (handled on the client via EnvExecutor inside each packet).
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, FloatTimingPacket.TYPE, FloatTimingPacket.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(payload::handleClient));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, JournalOpenPacket.TYPE, JournalOpenPacket.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(payload::handleClient));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LineSyncPacket.TYPE, LineSyncPacket.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(payload::handleClient));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RodWarningPacket.TYPE, RodWarningPacket.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(payload::handleClient));
    }

    public static void toPlayer(ServerPlayer player, RfPacket packet) {
        NetworkManager.sendToPlayer(player, packet);
    }

    /** Client → server (e.g. claiming a quest reward from the journal, §quests). */
    public static void toServer(RfPacket packet) {
        NetworkManager.sendToServer(packet);
    }

    /**
     * Sends to the player AND everyone who can see them (§line-multiplayer). Architectury core has no
     * "tracking players" helper (Fabric's PlayerLookup is Fabric-only), so approximate with the same-level
     * players within ~128 blocks — beyond that the in-world line isn't visible anyway.
     */
    public static void toTracking(ServerPlayer player, RfPacket packet) {
        List<ServerPlayer> targets = new ArrayList<>();
        targets.add(player);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.distanceToSqr(player) <= 128.0 * 128.0) {
                targets.add(other);
            }
        }
        NetworkManager.sendToPlayers(targets, packet);
    }
}
