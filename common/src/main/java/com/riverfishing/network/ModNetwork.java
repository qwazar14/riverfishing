package com.riverfishing.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-loader networking (§multiloader): Architectury {@link NetworkManager} in place of Forge's
 * {@code SimpleChannel}. Each packet implements {@link RfPacket} (its id + how it writes itself); the
 * receivers decode and hand off — C2S on the server thread, S2C to the client via each packet's own
 * {@code EnvExecutor}-guarded handler. The {@code toPlayer/toServer/toTracking} send API is unchanged, so
 * the 16 call sites didn't move.
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** A packet that knows its channel id and how to serialise itself. */
    public interface RfPacket {
        ResourceLocation type();
        void write(FriendlyByteBuf buf);
    }

    public static void register() {
        // Client -> server (handled on the server thread).
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, QuestClaimPacket.TYPE, (buf, ctx) -> {
            QuestClaimPacket p = QuestClaimPacket.decode(buf);
            ctx.queue(() -> p.handleServer(ctx));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SkillUnlockPacket.TYPE, (buf, ctx) -> {
            SkillUnlockPacket p = SkillUnlockPacket.decode(buf);
            ctx.queue(() -> p.handleServer(ctx));
        });

        // Server -> client (handled on the client via EnvExecutor inside each packet).
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, FloatTimingPacket.TYPE, (buf, ctx) -> {
            FloatTimingPacket p = FloatTimingPacket.decode(buf);
            ctx.queue(p::handleClient);
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, JournalOpenPacket.TYPE, (buf, ctx) -> {
            JournalOpenPacket p = JournalOpenPacket.decode(buf);
            ctx.queue(p::handleClient);
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LineSyncPacket.TYPE, (buf, ctx) -> {
            LineSyncPacket p = LineSyncPacket.decode(buf);
            ctx.queue(p::handleClient);
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RodWarningPacket.TYPE, (buf, ctx) -> {
            RodWarningPacket p = RodWarningPacket.decode(buf);
            ctx.queue(p::handleClient);
        });
    }

    public static void toPlayer(ServerPlayer player, RfPacket packet) {
        NetworkManager.sendToPlayer(player, packet.type(), write(packet));
    }

    /** Client → server (e.g. claiming a quest reward from the journal, §quests). */
    public static void toServer(RfPacket packet) {
        NetworkManager.sendToServer(packet.type(), write(packet));
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
        NetworkManager.sendToPlayers(targets, packet.type(), write(packet));
    }

    private static FriendlyByteBuf write(RfPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        return buf;
    }
}
