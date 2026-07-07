package com.riverfishing.network;

import com.riverfishing.quest.Quests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the player clicked a completed quest in the journal to CLAIM its reward (§quests).
 * The server re-validates (goal complete, not yet rewarded) before granting, so the click can't cheat.
 */
public class QuestClaimPacket {
    private final String questId;

    public QuestClaimPacket(String questId) {
        this.questId = questId;
    }

    public static void encode(QuestClaimPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.questId, 64);
    }

    public static QuestClaimPacket decode(FriendlyByteBuf buf) {
        return new QuestClaimPacket(buf.readUtf(64));
    }

    public static void handle(QuestClaimPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp != null) {
                Quests.claim(sp, sp.serverLevel(), p.questId);
            }
        });
        c.setPacketHandled(true);
    }
}
