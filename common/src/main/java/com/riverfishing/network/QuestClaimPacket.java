package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.quest.Quests;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: the player clicked a completed quest in the journal to CLAIM its reward (§quests).
 * The server re-validates (goal complete, not yet rewarded) before granting.
 */
public class QuestClaimPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<QuestClaimPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("quest_claim"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, QuestClaimPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), QuestClaimPacket::decode);

    private final String questId;

    public QuestClaimPacket(String questId) {
        this.questId = questId;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(questId, 64);
    }

    public static QuestClaimPacket decode(FriendlyByteBuf buf) {
        return new QuestClaimPacket(buf.readUtf(64));
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            Quests.claim(sp, sp.level(), questId);
        }
    }
}
