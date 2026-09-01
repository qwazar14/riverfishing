package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.quest.Quests;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
            // §contracts share this packet: both are "the journal says this row is ready, pay it", and
            // both re-validate from scratch here. A contract id is 'c' + the day it was minted on, so
            // the test is 'c' followed by a DIGIT — every quest id in the chain starts with "q_", but a
            // rule that only said "starts with c" would be one new quest name away from being wrong.
            if (questId.length() > 1 && questId.charAt(0) == 'c'
                    && Character.isDigit(questId.charAt(1))) {
                com.riverfishing.fishing.Contracts.claim(sp, questId);
                return;
            }
            Quests.claim(sp, sp.serverLevel(), questId);
        }
    }
}
